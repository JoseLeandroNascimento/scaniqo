package com.joseleandro.scaniqo.data.datasource

import com.joseleandro.scaniqo.data.local.tag.TagEntity
import kotlinx.coroutines.flow.Flow

interface TagDatasource {

    val tagsObserver: Flow<List<TagEntity>>

    suspend fun findById(id: Long): TagEntity?

    suspend fun insert(tag: TagEntity): Long

    suspend fun update(tag: TagEntity): Int

    suspend fun delete(tag: TagEntity): Int
}