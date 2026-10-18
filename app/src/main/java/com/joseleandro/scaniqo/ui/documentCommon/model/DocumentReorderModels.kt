package com.joseleandro.scaniqo.ui.documentCommon.model

import com.joseleandro.scaniqo.ui.documentCommon.util.moveDocumentOrder

/** Coordenadas dos itens visíveis, relativas à área rolável da lista. */
internal data class DocumentDragBounds(val id: String, val top: Int, val height: Int) {
    val center: Float get() = top + height / 2f
}

/** Rascunho local do gesto; cancelar basta descartá-lo, sem alterar o documento. */
internal data class DocumentReorderDrag(
    val initialOrder: List<String>,
    val itemId: String,
    val centerY: Float,
    val height: Int,
    val order: List<String> = initialOrder,
) {
    fun moveBy(deltaY: Float, visible: List<DocumentDragBounds>): DocumentReorderDrag =
        copy(centerY = centerY + deltaY).retarget(visible)

    fun retarget(visible: List<DocumentDragBounds>): DocumentReorderDrag {
        val currentIndex = order.indexOf(itemId)
        if (currentIndex < 0) return this
        val crossed = visible.mapNotNull { bounds ->
            val index = order.indexOf(bounds.id)
            when {
                index > currentIndex && centerY > bounds.center -> index
                index in 0 until currentIndex && centerY < bounds.center -> index
                else -> null
            }
        }
        val targetIndex = when {
            crossed.any { it > currentIndex } -> crossed.max()
            crossed.any { it < currentIndex } -> crossed.min()
            else -> currentIndex
        }
        return copy(order = moveDocumentOrder(order, itemId, targetIndex))
    }

    fun committedOrder(): List<String>? = order.takeIf { it != initialOrder }
}
