package com.joseleandro.scaniqo.ui.documentEditor

import androidx.lifecycle.ViewModelStore
import com.joseleandro.scaniqo.core.security.PasswordAccess
import com.joseleandro.scaniqo.core.security.PasswordVaultLockedException
import com.joseleandro.scaniqo.core.security.PasswordVaultState
import com.joseleandro.scaniqo.domain.model.DocumentModel
import com.joseleandro.scaniqo.domain.model.PageAlignment
import com.joseleandro.scaniqo.domain.model.PageScaleMode
import com.joseleandro.scaniqo.domain.model.PdfExportQuality
import com.joseleandro.scaniqo.domain.model.PdfImageFilter
import com.joseleandro.scaniqo.domain.model.PdfMergeSource
import com.joseleandro.scaniqo.domain.model.PdfPageLayout
import com.joseleandro.scaniqo.domain.model.PdfPageQuality
import com.joseleandro.scaniqo.domain.model.PdfPageSpec
import com.joseleandro.scaniqo.domain.model.PdfPerspective
import com.joseleandro.scaniqo.domain.model.PdfPoint
import com.joseleandro.scaniqo.domain.model.PdfQualityReport
import com.joseleandro.scaniqo.domain.model.PdfSignaturePlacement
import com.joseleandro.scaniqo.domain.model.PdfWorkspace
import com.joseleandro.scaniqo.domain.model.ScannedDocument
import com.joseleandro.scaniqo.domain.model.SignatureFont
import com.joseleandro.scaniqo.domain.model.SignatureModel
import com.joseleandro.scaniqo.domain.model.SignatureStroke
import com.joseleandro.scaniqo.domain.repository.PdfEditorRepository
import com.joseleandro.scaniqo.domain.repository.SignatureRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Verifica o contrato de edição sem depender de renderização Android ou reconhecimento de texto. */
@OptIn(ExperimentalCoroutinesApi::class)
class DocumentQualityViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val repository = QualityRepository()
    private val passwords = QualityPasswords()
    private val document = DocumentModel(
        id = 41, name = "Relatório", pdfUri = "file:///document.pdf", pageCount = 3,
        createdAt = 1L, sizeBytes = 300L,
    )
    private val viewModel by lazy {
        DocumentToolsViewModel(repository, EmptySignatures(), passwords)
            .also { store.put("quality", it) }
    }

    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() {
        store.clear()
        dispatcher.scheduler.runCurrent()
        Dispatchers.resetMain()
    }

    @Test fun filterScopeKeepsOtherPagesAndPersistsIntensityAndExplicitRasterization() = runTest(dispatcher) {
        viewModel.load(document)
        advanceUntilIdle()
        viewModel.onEvent(DocumentToolsEvent.SelectPage("page-2"))
        viewModel.onEvent(DocumentToolsEvent.ApplyFilter(
            PdfImageFilter.SHADOW_REMOVAL, allPages = false, intensity = .4f, rasterizeText = true,
        ))
        advanceUntilIdle()

        val pages = viewModel.uiState.value.workspace!!.pages
        assertEquals(PdfPageLayout(), pages[0].layout)
        assertEquals(PdfPageLayout(), pages[2].layout)
        assertEquals(PdfImageFilter.SHADOW_REMOVAL, pages[1].layout.imageFilter)
        assertEquals(.4f, pages[1].layout.filterIntensity, 0f)
        assertTrue(pages[1].layout.rasterizeText)
        assertTrue(repository.saves.isEmpty())

        viewModel.onEvent(DocumentToolsEvent.ApplyFilter(
            PdfImageFilter.DENOISE, allPages = true, intensity = 4f,
        ))
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.workspace!!.pages.all {
            it.layout.imageFilter == PdfImageFilter.DENOISE &&
                it.layout.filterIntensity == 1f && !it.layout.rasterizeText
        })
    }

    @Test fun nonFiniteFilterIntensityRetainsPreviousValidIntensity() = runTest(dispatcher) {
        viewModel.load(document)
        advanceUntilIdle()
        viewModel.onEvent(DocumentToolsEvent.ApplyFilter(PdfImageFilter.WHITEN, true, .6f))
        advanceUntilIdle()
        for (intensity in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            viewModel.onEvent(DocumentToolsEvent.ApplyFilter(PdfImageFilter.ENHANCE, true, intensity))
        }
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.workspace!!.pages.all {
            it.layout.imageFilter == PdfImageFilter.ENHANCE && it.layout.filterIntensity == .6f
        })
        assertTrue(repository.saves.isEmpty())
    }

    @Test fun correctionsApplyOnlyToChosenPageAndKeepLayoutAndFilter() = runTest(dispatcher) {
        viewModel.load(document)
        advanceUntilIdle()
        viewModel.onEvent(DocumentToolsEvent.SelectPage("page-2"))
        viewModel.onEvent(DocumentToolsEvent.ApplyLayout(
            PageAlignment.RIGHT, PageScaleMode.FILL, false, rotationQuarterTurns = 1, contentScale = .8f,
        ))
        viewModel.onEvent(DocumentToolsEvent.ApplyFilter(PdfImageFilter.ILLUMINATION, false, .6f))
        val perspective = PdfPerspective(topLeft = PdfPoint(.1f, .1f), topRight = PdfPoint(.9f, .1f))
        viewModel.onEvent(DocumentToolsEvent.ApplyCorrections(
            allPages = false, straightenDegrees = -4f, perspective = perspective,
            curvature = -.3f, rasterizeText = true,
        ))
        advanceUntilIdle()

        val pages = viewModel.uiState.value.workspace!!.pages
        assertEquals(PdfPageLayout(), pages[0].layout)
        assertEquals(PdfPageLayout(), pages[2].layout)
        val layout = pages[1].layout
        assertEquals(PageAlignment.RIGHT, layout.alignment)
        assertEquals(PageScaleMode.FILL, layout.scaleMode)
        assertEquals(1, layout.rotationQuarterTurns)
        assertEquals(.8f, layout.contentScale, 0f)
        assertEquals(PdfImageFilter.ILLUMINATION, layout.imageFilter)
        assertEquals(.6f, layout.filterIntensity, 0f)
        assertEquals(-4f, layout.straightenDegrees, 0f)
        assertEquals(perspective, layout.perspective)
        assertEquals(-.3f, layout.curvature, 0f)
        assertTrue(layout.rasterizeText)
    }

    @Test fun invalidCornersAndNonFiniteCorrectionsCannotReplaceValidRecipe() = runTest(dispatcher) {
        viewModel.load(document)
        advanceUntilIdle()
        val original = viewModel.uiState.value.workspace
        val crossed = PdfPerspective(
            PdfPoint(0f, 0f), PdfPoint(1f, 1f), PdfPoint(1f, 0f), PdfPoint(0f, 1f),
        )
        val events = listOf(
            DocumentToolsEvent.ApplyCorrections(true, 2f, crossed, 0f),
            DocumentToolsEvent.ApplyCorrections(true, Float.NaN, null, 0f),
            DocumentToolsEvent.ApplyCorrections(true, 0f, null, Float.POSITIVE_INFINITY),
            DocumentToolsEvent.ApplyCorrections(true, 0f, PdfPerspective(topLeft = PdfPoint(-.2f, 0f)), 0f),
        )
        events.forEach(viewModel::onEvent)
        advanceUntilIdle()
        assertEquals(original, viewModel.uiState.value.workspace)
        assertFalse(viewModel.uiState.value.hasChanges)
        assertFalse(viewModel.uiState.value.canUndo)
    }

    @Test fun automaticImprovementUsesSuggestedAngleOnlyInSelectedScope() = runTest(dispatcher) {
        viewModel.load(document)
        advanceUntilIdle()
        viewModel.onEvent(DocumentToolsEvent.SelectPage("page-2"))
        val perspective = PdfPerspective(topLeft = PdfPoint(.08f, .06f))
        viewModel.onEvent(DocumentToolsEvent.ApplyCorrections(false, 1f, perspective, .2f, true))
        viewModel.onEvent(DocumentToolsEvent.ApplyLayout(PageAlignment.BOTTOM, PageScaleMode.FILL, false, 2, .7f))
        viewModel.onEvent(DocumentToolsEvent.ApplyFilter(PdfImageFilter.WHITEN, false, .3f, true))
        advanceUntilIdle()
        val before = viewModel.uiState.value.workspace!!
        repository.report = PdfQualityReport(listOf(
            PdfPageQuality("page-1", suggestedStraightenDegrees = 5f),
            PdfPageQuality("page-2", suggestedStraightenDegrees = -3.5f),
            PdfPageQuality("page-3", suggestedStraightenDegrees = 2f),
        ))

        viewModel.onEvent(DocumentToolsEvent.AutoImprove(allPages = false))
        advanceUntilIdle()
        val after = viewModel.uiState.value.workspace!!
        assertEquals(before.pages[0], after.pages[0])
        assertEquals(before.pages[2], after.pages[2])
        assertEquals(before.pages[1].layout.copy(
            imageFilter = PdfImageFilter.ENHANCE, filterIntensity = 1f, straightenDegrees = -3.5f,
        ), after.pages[1].layout)
        assertEquals(before.pages[1].signatures, after.pages[1].signatures)
        assertTrue(repository.saves.isEmpty())
        assertFalse(viewModel.uiState.value.isAnalyzing)
    }

    @Test fun automaticImprovementCanUseDifferentAnglesForEveryPage() = runTest(dispatcher) {
        viewModel.load(document)
        advanceUntilIdle()
        repository.report = PdfQualityReport(listOf(
            PdfPageQuality("page-1", suggestedStraightenDegrees = -2f),
            PdfPageQuality("page-2", suggestedStraightenDegrees = 4f),
            PdfPageQuality("page-3", suggestedStraightenDegrees = 0f),
        ))
        viewModel.onEvent(DocumentToolsEvent.AutoImprove(allPages = true))
        advanceUntilIdle()
        val layouts = viewModel.uiState.value.workspace!!.pages.map { it.layout }
        assertTrue(layouts.all { it.imageFilter == PdfImageFilter.ENHANCE && it.filterIntensity == 1f })
        assertEquals(listOf(-2f, 4f, 0f), layouts.map { it.straightenDegrees })
    }

    @Test fun exportChoicesReachSaveAndBecomeNewUndoBaseline() = runTest(dispatcher) {
        viewModel.load(document)
        advanceUntilIdle()
        viewModel.onEvent(DocumentToolsEvent.SetExportOptions(PdfExportQuality.SMALL, ocrEnabled = true))
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.hasChanges)
        assertTrue(viewModel.uiState.value.canUndo)
        viewModel.onEvent(DocumentToolsEvent.Save)
        advanceUntilIdle()

        assertEquals(PdfExportQuality.SMALL, repository.saves.single().exportQuality)
        assertTrue(repository.saves.single().ocrEnabled)
        assertEquals(PdfExportQuality.SMALL, viewModel.uiState.value.workspace!!.exportQuality)
        assertTrue(viewModel.uiState.value.workspace!!.ocrEnabled)
        assertFalse(viewModel.uiState.value.hasChanges)
        assertFalse(viewModel.uiState.value.canUndo)
        val saved = viewModel.uiState.value.workspace
        viewModel.onEvent(DocumentToolsEvent.Undo)
        advanceUntilIdle()
        assertEquals(saved, viewModel.uiState.value.workspace)
    }

    @Test fun comparisonOnlyChangesPreviewKeepingPagesOrderSignaturesAndLayout() = runTest(dispatcher) {
        viewModel.load(document)
        advanceUntilIdle()
        viewModel.onEvent(DocumentToolsEvent.ReorderPages(listOf("page-3", "page-1", "page-2")))
        viewModel.onEvent(DocumentToolsEvent.SetNumbering(true))
        viewModel.onEvent(DocumentToolsEvent.SetExportOptions(PdfExportQuality.BALANCED, true))
        viewModel.onEvent(DocumentToolsEvent.ApplyLayout(PageAlignment.LEFT, PageScaleMode.FILL, true, 1, .75f))
        viewModel.onEvent(DocumentToolsEvent.ApplyCorrections(true, 3f, PdfPerspective(), .25f))
        viewModel.onEvent(DocumentToolsEvent.ApplyFilter(PdfImageFilter.SHARPEN, true, .8f))
        advanceUntilIdle()
        val edited = viewModel.uiState.value.workspace!!

        viewModel.onEvent(DocumentToolsEvent.SetCompareOriginal(true))
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isComparingOriginal)
        assertEquals(edited, viewModel.uiState.value.workspace)
        val preview = repository.previews.last()
        assertEquals(edited.pages.map { it.id }, preview.pages.map { it.id })
        assertEquals(edited.pages.map { it.signatures }, preview.pages.map { it.signatures })
        assertEquals(edited.showPageNumbers, preview.showPageNumbers)
        assertEquals(edited.exportQuality, preview.exportQuality)
        assertEquals(edited.ocrEnabled, preview.ocrEnabled)
        preview.pages.forEachIndexed { index, page ->
            if (page.id == viewModel.uiState.value.selectedPageId) {
                assertEquals(PdfImageFilter.ORIGINAL, page.layout.imageFilter)
                assertEquals(0f, page.layout.straightenDegrees, 0f)
                assertNull(page.layout.perspective)
                assertEquals(0f, page.layout.curvature, 0f)
                assertEquals(edited.pages[index].layout.alignment, page.layout.alignment)
                assertEquals(edited.pages[index].layout.rotationQuarterTurns, page.layout.rotationQuarterTurns)
                assertEquals(edited.pages[index].layout.contentScale, page.layout.contentScale, 0f)
            } else {
                assertEquals(edited.pages[index], page)
            }
        }
        viewModel.onEvent(DocumentToolsEvent.SetCompareOriginal(false))
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isComparingOriginal)
        assertEquals(edited, repository.previews.last())
        assertTrue(repository.saves.isEmpty())
    }

    @Test fun undoRestoresWholeRecipeUntilBaselineWithoutSaving() = runTest(dispatcher) {
        viewModel.load(document)
        advanceUntilIdle()
        val original = viewModel.uiState.value.workspace
        viewModel.onEvent(DocumentToolsEvent.ApplyFilter(PdfImageFilter.ENHANCE, true))
        advanceUntilIdle()
        val filtered = viewModel.uiState.value.workspace
        viewModel.onEvent(DocumentToolsEvent.SetExportOptions(PdfExportQuality.SMALL, true))
        advanceUntilIdle()

        viewModel.onEvent(DocumentToolsEvent.Undo)
        advanceUntilIdle()
        assertEquals(filtered, viewModel.uiState.value.workspace)
        assertTrue(viewModel.uiState.value.canUndo)
        viewModel.onEvent(DocumentToolsEvent.Undo)
        advanceUntilIdle()
        assertEquals(original, viewModel.uiState.value.workspace)
        assertFalse(viewModel.uiState.value.canUndo)
        assertFalse(viewModel.uiState.value.hasChanges)
        assertTrue(repository.saves.isEmpty())
    }

    @Test fun undoHistoryKeepsOnlyTwentyMostRecentEdits() = runTest(dispatcher) {
        viewModel.load(document)
        advanceUntilIdle()
        val states = mutableListOf(viewModel.uiState.value.workspace!!)
        repeat(30) { index ->
            viewModel.onEvent(DocumentToolsEvent.ApplyLayout(
                PageAlignment.CENTER, PageScaleMode.FIT, true, contentScale = .25f + index * .05f,
            ))
            states += viewModel.uiState.value.workspace!!
        }
        advanceUntilIdle()
        repeat(20) { viewModel.onEvent(DocumentToolsEvent.Undo) }
        advanceUntilIdle()
        assertEquals(states[10], viewModel.uiState.value.workspace)
        assertFalse(viewModel.uiState.value.canUndo)
    }

    @Test fun analysisPublishesSuggestionsWithoutDeletingPagesOrChangingRecipe() = runTest(dispatcher) {
        viewModel.load(document)
        advanceUntilIdle()
        val original = viewModel.uiState.value.workspace
        repository.report = PdfQualityReport(listOf(
            PdfPageQuality("page-1", isBlank = true),
            PdfPageQuality("page-2", duplicateOfPageId = "page-1", isBlurry = true),
            PdfPageQuality("page-3", isLowResolution = true, mayBeClipped = true),
        ))
        viewModel.onEvent(DocumentToolsEvent.AnalyzeQuality)
        advanceUntilIdle()

        assertEquals(repository.report, viewModel.uiState.value.qualityReport)
        assertEquals(original, viewModel.uiState.value.workspace)
        assertFalse(viewModel.uiState.value.isAnalyzing)
        assertFalse(viewModel.uiState.value.analysisError)
        assertFalse(viewModel.uiState.value.hasChanges)
        assertFalse(viewModel.uiState.value.canUndo)
    }

    @Test fun analysisErrorCanBeRetriedWithoutLosingCurrentEdits() = runTest(dispatcher) {
        viewModel.load(document)
        advanceUntilIdle()
        viewModel.onEvent(DocumentToolsEvent.ApplyFilter(PdfImageFilter.WHITEN, false))
        advanceUntilIdle()
        val edited = viewModel.uiState.value.workspace
        repository.failAnalysis = true
        viewModel.onEvent(DocumentToolsEvent.AnalyzeQuality)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.analysisError)
        assertFalse(viewModel.uiState.value.isBusy)
        assertEquals(edited, viewModel.uiState.value.workspace)

        repository.failAnalysis = false
        viewModel.onEvent(DocumentToolsEvent.AnalyzeQuality)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.analysisError)
        assertEquals(repository.report, viewModel.uiState.value.qualityReport)
        assertEquals(edited, viewModel.uiState.value.workspace)
    }

    @Test fun cancellingAnalysisStopsWorkAndDoesNotApplyLateAutomaticChanges() = runTest(dispatcher) {
        viewModel.load(document)
        advanceUntilIdle()
        val original = viewModel.uiState.value.workspace
        val gate = CompletableDeferred<Unit>()
        repository.analysisGate = gate
        viewModel.onEvent(DocumentToolsEvent.AutoImprove(true))
        runCurrent()
        assertTrue(viewModel.uiState.value.isAnalyzing)
        assertFalse(viewModel.uiState.value.canEdit)
        viewModel.onEvent(DocumentToolsEvent.CancelAnalysis)
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, repository.analysisCancellations)
        assertFalse(viewModel.uiState.value.isAnalyzing)
        assertFalse(viewModel.uiState.value.analysisError)
        assertNull(viewModel.uiState.value.qualityReport)
        assertEquals(original, viewModel.uiState.value.workspace)
        assertFalse(viewModel.uiState.value.hasChanges)
    }

    @Test fun busyAnalysisRejectsMutationsDuplicateRequestsAndSave() = runTest(dispatcher) {
        viewModel.load(document)
        advanceUntilIdle()
        val original = viewModel.uiState.value.workspace
        repository.analysisGate = CompletableDeferred()
        viewModel.onEvent(DocumentToolsEvent.AnalyzeQuality)
        runCurrent()
        viewModel.onEvent(DocumentToolsEvent.AnalyzeQuality)
        viewModel.onEvent(DocumentToolsEvent.AutoImprove(true))
        viewModel.onEvent(DocumentToolsEvent.ApplyFilter(PdfImageFilter.ENHANCE, true))
        viewModel.onEvent(DocumentToolsEvent.ApplyCorrections(true, 3f, null, .2f))
        viewModel.onEvent(DocumentToolsEvent.SetExportOptions(PdfExportQuality.SMALL, true))
        viewModel.onEvent(DocumentToolsEvent.SetNumbering(true))
        viewModel.onEvent(DocumentToolsEvent.Undo)
        viewModel.onEvent(DocumentToolsEvent.Save)
        runCurrent()
        assertEquals(1, repository.analysisCalls)
        assertEquals(original, viewModel.uiState.value.workspace)
        assertTrue(repository.saves.isEmpty())
        viewModel.onEvent(DocumentToolsEvent.CancelAnalysis)
        advanceUntilIdle()
    }

    @Test fun switchingDocumentCancelsAnalysisAndDoesNotLeakReportIntoNewDocument() = runTest(dispatcher) {
        viewModel.load(document)
        advanceUntilIdle()
        val gate = CompletableDeferred<Unit>()
        repository.analysisGate = gate
        viewModel.onEvent(DocumentToolsEvent.AnalyzeQuality)
        runCurrent()
        viewModel.load(document.copy(id = 42, pageCount = 1))
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, repository.analysisCancellations)
        assertEquals(42L, viewModel.uiState.value.workspace!!.documentId)
        assertEquals(1, viewModel.uiState.value.workspace!!.pages.size)
        assertNull(viewModel.uiState.value.qualityReport)
        assertFalse(viewModel.uiState.value.isAnalyzing)
        assertFalse(viewModel.uiState.value.hasChanges)
    }

    @Test fun lockingProtectedDocumentCancelsAnalysisAndBlocksEditing() = runTest(dispatcher) {
        passwords.unlock()
        viewModel.load(document.copy(protectionKeyId = "key"))
        advanceUntilIdle()
        val original = viewModel.uiState.value.workspace
        repository.analysisGate = CompletableDeferred()
        viewModel.onEvent(DocumentToolsEvent.AnalyzeQuality)
        runCurrent()
        passwords.lock()
        viewModel.onEvent(DocumentToolsEvent.Backgrounded)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isLocked)
        assertFalse(viewModel.uiState.value.isAnalyzing)
        assertNull(viewModel.uiState.value.qualityReport)
        assertEquals(1, repository.analysisCancellations)

        viewModel.onEvent(DocumentToolsEvent.AnalyzeQuality)
        viewModel.onEvent(DocumentToolsEvent.AutoImprove(true))
        viewModel.onEvent(DocumentToolsEvent.ApplyFilter(PdfImageFilter.ENHANCE, true))
        viewModel.onEvent(DocumentToolsEvent.SetExportOptions(PdfExportQuality.SMALL, true))
        viewModel.onEvent(DocumentToolsEvent.SetCompareOriginal(true))
        viewModel.onEvent(DocumentToolsEvent.Undo)
        advanceUntilIdle()
        assertEquals(1, repository.analysisCalls)
        assertEquals(original, viewModel.uiState.value.workspace)
        assertFalse(viewModel.uiState.value.isComparingOriginal)
        assertTrue(repository.saves.isEmpty())
    }

    @Test fun signatureDraftBlocksQualityUndoComparisonAndSaveUntilPlacementEnds() = runTest(dispatcher) {
        viewModel.load(document)
        advanceUntilIdle()
        viewModel.onEvent(DocumentToolsEvent.ApplyFilter(PdfImageFilter.WHITEN, true))
        advanceUntilIdle()
        val edited = viewModel.uiState.value.workspace
        viewModel.onEvent(DocumentToolsEvent.EditSignature("page-1", "signature"))
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.signatureDraft != null)
        viewModel.onEvent(DocumentToolsEvent.ApplyFilter(PdfImageFilter.ENHANCE, true))
        viewModel.onEvent(DocumentToolsEvent.ApplyCorrections(true, 5f, null, .3f))
        viewModel.onEvent(DocumentToolsEvent.SetExportOptions(PdfExportQuality.SMALL, true))
        viewModel.onEvent(DocumentToolsEvent.AnalyzeQuality)
        viewModel.onEvent(DocumentToolsEvent.AutoImprove(true))
        viewModel.onEvent(DocumentToolsEvent.SetCompareOriginal(true))
        viewModel.onEvent(DocumentToolsEvent.Undo)
        viewModel.onEvent(DocumentToolsEvent.Save)
        advanceUntilIdle()
        assertEquals(edited, viewModel.uiState.value.workspace)
        assertFalse(viewModel.uiState.value.isComparingOriginal)
        assertEquals(0, repository.analysisCalls)
        assertTrue(repository.saves.isEmpty())
        viewModel.onEvent(DocumentToolsEvent.CancelSignature)
        viewModel.onEvent(DocumentToolsEvent.Undo)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.workspace!!.pages.all {
            it.layout.imageFilter == PdfImageFilter.ORIGINAL
        })
    }

    @Test fun deletingAnalyzedPageInvalidatesReportAndDuplicateReferences() = runTest(dispatcher) {
        viewModel.load(document)
        advanceUntilIdle()
        repository.report = PdfQualityReport(listOf(
            PdfPageQuality("page-1", isBlank = true),
            PdfPageQuality("page-2", duplicateOfPageId = "page-1"),
            PdfPageQuality("page-3"),
        ))
        viewModel.onEvent(DocumentToolsEvent.AnalyzeQuality)
        advanceUntilIdle()
        viewModel.onEvent(DocumentToolsEvent.RequestDeletePage("page-1"))
        viewModel.onEvent(DocumentToolsEvent.ConfirmDeletePage)
        advanceUntilIdle()
        assertEquals(listOf("page-2", "page-3"), viewModel.uiState.value.workspace!!.pages.map { it.id })
        assertNull(viewModel.uiState.value.qualityReport)
    }

    @Test fun undoAppendedCapturesRestoresEarlierEditsAndOriginalSources() = runTest(dispatcher) {
        viewModel.load(document)
        advanceUntilIdle()
        viewModel.onEvent(DocumentToolsEvent.ApplyFilter(PdfImageFilter.ILLUMINATION, true, .7f))
        advanceUntilIdle()
        val beforeAppend = viewModel.uiState.value.workspace
        viewModel.onEvent(DocumentToolsEvent.AddPages)
        viewModel.onEvent(DocumentToolsEvent.ScanCompleted(listOf("content://capture")))
        advanceUntilIdle()
        assertEquals(4, viewModel.uiState.value.workspace!!.pages.size)
        viewModel.onEvent(DocumentToolsEvent.Undo)
        advanceUntilIdle()
        assertEquals(beforeAppend, viewModel.uiState.value.workspace)
        assertTrue(viewModel.uiState.value.hasChanges)
        assertTrue(viewModel.uiState.value.canUndo)
        assertTrue(repository.saves.isEmpty())
    }

    @Test fun mergeLeavesComparisonAndUndoRestoresWholePreviousRecipe() = runTest(dispatcher) {
        viewModel.load(document)
        advanceUntilIdle()
        viewModel.onEvent(DocumentToolsEvent.ApplyFilter(PdfImageFilter.ENHANCE, true))
        viewModel.onEvent(DocumentToolsEvent.SetExportOptions(PdfExportQuality.BALANCED, true))
        advanceUntilIdle()
        val beforeMerge = viewModel.uiState.value.workspace
        viewModel.onEvent(DocumentToolsEvent.SetCompareOriginal(true))
        advanceUntilIdle()
        viewModel.onEvent(DocumentToolsEvent.MergePdfs(insertionIndex = 1))
        viewModel.onEvent(DocumentToolsEvent.MergePdfsSelected(listOf("content://external.pdf")))
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isComparingOriginal)
        assertEquals(listOf("page-1", "merged-0", "page-2", "page-3"),
            viewModel.uiState.value.workspace!!.pages.map { it.id })
        viewModel.onEvent(DocumentToolsEvent.Undo)
        advanceUntilIdle()
        assertEquals(beforeMerge, viewModel.uiState.value.workspace)
        assertFalse(viewModel.uiState.value.isComparingOriginal)
    }

    @Test fun savingDuringComparisonSavesEditedRecipeAndEndsComparison() = runTest(dispatcher) {
        viewModel.load(document)
        advanceUntilIdle()
        viewModel.onEvent(DocumentToolsEvent.ApplyCorrections(true, 4f, null, .2f))
        viewModel.onEvent(DocumentToolsEvent.ApplyFilter(PdfImageFilter.ADAPTIVE_BLACK_WHITE, true, .9f))
        advanceUntilIdle()
        val edited = viewModel.uiState.value.workspace
        viewModel.onEvent(DocumentToolsEvent.SetCompareOriginal(true))
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isComparingOriginal)
        viewModel.onEvent(DocumentToolsEvent.Save)
        advanceUntilIdle()
        assertEquals(edited, repository.saves.single())
        assertEquals(edited, repository.previews.last())
        assertFalse(viewModel.uiState.value.isComparingOriginal)
        assertFalse(viewModel.uiState.value.canUndo)
    }

    private class QualityPasswords : PasswordAccess {
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

    private class QualityRepository : PdfEditorRepository {
        var analysisCalls = 0
        var analysisCancellations = 0
        var failAnalysis = false
        var analysisGate: CompletableDeferred<Unit>? = null
        var report = PdfQualityReport((1..3).map { PdfPageQuality("page-$it") })
        val previews = mutableListOf<PdfWorkspace>()
        val saves = mutableListOf<PdfWorkspace>()
        private lateinit var document: DocumentModel

        override suspend fun open(document: DocumentModel, password: String?): PdfWorkspace {
            this.document = document
            return PdfWorkspace(
                documentId = document.id,
                workspaceId = "workspace-${document.id}",
                directoryUri = "file:///workspace",
                pages = (1..document.pageCount).map { index ->
                    PdfPageSpec("page-$index", "file:///source.pdf", index - 1,
                        signatures = if (index == 1) listOf(
                            PdfSignaturePlacement("signature", "file:///signature.png"),
                        ) else emptyList(),
                    )
                },
                isProtected = document.protectionKeyId != null,
                protectionKeyId = document.protectionKeyId,
            )
        }
        override suspend fun analyze(workspace: PdfWorkspace): PdfQualityReport {
            analysisCalls++
            try {
                analysisGate?.await()
            } catch (cancelled: CancellationException) {
                analysisCancellations++
                throw cancelled
            }
            if (failAnalysis) error("Não foi possível analisar a captura")
            return report
        }
        override suspend fun preview(workspace: PdfWorkspace, retainedPreviewUri: String?): String {
            previews += workspace
            return "file:///preview-${previews.size}.pdf"
        }
        override suspend fun save(
            workspace: PdfWorkspace, password: String?, protectionKeyId: String?, removeProtection: Boolean,
        ): DocumentModel {
            saves += workspace
            return document.copy(pdfUri = "file:///saved.pdf", pageCount = workspace.pages.size)
        }
        override suspend fun importPdf(uri: String, password: String?, protectionKeyId: String?): ScannedDocument = error("Unused")
        override suspend fun appendScans(workspace: PdfWorkspace, pageUris: List<String>): PdfWorkspace =
            workspace.copy(pages = workspace.pages + pageUris.mapIndexed { index, uri ->
                PdfPageSpec("capture-$index", uri, 0)
            })
        override suspend fun appendPdfs(workspace: PdfWorkspace, sources: List<PdfMergeSource>): PdfWorkspace =
            workspace.copy(pages = workspace.pages + sources.mapIndexed { index, source ->
                PdfPageSpec("merged-$index", source.uri, 0)
            })
        override suspend fun copySignature(workspace: PdfWorkspace, imageUri: String): String = error("Unused")
        override suspend fun clearPreview(workspace: PdfWorkspace) = Unit
        override suspend fun discard(workspace: PdfWorkspace) = Unit
    }
}
