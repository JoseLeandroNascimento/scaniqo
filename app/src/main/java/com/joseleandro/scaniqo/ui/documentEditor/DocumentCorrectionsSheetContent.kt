package com.joseleandro.scaniqo.ui.documentEditor

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joseleandro.scaniqo.R
import com.joseleandro.scaniqo.domain.model.PdfImageFilter
import com.joseleandro.scaniqo.domain.model.PdfPageLayout
import com.joseleandro.scaniqo.domain.model.PdfPagePreview
import com.joseleandro.scaniqo.domain.model.PdfPageSpec
import com.joseleandro.scaniqo.domain.model.PdfPerspective
import com.joseleandro.scaniqo.domain.model.PdfPoint
import com.joseleandro.scaniqo.domain.model.PdfWorkspace
import com.joseleandro.scaniqo.ui.theme.ScaniqoTheme
import kotlin.math.roundToInt

internal enum class CorrectionEditorTool { STRAIGHTEN, PERSPECTIVE, CURVATURE }

/** O rascunho permanece ao navegar; somente Aplicar modifica as páginas escolhidas. */
@Composable
internal fun DocumentCorrectionsSheetContent(
    state: DocumentToolsUiState,
    onEvent: (DocumentToolsEvent) -> Unit,
    initialTool: CorrectionEditorTool = CorrectionEditorTool.STRAIGHTEN,
) {
    val workspace = state.workspace ?: return
    val initialPage = workspace.pages.getOrNull(state.selectedPageIndex) ?: return
    val anchorPageId = remember(workspace.workspaceId) { initialPage.id }
    var previewPageId by remember(workspace.workspaceId) { mutableStateOf(anchorPageId) }
    var draft by remember(workspace.workspaceId) { mutableStateOf(initialPage.layout) }
    var scope by remember(workspace.workspaceId) { mutableStateOf(DocumentPageScope()) }
    var tool by remember { mutableStateOf(initialTool) }
    var editingCorners by remember { mutableStateOf(initialTool == CorrectionEditorTool.PERSPECTIVE) }
    var retry by remember { mutableIntStateOf(0) }
    val targets = scope.targets(workspace.pages, anchorPageId)
    val page = workspace.pages.firstOrNull { it.id == previewPageId && it.id in targets }
        ?: workspace.pages.firstOrNull { it.id in targets } ?: initialPage
    val currentEvent by rememberUpdatedState(onEvent)
    val preserveFilterRasterization = page.layout.rasterizeText &&
        page.layout.imageFilter != PdfImageFilter.ORIGINAL && page.layout.filterIntensity > 0f
    val previewLayout = page.layout.copy(
        straightenDegrees = draft.straightenDegrees,
        perspective = draft.perspective,
        curvature = draft.curvature,
        rasterizeText = draft.rasterizeText || preserveFilterRasterization,
    )
    LaunchedEffect(page.id, previewLayout, retry, state.canEdit) {
        if (state.canEdit) currentEvent(DocumentToolsEvent.PreviewCorrections(page.id, previewLayout))
    }
    DisposableEffect(workspace.workspaceId) {
        onDispose { currentEvent(DocumentToolsEvent.ClearCorrectionsPreview) }
    }
    fun updateDraft(updated: PdfPageLayout) {
        draft = updated
    }
    fun selectTool(selected: CorrectionEditorTool) {
        tool = selected
        if (selected == CorrectionEditorTool.PERSPECTIVE) editingCorners = true
    }

    BoxWithConstraints(Modifier.fillMaxWidth().heightIn(max = 720.dp)) {
        val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
        // O controle ativo cabe junto da folha; a rolagem permanece para janelas muito baixas.
        val extraHeaderHeight = if (targets.size > 1 && maxWidth < 340.dp * fontScale) 48.dp else 0.dp
        val stageHeight = (maxHeight - (108.dp + 24.dp * fontScale) - extraHeaderHeight)
            .coerceIn(320.dp, 560.dp)
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FlowRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (targets.size > 1) {
                        DocumentToolTargetNavigation(
                            pages = workspace.pages,
                            targetIds = targets,
                            previewPageId = page.id,
                            onPageChange = { previewPageId = it },
                            enabled = state.canEdit,
                        )
                    }
                    DocumentToolScopeMenu(
                        pages = workspace.pages,
                        currentPageId = anchorPageId,
                        scope = scope,
                        onScopeChange = {
                            scope = it
                            val nextTargets = it.targets(workspace.pages, anchorPageId)
                            if (previewPageId !in nextTargets) previewPageId = nextTargets.firstOrNull() ?: anchorPageId
                        },
                        enabled = state.canEdit,
                    )
                }
                DocumentInteractivePreviewStage(
                    modifier = Modifier.fillMaxWidth().height(stageHeight),
                    tools = {
                        CorrectionEditorTool.entries.forEach { item ->
                            DocumentPreviewToolButton(
                                label = stringResource(item.labelResource()),
                                iconRes = item.iconResource(),
                                selected = tool == item,
                                enabled = !state.isBusy,
                                onClick = { selectTool(item) },
                            )
                        }
                    },
                    controls = {
                        when (tool) {
                            CorrectionEditorTool.STRAIGHTEN -> CorrectionValueControls(
                                label = stringResource(R.string.correction_editor_angle),
                                value = draft.straightenDegrees,
                                valueText = stringResource(R.string.quality_angle_value, draft.straightenDegrees),
                                range = -15f..15f,
                                enabled = state.canEdit,
                                rasterizeText = draft.rasterizeText,
                                rasterizationRequired = preserveFilterRasterization,
                                onRasterize = { updateDraft(draft.copy(rasterizeText = it)) },
                                onChange = { updateDraft(draft.copy(straightenDegrees = (it * 10).roundToInt() / 10f)) },
                            )
                            CorrectionEditorTool.PERSPECTIVE -> {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    SingleChoiceSegmentedButtonRow(Modifier.weight(1f)) {
                                        listOf(true, false).forEachIndexed { index, corners ->
                                            SegmentedButton(
                                                selected = editingCorners == corners,
                                                onClick = { editingCorners = corners },
                                                enabled = state.canEdit,
                                                icon = {},
                                                shape = SegmentedButtonDefaults.itemShape(index, 2),
                                                modifier = Modifier.heightIn(min = 48.dp),
                                            ) {
                                                Text(
                                                    stringResource(if (corners) R.string.correction_editor_corners else R.string.correction_editor_result),
                                                    style = MaterialTheme.typography.labelMedium,
                                                    textAlign = TextAlign.Center,
                                                )
                                            }
                                        }
                                    }
                                    CorrectionOptionsMenu(
                                        rasterizeText = draft.rasterizeText,
                                        enabled = state.canEdit,
                                        rasterizationRequired = preserveFilterRasterization,
                                        onRasterize = { updateDraft(draft.copy(rasterizeText = it)) },
                                    )
                                }
                            }
                            CorrectionEditorTool.CURVATURE -> CorrectionValueControls(
                                label = stringResource(R.string.correction_editor_curve),
                                value = draft.curvature,
                                valueText = stringResource(R.string.quality_curve_value, (draft.curvature * 100).roundToInt()),
                                range = -1f..1f,
                                enabled = state.canEdit,
                                rasterizeText = draft.rasterizeText,
                                rasterizationRequired = preserveFilterRasterization,
                                onRasterize = { updateDraft(draft.copy(rasterizeText = it)) },
                                onChange = { updateDraft(draft.copy(curvature = it)) },
                            )
                        }
                    },
                ) {
                    if (tool == CorrectionEditorTool.PERSPECTIVE && editingCorners) {
                        DocumentPerspectiveEditor(
                            page = page,
                            pageNumber = workspace.pages.indexOf(page) + 1,
                            perspective = draft.perspective ?: PdfPerspective(),
                            enabled = state.canEdit,
                            onChange = { updateDraft(draft.copy(perspective = it.takeUnless { value -> value == PdfPerspective() })) },
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        DocumentCorrectionsPreview(
                            state = state,
                            page = page,
                            onRetry = { retry++ },
                            pageNumber = workspace.pages.indexOf(page) + 1,
                            reserveHandleSpace = tool != CorrectionEditorTool.PERSPECTIVE,
                            liveLayout = previewLayout,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            if (tool != CorrectionEditorTool.PERSPECTIVE) {
                                val angle = tool == CorrectionEditorTool.STRAIGHTEN
                                CorrectionHandlesOverlay(
                                    value = if (angle) draft.straightenDegrees else draft.curvature,
                                    enabled = state.canEdit,
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
            }
            DocumentToolActions(
                compact = true,
                enabled = state.canEdit && targets.isNotEmpty(),
                applyLabel = pluralStringResource(R.plurals.document_scope_apply_count, targets.size, targets.size),
                onReset = {
                    updateDraft(draft.copy(straightenDegrees = 0f, perspective = null, curvature = 0f))
                },
                onApply = {
                    onEvent(DocumentToolsEvent.SelectPage(page.id))
                    onEvent(DocumentToolsEvent.ApplyCorrections(
                        allPages = scope.mode == DocumentPageScopeMode.ALL,
                        straightenDegrees = draft.straightenDegrees,
                        perspective = draft.perspective,
                        curvature = draft.curvature,
                        rasterizeText = draft.rasterizeText,
                        pageIds = targets,
                    ))
                },
            )
        }
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
    CorrectionEditorTool.STRAIGHTEN -> R.string.correction_editor_straighten
    CorrectionEditorTool.PERSPECTIVE -> R.string.correction_editor_perspective
    CorrectionEditorTool.CURVATURE -> R.string.correction_editor_curve
}

private fun CorrectionEditorTool.iconResource() = when (this) {
    CorrectionEditorTool.STRAIGHTEN -> R.drawable.ic_correction_straighten
    CorrectionEditorTool.PERSPECTIVE -> R.drawable.ic_correction_perspective
    CorrectionEditorTool.CURVATURE -> R.drawable.ic_correction_curve
}

@Preview(name = "Correções claro", widthDp = 390, heightDp = 760, showBackground = true)
@Preview(name = "Correções escuro", widthDp = 390, heightDp = 760, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Correções fonte ampliada", widthDp = 360, heightDp = 760, fontScale = 1.3f)
@Composable
private fun DocumentCorrectionsSheetPreview() {
    CorrectionEditorPreviewContent(CorrectionEditorTool.STRAIGHTEN)
}

@Preview(name = "Perspectiva interativa", widthDp = 390, heightDp = 760, showBackground = true)
@Preview(name = "Perspectiva fonte ampliada", widthDp = 360, heightDp = 760, fontScale = 1.3f)
@Composable
private fun DocumentPerspectivePreview() {
    CorrectionEditorPreviewContent(CorrectionEditorTool.PERSPECTIVE)
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
            Column(Modifier.padding(20.dp)) {
                DocumentCorrectionsSheetContent(
                    DocumentToolsUiState(
                        workspace = PdfWorkspace(
                            1, "preview", "preview",
                            (1..8).map {
                                PdfPageSpec("page-$it", "preview", it - 1, PdfPageLayout(
                                    perspective = PdfPerspective(
                                        PdfPoint(.12f, .08f), PdfPoint(.88f, .15f),
                                        PdfPoint(.95f, .91f), PdfPoint(.06f, .95f),
                                    ),
                                ))
                            },
                        ),
                        selectedPageId = "page-1",
                        tool = DocumentTool.CORRECTIONS,
                        correctionsPreview = PdfPagePreview("preview", 0),
                        correctionsPreviewPageId = "page-1",
                    ),
                    {},
                    initialTool = tool,
                )
            }
        }
    }
}
