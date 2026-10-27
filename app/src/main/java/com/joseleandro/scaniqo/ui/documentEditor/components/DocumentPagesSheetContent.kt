package com.joseleandro.scaniqo.ui.documentEditor.components

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.text.style.TextDirection
import com.joseleandro.scaniqo.ui.documentCommon.components.DocumentPageJumpDialog
import kotlinx.coroutines.launch
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.joseleandro.scaniqo.R
import com.joseleandro.scaniqo.domain.model.PdfPageSpec
import com.joseleandro.scaniqo.domain.model.PdfWorkspace
import com.joseleandro.scaniqo.ui.documentEditor.event.DocumentToolsEvent
import com.joseleandro.scaniqo.ui.documentEditor.model.PageDragBounds
import com.joseleandro.scaniqo.ui.documentEditor.model.PageReorderDrag
import com.joseleandro.scaniqo.ui.documentEditor.state.DocumentToolsUiState
import com.joseleandro.scaniqo.ui.documentEditor.util.pageDragScrollSpeed
import com.joseleandro.scaniqo.ui.theme.ScaniqoTheme
import kotlin.math.roundToInt
import kotlinx.coroutines.isActive

/** O arraste altera apenas a ordem local; o PDF só é renderizado uma vez, ao soltar. */
@Composable
internal fun DocumentPagesSheetContent(
    state: DocumentToolsUiState,
    onEvent: (DocumentToolsEvent) -> Unit,
    modifier: Modifier = Modifier,
    initialMode: PageReorderMode = PageReorderMode.DRAG,
) {
    val workspace = state.workspace ?: return
    val pageIds = remember(workspace.pages) { workspace.pages.map { it.id } }
    val listState = rememberLazyListState()
    var mode by rememberSaveable(workspace.workspaceId) { mutableStateOf(initialMode) }
    var numberingExpanded by rememberSaveable(workspace.workspaceId) { mutableStateOf(false) }
    var showPageJump by rememberSaveable(workspace.workspaceId) { mutableStateOf(false) }
    var pendingMove by remember(workspace.workspaceId) { mutableStateOf<Pair<String, Int>?>(null) }
    var moveMessage by remember(workspace.workspaceId) { mutableStateOf<Int?>(null) }
    var moveMessageWorkspace by remember(workspace.workspaceId) { mutableStateOf<PdfWorkspace?>(null) }
    val currentWorkspace by rememberUpdatedState(workspace)
    val visibleMoveMessage = moveMessage.takeIf { moveMessageWorkspace == workspace && state.canUndo }
    val coroutineScope = rememberCoroutineScope()
    var drag by remember(workspace.workspaceId) { mutableStateOf<PageReorderDrag?>(null) }
    val currentEvent by rememberUpdatedState(onEvent)
    val currentCanEdit by rememberUpdatedState(state.canEdit)
    val currentPageIds by rememberUpdatedState(pageIds)
    val order = drag?.order ?: pageIds
    val controlsEnabled = state.canEdit && drag == null
    val density = LocalDensity.current
    val edgeSize = with(density) { 56.dp.toPx() }
    val maxScrollSpeed = with(density) { 720.dp.toPx() }

    fun updateDrag(updated: PageReorderDrag?) {
        val previousOrder = drag?.order ?: currentPageIds
        val nextOrder = updated?.order ?: currentPageIds
        if (previousOrder != nextOrder && nextOrder.isNotEmpty()) {
            // Mantém o viewport numérico; seguir a chave faria a lista saltar ao trocar o primeiro item.
            listState.requestScrollToItem(
                listState.firstVisibleItemIndex.coerceAtMost(nextOrder.lastIndex + PageListHeader.entries.size),
                listState.firstVisibleItemScrollOffset,
            )
        }
        drag = updated
    }

    LaunchedEffect(pageIds, pendingMove) {
        pendingMove?.let { (id, target) ->
            if (pageIds.indexOf(id) == target) {
                moveMessage = target
                moveMessageWorkspace = workspace
                pendingMove = null
                listState.animateScrollToItem(target + PageListHeader.entries.size)
            }
        }
    }
    LaunchedEffect(workspace) {
        if (moveMessageWorkspace != null && moveMessageWorkspace != workspace) {
            moveMessage = null
            moveMessageWorkspace = null
        }
    }
    LaunchedEffect(pageIds, state.canEdit) {
        if (!state.canEdit || drag?.initialOrder?.let { it != pageIds } == true) updateDrag(null)
    }
    LaunchedEffect(drag?.itemId) {
        if (drag == null) return@LaunchedEffect
        var previousFrame = withFrameNanos { it }
        while (isActive && drag != null) {
            val frame = withFrameNanos { it }
            val seconds = ((frame - previousFrame) / 1_000_000_000f).coerceIn(0f, .05f)
            previousFrame = frame
            val moving = drag ?: break
            val info = listState.layoutInfo
            val speed = pageDragScrollSpeed(
                centerY = moving.centerY,
                viewportStart = info.viewportStartOffset.toFloat(),
                viewportEnd = info.viewportEndOffset.toFloat(),
                edgeSize = edgeSize,
                maxSpeed = maxScrollSpeed,
            )
            if (speed != 0f) {
                val consumed = listState.scrollBy(speed * seconds)
                if (consumed != 0f) updateDrag(drag?.retarget(listState.pageBounds()))
            }
        }
    }

    fun startDrag(pageId: String) {
        if (!currentCanEdit || mode != PageReorderMode.DRAG || drag != null || currentPageIds.size < 2) return
        val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == pageId } ?: return
        drag = PageReorderDrag(currentPageIds, pageId, item.offset + item.size / 2f, item.size)
    }

    fun finishDrag() {
        val finished = drag ?: return
        val committed = finished.committedOrder()
        if (currentCanEdit && finished.initialOrder == currentPageIds && committed != null) {
            drag = null
            pendingMove = finished.itemId to committed.indexOf(finished.itemId)
            currentEvent(DocumentToolsEvent.ReorderPages(committed))
        } else {
            updateDrag(null)
        }
    }

    // Os controles acompanham a rolagem: teclado e fontes grandes não deixam a lista sem espaço.
    Box(
        modifier
            .fillMaxSize()
            .clipToBounds()
            .pointerInput(workspace.workspaceId, state.canEdit, pageIds.size > 1, mode) {
                // O detector fica no pai estável: reciclar um item não interrompe o gesto.
                if (state.canEdit && pageIds.size > 1 && mode == PageReorderMode.DRAG) detectDragGesturesAfterLongPress(
                    onDragStart = { pointer ->
                        listState.layoutInfo.visibleItemsInfo.firstOrNull { item ->
                            pointer.y >= item.offset && pointer.y < item.offset + item.size
                        }?.key?.let { id -> if (id is String) startDrag(id) }
                    },
                    onDragEnd = { finishDrag() },
                    onDragCancel = { updateDrag(null) },
                    onDrag = { change, amount ->
                        if (drag != null) {
                            change.consume()
                            updateDrag(drag?.moveBy(amount.y, listState.pageBounds()))
                        }
                    },
                )
            },
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .selectableGroup(),
            contentPadding = PaddingValues(top = 4.dp, bottom = if (visibleMoveMessage != null) 84.dp else 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            userScrollEnabled = drag == null,
        ) {
            item(key = PageListHeader.CONTROLS, contentType = PageListHeader.CONTROLS) {
                Column(Modifier.fillMaxWidth().animateContentSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                        PageReorderMode.entries.forEachIndexed { index, option ->
                            SegmentedButton(
                                modifier = Modifier.fillMaxHeight(),
                                selected = mode == option,
                                onClick = { updateDrag(null); mode = option },
                                enabled = state.canEdit,
                                shape = SegmentedButtonDefaults.itemShape(index, PageReorderMode.entries.size),
                            ) {
                                Text(stringResource(if (option == PageReorderMode.DRAG)
                                    R.string.pages_v2_drag_mode else R.string.pages_v2_direct_mode))
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { numberingExpanded = !numberingExpanded }, enabled = controlsEnabled) {
                            Text(stringResource(R.string.pages_v2_numbering))
                        }
                        Spacer(Modifier.weight(1f))
                        if (pageIds.size > 12) TextButton(onClick = { showPageJump = true }, enabled = controlsEnabled) {
                            Text(stringResource(R.string.pages_v2_go_to_page))
                        }
                    }
                    AnimatedVisibility(numberingExpanded) {
                        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(stringResource(R.string.pages_numbering), Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyMedium)
                                Switch(checked = workspace.showPageNumbers,
                                    onCheckedChange = {
                                        moveMessage = null
                                        moveMessageWorkspace = null
                                        onEvent(DocumentToolsEvent.SetNumbering(it))
                                    },
                                    enabled = controlsEnabled)
                            }
                        }
                    }
                    if (mode == PageReorderMode.DIRECT) {
                        DirectPageMove(
                            pageIds = pageIds, selectedPageId = state.selectedPageId,
                            enabled = controlsEnabled && pageIds.size > 1,
                            thumbnail = {
                                val selected = state.selectedPageId?.takeIf { it in pageIds } ?: pageIds.first()
                                DocumentWorkspaceThumbnail(state, selected, onEvent, Modifier.fillMaxSize())
                            },
                            onMove = { id, index ->
                                pendingMove = id to index
                                onEvent(DocumentToolsEvent.SelectPage(id))
                                onEvent(DocumentToolsEvent.MovePageTo(id, index))
                            },
                        )
                    }
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(pluralStringResource(R.plurals.document_page_count, pageIds.size, pageIds.size),
                                style = MaterialTheme.typography.titleSmall)
                            if (mode == PageReorderMode.DRAG) Text(
                                stringResource(if (pageIds.size == 1) R.string.pages_one_page else R.string.pages_drag_hint),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (drag != null) IconButton(onClick = { updateDrag(null) }) {
                            Icon(painterResource(R.drawable.ic_toast_close), stringResource(R.string.pages_drag_cancel))
                        }
                    }
                }
            }
            itemsIndexed(order, key = { _, id -> id }) { index, id ->
                val isDragged = drag?.itemId == id
                val moveUp = stringResource(R.string.pages_move_up)
                val moveDown = stringResource(R.string.pages_move_down)
                val moveTo = stringResource(R.string.pages_v2_direct_mode)
                val delete = stringResource(R.string.tools_delete_page, index + 1)
                val actions = if (!controlsEnabled) emptyList() else buildList {
                    if (index > 0) add(CustomAccessibilityAction(moveUp) {
                        onEvent(DocumentToolsEvent.MovePageTo(id, index - 1)); true
                    })
                    if (index < order.lastIndex) add(CustomAccessibilityAction(moveDown) {
                        onEvent(DocumentToolsEvent.MovePageTo(id, index + 1)); true
                    })
                    add(CustomAccessibilityAction(moveTo) {
                        mode = PageReorderMode.DIRECT
                        onEvent(DocumentToolsEvent.SelectPage(id))
                        coroutineScope.launch { listState.animateScrollToItem(0) }
                        true
                    })
                    if (pageIds.size > 1) add(CustomAccessibilityAction(delete) {
                        onEvent(DocumentToolsEvent.RequestDeletePage(id)); true
                    })
                }
                PageOrderRow(
                    position = index + 1,
                    selected = id == state.selectedPageId,
                    thumbnail = { DocumentWorkspaceThumbnail(state, id, onEvent, Modifier.fillMaxSize()) },
                    enabled = controlsEnabled,
                    canDelete = pageIds.size > 1,
                    onSelect = {
                        onEvent(DocumentToolsEvent.SelectPage(id))
                        if (mode == PageReorderMode.DIRECT) coroutineScope.launch { listState.animateScrollToItem(0) }
                    },
                    onDelete = { onEvent(DocumentToolsEvent.RequestDeletePage(id)) },
                    modifier = Modifier.animateItem(
                        fadeInSpec = null, fadeOutSpec = null,
                        placementSpec = spring(dampingRatio = 1f, stiffness = 500f),
                    ).graphicsLayer { alpha = if (isDragged) 0f else 1f }
                        .semantics { customActions = actions },
                    dragHandle = {
                        if (mode == PageReorderMode.DRAG) PageDragHandle(
                            position = index + 1, enabled = state.canEdit && pageIds.size > 1,
                            accessibilityActions = emptyList(),
                        ) else Spacer(Modifier.width(8.dp))
                    },
                )
            }
        }
        drag?.let { moving ->
            // A miniatura continua apontando para a ordem da prévia, não para o rascunho.
            PageOrderRow(
                position = moving.order.indexOf(moving.itemId) + 1,
                selected = true,
                thumbnail = {
                    DocumentWorkspaceThumbnail(
                        state,
                        moving.itemId,
                        onEvent,
                        Modifier.fillMaxSize()
                    )
                },
                enabled = false,
                canDelete = false,
                onSelect = {},
                onDelete = {},
                modifier = Modifier
                    .offset { IntOffset(0, (moving.centerY - moving.height / 2f).roundToInt()) }
                    .height(with(density) { moving.height.toDp() })
                    .graphicsLayer { scaleX = 1.02f; scaleY = 1.02f }
                    .clearAndSetSemantics {},
                floating = true,
                dragHandle = {
                    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                        Icon(painterResource(R.drawable.ic_document_drag), null)
                    }
                },
            )
        }
        if (visibleMoveMessage != null && drag == null) Surface(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(8.dp),
            shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.secondaryContainer,
            shadowElevation = 4.dp,
        ) {
            Row(Modifier.padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.pages_v2_moved, visibleMoveMessage + 1),
                    Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
                    style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = {
                    val canUndoMove = moveMessageWorkspace == currentWorkspace
                    moveMessage = null
                    moveMessageWorkspace = null
                    if (canUndoMove) onEvent(DocumentToolsEvent.Undo)
                }, enabled = controlsEnabled && state.canUndo) {
                    Text(stringResource(R.string.viewer_undo_description))
                }
                IconButton(onClick = { moveMessage = null; moveMessageWorkspace = null }) {
                    Icon(painterResource(R.drawable.ic_toast_close), stringResource(R.string.preview_zoom_close))
                }
            }
        }
    }
    if (showPageJump) DocumentPageJumpDialog(
        pageCount = pageIds.size, onDismiss = { showPageJump = false },
        onPage = { index -> coroutineScope.launch { listState.animateScrollToItem(index + PageListHeader.entries.size) } },
    )
}

