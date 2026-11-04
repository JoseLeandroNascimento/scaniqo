package com.joseleandro.scaniqo.domain.model

const val TAG_NAME_MAX_LENGTH = 50

data class TagModel(
    val id: Long = 0,
    val name: String,
    val colorArgb: Int,
    val iconKey: String = TagIcon.FOLDER.key
) {
    val icon: TagIcon
        get() = TagIcon.fromKey(iconKey)
}
