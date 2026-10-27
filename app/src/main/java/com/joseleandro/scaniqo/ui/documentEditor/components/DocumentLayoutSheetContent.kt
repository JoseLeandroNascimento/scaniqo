package com.joseleandro.scaniqo.ui.documentEditor.components

import android.content.res.Configuration
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.joseleandro.scaniqo.data.pdf.PdfPageGeometry
import com.joseleandro.scaniqo.domain.model.PdfContentPosition
import com.joseleandro.scaniqo.domain.model.effectiveContentPosition
import com.joseleandro.scaniqo.domain.model.PageScaleMode
import com.joseleandro.scaniqo.domain.model.PdfPageLayout
import com.joseleandro.scaniqo.domain.model.PdfPagePreview
import com.joseleandro.scaniqo.domain.model.PdfPageSpec
import com.joseleandro.scaniqo.domain.model.PdfWorkspace
import com.joseleandro.scaniqo.ui.documentEditor.event.DocumentToolsEvent
import com.joseleandro.scaniqo.ui.documentEditor.model.DocumentLayoutControl
import com.joseleandro.scaniqo.ui.documentEditor.model.DocumentPageScope
import com.joseleandro.scaniqo.ui.documentEditor.model.DocumentPageScopeMode
import com.joseleandro.scaniqo.ui.documentEditor.state.DocumentToolPreviewState
import com.joseleandro.scaniqo.ui.documentEditor.state.DocumentToolsUiState
import com.joseleandro.scaniqo.ui.documentEditor.util.DocumentPageScopeSaver
import com.joseleandro.scaniqo.ui.documentEditor.util.PdfPageLayoutSaver
import com.joseleandro.scaniqo.ui.documentEditor.util.documentLayoutRotation
import com.joseleandro.scaniqo.ui.documentEditor.util.withLayoutAdjustmentsFrom
import com.joseleandro.scaniqo.ui.theme.ScaniqoTheme
import kotlin.math.roundToInt

