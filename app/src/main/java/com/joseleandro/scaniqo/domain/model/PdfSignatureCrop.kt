package com.joseleandro.scaniqo.domain.model

import kotlin.math.ceil
import kotlin.math.floor

/** Retângulo normalizado na imagem original, antes da rotação da assinatura. */
data class PdfSignatureCrop(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val isValid: Boolean
        get() = listOf(left, top, right, bottom).all { it.isFinite() && it in 0f..1f } &&
            right > left && bottom > top

    fun pixels(width: Int, height: Int): PdfSignatureCropPixels {
        require(isValid && width > 0 && height > 0) { "Selecione uma área válida da assinatura." }
        val x = floor(left * width).toInt().coerceIn(0, width - 1)
        val y = floor(top * height).toInt().coerceIn(0, height - 1)
        val endX = ceil(right * width).toInt().coerceIn(x + 1, width)
        val endY = ceil(bottom * height).toInt().coerceIn(y + 1, height)
        return PdfSignatureCropPixels(x, y, endX - x, endY - y)
    }
}

data class PdfSignatureCropPixels(val x: Int, val y: Int, val width: Int, val height: Int)

/** Uma imagem nova da sessão; a assinatura salva na biblioteca continua intacta. */
data class PdfSignatureImage(val imageUri: String, val aspectRatio: Float)
