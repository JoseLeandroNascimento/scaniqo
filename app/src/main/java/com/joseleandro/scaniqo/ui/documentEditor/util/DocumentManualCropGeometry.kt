package com.joseleandro.scaniqo.ui.documentEditor.util

import com.joseleandro.scaniqo.domain.model.PdfCropBounds
import com.joseleandro.scaniqo.domain.model.PdfPoint

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
