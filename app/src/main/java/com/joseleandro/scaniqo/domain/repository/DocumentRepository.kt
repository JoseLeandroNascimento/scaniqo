package com.joseleandro.scaniqo.domain.repository

import com.joseleandro.scaniqo.domain.model.DocumentModel
import com.joseleandro.scaniqo.domain.model.ScannedDocument
import kotlinx.coroutines.flow.Flow

interface DocumentRepository {
    fun observeDocuments(): Flow<List<DocumentModel>>

    suspend fun findById(id: Long): DocumentModel?

    suspend fun prepareScan(pageUris: List<String>): ScannedDocument

    suspend fun discardScan(scan: ScannedDocument)

    suspend fun create(scan: ScannedDocument, name: String, tagId: Long?): Long

    suspend fun update(id: Long, name: String, tagId: Long?)

    suspend fun replaceContent(
        id: Long,
        scan: ScannedDocument,
        allowProtectionRemoval: Boolean = false
    ): DocumentModel

    suspend fun delete(document: DocumentModel)

    suspend fun export(document: DocumentModel, targetUri: String)

    suspend fun prepareShare(document: DocumentModel): String
}
