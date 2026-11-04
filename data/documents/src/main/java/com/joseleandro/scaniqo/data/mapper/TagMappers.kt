package com.joseleandro.scaniqo.data.mapper

import com.joseleandro.scaniqo.data.local.tag.TagEntity
import com.joseleandro.scaniqo.domain.model.TagModel

fun TagEntity.toDomain(): TagModel = TagModel(
    id = id,
    name = name,
    colorArgb = colorArgb,
    iconKey = iconKey
)

fun TagModel.toEntity(): TagEntity = TagEntity(
    id = id,
    name = name,
    colorArgb = colorArgb,
    iconKey = iconKey
)