/** A folha mostra o rascunho real; somente Aplicar transfere a geometria aos alvos escolhidos. */
@Composable
internal fun DocumentLayoutSheetContent(
    state: DocumentToolsUiState,
    onEvent: (DocumentToolsEvent) -> Unit,
    modifier: Modifier = Modifier,
    onDraftChanged: (Boolean) -> Unit = {},
    initialControl: DocumentLayoutControl = DocumentLayoutControl.POSITION,
    registerPrimaryAction: (((() -> Unit)?) -> Unit) = {},
) {
    val workspace = state.workspace ?: return
    val initialPage = workspace.pages.getOrNull(state.selectedPageIndex) ?: return
    val anchorPageId = rememberSaveable(workspace.workspaceId) { initialPage.id }
    val baselines = remember(workspace.workspaceId) {
        workspace.pages.associate { it.id to it.layout }
    }
    val anchorBaseline = baselines[anchorPageId] ?: initialPage.layout
    var previewPageId by rememberSaveable(workspace.workspaceId) { mutableStateOf(initialPage.id) }
    var draft by rememberSaveable(workspace.workspaceId, stateSaver = PdfPageLayoutSaver) {
        mutableStateOf(anchorBaseline)
    }
    var history by rememberSaveable(workspace.workspaceId, stateSaver = LayoutDraftHistorySaver) {
        mutableStateOf(LayoutDraftHistory())
    }
    var gestureStart by remember { mutableStateOf<PdfPageLayout?>(null) }
    val gestureStartLayout = remember(workspace.workspaceId) { mutableStateOf(draft) }
    var control by rememberSaveable {
        mutableStateOf(if (initialControl == DocumentLayoutControl.MARGINS) DocumentLayoutControl.SIZE else initialControl)
    }
    var scope by rememberSaveable(workspace.workspaceId, stateSaver = DocumentPageScopeSaver) {
        mutableStateOf(DocumentPageScope())
    }
    var showingBefore by rememberSaveable(workspace.workspaceId) { mutableStateOf(false) }
    var scaleEntry by rememberSaveable(workspace.workspaceId) { mutableStateOf<String?>(null) }
    var scaleError by rememberSaveable(workspace.workspaceId) { mutableStateOf(false) }
    var applyRequested by rememberSaveable(workspace.workspaceId) { mutableStateOf(false) }
    val targets = scope.targets(workspace.pages, anchorPageId)
    val page = workspace.pages.firstOrNull { it.id == previewPageId && it.id in targets }
        ?: workspace.pages.firstOrNull { it.id in targets } ?: initialPage
    val pageBaseline = baselines[page.id] ?: page.layout
    val afterLayout = pageBaseline.withLayoutAdjustmentsFrom(draft)
    val displayLayout = if (showingBefore) pageBaseline else afterLayout
    val currentPreview = state.layoutPreview.preview.takeIf {
        state.layoutPreview.pageId == page.id && state.layoutPreview.showingOriginal == showingBefore
    }
    val livePreview = state.layoutPreview.live.takeIf { it.pageId == page.id }
    val previewError = state.layoutPreview.error || livePreview?.error == true
    val changed = workspace.pages.any { it.id in targets && it.layout.withLayoutAdjustmentsFrom(draft) != it.layout }
    SideEffect { onDraftChanged(changed) }

    fun beginGesture() {
        if (gestureStart == null) gestureStart = draft
    }
    fun endGesture() {
        gestureStart?.let { history = history.record(it, draft) }
        gestureStart = null
    }
    fun changeDraft(next: PdfPageLayout) {
        if (next == draft) return
        showingBefore = false
        if (gestureStart == null) history = history.record(draft, next)
        draft = next
    }
    fun commitScaleEntry(): Boolean {
        val buffer = scaleEntry ?: return true
        val percent = buffer.toIntOrNull()
        if (percent == null || percent !in 25..200) {
            scaleError = true
            return false
        }
        scaleEntry = null
        scaleError = false
        changeDraft(draft.copy(contentScale = percent / 100f))
        return true
    }
    fun applyOnce() {
        if (applyRequested || !state.canEdit || targets.isEmpty() || !commitScaleEntry()) return
        endGesture()
        applyRequested = true
        onEvent(DocumentToolsEvent.SelectPage(page.id))
        onEvent(DocumentToolsEvent.ApplyLayout(
            alignment = draft.alignment,
            scaleMode = draft.scaleMode,
            allPages = scope.mode == DocumentPageScopeMode.ALL,
            pageIds = targets,
            rotationQuarterTurns = draft.rotationQuarterTurns,
            contentScale = draft.contentScale,
            contentPosition = draft.contentPosition,
            edgeToEdge = draft.edgeToEdge,
        ))
    }
    RegisterDocumentToolPrimaryAction(
        enabled = state.canEdit && !state.isBusy && targets.isNotEmpty() && !applyRequested &&
            (changed || scaleEntry?.toIntOrNull()?.let { it in 25..200 && it != (draft.contentScale * 100).roundToInt() } == true),
        onClick = ::applyOnce,
        register = registerPrimaryAction,
    )
    androidx.activity.compose.BackHandler(scaleEntry != null) {
        scaleEntry = null
        scaleError = false
    }
    LaunchedEffect(page.id, displayLayout, showingBefore, state.canEdit) {
        if (state.canEdit) onEvent(DocumentToolsEvent.PreviewLayout(page.id, displayLayout, showOriginal = showingBefore))
    }
    DisposableEffect(Unit) {
        onDispose { onEvent(DocumentToolsEvent.ClearLayoutPreview) }
    }

    DocumentToolScreenLayout(
        modifier = modifier,
        previewKey = page.id,
        onGestureStart = {
            gestureStartLayout.value = draft
            beginGesture()
        },
        onTransformStart = { draft = gestureStartLayout.value },
        previewTransformGestures = control == DocumentLayoutControl.POSITION,
        gestureHint = when (control) {
            DocumentLayoutControl.POSITION -> stringResource(R.string.direct_edit_move)
            DocumentLayoutControl.ROTATION -> stringResource(R.string.direct_edit_rotate)
            DocumentLayoutControl.SIZE -> stringResource(R.string.direct_edit_scale)
            DocumentLayoutControl.MARGINS -> ""
        },
        header = {
            LayoutContextBar(
                workspace = workspace,
                anchorPageId = anchorPageId,
                scope = scope,
                onScopeChange = { scope = it },
                canEdit = state.canEdit,
                canUndo = history.undo.isNotEmpty(),
                canRedo = history.redo.isNotEmpty(),
                showingBefore = showingBefore,
                canDiscard = draft != anchorBaseline,
                onDiscard = {
                    scaleEntry = null
                    scaleError = false
                    changeDraft(anchorBaseline)
                },
                onUndo = {
                    endGesture()
                    history.undo(draft)?.let { (previous, nextHistory) ->
                        draft = previous
                        history = nextHistory
                        showingBefore = false
                    }
                },
                onRedo = {
                    endGesture()
                    history.redo(draft)?.let { (next, nextHistory) ->
                        draft = next
                        history = nextHistory
                        showingBefore = false
                    }
                },
                onCompare = { showingBefore = !showingBefore },
            )
        },
        tools = {
            listOf(DocumentLayoutControl.POSITION, DocumentLayoutControl.SIZE, DocumentLayoutControl.ROTATION)
                .forEach { option ->
                    DocumentToolModeButton(
                        label = stringResource(option.labelResource()),
                        iconRes = option.iconResource(),
                        selected = control == option,
                        enabled = state.canEdit,
                        onClick = {
                            scaleEntry = null
                            scaleError = false
                            control = option
                        },
                    )
                }
        },
        controls = {
            when (control) {
                DocumentLayoutControl.POSITION -> LayoutPositionControls(
                    draft, anchorBaseline, state.canEdit,
                    livePreview?.pixels?.sourceWidth, livePreview?.pixels?.sourceHeight,
                    onChange = ::changeDraft,
                    onSize = { control = DocumentLayoutControl.SIZE },
                )
                DocumentLayoutControl.SIZE, DocumentLayoutControl.MARGINS -> LayoutSizeControls(
                    layout = draft,
                    baseline = anchorBaseline,
                    enabled = state.canEdit,
                    scaleEntry = scaleEntry,
                    scaleError = scaleError,
                    onStartScaleEntry = {
                        scaleEntry = (draft.contentScale * 100).roundToInt().toString()
                        scaleError = false
                    },
                    onScaleEntryChange = { scaleEntry = it; scaleError = false },
                    onCommitScaleEntry = { commitScaleEntry() },
                    onCancelScaleEntry = { scaleEntry = null; scaleError = false },
                    onContinuousStart = ::beginGesture,
                    onContinuousEnd = ::endGesture,
                    onChange = ::changeDraft,
                )
                DocumentLayoutControl.ROTATION -> LayoutRotationControls(
                    draft, anchorBaseline, state.canEdit, ::changeDraft,
                )
            }
        },
        preview = {
            DocumentInteractivePagePreview(
                page = page,
                preview = currentPreview,
                fallback = state.pagePreviews[page.id]?.preview,
                isLoading = state.layoutPreview.isRendering || livePreview?.isRendering == true ||
                    (currentPreview == null && livePreview?.pixels == null && !previewError),
                hasError = previewError,
                onRetry = { onEvent(DocumentToolsEvent.PreviewLayout(page.id, displayLayout, showOriginal = showingBefore)) },
                enabled = state.canEdit,
                pageNumber = workspace.pages.indexOf(page) + 1,
                reserveHandleSpace = true,
                livePixels = livePreview?.pixels,
                liveLayout = displayLayout,
                workspaceId = workspace.workspaceId,
                pageCount = workspace.pages.size,
                showPageNumbers = workspace.showPageNumbers,
                overlay = {
                    if (!showingBefore) DocumentLayoutHandles(
                        page.id, control, draft, state.canEdit,
                        sourceWidth = livePreview?.pixels?.sourceWidth ?: 0f,
                        sourceHeight = livePreview?.pixels?.sourceHeight ?: 0f,
                        onLayoutChange = ::changeDraft,
                        onGestureStart = ::beginGesture,
                        onGestureEnd = ::endGesture,
                    )
                },
            )
            if (currentPreview == null && livePreview?.pixels == null && !previewError) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Box(contentAlignment = Alignment.Center) {
                        androidx.compose.material3.CircularProgressIndicator(Modifier.size(28.dp))
                    }
                }
            }
            Surface(
                    Modifier.align(Alignment.TopStart).padding(12.dp),
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Text(stringResource(if (showingBefore) R.string.layout_v3_before else R.string.layout_v3_after),
                        Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
                }
        },
        navigation = {
            if (targets.size > 1) DocumentToolTargetNavigation(
                pages = workspace.pages,
                targetIds = targets,
                previewPageId = page.id,
                onPageChange = { previewPageId = it },
                enabled = state.canEdit,
            )
        },
        actions = {},
    )
}

