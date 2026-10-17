package com.joseleandro.scaniqo.data.pdf

import com.joseleandro.scaniqo.domain.model.PdfPoint
import kotlin.math.hypot

/** Converte a origem superior da imagem para a origem inferior do PDF, preservando texto girado. */
internal object PdfOcrGeometry {
    data class Placement(
        val fontSize: Float,
        val a: Float, val b: Float, val c: Float, val d: Float, val x: Float, val y: Float,
    )

    fun place(
        bottomLeft: PdfPoint,
        bottomRight: PdfPoint,
        topLeft: PdfPoint,
        bitmapWidth: Int,
        bitmapHeight: Int,
        pageWidth: Float,
        pageHeight: Float,
        textWidthAtUnit: Float,
    ): Placement? {
        if (bitmapWidth <= 0 || bitmapHeight <= 0 || textWidthAtUnit <= 0f) return null
        val sx = pageWidth / bitmapWidth
        val sy = pageHeight / bitmapHeight
        val dx = (bottomRight.x - bottomLeft.x) * sx
        val dy = -(bottomRight.y - bottomLeft.y) * sy
        val width = hypot(dx, dy)
        val height = hypot((bottomLeft.x - topLeft.x) * sx, (bottomLeft.y - topLeft.y) * sy)
        if (!width.isFinite() || !height.isFinite() || width < .1f || height < .1f) return null
        val size = height.coerceAtLeast(1f)
        val ux = dx / width
        val uy = dy / width
        val horizontalScale = width / (textWidthAtUnit * size)
        // Reserva inferior para descendentes, sem modificar a imagem visível.
        val x = bottomLeft.x * sx - uy * height * .16f
        val y = pageHeight - bottomLeft.y * sy + ux * height * .16f
        return Placement(size, ux * horizontalScale, uy * horizontalScale, -uy, ux, x, y)
    }
}
