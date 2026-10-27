package com.joseleandro.scaniqo.ui.documentEditor.components

import com.joseleandro.scaniqo.domain.model.PdfOverlay
import com.joseleandro.scaniqo.domain.model.PdfPoint
import kotlin.math.abs

/** O corpo move; a única alça visível ajusta a caixa pelo canto inferior direito. */
internal data class FillMarkSelectionGeometry(
    val corners: List<PdfPoint>,
    val handles: List<PdfPoint>,
    val topMiddle: PdfPoint,
    val visibleHandles: List<Int>,
    val targetSize: Float,
) {
    fun handleAt(point: PdfPoint): Int? = visibleHandles.firstOrNull { index ->
        abs(point.x - handles[index].x) <= targetSize / 2f &&
            abs(point.y - handles[index].y) <= targetSize / 2f
    }
}

@Suppress("UNUSED_PARAMETER")
internal fun fillMarkSelectionGeometry(
    overlay: PdfOverlay,
    pageLeft: Float, pageTop: Float, pageWidth: Float, pageHeight: Float,
    viewportWidth: Float, viewportHeight: Float,
    targetSize: Float, targetGap: Float, stemLength: Float,
): FillMarkSelectionGeometry {
    val left = pageLeft + overlay.x * pageWidth
    val top = pageTop + overlay.y * pageHeight
    val right = left + overlay.width * pageWidth
    val bottom = top + overlay.height * pageHeight
    val corners = listOf(
        PdfPoint(left, top), PdfPoint(right, top),
        PdfPoint(right, bottom), PdfPoint(left, bottom),
    )
    val topMiddle = PdfPoint((left + right) / 2f, top)
    val intersectsViewport = right >= 0f && left <= viewportWidth &&
        bottom >= 0f && top <= viewportHeight
    val halfTarget = targetSize / 2f
    fun reachable(value: Float, extent: Float): Float {
        val inset = minOf(halfTarget, extent / 2f)
        return value.coerceIn(inset, extent - inset)
    }
    val handleOffset = halfTarget + targetGap
    val handle = when {
        right + handleOffset <= viewportWidth - halfTarget ->
            PdfPoint(right + handleOffset, reachable(bottom, viewportHeight))
        bottom + handleOffset <= viewportHeight - halfTarget ->
            PdfPoint(reachable(right, viewportWidth), bottom + handleOffset)
        else -> PdfPoint(reachable(right, viewportWidth), reachable(bottom, viewportHeight))
    }
    val handles = corners.toMutableList()
    handles[2] = handle
    return FillMarkSelectionGeometry(
        corners, handles + topMiddle,
        topMiddle, if (intersectsViewport) listOf(2) else emptyList(), targetSize,
    )
}
