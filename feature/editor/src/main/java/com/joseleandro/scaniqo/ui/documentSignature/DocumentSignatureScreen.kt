package com.joseleandro.scaniqo.ui.documentSignature

import com.joseleandro.scaniqo.core.resources.R as CoreResourcesR
import com.joseleandro.scaniqo.feature.editor.R

import com.joseleandro.scaniqo.ui.signatures.crop.SignatureCropDialog

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.joseleandro.scaniqo.ui.components.ScaniqoLoadingIndicator
import com.joseleandro.scaniqo.ui.components.toast.AppToastHost
import com.joseleandro.scaniqo.ui.components.toast.rememberAppToastState
import com.joseleandro.scaniqo.ui.documentCommon.components.DocumentStatus
import com.joseleandro.scaniqo.ui.documentEditor.components.DocumentToolsMessages
import com.joseleandro.scaniqo.ui.documentEditor.components.DocumentEditorPrimaryActions
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
    onCreateSignature: (replacing: Boolean) -> Unit,
    onRetry: () -> Unit,
    onApply: () -> Unit,
    onDone: () -> Unit = onBack,
    initialAdjustmentMode: SignatureAdjustmentMode = SignatureAdjustmentMode.MOVE,
    initiallyShowControls: Boolean = false,
) {
    val sameSession = state.document?.id == documentId
    val workspace = state.workspace?.takeIf { sameSession }
    val page = workspace?.pages?.getOrNull(state.selectedPageIndex)
    val draft = state.signatureDraft?.takeIf { sameSession && state.signaturePageId == page?.id }
    var showCrop by rememberSaveable(draft?.id) { mutableStateOf(false) }
    var showControls by rememberSaveable(documentId) { mutableStateOf(initiallyShowControls) }
    var showPicker by rememberSaveable(documentId) { mutableStateOf(false) }
    var replacing by rememberSaveable(documentId) { mutableStateOf(false) }
    var adjustmentMode by rememberSaveable(draft?.id) { mutableStateOf(initialAdjustmentMode) }
    var adjustmentActive by remember(draft?.id) { mutableStateOf(false) }
    var sheetHeight by remember { mutableStateOf(0.dp) }
    val controlsVisible = showControls && draft != null && !state.signatureAwaitingPlacement && !state.isLocked
    val closeControls = { showControls = false; adjustmentActive = false }
    val cancelEdit = { closeControls(); onEvent(DocumentToolsEvent.CancelSignature) }
    val applyEdit = { closeControls(); onApply() }
    LaunchedEffect(draft?.id, state.isLocked) {
        if (draft == null || state.isLocked) {
            closeControls()
            showCrop = false
        }
        if (state.isLocked) showPicker = false
    }
    val toast = rememberAppToastState()
    val pageStateHolder = rememberSaveableStateHolder()
    DocumentToolsMessages(state, onEvent, toast)
    DisposableEffect(workspace?.workspaceId, page?.id) {
        val pageId = page?.id
        if (pageId != null) onEvent(DocumentToolsEvent.PageVisibility(pageId, true))
        onDispose { if (pageId != null) onEvent(DocumentToolsEvent.PageVisibility(pageId, false)) }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.signature_document_title))
                        if (documentName.isNotBlank()) Text(
                            documentName,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !state.isBusy) {
                        Icon(painterResource(CoreResourcesR.drawable.arrow_back_24px), stringResource(R.string.signature_back))
                    }
                },
                actions = {
                    if (draft == null && (state.canUndo || state.canRedo)) {
                        IconButton(
                            onClick = { onEvent(DocumentToolsEvent.Undo) },
                            enabled = state.canEdit && state.canUndo,
                        ) {
                            Icon(painterResource(CoreResourcesR.drawable.ic_document_undo), stringResource(R.string.viewer_undo_description))
                        }
                        IconButton(
                            onClick = { onEvent(DocumentToolsEvent.Redo) },
                            enabled = state.canEdit && state.canRedo,
                        ) {
                            Icon(painterResource(R.drawable.ic_document_redo), stringResource(R.string.viewer_redo_description))
                        }
                    }
                },
            )
        },
        snackbarHost = { AppToastHost(toast) },
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            val sheetBudget = if (maxHeight < 420.dp) maxHeight * .86f
                else (maxHeight * .68f).coerceAtMost(448.dp)
            val controlsMaxHeight = (sheetBudget - 48.dp).coerceAtLeast(0.dp)
            val reservedHeight by animateDpAsState(
                if (controlsVisible) (sheetHeight - padding.calculateBottomPadding())
                    .coerceIn(0.dp, maxHeight) else 0.dp,
                label = "signature_sheet_space",
            )
            when {
                state.isLocked && sameSession -> DocumentStatus(
                    title = stringResource(R.string.tools_locked_title),
                    description = stringResource(if (state.recoveryRequired) R.string.tools_recovery_description else R.string.tools_locked_description),
                    actionLabel = stringResource(if (state.recoveryRequired) R.string.tools_recovery_action else CoreResourcesR.string.tools_unlock),
                    onAction = { onEvent(if (state.recoveryRequired) DocumentToolsEvent.RequestPasswordRecovery else DocumentToolsEvent.Unlock) },
                )
                documentError || (state.loadError && sameSession) -> DocumentStatus(
                    title = stringResource(R.string.tools_load_error),
                    description = stringResource(CoreResourcesR.string.documents_error_body),
                    actionLabel = stringResource(R.string.signature_retry),
                    onAction = onRetry,
                )
                isLoadingDocument || !sameSession || page == null -> ScaniqoLoadingIndicator(
                    Modifier.fillMaxSize(),
                )
                else -> Column(Modifier.fillMaxSize()) {
                    pageStateHolder.SaveableStateProvider("${workspace.workspaceId}:${page.id}") {
                        DocumentSignaturePage(
                            state = state,
                            workspace = workspace,
                            page = page,
                            draft = draft,
                            adjustmentMode = adjustmentMode,
                            adjustmentActive = adjustmentActive && state.canEdit && !showCrop,
                            compactChrome = controlsVisible,
                            onEvent = onEvent,
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            selectionActions = {
                                if (!controlsVisible && !showCrop && !showPicker) {
                                    DocumentSignatureContextToolbar(
                                        enabled = state.canEdit && !state.isRendering,
                                        onReplace = { replacing = true; showPicker = true },
                                        onDuplicate = { onEvent(DocumentToolsEvent.DuplicateSignatureDraft) },
                                        onCrop = { showCrop = true },
                                        onDelete = { onEvent(DocumentToolsEvent.DeleteSignatureDraft) },
                                        onCancel = cancelEdit,
                                        onAdjust = { showControls = true },
                                    )
                                }
                            },
                        )
                    }
                    if (controlsVisible) {
                        Spacer(Modifier.height(reservedHeight))
                    } else {
                        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
                            Column(
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                if (state.signatureAwaitingPlacement && draft != null) {
                                    Text(
                                        stringResource(R.string.signature_place_hint),
                                        modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth(),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Row(
                                        modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        TextButton(
                                            onClick = cancelEdit,
                                            enabled = state.canEdit,
                                            modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("signature_cancel_placement"),
                                        ) { Text(stringResource(CoreResourcesR.string.signature_cancel)) }
                                        Button(
                                            onClick = { onEvent(DocumentToolsEvent.PlaceSignature(.5f, .5f)) },
                                            enabled = state.canEdit,
                                            modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("signature_place_center"),
                                        ) { Text(stringResource(R.string.signature_place_center)) }
                                    }
                                } else {
                                    DocumentEditorPrimaryActions(
                                        insertLabel = stringResource(if (draft == null) R.string.signature_add else R.string.signature_open_adjustments),
                                        doneLabel = stringResource(if (draft == null) R.string.signature_finish_tool else R.string.signature_apply_adjustments),
                                        onInsert = {
                                            if (draft == null) { replacing = false; showPicker = true }
                                            else showControls = true
                                        },
                                        onDone = if (draft == null) onDone else applyEdit,
                                        enabled = state.canEdit,
                                        modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth(),
                                        doneEnabled = state.canEdit && (draft == null || !state.isRendering),
                                        insertTag = if (draft == null) "signature_open_picker" else "signature_open_adjustments",
                                        doneTag = if (draft == null) "signature_finish_tool" else "signature_apply_adjustments",
                                    )
                                }
                            }
                        }
                    }
                }
            }
            if (controlsVisible && draft != null) {
                DocumentSignatureControls(
                    draft = draft,
                    mode = adjustmentMode,
                    enabled = state.canEdit,
                    applying = state.isRendering,
                    adjustmentActive = adjustmentActive,
                    maxHeight = controlsMaxHeight,
                    onModeChange = { adjustmentMode = it; adjustmentActive = false },
                    onEvent = onEvent,
                    onDismiss = closeControls,
                    onCancel = cancelEdit,
                    onApply = applyEdit,
                    onHeightChange = { sheetHeight = it },
                    onAdjustmentActiveChange = { adjustmentActive = it },
                )
            }
        }
    }
    if (showPicker && !state.isLocked && sameSession) {
        DocumentSignaturePickerSheet(
            signatures = state.signatures,
            selectedSignatureId = draft?.sourceSignatureId
                ?: state.signatures.firstOrNull { it.imageUri == draft?.imageUri }?.id,
            replacing = replacing,
            onSelect = { id ->
                showPicker = false
                onEvent(if (replacing) DocumentToolsEvent.ReplaceSignature(id) else DocumentToolsEvent.InsertSignature(id))
            },
            onCreate = {
                showPicker = false
                onCreateSignature(replacing)
            },
            onDismiss = { showPicker = false },
        )
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
