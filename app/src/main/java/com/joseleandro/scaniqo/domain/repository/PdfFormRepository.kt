package com.joseleandro.scaniqo.domain.repository

import com.joseleandro.scaniqo.domain.model.PdfFormDocument
import com.joseleandro.scaniqo.domain.model.PdfFormExport

interface PdfFormRepository {
    suspend fun open(uri: String): PdfFormDocument
    suspend fun fill(document: PdfFormDocument, values: Map<String, List<String>>): PdfFormExport
    suspend fun export(result: PdfFormExport, destinationUri: String)
    fun discard(document: PdfFormDocument)
    fun discard(result: PdfFormExport)
}
