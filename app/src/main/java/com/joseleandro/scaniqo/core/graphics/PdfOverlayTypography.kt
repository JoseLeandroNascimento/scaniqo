package com.joseleandro.scaniqo.core.graphics

import android.graphics.Typeface
import com.joseleandro.scaniqo.domain.model.PdfOverlay

/** Mantém o estilo usado para medir, editar e desenhar texto sobre a página. */
internal fun PdfOverlay.androidTypeface(): Typeface = Typeface.create(
    Typeface.SANS_SERIF,
    when {
        bold && italic -> Typeface.BOLD_ITALIC
        bold -> Typeface.BOLD
        italic -> Typeface.ITALIC
        else -> Typeface.NORMAL
    },
)
