package com.joseleandro.scaniqo.ui.documentSearch

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.joseleandro.scaniqo.domain.model.DocumentModel
import com.joseleandro.scaniqo.domain.model.DocumentSearchResult
import com.joseleandro.scaniqo.domain.model.DocumentSearchUpdate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import com.joseleandro.scaniqo.domain.repository.DocumentSearchRepository
import com.joseleandro.scaniqo.ui.documentCommon.FakeDocumentRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DocumentSearchViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val repository = FakeDocumentRepository()
    private val search = FakeSearchRepository()
    private val savedState = SavedStateHandle()
    private val document = DocumentModel(
        id = 7, name = "Contrato", pdfUri = "file:///contract.pdf",
        pageCount = 2, createdAt = 10, sizeBytes = 100,
    )
    private val viewModel by lazy {
        DocumentSearchViewModel(repository, search, savedState).also { store.put("search", it) }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository.documents.value = listOf(document)
    }

    @After
    fun tearDown() {
        store.clear()
        dispatcher.scheduler.runCurrent()
        Dispatchers.resetMain()
    }

    @Test
    fun blankInputDoesNotReadPdfsAndTypingOnlySearchesTheLatestQuery() = runTest(dispatcher) {
        observe()
        runCurrent()
        assertTrue(search.queries.isEmpty())

        viewModel.onQueryChanged("con")
        runCurrent()
        advanceTimeBy(200)
        viewModel.onQueryChanged("contrato")
        runCurrent()
        advanceTimeBy(299)
        assertTrue(search.queries.isEmpty())
        assertTrue(viewModel.uiState.value.isSearching)

        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf("contrato"), search.queries)
        assertEquals(document, viewModel.uiState.value.results.single().document)

        viewModel.onQueryChanged("  ")
        runCurrent()
        assertFalse(viewModel.uiState.value.isSearching)
        assertTrue(viewModel.uiState.value.results.isEmpty())
        assertEquals(1, search.queries.size)
    }

    @Test
    fun replacingQueryCancelsAnInFlightSearchAndNeverShowsItsResults() = runTest(dispatcher) {
        search.progressive = true
        val gate = CompletableDeferred<Unit>()
        search.gate = gate
        observe()
        viewModel.onQueryChanged("old")
        finishDebounce()
        assertEquals(listOf("old"), search.queries)
        assertEquals("old partial", viewModel.uiState.value.results.single().contentSnippet)
        assertTrue(viewModel.uiState.value.isSearching)

        search.gate = null
        viewModel.onQueryChanged("new")
        runCurrent()
        assertEquals(listOf("old"), search.cancelled)
        finishDebounce()
        gate.complete(Unit)
        runCurrent()

        assertEquals("new", viewModel.uiState.value.query)
        assertEquals(listOf("old", "new"), search.queries)
        assertEquals("new", viewModel.uiState.value.results.single().contentSnippet)
    }

    @Test
    fun typingIsVisibleWhilePreviousPdfIsStillClosing() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        search.gate = gate
        search.nonCancellable = true
        observe()
        viewModel.onQueryChanged("old")
        finishDebounce()

        viewModel.onQueryChanged("new")
        runCurrent()
        assertEquals("new", viewModel.query.value)
        assertTrue(viewModel.uiState.value.results.isEmpty())

        search.gate = null
        gate.complete(Unit)
        finishDebounce()
        assertEquals("new", viewModel.uiState.value.query)
        assertEquals("new", viewModel.uiState.value.results.single().contentSnippet)
    }

    @Test
    fun libraryChangesRefreshResultsForTheCurrentQuery() = runTest(dispatcher) {
        observe()
        viewModel.onQueryChanged("contrato")
        finishDebounce()
        assertEquals(1, viewModel.uiState.value.results.size)

        repository.documents.value = emptyList()
        finishDebounce()
        assertTrue(viewModel.uiState.value.results.isEmpty())
        assertEquals(listOf("contrato", "contrato"), search.queries)
    }

    @Test
    fun savedQueryIsRestoredAndRetriedAfterLibraryFailure() = runTest(dispatcher) {
        savedState["documentSearch.query"] = "  contrato  "
        repository.loadFailure = IllegalStateException("Database unavailable")
        observe()
        runCurrent()
        assertTrue(viewModel.uiState.value.loadError)
        assertEquals("  contrato  ", viewModel.uiState.value.query)

        repository.loadFailure = null
        viewModel.retry()
        finishDebounce()
        assertFalse(viewModel.uiState.value.loadError)
        assertEquals(listOf("contrato"), search.queries)
        assertEquals("  contrato  ", viewModel.uiState.value.query)
    }

    @Test
    fun failedSearchCanBeRetriedAndQueryUpdatesAreStillAccepted() = runTest(dispatcher) {
        search.failure = IllegalStateException("Search unavailable")
        observe()
        viewModel.onQueryChanged("contrato")
        finishDebounce()
        assertTrue(viewModel.uiState.value.loadError)
        assertFalse(viewModel.uiState.value.isSearching)

        search.failure = null
        viewModel.retry()
        finishDebounce()
        assertFalse(viewModel.uiState.value.loadError)
        assertEquals(1, viewModel.uiState.value.results.size)

        viewModel.onQueryChanged("outro")
        finishDebounce()
        assertEquals("outro", viewModel.uiState.value.query)
        assertEquals("outro", savedState.get<String>("documentSearch.query"))
    }

    @Test
    fun leavingTheScreenCancelsPdfWorkAndReturningKeepsTheQuery() = runTest(dispatcher) {
        search.gate = CompletableDeferred()
        val observer = observe()
        viewModel.onQueryChanged("contrato")
        finishDebounce()
        observer.cancel()
        runCurrent()
        assertEquals(listOf("contrato"), search.cancelled)
        assertEquals(0, repository.activeObservers)

        search.gate = null
        observe()
        finishDebounce()
        assertEquals("contrato", viewModel.uiState.value.query)
        assertEquals(1, viewModel.uiState.value.results.size)
    }

    private fun TestScope.observe(): Job = backgroundScope.launch(
        UnconfinedTestDispatcher(testScheduler),
    ) { viewModel.uiState.collect() }

    private fun TestScope.finishDebounce() {
        runCurrent()
        advanceTimeBy(300)
        runCurrent()
    }

    private class FakeSearchRepository : DocumentSearchRepository {
        val queries = mutableListOf<String>()
        val cancelled = mutableListOf<String>()
        var gate: CompletableDeferred<Unit>? = null
        var failure: Exception? = null
        var nonCancellable = false
        var progressive = false

        override fun searchUpdates(
            documents: List<DocumentModel>,
            query: String,
        ): Flow<DocumentSearchUpdate> = if (!progressive) super.searchUpdates(documents, query) else flow {
            emit(DocumentSearchUpdate(
                documents.map { DocumentSearchResult(it, false, "$query partial", 1) },
                isComplete = false,
            ))
            emit(DocumentSearchUpdate(search(documents, query), isComplete = true))
        }

        override suspend fun search(
            documents: List<DocumentModel>,
            query: String,
        ): List<DocumentSearchResult> {
            queries += query
            try {
                val currentGate = gate
                if (nonCancellable) withContext(NonCancellable) { currentGate?.await() }
                else currentGate?.await()
            } catch (cancellation: kotlinx.coroutines.CancellationException) {
                cancelled += query
                throw cancellation
            }
            failure?.let { throw it }
            return documents.map {
                DocumentSearchResult(it, matchesName = false, contentSnippet = query, pageNumber = 1)
            }
        }
    }
}
