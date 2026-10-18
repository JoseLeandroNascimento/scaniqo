package com.joseleandro.scaniqo.ui.documentEditor.preview

import com.joseleandro.scaniqo.data.pdf.PdfPageGeometry
import com.joseleandro.scaniqo.data.pdf.PdfSignatureGeometry
import com.joseleandro.scaniqo.domain.model.PdfPageLayout
import com.joseleandro.scaniqo.domain.model.PdfSignaturePlacement
import com.joseleandro.scaniqo.ui.documentEditor.model.DocumentLivePageNumber
import com.joseleandro.scaniqo.ui.documentEditor.model.DocumentLiveRect
import com.joseleandro.scaniqo.ui.documentEditor.model.DocumentLiveTransform


/** Usa a mesma geometria da exportação, convertendo apenas a origem inferior esquerda do PDF. */
internal object DocumentLivePageGeometry {
    fun bitmapTransform(
        bitmapWidth: Int,
        bitmapHeight: Int,
        sourceWidth: Float,
        sourceHeight: Float,
        layout: PdfPageLayout,
    ): DocumentLiveTransform {
        require(bitmapWidth > 0 && bitmapHeight > 0)
        val placement = PdfPageGeometry.placement(
            0f, 0f, sourceWidth, sourceHeight, 0, layout,
        )
        val sourceX = sourceWidth / bitmapWidth
        val sourceY = sourceHeight / bitmapHeight
        return DocumentLiveTransform(
            scaleX = placement.a * sourceX,
            skewX = -placement.c * sourceY,
            translateX = placement.c * sourceHeight + placement.e,
            skewY = -placement.b * sourceX,
            scaleY = placement.d * sourceY,
            translateY = PdfPageGeometry.HEIGHT - placement.d * sourceHeight - placement.f,
        )
    }

    fun contentClip(layout: PdfPageLayout): DocumentLiveRect {
        val margin = PdfPageGeometry.marginFor(layout)
        return DocumentLiveRect(
            margin,
            margin,
            PdfPageGeometry.WIDTH - margin,
            PdfPageGeometry.HEIGHT - margin
        )
    }

    /** A assinatura fica na A4 e preserva a proporção, independentemente dos ajustes da captura. */
    fun signatureRect(
        signature: PdfSignaturePlacement,
        imageWidth: Int,
        imageHeight: Int
    ): DocumentLiveRect {
        val bounds = PdfSignatureGeometry.bounds(signature, imageWidth, imageHeight)
        return DocumentLiveRect(
            bounds.left, bounds.top, bounds.left + bounds.width, bounds.top + bounds.height,
        )
    }

    /** Mesmos 10 pontos, linha de base a 15 pontos e fundo a 12 pontos usados no PDF. */
    fun pageNumber(textWidth: Float): DocumentLivePageNumber {
        require(textWidth.isFinite() && textWidth >= 0f)
        val left = (PdfPageGeometry.WIDTH - textWidth) / 2
        return DocumentLivePageNumber(
            left = left,
            baseline = PdfPageGeometry.HEIGHT - 15f,
            background = DocumentLiveRect(
                left - 4f, PdfPageGeometry.HEIGHT - 28f,
                left + textWidth + 4f, PdfPageGeometry.HEIGHT - 12f,
            ),
        )
    }
}
