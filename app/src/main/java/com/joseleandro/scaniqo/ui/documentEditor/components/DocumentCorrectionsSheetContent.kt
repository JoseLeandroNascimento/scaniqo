package com.joseleandro.scaniqo.ui.documentEditor.components

import android.content.res.Configuration
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
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
import com.joseleandro.scaniqo.ui.documentEditor.state.DocumentBackgroundFeedback
import com.joseleandro.scaniqo.ui.documentEditor.state.DocumentBackgroundSuggestionState
import com.joseleandro.scaniqo.ui.documentEditor.state.DocumentToolPreviewState
import com.joseleandro.scaniqo.ui.documentEditor.state.DocumentToolsUiState
import com.joseleandro.scaniqo.ui.documentEditor.util.PdfPageLayoutSaver
import com.joseleandro.scaniqo.ui.documentEditor.util.DocumentCaptureCoordinates
import com.joseleandro.scaniqo.ui.theme.ScaniqoTheme
import kotlin.math.roundToInt


/** O rascunho sobrevive à rotação; somente Aplicar modifica as páginas escolhidas. */
@Composable
internal fun DocumentCorrectionsSheetContent(
    state: DocumentToolsUiState,
    onEvent: (DocumentToolsEvent) -> Unit,
    modifier: Modifier = Modifier,
    onDraftChanged: (Boolean) -> Unit = {},
    initialTool: CorrectionEditorTool = CorrectionEditorTool.CROP,
) {
    val workspace = state.workspace ?: return
    val initialPage = workspace.pages.getOrNull(state.selectedPageIndex) ?: return
    val anchorPageId = rememberSaveable(workspace.workspaceId) { initialPage.id }
    var draft by rememberSaveable(workspace.workspaceId, stateSaver = PdfPageLayoutSaver) {
        mutableStateOf(initialPage.layout)
    }
    var tool by rememberSaveable(workspace.workspaceId) { mutableStateOf(initialTool) }
    var editingSelection by rememberSaveable(workspace.workspaceId) { mutableStateOf(true) }
    var retry by remember { mutableIntStateOf(0) }
    val page = workspace.pages.firstOrNull { it.id == anchorPageId } ?: initialPage
    val targets = setOf(page.id)
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

    fun correctedLayout(original: PdfPageLayout): PdfPageLayout = original.copy(
        crop = draft.crop,
        backgroundMask = draft.backgroundMask,
        perspective = if ((tool == CorrectionEditorTool.CROP && sourceReady) || draft.crop != null) null else draft.perspective,
        straightenDegrees = draft.straightenDegrees,
        curvature = draft.curvature,
        rasterizeText = draft.rasterizeText || (original.rasterizeText &&
            original.imageFilter != PdfImageFilter.ORIGINAL && original.filterIntensity > 0f),
    )
    val previewLayout = correctedLayout(page.layout)
    val preserveFilterRasterization = page.layout.rasterizeText &&
        page.layout.imageFilter != PdfImageFilter.ORIGINAL && page.layout.filterIntensity > 0f
    val isDirty = workspace.pages.any { it.id in targets && correctedLayout(it.layout) != it.layout }
    LaunchedEffect(isDirty) { currentDraftChanged(isDirty) }
    LaunchedEffect(page.id, usesCapture, state.canEdit, state.tool) {
        if (usesCapture && state.canEdit && state.tool == DocumentTool.CORRECTIONS) {
            currentEvent(DocumentToolsEvent.LoadCorrectionSource(page.id))
        }
    }
    LaunchedEffect(page.id, previewLayout, retry, canEdit, usesCapture, sourceReady, state.tool) {
        if (canEdit && state.tool == DocumentTool.CORRECTIONS && (!usesCapture || sourceReady)) {
            currentEvent(DocumentToolsEvent.PreviewCorrections(page.id, previewLayout, captureOnly = usesCapture))
        }
    }
    LaunchedEffect(tool, page.id, canEdit, sourceReady, state.tool) {
        if (tool == CorrectionEditorTool.BACKGROUND && canEditCapture &&
            state.tool == DocumentTool.CORRECTIONS && !inspecting
        ) {
            backgroundSuggestion.beginAutomatically(page.id, hasMask = draft.backgroundMask != null)?.let { request ->
                editingSelection = false
                currentEvent(DocumentToolsEvent.AnalyzeCorrectionPage(page.id, request.id))
            }
        }
    }
    LaunchedEffect(
        page.id, state.isLocked, state.isAnalyzing,
        state.correctionAnalysisRequestId, backgroundSuggestion.pending?.id,
    ) {
        val request = backgroundSuggestion.pending ?: return@LaunchedEffect
        if (state.isLocked || request.pageId != page.id) {
            currentEvent(DocumentToolsEvent.CancelAnalysis)
            backgroundSuggestion.cancel()
        } else {
            backgroundSuggestion.consume(state, page.id)?.let { bounds ->
                draft = draft.copy(backgroundMask = bounds)
                editingSelection = false
            }
            if (backgroundSuggestion.pending == null && backgroundSuggestion.feedback != DocumentBackgroundFeedback.SUGGESTED) {
                editingSelection = true
            }
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
    fun updateDraft(updated: PdfPageLayout) {
        backgroundSuggestion.clearFeedback()
        draft = updated
    }
    fun selectTool(selected: CorrectionEditorTool) {
        tool = selected
        editingSelection = selected != CorrectionEditorTool.BACKGROUND
    }

    val header: @Composable () -> Unit = {
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                stringResource(tool.labelResource()),
                modifier = Modifier.heightIn(min = 48.dp).wrapContentHeight(Alignment.CenterVertically),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                stringResource(R.string.interactive_preview_page, workspace.pages.indexOf(page) + 1),
                modifier = Modifier.heightIn(min = 48.dp).wrapContentHeight(Alignment.CenterVertically),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    val selectionControls: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SingleChoiceSegmentedButtonRow(Modifier.weight(1f)) {
                    listOf(true, false).forEachIndexed { index, manual ->
                        SegmentedButton(
                            selected = editingSelection == manual,
                            onClick = { editingSelection = manual },
                            enabled = canEditCapture,
                            icon = {},
                            shape = SegmentedButtonDefaults.itemShape(index, 2),
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) {
                            Text(
                                stringResource(
                                    if (!manual) R.string.correction_editor_result
                                    else if (tool == CorrectionEditorTool.BACKGROUND) R.string.background_editor_adjust
                                    else R.string.crop_editor_manual,
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
            if (tool == CorrectionEditorTool.BACKGROUND) {
                BackgroundRemovalControls(
                    enabled = canEditCapture,
                    detecting = backgroundSuggestion.pending != null,
                    feedback = backgroundSuggestion.feedback,
                    onDetect = {
                        backgroundSuggestion.begin(page.id)?.let { request ->
                            editingSelection = false
                            onEvent(DocumentToolsEvent.AnalyzeCorrectionPage(page.id, request.id))
                        }
                    },
                    onCancel = {
                        backgroundSuggestion.cancel()
                        onEvent(DocumentToolsEvent.CancelAnalysis)
                    },
                )
            }
        }
    }
    val adjustments: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            when (tool) {
                CorrectionEditorTool.CROP, CorrectionEditorTool.BACKGROUND -> selectionControls()
                CorrectionEditorTool.STRAIGHTEN -> CorrectionValueControls(
                    label = stringResource(R.string.correction_editor_angle),
                    value = draft.straightenDegrees,
                    valueText = stringResource(R.string.quality_angle_value, draft.straightenDegrees),
                    range = -15f..15f,
                    enabled = canEdit,
                    rasterizeText = draft.rasterizeText,
                    rasterizationRequired = preserveFilterRasterization,
                    onRasterize = { updateDraft(draft.copy(rasterizeText = it)) },
                    onChange = { updateDraft(draft.copy(straightenDegrees = (it * 10).roundToInt() / 10f)) },
                )
                CorrectionEditorTool.CURVATURE -> CorrectionValueControls(
                    label = stringResource(R.string.correction_editor_curve),
                    value = draft.curvature,
                    valueText = stringResource(R.string.quality_curve_value, (draft.curvature * 100).roundToInt()),
                    range = -1f..1f,
                    enabled = canEdit,
                    rasterizeText = draft.rasterizeText,
                    rasterizationRequired = preserveFilterRasterization,
                    onRasterize = { updateDraft(draft.copy(rasterizeText = it)) },
                    onChange = { updateDraft(draft.copy(curvature = it)) },
                )
            }
            if (source?.preservesNativeContent == true &&
                (tool == CorrectionEditorTool.BACKGROUND || draft.backgroundMask != null)
            ) {
                CaptureConversionControl(
                    checked = previewLayout.rasterizeText,
                    enabled = canEdit && !preserveFilterRasterization,
                    onCheckedChange = { updateDraft(draft.copy(rasterizeText = it)) },
                )
            }
        }
    }
    val toolButtons: @Composable ColumnScope.() -> Unit = {
        CorrectionEditorTool.entries.forEach { item ->
            DocumentPreviewToolButton(
                label = stringResource(item.labelResource()),
                iconRes = item.iconResource(),
                selected = tool == item,
                enabled = canEdit,
                onClick = { selectTool(item) },
            )
        }
    }
    val canvas: @Composable BoxScope.() -> Unit = {
        when {
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
                onChange = { updateDraft(draft.copy(
                    crop = DocumentCaptureCoordinates.pageCrop(it, imageBounds), perspective = null,
                )) },
                modifier = Modifier.fillMaxSize(),
                sourceState = sourceState,
                onRetry = { onEvent(DocumentToolsEvent.LoadCorrectionSource(page.id, force = true)) },
            )
            tool == CorrectionEditorTool.BACKGROUND && editingSelection -> DocumentBackgroundMaskEditor(
                page = page,
                pageNumber = workspace.pages.indexOf(page) + 1,
                perspective = DocumentCaptureCoordinates.localMask(draft.backgroundMask, imageBounds),
                enabled = canEditCapture,
                onChange = { updateDraft(draft.copy(
                    backgroundMask = DocumentCaptureCoordinates.pageMask(it, imageBounds),
                )) },
                modifier = Modifier.fillMaxSize(),
                sourceState = sourceState,
                onRetry = { onEvent(DocumentToolsEvent.LoadCorrectionSource(page.id, force = true)) },
            )
            usesCapture -> DocumentCaptureResultPreview(
                pageId = page.id,
                state = state.correctionCapturePreview,
                enabled = canEditCapture,
                onRetry = { retry++ },
                modifier = Modifier.fillMaxSize().padding(12.dp),
            )
            else -> DocumentCorrectionsPreview(
                state = state,
                page = page,
                onRetry = { retry++ },
                pageNumber = workspace.pages.indexOf(page) + 1,
                reserveHandleSpace = tool == CorrectionEditorTool.STRAIGHTEN || tool == CorrectionEditorTool.CURVATURE,
                liveLayout = previewLayout,
                modifier = Modifier.fillMaxSize(),
            ) {
                if (tool == CorrectionEditorTool.STRAIGHTEN || tool == CorrectionEditorTool.CURVATURE) {
                    val angle = tool == CorrectionEditorTool.STRAIGHTEN
                    CorrectionHandlesOverlay(
                        value = if (angle) draft.straightenDegrees else draft.curvature,
                        enabled = canEdit,
                        curvature = !angle,
                        onChange = {
                            updateDraft(if (angle) draft.copy(straightenDegrees = (it * 10).roundToInt() / 10f)
                            else draft.copy(curvature = it))
                        },
                    )
                }
            }
        }
    }
    val actions: @Composable () -> Unit = {
        DocumentToolActions(
            compact = true,
            enabled = canEdit && (!usesCapture || canEditCapture),
            applyEnabled = source?.preservesNativeContent != true ||
                draft.backgroundMask == null || previewLayout.rasterizeText,
            applyLabel = pluralStringResource(R.plurals.document_scope_apply_count, targets.size, targets.size),
            onReset = {
                updateDraft(when (tool) {
                    CorrectionEditorTool.CROP -> draft.copy(crop = null, perspective = null)
                    CorrectionEditorTool.BACKGROUND -> draft.copy(backgroundMask = null)
                    CorrectionEditorTool.STRAIGHTEN -> draft.copy(straightenDegrees = 0f)
                    CorrectionEditorTool.CURVATURE -> draft.copy(curvature = 0f)
                })
            },
            onApply = {
                onEvent(DocumentToolsEvent.SelectPage(page.id))
                onEvent(DocumentToolsEvent.ApplyCorrections(
                    allPages = false,
                    straightenDegrees = draft.straightenDegrees,
                    perspective = previewLayout.perspective,
                    crop = draft.crop,
                    backgroundMask = draft.backgroundMask,
                    curvature = draft.curvature,
                    rasterizeText = draft.rasterizeText,
                    pageIds = targets,
                ))
            },
        )
    }
    DocumentToolScreenLayout(
        modifier = modifier,
        header = header,
        preview = canvas,
        tools = toolButtons,
        controls = adjustments,
        actions = actions,
    )
}

@Composable
private fun CaptureConversionControl(
    checked: Boolean,
    enabled: Boolean,
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
                stringResource(R.string.capture_editor_convert_hint),
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
    feedback: DocumentBackgroundFeedback?,
    onDetect: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (detecting) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
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
            Button(onClick = onDetect, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(R.string.background_editor_retry))
            }
        }
        Text(
            stringResource(when (feedback) {
                DocumentBackgroundFeedback.SUGGESTED -> R.string.background_editor_suggested
                DocumentBackgroundFeedback.NO_EDGES -> R.string.background_editor_no_edges
                DocumentBackgroundFeedback.FAILED -> R.string.background_editor_failed
                null -> R.string.background_editor_hint
            }),
            modifier = Modifier.padding(horizontal = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CorrectionValueControls(
    label: String,
    value: Float,
    valueText: String,
    range: ClosedFloatingPointRange<Float>,
    rasterizeText: Boolean,
    rasterizationRequired: Boolean,
    onRasterize: (Boolean) -> Unit,
    enabled: Boolean,
    onChange: (Float) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.widthIn(max = 100.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(valueText, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = value.coerceIn(range),
            onValueChange = onChange,
            valueRange = range,
            enabled = enabled,
            modifier = Modifier.weight(1f).heightIn(min = 48.dp).semantics { contentDescription = label },
        )
        CorrectionOptionsMenu(rasterizeText, enabled, rasterizationRequired, onRasterize)
    }
}

@Composable
private fun CorrectionOptionsMenu(
    rasterizeText: Boolean,
    enabled: Boolean,
    rasterizationRequired: Boolean,
    onRasterize: (Boolean) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }, enabled = enabled, modifier = Modifier.size(48.dp)) {
            Icon(
                painterResource(R.drawable.more_vert_24px),
                stringResource(R.string.correction_editor_options),
                Modifier.size(22.dp),
            )
        }
        DropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.quality_native_title))
                        Text(
                            stringResource(
                                if (rasterizationRequired) R.string.correction_editor_native_required
                                else R.string.correction_editor_native_hint,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                trailingIcon = {
                    Checkbox(rasterizeText || rasterizationRequired, onCheckedChange = null, enabled = !rasterizationRequired)
                },
                enabled = !rasterizationRequired,
                onClick = { onRasterize(!rasterizeText); expanded = false },
            )
        }
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
