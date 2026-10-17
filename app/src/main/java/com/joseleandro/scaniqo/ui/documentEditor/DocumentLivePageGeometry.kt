package com.joseleandro.scaniqo.ui.documentEditor

import com.joseleandro.scaniqo.data.pdf.PdfPageGeometry
import com.joseleandro.scaniqo.domain.model.PdfPageLayout
import com.joseleandro.scaniqo.domain.model.PdfSignaturePlacement
import kotlin.math.min

/** Coordenadas da folha em pontos, com origem superior esquerda como no Canvas. */
internal data class DocumentLiveRect(val left: Float, val top: Float, val right: Float, val bottom: Float)

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
        return DocumentLiveRect(margin, margin, PdfPageGeometry.WIDTH - margin, PdfPageGeometry.HEIGHT - margin)
    }

    /** A assinatura fica na A4 e preserva a proporção, independentemente dos ajustes da captura. */
    fun signatureRect(signature: PdfSignaturePlacement, imageWidth: Int, imageHeight: Int): DocumentLiveRect {
        require(imageWidth > 0 && imageHeight > 0)
        require(listOf(signature.x, signature.y, signature.width, signature.height).all { it.isFinite() && it in 0f..1f })
        require(signature.width > 0f && signature.height > 0f)
        require(signature.x + signature.width <= 1.001f && signature.y + signature.height <= 1.001f)
        val boxWidth = signature.width * PdfPageGeometry.WIDTH
        val boxHeight = signature.height * PdfPageGeometry.HEIGHT
        val scale = min(boxWidth / imageWidth, boxHeight / imageHeight)
        val width = imageWidth * scale
        val height = imageHeight * scale
        val left = signature.x * PdfPageGeometry.WIDTH + (boxWidth - width) / 2
        val top = signature.y * PdfPageGeometry.HEIGHT + (boxHeight - height) / 2
        return DocumentLiveRect(left, top, left + width, top + height)
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
