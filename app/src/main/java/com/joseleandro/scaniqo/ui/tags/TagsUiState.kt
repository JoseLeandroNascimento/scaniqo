package com.joseleandro.scaniqo.ui.tags

import androidx.compose.ui.graphics.Color
import com.joseleandro.scaniqo.domain.model.TagIcon
import com.joseleandro.scaniqo.domain.model.TagModel

/** Estado único da lista, do formulário e da confirmação de exclusão. */
data class TagsUiState(
    val tags: List<TagModel> = emptyList(),
    val isLoading: Boolean = true,
    val loadError: Boolean = false,
    val editor: TagEditorUiState? = null,
    val pendingDeletion: TagModel? = null,
    val isDeleting: Boolean = false,
    val message: TagsMessage? = null,
)

/** O formulário permanece aberto com os valores preenchidos quando o salvamento falha. */
data class TagEditorUiState(
    val id: Long? = null,
    val name: String = "",
    val iconKey: String = TagIcon.FOLDER.key,
    val color: Color = Color(0xFF4F7CAC),
    val isSaving: Boolean = false,
    val nameError: TagNameError? = null,
    val saveError: Boolean = false,
)

enum class TagNameError {
    REQUIRED,
    TOO_LONG,
}

enum class TagsMessage {
    SAVED,
    DELETED,
    DELETE_ERROR,
}
