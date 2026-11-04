package com.joseleandro.scaniqo.ui.documentEditor.components

import com.joseleandro.scaniqo.domain.model.PdfOverlay
import kotlin.math.floor
import kotlin.math.roundToInt

internal class FillMarkSizeMapping(
    private val baseline: PdfOverlay,
    private val proportionalText: Boolean = false,
) {
    val isText = baseline.kind in textKinds
    val valueRange: ClosedFloatingPointRange<Float> = if (isText) {
        val maximum = when {
            proportionalText -> baseline.fontSize * baseline.fillMarkScaleRange().endInclusive
            baseline.autoSizeText -> 72f
            else -> baseline.scaledFillMarkAtCorner(2, 72f / baseline.fontSize).fontSize
        }
        6f..floor(maximum + .0001f).coerceIn(6f, 72f)
    } else {
        baseline.fillMarkScaleRange().let { (it.start * baseline.width)..(it.endInclusive * baseline.width) }
    }
    val steps = if (isText) ((valueRange.endInclusive - valueRange.start).toInt() - 1).coerceAtLeast(0) else 0

    fun value(overlay: PdfOverlay): Float = if (isText) overlay.fontSize else overlay.width

    fun resize(current: PdfOverlay, requested: Float): PdfOverlay {
        if (!requested.isFinite()) return current
        val size = (if (isText) requested.roundToInt().toFloat() else requested).coerceIn(valueRange)
        if (size == value(current)) return current
        val resized = when {
            !isText -> baseline.scaledFillMarkAroundCenter(size / baseline.width)
            proportionalText -> baseline.scaledFillMarkAroundCenter(size / baseline.fontSize).copy(fontSize = size)
            else -> baseline.withFillMarkFontSize(size).copy(fontSize = size)
        }
        return current.copy(
            x = resized.x, y = resized.y, width = resized.width, height = resized.height,
            fontSize = resized.fontSize, points = resized.points,
        )
    }
}