private enum class PageListHeader { CONTROLS }
internal enum class PageReorderMode { DRAG, DIRECT }

private fun LazyListState.pageBounds(): List<PageDragBounds> =
    layoutInfo.visibleItemsInfo.mapNotNull { item ->
        (item.key as? String)?.let { PageDragBounds(it, item.offset, item.size) }
    }

@Composable
private fun DirectPageMove(
    pageIds: List<String>,
    selectedPageId: String?,
    enabled: Boolean,
    thumbnail: @Composable () -> Unit,
    onMove: (String, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedIndex = pageIds.indexOf(selectedPageId).coerceAtLeast(0)
    val selectedId = pageIds.getOrNull(selectedIndex) ?: return
    var to by rememberSaveable(selectedId) { mutableStateOf("") }
    val target = to.map { Character.digit(it, 10) }.joinToString("").toIntOrNull()?.minus(1)
    val valid = target != null && target in pageIds.indices
    val samePosition = valid && target == selectedIndex
    val canMove = enabled && valid && !samePosition
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    fun move() {
        if (!canMove) return
        keyboard?.hide()
        focus.clearFocus()
        onMove(selectedId, requireNotNull(target))
        to = ""
    }
    Surface(modifier, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(Modifier.width(44.dp).aspectRatio(595f / 842f),
                    shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surface) { thumbnail() }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.pages_v2_selected_position, selectedIndex + 1),
                        style = MaterialTheme.typography.titleSmall)
                    if (valid && !samePosition) Text("${selectedIndex + 1} → ${requireNotNull(target) + 1}",
                        style = MaterialTheme.typography.headlineSmall.copy(textDirection = TextDirection.Ltr),
                        color = MaterialTheme.colorScheme.primary)
                }
            }
            OutlinedTextField(
                value = to,
                onValueChange = { value ->
                    if (value.length <= pageIds.size.toString().length && value.all { Character.digit(it, 10) >= 0 }) to = value
                },
                enabled = enabled, modifier = Modifier.fillMaxWidth(), singleLine = true,
                label = { Text(stringResource(R.string.pages_v2_final_position)) },
                isError = to.isNotBlank() && !valid,
                supportingText = {
                    Text(if (samePosition) stringResource(R.string.pages_v2_same_position)
                        else stringResource(R.string.pages_v2_range_bounds, pageIds.size))
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { move() }),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = target == 0, onClick = { to = "1" }, enabled = enabled,
                    label = { Text(stringResource(R.string.pages_v2_start)) })
                FilterChip(selected = target == pageIds.lastIndex, onClick = { to = pageIds.size.toString() }, enabled = enabled,
                    label = { Text(stringResource(R.string.pages_v2_end)) })
            }
            Button(onClick = ::move, enabled = canMove, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(if (valid) stringResource(R.string.pages_v2_move_to, requireNotNull(target) + 1)
                    else stringResource(R.string.pages_move_action))
            }
        }
    }
}

