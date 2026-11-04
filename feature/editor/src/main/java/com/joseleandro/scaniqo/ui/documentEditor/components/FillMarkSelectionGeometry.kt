package com.joseleandro.scaniqo.ui.documentEditor.components

import com.joseleandro.scaniqo.domain.model.PdfOverlay
import com.joseleandro.scaniqo.domain.model.PdfPoint
import com.joseleandro.scaniqo.ui.documentEditor.util.DocumentSelectionGeometry
import com.joseleandro.scaniqo.ui.documentEditor.util.documentSelectionGeometry
import kotlin.math.abs

internal data class FillMarkSelectionGeometry(
    val geometry: DocumentSelectionGeometry,
    val visibleHandles: List<Int>,
    val targetSize: Float,
) {
    val corners get() = geometry.corners
    val handles get() = geometry.cornerHandles + geometry.rotationHandle
    val topMiddle get() = geometry.topMiddle

    fun handleAt(point: PdfPoint): Int? = visibleHandles.firstOrNull { index ->
        abs(point.x - handles[index].x) <= targetSize / 2f &&
            abs(point.y - handles[index].y) <= targetSize / 2f
    }
}

internal fun fillMarkSelectionGeometry(
    overlay: PdfOverlay,
    pageLeft: Float, pageTop: Float, pageWidth: Float, pageHeight: Float,
    viewportWidth: Float, viewportHeight: Float,
    targetSize: Float, targetGap: Float, stemLength: Float,
): FillMarkSelectionGeometry {
    val geometry = documentSelectionGeometry(
        overlay.x, overlay.y, overlay.width, overlay.height, overlay.rotationDegrees,
        pageLeft, pageTop, pageWidth, pageHeight, viewportWidth, viewportHeight,
        targetSize, targetGap, stemLength,
    )
    val visible = (geometry.corners + geometry.topMiddle).mapIndexedNotNull { index, point ->
        index.takeIf { point.x in 0f..viewportWidth && point.y in 0f..viewportHeight }
    }
    return FillMarkSelectionGeometry(geometry, visible, targetSize)
}
