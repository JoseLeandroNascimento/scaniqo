package com.joseleandro.scaniqo.domain.model

/** O PDF salvo, ou uma cópia privada descriptografada que deve ser fechada ao terminar a leitura. */
data class ReadableDocument(
    val pdfUri: String,
    val pageCount: Int,
    val sessionId: String? = null,
)
