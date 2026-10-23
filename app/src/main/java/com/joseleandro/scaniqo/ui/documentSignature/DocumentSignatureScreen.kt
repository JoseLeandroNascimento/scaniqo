package com.joseleandro.scaniqo.ui.documentSignature

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.joseleandro.scaniqo.R
import com.joseleandro.scaniqo.ui.components.toast.AppToastHost
import com.joseleandro.scaniqo.ui.components.toast.rememberAppToastState
import com.joseleandro.scaniqo.ui.documentCommon.components.DocumentStatus
import com.joseleandro.scaniqo.ui.documentEditor.components.DocumentToolsMessages
import com.joseleandro.scaniqo.ui.documentEditor.event.DocumentToolsEvent
import com.joseleandro.scaniqo.ui.documentEditor.state.DocumentToolsUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DocumentSignatureScreen(
    documentId: Long,
    state: DocumentToolsUiState,
    documentName: String,
    isLoadingDocument: Boolean,
    documentError: Boolean,
    onEvent: (DocumentToolsEvent) -> Unit,
    onBack: () -> Unit,
    onCreateSignature: () -> Unit,
    onRetry: () -> Unit,
    onApply: () -> Unit,
    initialAdjustmentMode: SignatureAdjustmentMode = SignatureAdjustmentMode.MOVE,
) {
    val sameSession = state.document?.id == documentId
    val workspace = state.workspace?.takeIf { sameSession }
    val page = workspace?.pages?.getOrNull(state.selectedPageIndex)
    val draft = state.signatureDraft?.takeIf { sameSession && state.signaturePageId == page?.id }
    var showCrop by rememberSaveable(draft?.id) { mutableStateOf(false) }
    var showControls by rememberSaveable(documentId) { mutableStateOf(false) }
    LaunchedEffect(draft?.id) { if (draft != null) showControls = false }
    var adjustmentMode by rememberSaveable(draft?.id) { mutableStateOf(initialAdjustmentMode) }
    var adjustmentActive by remember(draft?.id) { mutableStateOf(false) }
    val controlsPage = page?.takeIf { showControls && !state.isLocked }
    val closeControls = {
        showControls = false
        adjustmentActive = false
    }
    BackHandler(enabled = controlsPage != null, onBack = closeControls)
    val toast = rememberAppToastState()
    val pageStateHolder = rememberSaveableStateHolder()
    DocumentToolsMessages(state, onEvent, toast)
    val visiblePageId = page?.id?.takeIf { controlsPage == null || draft != null }
    DisposableEffect(workspace?.workspaceId, visiblePageId) {
        val pageId = visiblePageId
        if (pageId != null) onEvent(DocumentToolsEvent.PageVisibility(pageId, true))
        onDispose {
            if (pageId != null) onEvent(DocumentToolsEvent.PageVisibility(pageId, false))
        }
    }
    if (controlsPage != null) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(stringResource(if (draft == null) R.string.signature_library else R.string.preview_controls_show))
                    },
                    navigationIcon = {
                        IconButton(onClick = closeControls) {
                            Icon(painterResource(R.drawable.arrow_back_24px), stringResource(R.string.signature_back))
                        }
                    },
                )
            },
            snackbarHost = { AppToastHost(toast) },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
                if (draft != null) {
                    val pageModifier = Modifier.weight(1f).fillMaxWidth()
                    pageStateHolder.SaveableStateProvider("${workspace.workspaceId}:${controlsPage.id}") {
                        DocumentSignaturePage(
                            state = state, workspace = workspace, page = controlsPage, draft = draft,
                            adjustmentMode = adjustmentMode,
                            adjustmentActive = adjustmentActive && state.canEdit && !showCrop,
                            onEvent = onEvent,
                            modifier = pageModifier,
                        )
                    }
                }
                DocumentSignatureControls(
                    state = state, page = controlsPage, draft = draft, onEvent = onEvent,
                    adjustmentMode = adjustmentMode,
                    onAdjustmentModeChange = { adjustmentMode = it; adjustmentActive = false },
                    onAdjustmentActiveChange = { adjustmentActive = it },
                    onCreateSignature = { closeControls(); onCreateSignature() },
                    onCrop = { closeControls(); showCrop = true },
                    onApply = { closeControls(); onApply() },
                    modifier = if (draft == null) Modifier.fillMaxSize()
                        else Modifier.weight(1f).fillMaxWidth(),
                )
            }
        }
    } else {
        Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.signature_document_title))
                        if (documentName.isNotBlank()) {
                            Text(
                                documentName,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !state.isBusy) {
                        Icon(painterResource(R.drawable.arrow_back_24px), stringResource(R.string.signature_back))
                    }
                },
            )
        },
        snackbarHost = { AppToastHost(toast) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            when {
                state.isLocked && sameSession -> DocumentStatus(
                    title = stringResource(R.string.tools_locked_title),
                    description = stringResource(if (state.recoveryRequired) R.string.tools_recovery_description else R.string.tools_locked_description),
                    actionLabel = stringResource(if (state.recoveryRequired) R.string.tools_recovery_action else R.string.tools_unlock),
                    onAction = { onEvent(if (state.recoveryRequired) DocumentToolsEvent.RequestPasswordRecovery else DocumentToolsEvent.Unlock) },
                )
                documentError || (state.loadError && sameSession) -> DocumentStatus(
                    title = stringResource(R.string.tools_load_error),
                    description = stringResource(R.string.documents_error_body),
                    actionLabel = stringResource(R.string.signature_retry),
                    onAction = onRetry,
                )
                isLoadingDocument || !sameSession || page == null -> Box(
                    Modifier.fillMaxSize(), contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
                else -> {
                    Column(Modifier.fillMaxSize()) {
                        val pageModifier = Modifier.weight(1f).fillMaxWidth()
                        pageStateHolder.SaveableStateProvider("${workspace.workspaceId}:${page.id}") {
                            DocumentSignaturePage(
                                state = state, workspace = workspace, page = page, draft = draft,
                                adjustmentMode = adjustmentMode,
                                adjustmentActive = adjustmentActive && state.canEdit && !showCrop,
                                onEvent = onEvent,
                                modifier = pageModifier,
                            )
                        }
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(onClick = { showControls = true }, enabled = state.canEdit,
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                                Text(stringResource(if (draft == null) R.string.signature_library else R.string.preview_controls_show))
                            }
                            if (draft != null) Button(onClick = onApply,
                                enabled = state.canEdit && !state.isRendering,
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                                Text(stringResource(R.string.signature_apply))
                            }
                        }
                    }
                }
            }
        }
        }
    }
    if (showCrop && draft != null && !state.isLocked) {
        SignatureCropDialog(
            uri = draft.imageUri,
            aspectRatio = draft.width * (595f / 842f) / draft.height,
            onDismiss = { showCrop = false },
            onApply = { left, top, right, bottom ->
                showCrop = false
                onEvent(DocumentToolsEvent.CropSignature(left, top, right, bottom))
            },
        )
    }
}
