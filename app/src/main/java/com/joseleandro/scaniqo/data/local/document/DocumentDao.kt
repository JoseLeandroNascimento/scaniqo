package com.joseleandro.scaniqo.data.local.document

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {
    @Query("SELECT * FROM documents ORDER BY createdAt DESC, id DESC")
    fun observeAll(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE id = :id")
    suspend fun findById(id: Long): DocumentEntity?

    @Query("SELECT * FROM documents WHERE pdfUri = :pdfUri LIMIT 1")
    suspend fun findByPdfUri(pdfUri: String): DocumentEntity?

    @Insert
    suspend fun insert(document: DocumentEntity): Long

    // A edição altera somente os campos do formulário, preservando o arquivo e sua origem.
    @Query("UPDATE documents SET name = :name, tagId = :tagId WHERE id = :id")
    suspend fun updateMetadata(id: Long, name: String, tagId: Long?): Int

    @Query(
        """UPDATE documents SET pdfUri = :pdfUri, pageCount = :pageCount,
           sizeBytes = :sizeBytes, thumbnailUri = :thumbnailUri,
           protectionKeyId = :protectionKeyId WHERE id = :id"""
    )
    suspend fun replaceContent(
        id: Long,
        pdfUri: String,
        pageCount: Int,
        sizeBytes: Long,
        thumbnailUri: String?,
        protectionKeyId: String?
    ): Int

    @Delete
    suspend fun delete(document: DocumentEntity): Int
}
