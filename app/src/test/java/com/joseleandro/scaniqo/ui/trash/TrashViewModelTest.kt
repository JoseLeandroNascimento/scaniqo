package com.joseleandro.scaniqo.ui.trash

import androidx.lifecycle.ViewModelStore
import com.joseleandro.scaniqo.domain.model.DocumentModel
import com.joseleandro.scaniqo.ui.documentCommon.FakeDocumentRepository
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
}
