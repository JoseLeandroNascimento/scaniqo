package com.joseleandro.scaniqo.domain.repository

import com.joseleandro.scaniqo.domain.model.TagModel
import kotlinx.coroutines.flow.Flow

/** Expõe as tags persistidas e as operações disponíveis para a interface. */
interface TagRepository {

    fun observeTags(): Flow<List<TagModel>>

    suspend fun findById(id: Long): TagModel?

    suspend fun create(tag: TagModel): Long

    suspend fun update(tag: TagModel)

    suspend fun delete(tag: TagModel)
}
