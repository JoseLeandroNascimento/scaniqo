package com.joseleandro.scaniqo.ui.documentEditor

import androidx.lifecycle.ViewModelStore
import com.joseleandro.scaniqo.domain.model.DocumentModel
import com.joseleandro.scaniqo.domain.model.ScannedDocument
import com.joseleandro.scaniqo.domain.repository.DocumentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
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

@OptIn(ExperimentalCoroutinesApi::class)
class DocumentViewerViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val repository = ViewerDocumentRepository()
    private val viewModel by lazy {
        DocumentViewerViewModel(repository).also { store.put("viewer", it) }
    }
    private val first = document(1, tagId = 10)
    private val second = document(2, tagId = 20)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        store.clear()
        dispatcher.scheduler.runCurrent()
        Dispatchers.resetMain()
    }

    @Test
    fun opensDirectlyAndKeepsAllMergeCandidatesWithoutAListViewModel() = runTest(dispatcher) {
        repository.documents.value = listOf(first, second)
        viewModel.load(first.id)
        assertTrue(viewModel.uiState.value.isLoading)
        runCurrent()

        assertEquals(first, viewModel.uiState.value.document)
        assertEquals(listOf(first, second), viewModel.uiState.value.availableDocuments)
        assertFalse(viewModel.uiState.value.isLoading)
        assertFalse(viewModel.uiState.value.loadError)

        val renamed = first.copy(name = "Documento atualizado")
        repository.documents.value = listOf(renamed, second)
        runCurrent()
        assertEquals(renamed, viewModel.uiState.value.document)

        repository.documents.value = listOf(second)
        runCurrent()
        assertNull(viewModel.uiState.value.document)
        assertEquals(listOf(second), viewModel.uiState.value.availableDocuments)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun switchingDocumentsCancelsThePreviousObserverAndClearReleasesIt() = runTest(dispatcher) {
        repository.documents.value = listOf(first, second)
        viewModel.load(first.id)
        viewModel.load(first.id)
        runCurrent()
        assertEquals(1, repository.observationCount)

        viewModel.load(second.id)
        assertEquals(second.id, viewModel.uiState.value.requestedDocumentId)
        assertEquals(second, viewModel.uiState.value.document)
        runCurrent()

        assertEquals(2, repository.observationCount)
        assertEquals(1, repository.activeObservers)
        assertEquals(1, repository.maximumActiveObservers)
        assertEquals(second, viewModel.uiState.value.document)

        store.clear()
        runCurrent()
        assertEquals(0, repository.activeObservers)
    }

    @Test
    fun failedLoadingCanBeRetriedWithoutDuplicateSubscriptions() = runTest(dispatcher) {
        repository.failure = IllegalStateException("Falha ao ler documentos")
        viewModel.load(second.id)
        runCurrent()
        assertTrue(viewModel.uiState.value.loadError)
        assertFalse(viewModel.uiState.value.isLoading)

        repository.failure = null
        repository.documents.value = listOf(first, second)
        viewModel.retry()
        viewModel.retry()
        assertTrue(viewModel.uiState.value.isLoading)
        runCurrent()

        assertFalse(viewModel.uiState.value.loadError)
        assertEquals(second, viewModel.uiState.value.document)
        assertEquals(2, repository.observationCount)
        assertEquals(1, repository.maximumActiveObservers)
    }

    @Test
    fun unknownDocumentDoesNotReuseThePreviouslyOpenedDocument() = runTest(dispatcher) {
        repository.documents.value = listOf(first)
        viewModel.load(first.id)
        runCurrent()
        viewModel.load(999)
        assertNull(viewModel.uiState.value.document)
        assertEquals(999L, viewModel.uiState.value.requestedDocumentId)
        runCurrent()

        assertNull(viewModel.uiState.value.document)
        assertFalse(viewModel.uiState.value.isLoading)
        assertFalse(viewModel.uiState.value.loadError)
        assertEquals(listOf(first), viewModel.uiState.value.availableDocuments)
    }

    private fun document(id: Long, tagId: Long) = DocumentModel(
        id = id,
        name = "Documento $id",
        pdfUri = "content://documents/$id.pdf",
        pageCount = 2,
        createdAt = 1_750_000_000_000,
        sizeBytes = 2048,
        thumbnailUri = null,
        tagId = tagId,
    )

    private class ViewerDocumentRepository : DocumentRepository {
        val documents = MutableStateFlow<List<DocumentModel>>(emptyList())
        var failure: Throwable? = null
        var observationCount = 0
        var activeObservers = 0
        var maximumActiveObservers = 0

        override fun observeDocuments(): Flow<List<DocumentModel>> = flow {
            observationCount++
            activeObservers++
            maximumActiveObservers = maxOf(maximumActiveObservers, activeObservers)
            try {
                failure?.let { throw it }
                emitAll(documents)
            } finally {
                activeObservers--
            }
        }

        override fun observeTrashedDocuments(): Flow<List<DocumentModel>> = error("Não usado")

        override suspend fun findById(id: Long): DocumentModel? =
            documents.value.firstOrNull { it.id == id }

        override suspend fun prepareScan(pageUris: List<String>): ScannedDocument = error("Não usado")
        override suspend fun discardScan(scan: ScannedDocument): Unit = error("Não usado")
        override suspend fun create(scan: ScannedDocument, name: String, tagId: Long?): Long = error("Não usado")
        override suspend fun update(id: Long, name: String, tagId: Long?): Unit = error("Não usado")
        override suspend fun replaceContent(
            id: Long,
            scan: ScannedDocument,
            allowProtectionRemoval: Boolean,
        ): DocumentModel = error("Não usado")
        override suspend fun moveToTrash(id: Long): Unit = error("Não usado")
        override suspend fun restoreFromTrash(id: Long): Unit = error("Não usado")
        override suspend fun deletePermanently(id: Long): Unit = error("Não usado")
        override suspend fun delete(document: DocumentModel): Unit = error("Não usado")
        override suspend fun export(document: DocumentModel, targetUri: String): Unit = error("Não usado")
        override suspend fun prepareShare(document: DocumentModel): String = error("Não usado")
    }
}
