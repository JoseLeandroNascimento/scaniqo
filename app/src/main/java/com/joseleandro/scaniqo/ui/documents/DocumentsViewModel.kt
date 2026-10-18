package com.joseleandro.scaniqo.ui.documents

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joseleandro.scaniqo.domain.model.DOCUMENT_NAME_MAX_LENGTH
import com.joseleandro.scaniqo.domain.model.DocumentModel
import com.joseleandro.scaniqo.domain.model.ScannedDocument
import com.joseleandro.scaniqo.domain.repository.DocumentRepository
import com.joseleandro.scaniqo.domain.repository.TagRepository
import com.joseleandro.scaniqo.ui.documentCommon.effect.DocumentActionEffect
import com.joseleandro.scaniqo.ui.documentCommon.event.DocumentActionEvent
import com.joseleandro.scaniqo.ui.documentCommon.state.DocumentMessage
import com.joseleandro.scaniqo.ui.documentCommon.state.DocumentMetadataUiState
import com.joseleandro.scaniqo.ui.documentCommon.state.DocumentNameError
import com.joseleandro.scaniqo.ui.documents.event.DocumentsEvent
import com.joseleandro.scaniqo.ui.documents.state.DocumentsUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DocumentsViewModel(
    private val repository: DocumentRepository,
    private val tagRepository: TagRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private var pendingDownloadId: Long? = savedStateHandle[PENDING_DOWNLOAD_ID]
    private val mutableUiState = MutableStateFlow(
        DocumentsUiState(
            selectedTagId = savedStateHandle[SELECTED_TAG_ID],
            isScanning = savedStateHandle[IS_SCANNING] ?: false,
            editor = restoreEditor(),
            busyDocumentId = pendingDownloadId,
        ),
    )
    val uiState: StateFlow<DocumentsUiState> = mutableUiState.asStateFlow()

    // Um único consumidor executa os efeitos; a recomposição não repete intents.
    private val effectChannel = Channel<DocumentActionEffect>(Channel.UNLIMITED)
    val effects: Flow<DocumentActionEffect> = effectChannel.receiveAsFlow()

    private var observationJob: Job? = null

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
        }
    }

    fun onDocumentAction(event: DocumentActionEvent) {
        when (event) {
            DocumentActionEvent.StartScan -> startScan()
            DocumentActionEvent.ImportStarted -> {
                if (canStartAction()) updateState { it.copy(isPreparingScan = true, message = null) }
            }
            is DocumentActionEvent.ImportPrepared -> updateState {
                it.copy(
                    isPreparingScan = false,
                    editor = DocumentMetadataUiState(
                        draft = event.scan,
                        name = event.name.take(DOCUMENT_NAME_MAX_LENGTH),
                        tagId = it.selectedTagId,
                    ),
                )
            }
            DocumentActionEvent.ImportCancelled -> updateState { it.copy(isPreparingScan = false) }
            is DocumentActionEvent.ScanCompleted -> prepareScan(event.pageUris)
            DocumentActionEvent.ScanCancelled -> finishScanner()
            DocumentActionEvent.ScanFailed -> finishScanner(DocumentMessage.SCAN_ERROR)
            is DocumentActionEvent.EditMetadata -> openEditor(event.document)
            is DocumentActionEvent.NameChanged -> updateEditor {
                it.copy(name = event.name, nameError = null, saveError = false)
            }
            is DocumentActionEvent.TagChanged -> updateEditor {
                it.copy(tagId = validTagId(event.tagId), saveError = false)
            }
            DocumentActionEvent.SaveDocument -> saveDocument()
            DocumentActionEvent.DismissEditor -> dismissEditor()
            is DocumentActionEvent.RequestDelete -> {
                if (canStartAction()) {
                    updateState { it.copy(pendingDeletion = event.document, message = null) }
                }
            }
            DocumentActionEvent.ConfirmDelete -> deleteDocument()
            DocumentActionEvent.DismissDelete -> {
                if (!isBusy()) {
                    updateState {
                        it.copy(
                            pendingDeletion = null,
                            message = it.message.takeUnless { message ->
                                message == DocumentMessage.DELETE_ERROR
                            },
                        )
                    }
                }
            }
            is DocumentActionEvent.Download -> requestDownload(event.document)
            is DocumentActionEvent.ExportDestinationSelected -> exportDocument(event.uri)
            DocumentActionEvent.ExportFailed -> {
                pendingDownloadId = null
                updateState {
                    it.copy(busyDocumentId = null, message = DocumentMessage.EXPORT_ERROR)
                }
            }
            is DocumentActionEvent.Share -> shareDocument(event.document)
            DocumentActionEvent.ShareFailed -> updateState {
                it.copy(message = DocumentMessage.SHARE_ERROR)
            }
            DocumentActionEvent.DismissMessage -> updateState { it.copy(message = null) }
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
                    repository.observeDocuments(),
                    tagRepository.observeTags(),
                ) { documents, tags -> documents to tags }.collect { (documents, tags) ->
                    val tagIds = tags.mapTo(mutableSetOf()) { it.id }
                    updateState {
                        // Excluir uma tag remove também filtros e seleções que ficaram inválidos.
                        it.copy(
                            documents = documents,
                            tags = tags,
                            selectedTagId = it.selectedTagId?.takeIf(tagIds::contains),
                            editor = it.editor?.copy(
                                tagId = it.editor.tagId?.takeIf(tagIds::contains),
                            ),
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

    private fun startScan() {
        if (!canStartAction()) return
        updateState { it.copy(isScanning = true, message = null) }
        effectChannel.trySend(DocumentActionEffect.LaunchScanner)
    }

    private fun finishScanner(message: DocumentMessage? = null) {
        if (!uiState.value.isScanning) return
        updateState { it.copy(isScanning = false, message = message) }
    }

    private fun prepareScan(pageUris: List<String>) {
        if (!uiState.value.isScanning || uiState.value.isPreparingScan) return
        if (pageUris.isEmpty()) {
            finishScanner(DocumentMessage.PREPARE_ERROR)
            return
        }
        updateState { it.copy(isScanning = false, isPreparingScan = true, message = null) }
        viewModelScope.launch {
            try {
                val draft = repository.prepareScan(pageUris)
                val date = SimpleDateFormat("dd-MM-yyyy HH.mm", Locale.getDefault())
                    .format(Date(draft.createdAt))
                updateState {
                    it.copy(
                        editor = DocumentMetadataUiState(
                            draft = draft,
                            name = "Documento $date",
                            tagId = it.selectedTagId,
                        ),
                    )
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                updateState { it.copy(message = DocumentMessage.PREPARE_ERROR) }
            } finally {
                updateState { it.copy(isPreparingScan = false) }
            }
        }
    }

    private fun openEditor(document: DocumentModel) {
        if (!canStartAction()) return
        updateState {
            it.copy(
                editor = DocumentMetadataUiState(
                    documentId = document.id,
                    name = document.name,
                    tagId = validTagId(document.tagId),
                ),
                message = null,
            )
        }
    }

    private fun updateEditor(update: (DocumentMetadataUiState) -> DocumentMetadataUiState) {
        if (isBusy()) return
        updateState { state -> state.copy(editor = state.editor?.let(update)) }
    }

    private fun saveDocument() {
        val editor = uiState.value.editor ?: return
        if (isBusy()) return
        val name = editor.name.trim()
        val nameError = when {
            name.isBlank() -> DocumentNameError.REQUIRED
            name.length > DOCUMENT_NAME_MAX_LENGTH -> DocumentNameError.TOO_LONG
            else -> null
        }
        if (nameError != null) {
            updateState {
                it.copy(editor = editor.copy(nameError = nameError, saveError = false))
            }
            return
        }

        // Marca a operação antes de lançar a coroutine para impedir salvamentos duplicados.
        updateState {
            it.copy(
                editor = editor.copy(isSaving = true, nameError = null, saveError = false),
                message = null,
            )
        }
        viewModelScope.launch {
            try {
                if (editor.documentId != null) {
                    repository.update(editor.documentId, name, editor.tagId)
                } else {
                    repository.create(checkNotNull(editor.draft), name, editor.tagId)
                }
                updateState {
                    it.copy(
                        editor = null,
                        // Uma nova captura deve continuar visível quando sua tag difere do filtro.
                        selectedTagId = if (editor.documentId == null && it.selectedTagId != null) {
                            editor.tagId
                        } else {
                            it.selectedTagId
                        },
                        message = if (editor.documentId == null) {
                            DocumentMessage.SAVED
                        } else {
                            DocumentMessage.UPDATED
                        },
                    )
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                updateState { it.copy(editor = it.editor?.copy(saveError = true)) }
            } finally {
                updateState { it.copy(editor = it.editor?.copy(isSaving = false)) }
            }
        }
    }

    private fun dismissEditor() {
        val editor = uiState.value.editor ?: return
        if (isBusy()) return
        val draft = editor.draft
        if (draft == null) {
            updateState { it.copy(editor = null) }
            return
        }

        updateState { it.copy(editor = editor.copy(isSaving = true, saveError = false)) }
        viewModelScope.launch {
            try {
                repository.discardScan(draft)
                updateState { it.copy(editor = null) }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                // Preserva a referência ao rascunho para permitir repetir a limpeza.
                updateState { it.copy(editor = it.editor?.copy(saveError = true)) }
            } finally {
                updateState { it.copy(editor = it.editor?.copy(isSaving = false)) }
            }
        }
    }

    private fun deleteDocument() {
        val document = uiState.value.pendingDeletion ?: return
        if (isBusy()) return
        updateState { it.copy(isDeleting = true, message = null) }
        viewModelScope.launch {
            try {
                repository.delete(document)
                updateState {
                    it.copy(pendingDeletion = null, message = DocumentMessage.DELETED)
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                updateState { it.copy(message = DocumentMessage.DELETE_ERROR) }
            } finally {
                updateState { it.copy(isDeleting = false) }
            }
        }
    }

    private fun requestDownload(document: DocumentModel) {
        if (!canStartAction()) return
        pendingDownloadId = document.id
        updateState { it.copy(busyDocumentId = document.id, message = null) }
        effectChannel.trySend(DocumentActionEffect.ChooseDownloadLocation(pdfFileName(document.name)))
    }

    private fun exportDocument(targetUri: String?) {
        val documentId = pendingDownloadId ?: return
        // Consome o retorno do seletor uma única vez, inclusive quando o usuário cancela.
        pendingDownloadId = null
        if (targetUri == null) {
            updateState { it.copy(busyDocumentId = null) }
            return
        }
        updateState { it.copy(busyDocumentId = documentId, message = null) }
        viewModelScope.launch {
            try {
                val document = checkNotNull(repository.findById(documentId))
                repository.export(document, targetUri)
                updateState { it.copy(message = DocumentMessage.DOWNLOADED) }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                updateState { it.copy(message = DocumentMessage.EXPORT_ERROR) }
            } finally {
                updateState { it.copy(busyDocumentId = null) }
            }
        }
    }

    private fun shareDocument(document: DocumentModel) {
        if (!canStartAction()) return
        updateState { it.copy(busyDocumentId = document.id, message = null) }
        viewModelScope.launch {
            try {
                val uri = repository.prepareShare(document)
                effectChannel.send(DocumentActionEffect.SharePdf(uri, pdfFileName(document.name)))
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                updateState { it.copy(message = DocumentMessage.SHARE_ERROR) }
            } finally {
                updateState { it.copy(busyDocumentId = null) }
            }
        }
    }

    private fun pdfFileName(name: String): String {
        val sanitized = name.map { character ->
            if (character in "\\/:*?\"<>|" || character.isISOControl()) '_' else character
        }.joinToString("").trim().ifBlank { "Documento" }
        return if (sanitized.endsWith(".pdf", ignoreCase = true)) sanitized else "$sanitized.pdf"
    }

    private fun validTagId(tagId: Long?): Long? =
        tagId?.takeIf { id -> uiState.value.tags.any { it.id == id } }

    private fun isBusy(): Boolean = uiState.value.actions.isBusy

    private fun canStartAction(): Boolean =
        !isBusy() && uiState.value.editor == null && uiState.value.pendingDeletion == null

    private fun updateState(update: (DocumentsUiState) -> DocumentsUiState) {
        mutableUiState.update(update)
        persistEditorAndActions()
    }

    private fun persistEditorAndActions() {
        val state = uiState.value
        val editor = state.editor
        val draft = editor?.draft
        // Somente valores pequenos vão para o SavedStateHandle; os PDFs ficam no armazenamento.
        savedStateHandle[SELECTED_TAG_ID] = state.selectedTagId
        savedStateHandle[IS_SCANNING] = state.isScanning
        savedStateHandle[PENDING_DOWNLOAD_ID] = pendingDownloadId
        savedStateHandle[HAS_EDITOR] = editor != null
        savedStateHandle[EDITOR_ID] = editor?.documentId
        savedStateHandle[EDITOR_NAME] = editor?.name
        savedStateHandle[EDITOR_TAG_ID] = editor?.tagId
        savedStateHandle[DRAFT_PDF_URI] = draft?.pdfUri
        savedStateHandle[DRAFT_PAGE_COUNT] = draft?.pageCount
        savedStateHandle[DRAFT_CREATED_AT] = draft?.createdAt
        savedStateHandle[DRAFT_SIZE_BYTES] = draft?.sizeBytes
        savedStateHandle[DRAFT_THUMBNAIL_URI] = draft?.thumbnailUri
        savedStateHandle[DRAFT_PROTECTION_KEY_ID] = draft?.protectionKeyId
    }

    private fun restoreEditor(): DocumentMetadataUiState? {
        if (savedStateHandle.get<Boolean>(HAS_EDITOR) != true) return null
        val id = savedStateHandle.get<Long>(EDITOR_ID)
        val draft = savedStateHandle.get<String>(DRAFT_PDF_URI)?.let { uri ->
            ScannedDocument(
                pdfUri = uri,
                pageCount = savedStateHandle[DRAFT_PAGE_COUNT] ?: return null,
                createdAt = savedStateHandle[DRAFT_CREATED_AT] ?: return null,
                sizeBytes = savedStateHandle[DRAFT_SIZE_BYTES] ?: return null,
                thumbnailUri = savedStateHandle[DRAFT_THUMBNAIL_URI],
                protectionKeyId = savedStateHandle[DRAFT_PROTECTION_KEY_ID],
            )
        }
        if (id == null && draft == null) return null
        return DocumentMetadataUiState(
            documentId = id,
            draft = draft,
            name = savedStateHandle[EDITOR_NAME] ?: "",
            tagId = savedStateHandle[EDITOR_TAG_ID],
        )
    }

    override fun onCleared() {
        effectChannel.close()
        super.onCleared()
    }

    private companion object {
        const val SELECTED_TAG_ID = "documents.selectedTagId"
        const val IS_SCANNING = "documents.isScanning"
        const val PENDING_DOWNLOAD_ID = "documents.pendingDownloadId"
        const val HAS_EDITOR = "documents.editor.present"
        const val EDITOR_ID = "documents.editor.id"
        const val EDITOR_NAME = "documents.editor.name"
        const val EDITOR_TAG_ID = "documents.editor.tagId"
        const val DRAFT_PDF_URI = "documents.draft.pdfUri"
        const val DRAFT_PAGE_COUNT = "documents.draft.pageCount"
        const val DRAFT_CREATED_AT = "documents.draft.createdAt"
        const val DRAFT_SIZE_BYTES = "documents.draft.sizeBytes"
        const val DRAFT_THUMBNAIL_URI = "documents.draft.thumbnailUri"
        const val DRAFT_PROTECTION_KEY_ID = "documents.draft.protectionKeyId"
    }
}
