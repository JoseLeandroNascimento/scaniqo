package com.joseleandro.scaniqo.ui.documentEditor.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.joseleandro.scaniqo.R
import com.joseleandro.scaniqo.core.graphics.ShapedTextLayout
import com.joseleandro.scaniqo.ui.components.DialogActions
import com.joseleandro.scaniqo.domain.model.PdfCheckmarkGeometry
import com.joseleandro.scaniqo.domain.model.PdfOverlay
import com.joseleandro.scaniqo.domain.model.PdfOverlayKind
import com.joseleandro.scaniqo.domain.model.PdfPoint
import com.joseleandro.scaniqo.domain.model.PdfRedactionArea
import com.joseleandro.scaniqo.ui.documentCommon.components.DocumentPdfPage
import com.joseleandro.scaniqo.ui.documentEditor.event.DocumentToolsEvent
import com.joseleandro.scaniqo.ui.documentEditor.model.DocumentTool
import com.joseleandro.scaniqo.ui.documentEditor.state.DocumentToolsUiState
import java.util.UUID
import kotlinx.serialization.json.Json
import kotlin.math.abs
import kotlin.math.min

/** Compartilha os mesmos rascunhos, páginas, prévias e histórico do editor existente. */
@Composable
internal fun DocumentOverlayEditor(
    state: DocumentToolsUiState,
    tool: DocumentTool,
    onEvent: (DocumentToolsEvent) -> Unit,
    onDraftChanged: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    if (tool == DocumentTool.FORMS || tool == DocumentTool.WATERMARK) {
        DocumentFillMarkEditor(state, tool, onEvent, onDraftChanged, onBack)
        return
    }
    val workspace = state.workspace ?: return
    val page = workspace.pages.getOrNull(state.selectedPageIndex) ?: return
    val redaction = tool == DocumentTool.REDACTION
    val initialKind = if (tool == DocumentTool.WATERMARK) PdfOverlayKind.WATERMARK else PdfOverlayKind.TEXT
    var encodedDraft by rememberSaveable(workspace.workspaceId, tool, page.id) {
        mutableStateOf(Json.encodeToString(newOverlay(initialKind)))
    }
    val draft = remember(encodedDraft) { Json.decodeFromString<PdfOverlay>(encodedDraft) }
    var hasDraft by rememberSaveable(workspace.workspaceId, tool, page.id) { mutableStateOf(false) }
    var allPages by rememberSaveable(tool) { mutableStateOf(tool == DocumentTool.WATERMARK) }
    var kindMenu by remember { mutableStateOf(false) }
    var colorMenu by remember { mutableStateOf(false) }
    var saveValueDialog by rememberSaveable { mutableStateOf(false) }
    var savedValueLabel by rememberSaveable { mutableStateOf("") }
    var encodedAreas by rememberSaveable(workspace.workspaceId) { mutableStateOf("[]") }
    val areas = remember(encodedAreas) { Json.decodeFromString<List<PdfRedactionArea>>(encodedAreas) }
    var confirmRedaction by rememberSaveable { mutableStateOf(false) }
    var gestureStart by remember(page.id) { mutableStateOf<Offset?>(null) }
    var gestureEnd by remember(page.id) { mutableStateOf<Offset?>(null) }
    var gesturePoints by remember(page.id) { mutableStateOf(emptyList<PdfPoint>()) }
    val editable = state.canEdit
    val currentDraft by rememberUpdatedState(draft)
    val currentAreas by rememberUpdatedState(areas)
    fun update(value: PdfOverlay) { encodedDraft = Json.encodeToString(value); hasDraft = true }
    val kinds = when (tool) {
        DocumentTool.FORMS -> listOf(PdfOverlayKind.TEXT, PdfOverlayKind.CHECKMARK)
        DocumentTool.WATERMARK -> listOf(PdfOverlayKind.WATERMARK, PdfOverlayKind.STAMP, PdfOverlayKind.HEADER, PdfOverlayKind.FOOTER, PdfOverlayKind.PAGE_NUMBER)
        else -> listOf(PdfOverlayKind.TEXT, PdfOverlayKind.HIGHLIGHT, PdfOverlayKind.UNDERLINE, PdfOverlayKind.ARROW, PdfOverlayKind.DRAWING)
    }
    val targets = if (allPages) workspace.pages.mapTo(mutableSetOf()) { it.id } else setOf(page.id)
    val existing = page.overlays.any { it.id == draft.id }
    val currentTargets by rememberUpdatedState(targets)
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            onEvent(DocumentToolsEvent.AddImageMark(uri.toString(), currentTargets,
                currentDraft.x, currentDraft.y, currentDraft.width, currentDraft.height))
            hasDraft = false
        }
    }
    LaunchedEffect(hasDraft, encodedAreas) { onDraftChanged(if (redaction) areas.isNotEmpty() else hasDraft) }
    LaunchedEffect(state.redactionSaveVersion) {
        if (state.redactionSaveVersion > 0) encodedAreas = "[]"
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(when (tool) {
            DocumentTool.FORMS -> R.string.overlay_forms_hint
            DocumentTool.WATERMARK -> R.string.overlay_watermark_hint
            DocumentTool.REDACTION -> R.string.overlay_redaction_hint
            else -> R.string.overlay_annotations_hint
        }), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            IconButton(enabled = editable && state.selectedPageIndex > 0 && !hasDraft,
                onClick = { onEvent(DocumentToolsEvent.SelectPage(workspace.pages[state.selectedPageIndex - 1].id)) }) {
                Icon(painterResource(R.drawable.arrow_back_24px), stringResource(R.string.overlay_previous))
            }
            Text(stringResource(R.string.overlay_page, state.selectedPageIndex + 1, workspace.pages.size),
                modifier = Modifier.weight(1f), textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelMedium)
            IconButton(enabled = editable && state.selectedPageIndex < workspace.pages.lastIndex && !hasDraft,
                onClick = { onEvent(DocumentToolsEvent.SelectPage(workspace.pages[state.selectedPageIndex + 1].id)) }) {
                Icon(painterResource(R.drawable.arrow_back_24px), stringResource(R.string.overlay_next),
                    modifier = Modifier.rotate(180f))
            }
        }
        val preview = state.pagePreviews[page.id]
        val description = stringResource(R.string.overlay_preview)
        val canPosition = editable && preview?.preview != null && preview.error == false && !preview.isRendering
        Box(Modifier.fillMaxWidth().heightIn(max = 420.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.widthIn(max = 300.dp).fillMaxWidth().aspectRatio(595f / 842f)
                .background(Color.White)) {
                preview?.preview?.let {
                    DocumentPdfPage(it.uri, it.pageIndex, Modifier.fillMaxSize(), contentKey = page.id)
                }
                val touch = Modifier.fillMaxSize().semantics { contentDescription = description }
                    .pointerInput(page.id, canPosition, draft.kind, redaction) {
                        if (!canPosition) return@pointerInput
                        detectTapGestures { position ->
                            if (!redaction) update(currentDraft.movedTo(position.x / size.width, position.y / size.height))
                        }
                    }
                    .pointerInput(page.id, canPosition, draft.kind, redaction) {
                        if (!canPosition) return@pointerInput
                        detectDragGestures(
                            onDragStart = { point ->
                                gestureStart = Offset((point.x / size.width).coerceIn(0f, 1f), (point.y / size.height).coerceIn(0f, 1f))
                                gestureEnd = gestureStart
                                gesturePoints = listOf(PdfPoint(gestureStart!!.x, gestureStart!!.y))
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                val point = Offset((change.position.x / size.width).coerceIn(0f, 1f),
                                    (change.position.y / size.height).coerceIn(0f, 1f))
                                gestureEnd = point
                                if (gesturePoints.size < 2000) gesturePoints = gesturePoints + PdfPoint(point.x, point.y)
                            },
                            onDragCancel = { gestureStart = null; gestureEnd = null; gesturePoints = emptyList() },
                            onDragEnd = {
                                val start = gestureStart
                                val end = gestureEnd
                                if (start != null && end != null) {
                                    val drawing = currentDraft.kind == PdfOverlayKind.DRAWING && !redaction
                                    val x = if (drawing) gesturePoints.minOf { it.x } else min(start.x, end.x)
                                    val y = if (drawing) gesturePoints.minOf { it.y } else min(start.y, end.y)
                                    val right = if (drawing) gesturePoints.maxOf { it.x } else maxOf(start.x, end.x)
                                    val bottom = if (drawing) gesturePoints.maxOf { it.y } else maxOf(start.y, end.y)
                                    val width = (right - x).coerceAtLeast(.005f).coerceAtMost(1f - x)
                                    val height = (bottom - y).coerceAtLeast(.005f).coerceAtMost(1f - y)
                                    if (width > 0f && height > 0f) {
                                        if (redaction) encodedAreas = Json.encodeToString(
                                            currentAreas + PdfRedactionArea(page.id, x, y, width, height))
                                        else update(currentDraft.copy(x = x, y = y, width = width, height = height,
                                            points = if (currentDraft.kind == PdfOverlayKind.DRAWING) gesturePoints else emptyList()))
                                    }
                                }
                                gestureStart = null; gestureEnd = null; gesturePoints = emptyList()
                            },
                        )
                    }
                Canvas(touch) {
                    areas.filter { it.pageId == page.id }.forEach {
                        drawRect(Color.Black, Offset(it.x * size.width, it.y * size.height),
                            Size(it.width * size.width, it.height * size.height))
                    }
                    if (!redaction && hasDraft) {
                        drawOverlayDraft(draft, state.selectedPageIndex + 1, workspace.pages.size)
                        drawRect(Color(0xFF315E80), Offset(draft.x * size.width, draft.y * size.height),
                            Size(draft.width * size.width, draft.height * size.height), style = Stroke(2f))
                        if (draft.kind == PdfOverlayKind.DRAWING) draft.points.zipWithNext().forEach { (a, b) ->
                            drawLine(Color(draft.color), Offset(a.x * size.width, a.y * size.height),
                                Offset(b.x * size.width, b.y * size.height), 2f)
                        }
                    }
                    val start = gestureStart
                    val end = gestureEnd
                    if (start != null && end != null) {
                        if (draft.kind == PdfOverlayKind.DRAWING && !redaction) {
                            gesturePoints.zipWithNext().forEach { (a, b) ->
                                drawLine(Color(draft.color), Offset(a.x * size.width, a.y * size.height),
                                    Offset(b.x * size.width, b.y * size.height), 2f)
                            }
                        } else drawRect(if (redaction) Color.Black else Color(0x66315E80),
                            Offset(min(start.x, end.x) * size.width, min(start.y, end.y) * size.height),
                            Size(abs(end.x - start.x) * size.width, abs(end.y - start.y) * size.height))
                    }
                }
                if (preview?.isRendering == true || (preview?.preview == null && preview?.error != true)) {
                    CircularProgressIndicator(Modifier.size(24.dp).align(Alignment.Center), strokeWidth = 2.dp)
                }
            }
        }
        if (preview?.error == true) {
            Text(stringResource(R.string.document_pdf_error), color = MaterialTheme.colorScheme.error)
            TextButton(onClick = { onEvent(DocumentToolsEvent.RetryPagePreview(page.id)) }, enabled = editable) {
                Text(stringResource(R.string.document_pdf_retry))
            }
        }
        if (redaction) {
            Text(stringResource(R.string.overlay_redaction_count, areas.size))
            TextButton(onClick = { encodedAreas = Json.encodeToString(areas.dropLast(1)) }, enabled = editable && areas.isNotEmpty()) {
                Text(stringResource(R.string.overlay_redaction_undo))
            }
            Button(onClick = { confirmRedaction = true }, enabled = editable && areas.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                if (state.isSaving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Text(stringResource(R.string.overlay_redaction_save))
            }
        } else {
            Box {
                OutlinedButton(onClick = { kindMenu = true }, enabled = editable, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.overlay_type, stringResource(draft.kind.label())))
                }
                DropdownMenu(expanded = kindMenu, onDismissRequest = { kindMenu = false }) {
                    kinds.forEach { kind ->
                        DropdownMenuItem(text = { Text(stringResource(kind.label())) },
                            onClick = { update(newOverlay(kind)); kindMenu = false })
                    }
                }
            }
            if (tool == DocumentTool.WATERMARK) {
                OutlinedButton(onClick = { imagePicker.launch("image/png") }, enabled = editable,
                    modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.overlay_logo)) }
                page.signatures.forEach { signature ->
                    TextButton(onClick = { onEvent(DocumentToolsEvent.EditSignature(page.id, signature.id)) },
                        enabled = editable, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.overlay_edit_image)) }
                }
            }
            if (draft.kind in textKinds) {
                OutlinedTextField(value = draft.text, onValueChange = { if (it.length <= 2000) update(draft.copy(text = it)) },
                    enabled = editable, label = { Text(stringResource(R.string.overlay_text)) },
                    modifier = Modifier.fillMaxWidth(), minLines = 1, maxLines = 4)
                Text(stringResource(R.string.overlay_font_size, draft.fontSize.toInt()), style = MaterialTheme.typography.labelMedium)
                Slider(value = draft.fontSize, onValueChange = { update(draft.copy(fontSize = it)) },
                    valueRange = 6f..72f, enabled = editable)
            }
            if (tool == DocumentTool.FORMS && state.canStoreFormValues) {
                TextButton(onClick = { savedValueLabel = ""; saveValueDialog = true },
                    enabled = editable && draft.text.isNotBlank() && !state.isUpdatingFormValues) {
                    Text(stringResource(R.string.overlay_save_value))
                }
                if (state.savedFormValues.isNotEmpty()) {
                    Text(stringResource(R.string.overlay_saved_values), style = MaterialTheme.typography.titleSmall)
                    state.savedFormValues.forEach { value ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { update(draft.copy(kind = PdfOverlayKind.TEXT, text = value.value)) },
                                enabled = editable, modifier = Modifier.weight(1f)) { Text(value.label) }
                            TextButton(onClick = { onEvent(DocumentToolsEvent.DeleteFormValue(value.id)) },
                                enabled = editable && !state.isUpdatingFormValues) { Text(stringResource(R.string.tools_delete)) }
                        }
                    }
                }
            }
            Box {
                TextButton(onClick = { colorMenu = true }, enabled = editable) {
                    Text(stringResource(R.string.overlay_color), color = Color(draft.color))
                }
                DropdownMenu(expanded = colorMenu, onDismissRequest = { colorMenu = false }) {
                    listOf(0xFF202124.toInt() to R.string.overlay_black, 0xFF1565C0.toInt() to R.string.overlay_blue,
                        0xFFC62828.toInt() to R.string.overlay_red, 0xFFFFCA28.toInt() to R.string.overlay_yellow).forEach { (color, label) ->
                        DropdownMenuItem(text = { Text(stringResource(label)) }, onClick = { update(draft.copy(color = color)); colorMenu = false })
                    }
                }
            }
            Text(stringResource(R.string.overlay_opacity, (draft.opacity * 100).toInt()), style = MaterialTheme.typography.labelMedium)
            Slider(value = draft.opacity, onValueChange = { update(draft.copy(opacity = it)) }, valueRange = .05f..1f, enabled = editable)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = allPages, onCheckedChange = { allPages = it }, enabled = editable)
                Text(stringResource(R.string.overlay_all_pages), style = MaterialTheme.typography.bodyMedium)
            }
            Button(onClick = {
                onEvent(DocumentToolsEvent.PutOverlay(draft, targets))
                encodedDraft = Json.encodeToString(newOverlay(draft.kind))
                hasDraft = false
            }, enabled = editable && draft.isValid() && (draft.kind !in textKinds || draft.text.isNotBlank()) &&
                (draft.kind != PdfOverlayKind.DRAWING || draft.points.size > 1) &&
                targets.all { id -> workspace.pages.find { it.id == id }?.let { it.overlays.size < 100 || it.overlays.any { item -> item.id == draft.id } } == true },
                modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(if (existing) R.string.overlay_update else R.string.overlay_add))
            }
            if (hasDraft || existing) TextButton(onClick = {
                encodedDraft = Json.encodeToString(newOverlay(initialKind)); hasDraft = false
            }, enabled = editable, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.overlay_new)) }
            if (existing) TextButton(onClick = {
                onEvent(DocumentToolsEvent.RemoveOverlay(draft.id, targets))
                encodedDraft = Json.encodeToString(newOverlay(initialKind)); hasDraft = false
            }, enabled = editable, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.overlay_delete)) }
            if (page.overlays.isNotEmpty()) {
                Text(stringResource(R.string.overlay_elements), style = MaterialTheme.typography.titleSmall)
                page.overlays.forEach { overlay ->
                    TextButton(onClick = { update(overlay) }, enabled = editable, modifier = Modifier.fillMaxWidth()) {
                        Text(overlay.text.take(60).ifBlank { stringResource(overlay.kind.label()) })
                    }
                }
            }
            Button(onClick = onBack, enabled = editable && !hasDraft, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.editor_tool_done))
            }
        }
    }
    if (saveValueDialog) AlertDialog(
        onDismissRequest = { saveValueDialog = false },
        title = { Text(stringResource(R.string.overlay_save_value)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.overlay_save_value_hint))
                OutlinedTextField(value = savedValueLabel, onValueChange = { if (it.length <= 60) savedValueLabel = it },
                    label = { Text(stringResource(R.string.overlay_value_label)) }, singleLine = true)
            }
        },
        confirmButton = {
            DialogActions(
                confirmText = stringResource(R.string.tools_save),
                onConfirm = {
                    onEvent(DocumentToolsEvent.SaveFormValue(savedValueLabel, draft.text)); saveValueDialog = false
                },
                dismissText = stringResource(R.string.tools_cancel),
                onDismiss = { saveValueDialog = false },
                confirmEnabled = editable && savedValueLabel.isNotBlank() && !state.isUpdatingFormValues,
            )
        },
    )
    if (confirmRedaction) AlertDialog(
        onDismissRequest = { confirmRedaction = false },
        title = { Text(stringResource(R.string.overlay_redaction_confirm_title)) },
        text = { Text(stringResource(R.string.overlay_redaction_confirm_body)) },
        confirmButton = {
            DialogActions(
                confirmText = stringResource(R.string.overlay_redaction_save),
                onConfirm = {
                    confirmRedaction = false
                    onEvent(DocumentToolsEvent.SaveRedactedCopy(areas))
                },
                dismissText = stringResource(R.string.tools_cancel),
                onDismiss = { confirmRedaction = false },
                confirmEnabled = editable,
            )
        },
    )
}

