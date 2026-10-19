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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.joseleandro.scaniqo.R
import com.joseleandro.scaniqo.domain.model.PdfOverlay
import com.joseleandro.scaniqo.domain.model.PdfOverlayKind
import com.joseleandro.scaniqo.domain.model.PdfPoint
import com.joseleandro.scaniqo.ui.components.DialogActions
import com.joseleandro.scaniqo.ui.documentEditor.event.DocumentToolsEvent
import com.joseleandro.scaniqo.ui.documentEditor.model.DocumentTool
import com.joseleandro.scaniqo.ui.documentEditor.state.DocumentToolsUiState
import kotlinx.serialization.json.Json

/** O texto é criado e editado na folha; propriedades ficam recolhidas até serem solicitadas. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DocumentFillMarkEditor(
    state: DocumentToolsUiState,
    tool: DocumentTool,
    onEvent: (DocumentToolsEvent) -> Unit,
    onDraftChanged: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    val workspace = state.workspace ?: return
    val page = workspace.pages.getOrNull(state.selectedPageIndex) ?: return
    var encoded by rememberSaveable(workspace.workspaceId, tool, page.id) {
        mutableStateOf(Json.encodeToString(newOverlay(PdfOverlayKind.TEXT)))
    }
    val draft = remember(encoded) { Json.decodeFromString<PdfOverlay>(encoded) }
    var active by rememberSaveable(workspace.workspaceId, tool, page.id) { mutableStateOf(false) }
    var dirty by rememberSaveable(workspace.workspaceId, tool, page.id) { mutableStateOf(false) }
    var selectedId by rememberSaveable(workspace.workspaceId, tool, page.id) { mutableStateOf<String?>(null) }
    var mode by rememberSaveable(tool) { mutableStateOf(PdfOverlayKind.TEXT) }
    var allPages by rememberSaveable(workspace.workspaceId, tool) { mutableStateOf(false) }
    var editingText by rememberSaveable(workspace.workspaceId, page.id) { mutableStateOf(false) }
    var focusRequest by rememberSaveable(workspace.workspaceId, page.id) { mutableIntStateOf(0) }
    var controlsVisible by rememberSaveable { mutableStateOf(true) }
    var showProperties by rememberSaveable { mutableStateOf(false) }
    var more by remember { mutableStateOf(false) }
    var showElements by rememberSaveable(page.id) { mutableStateOf(false) }
    var showValues by rememberSaveable { mutableStateOf(false) }
    var saveValue by rememberSaveable { mutableStateOf(false) }
    var valueLabel by rememberSaveable { mutableStateOf("") }
    val existing = page.overlays.firstOrNull { it.id == selectedId }
    val targets = if (allPages) workspace.pages.mapTo(mutableSetOf()) { it.id } else setOf(page.id)
    val editable = state.canEdit
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    var hadIme by remember { mutableStateOf(false) }

    fun stopTyping() { editingText = false; keyboard?.hide(); focusManager.clearFocus() }
    fun update(value: PdfOverlay) {
        encoded = Json.encodeToString(value)
        active = true
        dirty = if (existing != null) value != existing else value.kind !in textKinds || value.text.isNotBlank()
    }
    fun reset() {
        stopTyping()
        encoded = Json.encodeToString(newOverlay(mode))
        dirty = false
        active = false
        selectedId = null
    }
    fun canCommit(value: PdfOverlay): Boolean =
        editable && value.isValid() && (value.kind !in textKinds || value.text.isNotBlank()) &&
            targets.all { id -> workspace.pages.find { it.id == id }?.let { target ->
                target.overlays.size < 100 || target.overlays.any { it.id == value.id }
            } == true }

    fun commitCurrent(): Boolean {
        if (dirty && !canCommit(draft)) return false
        if (dirty) onEvent(DocumentToolsEvent.PutOverlay(draft, targets))
        return true
    }
    fun focusText() {
        editingText = true
        controlsVisible = true
        focusRequest++
    }
    fun select(value: PdfOverlay) {
        if (active && value.id == draft.id) {
            if (value.kind in textKinds) focusText()
            return
        }
        if (value.id != selectedId && !commitCurrent()) return
        encoded = Json.encodeToString(value)
        selectedId = value.id
        dirty = false
        active = true
        allPages = false
        if (value.kind in textKinds) focusText() else stopTyping()
    }
    fun insert(point: PdfPoint) {
        if (!commitCurrent()) return
        val value = newOverlay(mode).placedFillMarkAt(point)
        selectedId = null
        encoded = Json.encodeToString(value)
        active = true
        dirty = mode == PdfOverlayKind.CHECKMARK
        allPages = false
        if (mode == PdfOverlayKind.TEXT) focusText() else stopTyping()
    }

    LaunchedEffect(dirty) { onDraftChanged(dirty) }
    LaunchedEffect(page.id, existing?.id) { onEvent(DocumentToolsEvent.SetOverlayPreview(page.id, existing?.id)) }
    DisposableEffect(page.id) {
        onDispose { onEvent(DocumentToolsEvent.SetOverlayPreview(page.id, null)) }
    }
    LaunchedEffect(page.overlays, existing?.id) {
        if (!dirty && existing != null && existing != draft) encoded = Json.encodeToString(existing)
        if (!dirty && selectedId != null && existing == null) reset()
    }
    LaunchedEffect(editingText, imeVisible) {
        if (!editingText) hadIme = false
        else if (imeVisible) hadIme = true
        else if (hadIme) stopTyping()
    }
    BackHandler(enabled = editingText && !showProperties && !showValues && !saveValue && !showElements) { stopTyping() }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
        DocumentFillMarkStage(
            state = state, page = page, draft = draft.takeIf { active }, dirty = dirty, inputMode = mode,
            excludedId = existing?.id, editingText = editingText, focusRequest = focusRequest,
            onUpdate = ::update, onSelect = ::select, onInsert = ::insert,
            onStopTyping = ::stopTyping, onEvent = onEvent,
            modifier = Modifier.fillMaxSize(),
        )
        if (!controlsVisible) SmallFloatingActionButton(
            onClick = { controlsVisible = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
        ) {
            Icon(painterResource(R.drawable.ic_tool_adjust), stringResource(R.string.fill_inline_show_controls))
        }
        }
        if (controlsVisible) {
            Box(Modifier.fillMaxWidth()) {
                FillMarkControls(
                    active = active, mode = mode, textElement = draft.kind in textKinds, editingText = editingText,
                    editable = editable, canApply = dirty && canCommit(draft),
                    onMode = { mode = it },
                    onApply = { if (commitCurrent()) reset() },
                    onCancel = ::reset,
                    onProperties = { stopTyping(); showProperties = true },
                    onEditText = { if (editingText) stopTyping() else if (active && draft.kind in textKinds) focusText() },
                    onMore = { more = true }, onDone = { stopTyping(); onBack() },
                    modifier = Modifier.fillMaxWidth(),
                )
                DropdownMenu(expanded = more, onDismissRequest = { more = false },
                    modifier = Modifier.align(Alignment.BottomEnd)) {
                    if (state.canStoreFormValues) DropdownMenuItem(
                        text = { Text(stringResource(R.string.fill_mark_values)) },
                        leadingIcon = { Icon(painterResource(R.drawable.ic_tag_person), null) },
                        onClick = { more = false; stopTyping(); showValues = true })
                    if (page.overlays.isNotEmpty()) DropdownMenuItem(
                        text = { Text(stringResource(R.string.fill_mark_elements, page.overlays.size)) },
                        onClick = { more = false; stopTyping(); showElements = true })
                    if (existing != null) DropdownMenuItem(
                        text = { Text(stringResource(R.string.overlay_delete)) },
                        leadingIcon = { Icon(painterResource(R.drawable.delete_24px), null) },
                        onClick = { more = false; onEvent(DocumentToolsEvent.RemoveOverlay(draft.id, targets)); reset() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.fill_inline_hide_controls)) },
                        onClick = { more = false; controlsVisible = false })
                }
            }
        }
    }
    if (showProperties) ModalBottomSheet(
        onDismissRequest = { showProperties = false },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        FillMarkProperties(
            draft = draft, allPages = allPages, editable = editable,
            onUpdate = ::update,
            onAllPages = { allPages = it; if (existing != null) dirty = true },
            onDone = { showProperties = false },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding(),
        )
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
                            update(draft.copy(kind = PdfOverlayKind.TEXT, text = value.value))
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