@Composable
private fun LayoutContextBar(
    workspace: PdfWorkspace,
    anchorPageId: String,
    scope: DocumentPageScope,
    onScopeChange: (DocumentPageScope) -> Unit,
    canEdit: Boolean,
    canUndo: Boolean,
    canRedo: Boolean,
    showingBefore: Boolean,
    canDiscard: Boolean,
    onDiscard: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onCompare: () -> Unit,
) {
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
        val compact = maxWidth < 360.dp
        Column {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    DocumentToolScopeMenu(
                        pages = workspace.pages,
                        currentPageId = anchorPageId,
                        scope = scope,
                        onScopeChange = onScopeChange,
                        enabled = canEdit,
                    )
                }
                IconButton(onClick = onUndo, enabled = canEdit && canUndo, modifier = Modifier.size(48.dp)) {
                    Icon(painterResource(R.drawable.ic_document_undo), stringResource(R.string.viewer_undo_description))
                }
                IconButton(onClick = onRedo, enabled = canEdit && canRedo, modifier = Modifier.size(48.dp)) {
                    Icon(painterResource(R.drawable.ic_document_redo), stringResource(R.string.viewer_redo_description))
                }
                if (!compact) LayoutCompareButton(showingBefore, onCompare)
                LayoutSessionMenu(enabled = canEdit && canDiscard, onDiscard = onDiscard)
            }
            if (compact) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                LayoutCompareButton(showingBefore, onCompare)
            }
        }
    }
}

