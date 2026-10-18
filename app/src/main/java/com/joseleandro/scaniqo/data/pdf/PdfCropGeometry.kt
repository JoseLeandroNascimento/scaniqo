package com.joseleandro.scaniqo.data.pdf

import com.joseleandro.scaniqo.domain.model.PdfCropBounds
import kotlin.math.roundToInt

/** A mesma área rege pixels, composição vetorial e dimensões físicas da captura. */
internal object PdfCropGeometry {
    data class Viewport(
        val left: Float,
        val top: Float,
        val width: Float,
        val height: Float,
        val pixelWidth: Int,
        val pixelHeight: Int,
        val scale: Float,
    )

    data class SourceBox(val x: Float, val y: Float, val width: Float, val height: Float)

    fun viewport(width: Float, height: Float, crop: PdfCropBounds?, maxSide: Int): Viewport {
        require(width.isFinite() && height.isFinite() && width > 0f && height > 0f)
        require(maxSide in 128..4000)
        val bounds = checked(crop)
        val croppedWidth = width * (bounds.right - bounds.left)
        val croppedHeight = height * (bounds.bottom - bounds.top)
        // A resolução é da região final, nunca de uma miniatura da página inteira.
        val scale = maxSide / maxOf(croppedWidth, croppedHeight)
        return Viewport(
            left = width * bounds.left,
            top = height * bounds.top,
            width = croppedWidth,
            height = croppedHeight,
            pixelWidth = (croppedWidth * scale).roundToInt().coerceIn(1, maxSide),
            pixelHeight = (croppedHeight * scale).roundToInt().coerceIn(1, maxSide),
            scale = scale,
        )
    }

    /** Converte o retângulo visual (origem superior) para a CropBox PDF, respeitando /Rotate. */
    fun sourceBox(
        x: Float, y: Float, width: Float, height: Float, rotation: Int, crop: PdfCropBounds?,
    ): SourceBox {
        require(listOf(x, y, width, height).all(Float::isFinite) && width > 0f && height > 0f)
        require(rotation % 90 == 0)
        val bounds = checked(crop)
        val (left, bottom, right, top) = when ((rotation % 360 + 360) % 360) {
            90 -> listOf(bounds.top, bounds.left, bounds.bottom, bounds.right)
            180 -> listOf(1f - bounds.right, bounds.top, 1f - bounds.left, bounds.bottom)
            270 -> listOf(1f - bounds.bottom, 1f - bounds.right, 1f - bounds.top, 1f - bounds.left)
            else -> listOf(bounds.left, 1f - bounds.bottom, bounds.right, 1f - bounds.top)
        }
        return SourceBox(
            x + width * left, y + height * bottom,
            width * (right - left), height * (top - bottom),
        )
    }

    private fun checked(crop: PdfCropBounds?): PdfCropBounds =
        (crop ?: PdfCropBounds()).also { require(it.isValid()) { "O recorte deve ficar dentro da captura." } }
}
