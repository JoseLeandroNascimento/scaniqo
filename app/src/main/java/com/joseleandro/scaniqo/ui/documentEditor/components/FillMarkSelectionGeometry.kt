package com.joseleandro.scaniqo.ui.documentEditor.components

import com.joseleandro.scaniqo.domain.model.PdfOverlay
import com.joseleandro.scaniqo.domain.model.PdfPoint
import com.joseleandro.scaniqo.domain.model.PdfSignaturePlacement
import com.joseleandro.scaniqo.ui.documentEditor.util.signatureSelectionGeometry
import kotlin.math.abs

/** A quinta alça move o elemento; textos não possuem rotação no formato do PDF. */
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

/** Compartilha separação, margens e alvos de toque com a seleção da assinatura. */
internal fun fillMarkSelectionGeometry(
    overlay: PdfOverlay,
    pageLeft: Float, pageTop: Float, pageWidth: Float, pageHeight: Float,
    viewportWidth: Float, viewportHeight: Float,
    targetSize: Float, targetGap: Float, stemLength: Float,
): FillMarkSelectionGeometry {
    val selection = signatureSelectionGeometry(
        PdfSignaturePlacement(
            id = overlay.id, imageUri = "", x = overlay.x, y = overlay.y,
            width = overlay.width, height = overlay.height,
        ),
        pageLeft, pageTop, pageWidth, pageHeight, viewportWidth, viewportHeight,
        targetSize, targetGap, stemLength,
    )
    val intersectsViewport = selection.corners[2].x >= 0f && selection.corners[0].x <= viewportWidth &&
        selection.corners[2].y >= 0f && selection.corners[0].y <= viewportHeight
    val visible = selection.corners.indices.filter { index ->
        val point = selection.corners[index]
        point.x in 0f..viewportWidth && point.y in 0f..viewportHeight
    } + if (intersectsViewport) listOf(4) else emptyList()
    return FillMarkSelectionGeometry(
        selection.corners, selection.cornerHandles + selection.rotationHandle,
        selection.topMiddle, visible, targetSize,
    )
}
