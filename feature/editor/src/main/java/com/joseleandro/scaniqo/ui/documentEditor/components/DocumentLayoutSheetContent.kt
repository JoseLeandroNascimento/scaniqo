package com.joseleandro.scaniqo.ui.documentEditor.components

import com.joseleandro.scaniqo.core.resources.R as CoreResourcesR
import com.joseleandro.scaniqo.feature.editor.R

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.ui.platform.testTag
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joseleandro.scaniqo.domain.geometry.PdfPageGeometry
import com.joseleandro.scaniqo.domain.model.effectiveContentPosition
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
    registerOverflowMenu: ((@Composable () -> Unit)?) -> Unit = {},
    initiallyExpanded: Boolean = false,
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
    var scaleEntry by rememberSaveable(workspace.workspaceId) { mutableStateOf<String?>(null) }
    var scaleError by rememberSaveable(workspace.workspaceId) { mutableStateOf(false) }
    var applyRequested by rememberSaveable(workspace.workspaceId) { mutableStateOf(false) }
    val targets = scope.targets(workspace.pages, anchorPageId)
    val page = workspace.pages.firstOrNull { it.id == previewPageId && it.id in targets }
        ?: workspace.pages.firstOrNull { it.id in targets } ?: initialPage
    val pageBaseline = baselines[page.id] ?: page.layout
    val displayLayout = pageBaseline.withLayoutAdjustmentsFrom(draft)
    val currentPreview = state.layoutPreview.preview.takeIf {
        state.layoutPreview.pageId == page.id
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
        onClick = { applyOnce() },
        register = registerPrimaryAction,
    )
    androidx.activity.compose.BackHandler(scaleEntry != null) {
        scaleEntry = null
        scaleError = false
    }
    LaunchedEffect(page.id, displayLayout, state.canEdit) {
        if (state.canEdit) onEvent(DocumentToolsEvent.PreviewLayout(page.id, displayLayout))
    }
    DisposableEffect(Unit) {
        onDispose { onEvent(DocumentToolsEvent.ClearLayoutPreview) }
    }

    val overflowMenu: @Composable () -> Unit = {
        LayoutSessionMenu(
            workspace = workspace,
            anchorPageId = anchorPageId,
            scope = scope,
            onScopeChange = { endGesture(); scope = it },
            enabled = state.canEdit && !applyRequested,
            canDiscard = draft != anchorBaseline,
            onDiscard = {
                endGesture()
                scaleEntry = null
                scaleError = false
                changeDraft(anchorBaseline)
            },
        )
    }
    val currentOverflowMenu by androidx.compose.runtime.rememberUpdatedState(overflowMenu)
    DisposableEffect(registerOverflowMenu) {
        registerOverflowMenu { currentOverflowMenu() }
        onDispose { registerOverflowMenu(null) }
    }

    DocumentLayoutControlsSheet(
        modifier = modifier,
        initiallyExpanded = initiallyExpanded,
        control = control,
        enabled = state.canEdit && !applyRequested,
        canUndo = history.undo.isNotEmpty(),
        canRedo = history.redo.isNotEmpty(),
        onControlChange = {
            endGesture()
            scaleEntry = null
            scaleError = false
            control = it
        },
        onUndo = {
            endGesture()
            scaleEntry = null
            scaleError = false
            history.undo(draft)?.let { (previous, nextHistory) ->
                draft = previous
                history = nextHistory
            }
        },
        onRedo = {
            endGesture()
            scaleEntry = null
            scaleError = false
            history.redo(draft)?.let { (next, nextHistory) ->
                draft = next
                history = nextHistory
            }
        },
        onControlsClosed = {
            endGesture()
            scaleEntry = null
            scaleError = false
        },
        previewKey = page.id,
        onGestureStart = {
            gestureStartLayout.value = draft
            beginGesture()
        },
        onTransformStart = { draft = gestureStartLayout.value },
        controls = { compact, shortHeight ->
            when (control) {
                DocumentLayoutControl.POSITION -> LayoutPositionControls(
                    draft, anchorBaseline, state.canEdit && !applyRequested,
                    livePreview?.pixels?.sourceWidth, livePreview?.pixels?.sourceHeight,
                    onChange = ::changeDraft,
                    onSize = { endGesture(); control = DocumentLayoutControl.SIZE },
                    onContinuousStart = ::beginGesture,
                    onContinuousEnd = ::endGesture,
                    compact = compact,
                    shortHeight = shortHeight,
                )
                DocumentLayoutControl.SIZE, DocumentLayoutControl.MARGINS -> LayoutSizeControls(
                    layout = draft,
                    baseline = anchorBaseline,
                    enabled = state.canEdit,
                    scaleEntry = scaleEntry,
                    scaleError = scaleError,
                    onStartScaleEntry = {
                        beginGesture()
                        scaleEntry = (draft.contentScale * 100).roundToInt().toString()
                        scaleError = false
                    },
                    onScaleEntryChange = {
                        scaleEntry = it
                        scaleError = false
                        it.toIntOrNull()?.takeIf { value -> value in 25..200 }?.let { value ->
                            changeDraft(draft.copy(contentScale = value / 100f))
                        }
                    },
                    onCommitScaleEntry = { commitScaleEntry().also { if (it) endGesture() } },
                    onCancelScaleEntry = { scaleEntry = null; scaleError = false },
                    onContinuousStart = ::beginGesture,
                    onContinuousEnd = ::endGesture,
                    onChange = ::changeDraft,
                )
                DocumentLayoutControl.ROTATION -> LayoutRotationControls(
                    draft, anchorBaseline, state.canEdit, ::changeDraft,
                    onContinuousStart = ::beginGesture,
                    onContinuousEnd = ::endGesture,
                    compact = compact,
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
                onRetry = { onEvent(DocumentToolsEvent.PreviewLayout(page.id, displayLayout)) },
                enabled = state.canEdit,
                pageNumber = workspace.pages.indexOf(page) + 1,
                reserveHandleSpace = true,
                livePixels = livePreview?.pixels,
                liveLayout = displayLayout,
                workspaceId = workspace.workspaceId,
                pageCount = workspace.pages.size,
                showPageNumbers = workspace.showPageNumbers,
                overlay = {
                    DocumentLayoutHandles(
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
        },
        navigation = if (targets.size > 1) ({
            DocumentToolTargetNavigation(
                pages = workspace.pages,
                targetIds = targets,
                previewPageId = page.id,
                onPageChange = { previewPageId = it },
                enabled = state.canEdit,
            )
        }) else null,
    )
}

@Composable
private fun LayoutSessionMenu(
    workspace: PdfWorkspace,
    anchorPageId: String,
    scope: DocumentPageScope,
    onScopeChange: (DocumentPageScope) -> Unit,
    enabled: Boolean,
    canDiscard: Boolean,
    onDiscard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    var choosingScope by rememberSaveable { mutableStateOf(false) }
    var choosingPages by rememberSaveable { mutableStateOf(false) }
    Box(modifier) {
        IconButton(
            onClick = { expanded = true },
            enabled = enabled,
            modifier = Modifier.testTag("layout_more"),
        ) {
            Icon(painterResource(CoreResourcesR.drawable.more_vert_24px), stringResource(R.string.layout_v3_more))
        }
        DropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.layout_apply_scope)) },
                onClick = { expanded = false; choosingScope = true },
                modifier = Modifier.testTag("layout_apply_scope"),
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.layout_v3_discard_session)) },
                enabled = canDiscard,
                onClick = { expanded = false; onDiscard() },
            )
        }
    }
    if (choosingScope) {
        AlertDialog(
            onDismissRequest = { choosingScope = false },
            title = { Text(stringResource(R.string.layout_apply_scope)) },
            text = {
                Column(Modifier.selectableGroup()) {
                    DocumentPageScopeMode.entries.forEach { mode ->
                        val label = when (mode) {
                            DocumentPageScopeMode.CURRENT -> stringResource(
                                R.string.interactive_scope_current_summary,
                                workspace.pages.indexOfFirst { it.id == anchorPageId } + 1,
                            )
                            DocumentPageScopeMode.SELECTED -> if (scope.mode == mode) pluralStringResource(
                                R.plurals.interactive_scope_selected_summary,
                                scope.targets(workspace.pages, anchorPageId).size,
                                scope.targets(workspace.pages, anchorPageId).size,
                            ) else stringResource(R.string.interactive_scope_choose)
                            DocumentPageScopeMode.ALL -> stringResource(R.string.interactive_scope_all)
                        }
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 56.dp)
                                .testTag("layout_scope_${mode.name}")
                                .selectable(
                                    selected = scope.mode == mode,
                                    enabled = enabled,
                                    role = Role.RadioButton,
                                    onClick = {
                                        choosingScope = false
                                        if (mode == DocumentPageScopeMode.SELECTED) choosingPages = true
                                        else onScopeChange(scope.copy(mode = mode))
                                    },
                                ).padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            RadioButton(selected = scope.mode == mode, onClick = null)
                            Text(label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { choosingScope = false }) {
                    Text(stringResource(R.string.tools_cancel))
                }
            },
        )
    }
    if (choosingPages) {
        DocumentPagePickerDialog(
            pages = workspace.pages,
            selectedIds = scope.pageIds.ifEmpty { setOf(anchorPageId) },
            allowMultiple = true,
            onDismiss = { choosingPages = false },
            onConfirm = {
                onScopeChange(DocumentPageScope(DocumentPageScopeMode.SELECTED, it))
                choosingPages = false
            },
        )
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
    onContinuousStart: () -> Unit,
    onContinuousEnd: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    shortHeight: Boolean = false,
) {
    val current = layout.effectiveContentPosition()
    val travel = remember(layout, sourceWidth, sourceHeight) {
        if (sourceWidth != null && sourceHeight != null && sourceWidth > 0f && sourceHeight > 0f)
            PdfPageGeometry.contentTravel(sourceWidth, sourceHeight, layout) else null
    }
    val horizontalBlocked = travel != null && kotlin.math.abs(travel.horizontal) < .01f
    val verticalBlocked = travel != null && kotlin.math.abs(travel.vertical) < .01f
    val positionPad: @Composable (Modifier) -> Unit = { padModifier ->
        DocumentPositionPad(
            position = current,
            enabled = enabled,
            horizontalEnabled = !horizontalBlocked,
            verticalEnabled = !verticalBlocked,
            onPositionChange = { onChange(layout.copy(contentPosition = it)) },
            onGestureStart = onContinuousStart,
            onGestureEnd = onContinuousEnd,
            modifier = padModifier,
            compact = compact || shortHeight,
        )
    }
    val actions: @Composable RowScope.() -> Unit = {
        TextButton(
            onClick = onSize,
            enabled = enabled,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
        ) {
            Text(
                stringResource(R.string.layout_v3_reduce_size),
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = 14.sp),
            )
        }
        LayoutCategoryRestore(
            enabled = enabled,
            onClick = { onChange(restoreLayoutCategory(layout, baseline, DocumentLayoutControl.POSITION)) },
            modifier = Modifier.weight(1f),
        )
    }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (compact) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                positionPad(Modifier.weight(1f))
                Row(
                    Modifier.width(208.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) { actions() }
            }
        } else {
            positionPad(Modifier)
        }
        if (!compact || horizontalBlocked || verticalBlocked) {
            Text(
                stringResource(when {
                    horizontalBlocked && verticalBlocked -> R.string.layout_v3_no_both_travel
                    horizontalBlocked -> R.string.layout_v3_no_horizontal_travel
                    verticalBlocked -> R.string.layout_v3_no_vertical_travel
                    else -> R.string.layout_interactive_drag
                }),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!compact) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) { actions() }
        }
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
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
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
            Modifier.fillMaxWidth().height(IntrinsicSize.Min).selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LayoutSizePreset.entries.forEach { preset ->
                LayoutSizePresetTile(
                    preset = preset, selected = selectedPreset == preset, enabled = enabled,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    onClick = {
                        onCancelScaleEntry()
                        onChange(preset.apply(layout))
                    },
                )
            }
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DocumentScaleSlider(
                value = layout.contentScale,
                onValueChange = {
                    onCancelScaleEntry()
                    onContinuousStart()
                    onChange(layout.copy(contentScale = it))
                },
                onValueChangeFinished = onContinuousEnd,
                enabled = enabled,
                modifier = Modifier.weight(1f),
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
}

