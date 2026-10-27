package com.joseleandro.scaniqo.ui.documentEditor.components

import com.joseleandro.scaniqo.domain.model.PdfOverlay
import com.joseleandro.scaniqo.domain.model.PdfPoint

internal data class FillMarkTapTarget(
    val overlay: PdfOverlay? = null,
    val blocksInsertion: Boolean = false,
)

internal fun findFillMarkTapTarget(
    selected: PdfOverlay?,
    overlays: List<PdfOverlay>,
    point: PdfPoint,
    pageWidth: Float,
    pageHeight: Float,
    minimumTargetSize: Float,
): FillMarkTapTarget {
    if (!listOf(point.x, point.y, pageWidth, pageHeight, minimumTargetSize).all(Float::isFinite) ||
        pageWidth <= 0f || pageHeight <= 0f || minimumTargetSize <= 0f
    ) return FillMarkTapTarget()

    val selectableKinds = textKinds + binaryFillMarkKinds
    selected?.takeIf { it.kind in selectableKinds && it.containsFillMarkPoint(point) }?.let {
        return FillMarkTapTarget(overlay = it)
    }
    val existing = overlays.asReversed().filterNot { it.id == selected?.id }
    existing.firstOrNull { it.containsFillMarkPoint(point) }?.let {
        return if (it.kind in selectableKinds) FillMarkTapTarget(overlay = it)
        else FillMarkTapTarget(blocksInsertion = true)
    }

    val candidates = listOfNotNull(selected) + existing
    val closest = candidates
        .filter { it.kind in repeatableFillMarkKinds }
        .mapNotNull { overlay ->
            overlay.distanceFromExpandedFillMarkTarget(
                point, pageWidth, pageHeight, minimumTargetSize,
            )?.let { distance -> overlay to distance }
        }
        .minByOrNull { it.second }
        ?.first
    return FillMarkTapTarget(overlay = closest)
}

private fun PdfOverlay.distanceFromExpandedFillMarkTarget(
    point: PdfPoint,
    pageWidth: Float,
    pageHeight: Float,
    minimumTargetSize: Float,
): Float? {
    val left = x * pageWidth
    val top = y * pageHeight
    val right = (x + width) * pageWidth
    val bottom = (y + height) * pageHeight
    val targetWidth = maxOf(right - left, minOf(minimumTargetSize, pageWidth))
    val targetHeight = maxOf(bottom - top, minOf(minimumTargetSize, pageHeight))
    val targetLeft = ((left + right - targetWidth) / 2f).coerceIn(0f, pageWidth - targetWidth)
    val targetTop = ((top + bottom - targetHeight) / 2f).coerceIn(0f, pageHeight - targetHeight)
    val tapX = point.x * pageWidth
    val tapY = point.y * pageHeight
    if (tapX !in targetLeft..(targetLeft + targetWidth) ||
        tapY !in targetTop..(targetTop + targetHeight)
    ) return null
    val dx = maxOf(left - tapX, 0f, tapX - right)
    val dy = maxOf(top - tapY, 0f, tapY - bottom)
    return dx * dx + dy * dy
}
