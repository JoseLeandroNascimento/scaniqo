package com.joseleandro.scaniqo.ui.tags

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joseleandro.scaniqo.domain.model.TAG_NAME_MAX_LENGTH
import com.joseleandro.scaniqo.domain.model.TagModel
import com.joseleandro.scaniqo.domain.repository.TagRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TagsViewModel(
    private val repository: TagRepository,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(TagsUiState())
    val uiState: StateFlow<TagsUiState> = mutableUiState.asStateFlow()

    private var observationJob: Job? = null

    init {
        observeTags()
    }

    fun onEvent(event: TagsEvent) {
        when (event) {
            TagsEvent.CreateTag -> openEditor(TagEditorUiState())
            is TagsEvent.EditTag -> openEditor(
                TagEditorUiState(
                    id = event.tag.id,
                    name = event.tag.name,
                    iconKey = event.tag.iconKey,
                    color = event.tag.color,
                ),
            )
            is TagsEvent.NameChanged -> updateEditor {
                it.copy(name = event.name, nameError = null, saveError = false)
            }
            is TagsEvent.IconChanged -> updateEditor {
                it.copy(iconKey = event.icon.key, saveError = false)
            }
            is TagsEvent.ColorChanged -> updateEditor {
                it.copy(color = event.color, saveError = false)
            }
            TagsEvent.SaveTag -> saveTag()
            TagsEvent.DismissEditor -> {
                if (!isBusy()) mutableUiState.update { it.copy(editor = null) }
            }
            is TagsEvent.RequestDelete -> {
                if (!isBusy()) {
                    mutableUiState.update {
                        it.copy(pendingDeletion = event.tag, message = null)
                    }
                }
            }
            TagsEvent.ConfirmDelete -> deleteTag()
            TagsEvent.DismissDelete -> {
                if (!isBusy()) {
                    mutableUiState.update {
                        it.copy(
                            pendingDeletion = null,
                            message = it.message.takeUnless { message ->
                                message == TagsMessage.DELETE_ERROR
                            },
                        )
                    }
                }
            }
            TagsEvent.RetryLoad -> {
                if (uiState.value.loadError) observeTags()
            }
            TagsEvent.DismissMessage -> mutableUiState.update { it.copy(message = null) }
        }
    }

    private fun observeTags() {
        // A nova tentativa só começa depois que o coletor anterior é encerrado.
        val previousJob = observationJob
        previousJob?.cancel()
        mutableUiState.update { it.copy(isLoading = true, loadError = false) }
        observationJob = viewModelScope.launch {
            previousJob?.cancelAndJoin()
            try {
                repository.observeTags().collect { tags ->
                    mutableUiState.update {
                        it.copy(tags = tags, isLoading = false, loadError = false)
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                mutableUiState.update { it.copy(isLoading = false, loadError = true) }
            }
        }
    }

    private fun openEditor(editor: TagEditorUiState) {
        if (isBusy()) return
        mutableUiState.update {
            it.copy(editor = editor, pendingDeletion = null, message = null)
        }
    }

    private fun updateEditor(update: (TagEditorUiState) -> TagEditorUiState) {
        if (isBusy()) return
        mutableUiState.update { state ->
            state.copy(editor = state.editor?.let(update))
        }
    }

    private fun saveTag() {
        val editor = uiState.value.editor ?: return
        if (isBusy()) return

        val normalizedName = editor.name.trim()
        val nameError = when {
            normalizedName.isBlank() -> TagNameError.REQUIRED
            normalizedName.length > TAG_NAME_MAX_LENGTH -> TagNameError.TOO_LONG
            else -> null
        }
        if (nameError != null) {
            mutableUiState.update {
                it.copy(editor = editor.copy(nameError = nameError, saveError = false))
            }
            return
        }

        // O bloqueio acontece antes de iniciar a coroutine para impedir cliques duplicados.
        mutableUiState.update {
            it.copy(
                editor = editor.copy(isSaving = true, nameError = null, saveError = false),
                message = null,
            )
        }
        viewModelScope.launch {
            try {
                val tag = TagModel(
                    id = editor.id ?: 0L,
                    name = normalizedName,
                    color = editor.color,
                    iconKey = editor.iconKey,
                )
                if (editor.id == null) {
                    repository.create(tag)
                } else {
                    repository.update(tag)
                }
                mutableUiState.update { it.copy(editor = null, message = TagsMessage.SAVED) }
            } catch (cancellation: CancellationException) {
                // Cancelar a tela não representa uma falha de persistência.
                throw cancellation
            } catch (_: Exception) {
                mutableUiState.update {
                    it.copy(editor = it.editor?.copy(saveError = true))
                }
            } finally {
                mutableUiState.update {
                    it.copy(editor = it.editor?.copy(isSaving = false))
                }
            }
        }
    }

    private fun deleteTag() {
        val tag = uiState.value.pendingDeletion ?: return
        if (isBusy()) return

        mutableUiState.update { it.copy(isDeleting = true, message = null) }
        viewModelScope.launch {
            try {
                repository.delete(tag)
                mutableUiState.update {
                    it.copy(pendingDeletion = null, message = TagsMessage.DELETED)
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                // Mantém a confirmação aberta para permitir uma nova tentativa.
                mutableUiState.update { it.copy(message = TagsMessage.DELETE_ERROR) }
            } finally {
                mutableUiState.update { it.copy(isDeleting = false) }
            }
        }
    }

    private fun isBusy(): Boolean =
        uiState.value.isDeleting || uiState.value.editor?.isSaving == true
}
