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
    private var preparedDocumentIndex: DocumentTagIndex? = null
    private var preparedTagIndex: Map<Long, TagModel>? = null

    // Cada copy cria caches próprios, sem conservar resultados de documentos/tags anteriores.
    val visibleDocuments: List<DocumentModel> by lazy {
        preparedDocumentIndex?.visibleDocuments(selectedTagId) ?: selectedTagId?.let { tagId ->
            documents.filter { it.tagId == tagId }
        } ?: documents
    }
    val tagsById: Map<Long, TagModel> by lazy {
        preparedTagIndex ?: tags.associateBy { it.id }
    }

    /** Prepara outro snapshot antes de publicá-lo; nunca altera um estado já observado pela UI. */
    internal fun withPreparedLookups(
        index: DocumentTagIndex,
        tagLookup: Map<Long, TagModel>,
    ): DocumentsUiState = copy(documents = index.documents).also {
        it.preparedDocumentIndex = index
        it.preparedTagIndex = tagLookup
    }
}
