package com.joseleandro.scaniqo.ui.documentEditor.util

import com.joseleandro.scaniqo.ui.documentCommon.util.documentDragScrollSpeed
import com.joseleandro.scaniqo.ui.documentCommon.util.moveDocumentOrder

internal fun movePageOrder(order: List<String>, pageId: String, targetIndex: Int): List<String> =
    moveDocumentOrder(order, pageId, targetIndex)

internal fun pageDragScrollSpeed(
    centerY: Float,
    viewportStart: Float,
    viewportEnd: Float,
    edgeSize: Float,
    maxSpeed: Float,
): Float = documentDragScrollSpeed(centerY, viewportStart, viewportEnd, edgeSize, maxSpeed)
