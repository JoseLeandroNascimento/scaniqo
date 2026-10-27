package com.joseleandro.scaniqo.ui.documentEditor.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.joseleandro.scaniqo.R
import com.joseleandro.scaniqo.domain.model.PdfOverlay
import com.joseleandro.scaniqo.domain.model.PdfOverlayKind
import com.joseleandro.scaniqo.domain.model.PdfPoint
import com.joseleandro.scaniqo.ui.components.DialogActions
import com.joseleandro.scaniqo.ui.documentEditor.event.DocumentToolsEvent
import com.joseleandro.scaniqo.ui.documentEditor.model.DocumentTool
import com.joseleandro.scaniqo.ui.documentEditor.state.DocumentToolsUiState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private enum class FillMarkArmedTool { BROWSE, TEXT, MARK }

@Serializable
private data class FillMarkDraftSnapshot(
    val overlay: PdfOverlay,
    val allPages: Boolean,
    val dirty: Boolean,
    val active: Boolean,
    val selectedId: String?,
    val mode: PdfOverlayKind,
    val armedTool: FillMarkArmedTool,
    val selectionBaseline: String?,
    val selectionStart: Int,
    val selectionEnd: Int,
)

/** Texto e marcas são editados na página; cada propriedade ocupa um painel contextual. */
@OptIn(ExperimentalMaterial3Api::class)
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
    var recentColors by remember(styles) { mutableStateOf(styles.recentColors()) }
    var encoded by rememberSaveable(workspace.workspaceId, tool, page.id) {
        mutableStateOf(Json.encodeToString(styles.newOverlay(PdfOverlayKind.TEXT)))
    }
    val draft = remember(encoded) { Json.decodeFromString<PdfOverlay>(encoded) }
    var active by rememberSaveable(workspace.workspaceId, tool, page.id) { mutableStateOf(false) }
    var dirty by rememberSaveable(workspace.workspaceId, tool, page.id) { mutableStateOf(false) }
    var selectedId by rememberSaveable(workspace.workspaceId, tool, page.id) { mutableStateOf<String?>(null) }
    var mode by rememberSaveable(tool) { mutableStateOf(PdfOverlayKind.TEXT) }
    var allPages by rememberSaveable(workspace.workspaceId, tool) { mutableStateOf(false) }
    var armedTool by remember(workspace.workspaceId, tool, page.id) { mutableStateOf(if (tool == DocumentTool.WATERMARK) FillMarkArmedTool.TEXT else FillMarkArmedTool.BROWSE) }
    var lastMarkKind by rememberSaveable(workspace.workspaceId, tool) { mutableStateOf(PdfOverlayKind.CHECKMARK) }
    var pendingMarkIds by remember(page.id) { mutableStateOf(emptySet<String>()) }
    var editingText by rememberSaveable(workspace.workspaceId, page.id) { mutableStateOf(false) }
    var focusRequest by rememberSaveable(workspace.workspaceId, page.id) { mutableIntStateOf(0) }
    var textValue by rememberSaveable(workspace.workspaceId, tool, page.id, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(""))
    }
    var controlsVisible by rememberSaveable { mutableStateOf(true) }
    var panel by rememberSaveable(workspace.workspaceId, tool, page.id) { mutableStateOf(FillMarkPanel.NONE) }
    var numericSizeBuffer by rememberSaveable(workspace.workspaceId, tool, page.id) { mutableStateOf<String?>(null) }
    var numericSizeError by rememberSaveable(workspace.workspaceId, tool, page.id) { mutableStateOf(false) }
    var errorRes by rememberSaveable(workspace.workspaceId, tool, page.id) { mutableIntStateOf(0) }
    var more by remember { mutableStateOf(false) }
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
    val locale = LocalConfiguration.current.locales[0]
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    var hadIme by remember { mutableStateOf(false) }
    val localUndo = remember(undoEncoded) { Json.decodeFromString<List<FillMarkDraftSnapshot>>(undoEncoded) }
    val localRedo = remember(redoEncoded) { Json.decodeFromString<List<FillMarkDraftSnapshot>>(redoEncoded) }
    val processLifecycle = remember { ProcessLifecycleOwner.get().lifecycle }
    DisposableEffect(processLifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && armedTool == FillMarkArmedTool.MARK) {
                armedTool = FillMarkArmedTool.BROWSE
            }
        }
        processLifecycle.addObserver(observer)
        onDispose { processLifecycle.removeObserver(observer) }
    }

    fun currentDraft(): PdfOverlay = Json.decodeFromString(encoded)
    fun currentTargets(): Set<String> =
        if (allPages) workspace.pages.mapTo(mutableSetOf()) { it.id } else setOf(page.id)
    fun snapshot(): FillMarkDraftSnapshot = FillMarkDraftSnapshot(
        currentDraft(), allPages, dirty, active, selectedId, mode, armedTool,
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
        armedTool = value.armedTool
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
        val fitted = if (value.kind == PdfOverlayKind.TEXT && value.autoSizeText &&
            (value.text != old.text || value.fontSize != old.fontSize ||
                value.bold != old.bold || value.italic != old.italic || !old.autoSizeText)
        ) value.fittedFillMarkText() else value
        if (fitted == old) return
        if (!active && armedTool == FillMarkArmedTool.MARK && fitted.kind in binaryFillMarkKinds) {
            encoded = Json.encodeToString(fitted)
            mode = fitted.kind
            lastMarkKind = fitted.kind
            errorRes = 0
            return
        }
        numericSizeBuffer = null
        numericSizeError = false
        recordChange(groupWithTyping = editingText && fitted.text != old.text)
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
        recentColors = styles.rememberColor(color)
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
        armedTool = if (tool == DocumentTool.WATERMARK) FillMarkArmedTool.TEXT else FillMarkArmedTool.BROWSE
        selectionBaseline = null
        panel = FillMarkPanel.NONE
        numericSizeBuffer = null
        numericSizeError = false
        errorRes = 0
        clearHistory()
    }
    fun canCommit(value: PdfOverlay): Boolean =
        editable && value.isValid() && (value.kind !in textKinds || value.text.isNotBlank()) &&
            currentTargets().all { id -> workspace.pages.find { it.id == id }?.let { target ->
                target.overlays.size < 100 || target.overlays.any { it.id == value.id }
            } == true }

    fun commitCurrent(): Boolean {
        numericSizeBuffer?.let { input ->
            val size = parseFillMarkSize(input)
            if (size == null) {
                numericSizeError = true
                panel = FillMarkPanel.SIZE
                return false
            }
            numericSizeBuffer = null
            numericSizeError = false
            update(currentDraft().withFillMarkFontSize(size))
            keyboard?.hide()
            focusManager.clearFocus()
        }
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
        numericSizeBuffer = null
        numericSizeError = false
        panel = FillMarkPanel.NONE
        editingText = true
        controlsVisible = true
        focusRequest++
    }
    fun select(value: PdfOverlay) {
        if (value.kind !in textKinds && value.kind !in binaryFillMarkKinds) return
        if (active && value.id == currentDraft().id) {
            if (value.kind in textKinds) focusText()
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
        armedTool = FillMarkArmedTool.BROWSE
        panel = FillMarkPanel.NONE
        numericSizeBuffer = null
        errorRes = 0
    }
    fun insert(point: PdfPoint) {
        if (active) {
            if (commitCurrent()) reset()
            return
        }
        when (armedTool) {
            FillMarkArmedTool.BROWSE -> Unit
            FillMarkArmedTool.TEXT -> {
                val value = styles.newOverlay(PdfOverlayKind.TEXT).placedFillMarkAt(point).let {
                    if (it.autoSizeText) it.fittedFillMarkText() else it
                }
                stopTyping()
                clearHistory()
                selectedId = null
                selectionBaseline = null
                encoded = Json.encodeToString(value)
                textValue = TextFieldValue(value.text, TextRange(value.text.length))
                active = true
                dirty = false
                armedTool = FillMarkArmedTool.BROWSE
                panel = FillMarkPanel.NONE
                errorRes = 0
                focusText()
            }
            FillMarkArmedTool.MARK -> {
                if (!editable) return
                if (page.overlays.size + pendingMarkIds.size >= 100) {
                    errorRes = R.string.fill_v3_invalid_element
                    return
                }
                val candidate = currentDraft().withFillMarkKind(mode).nextFillMarkAt(point)
                if (candidate == null) {
                    errorRes = R.string.fill_v3_invalid_element
                    return
                }
                pendingMarkIds = pendingMarkIds + candidate.id
                styles.remember(candidate)
                onEvent(DocumentToolsEvent.PutOverlay(candidate, setOf(page.id)))
                errorRes = 0
            }
        }
    }
    fun changeMode(value: PdfOverlayKind) {
        if ((value == PdfOverlayKind.TEXT && armedTool == FillMarkArmedTool.TEXT) ||
            (value in binaryFillMarkKinds && armedTool == FillMarkArmedTool.MARK)) return
        if (active && !commitCurrent()) return
        reset()
        when {
            value == PdfOverlayKind.TEXT -> {
                mode = PdfOverlayKind.TEXT
                encoded = Json.encodeToString(styles.newOverlay(PdfOverlayKind.TEXT))
                armedTool = FillMarkArmedTool.TEXT
            }
            value in binaryFillMarkKinds -> {
                mode = lastMarkKind
                encoded = Json.encodeToString(styles.newOverlay(mode))
                armedTool = FillMarkArmedTool.MARK
            }
        }
    }
    fun changeMarkKindOrMode(value: PdfOverlayKind) {
        if (value !in binaryFillMarkKinds) return
        if (active && currentDraft().kind in binaryFillMarkKinds) {
            mode = value
            lastMarkKind = value
            update(currentDraft().withFillMarkKind(value))
        } else if (armedTool == FillMarkArmedTool.MARK) {
            mode = value
            lastMarkKind = value
            update(currentDraft().withFillMarkKind(value))
        } else changeMode(value)
    }
    fun stopInsertion() {
        if (armedTool == FillMarkArmedTool.BROWSE) return
        armedTool = FillMarkArmedTool.BROWSE
        panel = FillMarkPanel.NONE
        numericSizeBuffer = null
        numericSizeError = false
        errorRes = 0
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
        armedTool = FillMarkArmedTool.BROWSE
        encoded = Json.encodeToString(copy)
        textValue = TextFieldValue(copy.text, TextRange(copy.text.length))
        dirty = true
        active = true
        panel = FillMarkPanel.NONE
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
            panel = FillMarkPanel.NONE
            numericSizeBuffer = null
            errorRes = 0
        }
    }
    fun changePanel(value: FillMarkPanel) {
        if (editingText) stopTyping()
        numericSizeBuffer = null
        numericSizeError = false
        panel = if (panel == value) FillMarkPanel.NONE else value
    }
    fun changeScope(value: Boolean) {
        if (value == allPages) return
        recordChange()
        allPages = value
        dirty = if (existing != null) currentDraft() != existing || value
            else currentDraft().kind !in textKinds || currentDraft().text.isNotBlank()
    }
    fun startNumericSize() {
        if (editingText) stopTyping()
        numericSizeBuffer = formatFillMarkSize(currentDraft().fontSize, locale)
        numericSizeError = false
    }
    fun confirmNumericSize(): Boolean {
        val input = numericSizeBuffer ?: return true
        val size = parseFillMarkSize(input)
        if (size == null) {
            numericSizeError = true
            panel = FillMarkPanel.SIZE
            return false
        }
        numericSizeBuffer = null
        numericSizeError = false
        update(currentDraft().withFillMarkFontSize(size))
        keyboard?.hide()
        focusManager.clearFocus()
        return true
    }
    fun undo() {
        if (!editable) return
        numericSizeBuffer = null
        numericSizeError = false
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
        } else if (state.canUndo || pendingMarkIds.isNotEmpty()) {
            if (active) reset()
            pendingMarkIds = emptySet()
            onEvent(DocumentToolsEvent.Undo)
        }
    }
    fun redo() {
        if (!editable) return
        numericSizeBuffer = null
        numericSizeError = false
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
        if (finishInProgress || !editable || !confirmNumericSize() || !commitCurrent()) return
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
    LaunchedEffect(page.overlays) {
        val committedIds = page.overlays.mapTo(mutableSetOf()) { it.id }
        pendingMarkIds = pendingMarkIds - committedIds
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
    BackHandler(enabled = panel != FillMarkPanel.NONE && !editingText && numericSizeBuffer == null) {
        panel = FillMarkPanel.NONE
    }
    BackHandler(enabled = editingText && !showValues && !saveValue && !showElements) {
        stopTyping()
    }
    BackHandler(enabled = numericSizeBuffer != null) {
        numericSizeBuffer = null
        numericSizeError = false
        keyboard?.hide()
        focusManager.clearFocus()
    }

    BackHandler(enabled = armedTool != FillMarkArmedTool.BROWSE &&
        !editingText && panel == FillMarkPanel.NONE && numericSizeBuffer == null) {
        stopInsertion()
    }
    BackHandler(enabled = active && !editingText && panel == FillMarkPanel.NONE &&
        numericSizeBuffer == null && armedTool == FillMarkArmedTool.BROWSE) {
        dismissSelection()
    }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            DocumentFillMarkStage(
                state = state, page = page, draft = draft.takeIf { active },
                excludedId = existing?.id, editingText = editingText, focusRequest = focusRequest,
                textValue = textValue, onTextValueChange = ::changeTextValue,
                onUpdate = ::update, onSelect = ::select, onInsert = ::insert,
                onStopTyping = ::stopTyping, onNavigatePage = ::navigatePage,
                onEvent = onEvent, modifier = Modifier.fillMaxSize(),
                insertionKind = when (armedTool) {
                    FillMarkArmedTool.BROWSE -> null
                    FillMarkArmedTool.TEXT -> PdfOverlayKind.TEXT
                    FillMarkArmedTool.MARK -> mode
                },
                onDismissSelection = ::dismissSelection,
                canUndo = localUndo.isNotEmpty() || (active && dirty && selectionBaseline == null) || state.canUndo,
                canRedo = localRedo.isNotEmpty() || (!active && state.canRedo),
                onUndo = ::undo, onRedo = ::redo,
            )
            if (!controlsVisible) SmallFloatingActionButton(
                onClick = { controlsVisible = true },
                modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
            ) {
                Icon(painterResource(R.drawable.ic_tool_adjust), stringResource(R.string.fill_inline_show_controls))
            }
        }
        if (controlsVisible) Box(Modifier.fillMaxWidth()) {
            FillMarkControls(
                active = active, continuousInsertion = armedTool == FillMarkArmedTool.MARK,
                textInsertionArmed = armedTool == FillMarkArmedTool.TEXT,
                mode = mode, editingText = editingText, editable = editable,
                draft = draft.takeIf { active || armedTool == FillMarkArmedTool.MARK }, panel = panel,
                canUndo = localUndo.isNotEmpty() || (active && dirty && selectionBaseline == null) || state.canUndo,
                canRedo = localRedo.isNotEmpty() || (!active && state.canRedo),
                onMode = ::changeMarkKindOrMode,
                onCreateMode = ::changeMode,
                onStopInsertion = ::stopInsertion,
                showHistoryActions = false,
                onPanel = ::changePanel,
                onEditText = { if (editingText) stopTyping() else focusText() },
                onMore = { more = true },
                onUndo = ::undo, onRedo = ::redo, onUpdate = ::update,
                onSelectColor = ::selectColor, recentColors = recentColors,
                numericSizeBuffer = numericSizeBuffer,
                onStartNumericSize = ::startNumericSize,
                onNumericSizeChange = { if (acceptsFillMarkSizeInput(it)) {
                    numericSizeBuffer = it
                    numericSizeError = false
                } },
                onConfirmNumericSize = { confirmNumericSize() },
                onCancelNumericSize = {
                    numericSizeBuffer = null
                    numericSizeError = false
                    keyboard?.hide()
                    focusManager.clearFocus()
                },
                numericSizeError = numericSizeError,
                allPages = allPages, pageCount = workspace.pages.size,
                onAllPages = ::changeScope,
                errorText = errorRes.takeIf { it != 0 }?.let { stringResource(it) },
                modifier = Modifier.fillMaxWidth(),
            )
            DropdownMenu(expanded = more, onDismissRequest = { more = false },
                modifier = Modifier.align(Alignment.BottomEnd)) {
                if (active) {
                    if (canDuplicateCurrent()) DropdownMenuItem(
                        text = { Text(stringResource(R.string.fill_mark_duplicate)) },
                        leadingIcon = { Icon(painterResource(R.drawable.ic_fill_duplicate), null) },
                        onClick = { more = false; duplicate() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.overlay_delete)) },
                        leadingIcon = { Icon(painterResource(R.drawable.delete_24px), null) },
                        onClick = { more = false; deleteCurrent() },
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.fill_v3_precise_position)) },
                        leadingIcon = { Icon(painterResource(R.drawable.ic_layout_position), null) },
                        onClick = { more = false; changePanel(FillMarkPanel.POSITION) },
                    )
                    if (draft.kind in textKinds) DropdownMenuItem(
                        text = { Text(stringResource(R.string.fill_v3_text_box)) },
                        leadingIcon = { Icon(painterResource(R.drawable.ic_fill_text_size), null) },
                        onClick = { more = false; changePanel(FillMarkPanel.BOX) },
                    )
                    if (workspace.pages.size > 1) DropdownMenuItem(
                        text = { Text(stringResource(R.string.fill_v3_repeat_pages)) },
                        leadingIcon = { Icon(painterResource(R.drawable.ic_tool_pages), null) },
                        onClick = { more = false; changePanel(FillMarkPanel.SCOPE) },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.fill_v3_revert_element)) },
                        leadingIcon = { Icon(painterResource(R.drawable.ic_document_undo), null) },
                        onClick = { more = false; revertElement() },
                    )
                    HorizontalDivider()
                }
                if (state.canStoreFormValues) DropdownMenuItem(
                    text = { Text(stringResource(R.string.fill_mark_values)) },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_tag_person), null) },
                    onClick = { more = false; stopTyping(); showValues = true },
                )
                if (page.overlays.isNotEmpty()) DropdownMenuItem(
                    text = { Text(stringResource(R.string.fill_mark_elements, page.overlays.size)) },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_tool_pages), null) },
                    onClick = { more = false; stopTyping(); showElements = true },
                )
                DropdownMenuItem(text = { Text(stringResource(R.string.fill_inline_hide_controls)) },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_tool_adjust), null) },
                    onClick = { more = false; controlsVisible = false })
            }
        }
    }
    if (showElements) AlertDialog(
        onDismissRequest = { showElements = false },
        title = { Text(stringResource(R.string.overlay_elements)) },
        text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                page.overlays.forEach { overlay ->
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
                                encoded = Json.encodeToString(styles.newOverlay(PdfOverlayKind.TEXT).copy(text = value.value))
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
