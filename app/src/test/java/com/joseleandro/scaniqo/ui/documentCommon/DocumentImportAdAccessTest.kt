package com.joseleandro.scaniqo.ui.documentCommon

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.joseleandro.scaniqo.core.ads.RewardedTool
import com.joseleandro.scaniqo.domain.model.DocumentModel
import com.joseleandro.scaniqo.ui.documentCommon.event.DocumentActionEvent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DocumentImportAdAccessTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val savedState = SavedStateHandle()
    private val documents = FakeDocumentRepository()
    private val tags = FakeTagRepository()
    private val policy = FakeDocumentToolAdPolicy()
    private val viewModel by lazy {
        DocumentActionsViewModel(documents, tags, savedState, adAccess = policy)
            .also { store.put("actions", it) }
    }

    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() {
        store.clear()
        dispatcher.scheduler.runCurrent()
        Dispatchers.resetMain()
    }

    private fun prepareImport() {
        viewModel.onDocumentAction(DocumentActionEvent.ImportStarted())
        viewModel.onDocumentAction(DocumentActionEvent.ImportPrepared(documents.scan, "Importado"))
    }

    private fun save(unavailable: Boolean = false) {
        viewModel.onDocumentAction(DocumentActionEvent.SaveImportedDocument(documents.scan.pdfUri, unavailable))
    }

    @Test fun firstImportAndNextImportBothRequireTheirOwnVideo() = runTest(dispatcher) {
        prepareImport()
        save()
        runCurrent()
        assertTrue(documents.created.isEmpty())
        assertTrue(viewModel.uiState.value.editor!!.requiresImportAd)

        policy.grantReward(RewardedTool.IMPORT_PDF, "video-1")
        save()
        runCurrent()
        assertEquals(1, documents.created.size)
        assertEquals(listOf(RewardedTool.IMPORT_PDF to true), policy.finishes)

        prepareImport()
        save()
        runCurrent()
        assertEquals(1, documents.created.size)
        assertNotNull(viewModel.uiState.value.editor)
    }

    @Test fun ordinarySaveEventCannotBypassImportGate() = runTest(dispatcher) {
        prepareImport()
        viewModel.onDocumentAction(DocumentActionEvent.SaveDocument)
        runCurrent()
        assertTrue(documents.created.isEmpty())
        assertFalse(viewModel.uiState.value.editor!!.isSaving)
    }

    @Test fun missingAdAllowsImportWithoutKeepingAnUnlockForNextUse() = runTest(dispatcher) {
        prepareImport()
        save(unavailable = true)
        runCurrent()
        assertEquals(1, documents.created.size)
        assertTrue(policy.rewards.isEmpty())

        prepareImport()
        save()
        runCurrent()
        assertEquals(1, documents.created.size)
    }

    @Test fun failedSaveKeepsCreditAndRetriesWithoutAnotherVideo() = runTest(dispatcher) {
        prepareImport()
        policy.grantReward(RewardedTool.IMPORT_PDF, "video")
        documents.writeFailure = IllegalStateException("No space")
        save()
        runCurrent()
        assertTrue(viewModel.uiState.value.editor!!.saveError)
        assertEquals(listOf(RewardedTool.IMPORT_PDF to false), policy.finishes)
        assertNull(policy.reservation)
        assertTrue(RewardedTool.IMPORT_PDF in policy.rewards)

        documents.writeFailure = null
        save()
        runCurrent()
        assertNull(viewModel.uiState.value.editor)
        assertEquals(RewardedTool.IMPORT_PDF to true, policy.finishes.last())
        assertTrue(policy.rewards.isEmpty())
    }

    @Test fun invalidNameOrStaleDraftNeverReservesReward() = runTest(dispatcher) {
        prepareImport()
        policy.grantReward(RewardedTool.IMPORT_PDF, "video")
        viewModel.onDocumentAction(DocumentActionEvent.SaveImportedDocument("file:///other.pdf", true))
        assertTrue(policy.acquisitions.isEmpty())

        viewModel.onDocumentAction(DocumentActionEvent.NameChanged(" "))
        save()
        runCurrent()
        assertTrue(policy.acquisitions.isEmpty())
        assertNotNull(viewModel.uiState.value.editor!!.nameError)
        assertTrue(RewardedTool.IMPORT_PDF in policy.rewards)
    }

    @Test fun duplicateTapReservesOnlyOnceAndWaitsUntilWriteCompletes() = runTest(dispatcher) {
        prepareImport()
        policy.grantReward(RewardedTool.IMPORT_PDF, "video")
        documents.writeGate = CompletableDeferred()
        save()
        save()
        runCurrent()
        assertEquals(1, policy.acquisitions.size)
        assertEquals(1, documents.created.size)
        assertTrue(policy.finishes.isEmpty())
        documents.writeGate!!.complete(Unit)
        runCurrent()
        assertEquals(listOf(RewardedTool.IMPORT_PDF to true), policy.finishes)
    }

    @Test fun cancellingBeforeCoroutineStartsReturnsReservedCredit() = runTest(dispatcher) {
        prepareImport()
        policy.grantReward(RewardedTool.IMPORT_PDF, "video")
        save()
        assertNotNull(policy.reservation)
        store.clear()
        runCurrent()
        assertNull(policy.reservation)
        assertEquals(listOf(RewardedTool.IMPORT_PDF to false), policy.finishes)
        assertTrue(documents.created.isEmpty())
    }

    @Test fun scannerGeneratedResultAndMetadataEditStayOutsideImportGate() = runTest(dispatcher) {
        viewModel.onDocumentAction(DocumentActionEvent.StartScan())
        viewModel.onDocumentAction(DocumentActionEvent.ScanCompleted(listOf("content://scan")))
        runCurrent()
        assertFalse(viewModel.uiState.value.editor!!.requiresImportAd)
        viewModel.onDocumentAction(DocumentActionEvent.SaveDocument)
        runCurrent()

        assertTrue(viewModel.acceptGeneratedDraft("merge:result", documents.scan, "Merged", null))
        assertFalse(viewModel.uiState.value.editor!!.requiresImportAd)
        viewModel.onDocumentAction(DocumentActionEvent.SaveDocument)
        runCurrent()

        viewModel.onDocumentAction(DocumentActionEvent.EditMetadata(
            DocumentModel(99, "Document", "file:///original.pdf", 1, 0, 1),
        ))
        viewModel.onDocumentAction(DocumentActionEvent.SaveDocument)
        runCurrent()
        assertTrue(policy.acquisitions.isEmpty())
        assertEquals(2, documents.created.size)
        assertEquals(1, documents.updated.size)
    }

    @Test fun restoredImportedDraftStillRequiresVideo() = runTest(dispatcher) {
        prepareImport()
        val snapshot = savedState.keys().associateWith { savedState.get<Any?>(it) }
        store.clear()
        runCurrent()
        val restored = DocumentActionsViewModel(documents, tags, SavedStateHandle(snapshot), adAccess = policy)
            .also { store.put("restored", it) }
        assertTrue(restored.uiState.value.editor!!.requiresImportAd)
        restored.onDocumentAction(DocumentActionEvent.SaveDocument)
        runCurrent()
        assertTrue(documents.created.isEmpty())
    }
}
