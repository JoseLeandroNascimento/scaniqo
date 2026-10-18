package com.joseleandro.scaniqo.ui.documentCommon.components

import androidx.compose.runtime.Composable
import com.joseleandro.scaniqo.domain.model.DocumentModel
import com.joseleandro.scaniqo.domain.model.TagModel
import com.joseleandro.scaniqo.ui.documentCommon.state.DocumentMetadataUiState

@Composable
internal fun DocumentOverlays(
    editor: DocumentMetadataUiState?,
    tags: List<TagModel>,
    isLoadingTags: Boolean,
    tagsLoadError: Boolean,
    onRetryTags: () -> Unit,
    pendingDeletion: DocumentModel?,
    isDeleting: Boolean,
    deleteError: Boolean,
    onNameChange: (String) -> Unit,
    onTagChange: (Long?) -> Unit,
    onSave: () -> Unit,
    onDismissEditor: () -> Unit,
    onConfirmDelete: () -> Unit,
    onDismissDelete: () -> Unit,
) {
    editor?.let {
        DocumentMetadataSheet(
            editor = it,
            tags = tags,
            isLoadingTags = isLoadingTags,
            tagsLoadError = tagsLoadError,
            onRetryTags = onRetryTags,
            onNameChange = onNameChange,
            onTagChange = onTagChange,
            onSave = onSave,
            onDismiss = onDismissEditor,
        )
    }
    pendingDeletion?.let { document ->
        DocumentDeleteDialog(
            documentName = document.name,
            isDeleting = isDeleting,
            hasError = deleteError,
            onConfirm = onConfirmDelete,
            onDismiss = onDismissDelete,
        )
    }
}