@Composable
private fun PageDragHandle(
    position: Int,
    enabled: Boolean,
    accessibilityActions: List<CustomAccessibilityAction>,
) {
    val description = stringResource(R.string.pages_drag_handle, position)
    Box(
        Modifier
            .size(48.dp)
            .semantics {
                contentDescription = description
                customActions = accessibilityActions
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(R.drawable.ic_document_drag),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else .38f),
        )
    }
}

@Composable
private fun PageOrderRow(
    position: Int,
    selected: Boolean,
    thumbnail: @Composable () -> Unit,
    enabled: Boolean,
    canDelete: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    floating: Boolean = false,
    dragHandle: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onSelect,
            ),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.medium,
        border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
        shadowElevation = if (floating) 8.dp else 0.dp,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 88.dp)
                .padding(start = 4.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            dragHandle()
            Surface(
                modifier = Modifier
                    .width(40.dp)
                    .aspectRatio(595f / 842f),
                shape = MaterialTheme.shapes.extraSmall,
                color = MaterialTheme.colorScheme.surface,
            ) {
                thumbnail()
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(R.string.pages_page, position),
                    style = MaterialTheme.typography.titleSmall
                )
                if (selected) Text(
                    stringResource(R.string.pages_selected),
                    style = MaterialTheme.typography.labelMedium
                )
            }
            var showMenu by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { showMenu = true }, enabled = enabled) {
                    Icon(painterResource(R.drawable.more_vert_24px),
                        stringResource(R.string.pages_v2_options, position))
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.tools_delete_page, position)) },
                        leadingIcon = { Icon(painterResource(R.drawable.delete_24px), null) },
                        enabled = enabled && canDelete,
                        onClick = { showMenu = false; onDelete() },
                    )
                }
            }
        }
    }
}

