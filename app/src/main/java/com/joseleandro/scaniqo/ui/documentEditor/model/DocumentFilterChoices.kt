package com.joseleandro.scaniqo.ui.documentEditor.model

import com.joseleandro.scaniqo.domain.model.PdfImageFilter

internal val documentFilterChoices = listOf(
    PdfImageFilter.ORIGINAL, PdfImageFilter.ENHANCE,
    PdfImageFilter.SHADOW_REMOVAL, PdfImageFilter.ILLUMINATION,
    PdfImageFilter.WHITEN, PdfImageFilter.ADAPTIVE_BLACK_WHITE,
    PdfImageFilter.DENOISE, PdfImageFilter.SHARPEN,
    PdfImageFilter.BLEED_THROUGH, PdfImageFilter.AUTO,
    PdfImageFilter.GRAYSCALE, PdfImageFilter.BLACK_WHITE,
)
