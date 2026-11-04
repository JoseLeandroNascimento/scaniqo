package com.joseleandro.scaniqo.data.local.tag

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {
    @Query("SELECT * FROM tags ORDER BY name COLLATE NOCASE ASC, id ASC")
    fun observeAll(): Flow<List<TagEntity>>

    @Query("SELECT * FROM tags")
    suspend fun findAll(): List<TagEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM tag_defaults_state WHERE id = 1)")
    suspend fun defaultsInitialized(): Boolean

    @Insert
    suspend fun markDefaultsInitialized(state: TagDefaultsState)

    @Query("SELECT * FROM tags WHERE id = :id")
    suspend fun findById(id: Long): TagEntity?

    @Insert
    suspend fun insert(tag: TagEntity): Long

    @Update
    suspend fun update(tag: TagEntity): Int

    @Delete
    suspend fun delete(tag: TagEntity): Int
}
