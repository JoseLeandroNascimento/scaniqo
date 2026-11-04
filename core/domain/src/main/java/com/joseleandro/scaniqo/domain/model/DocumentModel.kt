package com.joseleandro.scaniqo.domain.model

const val DOCUMENT_NAME_MAX_LENGTH = 100

data class DocumentModel(
    val id: Long = 0,
    val name: String,
    val pdfUri: String,
    val pageCount: Int,
    val createdAt: Long,
    val sizeBytes: Long,
    val thumbnailUri: String? = null,
    val tagId: Long? = null,
    val protectionKeyId: String? = null,
    val deletedAt: Long? = null
) {
    val isProtected: Boolean get() = protectionKeyId != null
}
