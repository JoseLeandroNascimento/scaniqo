package com.joseleandro.scaniqo.domain.model

/** Localiza uma página pronta; a prévia pode reutilizar o PDF salvo ou conter só uma folha. */
data class PdfPagePreview(
    val uri: String,
    val pageIndex: Int = 0,
)
