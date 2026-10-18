package com.joseleandro.scaniqo.ui.documentEditor.model

import com.joseleandro.scaniqo.ui.documentEditor.util.movePageOrder

/** Coordenadas dos itens visíveis, relativas à área rolável da lista. */
internal data class PageDragBounds(val id: String, val top: Int, val height: Int) {
    val center: Float get() = top + height / 2f
}

/** Rascunho local do gesto; cancelar basta descartá-lo, sem alterar o documento. */
internal data class PageReorderDrag(
    val initialOrder: List<String>,
    val pageId: String,
    val centerY: Float,
    val height: Int,
    val order: List<String> = initialOrder,
) {
    fun moveBy(deltaY: Float, visible: List<PageDragBounds>): PageReorderDrag =
        copy(centerY = centerY + deltaY).retarget(visible)

    fun retarget(visible: List<PageDragBounds>): PageReorderDrag {
        val currentIndex = order.indexOf(pageId)
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
        return copy(order = movePageOrder(order, pageId, targetIndex))
    }

    fun committedOrder(): List<String>? = order.takeIf { it != initialOrder }
}
