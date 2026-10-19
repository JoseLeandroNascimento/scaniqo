package com.joseleandro.scaniqo.ui.documentEditor.components

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joseleandro.scaniqo.R
import com.joseleandro.scaniqo.domain.model.PdfOverlay
import com.joseleandro.scaniqo.domain.model.PdfPageSpec
import com.joseleandro.scaniqo.domain.model.PdfPoint
import com.joseleandro.scaniqo.ui.documentCommon.components.DocumentPdfPage
import com.joseleandro.scaniqo.ui.documentEditor.event.DocumentToolsEvent
import com.joseleandro.scaniqo.ui.documentEditor.state.DocumentToolsUiState
import com.joseleandro.scaniqo.ui.documentSignature.DocumentSignatureViewportState
import com.joseleandro.scaniqo.ui.documentSignature.signatureDocumentViewportBounds
import kotlin.math.roundToInt

@Composable
internal fun DocumentFillMarkStage(
    state: DocumentToolsUiState,
    page: PdfPageSpec,
    draft: PdfOverlay?,
    dirty: Boolean,
    inputMode: com.joseleandro.scaniqo.domain.model.PdfOverlayKind,
    excludedId: String?,
    editingText: Boolean,
    focusRequest: Int,
    onUpdate: (PdfOverlay) -> Unit,
    onSelect: (PdfOverlay) -> Unit,
    onInsert: (PdfPoint) -> Unit,
    onStopTyping: () -> Unit,
    onEvent: (DocumentToolsEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val workspace = state.workspace ?: return
    val viewport = rememberSaveable(workspace.workspaceId, page.id, saver = DocumentSignatureViewportState.Saver) {
        DocumentSignatureViewportState()
    }
    val density = LocalDensity.current
    val preview = state.pagePreviews[page.id]
    val requestMatches = excludedId == null || (state.overlayPreviewPageId == page.id && state.overlayPreviewId == excludedId)
    val ready = requestMatches && preview?.preview != null && !preview.isRendering && !preview.error
    val latestDraft by rememberUpdatedState(draft)
    val latestEditing by rememberUpdatedState(editingText)
    val latestUpdate by rememberUpdatedState(onUpdate)
    val latestSelect by rememberUpdatedState(onSelect)
    val latestInsert by rememberUpdatedState(onInsert)
    val latestPage by rememberUpdatedState(page)
    var gestureDraft by remember(page.id) { mutableStateOf<PdfOverlay?>(null) }
    var gestureInProgress by remember(page.id) { mutableStateOf(false) }
    val displayedDraft = gestureDraft ?: draft
    Column(modifier) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = {
                onStopTyping()
                onEvent(DocumentToolsEvent.SelectPage(workspace.pages[state.selectedPageIndex - 1].id))
            }, enabled = state.canEdit && !dirty && state.selectedPageIndex > 0) {
                Icon(painterResource(R.drawable.arrow_back_24px), stringResource(R.string.overlay_previous))
            }
            Text(stringResource(R.string.overlay_page, state.selectedPageIndex + 1, workspace.pages.size),
                style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
            val fitLabel = stringResource(R.string.fill_mark_fit)
            TextButton(onClick = { onStopTyping(); viewport.reset() }, enabled = ready,
                modifier = Modifier.semantics { contentDescription = fitLabel }) {
                Text("${(viewport.value.zoom * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium)
            }
            IconButton(onClick = {
                onStopTyping()
                onEvent(DocumentToolsEvent.SelectPage(workspace.pages[state.selectedPageIndex + 1].id))
            }, enabled = state.canEdit && !dirty && state.selectedPageIndex < workspace.pages.lastIndex) {
                Icon(painterResource(R.drawable.arrow_back_24px), stringResource(R.string.overlay_next), Modifier.rotate(180f))
            }
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().clipToBounds()
            .background(MaterialTheme.colorScheme.surfaceContainer)) {
            val width = constraints.maxWidth.toFloat().coerceAtLeast(1f)
            val height = constraints.maxHeight.toFloat().coerceAtLeast(1f)
            val margin = with(density) { 12.dp.toPx() }
            val bounds = signatureDocumentViewportBounds(viewport.value, width, height, margin, maxZoom = 6f)
            val latestBounds by rememberUpdatedState(bounds)
            val selectionRadius = with(density) { 8.dp.toPx() }
            val selection = displayedDraft?.let {
                fillMarkSelectionGeometry(
                    it, bounds.page.left, bounds.page.top, bounds.page.width, bounds.page.height,
                    width, height, with(density) { 48.dp.toPx() }, with(density) { 4.dp.toPx() },
                    with(density) { 48.dp.toPx() },
                )
            }
            val latestSelection by rememberUpdatedState(selection)
            fun pagePoint(point: Offset): PdfPoint {
                val current = latestBounds.page
                return PdfPoint((point.x - current.left) / current.width, (point.y - current.top) / current.height)
            }
            LaunchedEffect(focusRequest, editingText, state.canEdit, width, height) {
                val text = draft
                if (editingText && state.canEdit && text != null && text.kind in textKinds) {
                    val desiredPixels = with(density) { 18.sp.toPx() }
                    val baseFontPixels = text.fontSize * bounds.basePage.width / 595f
                    val zoom = (desiredPixels / baseFontPixels.coerceAtLeast(1f)).coerceIn(2f, 6f)
                    viewport.focusOn(
                        PdfPoint(text.x + minOf(text.width / 2f, .12f), text.y + text.height / 2f),
                        zoom, width, height, margin, maxZoom = 6f,
                    )
                }
            }
            val pageDescription = stringResource(R.string.fill_inline_hint)
            Box(
                Modifier.fillMaxSize().clipToBounds().semantics { contentDescription = pageDescription }
                    .pointerInput(page.id, ready, state.canEdit, width, height) {
                        if (!ready || !state.canEdit) return@pointerInput
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            val start = down.position
                            val initialBounds = latestBounds
                            val initialDraft = latestDraft
                            val startPoint = pagePoint(start)
                            val handle = latestSelection?.handleAt(PdfPoint(start.x, start.y))
                            val inside = initialDraft?.containsFillMarkPoint(startPoint) == true
                            // A borda externa e as alças movimentam; o interior preserva cursor e seleção do texto.
                            val border = !inside && initialDraft?.containsFillMarkPoint(
                                startPoint, selectionRadius / initialBounds.page.width,
                                selectionRadius / initialBounds.page.height,
                            ) == true
                            val delegatesToText = latestEditing && inside && handle == null
                            val resizeCorner = handle?.takeIf { it < 4 }
                            val moving = handle == 4 || border || (!latestEditing && inside && handle == null)
                            val claimsElement = handle != null || border
                            if (claimsElement) down.consume()
                            gestureInProgress = true
                            val intent = FillMarkGestureIntent()
                            var last = start
                            var pending: PdfOverlay? = null
                            try {
                                do {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    val change = event.changes.firstOrNull { it.id == down.id }
                                    val current = change?.position ?: last
                                    val pointers = maxOf(event.changes.count { it.pressed }, event.changes.count { it.previousPressed })
                                    intent.update(pointers, (current - start).getDistance(), viewConfiguration.touchSlop)
                                    if (claimsElement) event.changes.forEach { it.consume() }
                                    if (intent.transformed) {
                                        pending = null
                                        gestureDraft = null
                                        if (event.changes.count { it.pressed && it.previousPressed } >= 2) {
                                            viewport.transform(
                                                event.calculateCentroid(useCurrent = false), event.calculatePan(), event.calculateZoom(),
                                                width, height, margin, maxZoom = 6f,
                                            )
                                        }
                                        event.changes.forEach { it.consume() }
                                    } else if (!delegatesToText && intent.dragged && change != null && change.pressed) {
                                        change.consume()
                                        if (initialDraft != null && (resizeCorner != null || moving)) {
                                            val delta = current - start
                                            pending = if (resizeCorner != null) initialDraft.scaledFillMarkFromCorner(
                                                resizeCorner, delta.x, delta.y,
                                                initialBounds.page.width, initialBounds.page.height,
                                            ) else initialDraft.movedTo(
                                                initialDraft.x + delta.x / initialBounds.page.width,
                                                initialDraft.y + delta.y / initialBounds.page.height,
                                            )
                                            gestureDraft = pending
                                        } else {
                                            val delta = current - last
                                            viewport.pan(delta.x, delta.y, width, height, margin, maxZoom = 6f)
                                        }
                                    }
                                    last = current
                                    val released = event.changes.none { it.pressed }
                                    if (released) {
                                        if (intent.canTap && !delegatesToText && !claimsElement && initialBounds.page.contains(start)) {
                                            val point = pagePoint(start)
                                            val hit = latestDraft?.takeIf { it.containsFillMarkPoint(point) }
                                                ?: latestPage.overlays.lastOrNull { it.containsFillMarkPoint(point) }
                                                ?: latestPage.overlays.lastOrNull {
                                                    it.containsFillMarkPoint(point, selectionRadius / latestBounds.page.width,
                                                        selectionRadius / latestBounds.page.height)
                                                }
                                            if (hit != null) latestSelect(hit) else latestInsert(point)
                                        } else if (intent.canCommitDrag) pending?.let(latestUpdate)
                                    }
                                } while (event.changes.any { it.pressed })
                            } finally {
                                gestureDraft = null
                                gestureInProgress = false
                            }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (ready) {
                    Surface(
                        Modifier.width(with(density) { bounds.basePage.width.toDp() }).aspectRatio(595f / 842f)
                            .graphicsLayer {
                                scaleX = bounds.viewport.zoom
                                scaleY = bounds.viewport.zoom
                                translationX = bounds.page.center.x - bounds.basePage.center.x
                                translationY = bounds.page.center.y - bounds.basePage.center.y
                            },
                        color = Color.White, shadowElevation = 2.dp,
                    ) {
                        Box {
                            DocumentPdfPage(
                                requireNotNull(preview?.preview).uri, preview.preview.pageIndex,
                                Modifier.fillMaxSize(), contentKey = "${page.id}:${preview.preview.uri}:${excludedId}",
                            )
                            Canvas(Modifier.fillMaxSize()) {
                                displayedDraft?.let { overlay ->
                                    if (!editingText || overlay.kind !in textKinds) drawOverlayDraft(
                                        overlay, state.selectedPageIndex + 1, workspace.pages.size,
                                    )
                                }
                            }
                        }
                    }
                } else if (preview?.error == true) {
                    TextButton(onClick = { onEvent(DocumentToolsEvent.RetryPagePreview(page.id)) }, enabled = state.canEdit) {
                        Text(stringResource(R.string.document_pdf_retry))
                    }
                } else CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                // O campo mantém a conexão com o teclado enquanto o PDF de fundo é atualizado.
                // A imagem antiga continua oculta até a prévia sem o elemento selecionado ficar pronta.
                if (editingText && displayedDraft != null && displayedDraft.kind in textKinds) {
                    val left = bounds.page.left + displayedDraft.x * bounds.page.width
                    val top = bounds.page.top + displayedDraft.y * bounds.page.height
                    val fieldWidth = displayedDraft.width * bounds.page.width
                    val fieldHeight = displayedDraft.height * bounds.page.height
                    InlineFillMarkText(
                        displayedDraft, focusRequest, fieldWidth, fieldHeight, bounds.page.width, state.canEdit, onUpdate, onStopTyping,
                        Modifier.align(Alignment.TopStart)
                            .offset { IntOffset(left.roundToInt(), top.roundToInt()) }
                            .wrapContentSize(Alignment.TopStart, unbounded = true)
                            .requiredSize(with(density) { fieldWidth.toDp() }, with(density) { fieldHeight.toDp() }),
                        onCursor = { caret ->
                            val inset = with(density) { 18.dp.toPx() }
                            val x = left + caret.x
                            val y = top + caret.y
                            val dx = when { x < inset -> inset - x; x > width - inset -> width - inset - x; else -> 0f }
                            val dy = when { y < inset -> inset - y; y > height - inset -> height - inset - y; else -> 0f }
                            if (!gestureInProgress && (kotlin.math.abs(dx) > .5f || kotlin.math.abs(dy) > .5f))
                                viewport.pan(dx, dy, width, height, margin, maxZoom = 6f)
                        },
                    )
                }
                if (selection != null) DocumentFillMarkSelection(
                    selection, displayedDraft, enabled = ready && state.canEdit, onUpdate = onUpdate,
                )
            }
        }
        if (!editingText) Text(
            stringResource(when {
                draft != null -> R.string.fill_selection_hint
                inputMode == com.joseleandro.scaniqo.domain.model.PdfOverlayKind.CHECKMARK -> R.string.fill_inline_check_hint
                else -> R.string.fill_inline_hint
            }), style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp),
        )
    }
}

