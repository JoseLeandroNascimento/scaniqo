package com.joseleandro.scaniqo.ui.documentCommon.feedback

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.res.stringResource
import com.joseleandro.scaniqo.R
import com.joseleandro.scaniqo.ui.components.toast.AppToastState
import com.joseleandro.scaniqo.ui.components.toast.AppToastType
import com.joseleandro.scaniqo.ui.components.toast.rememberAppToastState
import com.joseleandro.scaniqo.ui.documentCommon.state.DocumentMessage

@Composable
internal fun rememberDocumentToastState(
    message: DocumentMessage?,
    onMessageShown: () -> Unit,
): AppToastState {
    val state = rememberAppToastState()
    val resource = when (message) {
        DocumentMessage.SAVED -> R.string.document_saved
        DocumentMessage.UPDATED -> R.string.document_updated
        DocumentMessage.DELETED -> R.string.document_deleted
        DocumentMessage.DOWNLOADED -> R.string.document_downloaded
        DocumentMessage.SCAN_ERROR -> R.string.document_scan_error
        DocumentMessage.PREPARE_ERROR -> R.string.document_prepare_error
        DocumentMessage.EXPORT_ERROR -> R.string.document_export_error
        DocumentMessage.SHARE_ERROR -> R.string.document_share_error
        else -> null
    }
    val text = resource?.let { stringResource(it) }
    val type = when (message) {
        DocumentMessage.SCAN_ERROR, DocumentMessage.PREPARE_ERROR,
        DocumentMessage.EXPORT_ERROR, DocumentMessage.SHARE_ERROR -> AppToastType.ERROR

        else -> AppToastType.SUCCESS
    }
    LaunchedEffect(message) {
        if (text != null) {
            state.show(message = text, type = type)
            onMessageShown()
        }
    }
    return state
}
