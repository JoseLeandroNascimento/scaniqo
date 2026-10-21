package com.joseleandro.scaniqo.ui.documentEditor

/** Converte a página da busca para a cópia de trabalho, que pode ter sido editada desde a busca. */
internal fun initialViewerPageIndex(pageNumber: Int?, pageCount: Int): Int? {
    if (pageNumber == null || pageCount <= 0) return null
    return pageNumber.coerceIn(1, pageCount) - 1
}
