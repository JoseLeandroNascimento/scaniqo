package com.joseleandro.scaniqo.ui.documentSearch

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joseleandro.scaniqo.domain.model.DocumentModel
import com.joseleandro.scaniqo.domain.repository.DocumentRepository
import com.joseleandro.scaniqo.domain.repository.DocumentSearchRepository
import com.joseleandro.scaniqo.ui.documentSearch.state.DocumentSearchUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update

/** Pesquisa somente enquanto a tela está sendo observada e descarta consultas antigas. */
@OptIn(ExperimentalCoroutinesApi::class)
class DocumentSearchViewModel(
    private val repository: DocumentRepository,
    private val searchRepository: DocumentSearchRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val query = savedStateHandle.getStateFlow(QUERY, "")
    private val retries = MutableStateFlow(0)
    private val library = retries.flatMapLatest {
        repository.observeDocuments()
            .map { LibraryState(documents = it) }
            .onStart { emit(LibraryState()) }
            .catch { error ->
                if (error is CancellationException) throw error
                emit(LibraryState(loadError = true))
            }
    }

    val uiState = combine(query, library) { query, library -> query to library }
        .transformLatest { (query, library) ->
            val hasQuery = query.isNotBlank()
            emit(
                DocumentSearchUiState(
                    query = query,
                    isSearching = hasQuery && !library.loadError,
                    loadError = library.loadError,
                ),
            )
            val documents = library.documents
            if (!hasQuery || documents == null || library.loadError) return@transformLatest
            // Evita abrir PDFs a cada tecla e permite cancelar ao mudar a consulta.
            delay(SEARCH_DEBOUNCE_MILLIS)
            try {
                searchRepository.searchUpdates(documents, query.trim()).collect { update ->
                    currentCoroutineContext().ensureActive()
                    emit(DocumentSearchUiState(
                        query = query,
                        results = update.results,
                        isSearching = !update.isComplete,
                        resultLimitReached = update.resultLimitReached,
                    ))
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                emit(DocumentSearchUiState(query = query, loadError = true))
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 0),
            initialValue = DocumentSearchUiState(query = query.value),
        )

    fun onQueryChanged(value: String) {
        savedStateHandle[QUERY] = value
    }

    fun retry() {
        retries.update { it + 1 }
    }

    private data class LibraryState(
        val documents: List<DocumentModel>? = null,
        val loadError: Boolean = false,
    )

    private companion object {
        const val QUERY = "documentSearch.query"
        const val SEARCH_DEBOUNCE_MILLIS = 300L
    }
}
