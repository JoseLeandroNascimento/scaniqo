package com.joseleandro.scaniqo.ui.documentEditor.util

import com.joseleandro.scaniqo.domain.model.PdfCropBounds
import com.joseleandro.scaniqo.domain.model.PdfPoint

/** Move a seleção inteira e redimensiona com pinça sem mudar sua proporção. */
internal fun transformManualCrop(
    crop: PdfCropBounds,
    deltaX: Float,
    deltaY: Float,
    zoom: Float,
    minimumWidth: Float = .05f,
    minimumHeight: Float = .05f,
): PdfCropBounds {
    if (!crop.isValid() || !deltaX.isFinite() || !deltaY.isFinite() || !zoom.isFinite() || zoom <= 0f) return crop
    val width = crop.right - crop.left
    val height = crop.bottom - crop.top
    val maximum = minOf(1f / width, 1f / height)
    val minimum = maxOf(
        (minimumWidth.takeIf { it.isFinite() } ?: .05f).coerceIn(.01f, 1f) / width,
        (minimumHeight.takeIf { it.isFinite() } ?: .05f).coerceIn(.01f, 1f) / height,
    ).coerceAtMost(1f).coerceAtMost(maximum)
    val factor = zoom.coerceIn(minimum, maximum)
    val newWidth = (width * factor).coerceAtMost(1f)
    val newHeight = (height * factor).coerceAtMost(1f)
    val left = ((crop.left + crop.right - newWidth) / 2f + deltaX).coerceIn(0f, 1f - newWidth)
    val top = ((crop.top + crop.bottom - newHeight) / 2f + deltaY).coerceIn(0f, 1f - newHeight)
    return PdfCropBounds(left, top, left + newWidth, top + newHeight)
}

/** Uma borda altera somente seu próprio eixo, mantendo a borda oposta fixa. */
internal fun resizeManualCropEdge(
    crop: PdfCropBounds,
    edge: Int,
    deltaX: Float,
    deltaY: Float,
    minimumWidth: Float = .05f,
    minimumHeight: Float = .05f,
): PdfCropBounds {
    val adjusted = when (edge) {
        0 -> resizeManualCrop(crop, 0, 0f, deltaY, minimumWidth, minimumHeight)
        1 -> resizeManualCrop(crop, 1, deltaX, 0f, minimumWidth, minimumHeight)
        2 -> resizeManualCrop(crop, 2, 0f, deltaY, minimumWidth, minimumHeight)
        3 -> resizeManualCrop(crop, 3, deltaX, 0f, minimumWidth, minimumHeight)
        else -> return crop
    }
    return if (edge % 2 == 0) adjusted.copy(left = crop.left, right = crop.right)
        else adjusted.copy(top = crop.top, bottom = crop.bottom)
}

internal fun PdfCropBounds.corners(): List<PdfPoint> = listOf(
    PdfPoint(left, top), PdfPoint(right, top),
    PdfPoint(right, bottom), PdfPoint(left, bottom),
)

/** Cada canto move duas bordas; os outros cantos acompanham os mesmos eixos. */
internal fun resizeManualCrop(
    crop: PdfCropBounds,
    corner: Int,
    deltaX: Float,
    deltaY: Float,
    minimumWidth: Float = .05f,
    minimumHeight: Float = .05f,
): PdfCropBounds {
    if (!crop.isValid() || corner !in 0..3 || !deltaX.isFinite() || !deltaY.isFinite()) return crop
    val minWidth = minimumWidth.takeIf { it.isFinite() }?.coerceIn(.01f, 1f) ?: .05f
    val minHeight = minimumHeight.takeIf { it.isFinite() }?.coerceIn(.01f, 1f) ?: .05f
    val movesLeft = corner == 0 || corner == 3
    val movesTop = corner == 0 || corner == 1
    return crop.copy(
        left = if (movesLeft) (crop.left + deltaX).coerceIn(0f, (crop.right - minWidth).coerceAtLeast(0f)) else crop.left,
        right = if (!movesLeft) (crop.right + deltaX).coerceIn((crop.left + minWidth).coerceAtMost(1f), 1f) else crop.right,
        top = if (movesTop) (crop.top + deltaY).coerceIn(0f, (crop.bottom - minHeight).coerceAtLeast(0f)) else crop.top,
        bottom = if (!movesTop) (crop.bottom + deltaY).coerceIn((crop.top + minHeight).coerceAtMost(1f), 1f) else crop.bottom,
    )
}
