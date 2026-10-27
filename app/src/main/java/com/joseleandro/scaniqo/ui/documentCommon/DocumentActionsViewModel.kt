package com.joseleandro.scaniqo.ui.documentCommon

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joseleandro.scaniqo.core.ads.RewardedTool
import com.joseleandro.scaniqo.core.ads.ToolAdAccessPolicy
import com.joseleandro.scaniqo.core.ads.ToolAdPermit
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
import com.joseleandro.scaniqo.ui.documentCommon.state.DocumentActionsUiState
import com.joseleandro.scaniqo.ui.documentCommon.state.DocumentCreationResult
import com.joseleandro.scaniqo.ui.documentCommon.state.DocumentGeneratedResult
import java.util.Date
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DocumentActionsViewModel(
    private val repository: DocumentRepository,
    private val tagRepository: TagRepository,
    private val savedStateHandle: SavedStateHandle,
    private val defaultDocumentName: (Long) -> String = { createdAt ->
        "Document " + java.text.DateFormat.getDateTimeInstance().format(Date(createdAt))
    },
    private val adAccess: ToolAdAccessPolicy? = null,
) : ViewModel() {
    private var pendingDownloadId: Long? = savedStateHandle[PENDING_DOWNLOAD_ID]
    private var pendingDefaultTagId: Long? = savedStateHandle[PENDING_DEFAULT_TAG_ID]
    private val mutableUiState = MutableStateFlow(
        DocumentActionsUiState(
            pendingCreation = savedStateHandle.get<Long>(CREATED_DOCUMENT_ID)?.let { id ->
                DocumentCreationResult(id, savedStateHandle[CREATED_DOCUMENT_TAG_ID])
            },
            isScanning = savedStateHandle[IS_SCANNING] ?: false,
            generatedResult = savedStateHandle.get<String>(GENERATED_RESULT_OPERATION)?.let { operationId ->
                DocumentGeneratedResult(operationId, savedStateHandle[GENERATED_RESULT_DOCUMENT_ID])
            },
            editor = restoreEditor(),
            busyDocumentId = pendingDownloadId,
        ),
    )
    val uiState: StateFlow<DocumentActionsUiState> = mutableUiState.asStateFlow()

    // Um único consumidor executa os efeitos; a recomposição não repete intents.
    private val effectChannel = Channel<DocumentActionEffect>(Channel.UNLIMITED)
    val effects: Flow<DocumentActionEffect> = effectChannel.receiveAsFlow()

    private var observationJob: Job? = null
    private var tagsLoaded = false
    private var importPermit: ToolAdPermit? = null

    init {
        observeTags()
    }

    fun retryLoadingTags() {
        if (uiState.value.tagsLoadError) observeTags()
    }

    fun acknowledgeCreation(documentId: Long) {
        updateState {
            if (it.pendingCreation?.documentId == documentId) it.copy(pendingCreation = null)
            else it
        }
    }

    /** A transferência é síncrona: depois de true, apenas este ViewModel possui o arquivo temporário. */
    fun acceptGeneratedDraft(
        operationId: String,
        scan: ScannedDocument,
        suggestedName: String,
        suggestedTagId: Long?,
    ): Boolean {
        require(operationId.isNotBlank())
        val current = uiState.value
        if (current.editor?.draft?.pdfUri == scan.pdfUri &&
            current.editor.generatedOperationId == operationId) return true
        // Uma reentrega tardia não deve reabrir o formulário após salvar ou descartar.
        if (current.generatedResult?.operationId == operationId) return true
        val preparedMerge = operationId.startsWith("merge:") && current.isPreparingScan
        if (!canStartAction() && !(preparedMerge && current.editor == null &&
                current.pendingDeletion == null && !current.isDeleting &&
                current.busyDocumentId == null && !current.isScanning)) return false
        val defaultTag = if (preparedMerge) pendingDefaultTagId else suggestedTagId
        if (preparedMerge) pendingDefaultTagId = null
        updateState {
            it.copy(
                isPreparingScan = false,
                editor = DocumentMetadataUiState(
                    draft = scan,
                    generatedOperationId = operationId,
                    name = suggestedName.take(DOCUMENT_NAME_MAX_LENGTH),
                    tagId = validTagId(defaultTag),
                ),
                message = null,
            )
        }
        return true
    }

    fun acknowledgeGeneratedResult(operationId: String) {
        updateState {
            if (it.generatedResult?.operationId == operationId) it.copy(generatedResult = null)
            else it
        }
    }

    fun onDocumentAction(event: DocumentActionEvent) {
        when (event) {
            is DocumentActionEvent.StartScan -> startScan(event.defaultTagId)
            is DocumentActionEvent.ImportStarted -> {
                if (canStartAction()) {
                    pendingDefaultTagId = validTagId(event.defaultTagId)
                    updateState { it.copy(isPreparingScan = true, message = null) }
                }
            }
            is DocumentActionEvent.ImportPrepared -> {
                // O importador pode reenviar a entrega pendente após recriação.
                if (uiState.value.editor?.draft?.pdfUri == event.scan.pdfUri) return
                val defaultTagId = validTagId(pendingDefaultTagId)
                pendingDefaultTagId = null
                updateState {
                    it.copy(
                        isPreparingScan = false,
                        editor = DocumentMetadataUiState(
                            draft = event.scan,
                            requiresImportAd = true,
                            name = event.name.take(DOCUMENT_NAME_MAX_LENGTH),
                            tagId = defaultTagId,
                        ),
                    )
                }
            }
            DocumentActionEvent.ImportCancelled -> {
                pendingDefaultTagId = null
                updateState { it.copy(isPreparingScan = false) }
            }
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
            is DocumentActionEvent.SaveImportedDocument -> {
                val editor = uiState.value.editor
                if (editor?.requiresImportAd == true && editor.draft?.pdfUri == event.pdfUri) {
                    saveDocument(event.adUnavailable)
                }
            }
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
            DocumentActionEvent.RetryTags -> retryLoadingTags()
        }
    }

    private fun observeTags() {
        val previousJob = observationJob
        previousJob?.cancel()
        updateState { it.copy(isLoadingTags = true, tagsLoadError = false) }
        observationJob = viewModelScope.launch {
            previousJob?.cancelAndJoin()
            try {
                tagRepository.observeTags().collect { tags ->
                    tagsLoaded = true
                    val tagIds = tags.mapTo(mutableSetOf()) { it.id }
                    pendingDefaultTagId = pendingDefaultTagId?.takeIf(tagIds::contains)
                    updateState {
                        it.copy(
                            tags = tags,
                            editor = it.editor?.copy(
                                tagId = it.editor.tagId?.takeIf(tagIds::contains),
                            ),
                            isLoadingTags = false,
                            tagsLoadError = false,
                        )
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                updateState { it.copy(isLoadingTags = false, tagsLoadError = true) }
            }
        }
    }

    private fun startScan(defaultTagId: Long?) {
        if (!canStartAction()) return
        pendingDefaultTagId = validTagId(defaultTagId)
        updateState { it.copy(isScanning = true, message = null) }
        effectChannel.trySend(DocumentActionEffect.LaunchScanner)
    }

    private fun finishScanner(message: DocumentMessage? = null) {
        if (!uiState.value.isScanning) return
        pendingDefaultTagId = null
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
                updateState {
                    it.copy(
                        editor = DocumentMetadataUiState(
                            draft = draft,
                            name = defaultDocumentName(draft.createdAt).take(DOCUMENT_NAME_MAX_LENGTH),
                            tagId = validTagId(pendingDefaultTagId),
                        ),
                    )
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                updateState { it.copy(message = DocumentMessage.PREPARE_ERROR) }
            } finally {
                pendingDefaultTagId = null
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

    private fun saveDocument(adUnavailable: Boolean = false) {
        val editor = uiState.value.editor ?: return
        if (isBusy()) return
        // Uma tag só pode ser persistida após validar a seleção com o catálogo disponível.
        if (editor.tagId != null && (uiState.value.isLoadingTags || uiState.value.tagsLoadError)) return
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

        val permit = if (editor.requiresImportAd && editor.documentId == null && adAccess != null) {
            adAccess.acquire(RewardedTool.IMPORT_PDF, UUID.randomUUID().toString(), adUnavailable)
                ?: return
        } else null
        importPermit = permit

        // Marca a operação antes de lançar a coroutine para impedir salvamentos duplicados.
        updateState {
            it.copy(
                editor = editor.copy(isSaving = true, nameError = null, saveError = false),
                message = null,
            )
        }
        var started = false
        val saveJob = viewModelScope.launch {
            started = true
            var saved = false
            try {
                val createdId = if (editor.documentId != null) {
                    repository.update(editor.documentId, name, editor.tagId)
                    null
                } else {
                    repository.create(checkNotNull(editor.draft), name, editor.tagId)
                }
                saved = true
                updateState {
                    it.copy(
                        editor = null,
                        pendingCreation = createdId?.let { id ->
                            DocumentCreationResult(id, editor.tagId)
                        } ?: it.pendingCreation,
                        generatedResult = if (createdId != null && editor.generatedOperationId != null) {
                            DocumentGeneratedResult(editor.generatedOperationId, createdId)
                        } else it.generatedResult,
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
                finishImportPermit(permit, saved)
                updateState { it.copy(editor = it.editor?.copy(isSaving = false)) }
            }
        }
        saveJob.invokeOnCompletion {
            if (!started) finishImportPermit(permit, success = false)
        }
    }

    private fun finishImportPermit(permit: ToolAdPermit?, success: Boolean) {
        if (permit == null || importPermit !== permit) return
        importPermit = null
        adAccess?.finish(permit, success)
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
                updateState {
                    it.copy(
                        editor = null,
                        generatedResult = editor.generatedOperationId?.let { operationId ->
                            DocumentGeneratedResult(operationId, null)
                        } ?: it.generatedResult,
                    )
                }
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
                repository.moveToTrash(document.id)
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
        tagId?.takeIf { id -> !tagsLoaded || uiState.value.tags.any { it.id == id } }

    private fun isBusy(): Boolean = uiState.value.isBusy

    private fun canStartAction(): Boolean =
        !isBusy() && uiState.value.editor == null && uiState.value.pendingDeletion == null

    private fun updateState(update: (DocumentActionsUiState) -> DocumentActionsUiState) {
        mutableUiState.update(update)
        persistEditorAndActions()
    }

    private fun persistEditorAndActions() {
        val state = uiState.value
        val editor = state.editor
        val draft = editor?.draft
        // Somente valores pequenos vão para o SavedStateHandle; os PDFs ficam no armazenamento.
        savedStateHandle[PENDING_DEFAULT_TAG_ID] = pendingDefaultTagId
        savedStateHandle[CREATED_DOCUMENT_ID] = state.pendingCreation?.documentId
        savedStateHandle[CREATED_DOCUMENT_TAG_ID] = state.pendingCreation?.tagId
        savedStateHandle[GENERATED_RESULT_OPERATION] = state.generatedResult?.operationId
        savedStateHandle[GENERATED_RESULT_DOCUMENT_ID] = state.generatedResult?.documentId
        savedStateHandle[IS_SCANNING] = state.isScanning
        savedStateHandle[PENDING_DOWNLOAD_ID] = pendingDownloadId
        savedStateHandle[HAS_EDITOR] = editor != null
        savedStateHandle[EDITOR_ID] = editor?.documentId
        savedStateHandle[EDITOR_NAME] = editor?.name
        savedStateHandle[EDITOR_TAG_ID] = editor?.tagId
        savedStateHandle[EDITOR_GENERATED_OPERATION_ID] = editor?.generatedOperationId
        savedStateHandle[EDITOR_REQUIRES_IMPORT_AD] = editor?.requiresImportAd
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
            generatedOperationId = savedStateHandle[EDITOR_GENERATED_OPERATION_ID],
            requiresImportAd = savedStateHandle[EDITOR_REQUIRES_IMPORT_AD] ?: false,
            name = savedStateHandle[EDITOR_NAME] ?: "",
            tagId = savedStateHandle[EDITOR_TAG_ID],
        )
    }

    override fun onCleared() {
        finishImportPermit(importPermit, success = false)
        effectChannel.close()
        super.onCleared()
    }

    private companion object {
        const val PENDING_DEFAULT_TAG_ID = "documents.pendingDefaultTagId"
        const val CREATED_DOCUMENT_ID = "documents.createdDocumentId"
        const val CREATED_DOCUMENT_TAG_ID = "documents.createdDocumentTagId"
        const val GENERATED_RESULT_OPERATION = "documents.generatedResult.operationId"
        const val GENERATED_RESULT_DOCUMENT_ID = "documents.generatedResult.documentId"
        const val IS_SCANNING = "documents.isScanning"
        const val PENDING_DOWNLOAD_ID = "documents.pendingDownloadId"
        const val HAS_EDITOR = "documents.editor.present"
        const val EDITOR_ID = "documents.editor.id"
        const val EDITOR_NAME = "documents.editor.name"
        const val EDITOR_TAG_ID = "documents.editor.tagId"
        const val EDITOR_GENERATED_OPERATION_ID = "documents.editor.generatedOperationId"
        const val EDITOR_REQUIRES_IMPORT_AD = "documents.editor.requiresImportAd"
        const val DRAFT_PDF_URI = "documents.draft.pdfUri"
        const val DRAFT_PAGE_COUNT = "documents.draft.pageCount"
        const val DRAFT_CREATED_AT = "documents.draft.createdAt"
        const val DRAFT_SIZE_BYTES = "documents.draft.sizeBytes"
        const val DRAFT_THUMBNAIL_URI = "documents.draft.thumbnailUri"
        const val DRAFT_PROTECTION_KEY_ID = "documents.draft.protectionKeyId"
    }
}
