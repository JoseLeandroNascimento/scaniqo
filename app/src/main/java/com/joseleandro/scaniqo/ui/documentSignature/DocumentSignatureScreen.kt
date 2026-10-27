package com.joseleandro.scaniqo.ui.documentSignature

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joseleandro.scaniqo.R
import com.joseleandro.scaniqo.ui.components.toast.AppToastHost
import com.joseleandro.scaniqo.ui.components.toast.rememberAppToastState
import com.joseleandro.scaniqo.ui.documentCommon.components.DocumentStatus
import com.joseleandro.scaniqo.ui.documentEditor.components.DocumentToolsMessages
import com.joseleandro.scaniqo.ui.documentEditor.event.DocumentToolsEvent
import com.joseleandro.scaniqo.ui.documentEditor.state.DocumentToolsUiState
import com.joseleandro.scaniqo.ui.theme.ScaniqoTheme

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
    onDone: () -> Unit = onBack,
    initialAdjustmentMode: SignatureAdjustmentMode = SignatureAdjustmentMode.MOVE,
) {
    val sameSession = state.document?.id == documentId
    val workspace = state.workspace?.takeIf { sameSession }
    val page = workspace?.pages?.getOrNull(state.selectedPageIndex)
    val draft = state.signatureDraft?.takeIf { sameSession && state.signaturePageId == page?.id }
    var showCrop by rememberSaveable(draft?.id) { mutableStateOf(false) }
    var showControls by rememberSaveable(documentId) { mutableStateOf(false) }
    var showReplacementSheet by rememberSaveable(draft?.id) { mutableStateOf(false) }
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
    val visiblePageId = page?.id
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
                    actions = {
                        if (draft != null && !state.signatureAwaitingPlacement) {
                            DocumentSignatureActionMenu(
                                enabled = state.canEdit && !state.isRendering,
                                onReplace = { closeControls(); showReplacementSheet = true },
                                onDuplicate = { closeControls(); onEvent(DocumentToolsEvent.DuplicateSignatureDraft) },
                                onDelete = { closeControls(); onEvent(DocumentToolsEvent.DeleteSignatureDraft) },
                                onCancel = { closeControls(); onEvent(DocumentToolsEvent.CancelSignature) },
                            )
                        }
                        TextButton(onClick = onDone, enabled = state.canEdit && !state.signatureAwaitingPlacement) {
                            Text(stringResource(R.string.signature_finish_tool))
                        }
                    },
                )
            },
            snackbarHost = { AppToastHost(toast) },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
                val pageModifier = Modifier.weight(2f).fillMaxWidth()
                pageStateHolder.SaveableStateProvider("${workspace.workspaceId}:${controlsPage.id}") {
                    DocumentSignaturePage(
                        state = state, workspace = workspace, page = controlsPage, draft = draft,
                        adjustmentMode = adjustmentMode,
                        adjustmentActive = adjustmentActive && state.canEdit && !showCrop,
                        onEvent = { event ->
                            if (event is DocumentToolsEvent.EditSignature) closeControls()
                            onEvent(event)
                        },
                        modifier = pageModifier,
                    )
                }
                DocumentSignatureControls(
                    state = state, page = controlsPage, draft = draft,
                    onEvent = { event ->
                        if (event is DocumentToolsEvent.InsertSignature || event is DocumentToolsEvent.EditSignature) closeControls()
                        onEvent(event)
                    },
                    adjustmentMode = adjustmentMode,
                    onAdjustmentModeChange = { adjustmentMode = it; adjustmentActive = false },
                    onAdjustmentActiveChange = { adjustmentActive = it },
                    onCreateSignature = { closeControls(); onCreateSignature() },
                    onCrop = { closeControls(); showCrop = true },
                    onApply = { closeControls(); onApply() },
                    modifier = Modifier.weight(1f).fillMaxWidth(),
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
                actions = {
                    if (draft != null && !state.signatureAwaitingPlacement) {
                        DocumentSignatureActionMenu(
                            enabled = state.canEdit && !state.isRendering,
                            onReplace = { showReplacementSheet = true },
                            onDuplicate = { onEvent(DocumentToolsEvent.DuplicateSignatureDraft) },
                            onDelete = { onEvent(DocumentToolsEvent.DeleteSignatureDraft) },
                            onCancel = { onEvent(DocumentToolsEvent.CancelSignature) },
                        )
                    }
                    TextButton(onClick = onDone, enabled = state.canEdit && !state.signatureAwaitingPlacement) {
                        Text(stringResource(R.string.signature_finish_tool))
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
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            tonalElevation = 2.dp,
                        ) {
                            Column(
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                if (state.signatureAwaitingPlacement && draft != null) {
                                    Text(stringResource(R.string.signature_place_hint),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(
                                            onClick = { onEvent(DocumentToolsEvent.CancelSignature) },
                                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                                        ) { Text(stringResource(R.string.signature_cancel)) }
                                        Button(
                                            onClick = { onEvent(DocumentToolsEvent.PlaceSignature(.5f, .5f)) },
                                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                                        ) { Text(stringResource(R.string.signature_place_center)) }
                                    }
                                } else {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        OutlinedButton(
                                            onClick = { showControls = true },
                                            enabled = state.canEdit,
                                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                                        ) {
                                            Text(
                                                stringResource(if (draft == null) R.string.signature_library else R.string.preview_controls_show),
                                                maxLines = 2, overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                        if (draft != null) SignatureApplyAction(
                                            isRendering = state.isRendering,
                                            canEdit = state.canEdit,
                                            onApply = onApply,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        }
    }
    if (showReplacementSheet && draft != null && !state.isLocked) {
        DocumentSignatureReplaceSheet(
            signatures = state.signatures,
            onSelect = { id ->
                showReplacementSheet = false
                onEvent(DocumentToolsEvent.ReplaceSignature(id))
            },
            onDismiss = { showReplacementSheet = false },
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


@Composable
private fun SignatureApplyAction(
    isRendering: Boolean,
    canEdit: Boolean,
    onApply: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val renderingLabel = stringResource(R.string.tools_rendering)
    Box(modifier) {
        FilledIconButton(
            onClick = onApply,
            enabled = canEdit && !isRendering,
            modifier = Modifier.size(48.dp).semantics {
                if (isRendering) stateDescription = renderingLabel
            },
        ) {
            Icon(
                painterResource(R.drawable.ic_check_24),
                contentDescription = stringResource(R.string.fill_mark_confirm_element),
            )
        }
        if (isRendering) {
            SignatureApplyingStroke(Modifier.align(Alignment.TopEnd).padding(top = 6.dp, end = 6.dp))
        }
    }
}

@Composable
private fun SignatureApplyingStroke(modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    val transition = rememberInfiniteTransition(label = "Signature preview")
    val reveal by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Restart,
        ),
        label = "Signature stroke",
    )
    Canvas(modifier.size(width = 24.dp, height = 18.dp).clearAndSetSemantics {}) {
        val stroke = Path().apply {
            moveTo(size.width * .03f, size.height * .72f)
            cubicTo(
                size.width * .22f, size.height * .15f,
                size.width * .23f, size.height * .95f,
                size.width * .48f, size.height * .48f,
            )
            cubicTo(
                size.width * .65f, size.height * .25f,
                size.width * .67f, size.height * .82f,
                size.width * .96f, size.height * .3f,
            )
        }
        val strokeStyle = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
        drawPath(stroke, color.copy(alpha = .4f), style = strokeStyle)
        clipRect(right = size.width * reveal) {
            drawPath(stroke, color, style = strokeStyle)
        }
    }
}

@Preview(name = "Assinatura — atualizando prévia", locale = "pt-rBR", showBackground = true)
@Preview(name = "Assinatura — fonte ampliada", widthDp = 320, fontScale = 1.5f, locale = "pt-rBR", showBackground = true)
@Composable
private fun SignatureApplyingStrokePreview() {
    ScaniqoTheme {
        SignatureApplyAction(
            isRendering = true,
            canEdit = true,
            onApply = {},
            modifier = Modifier.requiredWidth(160.dp),
        )
    }
}
