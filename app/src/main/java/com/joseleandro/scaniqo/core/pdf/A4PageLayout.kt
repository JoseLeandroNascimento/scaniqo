package com.joseleandro.scaniqo.core.pdf

import kotlin.math.min

/** Medidas em pontos PDF: 72 pontos equivalem a uma polegada. */
object A4PageLayout {
    const val PAGE_WIDTH = 595
    const val PAGE_HEIGHT = 842
    const val MARGIN = 32f

    data class Bounds(val left: Float, val top: Float, val right: Float, val bottom: Float) {
        val width: Float get() = right - left
        val height: Float get() = bottom - top
    }

    /** Preserva a proporção original e centraliza a imagem dentro das margens. */
    fun fit(imageWidth: Int, imageHeight: Int): Bounds {
        require(imageWidth > 0 && imageHeight > 0) { "A imagem precisa ter dimensões válidas." }
        val scale = min(
            (PAGE_WIDTH - 2 * MARGIN) / imageWidth,
            (PAGE_HEIGHT - 2 * MARGIN) / imageHeight
        )
        val width = imageWidth * scale
        val height = imageHeight * scale
        val left = (PAGE_WIDTH - width) / 2
        val top = (PAGE_HEIGHT - height) / 2
        return Bounds(left, top, left + width, top + height)
    }
}
