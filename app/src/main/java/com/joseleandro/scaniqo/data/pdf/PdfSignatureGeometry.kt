package com.joseleandro.scaniqo.data.pdf

import com.joseleandro.scaniqo.domain.model.PdfSignaturePlacement
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Mesma posição e rotação nas prévias, miniaturas e no PDF exportado. */
internal object PdfSignatureGeometry {
    data class Bounds(val left: Float, val top: Float, val width: Float, val height: Float) {
        val centerX: Float get() = left + width / 2f
        val centerY: Float get() = top + height / 2f
    }

    fun bounds(signature: PdfSignaturePlacement, imageWidth: Int, imageHeight: Int): Bounds {
        require(imageWidth > 0 && imageHeight > 0)
        require(listOf(signature.x, signature.y, signature.width, signature.height)
            .all { it.isFinite() && it in 0f..1f })
        require(signature.width > 0f && signature.height > 0f && signature.rotationDegrees.isFinite())
        require(signature.x + signature.width <= 1.001f && signature.y + signature.height <= 1.001f)
        val boxWidth = signature.width * PdfPageGeometry.WIDTH
        val boxHeight = signature.height * PdfPageGeometry.HEIGHT
        val scale = min(boxWidth / imageWidth, boxHeight / imageHeight)
        val width = if (signature.stretchToBounds) boxWidth else imageWidth * scale
        val height = if (signature.stretchToBounds) boxHeight else imageHeight * scale
        return Bounds(
            signature.x * PdfPageGeometry.WIDTH + (boxWidth - width) / 2f,
            signature.y * PdfPageGeometry.HEIGHT + (boxHeight - height) / 2f,
            width,
            height,
        )
    }

    /** Mapeia o quadrado da imagem para coordenadas PDF, com origem inferior esquerda. */
    fun pdfTransform(signature: PdfSignaturePlacement, imageWidth: Int, imageHeight: Int): PdfPageGeometry.Transform {
        val bounds = bounds(signature, imageWidth, imageHeight)
        val radians = -Math.toRadians(signature.rotationDegrees.toDouble())
        val cosine = cos(radians).toFloat()
        val sine = sin(radians).toFloat()
        val a = bounds.width * cosine
        val b = bounds.width * sine
        val c = -bounds.height * sine
        val d = bounds.height * cosine
        return PdfPageGeometry.Transform(
            a = a, b = b, c = c, d = d,
            e = bounds.centerX - a / 2f - c / 2f,
            f = PdfPageGeometry.HEIGHT - bounds.centerY - b / 2f - d / 2f,
        )
    }
}
