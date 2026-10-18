package com.joseleandro.scaniqo.ui.documentSignature

import com.joseleandro.scaniqo.domain.model.PdfSignaturePlacement
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

internal data class SignatureMagnifierViewport(
    val zoom: Float,
    val translateX: Float,
    val translateY: Float,
    val centerX: Float,
    val centerY: Float,
)

internal data class SignatureMagnifierImageBounds(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
)

/** Prefere assinatura inteira com contexto; seleções grandes continuam ampliadas ao redor do centro. */
internal fun signatureMagnifierViewport(
    signature: PdfSignaturePlacement,
    pageWidth: Float,
    pageHeight: Float,
    lensWidth: Float,
    lensHeight: Float,
    maximumZoom: Float = 3f,
    contextFactor: Float = 1.45f,
    minimumZoom: Float = 1.25f,
): SignatureMagnifierViewport? {
    if (listOf(signature.x, signature.y, signature.width, signature.height, signature.rotationDegrees,
            pageWidth, pageHeight, lensWidth, lensHeight, maximumZoom, contextFactor, minimumZoom).any { !it.isFinite() } ||
        signature.width <= 0f || signature.height <= 0f || pageWidth <= 0f || pageHeight <= 0f ||
        lensWidth <= 0f || lensHeight <= 0f || minimumZoom <= 1f || maximumZoom < minimumZoom || contextFactor <= 1f
    ) return null
    val width = signature.width * pageWidth
    val height = signature.height * pageHeight
    val radians = Math.toRadians((signature.rotationDegrees % 360f).toDouble())
    val rotatedWidth = abs(width * cos(radians)) + abs(height * sin(radians))
    val rotatedHeight = abs(width * sin(radians)) + abs(height * cos(radians))
    val zoom = min(maximumZoom.toDouble(),
        min(lensWidth / (rotatedWidth * contextFactor), lensHeight / (rotatedHeight * contextFactor)))
        .toFloat().coerceAtLeast(minimumZoom)
    val centerX = (signature.x + signature.width / 2f) * pageWidth
    val centerY = (signature.y + signature.height / 2f) * pageHeight
    return SignatureMagnifierViewport(
        zoom = zoom,
        translateX = lensWidth / 2f - centerX * zoom,
        translateY = lensHeight / 2f - centerY * zoom,
        centerX = centerX,
        centerY = centerY,
    ).takeIf { it.zoom.isFinite() && it.zoom > 0f &&
        it.translateX.isFinite() && it.translateY.isFinite() }
}

/** Mesma política da imagem na folha: Fit legado, FillBounds quando largura/altura são independentes. */
internal fun signatureMagnifierImageBounds(
    signature: PdfSignaturePlacement,
    pageWidth: Float,
    pageHeight: Float,
    imageWidth: Int,
    imageHeight: Int,
): SignatureMagnifierImageBounds? {
    if (imageWidth <= 0 || imageHeight <= 0 || pageWidth <= 0f || pageHeight <= 0f ||
        listOf(pageWidth, pageHeight, signature.x, signature.y, signature.width, signature.height)
            .any { !it.isFinite() } || signature.width <= 0f || signature.height <= 0f
    ) return null
    val boxWidth = signature.width * pageWidth
    val boxHeight = signature.height * pageHeight
    val scale = min(boxWidth / imageWidth, boxHeight / imageHeight)
    val width = if (signature.stretchToBounds) boxWidth else imageWidth * scale
    val height = if (signature.stretchToBounds) boxHeight else imageHeight * scale
    return SignatureMagnifierImageBounds(
        left = signature.x * pageWidth + (boxWidth - width) / 2f,
        top = signature.y * pageHeight + (boxHeight - height) / 2f,
        width = width,
        height = height,
    )
}
