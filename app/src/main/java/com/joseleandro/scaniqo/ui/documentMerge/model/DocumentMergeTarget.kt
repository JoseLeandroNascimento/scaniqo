package com.joseleandro.scaniqo.ui.documentMerge.model

import com.joseleandro.scaniqo.ui.documentCommon.model.DocumentMergeItem

/** Destino existente; sem destino, a seleção cria um documento novo. */
data class DocumentMergeTarget(
    val documentId: Long,
    val pageCount: Int,
    val selectedPageIndex: Int = 0,
    val isProtected: Boolean = false,
    val currentDocument: DocumentMergeItem? = null,
)

internal enum class DocumentMergePosition {
    START, AFTER_PAGE, END;

    fun insertionIndex(pageCount: Int, afterPage: String): Int? {
        if (pageCount < 1) return null
        return when (this) {
            START -> 0
            AFTER_PAGE -> afterPage.toIntOrNull()?.takeIf { it in 1..pageCount }
            END -> pageCount
        }
    }
}