@Composable
private fun LayoutRotationControls(
    layout: PdfPageLayout,
    baseline: PdfPageLayout,
    enabled: Boolean,
    onChange: (PdfPageLayout) -> Unit,
    onContinuousStart: () -> Unit,
    onContinuousEnd: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val dial: @Composable (Modifier) -> Unit = { dialModifier ->
        DocumentRotationDial(
            rotationQuarterTurns = layout.rotationQuarterTurns,
            enabled = enabled,
            onRotationChange = { onChange(layout.copy(rotationQuarterTurns = it)) },
            onGestureStart = onContinuousStart,
            onGestureEnd = onContinuousEnd,
            modifier = dialModifier,
            compact = compact,
        )
    }
    val restore: @Composable () -> Unit = {
        LayoutCategoryRestore(
            enabled = enabled,
            onClick = { onChange(restoreLayoutCategory(layout, baseline, DocumentLayoutControl.ROTATION)) },
        )
    }
    if (compact && androidx.compose.ui.platform.LocalDensity.current.fontScale <= 1.3f) {
        Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            dial(Modifier.weight(1f))
            restore()
        }
    } else {
        Column(
            modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            dial(Modifier)
            restore()
        }
    }
}

@Composable
private fun LayoutCategoryRestore(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 48.dp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
    ) {
        Text(
            stringResource(R.string.layout_v3_restore_category),
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = 14.sp),
        )
    }
}

@Preview(name = "Ajustar estreito", widthDp = 320, heightDp = 580, fontScale = 1.3f, showBackground = true, locale = "pt-rBR")
@Preview(name = "Ajustar página", widthDp = 390, heightDp = 760, showBackground = true)
@Preview(
    name = "Ajustar página escuro",
    widthDp = 390,
    heightDp = 760,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Preview(name = "Ajustar fonte ampliada", widthDp = 360, heightDp = 760, fontScale = 1.3f)
@Preview(name = "Posição em paisagem", widthDp = 800, heightDp = 400, locale = "pt-rBR")
@Preview(name = "Abas fonte 2x", widthDp = 320, heightDp = 680, fontScale = 2f, locale = "pt-rBR")
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

@Preview(name = "Ajustar tamanho fonte ampliada", widthDp = 360, heightDp = 760, fontScale = 1.3f, locale = "pt-rBR")
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
                    initiallyExpanded = true,
                )
            }
        }
    }
}
