package com.joseleandro.scaniqo.ui.trash

import androidx.lifecycle.ViewModelStore
import com.joseleandro.scaniqo.domain.model.DocumentModel
import com.joseleandro.scaniqo.domain.repository.DocumentRepository
import com.joseleandro.scaniqo.ui.documentCommon.FakeDocumentRepository
import kotlinx.coroutines.CancellationException
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
class TrashViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val repository = FakeDocumentRepository()
    private val document = DocumentModel(
        id = 7, name = "Contrato", pdfUri = "file:///documents/contract.pdf",
        pageCount = 2, createdAt = 1234, sizeBytes = 2048, tagId = 3,
        protectionKeyId = "key", deletedAt = 5678,
    )
    private val viewModel by lazy { TrashViewModel(repository).also { store.put("trash", it) } }

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository.trashedDocuments.value = listOf(document)
    }
    @After fun tearDown() {
        store.clear()
        Dispatchers.resetMain()
    }

    @Test fun restoreReturnsDocumentToLibraryAndBlocksRepeatedClicks() = runTest(dispatcher) {
        viewModel
        runCurrent()
        repository.writeGate = CompletableDeferred()
        viewModel.restore(document.id)
        viewModel.restore(document.id)
        runCurrent()
        assertEquals(listOf(document.id), repository.restored)
        assertTrue(viewModel.uiState.value.isBusy)
        repository.writeGate!!.complete(Unit)
        runCurrent()
        assertEquals(document.copy(deletedAt = null), repository.documents.value.single())
        assertTrue(viewModel.uiState.value.documents.isEmpty())
        assertFalse(viewModel.uiState.value.isBusy)
        assertEquals(TrashMessage.RESTORED, viewModel.uiState.value.message)
    }

    @Test fun permanentDeletionRequiresConfirmationAndCanBeCancelled() = runTest(dispatcher) {
        viewModel
        runCurrent()
        viewModel.confirmDeletion()
        viewModel.requestDeletion(document.id)
        assertEquals(document, viewModel.uiState.value.pendingDeletion)
        assertTrue(repository.permanentlyDeleted.isEmpty())
        viewModel.dismissDeletion()
        viewModel.confirmDeletion()
        runCurrent()
        assertTrue(repository.permanentlyDeleted.isEmpty())
        assertEquals(listOf(document), repository.trashedDocuments.value)
    }

    @Test fun confirmedPermanentDeletionBlocksDuplicateAndDismissUntilComplete() = runTest(dispatcher) {
        viewModel
        runCurrent()
        repository.writeGate = CompletableDeferred()
        viewModel.requestDeletion(document.id)
        viewModel.confirmDeletion()
        viewModel.confirmDeletion()
        viewModel.dismissDeletion()
        viewModel.restore(document.id)
        runCurrent()
        assertEquals(listOf(document.id), repository.permanentlyDeleted)
        assertEquals(document, viewModel.uiState.value.pendingDeletion)
        assertTrue(repository.restored.isEmpty())
        repository.writeGate!!.complete(Unit)
        runCurrent()
        assertNull(viewModel.uiState.value.pendingDeletion)
        assertTrue(viewModel.uiState.value.documents.isEmpty())
        assertEquals(TrashMessage.DELETED, viewModel.uiState.value.message)
    }

    @Test fun failedDeletionKeepsDocumentAndConfirmationForRetry() = runTest(dispatcher) {
        viewModel
        runCurrent()
        repository.writeFailure = IllegalStateException("offline storage")
        viewModel.requestDeletion(document.id)
        viewModel.confirmDeletion()
        runCurrent()
        assertTrue(viewModel.uiState.value.deleteError)
        assertEquals(document, viewModel.uiState.value.pendingDeletion)
        assertEquals(listOf(document), repository.trashedDocuments.value)
        assertFalse(viewModel.uiState.value.isBusy)
        repository.writeFailure = null
        viewModel.confirmDeletion()
        runCurrent()
        assertFalse(viewModel.uiState.value.deleteError)
        assertNull(viewModel.uiState.value.pendingDeletion)
        assertTrue(repository.trashedDocuments.value.isEmpty())
    }

    @Test fun failedRestoreKeepsDocumentAvailableForRetry() = runTest(dispatcher) {
        viewModel
        runCurrent()
        repository.writeFailure = IllegalStateException("storage")
        viewModel.restore(document.id)
        runCurrent()
        assertEquals(TrashMessage.RESTORE_ERROR, viewModel.uiState.value.message)
        assertEquals(listOf(document), viewModel.uiState.value.documents)
        assertFalse(viewModel.uiState.value.isBusy)
        repository.writeFailure = null
        viewModel.restore(document.id)
        runCurrent()
        assertEquals(document.copy(deletedAt = null), repository.documents.value.single())
    }

    @Test fun loadFailureCanBeRetriedAndUnknownIdsCannotBeChanged() = runTest(dispatcher) {
        repository.loadFailure = IllegalStateException("database")
        viewModel
        runCurrent()
        assertTrue(viewModel.uiState.value.loadError)
        repository.loadFailure = null
        viewModel.load()
        runCurrent()
        assertFalse(viewModel.uiState.value.loadError)
        assertEquals(listOf(document), viewModel.uiState.value.documents)
        viewModel.restore(999)
        viewModel.requestDeletion(999)
        viewModel.confirmDeletion()
        runCurrent()
        assertTrue(repository.restored.isEmpty())
        assertTrue(repository.permanentlyDeleted.isEmpty())
    }

    @Test fun clearingAllRequiresConfirmationAndCanBeDismissedWithoutDeleting() = runTest(dispatcher) {
        viewModel
        runCurrent()
        viewModel.confirmClearAll()
        assertTrue(repository.permanentlyDeleted.isEmpty())
        viewModel.requestClearAll()
        assertTrue(viewModel.uiState.value.pendingClearAll)
        assertEquals(1, viewModel.uiState.value.pendingClearAllCount)
        assertTrue(repository.permanentlyDeleted.isEmpty())
        viewModel.restore(document.id)
        viewModel.requestDeletion(document.id)
        assertTrue(repository.restored.isEmpty())
        assertNull(viewModel.uiState.value.pendingDeletion)

        viewModel.dismissClearAll()
        viewModel.confirmClearAll()
        runCurrent()
        assertFalse(viewModel.uiState.value.pendingClearAll)
        assertEquals(0, viewModel.uiState.value.pendingClearAllCount)
        assertTrue(repository.permanentlyDeleted.isEmpty())
        assertEquals(listOf(document), repository.trashedDocuments.value)
    }

    @Test fun clearingAllOnlyDeletesConfirmedSnapshotAndBlocksDuplicateOperations() = runTest(dispatcher) {
        val second = document.copy(id = 8)
        val third = document.copy(id = 9)
        val newlyTrashed = document.copy(id = 10)
        val active = document.copy(id = 11, deletedAt = null)
        repository.trashedDocuments.value = listOf(document, second, third)
        repository.documents.value = listOf(active)
        viewModel
        runCurrent()
        viewModel.requestClearAll()
        repository.trashedDocuments.value += newlyTrashed
        runCurrent()
        viewModel.requestClearAll()
        assertEquals(3, viewModel.uiState.value.pendingClearAllCount)

        repository.writeGate = CompletableDeferred()
        viewModel.confirmClearAll()
        viewModel.confirmClearAll()
        viewModel.dismissClearAll()
        viewModel.restore(second.id)
        viewModel.requestDeletion(third.id)
        runCurrent()
        assertEquals(listOf(document.id), repository.permanentlyDeleted)
        assertTrue(viewModel.uiState.value.isBusy)
        assertTrue(viewModel.uiState.value.isClearingAll)
        assertTrue(viewModel.uiState.value.pendingClearAll)
        assertNull(viewModel.uiState.value.pendingDeletion)
        assertTrue(repository.restored.isEmpty())

        repository.writeGate!!.complete(Unit)
        runCurrent()
        assertEquals(listOf(document.id, second.id, third.id), repository.permanentlyDeleted)
        assertEquals(listOf(newlyTrashed), repository.trashedDocuments.value)
        assertEquals(listOf(active), repository.documents.value)
        assertFalse(viewModel.uiState.value.isBusy)
        assertFalse(viewModel.uiState.value.pendingClearAll)
        assertEquals(0, viewModel.uiState.value.pendingClearAllCount)
        assertEquals(TrashMessage.CLEARED, viewModel.uiState.value.message)
    }

    @Test fun clearAllContinuesAfterFailureAndRetriesOnlyFailedDocuments() = runTest(dispatcher) {
        val second = document.copy(id = 8)
        val third = document.copy(id = 9)
        val newlyTrashed = document.copy(id = 10)
        repository.trashedDocuments.value = listOf(document, second, third)
        val failures = mutableSetOf(second.id)
        val attempted = mutableListOf<Long>()
        val failingRepository = object : DocumentRepository by repository {
            override suspend fun deletePermanently(id: Long) {
                attempted += id
                if (id in failures) throw IllegalStateException("storage unavailable")
                repository.deletePermanently(id)
            }
        }
        val model = TrashViewModel(failingRepository).also { store.put("partial", it) }
        runCurrent()
        model.requestClearAll()
        model.confirmClearAll()
        runCurrent()

        assertEquals(listOf(document.id, second.id, third.id), attempted)
        assertEquals(listOf(second), repository.trashedDocuments.value)
        assertTrue(model.uiState.value.clearAllError)
        assertTrue(model.uiState.value.pendingClearAll)
        assertEquals(1, model.uiState.value.pendingClearAllCount)
        assertFalse(model.uiState.value.isBusy)
        assertNull(model.uiState.value.message)

        failures.clear()
        repository.trashedDocuments.value += newlyTrashed
        runCurrent()
        model.confirmClearAll()
        runCurrent()
        assertEquals(listOf(document.id, second.id, third.id, second.id), attempted)
        assertEquals(listOf(newlyTrashed), repository.trashedDocuments.value)
        assertFalse(model.uiState.value.clearAllError)
        assertFalse(model.uiState.value.pendingClearAll)
        assertEquals(TrashMessage.CLEARED, model.uiState.value.message)
    }

    @Test fun clearAllCancellationStopsRemainingDeletesAndAllowsExplicitRetry() = runTest(dispatcher) {
        val second = document.copy(id = 8)
        val third = document.copy(id = 9)
        repository.trashedDocuments.value = listOf(document, second, third)
        val attempted = mutableListOf<Long>()
        var shouldCancel = true
        val cancellingRepository = object : DocumentRepository by repository {
            override suspend fun deletePermanently(id: Long) {
                attempted += id
                if (id == second.id && shouldCancel) throw CancellationException("cancelled write")
                repository.deletePermanently(id)
            }
        }
        val model = TrashViewModel(cancellingRepository).also { store.put("cancelled", it) }
        runCurrent()
        model.requestClearAll()
        model.confirmClearAll()
        runCurrent()

        assertEquals(listOf(document.id, second.id), attempted)
        assertEquals(listOf(second, third), repository.trashedDocuments.value)
        assertFalse(model.uiState.value.isBusy)
        assertTrue(model.uiState.value.pendingClearAll)
        assertEquals(2, model.uiState.value.pendingClearAllCount)
        assertFalse(model.uiState.value.clearAllError)
        assertNull(model.uiState.value.message)

        shouldCancel = false
        model.confirmClearAll()
        runCurrent()
        assertEquals(listOf(document.id, second.id, second.id, third.id), attempted)
        assertTrue(repository.trashedDocuments.value.isEmpty())
        assertEquals(TrashMessage.CLEARED, model.uiState.value.message)
    }

    @Test fun clearingViewModelCancelsBatchWithoutDeletingRemainingFiles() = runTest(dispatcher) {
        val second = document.copy(id = 8)
        val third = document.copy(id = 9)
        repository.trashedDocuments.value = listOf(document, second, third)
        val writeGate = CompletableDeferred<Unit>()
        val attempted = mutableListOf<Long>()
        val blockingRepository = object : DocumentRepository by repository {
            override suspend fun deletePermanently(id: Long) {
                attempted += id
                if (id == second.id) writeGate.await()
                repository.deletePermanently(id)
            }
        }
        val model = TrashViewModel(blockingRepository).also { store.put("lifecycle", it) }
        runCurrent()
        model.requestClearAll()
        model.confirmClearAll()
        runCurrent()
        assertEquals(listOf(document.id, second.id), attempted)
        assertTrue(model.uiState.value.isBusy)

        store.clear()
        runCurrent()
        writeGate.complete(Unit)
        runCurrent()
        assertEquals(listOf(document.id, second.id), attempted)
        assertEquals(listOf(document.id), repository.permanentlyDeleted)
        assertEquals(listOf(second, third), repository.trashedDocuments.value)
        assertFalse(model.uiState.value.isBusy)
        assertNull(model.uiState.value.message)
    }

    @Test fun clearAllCannotBeRequestedBeforeLoadOnErrorWhenEmptyOrDuringSingleDeletion() = runTest(dispatcher) {
        repository.loadFailure = IllegalStateException("database unavailable")
        viewModel.requestClearAll()
        assertFalse(viewModel.uiState.value.pendingClearAll)
        runCurrent()
        viewModel.requestClearAll()
        assertFalse(viewModel.uiState.value.pendingClearAll)
        repository.loadFailure = null
        repository.trashedDocuments.value = emptyList()
        viewModel.load()
        runCurrent()
        viewModel.requestClearAll()
        assertFalse(viewModel.uiState.value.pendingClearAll)

        repository.trashedDocuments.value = listOf(document)
        runCurrent()
        viewModel.requestDeletion(document.id)
        viewModel.requestClearAll()
        assertFalse(viewModel.uiState.value.pendingClearAll)
        assertEquals(document, viewModel.uiState.value.pendingDeletion)
        assertTrue(repository.permanentlyDeleted.isEmpty())
    }

    @Test fun documentsRestoredWhileConfirmingAreRemovedFromTheClearAllSnapshot() = runTest(dispatcher) {
        val second = document.copy(id = 8)
        repository.trashedDocuments.value = listOf(document, second)
        viewModel
        runCurrent()
        viewModel.requestClearAll()
        repository.restoreFromTrash(document.id)
        runCurrent()
        assertEquals(1, viewModel.uiState.value.pendingClearAllCount)
        viewModel.confirmClearAll()
        runCurrent()
        assertEquals(listOf(second.id), repository.permanentlyDeleted)
        assertEquals(listOf(document.copy(deletedAt = null)), repository.documents.value)
        assertTrue(repository.trashedDocuments.value.isEmpty())
    }
}
