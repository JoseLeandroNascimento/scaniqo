package com.joseleandro.scaniqo.data.datasource

import com.joseleandro.scaniqo.data.local.tag.TagDao
import com.joseleandro.scaniqo.data.local.tag.TagEntity
import kotlinx.coroutines.flow.Flow

class TagDatasourceImpl(
    private val tagDao: TagDao
) : TagDatasource {

    override val tagsObserver: Flow<List<TagEntity>>
        get() = tagDao.observeAll()

    override suspend fun findById(id: Long): TagEntity? = tagDao.findById(id)

    override suspend fun insert(tag: TagEntity): Long = tagDao.insert(tag)

    override suspend fun update(tag: TagEntity): Int = tagDao.update(tag)

    override suspend fun delete(tag: TagEntity): Int = tagDao.delete(tag)
}