@Composable
private fun InlineFillMarkText(
    draft: PdfOverlay,
    focusRequest: Int,
    width: Float,
    height: Float,
    pageWidth: Float,
    enabled: Boolean,
    onUpdate: (PdfOverlay) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier,
    onCursor: (Offset) -> Unit,
) {
    val density = LocalDensity.current
    val focusRequester = remember(draft.id) { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var value by rememberSaveable(draft.id, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(draft.text, TextRange(draft.text.length)))
    }
    LaunchedEffect(draft.text) {
        if (value.text != draft.text) value = TextFieldValue(draft.text, TextRange(draft.text.length))
    }
    LaunchedEffect(draft.id, focusRequest, enabled) {
        if (enabled) {
            focusRequester.requestFocus()
            keyboard?.show()
        }
    }
    val lines = draft.text.lines()
    val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG) }
    paint.textSize = draft.fontSize * pageWidth / 595f
    val maxTextWidth = lines.maxOfOrNull(paint::measureText)?.coerceAtLeast(1f) ?: 1f
    val fontPixels = paint.textSize * minOf(
        1f, width.coerceAtLeast(1f) / maxTextWidth,
        height.coerceAtLeast(1f) / (paint.textSize * lines.size * 1.2f),
    )
    val description = stringResource(R.string.fill_inline_edit_text)
    BasicTextField(
        value = value,
        enabled = enabled,
        onValueChange = { next ->
            if (next.text.length <= 2000) {
                val changed = next.text != value.text
                value = next
                if (changed) onUpdate(draft.copy(text = next.text))
            }
        },
        modifier = modifier.focusRequester(focusRequester).clipToBounds().semantics { contentDescription = description },
        textStyle = TextStyle(
            color = Color(draft.color).copy(alpha = draft.opacity),
            fontSize = with(density) { fontPixels.toSp() },
            lineHeight = with(density) { (fontPixels * 1.2f).toSp() },
            platformStyle = PlatformTextStyle(includeFontPadding = false),
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        onTextLayout = { layout ->
            val caret = layout.getCursorRect(value.selection.end.coerceIn(0, value.text.length))
            onCursor(Offset(caret.left, caret.bottom))
        },
        decorationBox = { field ->
            Box {
                if (value.text.isEmpty()) Text(stringResource(R.string.fill_inline_placeholder),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = .6f),
                    fontSize = with(density) { fontPixels.toSp() })
                field()
            }
        },
    )
}
