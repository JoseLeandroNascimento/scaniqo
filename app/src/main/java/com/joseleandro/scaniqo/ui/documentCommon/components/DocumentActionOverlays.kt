package com.joseleandro.scaniqo.ui.documentCommon.components

import androidx.compose.runtime.Composable
import com.joseleandro.scaniqo.core.ads.RewardedTool
import com.joseleandro.scaniqo.domain.model.DOCUMENT_NAME_MAX_LENGTH
import com.joseleandro.scaniqo.ui.ads.rememberToolAdAction
import com.joseleandro.scaniqo.ui.documentCommon.event.DocumentActionEvent
import com.joseleandro.scaniqo.ui.documentCommon.state.DocumentActionsUiState
import com.joseleandro.scaniqo.ui.documentCommon.state.DocumentMessage

/** Conecta o estado e as ações comuns aos componentes de edição e exclusão. */
@Composable
internal fun DocumentActionOverlays(
    state: DocumentActionsUiState,
    onEvent: (DocumentActionEvent) -> Unit,
) {
    val imported = state.editor?.takeIf { it.requiresImportAd && it.documentId == null && it.draft != null }
    val validName = imported?.name?.trim()?.length in 1..DOCUMENT_NAME_MAX_LENGTH
    val importAction = if (imported != null) {
        rememberToolAdAction(
            tool = RewardedTool.IMPORT_PDF,
            contextKey = listOf(imported.draft?.pdfUri, imported.name, imported.tagId),
            active = !state.isBusy && validName &&
                (imported.tagId == null || (!state.isLoadingTags && !state.tagsLoadError)),
            onApply = { unavailable ->
                imported.draft?.let { draft ->
                    onEvent(DocumentActionEvent.SaveImportedDocument(draft.pdfUri, unavailable))
                }
            },
        )
    } else null
    DocumentOverlays(
        editor = state.editor?.let { if (importAction?.inProgress == true) it.copy(isSaving = true) else it },
        saveShowsVideo = importAction?.showsVideo == true,
        tags = state.tags,
        isLoadingTags = state.isLoadingTags,
        tagsLoadError = state.tagsLoadError,
        onRetryTags = { onEvent(DocumentActionEvent.RetryTags) },
        pendingDeletion = state.pendingDeletion,
        isDeleting = state.isDeleting,
        deleteError = state.message == DocumentMessage.DELETE_ERROR,
        onNameChange = { onEvent(DocumentActionEvent.NameChanged(it)) },
        onTagChange = { onEvent(DocumentActionEvent.TagChanged(it)) },
        onSave = {
            if (importAction != null && validName) importAction.onClick()
            else onEvent(DocumentActionEvent.SaveDocument)
        },
        onDismissEditor = { onEvent(DocumentActionEvent.DismissEditor) },
        onConfirmDelete = { onEvent(DocumentActionEvent.ConfirmDelete) },
        onDismissDelete = { onEvent(DocumentActionEvent.DismissDelete) },
    )
}
