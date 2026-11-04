package com.joseleandro.scaniqo.ui.documents

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joseleandro.scaniqo.domain.repository.DocumentRepository
import com.joseleandro.scaniqo.domain.repository.TagRepository
import com.joseleandro.scaniqo.ui.documents.event.DocumentsEvent
import com.joseleandro.scaniqo.ui.documents.state.DocumentsUiState
import com.joseleandro.scaniqo.ui.documents.state.DocumentTagIndex
import com.joseleandro.scaniqo.domain.model.TagModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Observa a biblioteca e mantém apenas o filtro da lista. */
@OptIn(ExperimentalCoroutinesApi::class)
class DocumentsViewModel(
    private val repository: DocumentRepository,
    private val tagRepository: TagRepository,
    private val savedStateHandle: SavedStateHandle,
    private val computationDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(
        DocumentsUiState(selectedTagId = savedStateHandle[SELECTED_TAG_ID]),
    )
    val uiState: StateFlow<DocumentsUiState> = mutableUiState.asStateFlow()
    private var observationJob: Job? = null
    private var tagsLoaded = false
    private var documentIndex = DocumentTagIndex.build(emptyList())
    private var tagsById: Map<Long, TagModel> = emptyMap()
    private var lastCreatedDocumentId: Long? = savedStateHandle[LAST_CREATED_DOCUMENT_ID]

    init {
        observeDocuments()
    }

    fun onEvent(event: DocumentsEvent) {
        when (event) {
            is DocumentsEvent.SelectTag -> updateState {
                it.copy(selectedTagId = validTagId(event.tagId))
            }
            DocumentsEvent.RetryLoad -> {
                if (uiState.value.loadError) observeDocuments()
            }
            is DocumentsEvent.DocumentCreated -> {
                if (lastCreatedDocumentId == event.documentId) return
                lastCreatedDocumentId = event.documentId
                updateState {
                    // Uma nova captura deve continuar visível quando sua tag difere do filtro.
                    if (it.selectedTagId == null) it
                    else it.copy(selectedTagId = validTagId(event.tagId))
                }
            }
        }
    }

    private fun observeDocuments() {
        val previousJob = observationJob
        previousJob?.cancel()
        updateState { it.copy(isLoading = true, loadError = false) }
        observationJob = viewModelScope.launch {
            previousJob?.cancelAndJoin()
            try {
                combine(
                    repository.observeDocuments().mapLatest { documents ->
                        val context = currentCoroutineContext()
                        DocumentTagIndex.build(documents) { context.ensureActive() }
                    },
                    tagRepository.observeTags(),
                ) { index, tags -> Triple(index, tags, tags.associateBy { it.id }) }
                    .flowOn(computationDispatcher)
                    .collect { (index, tags, tagMap) ->
                        tagsLoaded = true
                        documentIndex = index
                        tagsById = tagMap
                        updateState {
                            it.copy(
                                documents = index.documents,
                                tags = tags,
                                selectedTagId = it.selectedTagId?.takeIf(tagsById::containsKey),
                                isLoading = false,
                                loadError = false,
                            )
                        }
                    }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                updateState { it.copy(isLoading = false, loadError = true) }
            }
        }
    }

    private fun validTagId(tagId: Long?): Long? =
        tagId?.takeIf { id -> !tagsLoaded || tagsById.containsKey(id) }

    private fun updateState(update: (DocumentsUiState) -> DocumentsUiState) {
        mutableUiState.update { current ->
            val updated = update(current)
            updated.withPreparedLookups(documentIndex, tagsById)
        }
        savedStateHandle[SELECTED_TAG_ID] = uiState.value.selectedTagId
        savedStateHandle[LAST_CREATED_DOCUMENT_ID] = lastCreatedDocumentId
    }

    private companion object {
        const val SELECTED_TAG_ID = "documents.selectedTagId"
        const val LAST_CREATED_DOCUMENT_ID = "documents.lastCreatedDocumentId"
    }
}
