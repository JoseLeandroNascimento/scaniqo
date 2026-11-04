package com.joseleandro.scaniqo.ui.documentEditor.components

import com.joseleandro.scaniqo.feature.editor.R

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.joseleandro.scaniqo.domain.model.PdfOverlay
import com.joseleandro.scaniqo.domain.model.PdfOverlayKind
import com.joseleandro.scaniqo.ui.components.DialogActions
import com.joseleandro.scaniqo.ui.documentEditor.event.DocumentToolsEvent
import com.joseleandro.scaniqo.ui.documentEditor.model.DocumentTool
import com.joseleandro.scaniqo.ui.documentEditor.state.DocumentToolsUiState
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class FillMarkDraftSnapshot(
    val overlay: PdfOverlay,
    val allPages: Boolean,
    val dirty: Boolean,
    val active: Boolean,
    val selectedId: String?,
    val mode: PdfOverlayKind,
    val selectionBaseline: String?,
    val selectionStart: Int,
    val selectionEnd: Int,
)

@Composable
internal fun DocumentFillMarkEditor(
    state: DocumentToolsUiState,
    tool: DocumentTool,
    onEvent: (DocumentToolsEvent) -> Unit,
    onDraftChanged: (Boolean) -> Unit,
    onBack: () -> Unit,
    registerPrimaryAction: (((() -> Unit)?) -> Unit) = {},
    onFinish: () -> Unit = onBack,
) {
    val workspace = state.workspace ?: return
    val page = workspace.pages.getOrNull(state.selectedPageIndex) ?: return
    val context = LocalContext.current
    val styles = remember(context.applicationContext) { FillMarkStylePreferences(context) }
    var encoded by rememberSaveable(workspace.workspaceId, tool, page.id) {
        mutableStateOf(Json.encodeToString(styles.newOverlay(PdfOverlayKind.TEXT)))
    }
    val draft = remember(encoded) { Json.decodeFromString<PdfOverlay>(encoded) }
    var active by rememberSaveable(workspace.workspaceId, tool, page.id) { mutableStateOf(false) }
    var dirty by rememberSaveable(workspace.workspaceId, tool, page.id) { mutableStateOf(false) }
    var selectedId by rememberSaveable(workspace.workspaceId, tool, page.id) { mutableStateOf<String?>(null) }
    var mode by rememberSaveable(tool) { mutableStateOf(PdfOverlayKind.TEXT) }
    var allPages by rememberSaveable(workspace.workspaceId, tool) { mutableStateOf(false) }
    var lastMarkKind by rememberSaveable(workspace.workspaceId, tool) { mutableStateOf(PdfOverlayKind.CHECKMARK) }
    var editingText by rememberSaveable(workspace.workspaceId, page.id) { mutableStateOf(false) }
    var focusRequest by rememberSaveable(workspace.workspaceId, page.id) { mutableIntStateOf(0) }
    var textValue by rememberSaveable(workspace.workspaceId, tool, page.id, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(""))
    }
    var errorRes by rememberSaveable(workspace.workspaceId, tool, page.id) { mutableIntStateOf(0) }
    var showInsert by rememberSaveable { mutableStateOf(false) }
    var property by rememberSaveable(workspace.workspaceId, tool, page.id) { mutableStateOf<FillMarkProperty?>(null) }
    var propertyBaseline by rememberSaveable(workspace.workspaceId, tool, page.id) { mutableStateOf<String?>(null) }
    var sheetHeight by remember { mutableStateOf(0.dp) }
    var showElements by rememberSaveable(page.id) { mutableStateOf(false) }
    var showValues by rememberSaveable { mutableStateOf(false) }
    var saveValue by rememberSaveable { mutableStateOf(false) }
    var valueLabel by rememberSaveable { mutableStateOf("") }
    var selectionBaseline by rememberSaveable(workspace.workspaceId, tool, page.id) { mutableStateOf<String?>(null) }
    var undoEncoded by rememberSaveable(workspace.workspaceId, tool, page.id) { mutableStateOf("[]") }
    var redoEncoded by rememberSaveable(workspace.workspaceId, tool, page.id) { mutableStateOf("[]") }
    var textGroupOpen by remember { mutableStateOf(false) }
    var finishInProgress by remember { mutableStateOf(false) }

    val existing = page.overlays.firstOrNull { it.id == selectedId }
    val editable = state.canEdit
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    var hadIme by remember { mutableStateOf(false) }
    val localUndo = remember(undoEncoded) { Json.decodeFromString<List<FillMarkDraftSnapshot>>(undoEncoded) }
    val localRedo = remember(redoEncoded) { Json.decodeFromString<List<FillMarkDraftSnapshot>>(redoEncoded) }

    fun currentDraft(): PdfOverlay = Json.decodeFromString(encoded)
    fun currentTargets(): Set<String> =
        if (allPages) workspace.pages.mapTo(mutableSetOf()) { it.id } else setOf(page.id)
    fun snapshot(): FillMarkDraftSnapshot = FillMarkDraftSnapshot(
        currentDraft(), allPages, dirty, active, selectedId, mode,
        selectionBaseline, textValue.selection.start, textValue.selection.end,
    )
    fun clearHistory() {
        undoEncoded = "[]"
        redoEncoded = "[]"
        textGroupOpen = false
    }
    fun recordChange(groupWithTyping: Boolean = false) {
        if (!groupWithTyping || !textGroupOpen) {
            val previous = Json.decodeFromString<List<FillMarkDraftSnapshot>>(undoEncoded)
            undoEncoded = Json.encodeToString((previous + snapshot()).takeLast(30))
            redoEncoded = "[]"
        }
        textGroupOpen = groupWithTyping
    }
    fun restore(value: FillMarkDraftSnapshot) {
        encoded = Json.encodeToString(value.overlay)
        allPages = value.allPages
        dirty = value.dirty
        active = value.active
        selectedId = value.selectedId
        mode = value.mode
        selectionBaseline = value.selectionBaseline
        val length = value.overlay.text.length
        textValue = TextFieldValue(
            value.overlay.text,
            TextRange(value.selectionStart.coerceIn(0, length), value.selectionEnd.coerceIn(0, length)),
        )
        errorRes = 0
        textGroupOpen = false
    }
    fun stopTyping() {
        if (editingText) {
            textValue = TextFieldValue(textValue.text, textValue.selection)
        }
        editingText = false
        textGroupOpen = false
        keyboard?.hide()
        focusManager.clearFocus()
    }
    fun update(value: PdfOverlay) {
        val old = currentDraft()
        val measured = if (property != FillMarkProperty.TRANSFORM &&
            value.kind == PdfOverlayKind.TEXT && value.autoSizeText &&
            (value.text != old.text || value.fontSize != old.fontSize ||
                value.bold != old.bold || value.italic != old.italic || !old.autoSizeText)
        ) value.fittedFillMarkText() else value
        val fitted = if (measured.rotationDegrees != 0f) measured.rotatedFillMark(measured.rotationDegrees) else measured
        if (fitted == old) return
        if (propertyBaseline == null) recordChange(groupWithTyping = editingText && fitted.text != old.text)
        mode = fitted.kind
        encoded = Json.encodeToString(fitted)
        active = true
        dirty = if (existing != null) fitted != existing || allPages
            else fitted.kind !in textKinds || fitted.text.isNotBlank()
        errorRes = 0
        if (fitted.text != textValue.text && !editingText) {
            textValue = TextFieldValue(fitted.text, TextRange(fitted.text.length))
        }
    }
    fun selectColor(color: Int) {
        update(currentDraft().copy(color = color))
    }
    fun reset() {
        stopTyping()
        encoded = Json.encodeToString(styles.newOverlay(mode))
        textValue = TextFieldValue("")
        dirty = false
        active = false
        selectedId = null
        allPages = false
        selectionBaseline = null
        property = null
        propertyBaseline = null
        errorRes = 0
        clearHistory()
    }
    fun canCommit(value: PdfOverlay): Boolean =
        editable && value.isValid() && (value.kind !in textKinds || value.text.isNotBlank()) &&
            currentTargets().all { id -> workspace.pages.find { it.id == id }?.let { target ->
                target.overlays.size < 100 || target.overlays.any { it.id == value.id }
            } == true }

    fun commitCurrent(): Boolean {
        val value = currentDraft()
        if (dirty && !canCommit(value)) {
            errorRes = if (existing != null && value.kind in textKinds && value.text.isBlank())
                R.string.fill_v3_empty_existing else R.string.fill_v3_invalid_element
            return false
        }
        if (dirty) {
            styles.remember(value)
            onEvent(DocumentToolsEvent.PutOverlay(value, currentTargets()))
        }
        return true
    }
    fun focusText() {
        if (!active || currentDraft().kind !in textKinds) return
        editingText = true
        focusRequest++
    }
    fun select(value: PdfOverlay) {
        if (value.kind !in textKinds && value.kind !in binaryFillMarkKinds) return
        if (active && value.id == currentDraft().id) {
            return
        }
        if (value.id != selectedId && !commitCurrent()) return
        stopTyping()
        clearHistory()
        encoded = Json.encodeToString(value)
        textValue = TextFieldValue(value.text, TextRange(value.text.length))
        selectedId = value.id
        selectionBaseline = Json.encodeToString(value)
        dirty = false
        active = true
        mode = value.kind
        allPages = false
        errorRes = 0
    }
    fun createElement(kind: PdfOverlayKind) {
        if (!editable || !commitCurrent()) return
        if (page.overlays.size + (if (active && existing == null && dirty) 1 else 0) >= 100) {
            errorRes = R.string.fill_v3_invalid_element
            return
        }
        reset()
        val value = styles.newOverlay(kind).let { it.movedTo((1f - it.width) / 2f, (1f - it.height) * .45f) }
        encoded = Json.encodeToString(value)
        mode = kind
        textValue = TextFieldValue(value.text, TextRange(value.text.length))
        active = true
        dirty = kind !in textKinds || value.text.isNotBlank()
        if (kind in binaryFillMarkKinds) lastMarkKind = kind
        showInsert = false
        if (kind in textKinds) focusText()
    }
    fun dismissSelection() {
        if (active && commitCurrent()) reset()
    }
    fun deleteCurrent() {
        val value = currentDraft()
        if (existing != null) onEvent(DocumentToolsEvent.RemoveOverlay(value.id, currentTargets()))
        reset()
    }
    fun canDuplicateCurrent(): Boolean = active && currentDraft().kind in repeatableFillMarkKinds &&
        canCommit(currentDraft()) && page.overlays.size + (if (existing != null) 0 else 1) < 100
    fun duplicate() {
        if (!canDuplicateCurrent() || !commitCurrent()) return
        val copy = currentDraft().duplicatedFillMark()
        stopTyping()
        clearHistory()
        selectedId = null
        selectionBaseline = null
        allPages = false
        encoded = Json.encodeToString(copy)
        textValue = TextFieldValue(copy.text, TextRange(copy.text.length))
        dirty = true
        active = true
    }
    fun revertElement() {
        val baseline = selectionBaseline?.let { Json.decodeFromString<PdfOverlay>(it) }
        if (baseline == null) {
            reset()
        } else {
            stopTyping()
            clearHistory()
            encoded = Json.encodeToString(baseline)
            textValue = TextFieldValue(baseline.text, TextRange(baseline.text.length))
            dirty = false
            allPages = false
            errorRes = 0
        }
    }
    fun applyProperty() {
        val baseline = propertyBaseline?.let { Json.decodeFromString<FillMarkDraftSnapshot>(it) }
        if (baseline != null && (baseline.overlay != currentDraft() || baseline.allPages != allPages)) {
            val previous = Json.decodeFromString<List<FillMarkDraftSnapshot>>(undoEncoded)
            undoEncoded = Json.encodeToString((previous + baseline).takeLast(30))
            redoEncoded = "[]"
        }
        if (currentDraft().kind in binaryFillMarkKinds) lastMarkKind = currentDraft().kind
        property = null
        propertyBaseline = null
    }
    fun cancelProperty() {
        propertyBaseline?.let { restore(Json.decodeFromString<FillMarkDraftSnapshot>(it)) }
        property = null
        propertyBaseline = null
    }
    fun openProperty(value: FillMarkProperty) {
        if (!active || !editable) return
        stopTyping()
        propertyBaseline = Json.encodeToString(snapshot())
        property = value
    }
    fun changeScope(value: Boolean) {
        if (value == allPages) return
        if (propertyBaseline == null) recordChange()
        allPages = value
        dirty = if (existing != null) currentDraft() != existing || value
            else currentDraft().kind !in textKinds || currentDraft().text.isNotBlank()
    }
    fun undo() {
        if (!editable) return
        stopTyping()
        val entries = Json.decodeFromString<List<FillMarkDraftSnapshot>>(undoEncoded)
        if (entries.isNotEmpty()) {
            val previous = entries.last()
            undoEncoded = Json.encodeToString(entries.dropLast(1))
            val future = Json.decodeFromString<List<FillMarkDraftSnapshot>>(redoEncoded)
            redoEncoded = Json.encodeToString((future + snapshot()).takeLast(30))
            restore(previous)
        } else if (active && dirty && selectionBaseline == null) {
            val pending = snapshot()
            reset()
            redoEncoded = Json.encodeToString(listOf(pending))
        } else if (state.canUndo) {
            if (active) reset()
            onEvent(DocumentToolsEvent.Undo)
        }
    }
    fun redo() {
        if (!editable) return
        stopTyping()
        val entries = Json.decodeFromString<List<FillMarkDraftSnapshot>>(redoEncoded)
        if (entries.isNotEmpty()) {
            val next = entries.last()
            redoEncoded = Json.encodeToString(entries.dropLast(1))
            val previous = Json.decodeFromString<List<FillMarkDraftSnapshot>>(undoEncoded)
            undoEncoded = Json.encodeToString((previous + snapshot()).takeLast(30))
            restore(next)
        } else if (state.canRedo && !active) {
            onEvent(DocumentToolsEvent.Redo)
        }
    }
    fun finishTool() {
        if (finishInProgress || !editable || !commitCurrent()) return
        finishInProgress = true
        stopTyping()
        dirty = false
        onDraftChanged(false)
        onFinish()
    }
    fun navigatePage(id: String) {
        if (!commitCurrent()) return
        reset()
        onEvent(DocumentToolsEvent.SelectPage(id))
    }
    fun changeTextValue(next: TextFieldValue) {
        if (next.text.length > 2000) return
        if (next.text != textValue.text) update(currentDraft().copy(text = next.text))
        textValue = next
    }

    RegisterDocumentToolPrimaryAction(
        enabled = editable && !finishInProgress,
        onClick = ::finishTool,
        register = registerPrimaryAction,
    )
    LaunchedEffect(dirty) { onDraftChanged(dirty) }
    LaunchedEffect(page.id, existing?.id) { onEvent(DocumentToolsEvent.SetOverlayPreview(page.id, existing?.id)) }
    DisposableEffect(page.id) {
        onDispose { onEvent(DocumentToolsEvent.SetOverlayPreview(page.id, null)) }
    }
    LaunchedEffect(page.overlays, existing?.id) {
        if (!dirty && existing != null && existing != draft) {
            encoded = Json.encodeToString(existing)
            textValue = TextFieldValue(existing.text, TextRange(existing.text.length))
        }
        if (!dirty && selectedId != null && existing == null) reset()
    }
    LaunchedEffect(editingText, imeVisible) {
        if (!editingText) hadIme = false
        else if (imeVisible) hadIme = true
        else if (hadIme) stopTyping()
    }
    BackHandler(enabled = editingText && !showValues && !saveValue && !showElements && property == null) {
        stopTyping()
    }
    BackHandler(enabled = active && !editingText && property == null && !showInsert && !showValues && !saveValue && !showElements) {
        dismissSelection()
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val transformVisible = property == FillMarkProperty.TRANSFORM
        val sheetBudget = if (maxHeight < 420.dp) maxHeight * .86f else (maxHeight * .68f).coerceAtMost(448.dp)
        val reservedHeight by animateDpAsState(
            if (transformVisible) sheetHeight.coerceIn(0.dp, maxHeight) else 0.dp,
            label = "fill adjustment space",
        )
        Column(Modifier.fillMaxSize()) {
            DocumentFillMarkStage(
                state = state, page = page, draft = draft.takeIf { active },
                excludedId = existing?.id, editingText = editingText, focusRequest = focusRequest,
                textValue = textValue, onTextValueChange = ::changeTextValue,
                onUpdate = ::update, onSelect = ::select, onInsert = { dismissSelection() },
                onStopTyping = ::stopTyping, onNavigatePage = ::navigatePage,
                onEvent = onEvent, modifier = Modifier.weight(1f).fillMaxWidth(),
                onDismissSelection = ::dismissSelection,
                canUndo = localUndo.isNotEmpty() || (active && dirty && selectionBaseline == null) || state.canUndo,
                canRedo = localRedo.isNotEmpty() || (!active && state.canRedo),
                onUndo = ::undo, onRedo = ::redo,
                hideSelectionActions = property != null || showInsert || showValues || saveValue || showElements,
                selectionActions = {
                    FillMarkContextToolbar(
                        draft = draft,
                        enabled = editable,
                        onEditText = ::focusText,
                        onProperty = ::openProperty,
                        onDuplicate = ::duplicate,
                        onDelete = ::deleteCurrent,
                        onSaveValue = { stopTyping(); valueLabel = ""; saveValue = true },
                        onReset = ::revertElement,
                        canDuplicate = canDuplicateCurrent(),
                        canSaveValue = state.canStoreFormValues && draft.text.isNotBlank(),
                        canReset = dirty,
                        pageCount = workspace.pages.size,
                    )
                },
            )
            if (transformVisible) {
                Spacer(Modifier.height(reservedHeight))
            } else {
                Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (errorRes != 0) Text(
                            stringResource(errorRes),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        DocumentEditorPrimaryActions(
                            insertLabel = stringResource(R.string.fill_context_insert),
                            doneLabel = stringResource(R.string.editor_tool_done),
                            onInsert = { stopTyping(); showInsert = true },
                            onDone = ::finishTool,
                            enabled = editable && !finishInProgress,
                            insertTag = "fill_insert",
                            doneTag = "fill_finish",
                        )
                    }
                }
            }
        }
        property?.let { current ->
            FillMarkPropertyDialog(
                property = current,
                draft = draft,
                editable = editable,
                onUpdate = ::update,
                onCancel = ::cancelProperty,
                onApply = ::applyProperty,
                    onSelectColor = ::selectColor,
                allPages = allPages,
                pageCount = workspace.pages.size,
                onAllPages = ::changeScope,
                maxHeight = (sheetBudget - 48.dp).coerceAtLeast(0.dp),
                onHeightChange = { sheetHeight = it },
            )
        }
    }
    if (showInsert) FillMarkInsertSheet(
        enabled = editable,
        hasElements = page.overlays.any { it.kind in textKinds || it.kind in binaryFillMarkKinds },
        showSavedValues = state.canStoreFormValues,
        onText = { createElement(PdfOverlayKind.TEXT) },
        onMark = { createElement(lastMarkKind) },
        onSavedValues = {
            if (commitCurrent()) {
                reset()
                showInsert = false
                showValues = true
            }
        },
        onElements = { showInsert = false; showElements = true },
        onDismiss = { showInsert = false },
    )
    if (showElements) AlertDialog(
        onDismissRequest = { showElements = false },
        title = { Text(stringResource(R.string.overlay_elements)) },
        text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                page.overlays.filter { it.kind in textKinds || it.kind in binaryFillMarkKinds }.forEach { overlay ->
                    TextButton(onClick = { select(overlay); showElements = false }, enabled = editable,
                        modifier = Modifier.fillMaxWidth()) {
                        Text(overlay.text.take(60).ifBlank { stringResource(overlay.kind.label()) })
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { showElements = false }) { Text(stringResource(R.string.tools_cancel)) } },
    )
    if (showValues) AlertDialog(
        onDismissRequest = { showValues = false },
        title = { Text(stringResource(R.string.overlay_saved_values)) },
        text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                if (state.savedFormValues.isEmpty()) Text(stringResource(R.string.fill_mark_no_values))
                state.savedFormValues.forEach { value ->
                    Row {
                        TextButton(onClick = {
                            if (active && currentDraft().kind in textKinds) {
                                update(currentDraft().copy(text = value.value))
                            } else {
                                if (active && !commitCurrent()) return@TextButton
                                reset()
                                mode = PdfOverlayKind.TEXT
                                val inserted = styles.newOverlay(PdfOverlayKind.TEXT).copy(text = value.value).fittedFillMarkText()
                                encoded = Json.encodeToString(inserted)
                                textValue = TextFieldValue(inserted.text, TextRange(inserted.text.length))
                                active = true
                                dirty = true
                            }
                            showValues = false
                            focusText()
                        }, enabled = editable, modifier = Modifier.weight(1f)) { Text(value.label) }
                        TextButton(onClick = { onEvent(DocumentToolsEvent.DeleteFormValue(value.id)) },
                            enabled = editable && !state.isUpdatingFormValues) {
                            Text(stringResource(R.string.fill_mark_remove))
                        }
                    }
                }
            }
        },
        confirmButton = {
            DialogActions(
                confirmText = stringResource(R.string.overlay_save_value),
                onConfirm = { showValues = false; valueLabel = ""; saveValue = true },
                dismissText = stringResource(R.string.tools_cancel),
                onDismiss = { showValues = false },
                confirmEnabled = editable && draft.text.isNotBlank() && !state.isUpdatingFormValues,
            )
        },
    )
    if (saveValue) AlertDialog(
        onDismissRequest = { saveValue = false },
        title = { Text(stringResource(R.string.overlay_save_value)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.overlay_save_value_hint))
                OutlinedTextField(valueLabel, { if (it.length <= 60) valueLabel = it },
                    label = { Text(stringResource(R.string.overlay_value_label)) }, singleLine = true)
            }
        },
        confirmButton = {
            DialogActions(
                confirmText = stringResource(R.string.tools_save),
                onConfirm = { onEvent(DocumentToolsEvent.SaveFormValue(valueLabel, draft.text)); saveValue = false },
                dismissText = stringResource(R.string.tools_cancel),
                onDismiss = { saveValue = false },
                confirmEnabled = editable && valueLabel.isNotBlank() && !state.isUpdatingFormValues,
            )
        },
    )
}
