package com.joseleandro.scaniqo.data.pdf

import com.joseleandro.scaniqo.domain.model.PdfExportQuality
import kotlin.math.max
import kotlin.math.roundToInt

/** Perfis alteram somente imagens; texto, vetores e fontes originais são preservados. */
internal data class PdfExportSettings(val maxImageSide: Int, val jpegQuality: Float) {
    fun dimensions(width: Int, height: Int): Pair<Int, Int> {
        require(width > 0 && height > 0)
        val scale = (maxImageSide.toDouble() / max(width, height)).coerceAtMost(1.0)
        return (width * scale).roundToInt().coerceAtLeast(1) to
            (height * scale).roundToInt().coerceAtLeast(1)
    }

    companion object {
        fun forQuality(quality: PdfExportQuality): PdfExportSettings = when (quality) {
            PdfExportQuality.HIGH_QUALITY -> PdfExportSettings(3200, .96f)
            PdfExportQuality.BALANCED -> PdfExportSettings(2200, .85f)
            PdfExportQuality.SMALL -> PdfExportSettings(1500, .72f)
        }
    }
}