@Composable
private fun LayoutSessionMenu(enabled: Boolean, onDiscard: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }, enabled = enabled, modifier = Modifier.size(48.dp)) {
            Icon(painterResource(R.drawable.more_vert_24px), stringResource(R.string.layout_v3_more))
        }
        androidx.compose.material3.DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            androidx.compose.material3.DropdownMenuItem(
                text = { Text(stringResource(R.string.layout_v3_discard_session)) },
                onClick = { expanded = false; onDiscard() },
            )
        }
    }
}

@Composable
private fun LayoutCompareButton(showingBefore: Boolean, onCompare: () -> Unit) {
    androidx.compose.material3.TextButton(onClick = onCompare, modifier = Modifier.heightIn(min = 48.dp)) {
        Text(stringResource(if (showingBefore) R.string.layout_v3_after else R.string.layout_v3_compare))
    }
}

@Composable
private fun LayoutPositionControls(
    layout: PdfPageLayout,
    baseline: PdfPageLayout,
    enabled: Boolean,
    sourceWidth: Float?,
    sourceHeight: Float?,
    onChange: (PdfPageLayout) -> Unit,
    onSize: () -> Unit,
) {
    val current = layout.effectiveContentPosition()
    val positions = listOf(0f, .5f, 1f)
    val horizontalNames = listOf(
        stringResource(R.string.tools_align_left),
        stringResource(R.string.tools_align_center),
        stringResource(R.string.tools_align_right),
    )
    val verticalNames = listOf(
        stringResource(R.string.tools_align_top),
        stringResource(R.string.tools_align_center),
        stringResource(R.string.tools_align_bottom),
    )
    val travel = remember(layout, sourceWidth, sourceHeight) {
        if (sourceWidth != null && sourceHeight != null && sourceWidth > 0f && sourceHeight > 0f)
            PdfPageGeometry.contentTravel(sourceWidth, sourceHeight, layout) else null
    }
    val horizontalBlocked = travel != null && kotlin.math.abs(travel.horizontal) < .01f
    val verticalBlocked = travel != null && kotlin.math.abs(travel.vertical) < .01f
    var showPrecise by rememberSaveable { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.layout_v3_align), style = MaterialTheme.typography.titleSmall)
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            positions.forEachIndexed { row, vertical ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    positions.forEachIndexed { column, horizontal ->
                        val selected = kotlin.math.abs(current.horizontal - horizontal) < .001f &&
                            kotlin.math.abs(current.vertical - vertical) < .001f
                        val description = stringResource(
                            R.string.layout_v3_align_target,
                            verticalNames[row], horizontalNames[column],
                        )
                        val ink by animateColorAsState(
                            if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            animationSpec = tween(160), label = "layout_anchor_ink",
                        )
                        val tileColor by animateColorAsState(
                            if (selected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surface,
                            animationSpec = tween(160), label = "layout_anchor_surface",
                        )
                        Surface(
                            modifier = Modifier.size(48.dp)
                                .selectable(
                                    selected = selected, enabled = enabled,
                                    role = Role.RadioButton,
                                    onClick = {
                                        onChange(layout.copy(contentPosition = PdfContentPosition(horizontal, vertical)))
                                    },
                                ).semantics { contentDescription = description },
                            shape = MaterialTheme.shapes.small,
                            color = tileColor,
                            border = if (selected) BorderStroke(
                                1.dp, MaterialTheme.colorScheme.primary.copy(alpha = .45f),
                            ) else null,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Canvas(Modifier.size(24.dp)) {
                                    drawRect(ink.copy(alpha = .5f), style = Stroke(1.dp.toPx()))
                                    drawCircle(
                                        ink, radius = 3.dp.toPx(),
                                        center = Offset(
                                            size.width * (.2f + horizontal * .6f),
                                            size.height * (.2f + vertical * .6f),
                                        ),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        if (horizontalBlocked || verticalBlocked) {
            Text(
                stringResource(
                    when {
                        horizontalBlocked && verticalBlocked -> R.string.layout_v3_no_both_travel
                        horizontalBlocked -> R.string.layout_v3_no_horizontal_travel
                        else -> R.string.layout_v3_no_vertical_travel
                    }
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            androidx.compose.material3.TextButton(onClick = onSize, enabled = enabled) {
                Text(stringResource(R.string.layout_v3_reduce_size))
            }
        }
        androidx.compose.material3.TextButton(
            onClick = { showPrecise = !showPrecise },
            enabled = enabled,
            modifier = Modifier.heightIn(min = 48.dp),
        ) {
            Text(stringResource(if (showPrecise) R.string.layout_v3_hide_precise else R.string.layout_v3_precise))
        }
        if (showPrecise) {
            val step = .02f
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                listOf(
                    Triple("←", -step to 0f, horizontalNames[0]),
                    Triple("↑", 0f to -step, verticalNames[0]),
                    Triple("↓", 0f to step, verticalNames[2]),
                    Triple("→", step to 0f, horizontalNames[2]),
                ).forEach { (glyph, delta, name) ->
                    val blocked = if (delta.first != 0f) horizontalBlocked else verticalBlocked
                    val nudgeDescription = stringResource(R.string.layout_v3_nudge, name)
                    IconButton(
                        onClick = {
                            onChange(layout.copy(contentPosition = PdfContentPosition(
                                (current.horizontal + delta.first).coerceIn(0f, 1f),
                                (current.vertical + delta.second).coerceIn(0f, 1f),
                            )))
                        },
                        enabled = enabled && !blocked,
                        modifier = Modifier.size(48.dp)
                            .semantics { contentDescription = nudgeDescription },
                    ) { Text(glyph, style = MaterialTheme.typography.titleLarge) }
                }
            }
            androidx.compose.material3.OutlinedButton(
                onClick = { onChange(layout.copy(contentPosition = PdfContentPosition())) },
                enabled = enabled,
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text(stringResource(R.string.layout_v3_center)) }
        }
        LayoutCategoryRestore(
            enabled = enabled,
            onClick = { onChange(restoreLayoutCategory(layout, baseline, DocumentLayoutControl.POSITION)) },
        )
    }
}

@Composable
private fun LayoutSizeControls(
    layout: PdfPageLayout,
    baseline: PdfPageLayout,
    enabled: Boolean,
    scaleEntry: String?,
    scaleError: Boolean,
    onStartScaleEntry: () -> Unit,
    onScaleEntryChange: (String) -> Unit,
    onCommitScaleEntry: () -> Boolean,
    onCancelScaleEntry: () -> Unit,
    onContinuousStart: () -> Unit,
    onContinuousEnd: () -> Unit,
    onChange: (PdfPageLayout) -> Unit,
) {
    val selectedPreset = LayoutSizePreset.selected(layout)
    val scaleFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    LaunchedEffect(scaleEntry != null) {
        if (scaleEntry != null) {
            scaleFocus.requestFocus()
            keyboardController?.show()
        }
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val decreaseScale = stringResource(R.string.layout_v3_decrease_scale)
        val increaseScale = stringResource(R.string.layout_v3_increase_scale)
        val scaleDescription = stringResource(R.string.layout_v3_scale)
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(scaleDescription, style = MaterialTheme.typography.labelLarge)
            androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
            IconButton(
                onClick = {
                    onCancelScaleEntry()
                    onChange(layout.copy(contentScale = ((layout.contentScale * 100).roundToInt() - 1).coerceIn(25, 200) / 100f))
                },
                enabled = enabled && layout.contentScale > .25f,
                modifier = Modifier.size(48.dp).semantics { contentDescription = decreaseScale },
            ) { Text("−", style = MaterialTheme.typography.titleLarge) }
            if (scaleEntry == null) {
                androidx.compose.material3.TextButton(
                    onClick = onStartScaleEntry, enabled = enabled,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.tools_content_size_value, (layout.contentScale * 100).roundToInt()))
                }
            } else {
                androidx.compose.material3.OutlinedTextField(
                    value = scaleEntry,
                    onValueChange = { value ->
                        if (value.length <= 3 && value.all(Char::isDigit)) onScaleEntryChange(value)
                    },
                    modifier = Modifier.size(width = 90.dp, height = 56.dp)
                        .then(Modifier.focusRequester(scaleFocus)),
                    singleLine = true,
                    suffix = { Text("%") },
                    isError = scaleError,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                        imeAction = androidx.compose.ui.text.input.ImeAction.Done,
                    ),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                        onDone = { onCommitScaleEntry() },
                    ),
                )
            }
            IconButton(
                onClick = {
                    onCancelScaleEntry()
                    onChange(layout.copy(contentScale = ((layout.contentScale * 100).roundToInt() + 1).coerceIn(25, 200) / 100f))
                },
                enabled = enabled && layout.contentScale < 2f,
                modifier = Modifier.size(48.dp).semantics { contentDescription = increaseScale },
            ) { Text("+", style = MaterialTheme.typography.titleLarge) }
        }
        if (scaleError) Text(
            stringResource(R.string.layout_v3_scale_error),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        Row(
            Modifier.fillMaxWidth().selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            LayoutSizePreset.entries.forEach { preset ->
                LayoutSizePresetTile(
                    preset = preset, selected = selectedPreset == preset, enabled = enabled,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        onCancelScaleEntry()
                        onChange(preset.apply(layout))
                    },
                )
            }
        }
        Text(
            if (selectedPreset == null) stringResource(
                R.string.layout_v3_custom_size,
                stringResource(if (layout.scaleMode == PageScaleMode.FIT) R.string.tools_fit_compact else R.string.tools_fill_compact),
                (layout.contentScale * 100).roundToInt(),
                stringResource(if (layout.scaleMode == PageScaleMode.FIT && !layout.edgeToEdge)
                    R.string.layout_with_margin else R.string.layout_v3_without_margin),
            ) else stringResource(R.string.layout_v3_preset_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            value = layout.contentScale,
            onValueChange = {
                onCancelScaleEntry()
                onContinuousStart()
                onChange(layout.copy(contentScale = it))
            },
            onValueChangeFinished = onContinuousEnd,
            valueRange = .25f..2f,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth()
                .semantics { contentDescription = scaleDescription },
        )
        LayoutCategoryRestore(
            enabled = enabled,
            onClick = {
                onCancelScaleEntry()
                onChange(restoreLayoutCategory(layout, baseline, DocumentLayoutControl.SIZE))
            },
        )
    }
}

@Composable
private fun LayoutSizePresetTile(
    preset: LayoutSizePreset,
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val label = stringResource(when (preset) {
        LayoutSizePreset.WITH_MARGIN -> R.string.layout_with_margin
        LayoutSizePreset.WITHOUT_MARGIN -> R.string.layout_v3_without_margin
        LayoutSizePreset.FILL -> R.string.tools_fill_compact
    })
    val description = stringResource(when (preset) {
        LayoutSizePreset.WITH_MARGIN -> R.string.layout_v3_with_margin_description
        LayoutSizePreset.WITHOUT_MARGIN -> R.string.layout_v3_without_margin_description
        LayoutSizePreset.FILL -> R.string.tools_fill_description
    })
    val ink = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    val surfaceColor by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        animationSpec = tween(160), label = "layout_preset",
    )
    Surface(
        modifier = modifier
            .selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = label + ". " + description },
        color = surfaceColor,
        shape = MaterialTheme.shapes.medium,
        border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Column(
            Modifier.heightIn(min = 68.dp).padding(horizontal = 2.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Canvas(Modifier.size(width = 24.dp, height = 28.dp)) {
                val width = size.width
                val height = size.height
                drawRect(ink, style = Stroke(1.dp.toPx()))
                val inset = when (preset) {
                    LayoutSizePreset.WITH_MARGIN -> 6.dp.toPx()
                    LayoutSizePreset.WITHOUT_MARGIN -> 2.dp.toPx()
                    LayoutSizePreset.FILL -> 0f
                }
                drawRect(
                    ink.copy(alpha = if (selected) .75f else .48f),
                    topLeft = Offset(inset, inset),
                    size = androidx.compose.ui.geometry.Size(width - 2 * inset, height - 2 * inset),
                )
            }
            Text(label, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 2)
        }
    }
}

