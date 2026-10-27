package com.joseleandro.scaniqo.ui.documentEditor.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joseleandro.scaniqo.R
import com.joseleandro.scaniqo.domain.model.PdfCropBounds
import com.joseleandro.scaniqo.domain.model.PdfImageFilter
import com.joseleandro.scaniqo.domain.model.PdfPageLayout
import com.joseleandro.scaniqo.domain.model.PdfPagePreview
import com.joseleandro.scaniqo.domain.model.PdfPageSpec
import com.joseleandro.scaniqo.domain.model.PdfPerspective
import com.joseleandro.scaniqo.domain.model.PdfPoint
import com.joseleandro.scaniqo.domain.model.PdfWorkspace
import com.joseleandro.scaniqo.ui.documentEditor.event.DocumentToolsEvent
import com.joseleandro.scaniqo.ui.documentEditor.model.CorrectionEditorTool
import com.joseleandro.scaniqo.ui.documentEditor.model.DocumentTool
import com.joseleandro.scaniqo.ui.documentEditor.state.CorrectionDraftSession
import com.joseleandro.scaniqo.ui.documentEditor.state.DocumentBackgroundFeedback
import com.joseleandro.scaniqo.ui.documentEditor.state.DocumentBackgroundSuggestionState
import com.joseleandro.scaniqo.ui.documentEditor.state.DocumentToolPreviewState
import com.joseleandro.scaniqo.ui.documentEditor.state.DocumentToolsUiState
import com.joseleandro.scaniqo.ui.documentEditor.util.DocumentCaptureCoordinates
import com.joseleandro.scaniqo.ui.documentEditor.util.withCorrectionAdjustmentsFrom
import com.joseleandro.scaniqo.ui.theme.ScaniqoTheme
import kotlin.math.roundToInt

