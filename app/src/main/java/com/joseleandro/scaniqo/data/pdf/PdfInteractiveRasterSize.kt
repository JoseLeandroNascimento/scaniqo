package com.joseleandro.scaniqo.data.pdf

import kotlin.math.max
import kotlin.math.roundToInt

/** Limita o trabalho por gesto antes de alocar a cópia e executar as correções. */
internal data class PdfInteractiveRasterSize(val width: Int, val height: Int) {
    companion object {
        const val MAX_SIDE = 1100

        fun fit(width: Int, height: Int, maxSide: Int): PdfInteractiveRasterSize {
            require(width > 0 && height > 0) { "As dimensões da fonte são inválidas." }
            require(maxSide in 128..MAX_SIDE) { "O tamanho da prévia deve ficar entre 128 e 1100 pixels." }
            val scale = minOf(1f, maxSide.toFloat() / max(width, height))
            return PdfInteractiveRasterSize(
                (width * scale).roundToInt().coerceAtLeast(1),
                (height * scale).roundToInt().coerceAtLeast(1),
            )
        }
    }
}
