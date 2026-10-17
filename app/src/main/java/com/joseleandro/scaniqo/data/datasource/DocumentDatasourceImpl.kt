package com.joseleandro.scaniqo.data.datasource

import com.joseleandro.scaniqo.data.local.document.DocumentDao
import com.joseleandro.scaniqo.data.local.document.DocumentEntity
import kotlinx.coroutines.flow.Flow

class DocumentDatasourceImpl(
    private val documentDao: DocumentDao
) : DocumentDatasource {

    override val documentsObserver: Flow<List<DocumentEntity>>
        get() = documentDao.observeAll()

    override suspend fun findById(id: Long): DocumentEntity? = documentDao.findById(id)

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

    override suspend fun delete(document: DocumentEntity): Int = documentDao.delete(document)
}
