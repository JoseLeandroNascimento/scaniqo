package com.joseleandro.scaniqo.data.local.document

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {
    @Query("SELECT * FROM documents WHERE deletedAt IS NULL ORDER BY createdAt DESC, id DESC")
    fun observeAll(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC, id DESC")
    fun observeTrashed(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE id = :id AND deletedAt IS NULL")
    suspend fun findById(id: Long): DocumentEntity?

    @Query("SELECT * FROM documents WHERE id = :id AND deletedAt IS NOT NULL")
    suspend fun findTrashedById(id: Long): DocumentEntity?

    @Query("SELECT * FROM documents WHERE pdfUri = :pdfUri LIMIT 1")
    suspend fun findByPdfUri(pdfUri: String): DocumentEntity?

    @Insert
    suspend fun insert(document: DocumentEntity): Long

    // A edição altera somente os campos do formulário, preservando o arquivo e sua origem.
    @Query("UPDATE documents SET name = :name, tagId = :tagId WHERE id = :id AND deletedAt IS NULL")
    suspend fun updateMetadata(id: Long, name: String, tagId: Long?): Int

    @Query(
        """UPDATE documents SET pdfUri = :pdfUri, pageCount = :pageCount,
           sizeBytes = :sizeBytes, thumbnailUri = :thumbnailUri,
           protectionKeyId = :protectionKeyId WHERE id = :id AND deletedAt IS NULL"""
    )
    suspend fun replaceContent(
        id: Long,
        pdfUri: String,
        pageCount: Int,
        sizeBytes: Long,
        thumbnailUri: String?,
        protectionKeyId: String?
    ): Int

    @Query("UPDATE documents SET deletedAt = :deletedAt WHERE id = :id AND deletedAt IS NULL")
    suspend fun moveToTrash(id: Long, deletedAt: Long): Int

    @Query("UPDATE documents SET deletedAt = NULL WHERE id = :id AND deletedAt IS NOT NULL")
    suspend fun restoreFromTrash(id: Long): Int

    @Query("DELETE FROM documents WHERE id = :id AND deletedAt IS NOT NULL")
    suspend fun deleteTrashed(id: Long): Int

    @Delete
    suspend fun delete(document: DocumentEntity): Int
}
