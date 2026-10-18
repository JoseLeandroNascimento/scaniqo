package com.joseleandro.scaniqo.ui.documentCommon.model

import com.joseleandro.scaniqo.domain.model.DocumentModel

/** Referência de seleção; não contém senhas nem copia o conteúdo dos PDFs. */
data class DocumentMergeItem(
    val uri: String,
    val name: String,
    val documentId: Long? = null,
    val pageCount: Int? = null,
    val protectionKeyId: String? = null,
    /** Representa as páginas da sessão aberta, nunca uma nova fonte de importação. */
    val isCurrentDocument: Boolean = false,
) {
    val key: String get() = documentId?.let { "document:$it" } ?: "uri:$uri"
}

fun DocumentModel.toMergeItem(): DocumentMergeItem = DocumentMergeItem(
    uri = pdfUri,
    name = name,
    documentId = id,
    pageCount = pageCount,
    protectionKeyId = protectionKeyId,
)

/** A mesma URI pode aparecer no app e no seletor; mantém a posição e prefere os metadados do app. */
internal fun uniqueMergeItems(items: List<DocumentMergeItem>): List<DocumentMergeItem> {
    val result = mutableListOf<DocumentMergeItem>()
    items.filter { it.uri.isNotBlank() }.forEach { item ->
        val matches = result.indices.filter { result[it].key == item.key || result[it].uri == item.uri }
        if (matches.isEmpty()) {
            result += item
        } else {
            val position = matches.first()
            val previous = result[position]
            result[position] = when {
                previous.isCurrentDocument && !item.isCurrentDocument -> previous
                item.documentId == null && previous.documentId != null -> previous
                else -> item
            }
            matches.drop(1).asReversed().forEach(result::removeAt)
        }
    }
    return result
}
