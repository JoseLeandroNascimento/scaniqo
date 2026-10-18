package com.joseleandro.scaniqo.ui.documentCommon.components

import androidx.compose.runtime.Composable
import com.joseleandro.scaniqo.ui.documentCommon.event.DocumentActionEvent
import com.joseleandro.scaniqo.ui.documentCommon.state.DocumentActionsUiState
import com.joseleandro.scaniqo.ui.documentCommon.state.DocumentMessage

/** Conecta o estado e as ações comuns aos componentes de edição e exclusão. */
@Composable
internal fun DocumentActionOverlays(
    state: DocumentActionsUiState,
    onEvent: (DocumentActionEvent) -> Unit,
) {
    DocumentOverlays(
        editor = state.editor,
        tags = state.tags,
        pendingDeletion = state.pendingDeletion,
        isDeleting = state.isDeleting,
        deleteError = state.message == DocumentMessage.DELETE_ERROR,
        onNameChange = { onEvent(DocumentActionEvent.NameChanged(it)) },
        onTagChange = { onEvent(DocumentActionEvent.TagChanged(it)) },
        onSave = { onEvent(DocumentActionEvent.SaveDocument) },
        onDismissEditor = { onEvent(DocumentActionEvent.DismissEditor) },
        onConfirmDelete = { onEvent(DocumentActionEvent.ConfirmDelete) },
        onDismissDelete = { onEvent(DocumentActionEvent.DismissDelete) },
    )
}
