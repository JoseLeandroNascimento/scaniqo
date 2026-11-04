package com.joseleandro.scaniqo.domain.geometry

/** Metrics for the ASCII page footer rendered in standard Helvetica at 10 PDF points. */
object PdfPageNumberMetrics {
    const val FONT_SIZE = 10f

    fun width(pageNumber: Int, pageCount: Int): Float {
        // Standard Helvetica AFM advances, in thousandths of an em. The exporter uses
        // this font without kerning; its numeric footer needs no Android/PDF runtime.
        val units = "$pageNumber / $pageCount".sumOf { character ->
            when (character) {
                ' ', '/' -> 278
                '-' -> 333
                else -> 556 // Int.toString() only adds decimal digits.
            }
        }
        return units / 1000f * FONT_SIZE
    }
}
