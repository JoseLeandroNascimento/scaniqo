package com.joseleandro.scaniqo.ui.documentEditor.model

/** Coordenadas da folha em pontos, com origem superior esquerda como no Canvas. */
internal data class DocumentLiveRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)

internal data class DocumentLiveTransform(
    val scaleX: Float,
    val skewX: Float,
    val translateX: Float,
    val skewY: Float,
    val scaleY: Float,
    val translateY: Float,
) {
    fun x(x: Float, y: Float): Float = scaleX * x + skewX * y + translateX
    fun y(x: Float, y: Float): Float = skewY * x + scaleY * y + translateY

    fun matrixValues(): FloatArray = floatArrayOf(
        scaleX, skewX, translateX,
        skewY, scaleY, translateY,
        0f, 0f, 1f,
    )
}

internal data class DocumentLivePageNumber(
    val left: Float,
    val baseline: Float,
    val background: DocumentLiveRect,
)
