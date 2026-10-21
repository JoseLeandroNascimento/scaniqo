package com.joseleandro.scaniqo.domain.model

import kotlin.math.min

/** Mesmo traço na edição e no PDF, com espaço para as pontas arredondadas dentro da seleção. */
internal object PdfCheckmarkGeometry {
    fun strokeWidth(width: Float, height: Float): Float = min(width, height) * .18f

    /** A altura negativa permite desenhar com a origem inferior do PDF. */
    inline fun trace(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        moveTo: (Float, Float) -> Unit,
        lineTo: (Float, Float) -> Unit,
        cubicTo: (Float, Float, Float, Float, Float, Float) -> Unit,
    ) {
        moveTo(x + width * .12f, y + height * .50f)
        lineTo(x + width * .32f, y + height * .72f)
        cubicTo(
            x + width * .36f, y + height * .764f,
            x + width * .39f, y + height * .757f,
            x + width * .43f, y + height * .71f,
        )
        lineTo(x + width * .88f, y + height * .18f)
    }
}
