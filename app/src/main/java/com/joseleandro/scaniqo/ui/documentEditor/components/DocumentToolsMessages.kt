package com.joseleandro.scaniqo.ui.documentEditor.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.res.stringResource
import com.joseleandro.scaniqo.R
import com.joseleandro.scaniqo.ui.components.toast.AppToastState
import com.joseleandro.scaniqo.ui.components.toast.AppToastType
import com.joseleandro.scaniqo.ui.documentEditor.event.DocumentToolsEvent
import com.joseleandro.scaniqo.ui.documentEditor.model.DocumentToolsMessage
import com.joseleandro.scaniqo.ui.documentEditor.state.DocumentToolsUiState

@Composable
internal fun DocumentToolsMessages(
    state: DocumentToolsUiState,
    onEvent: (DocumentToolsEvent) -> Unit,
    toastState: AppToastState,
) {
    val resource = when (state.message) {
        DocumentToolsMessage.SAVED -> R.string.tools_saved
        DocumentToolsMessage.SAVE_ERROR -> R.string.tools_save_error
        DocumentToolsMessage.SCAN_ERROR -> R.string.tools_scan_error
        DocumentToolsMessage.APPEND_ERROR -> R.string.tools_append_error
        DocumentToolsMessage.SIGNATURES_ERROR -> R.string.tools_signatures_error
        DocumentToolsMessage.CLEANUP_ERROR -> R.string.tools_cleanup_error
        DocumentToolsMessage.MERGED -> R.string.merge_tools_success
        DocumentToolsMessage.MERGE_ERROR -> R.string.merge_tools_error
        DocumentToolsMessage.MERGE_LIMIT_ERROR -> R.string.merge_tools_limit_error
        null -> null
    }
    val text = resource?.let { stringResource(it) }
    LaunchedEffect(state.message) {
        if (text != null) {
            toastState.show(
                text,
                if (state.message == DocumentToolsMessage.SAVED || state.message == DocumentToolsMessage.MERGED) AppToastType.SUCCESS else AppToastType.ERROR,
            )
            onEvent(DocumentToolsEvent.DismissMessage)
        }
    }
}
