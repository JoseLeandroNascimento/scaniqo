package com.joseleandro.scaniqo.ui.documents

import com.joseleandro.scaniqo.domain.model.DocumentModel
import com.joseleandro.scaniqo.domain.model.ScannedDocument
import com.joseleandro.scaniqo.domain.model.TagModel

/** Estado compartilhado pela lista e pelo visualizador de documentos. */
data class DocumentsUiState(
    val documents: List<DocumentModel> = emptyList(),
    val tags: List<TagModel> = emptyList(),
    val selectedTagId: Long? = null,
    val isLoading: Boolean = true,
    val loadError: Boolean = false,
    val isScanning: Boolean = false,
    val isPreparingScan: Boolean = false,
    val editor: DocumentEditorUiState? = null,
    val pendingDeletion: DocumentModel? = null,
    val isDeleting: Boolean = false,
    val busyDocumentId: Long? = null,
    val message: DocumentMessage? = null,
) {
    val visibleDocuments: List<DocumentModel>
        get() = selectedTagId?.let { tagId ->
            documents.filter { it.tagId == tagId }
        } ?: documents
}

data class DocumentEditorUiState(
    val documentId: Long? = null,
    val draft: ScannedDocument? = null,
    val name: String,
    val tagId: Long? = null,
    val isSaving: Boolean = false,
    val nameError: DocumentNameError? = null,
    val saveError: Boolean = false,
)

enum class DocumentNameError {
    REQUIRED,
    TOO_LONG,
}

enum class DocumentMessage {
    SAVED,
    UPDATED,
    DELETED,
    DOWNLOADED,
    SCAN_ERROR,
    PREPARE_ERROR,
    EXPORT_ERROR,
    SHARE_ERROR,
    DELETE_ERROR,
}
