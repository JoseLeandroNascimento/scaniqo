package com.joseleandro.scaniqo.ui.signatures.crop

import com.joseleandro.scaniqo.domain.model.PdfSignatureCrop

internal data class SignatureCropViewport(
    val scale: Float = 1f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
) {
    fun transform(
        crop: PdfSignatureCrop,
        zoom: Float,
        panX: Float,
        panY: Float,
        focusX: Float,
        focusY: Float,
    ): SignatureCropImageTransform {
        if (!crop.isValid || zoom <= 0f ||
            !listOf(zoom, panX, panY, focusX, focusY).all(Float::isFinite)
        ) return SignatureCropImageTransform(this, crop)
        val left = offsetX + crop.left * scale
        val top = offsetY + crop.top * scale
        val right = offsetX + crop.right * scale
        val bottom = offsetY + crop.bottom * scale
        val nextScale = (scale * zoom).coerceIn(maxOf(1f, right - left, bottom - top), 8f)
        val ratio = nextScale / scale
        val nextX = ((offsetX - focusX) * ratio + focusX + panX)
            .coerceIn(right - nextScale, left)
        val nextY = ((offsetY - focusY) * ratio + focusY + panY)
            .coerceIn(bottom - nextScale, top)
        return SignatureCropImageTransform(
            SignatureCropViewport(nextScale, nextX, nextY),
            PdfSignatureCrop(
                ((left - nextX) / nextScale).coerceIn(0f, 1f),
                ((top - nextY) / nextScale).coerceIn(0f, 1f),
                ((right - nextX) / nextScale).coerceIn(0f, 1f),
                ((bottom - nextY) / nextScale).coerceIn(0f, 1f),
            ),
        )
    }
}

internal data class SignatureCropImageTransform(
    val viewport: SignatureCropViewport,
    val crop: PdfSignatureCrop,
)
