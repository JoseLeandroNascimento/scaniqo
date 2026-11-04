package com.joseleandro.scaniqo.data.repository

import com.joseleandro.scaniqo.data.datasource.TagDatasource
import com.joseleandro.scaniqo.data.mapper.toEntity
import com.joseleandro.scaniqo.data.mapper.toDomain
import com.joseleandro.scaniqo.domain.model.TAG_NAME_MAX_LENGTH
import com.joseleandro.scaniqo.domain.model.TagModel
import com.joseleandro.scaniqo.domain.repository.TagRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll

class TagRepositoryImpl(
    private val tagDatasource: TagDatasource,
    private val initializeDefaults: suspend () -> Unit = {},
) : TagRepository {

    override fun observeTags(): Flow<List<TagModel>> = flow {
        initializeDefaults()
        emitAll(tagDatasource.tagsObserver.map { tags -> tags.map { it.toDomain() } })
    }

    override suspend fun findById(id: Long): TagModel? {
        initializeDefaults()
        return tagDatasource.findById(id)?.toDomain()
    }

    override suspend fun create(tag: TagModel): Long {
        val validated = tag.withValidatedName().toEntity()
        initializeDefaults()
        return tagDatasource.insert(validated)
    }

    override suspend fun update(tag: TagModel) {
        val validated = tag.withValidatedName().toEntity()
        initializeDefaults()
        val affectedRows = tagDatasource.update(validated)
        // Uma tag removida durante a edição não pode produzir uma confirmação falsa.
        check(affectedRows == 1) { "A tag não foi encontrada para atualização." }
    }

    override suspend fun delete(tag: TagModel) {
        initializeDefaults()
        val affectedRows = tagDatasource.delete(tag.toEntity())
        check(affectedRows == 1) { "A tag não foi encontrada para exclusão." }
    }

    private fun TagModel.withValidatedName(): TagModel {
        val normalizedName = name.trim()
        require(normalizedName.isNotEmpty()) { "Informe o nome da tag." }
        require(normalizedName.length <= TAG_NAME_MAX_LENGTH) {
            "O nome da tag deve ter até $TAG_NAME_MAX_LENGTH caracteres."
        }
        return copy(name = normalizedName)
    }
}
