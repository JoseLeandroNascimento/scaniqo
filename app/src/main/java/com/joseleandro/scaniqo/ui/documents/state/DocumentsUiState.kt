package com.joseleandro.scaniqo.ui.documents.state

import com.joseleandro.scaniqo.domain.model.DocumentModel
import com.joseleandro.scaniqo.domain.model.TagModel
import com.joseleandro.scaniqo.ui.documentCommon.state.DocumentActionsUiState
import com.joseleandro.scaniqo.ui.documentCommon.state.DocumentMessage
import com.joseleandro.scaniqo.ui.documentCommon.state.DocumentMetadataUiState

/** Estado da lista; ações comuns são expostas como uma projeção imutável. */
data class DocumentsUiState(
    val documents: List<DocumentModel> = emptyList(),
    val tags: List<TagModel> = emptyList(),
    val selectedTagId: Long? = null,
    val isLoading: Boolean = true,
    val loadError: Boolean = false,
    val isScanning: Boolean = false,
    val isPreparingScan: Boolean = false,
    val editor: DocumentMetadataUiState? = null,
    val pendingDeletion: DocumentModel? = null,
    val isDeleting: Boolean = false,
    val busyDocumentId: Long? = null,
    val message: DocumentMessage? = null,
) {
    val actions: DocumentActionsUiState
        get() = DocumentActionsUiState(
            tags = tags,
            editor = editor,
            pendingDeletion = pendingDeletion,
            isDeleting = isDeleting,
            busyDocumentId = busyDocumentId,
            isScanning = isScanning,
            isPreparingScan = isPreparingScan,
            message = message,
        )

    val visibleDocuments: List<DocumentModel>
        get() = selectedTagId?.let { tagId ->
            documents.filter { it.tagId == tagId }
        } ?: documents
}
