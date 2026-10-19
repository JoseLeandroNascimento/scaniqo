package com.joseleandro.scaniqo.ui.documents

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.joseleandro.scaniqo.core.security.PasswordVault
import com.joseleandro.scaniqo.domain.model.DocumentModel
import com.joseleandro.scaniqo.domain.model.PdfMergeSource
import com.joseleandro.scaniqo.domain.model.PdfPasswordRequiredException
import com.joseleandro.scaniqo.domain.model.PdfWorkspace
import com.joseleandro.scaniqo.domain.model.ScannedDocument
import com.joseleandro.scaniqo.domain.repository.PdfEditorRepository
import com.joseleandro.scaniqo.ui.documentCommon.FakeDocumentRepository
import com.joseleandro.scaniqo.ui.documents.model.PdfImportDestination
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, manifest = Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
@OptIn(ExperimentalCoroutinesApi::class)
class PdfImportViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val documents = FakeDocumentRepository()
    private val editor = FakeEditor()
    private val saved = SavedStateHandle()
    private lateinit var vault: PasswordVault
    private val viewModel by lazy { createModel(saved, "initial") }

    @Before fun setUp() {
        // O monitor periódico do cofre pertence ao Looper Android pausado, e não ao scheduler
        // que runTest drena. Apenas as coroutines do importador usam o dispatcher de teste.
        vault = PasswordVault(RuntimeEnvironment.getApplication())
        Dispatchers.setMain(dispatcher)
    }

    @After fun tearDown() {
        store.clear()
        dispatcher.scheduler.runCurrent()
        Dispatchers.resetMain()
    }

    private fun createModel(handle: SavedStateHandle, key: String): PdfImportViewModel =
        PdfImportViewModel(editor, documents, vault, handle).also { store.put(key, it) }

    @Test fun ordinaryImportStillDeliversDraftForMetadataWithoutCreatingDocument() = runTest(dispatcher) {
        viewModel.chooseFile()
        viewModel.fileSelected("content://external/report") { "Relatório.pdf" }
        runCurrent()
        viewModel.saveForTool()
        assertEquals("Relatório", viewModel.uiState.value.pending?.name)
        assertTrue(documents.created.isEmpty())
        viewModel.completeDelivery(editor.scan.pdfUri)
        assertFalse(viewModel.uiState.value.isActive)
    }

    @Test fun toolImportCreatesExactlyOneOwnedDocumentAndKeepsDestinationUntilAcknowledged() = runTest(dispatcher) {
        viewModel.chooseFile(PdfImportDestination.COMPRESS_PDF)
        viewModel.chooseFile(PdfImportDestination.SIGN)
        viewModel.fileSelected("content://external/report") { "  Relatório.PDF  " }
        runCurrent()
        viewModel.completeDelivery(editor.scan.pdfUri)
        assertNotNull(viewModel.uiState.value.pending)
        viewModel.saveForTool()
        viewModel.saveForTool()
        runCurrent()
        assertEquals(1, documents.created.size)
        assertEquals("Relatório", documents.created.single().name)
        assertEquals(editor.scan, documents.created.single().draft)
        assertEquals(PdfImportDestination.COMPRESS_PDF, viewModel.uiState.value.destination)
        val id = requireNotNull(viewModel.uiState.value.importedDocumentId)
        assertNull(viewModel.uiState.value.pending)
        viewModel.fileSelected("content://late-callback")
        runCurrent()
        assertEquals(1, editor.imported.size)
        viewModel.acknowledgeToolDelivery(id + 1)
        assertEquals(id, viewModel.uiState.value.importedDocumentId)
        viewModel.acknowledgeToolDelivery(id)
        assertFalse(viewModel.uiState.value.isActive)
        assertNull(viewModel.uiState.value.destination)
        assertNull(saved.get<Long>("tool_document_id"))
    }

    @Test fun cancellingPickerClearsContinuationAndNextImportUsesNormalFlow() = runTest(dispatcher) {
        viewModel.chooseFile(PdfImportDestination.FILL_PAGE)
        viewModel.fileSelected(null)
        assertFalse(viewModel.uiState.value.isActive)
        assertNull(saved.get<String>("tool_destination"))
        viewModel.chooseFile()
        viewModel.fileSelected("content://external/next")
        runCurrent()
        assertNull(viewModel.uiState.value.destination)
        assertNotNull(viewModel.uiState.value.pending)
        assertTrue(documents.created.isEmpty())
    }

    @Test fun cancellingPreparedToolImportDiscardsOnlyItsDraft() = runTest(dispatcher) {
        viewModel.chooseFile(PdfImportDestination.EXTRACT_PAGES)
        viewModel.fileSelected("content://external/report")
        runCurrent()
        viewModel.cancel()
        runCurrent()
        assertEquals(listOf(editor.scan), documents.discarded)
        assertTrue(documents.created.isEmpty())
        assertFalse(viewModel.uiState.value.isActive)
    }

    @Test fun committingCannotBeCancelledHalfwayOrCreateDuplicates() = runTest(dispatcher) {
        documents.writeGate = CompletableDeferred()
        viewModel.chooseFile(PdfImportDestination.EDITOR)
        viewModel.fileSelected("content://external/report")
        runCurrent()
        viewModel.saveForTool()
        runCurrent()
        assertTrue(viewModel.uiState.value.isSavingToLibrary)
        viewModel.cancel()
        viewModel.saveForTool()
        assertTrue(viewModel.uiState.value.isActive)
        requireNotNull(documents.writeGate).complete(Unit)
        runCurrent()
        assertEquals(1, documents.created.size)
        assertNotNull(viewModel.uiState.value.importedDocumentId)
        assertTrue(documents.discarded.isEmpty())
    }

    @Test fun creationFailureRetainsDraftForCleanupAndDoesNotRetryInALoop() = runTest(dispatcher) {
        documents.writeFailure = IllegalStateException("Sem espaço")
        viewModel.chooseFile(PdfImportDestination.PDF_TO_IMAGES)
        viewModel.fileSelected("content://external/report")
        runCurrent()
        viewModel.saveForTool()
        runCurrent()
        assertEquals("Sem espaço", viewModel.uiState.value.error)
        assertNotNull(viewModel.uiState.value.pending)
        assertFalse(viewModel.uiState.value.isSavingToLibrary)
        assertNull(viewModel.uiState.value.importedDocumentId)
        viewModel.saveForTool()
        runCurrent()
        assertEquals(1, documents.created.size)
        viewModel.cancel()
        runCurrent()
        assertEquals(listOf(editor.scan), documents.discarded)
    }

    @Test fun preparedAndCompletedContinuationsSurviveSavedStateRestoration() = runTest(dispatcher) {
        viewModel.chooseFile(PdfImportDestination.SIGN)
        viewModel.fileSelected("content://external/report") { "Assinar.pdf" }
        runCurrent()
        val restoredSaved = SavedStateHandle(saved.keys().associateWith { saved.get<Any?>(it) })
        val restored = createModel(restoredSaved, "restored")
        assertEquals(PdfImportDestination.SIGN, restored.uiState.value.destination)
        assertEquals("Assinar", restored.uiState.value.pending?.name)
        restored.saveForTool()
        runCurrent()
        val completed = createModel(SavedStateHandle(restoredSaved.keys().associateWith {
            restoredSaved.get<Any?>(it)
        }), "completed")
        assertEquals(restored.uiState.value.importedDocumentId, completed.uiState.value.importedDocumentId)
        completed.saveForTool()
        runCurrent()
        assertEquals(1, documents.created.size)
    }

    @Test fun passwordPromptAndImportFailureKeepTheRequestedDestinationWithoutCreatingDocuments() = runTest(dispatcher) {
        editor.failure = PdfPasswordRequiredException()
        viewModel.chooseFile(PdfImportDestination.EXTRACT_PAGES)
        viewModel.fileSelected("content://external/protected")
        runCurrent()
        assertTrue(viewModel.uiState.value.needsPassword)
        assertEquals(PdfImportDestination.EXTRACT_PAGES, viewModel.uiState.value.destination)
        assertTrue(documents.created.isEmpty())
        viewModel.cancel()
        editor.failure = IllegalStateException("PDF inválido")
        viewModel.chooseFile(PdfImportDestination.FILL_PAGE)
        viewModel.fileSelected("content://external/broken")
        runCurrent()
        assertEquals("PDF inválido", viewModel.uiState.value.error)
        assertEquals(PdfImportDestination.FILL_PAGE, viewModel.uiState.value.destination)
        assertNull(viewModel.uiState.value.importedDocumentId)
    }

    @Test fun sameExternalFileCanBeImportedAgainForANewToolOperation() = runTest(dispatcher) {
        val external = "content://external/report"
        viewModel.chooseFile(PdfImportDestination.EXTRACT_PAGES)
        viewModel.fileSelected(external) { "Primeiro.pdf" }
        runCurrent()
        viewModel.saveForTool()
        runCurrent()
        val firstId = requireNotNull(viewModel.uiState.value.importedDocumentId)
        viewModel.acknowledgeToolDelivery(firstId)

        viewModel.chooseFile(PdfImportDestination.FILL_PAGE)
        assertNull(viewModel.uiState.value.importedDocumentId)
        viewModel.fileSelected(external) { "Novo.pdf" }
        runCurrent()
        assertEquals("Novo", viewModel.uiState.value.pending?.name)
        assertEquals(PdfImportDestination.FILL_PAGE, viewModel.uiState.value.destination)
        viewModel.saveForTool()
        runCurrent()

        assertEquals(listOf(external, external), editor.imported)
        assertEquals(listOf("Primeiro", "Novo"), documents.created.map { it.name })
        assertTrue(requireNotNull(viewModel.uiState.value.importedDocumentId) != firstId)
        assertTrue(documents.discarded.isEmpty())
    }

    @Test fun protectedCopyMetadataIsPassedIntactToTheLibrary() = runTest(dispatcher) {
        editor.scan = editor.scan.copy(thumbnailUri = null, protectionKeyId = "existing-key")
        viewModel.chooseFile(PdfImportDestination.EDITOR)
        viewModel.fileSelected("content://external/protected")
        runCurrent()
        viewModel.saveForTool()
        runCurrent()
        assertEquals("existing-key", documents.created.single().draft.protectionKeyId)
        assertNull(documents.created.single().draft.thumbnailUri)
    }

    private class FakeEditor : PdfEditorRepository {
        var scan = ScannedDocument("file:///owned/import.pdf", 3, 10, 4096, "file:///owned/preview.jpg")
        var failure: Exception? = null
        val imported = mutableListOf<String>()
        override suspend fun importPdf(uri: String, password: String?, protectionKeyId: String?): ScannedDocument {
            imported += uri
            failure?.let { throw it }
            return scan
        }
        override suspend fun open(document: DocumentModel, password: String?): PdfWorkspace = error("Fora deste fluxo")
        override suspend fun appendScans(workspace: PdfWorkspace, pageUris: List<String>): PdfWorkspace = error("Fora deste fluxo")
        override suspend fun appendPdfs(workspace: PdfWorkspace, sources: List<PdfMergeSource>): PdfWorkspace = error("Fora deste fluxo")
        override suspend fun copySignature(workspace: PdfWorkspace, imageUri: String): String = error("Fora deste fluxo")
        override suspend fun preview(workspace: PdfWorkspace, retainedPreviewUri: String?): String = error("Fora deste fluxo")
        override suspend fun clearPreview(workspace: PdfWorkspace) = Unit
        override suspend fun save(workspace: PdfWorkspace, password: String?, protectionKeyId: String?, removeProtection: Boolean): DocumentModel =
            error("A importação não modifica o PDF original")
        override suspend fun discard(workspace: PdfWorkspace) = Unit
    }
}
