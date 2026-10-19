package com.joseleandro.scaniqo.domain.model

enum class PdfSignatureIntegrity { VERIFIED, INVALID, UNSUPPORTED }

data class PdfCertificateSignature(
    val signer: String,
    val integrity: PdfSignatureIntegrity,
    val coversWholeDocument: Boolean,
    val certificateCurrent: Boolean,
    val detail: String? = null,
)

data class PdfCertificateDocument(
    val sessionId: String,
    val name: String,
    val signatures: List<PdfCertificateSignature>,
    val canSign: Boolean,
)

data class PdfSignedExport(val id: String, val suggestedName: String)
