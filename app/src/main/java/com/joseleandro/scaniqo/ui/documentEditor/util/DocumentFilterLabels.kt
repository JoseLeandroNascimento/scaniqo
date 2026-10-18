package com.joseleandro.scaniqo.ui.documentEditor.util

import com.joseleandro.scaniqo.R
import com.joseleandro.scaniqo.domain.model.PdfImageFilter

internal fun PdfImageFilter.titleResource(): Int = when (this) {
    PdfImageFilter.ORIGINAL -> R.string.tools_filter_original
    PdfImageFilter.ENHANCE -> R.string.tools_filter_enhance
    PdfImageFilter.AUTO -> R.string.tools_filter_auto
    PdfImageFilter.GRAYSCALE -> R.string.tools_filter_grayscale
    PdfImageFilter.BLACK_WHITE -> R.string.tools_filter_black_white
    PdfImageFilter.SHADOW_REMOVAL -> R.string.quality_filter_shadow
    PdfImageFilter.ILLUMINATION -> R.string.quality_filter_light
    PdfImageFilter.WHITEN -> R.string.quality_filter_white
    PdfImageFilter.ADAPTIVE_BLACK_WHITE -> R.string.quality_filter_adaptive
    PdfImageFilter.DENOISE -> R.string.quality_filter_noise
    PdfImageFilter.SHARPEN -> R.string.quality_filter_sharp
    PdfImageFilter.BLEED_THROUGH -> R.string.quality_filter_bleed
}
