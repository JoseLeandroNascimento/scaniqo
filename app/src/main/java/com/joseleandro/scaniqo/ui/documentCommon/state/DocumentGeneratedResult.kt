package com.joseleandro.scaniqo.ui.documentCommon.state

/** Resultado de uma saída preparada por uma ferramenta. ID nulo indica descarte confirmado. */
data class DocumentGeneratedResult(
    val operationId: String,
    val documentId: Long?,
)
