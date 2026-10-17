package com.joseleandro.scaniqo.data.pdf

import com.joseleandro.scaniqo.domain.model.PdfPageLayout

/** Converte a geometria do PDF (origem inferior) para os pixels do Canvas (origem superior). */
internal object PdfFilterThumbnailPlacement {
    fun matrix(
        sourceWidth: Float,
        sourceHeight: Float,
        bitmapWidth: Int,
        bitmapHeight: Int,
        layout: PdfPageLayout,
    ): FloatArray {
        require(bitmapWidth > 0 && bitmapHeight > 0)
        val transform = PdfPageGeometry.placement(
            0f, 0f, sourceWidth, sourceHeight, 0, layout,
        )
        val horizontal = sourceWidth / bitmapWidth
        val vertical = sourceHeight / bitmapHeight
        return floatArrayOf(
            transform.a * horizontal, -transform.c * vertical, transform.c * sourceHeight + transform.e,
            -transform.b * horizontal, transform.d * vertical,
            PdfPageGeometry.HEIGHT - transform.d * sourceHeight - transform.f,
            0f, 0f, 1f,
        )
    }
}
