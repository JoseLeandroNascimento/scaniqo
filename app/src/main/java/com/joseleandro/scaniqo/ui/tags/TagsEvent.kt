package com.joseleandro.scaniqo.ui.tags

import androidx.compose.ui.graphics.Color
import com.joseleandro.scaniqo.domain.model.TagIcon
import com.joseleandro.scaniqo.domain.model.TagModel

/** Intenções da tela; a persistência e as mudanças de estado ficam no ViewModel. */
sealed interface TagsEvent {
    data object CreateTag : TagsEvent
    data class EditTag(val tag: TagModel) : TagsEvent
    data class NameChanged(val name: String) : TagsEvent
    data class IconChanged(val icon: TagIcon) : TagsEvent
    data class ColorChanged(val color: Color) : TagsEvent
    data object SaveTag : TagsEvent
    data object DismissEditor : TagsEvent
    data class RequestDelete(val tag: TagModel) : TagsEvent
    data object ConfirmDelete : TagsEvent
    data object DismissDelete : TagsEvent
    data object RetryLoad : TagsEvent
    data object DismissMessage : TagsEvent
}
