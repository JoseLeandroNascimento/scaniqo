package com.joseleandro.scaniqo.ui.documents.state

import com.joseleandro.scaniqo.domain.model.DocumentModel
import com.joseleandro.scaniqo.domain.model.TagModel

/** Estado exclusivo da listagem e do filtro de documentos. */
data class DocumentsUiState(
    val documents: List<DocumentModel> = emptyList(),
    val tags: List<TagModel> = emptyList(),
    val selectedTagId: Long? = null,
    val isLoading: Boolean = true,
    val loadError: Boolean = false,
) {
    val visibleDocuments: List<DocumentModel>
        get() = selectedTagId?.let { tagId ->
            documents.filter { it.tagId == tagId }
        } ?: documents
}
