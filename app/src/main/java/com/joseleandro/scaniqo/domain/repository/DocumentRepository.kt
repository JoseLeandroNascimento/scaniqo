package com.joseleandro.scaniqo.domain.repository

import com.joseleandro.scaniqo.domain.model.DocumentModel
import com.joseleandro.scaniqo.domain.model.ScannedDocument
import kotlinx.coroutines.flow.Flow

interface DocumentRepository {
    fun observeDocuments(): Flow<List<DocumentModel>>

    fun observeTrashedDocuments(): Flow<List<DocumentModel>>

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

    /** Retira da biblioteca sem apagar o PDF, suas páginas ou o projeto de edição. */
    suspend fun moveToTrash(id: Long)

    suspend fun restoreFromTrash(id: Long)

    /** Exclusão irreversível, permitida somente para documentos na lixeira. */
    suspend fun deletePermanently(id: Long)

    /** Limpeza de documentos ativos criados por uma operação que falhou (rollback). */
    suspend fun delete(document: DocumentModel)

    suspend fun export(document: DocumentModel, targetUri: String)

    suspend fun prepareShare(document: DocumentModel): String
}