@Composable
private fun LayoutRotationControls(
    layout: PdfPageLayout,
    baseline: PdfPageLayout,
    enabled: Boolean,
    onChange: (PdfPageLayout) -> Unit,
) {
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            stringResource(R.string.tools_rotation_angle, layout.rotationQuarterTurns * 90),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(-1, 1).forEach { turn ->
                androidx.compose.material3.OutlinedButton(
                    onClick = { onChange(layout.copy(rotationQuarterTurns = documentLayoutRotation(layout.rotationQuarterTurns, turn))) },
                    enabled = enabled,
                    modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                ) {
                    Icon(
                        painterResource(if (turn < 0) R.drawable.ic_layout_rotate_left else R.drawable.ic_layout_rotate_right),
                        null,
                        Modifier.size(20.dp),
                    )
                    Text(
                        stringResource(if (turn < 0) R.string.layout_interactive_rotate_left else R.string.layout_interactive_rotate_right),
                        style = MaterialTheme.typography.labelMedium,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                    )
                }
            }
        }
        LayoutCategoryRestore(
            enabled = enabled,
            onClick = { onChange(restoreLayoutCategory(layout, baseline, DocumentLayoutControl.ROTATION)) },
        )
    }
}

@Composable
private fun LayoutCategoryRestore(enabled: Boolean, onClick: () -> Unit) {
    androidx.compose.material3.TextButton(
        onClick = onClick, enabled = enabled,
        modifier = Modifier.heightIn(min = 48.dp),
    ) { Text(stringResource(R.string.layout_v3_restore_category)) }
}

