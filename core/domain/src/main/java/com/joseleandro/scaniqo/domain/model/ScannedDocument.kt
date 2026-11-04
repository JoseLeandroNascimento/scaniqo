package com.joseleandro.scaniqo.domain.model

/** Arquivos preparados para revisão; tornam-se documentos após a confirmação do cadastro. */
data class ScannedDocument(
    val pdfUri: String,
    val pageCount: Int,
    val createdAt: Long,
    val sizeBytes: Long,
    val thumbnailUri: String? = null,
    val protectionKeyId: String? = null
)
