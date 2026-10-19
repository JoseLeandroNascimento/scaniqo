package com.joseleandro.scaniqo.domain.repository

import com.joseleandro.scaniqo.domain.model.DocumentModel
import com.joseleandro.scaniqo.domain.model.PdfCompressionLevel
import com.joseleandro.scaniqo.domain.model.PdfCompressionResult
import com.joseleandro.scaniqo.domain.model.PdfImageFormat
import com.joseleandro.scaniqo.domain.model.ReceiptDraft
import com.joseleandro.scaniqo.domain.model.ScannedDocument

interface PdfUtilityRepository {
    suspend fun compress(document: DocumentModel, level: PdfCompressionLevel, password: String?): PdfCompressionResult
    suspend fun extractPages(document: DocumentModel, pages: List<Int>, password: String?): ScannedDocument
    suspend fun exportImages(document: DocumentModel, pages: List<Int>, format: PdfImageFormat, targetUri: String, password: String?)
    suspend fun imagesToPdf(imageUris: List<String>, idCard: Boolean): ScannedDocument
    suspend fun readReceipt(document: DocumentModel, page: Int, password: String?): ReceiptDraft
    suspend fun exportReceipt(receipt: ReceiptDraft, targetUri: String)
}