/** A página âncora e a receita inicial definem a sessão local de correção. */
@Composable
internal fun DocumentCorrectionsSheetContent(
    state: DocumentToolsUiState,
    onEvent: (DocumentToolsEvent) -> Unit,
    modifier: Modifier = Modifier,
    onDraftChanged: (Boolean) -> Unit = {},
    initialTool: CorrectionEditorTool = CorrectionEditorTool.CROP,
    registerPrimaryAction: (((() -> Unit)?) -> Unit) = {},
) {
    val workspace = state.workspace ?: return
    val initialPage = workspace.pages.getOrNull(state.selectedPageIndex) ?: return
    val anchorPageId = rememberSaveable(workspace.workspaceId) { initialPage.id }
    val page = workspace.pages.firstOrNull { it.id == anchorPageId } ?: initialPage
    val session = rememberSaveable(workspace.workspaceId, anchorPageId, saver = CorrectionDraftSession.Saver) {
        CorrectionDraftSession(page.layout)
    }
    val draft = session.draft
    var tool by rememberSaveable(workspace.workspaceId) { mutableStateOf(initialTool) }
    var editingSelection by rememberSaveable(workspace.workspaceId) {
        mutableStateOf(initialTool != CorrectionEditorTool.BACKGROUND)
    }
    var showingBefore by rememberSaveable(workspace.workspaceId) { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    val currentEvent by rememberUpdatedState(onEvent)
    val currentDraftChanged by rememberUpdatedState(onDraftChanged)
    val backgroundSuggestion = rememberSaveable(
        workspace.workspaceId, saver = DocumentBackgroundSuggestionState.Saver,
    ) { DocumentBackgroundSuggestionState() }
    val inspecting = LocalInspectionMode.current
    val usesCapture = tool == CorrectionEditorTool.CROP || tool == CorrectionEditorTool.BACKGROUND
    val sourceState = state.correctionSource
    val source = sourceState.source.takeIf {
        sourceState.pageId == page.id && !sourceState.isLoading && !sourceState.error && !sourceState.unsupported
    }
    val sourceReady = source != null
    val imageBounds = source?.bounds ?: PdfCropBounds()
    val canEdit = state.canEdit && backgroundSuggestion.pending == null
    val canEditCapture = canEdit && (sourceReady || inspecting)
    val targets = setOf(page.id)
    val preserveFilterRasterization = page.layout.rasterizeText &&
        page.layout.imageFilter != PdfImageFilter.ORIGINAL && page.layout.filterIntensity > 0f
    fun correctedLayout(original: PdfPageLayout): PdfPageLayout = original.withCorrectionAdjustmentsFrom(session.draft)
    val previewLayout = correctedLayout(page.layout)
    val isDirty = previewLayout != page.layout
    val baselinePreview = state.pagePreviews[page.id]?.preview
    val canCompare = sourceReady || baselinePreview != null
    val applyEnabled = canEdit && isDirty && (!usesCapture || canEditCapture) &&
        (source?.preservesNativeContent != true || draft.backgroundMask == null || previewLayout.rasterizeText)

    fun updateDraft(updated: PdfPageLayout) {
        showingBefore = false
        backgroundSuggestion.clearFeedback()
        session.edit(updated)
    }
    fun changeDraft(updated: PdfPageLayout) {
        showingBefore = false
        backgroundSuggestion.clearFeedback()
        session.changeGesture(updated)
    }
    fun selectTool(selected: CorrectionEditorTool) {
        session.finishGesture()
        showingBefore = false
        tool = selected
        editingSelection = selected != CorrectionEditorTool.BACKGROUND
    }
    fun apply() {
        if (!applyEnabled) return
        session.finishGesture()
        onEvent(DocumentToolsEvent.SelectPage(page.id))
        onEvent(DocumentToolsEvent.ApplyCorrections(
            allPages = false,
            straightenDegrees = session.draft.straightenDegrees,
            perspective = correctedLayout(page.layout).perspective,
            crop = session.draft.crop,
            backgroundMask = session.draft.backgroundMask,
            curvature = session.draft.curvature,
            rasterizeText = session.draft.rasterizeText,
            pageIds = targets,
        ))
    }
    RegisterDocumentToolPrimaryAction(applyEnabled, ::apply, registerPrimaryAction)
    LaunchedEffect(isDirty) { currentDraftChanged(isDirty) }
    LaunchedEffect(page.id, state.canEdit, state.tool) {
        if (state.canEdit && state.tool == DocumentTool.CORRECTIONS) {
            currentEvent(DocumentToolsEvent.LoadCorrectionSource(page.id))
        }
    }
    LaunchedEffect(page.id, previewLayout, retry, canEdit, usesCapture, sourceReady, state.tool) {
        if (canEdit && state.tool == DocumentTool.CORRECTIONS && (!usesCapture || sourceReady)) {
            currentEvent(DocumentToolsEvent.PreviewCorrections(page.id, previewLayout, captureOnly = usesCapture))
        }
    }
    LaunchedEffect(
        page.id, state.isLocked, state.isAnalyzing,
        state.correctionAnalysisRequestId, backgroundSuggestion.pending?.id, session.revision,
    ) {
        val request = backgroundSuggestion.pending ?: return@LaunchedEffect
        if (state.isLocked || request.pageId != page.id) {
            currentEvent(DocumentToolsEvent.CancelAnalysis)
            backgroundSuggestion.cancel()
        } else {
            backgroundSuggestion.consume(state, page.id, session.revision)?.let { bounds ->
                session.edit(session.draft.copy(backgroundMask = bounds))
                editingSelection = false
            }
            if (backgroundSuggestion.pending == null &&
                backgroundSuggestion.feedback != DocumentBackgroundFeedback.SUGGESTED
            ) editingSelection = true
        }
    }
    DisposableEffect(workspace.workspaceId) {
        onDispose {
            if (backgroundSuggestion.pending != null) currentEvent(DocumentToolsEvent.CancelAnalysis)
            backgroundSuggestion.cancel()
            currentEvent(DocumentToolsEvent.ClearCorrectionsPreview)
            currentEvent(DocumentToolsEvent.ClearCorrectionSource)
        }
    }

    val header: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.interactive_preview_page, workspace.pages.indexOf(page) + 1),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            IconButton(
                onClick = { session.undo(); showingBefore = false },
                enabled = canEdit && session.canUndo,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(painterResource(R.drawable.ic_document_undo), stringResource(R.string.correction_v3_undo))
            }
            IconButton(
                onClick = { session.redo(); showingBefore = false },
                enabled = canEdit && session.canRedo,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(painterResource(R.drawable.ic_document_redo), stringResource(R.string.correction_v3_redo))
            }
            if (!usesCapture || !editingSelection) {
                TextButton(
                    onClick = { showingBefore = !showingBefore },
                    enabled = canCompare && backgroundSuggestion.pending == null,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(stringResource(if (showingBefore) R.string.correction_v3_show_after
                        else R.string.correction_v3_show_before))
                }
            }
        }
    }
    val selectionControls: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (tool == CorrectionEditorTool.CROP || editingSelection || draft.backgroundMask != null) Row(
                Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = { editingSelection = !editingSelection; showingBefore = false },
                    enabled = canEditCapture,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                ) {
                    Text(stringResource(if (editingSelection) R.string.correction_v3_view_result
                        else if (tool == CorrectionEditorTool.CROP) R.string.correction_v3_edit_crop
                        else R.string.background_editor_adjust))
                }
                val baselineDiffers = if (tool == CorrectionEditorTool.CROP)
                    draft.crop != session.baseline.crop || draft.perspective != session.baseline.perspective
                    else draft.backgroundMask != session.baseline.backgroundMask
                if (baselineDiffers) TextButton(
                    onClick = {
                        updateDraft(if (tool == CorrectionEditorTool.CROP) draft.copy(
                            crop = session.baseline.crop, perspective = session.baseline.perspective,
                        ) else draft.copy(backgroundMask = session.baseline.backgroundMask))
                        editingSelection = true
                    },
                    enabled = canEditCapture,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(stringResource(if (tool == CorrectionEditorTool.CROP) R.string.correction_v3_restore_crop
                        else R.string.correction_v3_restore_background))
                }
            }
            if (tool == CorrectionEditorTool.BACKGROUND) {
                BackgroundRemovalControls(
                    enabled = canEditCapture,
                    detecting = backgroundSuggestion.pending != null,
                    hasMask = draft.backgroundMask != null,
                    feedback = backgroundSuggestion.feedback,
                    onDetect = {
                        backgroundSuggestion.begin(page.id, session.revision)?.let { request ->
                            editingSelection = false
                            onEvent(DocumentToolsEvent.AnalyzeCorrectionPage(page.id, request.id))
                        }
                    },
                    onCancel = {
                        backgroundSuggestion.cancel()
                        onEvent(DocumentToolsEvent.CancelAnalysis)
                    },
                    onManual = {
                        if (backgroundSuggestion.pending != null) {
                            backgroundSuggestion.cancel()
                            onEvent(DocumentToolsEvent.CancelAnalysis)
                        }
                        editingSelection = true
                        showingBefore = false
                    },
                )
            }
        }
    }
    val adjustments: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (tool) {
                CorrectionEditorTool.CROP, CorrectionEditorTool.BACKGROUND -> selectionControls()
                CorrectionEditorTool.STRAIGHTEN -> {
                    CorrectionNumericControl(
                        label = stringResource(R.string.correction_editor_angle),
                        value = draft.straightenDegrees,
                        curvature = false,
                        enabled = canEdit,
                        onPreviewChange = {
                            changeDraft(session.draft.copy(straightenDegrees = it))
                        },
                        onPreviewFinished = { session.finishGesture() },
                        onCommit = { updateDraft(session.draft.copy(straightenDegrees = it)) },
                    )
                    if (draft.straightenDegrees != 0f) TextButton(
                        onClick = { updateDraft(session.draft.copy(straightenDegrees = 0f)) },
                        enabled = canEdit,
                    ) { Text(stringResource(R.string.correction_v3_zero_tilt)) }
                }
                CorrectionEditorTool.CURVATURE -> {
                    CorrectionNumericControl(
                        label = stringResource(R.string.correction_editor_curve),
                        value = draft.curvature,
                        curvature = true,
                        enabled = canEdit,
                        onPreviewChange = {
                            changeDraft(session.draft.copy(curvature = it))
                        },
                        onPreviewFinished = { session.finishGesture() },
                        onCommit = { updateDraft(session.draft.copy(curvature = it)) },
                    )
                    if (draft.curvature != 0f) TextButton(
                        onClick = { updateDraft(session.draft.copy(curvature = 0f)) },
                        enabled = canEdit,
                    ) { Text(stringResource(R.string.correction_v3_zero_curve)) }
                }
            }
            if (source?.preservesNativeContent == true && tool != CorrectionEditorTool.CROP) {
                CaptureConversionControl(
                    checked = previewLayout.rasterizeText,
                    enabled = canEdit && !preserveFilterRasterization,
                    required = preserveFilterRasterization,
                    showWarning = draft.rasterizeText || (tool == CorrectionEditorTool.BACKGROUND &&
                        draft.backgroundMask != null),
                    onCheckedChange = { updateDraft(session.draft.copy(rasterizeText = it)) },
                )
            }
            if (isDirty) TextButton(
                onClick = { updateDraft(session.baseline); editingSelection = tool != CorrectionEditorTool.BACKGROUND },
                enabled = canEdit,
            ) { Text(stringResource(R.string.correction_v3_discard_all)) }
        }
    }
    val toolButtons: @Composable RowScope.() -> Unit = {
        CorrectionEditorTool.entries.forEach { item ->
            DocumentToolModeButton(
                label = stringResource(item.labelResource()),
                iconRes = item.iconResource(),
                selected = tool == item,
                enabled = canEdit,
                onClick = { selectTool(item) },
            )
        }
    }
    val toolGrid: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            CorrectionEditorTool.entries.chunked(2).forEach { items ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    items.forEach { item ->
                        DocumentToolModeButton(
                            label = stringResource(item.labelResource()),
                            iconRes = item.iconResource(),
                            selected = tool == item,
                            enabled = canEdit,
                            onClick = { selectTool(item) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
    val canvas: @Composable BoxScope.() -> Unit = {
        when {
            showingBefore && canCompare -> {
                DocumentInteractivePagePreview(
                    page = page,
                    preview = baselinePreview,
                    fallback = baselinePreview,
                    isLoading = false,
                    hasError = false,
                    enabled = canEdit,
                    onRetry = { retry++ },
                    modifier = Modifier.fillMaxSize(),
                    pageNumber = workspace.pages.indexOf(page) + 1,
                    livePixels = source?.pixels,
                    liveLayout = session.baseline,
                    workspaceId = workspace.workspaceId,
                    pageCount = workspace.pages.size,
                    showPageNumbers = workspace.showPageNumbers,
                )
                Surface(
                    Modifier.align(Alignment.TopStart).padding(8.dp),
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) { Text(stringResource(R.string.corrections_preview_before), Modifier.padding(6.dp)) }
            }
            usesCapture && !sourceReady && !inspecting -> DocumentCaptureStatus(
                unsupported = sourceState.pageId == page.id && sourceState.unsupported,
                error = sourceState.pageId == page.id && sourceState.error,
                enabled = canEdit,
                onRetry = { onEvent(DocumentToolsEvent.LoadCorrectionSource(page.id, force = true)) },
                modifier = Modifier.fillMaxSize(),
            )
            tool == CorrectionEditorTool.CROP && editingSelection -> DocumentManualCropEditor(
                page = page,
                pageNumber = workspace.pages.indexOf(page) + 1,
                crop = DocumentCaptureCoordinates.localCrop(draft.crop, imageBounds),
                enabled = canEditCapture,
                onChange = { changeDraft(session.draft.copy(
                    crop = DocumentCaptureCoordinates.pageCrop(it, imageBounds), perspective = null,
                )) },
                onGestureEnd = { session.finishGesture() },
                modifier = Modifier.fillMaxSize(),
                sourceState = sourceState,
                onRetry = { onEvent(DocumentToolsEvent.LoadCorrectionSource(page.id, force = true)) },
            )
            tool == CorrectionEditorTool.BACKGROUND && editingSelection -> DocumentBackgroundMaskEditor(
                page = page,
                pageNumber = workspace.pages.indexOf(page) + 1,
                perspective = DocumentCaptureCoordinates.localMask(draft.backgroundMask, imageBounds),
                enabled = canEditCapture,
                onChange = { changeDraft(session.draft.copy(
                    backgroundMask = DocumentCaptureCoordinates.pageMask(it, imageBounds),
                )) },
                onGestureEnd = { session.finishGesture() },
                modifier = Modifier.fillMaxSize(),
                sourceState = sourceState,
                onRetry = { onEvent(DocumentToolsEvent.LoadCorrectionSource(page.id, force = true)) },
            )
            usesCapture -> CorrectionCaptureResult(
                pageId = page.id,
                state = state,
                onRetry = { retry++ },
                modifier = Modifier.fillMaxSize().padding(12.dp),
            )
            else -> DocumentCorrectionsPreview(
                state = state,
                page = page,
                onRetry = { retry++ },
                pageNumber = workspace.pages.indexOf(page) + 1,
                reserveHandleSpace = true,
                liveLayout = previewLayout,
                modifier = Modifier.fillMaxSize(),
            ) {
                if (!showingBefore) CorrectionHandlesOverlay(
                    value = if (tool == CorrectionEditorTool.STRAIGHTEN) draft.straightenDegrees else draft.curvature,
                    enabled = canEdit,
                    curvature = tool == CorrectionEditorTool.CURVATURE,
                    onChange = {
                        changeDraft(if (tool == CorrectionEditorTool.STRAIGHTEN)
                            session.draft.copy(straightenDegrees = (it * 10).roundToInt() / 10f)
                            else session.draft.copy(curvature = (it * 100).roundToInt() / 100f))
                    },
                    onGestureEnd = { session.finishGesture() },
                )
            }
        }
        if (tool == CorrectionEditorTool.BACKGROUND && backgroundSuggestion.pending != null) {
            Surface(
                Modifier.align(Alignment.TopStart).padding(8.dp),
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text(stringResource(R.string.background_editor_detecting),
                        style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
    DocumentToolScreenLayout(
        modifier = modifier,
        previewKey = page.id,
        onGestureStart = { session.beginGesture() },
        onTransformStart = { session.cancelGesture() },
        previewTransformGestures = true,
        gestureHint = stringResource(when (tool) {
            CorrectionEditorTool.CROP -> if (editingSelection) R.string.direct_edit_crop else R.string.correction_editor_result
            CorrectionEditorTool.BACKGROUND -> if (editingSelection) R.string.direct_edit_background else R.string.correction_editor_result
            CorrectionEditorTool.STRAIGHTEN -> R.string.direct_edit_straighten
            CorrectionEditorTool.CURVATURE -> R.string.direct_edit_curve
        }),
        header = header,
        preview = canvas,
        tools = toolButtons,
        toolsGrid = toolGrid,
        controls = adjustments,
        actions = {},
    )
}

@Composable
private fun CaptureConversionControl(
    checked: Boolean,
    enabled: Boolean,
    required: Boolean,
    showWarning: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = onCheckedChange)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Checkbox(checked = checked, enabled = enabled, onCheckedChange = null)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.capture_editor_convert_title), style = MaterialTheme.typography.labelLarge)
            Text(
                stringResource(when {
                    required -> R.string.correction_v3_filter_requires_conversion
                    showWarning -> R.string.correction_v3_convert_warning
                    else -> R.string.capture_editor_convert_hint
                }),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BackgroundRemovalControls(
    enabled: Boolean,
    detecting: Boolean,
    hasMask: Boolean,
    feedback: DocumentBackgroundFeedback?,
    onDetect: () -> Unit,
    onCancel: () -> Unit,
    onManual: () -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (detecting) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.background_editor_detecting),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                )
                TextButton(onClick = onCancel, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.quality_analysis_cancel))
                }
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onDetect, enabled = enabled, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                    Text(stringResource(if (hasMask) R.string.correction_v3_detect_again
                        else R.string.correction_v3_detect))
                }
                if (!hasMask) TextButton(onClick = onManual, enabled = enabled,
                    modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.correction_v3_manual))
                }
            }
        }
        if (feedback != null) Text(
            stringResource(when (feedback) {
                DocumentBackgroundFeedback.SUGGESTED -> R.string.background_editor_suggested
                DocumentBackgroundFeedback.NO_EDGES -> R.string.background_editor_no_edges
                DocumentBackgroundFeedback.FAILED -> R.string.background_editor_failed
            }),
            modifier = Modifier.padding(horizontal = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CorrectionCaptureResult(
    pageId: String,
    state: DocumentToolsUiState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val preview = state.correctionCapturePreview
    val matches = preview.pageId == pageId
    val pixels = preview.pixels.takeIf { matches }
    Box(modifier, contentAlignment = Alignment.Center) {
        if (pixels != null) {
            DocumentCaptureImage(
                pixels = pixels,
                description = stringResource(R.string.capture_editor_result),
                modifier = Modifier.fillMaxSize(),
            )
            if (preview.isRendering) {
                Surface(Modifier.align(Alignment.TopStart).padding(8.dp),
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Text(stringResource(R.string.correction_v3_result_updating), Modifier.padding(8.dp),
                        style = MaterialTheme.typography.labelSmall)
                }
            }
            if (preview.error) TextButton(
                onClick = onRetry,
                modifier = Modifier.align(Alignment.BottomCenter),
            ) { Text(stringResource(R.string.corrections_preview_retry)) }
        } else DocumentCaptureStatus(
            unsupported = matches && preview.unsupported,
            error = matches && preview.error,
            enabled = state.canEdit,
            onRetry = onRetry,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** Também usado pela análise completa da ferramenta Qualidade. */
@Composable
internal fun AnalysisInProgress(onEvent: (DocumentToolsEvent) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LinearProgressIndicator(Modifier.fillMaxWidth())
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.quality_analysis_running),
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = { onEvent(DocumentToolsEvent.CancelAnalysis) }) {
                Text(stringResource(R.string.quality_analysis_cancel))
            }
        }
    }
}

private fun CorrectionEditorTool.labelResource() = when (this) {
    CorrectionEditorTool.CROP -> R.string.correction_editor_perspective
    CorrectionEditorTool.BACKGROUND -> R.string.background_editor_title
    CorrectionEditorTool.STRAIGHTEN -> R.string.correction_editor_straighten
    CorrectionEditorTool.CURVATURE -> R.string.correction_editor_curve
}

private fun CorrectionEditorTool.iconResource() = when (this) {
    CorrectionEditorTool.CROP -> R.drawable.ic_correction_perspective
    CorrectionEditorTool.BACKGROUND -> R.drawable.ic_background_remove
    CorrectionEditorTool.STRAIGHTEN -> R.drawable.ic_correction_straighten
    CorrectionEditorTool.CURVATURE -> R.drawable.ic_correction_curve
}

@Preview(name = "Correções claro", widthDp = 390, heightDp = 760, showBackground = true)
@Preview(name = "Correções escuro", widthDp = 390, heightDp = 760, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Correções fonte ampliada", widthDp = 360, heightDp = 640, fontScale = 1.3f)
@Composable
private fun DocumentCorrectionsSheetPreview() {
    CorrectionEditorPreviewContent(CorrectionEditorTool.STRAIGHTEN)
}

@Preview(name = "Recortar manual", widthDp = 390, heightDp = 760, showBackground = true)
@Preview(name = "Recortar fonte ampliada", widthDp = 360, heightDp = 640, fontScale = 1.3f)
@Preview(name = "Recortar paisagem", widthDp = 850, heightDp = 390)
@Composable
private fun DocumentPerspectivePreview() {
    CorrectionEditorPreviewContent(CorrectionEditorTool.CROP)
}

@Preview(name = "Remover fundo", widthDp = 390, heightDp = 760, showBackground = true)
@Preview(name = "Remover fundo fonte ampliada", widthDp = 360, heightDp = 640, fontScale = 1.3f)
@Preview(name = "Remover fundo paisagem", widthDp = 850, heightDp = 390)
@Composable
private fun DocumentBackgroundRemovalPreview() {
    CorrectionEditorPreviewContent(CorrectionEditorTool.BACKGROUND)
}

@Preview(name = "Curvatura interativa", widthDp = 390, heightDp = 760, showBackground = true)
@Composable
private fun DocumentCurvaturePreview() {
    CorrectionEditorPreviewContent(CorrectionEditorTool.CURVATURE)
}

@Composable
private fun CorrectionEditorPreviewContent(tool: CorrectionEditorTool) {
    ScaniqoTheme {
        Surface {
            DocumentCorrectionsSheetContent(
                state = DocumentToolsUiState(
                    workspace = PdfWorkspace(
                        1, "preview", "preview",
                        (1..8).map {
                            PdfPageSpec("page-$it", "preview", it - 1, PdfPageLayout(
                                crop = PdfCropBounds(.15f, .1f, .85f, .9f),
                                backgroundMask = PdfPerspective(
                                    PdfPoint(.12f, .08f), PdfPoint(.88f, .15f),
                                    PdfPoint(.95f, .91f), PdfPoint(.06f, .95f),
                                ),
                            ))
                        },
                    ),
                    selectedPageId = "page-1",
                    tool = DocumentTool.CORRECTIONS,
                    correctionsPreview = DocumentToolPreviewState(
                        preview = PdfPagePreview("preview", 0),
                        pageId = "page-1",
                    ),
                ),
                onEvent = {},
                modifier = Modifier.padding(16.dp),
                initialTool = tool,
            )
        }
    }
}
