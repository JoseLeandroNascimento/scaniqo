package com.joseleandro.scaniqo.ui.documentEditor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joseleandro.scaniqo.domain.repository.DocumentRepository
import com.joseleandro.scaniqo.ui.documentEditor.state.DocumentViewerUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Observa o documento e os candidatos à mesclagem sem depender da tela de lista. */
class DocumentViewerViewModel(
    private val repository: DocumentRepository,
) : ViewModel() {
    private val state = MutableStateFlow(DocumentViewerUiState())
    val uiState = state.asStateFlow()
    private var documentId: Long? = null
    private var observationJob: Job? = null
    private var revision = 0L

    fun load(documentId: Long) {
        if (this.documentId == documentId) return
        this.documentId = documentId
        observeDocuments(documentId)
    }

    fun retry() {
        if (!state.value.loadError) return
        documentId?.let(::observeDocuments)
    }

    private fun observeDocuments(documentId: Long) {
        val request = ++revision
        val previousJob = observationJob
        previousJob?.cancel()
        state.update {
            it.copy(
                requestedDocumentId = documentId,
                document = it.availableDocuments.firstOrNull { document -> document.id == documentId },
                isLoading = true,
                loadError = false,
            )
        }
        observationJob = viewModelScope.launch {
            previousJob?.cancelAndJoin()
            try {
                repository.observeDocuments().collect { documents ->
                    if (request != revision) return@collect
                    state.update {
                        it.copy(
                            document = documents.firstOrNull { document -> document.id == documentId },
                            availableDocuments = documents,
                            isLoading = false,
                            loadError = false,
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (request == revision) {
                    state.update { it.copy(isLoading = false, loadError = true) }
                }
            }
        }
    }
}
