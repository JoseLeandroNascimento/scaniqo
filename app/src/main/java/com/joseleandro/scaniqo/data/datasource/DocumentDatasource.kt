package com.joseleandro.scaniqo.data.datasource

import com.joseleandro.scaniqo.data.local.document.DocumentEntity
import kotlinx.coroutines.flow.Flow

interface DocumentDatasource {
    val documentsObserver: Flow<List<DocumentEntity>>

    suspend fun findById(id: Long): DocumentEntity?

    suspend fun findByPdfUri(pdfUri: String): DocumentEntity?

    suspend fun insert(document: DocumentEntity): Long

    suspend fun updateMetadata(id: Long, name: String, tagId: Long?): Int

    suspend fun replaceContent(document: DocumentEntity): Int

    suspend fun delete(document: DocumentEntity): Int
}
