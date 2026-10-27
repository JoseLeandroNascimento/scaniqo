package com.joseleandro.scaniqo.data.pdf

import com.joseleandro.scaniqo.domain.model.PageAlignment
import com.joseleandro.scaniqo.domain.model.PageScaleMode
import com.joseleandro.scaniqo.domain.model.PdfPageLayout
import kotlin.math.abs
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
        if (layout.scaleMode == PageScaleMode.FILL || layout.edgeToEdge) 0f else MARGIN

    /** Distância disponível em pontos; valor negativo representa conteúdo recortado. */
    data class ContentTravel(val horizontal: Float, val vertical: Float)

    fun contentTravel(
        sourceWidth: Float,
        sourceHeight: Float,
        layout: PdfPageLayout,
        targetWidth: Float = WIDTH,
        targetHeight: Float = HEIGHT,
    ): ContentTravel {
        val transform = placement(0f, 0f, sourceWidth, sourceHeight, 0, layout, targetWidth, targetHeight)
        val placedWidth = abs(transform.a) * sourceWidth + abs(transform.c) * sourceHeight
        val placedHeight = abs(transform.b) * sourceWidth + abs(transform.d) * sourceHeight
        val margin = marginFor(layout)
        return ContentTravel(
            targetWidth - margin * 2 - placedWidth,
            targetHeight - margin * 2 - placedHeight,
        )
    }

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
        val position = layout.contentPosition
        require(position?.isValid() != false) { "A posição do conteúdo é inválida." }
        val left = if (position != null) {
            margin + (availableWidth - width * scale) * position.horizontal
        } else when (layout.alignment) {
            PageAlignment.LEFT -> margin
            PageAlignment.RIGHT -> targetWidth - margin - width * scale
            else -> (targetWidth - width * scale) / 2
        }
        val bottom = if (position != null) {
            margin + (availableHeight - height * scale) * (1f - position.vertical)
        } else when (layout.alignment) {
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
