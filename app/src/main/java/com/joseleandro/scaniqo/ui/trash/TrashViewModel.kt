package com.joseleandro.scaniqo.ui.trash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joseleandro.scaniqo.domain.model.DocumentModel
import com.joseleandro.scaniqo.domain.repository.DocumentRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal enum class TrashMessage { RESTORED, DELETED, RESTORE_ERROR }

internal data class TrashUiState(
    val documents: List<DocumentModel> = emptyList(),
    val isLoading: Boolean = true,
    val loadError: Boolean = false,
    val busyDocumentId: Long? = null,
    val pendingDeletion: DocumentModel? = null,
    val deleteError: Boolean = false,
    val message: TrashMessage? = null,
) {
    val isBusy: Boolean get() = busyDocumentId != null
}

class TrashViewModel(private val repository: DocumentRepository) : ViewModel() {
    private val state = MutableStateFlow(TrashUiState())
    internal val uiState = state.asStateFlow()
    private var observation: Job? = null

    init { load() }

    internal fun load() {
        val previous = observation
        previous?.cancel()
        state.update { it.copy(isLoading = true, loadError = false) }
        observation = viewModelScope.launch {
            previous?.cancelAndJoin()
            try {
                repository.observeTrashedDocuments().collect { documents ->
                    state.update { current ->
                        current.copy(
                            documents = documents,
                            isLoading = false,
                            loadError = false,
                            pendingDeletion = current.pendingDeletion?.takeIf { pending ->
                                current.isBusy || documents.any { it.id == pending.id }
                            },
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                state.update { it.copy(isLoading = false, loadError = true) }
            }
        }
    }

    internal fun restore(id: Long) {
        if (state.value.isBusy || state.value.pendingDeletion != null ||
            state.value.documents.none { it.id == id }) return
        state.update { it.copy(busyDocumentId = id, message = null) }
        viewModelScope.launch {
            try {
                repository.restoreFromTrash(id)
                state.update { it.copy(message = TrashMessage.RESTORED) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                state.update { it.copy(message = TrashMessage.RESTORE_ERROR) }
            } finally {
                state.update { it.copy(busyDocumentId = null) }
            }
        }
    }

    internal fun requestDeletion(id: Long) {
        if (state.value.isBusy) return
        val document = state.value.documents.firstOrNull { it.id == id } ?: return
        state.update { it.copy(pendingDeletion = document, deleteError = false, message = null) }
    }

    internal fun dismissDeletion() {
        if (!state.value.isBusy) state.update { it.copy(pendingDeletion = null, deleteError = false) }
    }

    internal fun confirmDeletion() {
        val document = state.value.pendingDeletion ?: return
        if (state.value.isBusy) return
        state.update { it.copy(busyDocumentId = document.id, deleteError = false, message = null) }
        viewModelScope.launch {
            try {
                repository.deletePermanently(document.id)
                state.update { it.copy(pendingDeletion = null, message = TrashMessage.DELETED) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                state.update { it.copy(deleteError = true) }
            } finally {
                state.update { it.copy(busyDocumentId = null) }
            }
        }
    }

    internal fun dismissMessage() { state.update { it.copy(message = null) } }
}
