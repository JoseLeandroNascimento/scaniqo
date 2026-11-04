package com.joseleandro.scaniqo.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class PdfUtility { EXTRACT_PAGES, PDF_TO_IMAGES, IMAGES_TO_PDF, ID_CARD, RECEIPT, COMPRESS_PDF }

enum class PdfCompressionLevel { BALANCED, STRONG }

/** Uma cópia só é devolvida quando o tamanho real ficou menor que o original. */
data class PdfCompressionResult(
    val scan: ScannedDocument?,
    val originalBytes: Long,
    val resultBytes: Long,
)

enum class PdfImageFormat(val extension: String) { JPEG("jpg"), PNG("png") }

/** OCR propõe valores; o usuário deve revisá-los antes de exportar. */
data class ReceiptDraft(
    val merchant: String = "",
    val date: String = "",
    val total: String = "",
    val items: String = "",
    val sourceText: String = "",
)
