package com.joseleandro.scaniqo.ui.documentEditor

import androidx.lifecycle.ViewModelStore
import com.joseleandro.scaniqo.core.security.PasswordAccess
import com.joseleandro.scaniqo.core.security.PasswordVaultLockedException
import com.joseleandro.scaniqo.core.security.PasswordVaultState
import com.joseleandro.scaniqo.domain.model.DocumentModel
import com.joseleandro.scaniqo.domain.model.PageAlignment
import com.joseleandro.scaniqo.domain.model.PdfPerspective
import com.joseleandro.scaniqo.domain.model.PdfSignaturePlacement
import com.joseleandro.scaniqo.domain.model.PdfFilterThumbnails
import com.joseleandro.scaniqo.domain.model.PdfImageFilter
import com.joseleandro.scaniqo.domain.model.PdfMergeSource
import com.joseleandro.scaniqo.domain.model.PdfPageLayout
import com.joseleandro.scaniqo.domain.model.PdfPagePreview
import com.joseleandro.scaniqo.domain.model.PdfPageQuality
import com.joseleandro.scaniqo.domain.model.PdfPageSpec
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DocumentFilterPreviewViewModelTest {
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


    @Test fun filterDraftPreservesGeometryAndDoesNotChangeCommittedRecipe() = runTest(dispatcher) {
        viewModel.load(document)
        advanceUntilIdle()
        viewModel.onEvent(DocumentToolsEvent.ApplyCorrections(false, 3f, null, .2f))
        advanceUntilIdle()
        val committed = viewModel.uiState.value.workspace
        viewModel.onEvent(DocumentToolsEvent.OpenTool(DocumentTool.FILTERS))
        viewModel.onEvent(DocumentToolsEvent.PreviewFilter("page-1",
            PdfPageLayout(imageFilter = PdfImageFilter.SHARPEN, filterIntensity = .4f, straightenDegrees = -9f)))
        advanceUntilIdle()
        val preview = repository.calls.last().page.layout
        assertEquals(PdfImageFilter.SHARPEN, preview.imageFilter)
        assertEquals(.4f, preview.filterIntensity, 0f)
        assertEquals(3f, preview.straightenDegrees, 0f)
        assertEquals(.2f, preview.curvature, 0f)
        assertEquals(committed, viewModel.uiState.value.workspace)
        assertEquals("page-1", viewModel.uiState.value.filterPreviewPageId)
    }

    @Test fun rapidFilterChangesPrepareOnlyLastDraft() = runTest(dispatcher) {
        viewModel.load(document)
        advanceUntilIdle()
        val count = repository.calls.size
        viewModel.onEvent(DocumentToolsEvent.OpenTool(DocumentTool.FILTERS))
        for (filter in listOf(PdfImageFilter.WHITEN, PdfImageFilter.DENOISE, PdfImageFilter.SHARPEN)) {
            viewModel.onEvent(DocumentToolsEvent.PreviewFilter("page-1", PdfPageLayout(imageFilter = filter)))
            advanceTimeBy(40)
        }
        advanceUntilIdle()
        assertEquals(count + 1, repository.calls.size)
        assertEquals(PdfImageFilter.SHARPEN, repository.calls.last().page.layout.imageFilter)
        assertFalse(viewModel.uiState.value.hasChanges)
        assertFalse(viewModel.uiState.value.canUndo)
    }

    @Test fun beforeComparisonRestoresCommittedFilterAndIntensity() = runTest(dispatcher) {
        viewModel.load(document)
        advanceUntilIdle()
        viewModel.onEvent(DocumentToolsEvent.ApplyFilter(PdfImageFilter.DENOISE, false, .6f))
        advanceUntilIdle()
        val committed = viewModel.uiState.value.workspace
        viewModel.onEvent(DocumentToolsEvent.OpenTool(DocumentTool.FILTERS))
        viewModel.onEvent(DocumentToolsEvent.PreviewFilter("page-1", PdfPageLayout(imageFilter = PdfImageFilter.SHARPEN)))
        advanceUntilIdle()
        viewModel.onEvent(DocumentToolsEvent.PreviewFilter("page-1",
            PdfPageLayout(imageFilter = PdfImageFilter.SHARPEN), showOriginal = true))
        advanceUntilIdle()
        assertEquals(PdfImageFilter.DENOISE, repository.calls.last().page.layout.imageFilter)
        assertEquals(.6f, repository.calls.last().page.layout.filterIntensity, 0f)
        assertTrue(viewModel.uiState.value.filterPreviewShowingOriginal)
        assertEquals(committed, viewModel.uiState.value.workspace)
    }

    @Test fun intensityChangesDoNotRebuildThumbnailBatch() = runTest(dispatcher) {
        viewModel.load(document)
        advanceUntilIdle()
        viewModel.onEvent(DocumentToolsEvent.OpenTool(DocumentTool.FILTERS))
        viewModel.onEvent(DocumentToolsEvent.LoadFilterThumbnails("page-1"))
        advanceUntilIdle()
        assertEquals(PdfImageFilter.entries.size, viewModel.uiState.value.filterThumbnails.size)
        assertTrue(viewModel.uiState.value.filterThumbnailsRequireRasterization)
        for (intensity in listOf(.2f, .5f, .8f)) {
            viewModel.onEvent(DocumentToolsEvent.PreviewFilter("page-1",
                PdfPageLayout(imageFilter = PdfImageFilter.ENHANCE, filterIntensity = intensity)))
            viewModel.onEvent(DocumentToolsEvent.LoadFilterThumbnails("page-1"))
            advanceUntilIdle()
        }
        assertEquals(1, repository.thumbnailCalls)
        viewModel.onEvent(DocumentToolsEvent.LoadFilterThumbnails("page-1", rasterizeText = true))
        advanceUntilIdle()
        assertEquals(2, repository.thumbnailCalls)
        assertEquals(listOf(false, true), repository.thumbnailRasterizationRequests)
        // A permissão muda a amostra; a fonte continua contendo texto ou vetores.
        assertTrue(viewModel.uiState.value.filterThumbnailsRequireRasterization)
    }

    @Test fun dismissCancelsPendingDraftAndThumbnailsWithoutCommitting() = runTest(dispatcher) {
        viewModel.load(document)
        advanceUntilIdle()
        val original = viewModel.uiState.value.workspace
        viewModel.onEvent(DocumentToolsEvent.OpenTool(DocumentTool.FILTERS))
        val gate = CompletableDeferred<Unit>()
        repository.thumbnailGate = gate
        viewModel.onEvent(DocumentToolsEvent.LoadFilterThumbnails("page-2"))
        viewModel.onEvent(DocumentToolsEvent.PreviewFilter("page-2", PdfPageLayout(imageFilter = PdfImageFilter.WHITEN)))
        runCurrent()
        viewModel.onEvent(DocumentToolsEvent.DismissTool)
        gate.complete(Unit)
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.filterPreview)
        assertNull(viewModel.uiState.value.filterThumbnailsPageId)
        assertTrue(viewModel.uiState.value.filterThumbnails.isEmpty())
        assertFalse(viewModel.uiState.value.isLoadingFilterThumbnails)
        assertEquals(original, viewModel.uiState.value.workspace)
    }

    @Test fun lockingRemovesFilterPreviewAndThumbnailUris() = runTest(dispatcher) {
        passwords.unlock()
        viewModel.load(document.copy(protectionKeyId = "key"))
        advanceUntilIdle()
        viewModel.onEvent(DocumentToolsEvent.OpenTool(DocumentTool.FILTERS))
        viewModel.onEvent(DocumentToolsEvent.LoadFilterThumbnails("page-1", rasterizeText = true))
        viewModel.onEvent(DocumentToolsEvent.PreviewFilter("page-1", PdfPageLayout(imageFilter = PdfImageFilter.WHITEN)))
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.filterThumbnails.isNotEmpty())
        assertTrue(viewModel.uiState.value.filterPreview != null)
        passwords.lock()
        viewModel.onEvent(DocumentToolsEvent.Backgrounded)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isLocked)
        assertTrue(viewModel.uiState.value.filterThumbnails.isEmpty())
        assertNull(viewModel.uiState.value.filterPreview)
        assertNull(viewModel.uiState.value.filterPreviewPageId)
    }

    @Test fun applyDraftToSelectedPagesClearsPreviewAndCreatesOnlyOneUndo() = runTest(dispatcher) {
        viewModel.load(document)
        advanceUntilIdle()
        val original = viewModel.uiState.value.workspace
        viewModel.onEvent(DocumentToolsEvent.OpenTool(DocumentTool.FILTERS))
        viewModel.onEvent(DocumentToolsEvent.PreviewFilter("page-4", PdfPageLayout(imageFilter = PdfImageFilter.ENHANCE)))
        advanceUntilIdle()
        viewModel.onEvent(DocumentToolsEvent.ApplyFilter(PdfImageFilter.ENHANCE, false,
            pageIds = setOf("page-2", "page-4")))
        advanceUntilIdle()
        assertEquals(setOf("page-2", "page-4"), viewModel.uiState.value.workspace!!.pages
            .filter { it.layout.imageFilter == PdfImageFilter.ENHANCE }.map { it.id }.toSet())
        assertNull(viewModel.uiState.value.filterPreview)
        assertNull(viewModel.uiState.value.tool)
        viewModel.onEvent(DocumentToolsEvent.Undo)
        advanceUntilIdle()
        assertEquals(original, viewModel.uiState.value.workspace)
        assertFalse(viewModel.uiState.value.canUndo)
    }

    @Test fun numberingIsGlobalForVisibleHiddenAndNewPagesAndPersistsDisabled() = runTest(dispatcher) {
        viewModel.load(document)
        advanceUntilIdle()
        viewModel.onEvent(DocumentToolsEvent.SelectPage("page-4"))
        viewModel.onEvent(DocumentToolsEvent.PageVisibility("page-2", true))
        advanceUntilIdle()
        var start = repository.calls.size
        viewModel.onEvent(DocumentToolsEvent.SetNumbering(true))
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.workspace!!.showPageNumbers)
        assertEquals(setOf("page-2", "page-4"), repository.calls.drop(start).map { it.page.id }.toSet())
        assertTrue(repository.calls.drop(start).all { it.numbering })
        viewModel.onEvent(DocumentToolsEvent.PageVisibility("page-5", true))
        advanceUntilIdle()
        assertEquals("page-5", repository.calls.last().page.id)
        assertTrue(repository.calls.last().numbering)
        viewModel.onEvent(DocumentToolsEvent.AddPages)
        viewModel.onEvent(DocumentToolsEvent.ScanCompleted(listOf("file:///scan.pdf")))
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.workspace!!.showPageNumbers)
        assertTrue(repository.calls.last { it.page.id == "new-0" }.numbering)
        start = repository.calls.size
        viewModel.onEvent(DocumentToolsEvent.SetNumbering(false))
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.workspace!!.showPageNumbers)
        assertTrue(repository.calls.drop(start).all { !it.numbering })
        viewModel.onEvent(DocumentToolsEvent.Save)
        advanceUntilIdle()
        assertFalse(repository.savedWorkspace!!.showPageNumbers)
        assertEquals(6, repository.savedWorkspace!!.pages.size)
    }

    @Test fun globalFilterUpdatesEveryPagePreservingGeometryAndSignatures() = runTest(dispatcher) {
        val placement = PdfSignaturePlacement("signature", "file:///signature.png")
        repository.initialPages = (1..5).map { index ->
            PdfPageSpec("page-$index", "file:///source.pdf", index - 1,
                layout = PdfPageLayout(
                    alignment = PageAlignment.BOTTOM, rotationQuarterTurns = index % 4,
                    contentScale = .8f, straightenDegrees = index.toFloat(),
                    curvature = .1f, imageFilter = PdfImageFilter.DENOISE, filterIntensity = .4f,
                ),
                signatures = listOf(placement),
            )
        }
        viewModel.load(document)
        advanceUntilIdle()
        viewModel.onEvent(DocumentToolsEvent.PageVisibility("page-3", true))
        advanceUntilIdle()
        val original = viewModel.uiState.value.workspace!!
        val start = repository.calls.size
        viewModel.onEvent(DocumentToolsEvent.OpenTool(DocumentTool.FILTERS))
        viewModel.onEvent(DocumentToolsEvent.SelectGlobalFilter(PdfImageFilter.WHITEN))
        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertEquals(DocumentTool.FILTERS, state.tool)
        assertEquals(PdfImageFilter.WHITEN, state.globalImageFilter)
        assertTrue(state.hasChanges)
        assertTrue(state.canEdit)
        state.workspace!!.pages.zip(original.pages).forEach { (changed, before) ->
            assertEquals(before.copy(layout = before.layout.copy(
                imageFilter = PdfImageFilter.WHITEN, filterIntensity = 1f, rasterizeText = true,
            )), changed)
        }
        assertEquals(setOf("page-1", "page-3"), repository.calls.drop(start).map { it.page.id }.toSet())
        assertTrue(repository.calls.drop(start).all { it.page.layout.imageFilter == PdfImageFilter.WHITEN })
        viewModel.onEvent(DocumentToolsEvent.Undo)
        advanceUntilIdle()
        assertEquals(original, viewModel.uiState.value.workspace)
        assertEquals(DocumentTool.FILTERS, viewModel.uiState.value.tool)
        assertFalse(viewModel.uiState.value.canUndo)
    }

    @Test fun rapidlySelectingGlobalFiltersRendersOnlyLatestChoice() = runTest(dispatcher) {
        viewModel.load(document)
        advanceUntilIdle()
        viewModel.onEvent(DocumentToolsEvent.OpenTool(DocumentTool.FILTERS))
        val start = repository.calls.size
        for (filter in listOf(PdfImageFilter.AUTO, PdfImageFilter.DENOISE, PdfImageFilter.SHARPEN)) {
            viewModel.onEvent(DocumentToolsEvent.SelectGlobalFilter(filter))
            advanceTimeBy(20)
        }
        advanceUntilIdle()
        assertEquals(start + 1, repository.calls.size)
        assertEquals(PdfImageFilter.SHARPEN, repository.calls.last().page.layout.imageFilter)
        assertTrue(viewModel.uiState.value.workspace!!.pages.all { it.layout.imageFilter == PdfImageFilter.SHARPEN })
        assertFalse(viewModel.uiState.value.isBusy)
        assertEquals(DocumentTool.FILTERS, viewModel.uiState.value.tool)
    }

    @Test fun globalSelectionRetainsThumbnailsAndDoesNotRebuildThem() = runTest(dispatcher) {
        viewModel.load(document)
        advanceUntilIdle()
        viewModel.onEvent(DocumentToolsEvent.OpenTool(DocumentTool.FILTERS))
        viewModel.onEvent(DocumentToolsEvent.LoadFilterThumbnails("page-1", rasterizeText = true))
        advanceUntilIdle()
        val thumbnails = viewModel.uiState.value.filterThumbnails
        for (filter in listOf(PdfImageFilter.AUTO, PdfImageFilter.ENHANCE, PdfImageFilter.ORIGINAL)) {
            viewModel.onEvent(DocumentToolsEvent.SelectGlobalFilter(filter))
            assertEquals(thumbnails, viewModel.uiState.value.filterThumbnails)
            viewModel.onEvent(DocumentToolsEvent.LoadFilterThumbnails("page-1", rasterizeText = true))
            advanceUntilIdle()
        }
        assertEquals(1, repository.thumbnailCalls)
        viewModel.onEvent(DocumentToolsEvent.Undo)
        advanceUntilIdle()
        assertEquals(thumbnails, viewModel.uiState.value.filterThumbnails)
        assertEquals(DocumentTool.FILTERS, viewModel.uiState.value.tool)
    }

    @Test fun originalRestoresNativeContentUnlessCorrectionsStillRequirePixels() = runTest(dispatcher) {
        val layouts = listOf(
            PdfPageLayout(straightenDegrees = 2f),
            PdfPageLayout(curvature = .2f),
            PdfPageLayout(perspective = PdfPerspective()),
            PdfPageLayout(rotationQuarterTurns = 1, contentScale = .7f),
            PdfPageLayout(),
        )
        repository.initialPages = layouts.mapIndexed { index, layout ->
            PdfPageSpec("page-${index + 1}", "file:///source.pdf", index, layout)
        }
        viewModel.load(document)
        advanceUntilIdle()
        viewModel.onEvent(DocumentToolsEvent.OpenTool(DocumentTool.FILTERS))
        viewModel.onEvent(DocumentToolsEvent.SelectGlobalFilter(PdfImageFilter.AUTO))
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.workspace!!.pages.all { it.layout.rasterizeText })
        viewModel.onEvent(DocumentToolsEvent.SelectGlobalFilter(PdfImageFilter.ORIGINAL))
        advanceUntilIdle()
        val result = viewModel.uiState.value.workspace!!.pages
        assertEquals(listOf(true, true, true, false, false), result.map { it.layout.rasterizeText })
        result.zip(layouts).forEach { (page, before) ->
            assertEquals(before.copy(rasterizeText = page.layout.rasterizeText), page.layout)
        }
        assertEquals(PdfImageFilter.ORIGINAL, viewModel.uiState.value.globalImageFilter)
    }

    @Test fun tappingFiltersAgainAndBackCloseTheStripWithoutDiscardingAppliedChoice() = runTest(dispatcher) {
        viewModel.load(document)
        advanceUntilIdle()
        viewModel.onEvent(DocumentToolsEvent.OpenTool(DocumentTool.FILTERS))
        viewModel.onEvent(DocumentToolsEvent.SelectGlobalFilter(PdfImageFilter.AUTO))
        advanceUntilIdle()
        viewModel.onEvent(DocumentToolsEvent.OpenTool(DocumentTool.FILTERS))
        assertNull(viewModel.uiState.value.tool)
        assertEquals(PdfImageFilter.AUTO, viewModel.uiState.value.globalImageFilter)
        viewModel.onEvent(DocumentToolsEvent.OpenTool(DocumentTool.FILTERS))
        viewModel.onEvent(DocumentToolsEvent.RequestBack)
        assertNull(viewModel.uiState.value.tool)
        assertFalse(viewModel.uiState.value.confirmDiscard)
        assertEquals(PdfImageFilter.AUTO, viewModel.uiState.value.globalImageFilter)
        viewModel.onEvent(DocumentToolsEvent.RequestBack)
        assertTrue(viewModel.uiState.value.confirmDiscard)
    }

    @Test fun mixedFilterAndIntensityDoNotHighlightAnIncorrectGlobalChoice() = runTest(dispatcher) {
        repository.initialPages = (1..5).map { index ->
            PdfPageSpec("page-$index", "file:///source.pdf", index - 1,
                PdfPageLayout(imageFilter = PdfImageFilter.WHITEN, filterIntensity = if (index == 3) .4f else 1f))
        }
        viewModel.load(document)
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.globalImageFilter)
        viewModel.onEvent(DocumentToolsEvent.OpenTool(DocumentTool.FILTERS))
        viewModel.onEvent(DocumentToolsEvent.SelectGlobalFilter(PdfImageFilter.WHITEN))
        advanceUntilIdle()
        assertEquals(PdfImageFilter.WHITEN, viewModel.uiState.value.globalImageFilter)
        viewModel.onEvent(DocumentToolsEvent.Undo)
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.globalImageFilter)
        viewModel.onEvent(DocumentToolsEvent.SelectGlobalFilter(PdfImageFilter.ORIGINAL))
        advanceUntilIdle()
        assertEquals(PdfImageFilter.ORIGINAL, viewModel.uiState.value.globalImageFilter)
    }

    @Test fun repeatingSameGlobalChoiceDoesNotCreateAnotherUndoStep() = runTest(dispatcher) {
        viewModel.load(document)
        advanceUntilIdle()
        val original = viewModel.uiState.value.workspace
        viewModel.onEvent(DocumentToolsEvent.OpenTool(DocumentTool.FILTERS))
        repeat(3) { viewModel.onEvent(DocumentToolsEvent.SelectGlobalFilter(PdfImageFilter.SHARPEN)) }
        advanceUntilIdle()
        viewModel.onEvent(DocumentToolsEvent.Undo)
        advanceUntilIdle()
        assertEquals(original, viewModel.uiState.value.workspace)
        assertFalse(viewModel.uiState.value.canUndo)
    }

    @Test fun failedGlobalPreviewCanRetryWithoutLosingTheAppliedRecipe() = runTest(dispatcher) {
        viewModel.load(document)
        advanceUntilIdle()
        val previous = viewModel.uiState.value.previewUri
        viewModel.onEvent(DocumentToolsEvent.OpenTool(DocumentTool.FILTERS))
        repository.failNextPreview = true
        viewModel.onEvent(DocumentToolsEvent.SelectGlobalFilter(PdfImageFilter.ENHANCE))
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.previewError)
        assertEquals(previous, viewModel.uiState.value.previewUri)
        assertEquals(PdfImageFilter.ENHANCE, viewModel.uiState.value.globalImageFilter)
        assertTrue(viewModel.uiState.value.canEdit)
        viewModel.onEvent(DocumentToolsEvent.RetryPagePreview("page-1"))
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.previewError)
        assertEquals(PdfImageFilter.ENHANCE, repository.calls.last().page.layout.imageFilter)
        assertEquals(DocumentTool.FILTERS, viewModel.uiState.value.tool)
    }

    @Test fun globalSelectionIsIgnoredOutsideFiltersAndWhileSavingOrLocked() = runTest(dispatcher) {
        passwords.unlock()
        viewModel.load(document.copy(protectionKeyId = "key"))
        advanceUntilIdle()
        val original = viewModel.uiState.value.workspace
        viewModel.onEvent(DocumentToolsEvent.SelectGlobalFilter(PdfImageFilter.AUTO))
        assertEquals(original, viewModel.uiState.value.workspace)
        viewModel.onEvent(DocumentToolsEvent.OpenTool(DocumentTool.FILTERS))
        viewModel.onEvent(DocumentToolsEvent.SelectGlobalFilter(PdfImageFilter.AUTO))
        advanceUntilIdle()
        viewModel.onEvent(DocumentToolsEvent.Save)
        viewModel.onEvent(DocumentToolsEvent.SelectGlobalFilter(PdfImageFilter.DENOISE))
        advanceUntilIdle()
        assertTrue(repository.savedWorkspace!!.pages.all { it.layout.imageFilter == PdfImageFilter.AUTO })
        assertFalse(viewModel.uiState.value.hasChanges)
        viewModel.onEvent(DocumentToolsEvent.OpenTool(DocumentTool.FILTERS))
        passwords.lock()
        viewModel.onEvent(DocumentToolsEvent.Backgrounded)
        advanceUntilIdle()
        val locked = viewModel.uiState.value.workspace
        viewModel.onEvent(DocumentToolsEvent.SelectGlobalFilter(PdfImageFilter.WHITEN))
        assertEquals(locked, viewModel.uiState.value.workspace)
        assertTrue(viewModel.uiState.value.isLocked)
    }

    private data class Call(val page: PdfPageSpec, val uri: String, val numbering: Boolean)

    private class InteractiveRepository : PdfEditorRepository {
        val calls = mutableListOf<Call>()
        var thumbnailCalls = 0
        val thumbnailRasterizationRequests = mutableListOf<Boolean>()
        var initialPages: List<PdfPageSpec>? = null
        var failNextPreview = false
        var thumbnailGate: CompletableDeferred<Unit>? = null
        var savedWorkspace: PdfWorkspace? = null
        val analyses = mutableListOf<PdfWorkspace>()
        var nextGate: CompletableDeferred<Unit>? = null
        var ignoreNextCancellation = false
        private lateinit var document: DocumentModel

        override suspend fun open(document: DocumentModel, password: String?): PdfWorkspace {
            this.document = document
            return PdfWorkspace(
                documentId = document.id, workspaceId = "workspace-${document.id}",
                directoryUri = "file:///workspace",
                pages = initialPages ?: (1..document.pageCount).map { PdfPageSpec("page-$it", "file:///source.pdf", it - 1) },
                isProtected = document.protectionKeyId != null, protectionKeyId = document.protectionKeyId,
            )
        }
        override suspend fun renderPagePreview(
            workspace: PdfWorkspace, pageId: String, retainedPreviewUris: Set<String>,
        ): PdfPagePreview {
            if (failNextPreview) {
                failNextPreview = false
                error("Falha simulada ao gerar a prévia")
            }
            val gate = nextGate.also { nextGate = null }
            val ignoreCancellation = ignoreNextCancellation.also { ignoreNextCancellation = false }
            val uri = "file:///page-${calls.size + 1}.pdf"
            calls += Call(workspace.pages.first { it.id == pageId }, uri, workspace.showPageNumbers)
            if (ignoreCancellation) withContext(NonCancellable) { gate?.await() } else gate?.await()
            return PdfPagePreview(uri, 0)
        }
        override suspend fun filterThumbnails(workspace: PdfWorkspace, pageId: String, rasterizeText: Boolean, force: Boolean): PdfFilterThumbnails {
            thumbnailCalls++
            thumbnailRasterizationRequests += rasterizeText
            thumbnailGate?.await()
            return PdfFilterThumbnails(
                PdfImageFilter.entries.associateWith { "file:///$pageId-${it.name}.jpg" },
                requiresRasterization = true,
            )
        }
        override suspend fun analyze(workspace: PdfWorkspace): PdfQualityReport {
            analyses += workspace
            return PdfQualityReport(workspace.pages.map { PdfPageQuality(it.id, suggestedStraightenDegrees = 2f) })
        }
        override suspend fun preview(workspace: PdfWorkspace, retainedPreviewUri: String?): String =
            error("A edição deve renderizar apenas a página solicitada")
        override suspend fun save(workspace: PdfWorkspace, password: String?, protectionKeyId: String?, removeProtection: Boolean): DocumentModel {
            savedWorkspace = workspace
            return document
        }
        override suspend fun importPdf(uri: String, password: String?, protectionKeyId: String?): ScannedDocument = error("Unused")
        override suspend fun appendScans(workspace: PdfWorkspace, pageUris: List<String>): PdfWorkspace =
            workspace.copy(pages = workspace.pages + pageUris.mapIndexed { index, uri ->
                PdfPageSpec("new-$index", uri, 0)
            })
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