@Preview(name = "Páginas claro", widthDp = 390, heightDp = 800, showBackground = true)
@Preview(
    name = "Páginas escuro",
    widthDp = 390,
    heightDp = 800,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Preview(name = "Páginas fonte ampliada", widthDp = 360, heightDp = 800, fontScale = 1.3f)
@Composable
private fun DocumentPagesSheetPreview() = DocumentPagesPreviewContent(PageReorderMode.DRAG)

@Preview(name = "Mover para posição", widthDp = 390, heightDp = 800, locale = "pt-rBR")
@Preview(name = "Mover compacto", widthDp = 320, heightDp = 740, locale = "pt-rBR", fontScale = 1.5f)
@Composable
private fun DocumentPagesDirectPreview() = DocumentPagesPreviewContent(PageReorderMode.DIRECT)

@Composable
private fun DocumentPagesPreviewContent(mode: PageReorderMode) {
    ScaniqoTheme {
        Surface {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    stringResource(R.string.tools_pages),
                    style = MaterialTheme.typography.titleLarge
                )
                DocumentPagesSheetContent(
                    state = DocumentToolsUiState(
                        workspace = PdfWorkspace(
                            documentId = 1, workspaceId = "preview", directoryUri = "preview",
                            pages = (1..12).map { PdfPageSpec("page-$it", "preview.pdf", it - 1) },
                            showPageNumbers = true,
                        ),
                        selectedPageId = "page-2",
                        previewUri = "preview.pdf",
                    ),
                    onEvent = {},
                    initialMode = mode,
                )
            }
        }
    }
}
