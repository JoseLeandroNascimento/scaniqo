package com.joseleandro.scaniqo.ui.documentEditor

import androidx.activity.compose.BackHandler
import androidx.compose.material3.AlertDialog
import com.joseleandro.scaniqo.ui.components.DialogActions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.joseleandro.scaniqo.R
import com.joseleandro.scaniqo.core.security.SecureWindow
import com.joseleandro.scaniqo.ui.components.toast.AppToastHost
import com.joseleandro.scaniqo.ui.components.toast.rememberAppToastState
import com.joseleandro.scaniqo.ui.documentEditor.components.DocumentToolsMessages
import com.joseleandro.scaniqo.ui.documentEditor.components.DocumentToolsSheets
import com.joseleandro.scaniqo.ui.documentEditor.event.DocumentToolsEvent
import com.joseleandro.scaniqo.ui.documentEditor.model.DocumentTool
import com.joseleandro.scaniqo.ui.documentEditor.model.hasEditorScreen

/** Ajustes e correções mantêm um rascunho local; páginas atualizam a mesma sessão editável. */
@Composable
fun DocumentEditorToolRoute(
    documentId: Long,
    tool: DocumentTool,
    viewerViewModel: DocumentViewerViewModel,
    toolsViewModel: DocumentToolsViewModel,
    isActive: Boolean,
    onBack: () -> Unit,
) {
    require(tool.hasEditorScreen)
    val viewer by viewerViewModel.uiState.collectAsStateWithLifecycle()
    val state by toolsViewModel.uiState.collectAsStateWithLifecycle()
    val document = viewer.document?.takeIf { it.id == documentId }
    val sameSession = state.document?.id == documentId
    var draftChanged by rememberSaveable(documentId, tool) { mutableStateOf(false) }
    var confirmExit by rememberSaveable(documentId, tool) { mutableStateOf(false) }
    var leaving by remember(documentId, tool) { mutableStateOf(false) }
    var applied by remember(documentId, tool) { mutableStateOf(false) }
    val active by rememberUpdatedState(isActive)
    val toast = rememberAppToastState()
    SecureWindow(enabled = document?.isProtected == true || (sameSession && state.workspace?.isProtected == true))

    LaunchedEffect(documentId, isActive) { if (isActive) viewerViewModel.load(documentId) }
    LaunchedEffect(document?.id, document?.pdfUri, isActive) {
        if (isActive) document?.let(toolsViewModel::load)
    }
    LaunchedEffect(isActive, sameSession, state.canEdit, state.tool, leaving, applied) {
        if (isActive && sameSession && state.canEdit && !leaving && !applied && state.tool != tool) {
            toolsViewModel.onEvent(DocumentToolsEvent.ActivateEditorTool(tool))
        }
    }

    val onEvent: (DocumentToolsEvent) -> Unit = { event ->
        if (active && !leaving && !applied && toolsViewModel.uiState.value.document?.id == documentId) {
            val previousTool = toolsViewModel.uiState.value.tool
            toolsViewModel.onEvent(event)
            if ((event is DocumentToolsEvent.ApplyLayout || event is DocumentToolsEvent.ApplyCorrections) &&
                previousTool == tool && toolsViewModel.uiState.value.tool == null
            ) {
                applied = true
                draftChanged = false
            }
        }
    }
    val leave = {
        val current = toolsViewModel.uiState.value
        if (active && !leaving && !applied && (!current.isBusy || current.isLoading)) {
            leaving = true
            toolsViewModel.onEvent(DocumentToolsEvent.DismissPageDeletion)
            toolsViewModel.onEvent(DocumentToolsEvent.DismissTool)
            onBack()
        }
    }
    val requestBack = {
        val current = toolsViewModel.uiState.value
        if (active && !leaving && !applied && (!current.isBusy || current.isLoading)) {
            if (tool != DocumentTool.PAGES && draftChanged) confirmExit = true else leave()
        }
    }
    BackHandler(enabled = isActive && !leaving, onBack = requestBack)
    if (isActive && sameSession) {
        DocumentToolsMessages(state, onEvent, toast)
        DocumentToolsSheets(state, onEvent)
    }
    DocumentEditorToolScreen(
        documentId = documentId,
        tool = tool,
        state = state,
        documentLoading = viewer.isLoading || viewer.requestedDocumentId != documentId,
        documentError = (viewer.loadError && viewer.requestedDocumentId == documentId) ||
            (!viewer.isLoading && viewer.requestedDocumentId == documentId && document == null),
        enabled = isActive && !leaving && !applied,
        onEvent = onEvent,
        onDraftChanged = { if (isActive && !leaving && !applied) draftChanged = it },
        onBack = requestBack,
        onFinish = leave,
        onRetry = {
            viewerViewModel.retry()
            toolsViewModel.onEvent(DocumentToolsEvent.Retry)
        },
        snackbarHost = { AppToastHost(toast) },
        hasLocalDraft = draftChanged,
    )
    if (confirmExit && isActive) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text(stringResource(R.string.editor_tool_discard_title)) },
            text = { Text(stringResource(R.string.editor_tool_discard_body)) },
            confirmButton = {
                DialogActions(
                    confirmText = stringResource(R.string.tools_discard),
                    onConfirm = { confirmExit = false; leave() },
                    dismissText = stringResource(R.string.tools_keep_editing),
                    onDismiss = { confirmExit = false },
                    confirmEnabled = !state.isBusy,
                    destructive = true,
                )
            },
        )
    }
}
