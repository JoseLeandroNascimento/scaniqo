package com.joseleandro.scaniqo.ui.signatures

/** Entrega uma assinatura recém-criada somente ao PDF que iniciou a criação. */
data class SignatureDocumentSelection(
    val documentId: Long,
    val signatureId: String,
)
