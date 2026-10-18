package com.joseleandro.scaniqo.ui.documentCommon.state

/** Resultado persistido até a lista confirmar que tornou o novo documento visível. */
data class DocumentCreationResult(
    val documentId: Long,
    val tagId: Long?,
)
