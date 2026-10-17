package com.joseleandro.scaniqo.ui.documentEditor

import androidx.lifecycle.ViewModelStore
import com.joseleandro.scaniqo.core.security.PasswordAccess
import com.joseleandro.scaniqo.core.security.PasswordVaultLockedException
import com.joseleandro.scaniqo.core.security.PasswordVaultState
import com.joseleandro.scaniqo.domain.model.DocumentModel
import com.joseleandro.scaniqo.domain.model.PdfExportQuality
import com.joseleandro.scaniqo.domain.model.PdfImageFilter
import com.joseleandro.scaniqo.domain.model.PdfMergeSource
import com.joseleandro.scaniqo.domain.model.PdfPageSpec
import com.joseleandro.scaniqo.domain.model.PdfPagePreview
import com.joseleandro.scaniqo.domain.model.PdfQualityReport
import com.joseleandro.scaniqo.domain.model.PdfSignaturePlacement
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
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Mede trabalho solicitado e continuidade da prévia; não depende de tempo real ou dispositivo. */
@OptIn(ExperimentalCoroutinesApi::class)
class DocumentPreviewPerformanceTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val repository = PreviewRepository()
    private val passwords = PreviewPasswords()
    private val document = DocumentModel(
        id = 83, name = "Captura", pdfUri = "file:///saved.pdf",
        pageCount = 3, createdAt = 1L, sizeBytes = 900L,
    )
    private val viewModel by lazy {
        DocumentToolsViewModel(repository, EmptySignatures(), passwords)
            .also { store.put("preview", it) }
    }

    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() {
        store.clear()
        dispatcher.scheduler.runCurrent()
        Dispatchers.resetMain()
    }

    @Test fun exportOnlyChangesDoNotRebuildVisibleDocument() = runTest(dispatcher) {
        viewModel.load(document)
        advanceUntilIdle()
        val uri = viewModel.uiState.value.previewUri
        val count = repository.requests.size

        viewModel.onEvent(DocumentToolsEvent.SetExportOptions(PdfExportQuality.SMALL, true))
        advanceUntilIdle()
        viewModel.onEvent(DocumentToolsEvent.SetExportOptions(PdfExportQuality.BALANCED, false))
        advanceUntilIdle()

        assertEquals(count, repository.requests.size)
        assertEquals(uri, viewModel.uiState.value.previewUri)
        assertFalse(viewModel.uiState.value.isRendering)
        assertTrue(viewModel.uiState.value.hasChanges)
    }

    @Test fun savingExportOnlyChangesRefreshesFromFinalDocumentPreview() = runTest(dispatcher) {
        viewModel.load(document)
        advanceUntilIdle()
        val oldUri = viewModel.uiState.value.previewUri
        val count = repository.requests.size
        viewModel.onEvent(DocumentToolsEvent.SetExportOptions(PdfExportQuality.SMALL, true))
        advanceUntilIdle()
        assertEquals(count, repository.requests.size)

        viewModel.onEvent(DocumentToolsEvent.Save)
        advanceUntilIdle()

        assertEquals(count + 1, repository.requests.size)
        assertNotEquals(oldUri, viewModel.uiState.value.previewUri)
        assertFalse(viewModel.uiState.value.hasChanges)
        assertEquals(PdfExportQuality.SMALL, repository.requests.last().workspace.exportQuality)
        assertTrue(repository.requests.last().workspace.ocrEnabled)
    }

    @Test fun repeatedComparisonRequestsReuseCompletedAndRunningPreview() = runTest(dispatcher) {
        viewModel.load(document)
        advanceUntilIdle()
        viewModel.onEvent(DocumentToolsEvent.ApplyFilter(PdfImageFilter.ENHANCE, true))
        advanceUntilIdle()
        val initialCount = repository.requests.size
        val gate = CompletableDeferred<Unit>()
        repository.nextBehavior = PreviewBehavior(gate = gate)

        viewModel.onEvent(DocumentToolsEvent.SetCompareOriginal(true))
        advanceTimeBy(250)
        runCurrent()
        assertEquals(initialCount + 1, repository.requests.size)
        repeat(3) { viewModel.onEvent(DocumentToolsEvent.SetCompareOriginal(true)) }
        runCurrent()
        assertEquals(initialCount + 1, repository.requests.size)
        assertTrue(viewModel.uiState.value.isRendering)
        gate.complete(Unit)
        advanceUntilIdle()
        val uri = viewModel.uiState.value.previewUri

        repeat(3) { viewModel.onEvent(DocumentToolsEvent.SetCompareOriginal(true)) }
        advanceUntilIdle()
        assertEquals(initialCount + 1, repository.requests.size)
        assertEquals(uri, viewModel.uiState.value.previewUri)
        assertFalse(viewModel.uiState.value.isRendering)
    }

    @Test fun changingFilterKeepsPreviousImageUntilReplacementIsReady() = runTest(dispatcher) {
        viewModel.load(document)
        advanceUntilIdle()
        val oldUri = viewModel.uiState.value.previewUri
        assertNotNull(oldUri)
        val gate = CompletableDeferred<Unit>()
        repository.nextBehavior = PreviewBehavior(gate = gate)

        viewModel.onEvent(DocumentToolsEvent.ApplyFilter(PdfImageFilter.DENOISE, false))
        assertTrue(viewModel.uiState.value.isRendering)
        assertEquals(oldUri, viewModel.uiState.value.previewUri)
        advanceTimeBy(250)
        runCurrent()
        assertEquals(oldUri, repository.requests.last().retainedUri)
        assertEquals(oldUri, viewModel.uiState.value.previewUri)
        gate.complete(Unit)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isRendering)
        assertNotEquals(oldUri, viewModel.uiState.value.previewUri)
        assertEquals(repository.requests.last().uri, viewModel.uiState.value.previewUri)
    }

    @Test fun renderingFailureKeepsPreviousImageAndExplicitRetryRegenerates() = runTest(dispatcher) {
        viewModel.load(document)
        advanceUntilIdle()
        val oldUri = viewModel.uiState.value.previewUri
        repository.nextBehavior = PreviewBehavior(fail = true)
        viewModel.onEvent(DocumentToolsEvent.ApplyFilter(PdfImageFilter.SHARPEN, false))
        advanceUntilIdle()

        assertEquals(oldUri, viewModel.uiState.value.previewUri)
        assertTrue(viewModel.uiState.value.previewError)
        assertFalse(viewModel.uiState.value.isRendering)
        val failedCount = repository.requests.size
        viewModel.onEvent(DocumentToolsEvent.Retry)
        advanceUntilIdle()

        assertEquals(failedCount + 1, repository.requests.size)
        assertFalse(viewModel.uiState.value.previewError)
        assertNotEquals(oldUri, viewModel.uiState.value.previewUri)
        assertEquals(PdfImageFilter.SHARPEN, repository.requests.last().workspace.pages.first().layout.imageFilter)
    }

    @Test fun pageReorderingKeepsIndependentPagePreviewAndIndexZero() = runTest(dispatcher) {
        viewModel.load(document)
        advanceUntilIdle()
        val previous = viewModel.uiState.value.pagePreviews.getValue("page-1").preview
        val count = repository.requests.size
        viewModel.onEvent(DocumentToolsEvent.ReorderPages(listOf("page-3", "page-1", "page-2")))
        advanceUntilIdle()

        assertEquals(previous, viewModel.uiState.value.pagePreviews.getValue("page-1").preview)
        assertEquals(0, previous!!.pageIndex)
        assertEquals(count, repository.requests.size)
        assertFalse(viewModel.uiState.value.isRendering)
        assertEquals(listOf("page-3", "page-1", "page-2"),
            viewModel.uiState.value.workspace!!.pages.map { it.id })
    }

    @Test fun lateUncancellableResultCannotReplaceLatestRequestedFilter() = runTest(dispatcher) {
        viewModel.load(document)
        advanceUntilIdle()
        val oldUri = viewModel.uiState.value.previewUri
        val staleGate = CompletableDeferred<Unit>()
        repository.nextBehavior = PreviewBehavior(gate = staleGate, ignoreCancellation = true)
        viewModel.onEvent(DocumentToolsEvent.ApplyFilter(PdfImageFilter.ENHANCE, false))
        advanceTimeBy(250)
        runCurrent()
        val staleUri = repository.requests.last().uri

        val latestGate = CompletableDeferred<Unit>()
        repository.nextBehavior = PreviewBehavior(gate = latestGate)
        viewModel.onEvent(DocumentToolsEvent.ApplyFilter(PdfImageFilter.GRAYSCALE, false))
        advanceTimeBy(250)
        runCurrent()
        staleGate.complete(Unit)
        runCurrent()

        assertEquals(oldUri, viewModel.uiState.value.previewUri)
        assertNotEquals(staleUri, viewModel.uiState.value.previewUri)
        assertTrue(viewModel.uiState.value.isRendering)
        latestGate.complete(Unit)
        advanceUntilIdle()
        assertEquals(repository.requests.last().uri, viewModel.uiState.value.previewUri)
        assertEquals(PdfImageFilter.GRAYSCALE, repository.requests.last().workspace.pages.first().layout.imageFilter)
    }

    @Test fun comparisonRemovesEnhancementsOnlyFromSelectedPageAndTracksDisplayedResult() = runTest(dispatcher) {
        viewModel.load(document)
        advanceUntilIdle()
        viewModel.onEvent(DocumentToolsEvent.ApplyFilter(PdfImageFilter.ENHANCE, true))
        advanceUntilIdle()
        viewModel.onEvent(DocumentToolsEvent.SelectPage("page-2"))
        val edited = viewModel.uiState.value.workspace
        val gate = CompletableDeferred<Unit>()
        repository.nextBehavior = PreviewBehavior(gate = gate)

        viewModel.onEvent(DocumentToolsEvent.SetCompareOriginal(true))
        assertTrue(viewModel.uiState.value.isComparingOriginal)
        assertNull(viewModel.uiState.value.displayedComparisonPageId)
        advanceTimeBy(250)
        runCurrent()
        assertEquals(listOf(PdfImageFilter.ENHANCE, PdfImageFilter.ORIGINAL, PdfImageFilter.ENHANCE),
            repository.requests.last().workspace.pages.map { it.layout.imageFilter })
        assertEquals(edited, viewModel.uiState.value.workspace)
        assertNull(viewModel.uiState.value.displayedComparisonPageId)
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals("page-2", viewModel.uiState.value.displayedComparisonPageId)
        assertEquals(edited, viewModel.uiState.value.workspace)
    }

    @Test fun comparisonFailureDoesNotLabelEditedImageAsOriginal() = runTest(dispatcher) {
        viewModel.load(document)
        advanceUntilIdle()
        viewModel.onEvent(DocumentToolsEvent.ApplyFilter(PdfImageFilter.ENHANCE, true))
        advanceUntilIdle()
        val editedUri = viewModel.uiState.value.previewUri
        repository.nextBehavior = PreviewBehavior(fail = true)
        viewModel.onEvent(DocumentToolsEvent.SetCompareOriginal(true))
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isComparingOriginal)
        assertTrue(viewModel.uiState.value.previewError)
        assertEquals(editedUri, viewModel.uiState.value.previewUri)
        assertNull(viewModel.uiState.value.displayedComparisonPageId)
    }

    @Test fun switchingSelectedPageEndsComparisonAndRestoresEditedPreview() = runTest(dispatcher) {
        viewModel.load(document)
        advanceUntilIdle()
        viewModel.onEvent(DocumentToolsEvent.ApplyFilter(PdfImageFilter.ENHANCE, true))
        advanceUntilIdle()
        viewModel.onEvent(DocumentToolsEvent.SelectPage("page-2"))
        viewModel.onEvent(DocumentToolsEvent.SetCompareOriginal(true))
        advanceUntilIdle()
        assertEquals("page-2", viewModel.uiState.value.displayedComparisonPageId)
        val gate = CompletableDeferred<Unit>()
        repository.nextBehavior = PreviewBehavior(gate = gate)

        viewModel.onEvent(DocumentToolsEvent.SelectPage("page-3"))
        assertFalse(viewModel.uiState.value.isComparingOriginal)
        assertEquals("page-3", viewModel.uiState.value.selectedPageId)
        advanceTimeBy(250)
        runCurrent()
        gate.complete(Unit)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.displayedComparisonPageId)
        assertTrue(repository.requests.last().workspace.pages.all {
            it.layout.imageFilter == PdfImageFilter.ENHANCE
        })
    }

    @Test fun unmodifiedSelectedPageCannotCompareChangesFromAnotherPage() = runTest(dispatcher) {
        viewModel.load(document)
        advanceUntilIdle()
        viewModel.onEvent(DocumentToolsEvent.ApplyFilter(PdfImageFilter.WHITEN, false))
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.canCompare)
        viewModel.onEvent(DocumentToolsEvent.SelectPage("page-2"))
        advanceUntilIdle()
        val count = repository.requests.size
        assertFalse(viewModel.uiState.value.canCompare)
        viewModel.onEvent(DocumentToolsEvent.SetCompareOriginal(true))
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isComparingOriginal)
        assertNull(viewModel.uiState.value.displayedComparisonPageId)
        assertEquals(count, repository.requests.size)
    }

    @Test fun confirmingSignatureRequestsOnlyOneUpdatedPreview() = runTest(dispatcher) {
        viewModel.load(document)
        advanceUntilIdle()
        viewModel.onEvent(DocumentToolsEvent.EditSignature("page-1", "signature"))
        advanceUntilIdle()
        viewModel.onEvent(DocumentToolsEvent.MoveSignature(.2f, .6f))
        val count = repository.requests.size

        viewModel.onEvent(DocumentToolsEvent.ConfirmSignature)
        advanceUntilIdle()

        assertEquals(count + 1, repository.requests.size)
        assertEquals(.2f, repository.requests.last().workspace.pages.first().signatures.single().x, 0f)
        assertEquals(.6f, repository.requests.last().workspace.pages.first().signatures.single().y, 0f)
        assertNull(viewModel.uiState.value.signatureDraft)
    }

    @Test fun analysisResumesPendingVisualUpdateEvenWhenOldImageWasRetained() = runTest(dispatcher) {
        viewModel.load(document)
        advanceUntilIdle()
        val originalUri = viewModel.uiState.value.previewUri
        repository.nextBehavior = PreviewBehavior(gate = CompletableDeferred())
        viewModel.onEvent(DocumentToolsEvent.ApplyFilter(PdfImageFilter.SHARPEN, false))
        advanceTimeBy(250)
        runCurrent()
        assertEquals(originalUri, viewModel.uiState.value.previewUri)

        viewModel.onEvent(DocumentToolsEvent.AnalyzeQuality)
        advanceUntilIdle()

        assertEquals(1, repository.analysisCalls)
        assertFalse(viewModel.uiState.value.isAnalyzing)
        assertFalse(viewModel.uiState.value.isRendering)
        assertNotEquals(originalUri, viewModel.uiState.value.previewUri)
        assertEquals(PdfImageFilter.SHARPEN, repository.requests.last().workspace.pages.first().layout.imageFilter)
    }

    @Test fun cancellingAnalysisAlsoResumesPendingVisualUpdate() = runTest(dispatcher) {
        viewModel.load(document)
        advanceUntilIdle()
        val originalUri = viewModel.uiState.value.previewUri
        repository.nextBehavior = PreviewBehavior(gate = CompletableDeferred())
        viewModel.onEvent(DocumentToolsEvent.ApplyFilter(PdfImageFilter.DENOISE, false))
        advanceTimeBy(250)
        runCurrent()
        repository.analysisGate = CompletableDeferred()
        viewModel.onEvent(DocumentToolsEvent.AnalyzeQuality)
        runCurrent()
        assertTrue(viewModel.uiState.value.isAnalyzing)

        viewModel.onEvent(DocumentToolsEvent.CancelAnalysis)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isAnalyzing)
        assertFalse(viewModel.uiState.value.isRendering)
        assertNotEquals(originalUri, viewModel.uiState.value.previewUri)
        assertEquals(PdfImageFilter.DENOISE, repository.requests.last().workspace.pages.first().layout.imageFilter)
    }

    @Test fun lockingClearsVisibleComparisonAndLateResultCannotRestoreIt() = runTest(dispatcher) {
        passwords.unlock()
        viewModel.load(document.copy(protectionKeyId = "key"))
        advanceUntilIdle()
        viewModel.onEvent(DocumentToolsEvent.ApplyFilter(PdfImageFilter.ENHANCE, true))
        advanceUntilIdle()
        viewModel.onEvent(DocumentToolsEvent.SetCompareOriginal(true))
        advanceUntilIdle()
        assertEquals("page-1", viewModel.uiState.value.displayedComparisonPageId)
        val gate = CompletableDeferred<Unit>()
        repository.nextBehavior = PreviewBehavior(gate = gate, ignoreCancellation = true)
        viewModel.onEvent(DocumentToolsEvent.SetCompareOriginal(false))
        advanceTimeBy(250)
        runCurrent()

        passwords.lock()
        viewModel.onEvent(DocumentToolsEvent.Backgrounded)
        assertNull(viewModel.uiState.value.previewUri)
        assertNull(viewModel.uiState.value.displayedComparisonPageId)
        assertFalse(viewModel.uiState.value.isComparingOriginal)
        gate.complete(Unit)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isLocked)
        assertNull(viewModel.uiState.value.previewUri)
        assertNull(viewModel.uiState.value.displayedComparisonPageId)

        val count = repository.requests.size
        passwords.unlock()
        viewModel.onEvent(DocumentToolsEvent.AuthenticationSucceeded)
        advanceUntilIdle()
        assertTrue(repository.requests.size > count)
        assertNotNull(viewModel.uiState.value.previewUri)
        assertNull(viewModel.uiState.value.displayedComparisonPageId)
    }

    private data class PreviewBehavior(
        val gate: CompletableDeferred<Unit>? = null,
        val ignoreCancellation: Boolean = false,
        val fail: Boolean = false,
    )

    private data class PreviewRequest(val workspace: PdfWorkspace, val retainedUri: String?, val uri: String)

    private class PreviewRepository : PdfEditorRepository {
        val requests = mutableListOf<PreviewRequest>()
        var nextBehavior: PreviewBehavior? = null
        var analysisGate: CompletableDeferred<Unit>? = null
        var analysisCalls = 0
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

        override suspend fun renderPagePreview(
            workspace: PdfWorkspace, pageId: String, retainedPreviewUris: Set<String>,
        ): PdfPagePreview = PdfPagePreview(preview(workspace, retainedPreviewUris.firstOrNull()), 0)

        override suspend fun preview(workspace: PdfWorkspace, retainedPreviewUri: String?): String {
            val behavior = nextBehavior.also { nextBehavior = null } ?: PreviewBehavior()
            val uri = "file:///preview-${requests.size + 1}.pdf"
            requests += PreviewRequest(workspace, retainedPreviewUri, uri)
            if (behavior.ignoreCancellation) {
                withContext(NonCancellable) { behavior.gate?.await() }
            } else behavior.gate?.await()
            if (behavior.fail) error("Falha de renderização")
            return uri
        }

        override suspend fun analyze(workspace: PdfWorkspace): PdfQualityReport {
            analysisCalls++
            analysisGate?.await()
            return PdfQualityReport(emptyList())
        }

        override suspend fun save(
            workspace: PdfWorkspace, password: String?, protectionKeyId: String?, removeProtection: Boolean,
        ): DocumentModel = document
        override suspend fun importPdf(uri: String, password: String?, protectionKeyId: String?): ScannedDocument = error("Unused")
        override suspend fun appendScans(workspace: PdfWorkspace, pageUris: List<String>): PdfWorkspace = error("Unused")
        override suspend fun appendPdfs(workspace: PdfWorkspace, sources: List<PdfMergeSource>): PdfWorkspace = error("Unused")
        override suspend fun copySignature(workspace: PdfWorkspace, imageUri: String): String = error("Unused")
        override suspend fun clearPreview(workspace: PdfWorkspace) = Unit
        override suspend fun discard(workspace: PdfWorkspace) = Unit
    }

    private class PreviewPasswords : PasswordAccess {
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
