package com.joseleandro.scaniqo.ui.documentEditor.components

import java.text.DecimalFormatSymbols
import java.util.Locale

/** A exibição conserva frações legadas; abrir o campo não altera o PDF. */
internal fun formatFillMarkSize(size: Float, locale: Locale): String =
    size.toString().removeSuffix(".0")
        .replace('.', DecimalFormatSymbols.getInstance(locale).decimalSeparator)

/** Não ajusta valores fora do intervalo em silêncio. */
internal fun parseFillMarkSize(input: String): Float? {
    val normalized = input.trim().replace(',', '.')
    if (normalized.isEmpty() || normalized.count { it == '.' } > 1) return null
    return normalized.toFloatOrNull()?.takeIf { it.isFinite() && it in 6f..72f }
}

internal fun acceptsFillMarkSizeInput(input: String): Boolean =
    input.length <= 8 && input.all { it.isDigit() || it == ',' || it == '.' } &&
        input.count { it == ',' || it == '.' } <= 1
