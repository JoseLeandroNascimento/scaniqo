package com.joseleandro.scaniqo.domain.model

/** Amostras da página atual a 100%; servem apenas à escolha visual do efeito. */
data class PdfFilterThumbnails(
    val images: Map<PdfImageFilter, String> = emptyMap(),
    /** A fonte contém texto ou vetores; aplicar um filtro exige convertê-la em imagem. */
    val requiresRasterization: Boolean = false,
)
