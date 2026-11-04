package com.joseleandro.scaniqo.domain.repository

import com.joseleandro.scaniqo.domain.model.PdfCertificateDocument
import com.joseleandro.scaniqo.domain.model.PdfSignedExport

interface PdfCertificateRepository {
    suspend fun open(uri: String): PdfCertificateDocument
    suspend fun sign(document: PdfCertificateDocument, certificateAlias: String): PdfSignedExport
    suspend fun export(result: PdfSignedExport, destinationUri: String)
    fun discard(document: PdfCertificateDocument)
    fun discard(result: PdfSignedExport)
}
