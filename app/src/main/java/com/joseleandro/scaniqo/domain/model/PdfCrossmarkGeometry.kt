package com.joseleandro.scaniqo.domain.model

import kotlin.math.min

/** Mesma forma vetorial na prévia e no PDF exportado. */
internal object PdfCrossmarkGeometry {
    fun strokeWidth(width: Float, height: Float): Float = min(width, height) * .15f

    inline fun trace(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        moveTo: (Float, Float) -> Unit,
        lineTo: (Float, Float) -> Unit,
    ) {
        moveTo(x + width * .18f, y + height * .18f)
        lineTo(x + width * .82f, y + height * .82f)
        moveTo(x + width * .82f, y + height * .18f)
        lineTo(x + width * .18f, y + height * .82f)
    }
}
