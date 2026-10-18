package com.joseleandro.scaniqo.ui.documentEditor.util

/** Remove e insere no índice final; mover 1 → 10 significa ocupar a décima posição. */
internal fun movePageOrder(order: List<String>, pageId: String, targetIndex: Int): List<String> {
    val currentIndex = order.indexOf(pageId)
    if (currentIndex < 0 || targetIndex !in order.indices || currentIndex == targetIndex) return order
    return order.toMutableList().apply { add(targetIndex, removeAt(currentIndex)) }
}

/** Velocidade em pixels/segundo, gradual nas bordas e limitada fora da lista. */
internal fun pageDragScrollSpeed(
    centerY: Float,
    viewportStart: Float,
    viewportEnd: Float,
    edgeSize: Float,
    maxSpeed: Float,
): Float {
    if (viewportEnd <= viewportStart || edgeSize <= 0f) return 0f
    val edge = minOf(edgeSize, (viewportEnd - viewportStart) / 2)
    return when {
        centerY < viewportStart + edge ->
            -maxSpeed * ((viewportStart + edge - centerY) / edge).coerceIn(0f, 1f)
        centerY > viewportEnd - edge ->
            maxSpeed * ((centerY - viewportEnd + edge) / edge).coerceIn(0f, 1f)
        else -> 0f
    }
}
