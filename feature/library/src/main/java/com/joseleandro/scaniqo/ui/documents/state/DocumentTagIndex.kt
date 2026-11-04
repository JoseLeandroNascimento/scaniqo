package com.joseleandro.scaniqo.ui.documents.state

import com.joseleandro.scaniqo.domain.model.DocumentModel

/** Uma passagem pela biblioteca por atualização, sem varrê-la ao alternar filtros. */
internal class DocumentTagIndex private constructor(
    val documents: List<DocumentModel>,
    private val documentsByTag: Map<Long, List<DocumentModel>>,
) {
    fun visibleDocuments(tagId: Long?): List<DocumentModel> =
        if (tagId == null) documents else documentsByTag[tagId].orEmpty()

    companion object {
        fun build(
            documents: List<DocumentModel>,
            checkCancellation: () -> Unit = {},
        ): DocumentTagIndex {
            val groups = mutableMapOf<Long, MutableList<DocumentModel>>()
            documents.forEachIndexed { index, document ->
                if (index % 256 == 0) checkCancellation()
                document.tagId?.let { tagId ->
                    groups.getOrPut(tagId) { ArrayList() }.add(document)
                }
            }
            checkCancellation()
            return DocumentTagIndex(documents, groups)
        }
    }
}
