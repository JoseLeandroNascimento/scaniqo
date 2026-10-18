package com.joseleandro.scaniqo.ui.documentEditor.model

import com.joseleandro.scaniqo.domain.model.PdfPageSpec

internal enum class DocumentPageScopeMode { CURRENT, SELECTED, ALL }

/** IDs estáveis evitam aplicar o ajuste na página errada depois de uma reordenação. */
internal data class DocumentPageScope(
    val mode: DocumentPageScopeMode = DocumentPageScopeMode.CURRENT,
    val pageIds: Set<String> = emptySet(),
) {
    fun targets(pages: List<PdfPageSpec>, currentPageId: String): Set<String> {
        val available = pages.mapTo(mutableSetOf()) { it.id }
        return when (mode) {
            DocumentPageScopeMode.CURRENT -> setOf(currentPageId).intersect(available)
            DocumentPageScopeMode.SELECTED -> pageIds.intersect(available)
            DocumentPageScopeMode.ALL -> available
        }
    }
}