internal fun DrawScope.drawOverlayDraft(overlay: PdfOverlay, pageNumber: Int, count: Int) {
    val x = overlay.x * size.width
    val y = overlay.y * size.height
    val width = overlay.width * size.width
    val height = overlay.height * size.height
    val color = Color(overlay.color).copy(alpha = overlay.opacity)
    when (overlay.kind) {
        PdfOverlayKind.HIGHLIGHT -> drawRect(color, Offset(x, y), Size(width, height))
        PdfOverlayKind.UNDERLINE -> drawLine(color, Offset(x, y + height), Offset(x + width, y + height), 2f)
        PdfOverlayKind.CHECKMARK -> {
            val path = Path().apply {
                PdfCheckmarkGeometry.trace(x, y, width, height, ::moveTo, ::lineTo, ::cubicTo)
            }
            drawPath(
                path, color,
                style = Stroke(
                    width = PdfCheckmarkGeometry.strokeWidth(width, height),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
            )
        }
        PdfOverlayKind.ARROW -> {
            drawLine(color, Offset(x, y + height * .5f), Offset(x + width, y + height * .5f), 2f)
            drawLine(color, Offset(x + width, y + height * .5f), Offset(x + width * .8f, y + height * .15f), 2f)
            drawLine(color, Offset(x + width, y + height * .5f), Offset(x + width * .8f, y + height * .85f), 2f)
        }
        PdfOverlayKind.DRAWING -> Unit
        else -> {
            if (overlay.kind == PdfOverlayKind.STAMP) drawRect(color, Offset(x, y), Size(width, height), style = Stroke(2f))
            val content = overlay.text.replace("{pagina}", pageNumber.toString()).replace("{total}", count.toString())
            val pointWidth = overlay.width * 595f
            val pointHeight = overlay.height * 842f
            val padding = if (overlay.kind == PdfOverlayKind.STAMP) minOf(6f, minOf(pointWidth, pointHeight) / 4f) else 0f
            val innerWidth = (pointWidth - padding * 2).coerceAtLeast(.001f)
            val innerHeight = (pointHeight - padding * 2).coerceAtLeast(.001f)
            val layout = ShapedTextLayout.create(
                text = content, fontSize = overlay.fontSize,
                color = (overlay.color and 0x00FFFFFF) or ((overlay.opacity * 255).toInt() shl 24),
                maxWidth = innerWidth, maxHeight = innerHeight,
                wrap = overlay.wrapText, horizontalAlignment = overlay.textHorizontalAlignment,
            )
            drawIntoCanvas { canvas ->
                val native = canvas.nativeCanvas
                val save = native.save()
                try {
                    native.translate(x, y)
                    native.scale(size.width / 595f, size.height / 842f)
                    native.clipRect(0f, 0f, pointWidth, pointHeight)
                    layout.draw(
                        native, padding, padding, availableWidth = innerWidth, availableHeight = innerHeight,
                        verticalAlignment = overlay.textVerticalAlignment,
                    )
                } finally {
                    native.restoreToCount(save)
                }
            }
        }
    }
}

internal val textKinds = setOf(PdfOverlayKind.TEXT, PdfOverlayKind.STAMP, PdfOverlayKind.WATERMARK,
    PdfOverlayKind.HEADER, PdfOverlayKind.FOOTER, PdfOverlayKind.PAGE_NUMBER)

internal fun newOverlay(kind: PdfOverlayKind): PdfOverlay = PdfOverlay(
    id = UUID.randomUUID().toString(), kind = kind,
    text = when (kind) { PdfOverlayKind.PAGE_NUMBER -> "{pagina} / {total}"; else -> "" },
    x = if (kind == PdfOverlayKind.WATERMARK) .2f else .1f,
    y = when (kind) { PdfOverlayKind.HEADER -> .03f; PdfOverlayKind.FOOTER, PdfOverlayKind.PAGE_NUMBER -> .92f; PdfOverlayKind.WATERMARK -> .45f; else -> .2f },
    width = if (kind == PdfOverlayKind.CHECKMARK) .04f else .6f,
    height = if (kind == PdfOverlayKind.CHECKMARK) .03f else .06f,
    opacity = when (kind) { PdfOverlayKind.HIGHLIGHT -> .35f; PdfOverlayKind.WATERMARK -> .25f; else -> 1f },
    color = if (kind == PdfOverlayKind.HIGHLIGHT) 0xFFFFCA28.toInt() else 0xFF202124.toInt(),
)

internal fun PdfOverlayKind.label(): Int = when (this) {
    PdfOverlayKind.TEXT -> R.string.overlay_text
    PdfOverlayKind.CHECKMARK -> R.string.overlay_checkmark
    PdfOverlayKind.HIGHLIGHT -> R.string.overlay_highlight
    PdfOverlayKind.UNDERLINE -> R.string.overlay_underline
    PdfOverlayKind.ARROW -> R.string.overlay_arrow
    PdfOverlayKind.DRAWING -> R.string.overlay_drawing
    PdfOverlayKind.STAMP -> R.string.overlay_stamp
    PdfOverlayKind.WATERMARK -> R.string.overlay_watermark_kind
    PdfOverlayKind.HEADER -> R.string.overlay_header
    PdfOverlayKind.FOOTER -> R.string.overlay_footer
    PdfOverlayKind.PAGE_NUMBER -> R.string.overlay_number
}
