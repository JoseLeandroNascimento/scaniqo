package com.joseleandro.scaniqo.domain.model

/** Alertas são sugestões de revisão e nunca removem conteúdo automaticamente. */
data class PdfQualityReport(val pages: List<PdfPageQuality>)

data class PdfPageQuality(
    val pageId: String,
    val hasNativeText: Boolean = false,
    val hasNativeVectors: Boolean = false,
    val isBlank: Boolean = false,
    val duplicateOfPageId: String? = null,
    val isBlurry: Boolean = false,
    val isLowResolution: Boolean = false,
    val mayBeClipped: Boolean = false,
    val suggestedStraightenDegrees: Float = 0f
)
