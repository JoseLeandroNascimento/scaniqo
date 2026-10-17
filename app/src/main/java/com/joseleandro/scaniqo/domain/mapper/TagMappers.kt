package com.joseleandro.scaniqo.domain.mapper

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.joseleandro.scaniqo.data.local.tag.TagEntity
import com.joseleandro.scaniqo.domain.model.TagModel

fun TagEntity.toUiModel(): TagModel = TagModel(
    id = id,
    name = name,
    color = Color(colorArgb),
    iconKey = iconKey
)

fun TagModel.toEntity(): TagEntity = TagEntity(
    id = id,
    name = name,
    colorArgb = color.toArgb(),
    iconKey = iconKey
)
