package com.joseleandro.scaniqo.data.repository

import com.joseleandro.scaniqo.data.datasource.TagDatasource
import com.joseleandro.scaniqo.domain.mapper.toEntity
import com.joseleandro.scaniqo.domain.mapper.toUiModel
import com.joseleandro.scaniqo.domain.model.TAG_NAME_MAX_LENGTH
import com.joseleandro.scaniqo.domain.model.TagModel
import com.joseleandro.scaniqo.domain.repository.TagRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TagRepositoryImpl(
    private val tagDatasource: TagDatasource
) : TagRepository {

    override fun observeTags(): Flow<List<TagModel>> =
        tagDatasource.tagsObserver.map { tags -> tags.map { it.toUiModel() } }

    override suspend fun findById(id: Long): TagModel? =
        tagDatasource.findById(id)?.toUiModel()

    override suspend fun create(tag: TagModel): Long =
        tagDatasource.insert(tag.withValidatedName().toEntity())

    override suspend fun update(tag: TagModel) {
        val affectedRows = tagDatasource.update(tag.withValidatedName().toEntity())
        // Uma tag removida durante a edição não pode produzir uma confirmação falsa.
        check(affectedRows == 1) { "A tag não foi encontrada para atualização." }
    }

    override suspend fun delete(tag: TagModel) {
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
