package com.joseleandro.scaniqo.ui.trash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joseleandro.scaniqo.domain.model.DocumentModel
import com.joseleandro.scaniqo.domain.repository.DocumentRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal enum class TrashMessage { RESTORED, DELETED, RESTORE_ERROR, CLEARED }

internal data class TrashUiState(
    val documents: List<DocumentModel> = emptyList(),
    val isLoading: Boolean = true,
    val loadError: Boolean = false,
    val busyDocumentId: Long? = null,
    val pendingDeletion: DocumentModel? = null,
    val deleteError: Boolean = false,
    val message: TrashMessage? = null,
    val pendingClearAll: Boolean = false,
    val pendingClearAllCount: Int = 0,
    val isClearingAll: Boolean = false,
    val clearAllError: Boolean = false,
) {
    val isBusy: Boolean get() = busyDocumentId != null || isClearingAll
}

class TrashViewModel(private val repository: DocumentRepository) : ViewModel() {
    private val state = MutableStateFlow(TrashUiState())
    internal val uiState = state.asStateFlow()
    private var observation: Job? = null
    // The confirmation applies only to this snapshot, including when retrying a partial failure.
    private val pendingClearAllIds = linkedSetOf<Long>()

    init { load() }

    internal fun load() {
        val previous = observation
        previous?.cancel()
        state.update { it.copy(isLoading = true, loadError = false) }
        observation = viewModelScope.launch {
            previous?.cancelAndJoin()
            try {
                repository.observeTrashedDocuments().collect { documents ->
                    if (!state.value.isClearingAll && pendingClearAllIds.isNotEmpty()) {
                        pendingClearAllIds.retainAll(documents.mapTo(HashSet()) { it.id })
                    }
                    state.update { current ->
                        current.copy(
                            documents = documents,
                            isLoading = false,
                            loadError = false,
                            pendingClearAll = current.pendingClearAll && pendingClearAllIds.isNotEmpty(),
                            pendingClearAllCount = pendingClearAllIds.size,
                            clearAllError = current.clearAllError && pendingClearAllIds.isNotEmpty(),
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
        if (state.value.isBusy || state.value.pendingDeletion != null || state.value.pendingClearAll ||
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
        if (state.value.isBusy || state.value.pendingClearAll) return
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

    internal fun requestClearAll() {
        val current = state.value
        if (current.isBusy || current.isLoading || current.loadError || current.pendingDeletion != null ||
            current.pendingClearAll || current.documents.isEmpty()) return
        pendingClearAllIds.addAll(current.documents.map { it.id })
        state.update {
            it.copy(
                pendingClearAll = true,
                pendingClearAllCount = pendingClearAllIds.size,
                clearAllError = false,
                message = null,
            )
        }
    }

    internal fun dismissClearAll() {
        if (state.value.isBusy) return
        pendingClearAllIds.clear()
        state.update {
            it.copy(pendingClearAll = false, pendingClearAllCount = 0, clearAllError = false)
        }
    }

    internal fun confirmClearAll() {
        if (!state.value.pendingClearAll || state.value.isBusy) return
        pendingClearAllIds.retainAll(state.value.documents.mapTo(HashSet()) { it.id })
        if (pendingClearAllIds.isEmpty()) {
            dismissClearAll()
            return
        }
        val confirmedIds = pendingClearAllIds.toList()
        state.update {
            it.copy(isClearingAll = true, pendingClearAllCount = confirmedIds.size, clearAllError = false, message = null)
        }
        viewModelScope.launch {
            try {
                for (id in confirmedIds) {
                    currentCoroutineContext().ensureActive()
                    try {
                        repository.deletePermanently(id)
                        pendingClearAllIds.remove(id)
                        state.update { it.copy(pendingClearAllCount = pendingClearAllIds.size) }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        // Keep this ID for an explicit retry while processing the other confirmed items.
                    }
                }
                state.update {
                    it.copy(
                        clearAllError = pendingClearAllIds.isNotEmpty(),
                        message = if (pendingClearAllIds.isEmpty()) TrashMessage.CLEARED else null,
                    )
                }
            } finally {
                state.update {
                    it.copy(
                        isClearingAll = false,
                        pendingClearAll = pendingClearAllIds.isNotEmpty(),
                        pendingClearAllCount = pendingClearAllIds.size,
                    )
                }
            }
        }
    }

    internal fun dismissMessage() { state.update { it.copy(message = null) } }
}
