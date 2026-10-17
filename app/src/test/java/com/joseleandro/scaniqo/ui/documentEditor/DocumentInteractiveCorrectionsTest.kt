package com.joseleandro.scaniqo.ui.documentEditor

import androidx.lifecycle.ViewModelStore
import com.joseleandro.scaniqo.core.security.PasswordAccess
import com.joseleandro.scaniqo.core.security.PasswordVaultLockedException
import com.joseleandro.scaniqo.core.security.PasswordVaultState
import com.joseleandro.scaniqo.domain.model.DocumentModel
import com.joseleandro.scaniqo.domain.model.PageAlignment
import com.joseleandro.scaniqo.domain.model.PageScaleMode
import com.joseleandro.scaniqo.domain.model.PdfImageFilter
import com.joseleandro.scaniqo.domain.model.PdfInteractivePixels
import com.joseleandro.scaniqo.domain.model.PdfMergeSource
import com.joseleandro.scaniqo.domain.model.PdfPageLayout
import com.joseleandro.scaniqo.domain.model.PdfPagePreview
import com.joseleandro.scaniqo.domain.model.PdfPageQuality
import com.joseleandro.scaniqo.domain.model.PdfPageSpec
import com.joseleandro.scaniqo.domain.model.PdfPerspective
import com.joseleandro.scaniqo.domain.model.PdfSignaturePlacement
import com.joseleandro.scaniqo.domain.model.PdfQualityReport
import com.joseleandro.scaniqo.domain.model.PdfWorkspace
import com.joseleandro.scaniqo.domain.model.ScannedDocument
import com.joseleandro.scaniqo.domain.model.SignatureFont
import com.joseleandro.scaniqo.domain.model.SignatureModel
import com.joseleandro.scaniqo.domain.model.SignatureStroke
import com.joseleandro.scaniqo.domain.repository.PdfEditorRepository
import com.joseleandro.scaniqo.domain.repository.SignatureRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Cobre gestos rápidos, seleção descontínua e renderização apenas das folhas realmente exibidas. */
@OptIn(ExperimentalCoroutinesApi::class)
class DocumentInteractiveCorrectionsTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val repository = InteractiveRepository()
    private val passwords = InteractivePasswords()
    private val document = DocumentModel(
        id = 51, name = "Páginas", pdfUri = "file:///saved.pdf",
        pageCount = 5, createdAt = 1L, sizeBytes = 500L,
    )
    private val viewModel by lazy {
        DocumentToolsViewModel(repository, EmptySignatures(), passwords)
            .also { store.put("interactive", it) }
    }

    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() {
        store.clear()
        dispatcher.scheduler.runCurrent()
        Dispatchers.resetMain()
    }

    @Test fun layoutGesturesDebounceWithoutChangingRecipeOrUndo() = runTest(dispatcher) {
        viewModel.load(document)
        advanceUntilIdle()
        val original = viewModel.uiState.value.workspace
        val count = repository.calls.size
        viewModel.onEvent(DocumentToolsEvent.OpenTool(DocumentTool.LAYOUT))
        for (scale in listOf(.8f, 1.2f, 1.6f)) {
            viewModel.onEvent(DocumentToolsEvent.PreviewLayout("page-1", PdfPageLayout(contentScale = scale)))
            advanceTimeBy(40)
        }
        advanceTimeBy(79)
        runCurrent()
        assertEquals(count, repository.calls.size)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(count + 1, repository.calls.size)
        assertEquals(1.6f, repository.calls.last().page.layout.contentScale, 0f)
        assertEquals(original, viewModel.uiState.value.workspace)
        assertFalse(viewModel.uiState.value.hasChanges)
        assertFalse(viewModel.uiState.value.canUndo)
        assertEquals("page-1", viewModel.uiState.value.layoutPreviewPageId)
        assertFalse(viewModel.uiState.value.isPreviewingLayout)
    }

    @Test fun layoutDraftPreservesEachPagesFilterCorrectionsAndSignatures() = runTest(dispatcher) {
        val signature = PdfSignaturePlacement("signature", "file:///signature.png")
        repository.transformPage = { page ->
            page.copy(
                layout = PdfPageLayout(
                    imageFilter = if (page.id == "page-1") PdfImageFilter.WHITEN else PdfImageFilter.GRAYSCALE,
                    filterIntensity = .4f,
                    straightenDegrees = 3f,
                    perspective = PdfPerspective(),
                    curvature = .2f,
                    rasterizeText = true,
                ),
                signatures = listOf(signature),
            )
        }
        viewModel.load(document)
        advanceUntilIdle()
        val committed = viewModel.uiState.value.workspace!!
        viewModel.onEvent(DocumentToolsEvent.OpenTool(DocumentTool.LAYOUT))
        for (page in committed.pages.take(2)) {
            viewModel.onEvent(DocumentToolsEvent.PreviewLayout(
                page.id, PdfPageLayout(
                    alignment = PageAlignment.RIGHT, scaleMode = PageScaleMode.FILL,
                    rotationQuarterTurns = 1, contentScale = .7f,
                ),
            ))
            advanceUntilIdle()
            val expected = page.copy(layout = page.layout.copy(
                alignment = PageAlignment.RIGHT, scaleMode = PageScaleMode.FILL,
                rotationQuarterTurns = 1, contentScale = .7f,
            ))
            assertEquals(expected, repository.calls.last().page)
        }
        assertEquals(committed, viewModel.uiState.value.workspace)
        assertEquals("page-1", viewModel.uiState.value.selectedPageId)
    }

    @Test fun layoutComparisonShowsCommittedPageAndDoesNotResetItsImprovements() = runTest(dispatcher) {
        repository.transformPage = { page ->
            page.copy(layout = PdfPageLayout(
                alignment = PageAlignment.BOTTOM, rotationQuarterTurns = 2,
                imageFilter = PdfImageFilter.DENOISE, straightenDegrees = 4f,
            ))
        }
        viewModel.load(document)
        advanceUntilIdle()
        val committed = viewModel.uiState.value.workspace!!
        viewModel.onEvent(DocumentToolsEvent.OpenTool(DocumentTool.LAYOUT))
        val draft = PdfPageLayout(alignment = PageAlignment.TOP, rotationQuarterTurns = 1)
        viewModel.onEvent(DocumentToolsEvent.PreviewLayout("page-1", draft))
        advanceUntilIdle()
        val editedUri = viewModel.uiState.value.layoutPreview!!.uri
        viewModel.onEvent(DocumentToolsEvent.PreviewLayout("page-1", draft, showOriginal = true))
        advanceUntilIdle()
        assertEquals(committed.pages.first(), repository.calls.last().page)
        assertTrue(viewModel.uiState.value.layoutPreviewShowingOriginal)
        assertTrue(editedUri != viewModel.uiState.value.layoutPreview!!.uri)
        assertEquals(committed, viewModel.uiState.value.workspace)
    }

    @Test fun changingDraftPageCancelsPendingLayoutAndOnlyRendersNewFocus() = runTest(dispatcher) {
        viewModel.load(document.copy(pageCount = 100))
        advanceUntilIdle()
        val count = repository.calls.size
        viewModel.onEvent(DocumentToolsEvent.OpenTool(DocumentTool.LAYOUT))
        viewModel.onEvent(DocumentToolsEvent.PreviewLayout("page-1", PdfPageLayout(rotationQuarterTurns = 1)))
        advanceTimeBy(80)
        viewModel.onEvent(DocumentToolsEvent.PreviewLayout("page-75", PdfPageLayout(rotationQuarterTurns = 1)))
        advanceUntilIdle()
        assertEquals(listOf("page-75"), repository.calls.drop(count).map { it.page.id })
        assertEquals("page-75", viewModel.uiState.value.layoutPreviewPageId)
        assertEquals("page-1", viewModel.uiState.value.selectedPageId)
        assertFalse(viewModel.uiState.value.hasChanges)
    }

    @Test fun lateLayoutPreviewCannotReplaceMoreRecentGesture() = runTest(dispatcher) {
        viewModel.load(document)
        advanceUntilIdle()
        viewModel.onEvent(DocumentToolsEvent.OpenTool(DocumentTool.LAYOUT))
        val gate = CompletableDeferred<Unit>()
        repository.nextGate = gate
        repository.ignoreNextCancellation = true
        viewModel.onEvent(DocumentToolsEvent.PreviewLayout("page-1", PdfPageLayout(rotationQuarterTurns = 1)))
        advanceTimeBy(120)
        runCurrent()
        val staleUri = repository.calls.last().uri
        viewModel.onEvent(DocumentToolsEvent.PreviewLayout("page-1", PdfPageLayout(rotationQuarterTurns = 3)))
        advanceTimeBy(120)
        runCurrent()
        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(3, repository.calls.last().page.layout.rotationQuarterTurns)
        assertEquals(repository.calls.last().uri, viewModel.uiState.value.layoutPreview!!.uri)
        assertTrue(staleUri != viewModel.uiState.value.layoutPreview!!.uri)
        assertFalse(viewModel.uiState.value.hasChanges)
    }

    @Test fun clearingAndClosingLayoutDiscardOnlyDraft() = runTest(dispatcher) {
        viewModel.load(document)
        advanceUntilIdle()
        val committed = viewModel.uiState.value.workspace
        viewModel.onEvent(DocumentToolsEvent.OpenTool(DocumentTool.LAYOUT))
        viewModel.onEvent(DocumentToolsEvent.PreviewLayout("page-1", PdfPageLayout(rotationQuarterTurns = 1)))
        advanceUntilIdle()
        viewModel.onEvent(DocumentToolsEvent.ClearLayoutPreview)
        assertNull(viewModel.uiState.value.layoutPreview)
        assertNull(viewModel.uiState.value.layoutPreviewPageId)
        viewModel.onEvent(DocumentToolsEvent.PreviewLayout("page-2", PdfPageLayout(contentScale = 1.5f)))
        viewModel.onEvent(DocumentToolsEvent.RequestBack)
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.tool)
        assertNull(viewModel.uiState.value.layoutPreview)
        assertEquals(committed, viewModel.uiState.value.workspace)
        assertFalse(viewModel.uiState.value.canUndo)
    }

    @Test fun lockClearsLayoutDraftAndRejectsResultFromUncancelableRender() = runTest(dispatcher) {
        passwords.unlock()
        viewModel.load(document.copy(protectionKeyId = "key"))
        advanceUntilIdle()
        val committed = viewModel.uiState.value.workspace
        viewModel.onEvent(DocumentToolsEvent.OpenTool(DocumentTool.LAYOUT))
        val gate = CompletableDeferred<Unit>()
        repository.nextGate = gate
        repository.ignoreNextCancellation = true
        viewModel.onEvent(DocumentToolsEvent.PreviewLayout("page-1", PdfPageLayout(contentScale = 1.5f)))
        advanceTimeBy(120)
        runCurrent()
        passwords.lock()
        viewModel.onEvent(DocumentToolsEvent.Backgrounded)
        assertNull(viewModel.uiState.value.layoutPreviewPageId)
        gate.complete(Unit)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isLocked)
        assertNull(viewModel.uiState.value.layoutPreview)
        assertEquals(committed, viewModel.uiState.value.workspace)
    }

    @Test fun applyingLayoutDraftToSelectedPagesMatchesPreviewWithOneUndoStep() = runTest(dispatcher) {
        viewModel.load(document)
        advanceUntilIdle()
        val original = viewModel.uiState.value.workspace
        viewModel.onEvent(DocumentToolsEvent.OpenTool(DocumentTool.LAYOUT))
        viewModel.onEvent(DocumentToolsEvent.PreviewLayout(
            "page-4", PdfPageLayout(
                alignment = PageAlignment.TOP, scaleMode = PageScaleMode.FILL,
                rotationQuarterTurns = -1, contentScale = 3f,
            ),
        ))
        advanceUntilIdle()
        val previewed = repository.calls.last().page.layout
        viewModel.onEvent(DocumentToolsEvent.ApplyLayout(
            PageAlignment.TOP, PageScaleMode.FILL, allPages = false,
            rotationQuarterTurns = -1, contentScale = 3f, pageIds = setOf("page-2", "page-4"),
        ))
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.layoutPreview)
        assertNull(viewModel.uiState.value.tool)
        assertTrue(viewModel.uiState.value.workspace!!.pages
            .filter { it.id in setOf("page-2", "page-4") }.all { it.layout == previewed })
        assertEquals(3, previewed.rotationQuarterTurns)
        assertEquals(2f, previewed.contentScale, 0f)
        viewModel.onEvent(DocumentToolsEvent.Undo)
        advanceUntilIdle()
        assertEquals(original, viewModel.uiState.value.workspace)
        assertFalse(viewModel.uiState.value.canUndo)
    }

    @Test fun savingWhileLayoutDraftExistsExportsOnlyCommittedRecipe() = runTest(dispatcher) {
        viewModel.load(document)
        advanceUntilIdle()
        viewModel.onEvent(DocumentToolsEvent.ApplyFilter(PdfImageFilter.ENHANCE, allPages = true))
        advanceUntilIdle()
        val committed = viewModel.uiState.value.workspace
        viewModel.onEvent(DocumentToolsEvent.OpenTool(DocumentTool.LAYOUT))
        viewModel.onEvent(DocumentToolsEvent.PreviewLayout("page-1", PdfPageLayout(rotationQuarterTurns = 2)))
        advanceUntilIdle()
        viewModel.onEvent(DocumentToolsEvent.Save)
        advanceUntilIdle()
        assertEquals(listOf(committed), repository.saved)
        assertFalse(viewModel.uiState.value.hasChanges)
    }

    @Test fun layoutPreviewRequiresOpenToolValidPageAndFiniteScale() = runTest(dispatcher) {
        viewModel.load(document)
        advanceUntilIdle()
        val count = repository.calls.size
        viewModel.onEvent(DocumentToolsEvent.PreviewLayout("page-1", PdfPageLayout(contentScale = 1.5f)))
        advanceUntilIdle()
        assertEquals(count, repository.calls.size)
        viewModel.onEvent(DocumentToolsEvent.OpenTool(DocumentTool.LAYOUT))
        viewModel.onEvent(DocumentToolsEvent.PreviewLayout("missing", PdfPageLayout()))
        advanceUntilIdle()
        assertEquals(count, repository.calls.size)
        assertNull(viewModel.uiState.value.layoutPreview)
        viewModel.onEvent(DocumentToolsEvent.PreviewLayout("page-1", PdfPageLayout(contentScale = Float.NaN)))
        advanceUntilIdle()
        assertEquals(1f, repository.calls.last().page.layout.contentScale, 0f)
        assertFalse(viewModel.uiState.value.hasChanges)
    }

    @Test fun explicitNonContiguousFilterSelectionOverridesAllAndCreatesOneUndo() = runTest(dispatcher) {
        viewModel.load(document)
        advanceUntilIdle()
        val original = viewModel.uiState.value.workspace
        viewModel.onEvent(DocumentToolsEvent.ApplyFilter(
            PdfImageFilter.DENOISE, allPages = true, intensity = .6f,
            pageIds = setOf("page-1", "page-4"),
        ))
        advanceUntilIdle()
        assertEquals(setOf("page-1", "page-4"), viewModel.uiState.value.workspace!!.pages
            .filter { it.layout.imageFilter == PdfImageFilter.DENOISE }.map { it.id }.toSet())
        assertTrue(viewModel.uiState.value.canUndo)
        viewModel.onEvent(DocumentToolsEvent.Undo)
        advanceUntilIdle()
        assertEquals(original, viewModel.uiState.value.workspace)
        assertFalse(viewModel.uiState.value.canUndo)
    }

    @Test fun selectedLayoutPagesCanExcludeCurrentPageAndIgnoreUnknownIds() = runTest(dispatcher) {
        viewModel.load(document)
        advanceUntilIdle()
        viewModel.onEvent(DocumentToolsEvent.ApplyLayout(
            PageAlignment.RIGHT, PageScaleMode.FILL, allPages = false,
            rotationQuarterTurns = 1, contentScale = .75f,
            pageIds = setOf("page-2", "page-5", "missing"),
        ))
        advanceUntilIdle()
        val changed = viewModel.uiState.value.workspace!!.pages.filter { it.layout != PdfPageLayout() }
        assertEquals(listOf("page-2", "page-5"), changed.map { it.id })
        assertTrue(changed.all {
            it.layout.rotationQuarterTurns == 1 && it.layout.contentScale == .75f &&
                it.layout.alignment == PageAlignment.RIGHT && it.layout.scaleMode == PageScaleMode.FILL
        })
        assertEquals("page-1", viewModel.uiState.value.selectedPageId)
    }

    @Test fun correctionsApplyOnlyToExplicitNonContiguousSelection() = runTest(dispatcher) {
        viewModel.load(document)
        advanceUntilIdle()
        viewModel.onEvent(DocumentToolsEvent.ApplyCorrections(
            allPages = true, straightenDegrees = 4f, perspective = null, curvature = -.2f,
            pageIds = setOf("page-3", "page-5"),
        ))
        advanceUntilIdle()
        val pages = viewModel.uiState.value.workspace!!.pages
        assertEquals(setOf("page-3", "page-5"), pages.filter { it.layout.straightenDegrees == 4f }.map { it.id }.toSet())
        assertTrue(pages.filter { it.id in setOf("page-3", "page-5") }.all { it.layout.curvature == -.2f })
        assertTrue(pages.filterNot { it.id in setOf("page-3", "page-5") }.all { it.layout == PdfPageLayout() })
    }

    @Test fun correctionPreviewAndApplyPreserveRasterizationOnlyForPreviouslyEnabledFilters() = runTest(dispatcher) {
        repository.transformPage = { page ->
            page.copy(layout = when (page.id) {
                "page-1" -> PdfPageLayout(imageFilter = PdfImageFilter.ENHANCE, rasterizeText = true)
                "page-2" -> PdfPageLayout(rasterizeText = true)
                "page-3" -> PdfPageLayout(imageFilter = PdfImageFilter.DENOISE, filterIntensity = 0f, rasterizeText = true)
                "page-4" -> PdfPageLayout(imageFilter = PdfImageFilter.ENHANCE, rasterizeText = false)
                else -> PdfPageLayout()
            })
        }
        viewModel.load(document)
        advanceUntilIdle()
        val committed = viewModel.uiState.value.workspace!!
        viewModel.onEvent(DocumentToolsEvent.OpenTool(DocumentTool.CORRECTIONS))
        for (page in committed.pages) {
            viewModel.onEvent(DocumentToolsEvent.PreviewCorrections(
                page.id, PdfPageLayout(straightenDegrees = 3f, rasterizeText = false),
            ))
            advanceUntilIdle()
            assertEquals(page.id == "page-1", repository.calls.last().page.layout.rasterizeText)
            assertEquals(page.layout.imageFilter, repository.calls.last().page.layout.imageFilter)
        }
        assertEquals(committed, viewModel.uiState.value.workspace)
        viewModel.onEvent(DocumentToolsEvent.ApplyCorrections(
            allPages = true, straightenDegrees = 3f, perspective = null,
            curvature = 0f, rasterizeText = false,
        ))
        advanceUntilIdle()
        val pages = viewModel.uiState.value.workspace!!.pages
        assertEquals(listOf("page-1"), pages.filter { it.layout.rasterizeText }.map { it.id })
        assertEquals(committed.pages.map { it.layout.imageFilter }, pages.map { it.layout.imageFilter })
        assertTrue(pages.all { it.layout.straightenDegrees == 3f })
    }

    @Test fun explicitCorrectionRasterizationIsKeptInPreviewAndApplyWithoutFilters() = runTest(dispatcher) {
        viewModel.load(document)
        advanceUntilIdle()
        viewModel.onEvent(DocumentToolsEvent.OpenTool(DocumentTool.CORRECTIONS))
        viewModel.onEvent(DocumentToolsEvent.PreviewCorrections(
            "page-2", PdfPageLayout(straightenDegrees = 2f, rasterizeText = true),
        ))
        advanceUntilIdle()
        assertTrue(repository.calls.last().page.layout.rasterizeText)
        assertEquals(PdfImageFilter.ORIGINAL, repository.calls.last().page.layout.imageFilter)
        viewModel.onEvent(DocumentToolsEvent.ApplyCorrections(
            allPages = true, straightenDegrees = 2f, perspective = null,
            curvature = 0f, rasterizeText = true,
        ))
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.workspace!!.pages.all { it.layout.rasterizeText })
    }

    @Test fun emptyOrUnknownSelectionsDoNotFallbackToAllPages() = runTest(dispatcher) {
        viewModel.load(document)
        advanceUntilIdle()
        val original = viewModel.uiState.value.workspace
        val count = repository.calls.size
        for (ids in listOf(emptySet(), setOf("missing"))) {
            viewModel.onEvent(DocumentToolsEvent.ApplyFilter(PdfImageFilter.ENHANCE, true, pageIds = ids))
            viewModel.onEvent(DocumentToolsEvent.ApplyLayout(PageAlignment.TOP, PageScaleMode.FILL, true, pageIds = ids))
            viewModel.onEvent(DocumentToolsEvent.ApplyCorrections(true, 3f, null, .4f, pageIds = ids))
        }
        advanceUntilIdle()
        assertEquals(original, viewModel.uiState.value.workspace)
        assertEquals(count, repository.calls.size)
        assertFalse(viewModel.uiState.value.hasChanges)
        assertFalse(viewModel.uiState.value.canUndo)
    }

    @Test fun correctionSliderDebouncesAndNeverCommitsItsDraft() = runTest(dispatcher) {
        viewModel.load(document)
        advanceUntilIdle()
        val original = viewModel.uiState.value.workspace
        val count = repository.calls.size
        viewModel.onEvent(DocumentToolsEvent.OpenTool(DocumentTool.CORRECTIONS))
        for (angle in listOf(1f, 4f, 7f)) {
            viewModel.onEvent(DocumentToolsEvent.PreviewCorrections("page-1", PdfPageLayout(straightenDegrees = angle)))
            advanceTimeBy(40)
        }
        advanceTimeBy(79)
        runCurrent()
        assertEquals(count, repository.calls.size)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(count + 1, repository.calls.size)
        assertEquals(7f, repository.calls.last().page.layout.straightenDegrees, 0f)
        assertEquals(original, viewModel.uiState.value.workspace)
        assertFalse(viewModel.uiState.value.hasChanges)
        assertFalse(viewModel.uiState.value.canUndo)
        assertEquals("page-1", viewModel.uiState.value.correctionsPreviewPageId)
        assertFalse(viewModel.uiState.value.isPreviewingCorrections)
    }

    @Test fun draftOriginalComparisonUsesCommittedPageIncludingExistingFilter() = runTest(dispatcher) {
        viewModel.load(document)
        advanceUntilIdle()
        viewModel.onEvent(DocumentToolsEvent.ApplyFilter(PdfImageFilter.WHITEN, false, .6f))
        viewModel.onEvent(DocumentToolsEvent.ApplyCorrections(false, 1f, null, .1f))
        advanceUntilIdle()
        val committed = viewModel.uiState.value.workspace!!
        viewModel.onEvent(DocumentToolsEvent.OpenTool(DocumentTool.CORRECTIONS))
        viewModel.onEvent(DocumentToolsEvent.PreviewCorrections(
            "page-1", PdfPageLayout(straightenDegrees = 5f, curvature = -.3f),
        ))
        advanceUntilIdle()
        assertEquals(PdfImageFilter.WHITEN, repository.calls.last().page.layout.imageFilter)
        viewModel.onEvent(DocumentToolsEvent.PreviewCorrections(
            "page-1", PdfPageLayout(straightenDegrees = 5f), showOriginal = true,
        ))
        advanceUntilIdle()
        assertEquals(committed.pages.first(), repository.calls.last().page)
        assertTrue(viewModel.uiState.value.correctionsPreviewShowingOriginal)
        assertEquals(committed, viewModel.uiState.value.workspace)
    }

    @Test fun clearingAndDismissingCorrectionDraftPreserveCommittedRecipe() = runTest(dispatcher) {
        viewModel.load(document)
        advanceUntilIdle()
        val original = viewModel.uiState.value.workspace
        viewModel.onEvent(DocumentToolsEvent.OpenTool(DocumentTool.CORRECTIONS))
        viewModel.onEvent(DocumentToolsEvent.PreviewCorrections("page-1", PdfPageLayout(straightenDegrees = 5f)))
        advanceUntilIdle()
        viewModel.onEvent(DocumentToolsEvent.ClearCorrectionsPreview)
        assertNull(viewModel.uiState.value.correctionsPreview)
        assertNull(viewModel.uiState.value.correctionsPreviewPageId)
        assertEquals(original, viewModel.uiState.value.workspace)

        viewModel.onEvent(DocumentToolsEvent.PreviewCorrections("page-1", PdfPageLayout(curvature = .2f)))
        advanceUntilIdle()
        viewModel.onEvent(DocumentToolsEvent.DismissTool)
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.tool)
        assertNull(viewModel.uiState.value.correctionsPreview)
        assertEquals(original, viewModel.uiState.value.workspace)
        assertFalse(viewModel.uiState.value.hasChanges)
    }

    @Test fun committingDraftToSeveralPagesCreatesOnlyOneUndoStep() = runTest(dispatcher) {
        viewModel.load(document)
        advanceUntilIdle()
        val original = viewModel.uiState.value.workspace
        viewModel.onEvent(DocumentToolsEvent.OpenTool(DocumentTool.CORRECTIONS))
        viewModel.onEvent(DocumentToolsEvent.PreviewCorrections("page-1", PdfPageLayout(straightenDegrees = 2f)))
        advanceUntilIdle()
        viewModel.onEvent(DocumentToolsEvent.PreviewCorrections("page-1", PdfPageLayout(straightenDegrees = 6f)))
        advanceUntilIdle()
        viewModel.onEvent(DocumentToolsEvent.ApplyCorrections(
            false, 6f, null, 0f, pageIds = setOf("page-1", "page-3"),
        ))
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.correctionsPreview)
        assertNull(viewModel.uiState.value.tool)
        assertTrue(viewModel.uiState.value.canUndo)
        viewModel.onEvent(DocumentToolsEvent.Undo)
        advanceUntilIdle()
        assertEquals(original, viewModel.uiState.value.workspace)
        assertFalse(viewModel.uiState.value.canUndo)
    }

    @Test fun lateDraftResultCannotReplaceMoreRecentGesture() = runTest(dispatcher) {
        viewModel.load(document)
        advanceUntilIdle()
        viewModel.onEvent(DocumentToolsEvent.OpenTool(DocumentTool.CORRECTIONS))
        val gate = CompletableDeferred<Unit>()
        repository.nextGate = gate
        repository.ignoreNextCancellation = true
        viewModel.onEvent(DocumentToolsEvent.PreviewCorrections("page-1", PdfPageLayout(straightenDegrees = 1f)))
        advanceTimeBy(120)
        runCurrent()
        val staleUri = repository.calls.last().uri
        viewModel.onEvent(DocumentToolsEvent.PreviewCorrections("page-1", PdfPageLayout(straightenDegrees = 8f)))
        advanceTimeBy(120)
        runCurrent()
        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(8f, repository.calls.last().page.layout.straightenDegrees, 0f)
        assertEquals(repository.calls.last().uri, viewModel.uiState.value.correctionsPreview!!.uri)
        assertTrue(staleUri != viewModel.uiState.value.correctionsPreview!!.uri)
        assertFalse(viewModel.uiState.value.hasChanges)
    }

    @Test fun lockClearsDraftAndRejectsLateResult() = runTest(dispatcher) {
        passwords.unlock()
        viewModel.load(document.copy(protectionKeyId = "key"))
        advanceUntilIdle()
        val original = viewModel.uiState.value.workspace
        viewModel.onEvent(DocumentToolsEvent.OpenTool(DocumentTool.CORRECTIONS))
        val gate = CompletableDeferred<Unit>()
        repository.nextGate = gate
        repository.ignoreNextCancellation = true
        viewModel.onEvent(DocumentToolsEvent.PreviewCorrections("page-1", PdfPageLayout(straightenDegrees = 5f)))
        advanceTimeBy(120)
        runCurrent()
        passwords.lock()
        viewModel.onEvent(DocumentToolsEvent.Backgrounded)
        assertNull(viewModel.uiState.value.correctionsPreview)
        assertNull(viewModel.uiState.value.correctionsPreviewPageId)
        gate.complete(Unit)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isLocked)
        assertNull(viewModel.uiState.value.correctionsPreview)
        assertTrue(viewModel.uiState.value.pagePreviews.isEmpty())
        assertEquals(original, viewModel.uiState.value.workspace)
    }

    @Test fun allPagesFilterOnlyRendersSelectedAndVisiblePagesWithReferenceCounting() = runTest(dispatcher) {
        viewModel.load(document.copy(pageCount = 100))
        advanceUntilIdle()
        assertEquals(listOf("page-1"), repository.calls.map { it.page.id })
        viewModel.onEvent(DocumentToolsEvent.PageVisibility("page-50", true))
        viewModel.onEvent(DocumentToolsEvent.PageVisibility("page-50", true))
        advanceUntilIdle()
        assertEquals(listOf("page-1", "page-50"), repository.calls.map { it.page.id })
        viewModel.onEvent(DocumentToolsEvent.PageVisibility("page-50", false))
        val count = repository.calls.size
        viewModel.onEvent(DocumentToolsEvent.ApplyFilter(PdfImageFilter.ENHANCE, true))
        advanceUntilIdle()
        assertEquals(setOf("page-1", "page-50"), repository.calls.drop(count).map { it.page.id }.toSet())
        assertEquals(2, repository.calls.size - count)
        assertTrue(viewModel.uiState.value.workspace!!.pages.all { it.layout.imageFilter == PdfImageFilter.ENHANCE })

        viewModel.onEvent(DocumentToolsEvent.PageVisibility("page-50", false))
        val hiddenCount = repository.calls.size
        viewModel.onEvent(DocumentToolsEvent.ApplyFilter(PdfImageFilter.DENOISE, true))
        advanceUntilIdle()
        assertEquals(listOf("page-1"), repository.calls.drop(hiddenCount).map { it.page.id })
    }

    @Test fun correctionDraftRequiresItsToolAndValidExistingPage() = runTest(dispatcher) {
        viewModel.load(document)
        advanceUntilIdle()
        val count = repository.calls.size
        viewModel.onEvent(DocumentToolsEvent.PreviewCorrections("page-1", PdfPageLayout(straightenDegrees = 3f)))
        advanceUntilIdle()
        assertEquals(count, repository.calls.size)
        viewModel.onEvent(DocumentToolsEvent.OpenTool(DocumentTool.CORRECTIONS))
        viewModel.onEvent(DocumentToolsEvent.PreviewCorrections("missing", PdfPageLayout(straightenDegrees = 3f)))
        advanceUntilIdle()
        assertEquals(count, repository.calls.size)
        assertNull(viewModel.uiState.value.correctionsPreview)
    }

    @Test fun automaticImprovementAnalyzesOnlySelectedPageUnlessAllWasRequested() = runTest(dispatcher) {
        viewModel.load(document.copy(pageCount = 100))
        advanceUntilIdle()
        viewModel.onEvent(DocumentToolsEvent.SelectPage("page-73"))
        advanceUntilIdle()
        viewModel.onEvent(DocumentToolsEvent.AutoImprove(false))
        advanceUntilIdle()
        assertEquals(listOf("page-73"), repository.analyses.last().pages.map { it.id })
        assertEquals(1, viewModel.uiState.value.workspace!!.pages.count { it.layout.imageFilter == PdfImageFilter.ENHANCE })
        viewModel.onEvent(DocumentToolsEvent.AutoImprove(true))
        advanceUntilIdle()
        assertEquals(100, repository.analyses.last().pages.size)
    }

    @Test fun correctionsAnalyzeItsLocalPageWithoutChangingMainSelection() = runTest(dispatcher) {
        viewModel.load(document)
        advanceUntilIdle()
        viewModel.onEvent(DocumentToolsEvent.OpenTool(DocumentTool.CORRECTIONS))
        viewModel.onEvent(DocumentToolsEvent.AnalyzeCorrectionPage("page-4"))
        advanceUntilIdle()
        assertEquals(listOf("page-4"), repository.analyses.last().pages.map { it.id })
        assertEquals("page-1", viewModel.uiState.value.selectedPageId)
        assertFalse(viewModel.uiState.value.hasChanges)
    }

    @Test fun liveLayoutGesturesReusePixelsWithoutGeneratingPdfOrChangingRecipe() = runTest(dispatcher) {
        repository.supportsLivePreview = true
        viewModel.load(document)
        advanceUntilIdle()
        val original = viewModel.uiState.value.workspace
        val pdfCount = repository.calls.size
        viewModel.onEvent(DocumentToolsEvent.OpenTool(DocumentTool.LAYOUT))
        viewModel.onEvent(DocumentToolsEvent.PreviewLayout("page-1", PdfPageLayout(contentScale = .8f)))
        runCurrent()
        assertEquals(listOf(480), repository.pixelCalls.map { it.maxSide })
        assertNotNull(viewModel.uiState.value.layoutLivePreview.pixels)

        repeat(30) { index ->
            viewModel.onEvent(DocumentToolsEvent.PreviewLayout(
                "page-1", PdfPageLayout(
                    contentScale = .8f + index * .02f,
                    alignment = PageAlignment.BOTTOM,
                    rotationQuarterTurns = index % 4,
                ),
            ))
            advanceTimeBy(10)
        }
        advanceUntilIdle()

        assertEquals(listOf(480, 1100), repository.pixelCalls.map { it.maxSide })
        assertEquals(pdfCount, repository.calls.size)
        assertNull(viewModel.uiState.value.layoutPreview)
        assertFalse(viewModel.uiState.value.isPreviewingLayout)
        assertEquals(original, viewModel.uiState.value.workspace)
        assertFalse(viewModel.uiState.value.hasChanges)
        assertFalse(viewModel.uiState.value.canUndo)
    }

    @Test fun liveCorrectionsRenderLatestDraftAndApplyCreatesOnlyCommittedPdf() = runTest(dispatcher) {
        repository.supportsLivePreview = true
        viewModel.load(document)
        advanceUntilIdle()
        val original = viewModel.uiState.value.workspace
        val pdfCount = repository.calls.size
        viewModel.onEvent(DocumentToolsEvent.OpenTool(DocumentTool.CORRECTIONS))
        for (angle in listOf(1f, 2f, 3f)) {
            viewModel.onEvent(DocumentToolsEvent.PreviewCorrections(
                "page-1", PdfPageLayout(straightenDegrees = angle, rasterizeText = true),
            ))
            advanceTimeBy(40)
        }
        advanceUntilIdle()

        assertEquals(3f, repository.pixelCalls.last().page.layout.straightenDegrees, 0f)
        assertEquals(1100, repository.pixelCalls.last().maxSide)
        assertEquals(pdfCount, repository.calls.size)
        assertNotNull(viewModel.uiState.value.correctionsLivePreview.pixels)
        assertFalse(viewModel.uiState.value.isPreviewingCorrections)
        assertEquals(original, viewModel.uiState.value.workspace)

        viewModel.onEvent(DocumentToolsEvent.ApplyCorrections(false, 3f, null, 0f, rasterizeText = true))
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.correctionsLivePreview.pixels)
        assertEquals(pdfCount + 1, repository.calls.size)
        assertEquals(3f, viewModel.uiState.value.workspace!!.pages.first().layout.straightenDegrees, 0f)
        assertTrue(viewModel.uiState.value.hasChanges)
        assertTrue(viewModel.uiState.value.canUndo)
    }

    @Test fun switchingLiveToolPageClearsPreviousPixelsAndClosingClearsDraft() = runTest(dispatcher) {
        repository.supportsLivePreview = true
        viewModel.load(document)
        advanceUntilIdle()
        val original = viewModel.uiState.value.workspace
        viewModel.onEvent(DocumentToolsEvent.OpenTool(DocumentTool.LAYOUT))
        viewModel.onEvent(DocumentToolsEvent.PreviewLayout("page-1", PdfPageLayout()))
        advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.layoutLivePreview.pixels)

        viewModel.onEvent(DocumentToolsEvent.PreviewLayout("page-2", PdfPageLayout()))
        assertNull(viewModel.uiState.value.layoutLivePreview.pixels)
        advanceUntilIdle()
        assertEquals("page-2", viewModel.uiState.value.layoutLivePreview.pageId)
        assertEquals(1, viewModel.uiState.value.layoutLivePreview.pixels!!.pixels.single())

        viewModel.onEvent(DocumentToolsEvent.DismissTool)
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.layoutLivePreview.pixels)
        assertNull(viewModel.uiState.value.layoutLivePreview.pageId)
        assertEquals(original, viewModel.uiState.value.workspace)
    }

    @Test fun lockingDiscardsLateLivePixelsAndRetainedFrame() = runTest(dispatcher) {
        repository.supportsLivePreview = true
        passwords.unlock()
        viewModel.load(document.copy(protectionKeyId = "key"))
        advanceUntilIdle()
        viewModel.onEvent(DocumentToolsEvent.OpenTool(DocumentTool.CORRECTIONS))
        viewModel.onEvent(DocumentToolsEvent.PreviewCorrections("page-1", PdfPageLayout(straightenDegrees = 1f)))
        advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.correctionsLivePreview.pixels)
        val gate = CompletableDeferred<Unit>()
        repository.nextPixelGate = gate
        repository.ignorePixelCancellation = true
        viewModel.onEvent(DocumentToolsEvent.PreviewCorrections("page-1", PdfPageLayout(straightenDegrees = 4f)))
        runCurrent()

        passwords.lock()
        viewModel.onEvent(DocumentToolsEvent.Backgrounded)
        assertNull(viewModel.uiState.value.correctionsLivePreview.pixels)
        gate.complete(Unit)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isLocked)
        assertNull(viewModel.uiState.value.correctionsLivePreview.pixels)
        assertNull(viewModel.uiState.value.correctionsLivePreview.pageId)
        assertFalse(viewModel.uiState.value.isPreviewingCorrections)
    }

    private data class PixelCall(val page: PdfPageSpec, val maxSide: Int)
    private data class Call(val page: PdfPageSpec, val uri: String)

    private class InteractiveRepository : PdfEditorRepository {
        val calls = mutableListOf<Call>()
        val pixelCalls = mutableListOf<PixelCall>()
        var supportsLivePreview = false
        var nextPixelGate: CompletableDeferred<Unit>? = null
        var ignorePixelCancellation = false
        val analyses = mutableListOf<PdfWorkspace>()
        val saved = mutableListOf<PdfWorkspace>()
        var transformPage: (PdfPageSpec) -> PdfPageSpec = { it }
        var nextGate: CompletableDeferred<Unit>? = null
        var ignoreNextCancellation = false
        private lateinit var document: DocumentModel

        override suspend fun open(document: DocumentModel, password: String?): PdfWorkspace {
            this.document = document
            return PdfWorkspace(
                documentId = document.id, workspaceId = "workspace-${document.id}",
                directoryUri = "file:///workspace",
                pages = (1..document.pageCount).map { transformPage(PdfPageSpec("page-$it", "file:///source.pdf", it - 1)) },
                isProtected = document.protectionKeyId != null, protectionKeyId = document.protectionKeyId,
            )
        }
        override suspend fun renderPagePreview(
            workspace: PdfWorkspace, pageId: String, retainedPreviewUris: Set<String>,
        ): PdfPagePreview {
            val gate = nextGate.also { nextGate = null }
            val ignoreCancellation = ignoreNextCancellation.also { ignoreNextCancellation = false }
            val uri = "file:///page-${calls.size + 1}.pdf"
            calls += Call(workspace.pages.first { it.id == pageId }, uri)
            if (ignoreCancellation) withContext(NonCancellable) { gate?.await() } else gate?.await()
            return PdfPagePreview(uri, 0)
        }
        override suspend fun renderInteractivePixels(
            workspace: PdfWorkspace, pageId: String, maxSide: Int,
        ): PdfInteractivePixels? {
            if (!supportsLivePreview) return null
            val page = workspace.pages.first { it.id == pageId }
            pixelCalls += PixelCall(page, maxSide)
            val gate = nextPixelGate.also { nextPixelGate = null }
            val ignoreCancellation = ignorePixelCancellation.also { ignorePixelCancellation = false }
            if (ignoreCancellation) withContext(NonCancellable) { gate?.await() } else gate?.await()
            return PdfInteractivePixels(1, 1, intArrayOf(page.sourcePageIndex), 595f, 842f)
        }
        override suspend fun analyze(workspace: PdfWorkspace): PdfQualityReport {
            analyses += workspace
            return PdfQualityReport(workspace.pages.map { PdfPageQuality(it.id, suggestedStraightenDegrees = 2f) })
        }
        override suspend fun preview(workspace: PdfWorkspace, retainedPreviewUri: String?): String =
            error("A edição deve renderizar apenas a página solicitada")
        override suspend fun save(workspace: PdfWorkspace, password: String?, protectionKeyId: String?, removeProtection: Boolean): DocumentModel {
            saved += workspace
            return document
        }
        override suspend fun importPdf(uri: String, password: String?, protectionKeyId: String?): ScannedDocument = error("Unused")
        override suspend fun appendScans(workspace: PdfWorkspace, pageUris: List<String>): PdfWorkspace = error("Unused")
        override suspend fun appendPdfs(workspace: PdfWorkspace, sources: List<PdfMergeSource>): PdfWorkspace = error("Unused")
        override suspend fun copySignature(workspace: PdfWorkspace, imageUri: String): String = error("Unused")
        override suspend fun clearPreview(workspace: PdfWorkspace) = Unit
        override suspend fun discard(workspace: PdfWorkspace) = Unit
    }

    private class InteractivePasswords : PasswordAccess {
        override val state = MutableStateFlow(PasswordVaultState(isConfigured = true))
        override val activeKeyId = "key"
        fun unlock() { state.value = state.value.copy(isUnlocked = true) }
        override fun lock() { state.value = state.value.copy(isUnlocked = false) }
        override suspend fun <T> withPassword(keyId: String?, block: suspend (String) -> T): T {
            if (!state.value.isUnlocked) throw PasswordVaultLockedException()
            return block("secret")
        }
    }

    private class EmptySignatures : SignatureRepository {
        override fun observeSignatures() = MutableStateFlow(emptyList<SignatureModel>())
        override suspend fun createDrawing(name: String, strokes: List<SignatureStroke>): SignatureModel = error("Unused")
        override suspend fun createTyped(name: String, text: String, font: SignatureFont, colorArgb: Int): SignatureModel = error("Unused")
        override suspend fun importImage(name: String, uri: String, removeLightBackground: Boolean): SignatureModel = error("Unused")
        override suspend fun rename(id: String, name: String) = Unit
        override suspend fun delete(id: String) = Unit
    }
}
