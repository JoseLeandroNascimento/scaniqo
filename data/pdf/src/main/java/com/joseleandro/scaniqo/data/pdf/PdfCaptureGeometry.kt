package com.joseleandro.scaniqo.data.pdf

import com.joseleandro.scaniqo.domain.model.PdfCropBounds
import com.joseleandro.scaniqo.domain.model.PdfPerspective
import com.joseleandro.scaniqo.domain.model.PdfPoint
import kotlin.math.ceil

/** Converte a janela visual da captura sem alterar as coordenadas persistidas da página fonte. */
internal object PdfCaptureGeometry {
    fun intersect(first: PdfCropBounds, second: PdfCropBounds): PdfCropBounds? = PdfCropBounds(
        maxOf(first.left, second.left), maxOf(first.top, second.top),
        minOf(first.right, second.right), minOf(first.bottom, second.bottom),
    ).takeIf { it.isValid() }

    fun relativeTo(bounds: PdfCropBounds, parent: PdfCropBounds): PdfCropBounds {
        val width = parent.right - parent.left
        val height = parent.bottom - parent.top
        return PdfCropBounds(
            ((bounds.left - parent.left) / width).coerceIn(0f, 1f),
            ((bounds.top - parent.top) / height).coerceIn(0f, 1f),
            ((bounds.right - parent.left) / width).coerceIn(0f, 1f),
            ((bounds.bottom - parent.top) / height).coerceIn(0f, 1f),
        )
    }

    fun toSource(points: PdfPerspective, bounds: PdfCropBounds): PdfPerspective {
        fun point(value: PdfPoint) = PdfPoint(
            bounds.left + value.x * (bounds.right - bounds.left),
            bounds.top + value.y * (bounds.bottom - bounds.top),
        )
        return PdfPerspective(point(points.topLeft), point(points.topRight),
            point(points.bottomRight), point(points.bottomLeft))
    }

    /** Compensa margens no preview sem reduzir a resolução da captura ou ultrapassar o limite global. */
    fun resultMaxSide(
        sourceWidth: Float, sourceHeight: Float,
        renderArea: PdfCropBounds, visibleArea: PdfCropBounds, maxSide: Int,
    ): Int {
        val renderSide = maxOf(
            sourceWidth * (renderArea.right - renderArea.left),
            sourceHeight * (renderArea.bottom - renderArea.top),
        )
        val visibleSide = maxOf(
            sourceWidth * (visibleArea.right - visibleArea.left),
            sourceHeight * (visibleArea.bottom - visibleArea.top),
        )
        require(renderSide.isFinite() && visibleSide.isFinite() && visibleSide > 0f && renderSide > 0f)
        return ceil(maxSide * renderSide / visibleSide).toInt().coerceIn(128, 4000)
    }
}