private fun DocumentLayoutControl.labelResource() = when (this) {
    DocumentLayoutControl.POSITION -> R.string.layout_interactive_position
    DocumentLayoutControl.ROTATION -> R.string.layout_interactive_rotate
    DocumentLayoutControl.SIZE, DocumentLayoutControl.MARGINS -> R.string.tools_size_compact
}

private fun DocumentLayoutControl.iconResource() = when (this) {
    DocumentLayoutControl.POSITION -> R.drawable.ic_layout_position
    DocumentLayoutControl.ROTATION -> R.drawable.ic_layout_rotate_right
    DocumentLayoutControl.SIZE, DocumentLayoutControl.MARGINS -> R.drawable.ic_layout_size
}

@Preview(name = "Ajustar estreito", widthDp = 320, heightDp = 580, fontScale = 1.3f, showBackground = true)
@Preview(name = "Ajustar página", widthDp = 390, heightDp = 760, showBackground = true)
@Preview(
    name = "Ajustar página escuro",
    widthDp = 390,
    heightDp = 760,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Preview(name = "Ajustar fonte ampliada", widthDp = 360, heightDp = 760, fontScale = 1.3f)
@Composable
private fun DocumentLayoutSheetPreview() {
    DocumentLayoutPreviewContent(DocumentLayoutControl.POSITION)
}

@Preview(name = "Ajustar rotação", widthDp = 390, heightDp = 760, showBackground = true)
@Preview(name = "Girar compacto", widthDp = 320, heightDp = 580, fontScale = 1.3f, locale = "pt-rBR")
@Preview(name = "Girar paisagem", widthDp = 800, heightDp = 400, locale = "pt-rBR")
@Composable
private fun DocumentLayoutRotationPreview() {
    DocumentLayoutPreviewContent(DocumentLayoutControl.ROTATION)
}

@Preview(name = "Ajustar tamanho fonte ampliada", widthDp = 360, heightDp = 760, fontScale = 1.3f)
@Composable
private fun DocumentLayoutSizePreview() {
    DocumentLayoutPreviewContent(DocumentLayoutControl.SIZE)
}

@Composable
private fun DocumentLayoutPreviewContent(control: DocumentLayoutControl) {
    ScaniqoTheme {
        Surface {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    stringResource(R.string.tools_layout),
                    style = MaterialTheme.typography.titleLarge
                )
                DocumentLayoutSheetContent(
                    DocumentToolsUiState(
                        workspace = PdfWorkspace(
                            1, "preview", "preview",
                            (1..8).map { PdfPageSpec("page-$it", "preview", it - 1) },
                        ),
                        selectedPageId = "page-1",
                        layoutPreview = DocumentToolPreviewState(
                            preview = PdfPagePreview("preview", 0),
                            pageId = "page-1",
                        ),
                    ),
                    {},
                    initialControl = control,
                )
            }
        }
    }
}
