package com.joseleandro.scaniqo.ui.documentEditor.util

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize

/** A inclinação acompanha o deslocamento horizontal; a curvatura acompanha o vertical. */
internal fun documentCorrectionHandleValue(
    current: Float,
    drag: Offset,
    paperSize: IntSize,
    curvature: Boolean,
): Float {
    val range = if (curvature) -1f..1f else -15f..15f
    val safeCurrent = if (current.isFinite()) current.coerceIn(range) else 0f
    val dimension = if (curvature) paperSize.height else paperSize.width
    // O processador faz amostragem inversa: valor positivo move o conteúdo para cima.
    val delta = if (curvature) -drag.y else drag.x
    if (dimension <= 0 || !delta.isFinite()) return safeCurrent
    return (safeCurrent + delta / dimension * (range.endInclusive - range.start)).coerceIn(range)
}
