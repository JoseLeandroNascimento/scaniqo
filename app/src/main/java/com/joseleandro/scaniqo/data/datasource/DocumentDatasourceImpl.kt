package com.joseleandro.scaniqo.data.datasource

import com.joseleandro.scaniqo.data.local.document.DocumentDao
import com.joseleandro.scaniqo.data.local.document.DocumentEntity
import kotlinx.coroutines.flow.Flow

class DocumentDatasourceImpl(
    private val documentDao: DocumentDao
) : DocumentDatasource {

    override val documentsObserver: Flow<List<DocumentEntity>>
        get() = documentDao.observeAll()

    override val trashedDocumentsObserver: Flow<List<DocumentEntity>>
        get() = documentDao.observeTrashed()

    override suspend fun findById(id: Long): DocumentEntity? = documentDao.findById(id)

    override suspend fun findTrashedById(id: Long): DocumentEntity? = documentDao.findTrashedById(id)

    override suspend fun findByPdfUri(pdfUri: String): DocumentEntity? =
        documentDao.findByPdfUri(pdfUri)

    override suspend fun insert(document: DocumentEntity): Long = documentDao.insert(document)

    override suspend fun updateMetadata(id: Long, name: String, tagId: Long?): Int =
        documentDao.updateMetadata(id, name, tagId)

    override suspend fun replaceContent(document: DocumentEntity): Int =
        documentDao.replaceContent(
            document.id, document.pdfUri, document.pageCount, document.sizeBytes,
            document.thumbnailUri, document.protectionKeyId
        )

    override suspend fun moveToTrash(id: Long, deletedAt: Long): Int =
        documentDao.moveToTrash(id, deletedAt)

    override suspend fun restoreFromTrash(id: Long): Int = documentDao.restoreFromTrash(id)

    override suspend fun deleteTrashed(id: Long): Int = documentDao.deleteTrashed(id)

    override suspend fun delete(document: DocumentEntity): Int = documentDao.delete(document)
}
