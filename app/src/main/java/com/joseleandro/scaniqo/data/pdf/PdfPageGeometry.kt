package com.joseleandro.scaniqo.data.pdf

import com.joseleandro.scaniqo.domain.model.PageAlignment
import com.joseleandro.scaniqo.domain.model.PageScaleMode
import com.joseleandro.scaniqo.domain.model.PdfPageLayout
import kotlin.math.max
import kotlin.math.min

/** Cálculos sem dependência Android, compartilhados pelo PDF de prévia e pelo definitivo. */
internal object PdfPageGeometry {
    const val WIDTH = 595f
    const val HEIGHT = 842f
    const val MARGIN = 32f

    data class Transform(
        val a: Float, val b: Float, val c: Float,
        val d: Float, val e: Float, val f: Float
    ) {
        fun x(x: Float, y: Float): Float = a * x + c * y + e
        fun y(x: Float, y: Float): Float = b * x + d * y + f
    }

    fun marginFor(layout: PdfPageLayout): Float =
        if (layout.scaleMode == PageScaleMode.FILL) 0f else MARGIN

    fun placement(
        sourceX: Float,
        sourceY: Float,
        sourceWidth: Float,
        sourceHeight: Float,
        rotation: Int,
        layout: PdfPageLayout,
        targetWidth: Float = WIDTH,
        targetHeight: Float = HEIGHT,
        margin: Float = marginFor(layout)
    ): Transform {
        require(listOf(sourceX, sourceY, sourceWidth, sourceHeight).all { it.isFinite() })
        require(sourceWidth > 0 && sourceHeight > 0)
        require(rotation % 90 == 0)
        require(layout.contentScale.isFinite() && layout.contentScale in .25f..2f) {
            "O tamanho do conteúdo deve ficar entre 25% e 200%."
        }
        // A rotação da receita é adicional à orientação original do PDF, sempre no sentido horário.
        val turn = ((rotation % 360) + (layout.rotationQuarterTurns % 4) * 90 + 720) % 360
        val rotated = turn == 90 || turn == 270
        val width = if (rotated) sourceHeight else sourceWidth
        val height = if (rotated) sourceWidth else sourceHeight
        val availableWidth = targetWidth - margin * 2
        val availableHeight = targetHeight - margin * 2
        require(listOf(targetWidth, targetHeight, margin).all { it.isFinite() })
        require(margin >= 0 && availableWidth > 0 && availableHeight > 0)
        val baseScale = when (layout.scaleMode) {
            PageScaleMode.FIT -> min(availableWidth / width, availableHeight / height)
            PageScaleMode.FILL -> max(availableWidth / width, availableHeight / height)
        }
        val scale = baseScale * layout.contentScale
        val left = when (layout.alignment) {
            PageAlignment.LEFT -> margin
            PageAlignment.RIGHT -> targetWidth - margin - width * scale
            else -> (targetWidth - width * scale) / 2
        }
        val bottom = when (layout.alignment) {
            PageAlignment.BOTTOM -> margin
            PageAlignment.TOP -> targetHeight - margin - height * scale
            else -> (targetHeight - height * scale) / 2
        }
        return when (turn) {
            0 -> Transform(scale, 0f, 0f, scale, left - scale * sourceX, bottom - scale * sourceY)
            90 -> Transform(0f, -scale, scale, 0f, left - scale * sourceY,
                bottom + scale * (sourceWidth + sourceX))
            180 -> Transform(-scale, 0f, 0f, -scale, left + scale * (sourceWidth + sourceX),
                bottom + scale * (sourceHeight + sourceY))
            else -> Transform(0f, scale, -scale, 0f, left + scale * (sourceHeight + sourceY),
                bottom - scale * sourceX)
        }
    }
}
