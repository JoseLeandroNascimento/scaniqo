package com.joseleandro.scaniqo.domain.model

/** Imagem bruta da captura; bounds posiciona seus pixels na página fonte original. */
data class PdfCaptureSource(
    val pixels: PdfInteractivePixels,
    val bounds: PdfCropBounds,
    /** A página também contém texto ou vetores que exigem conversão explícita para remover o fundo. */
    val preservesNativeContent: Boolean = false,
)
