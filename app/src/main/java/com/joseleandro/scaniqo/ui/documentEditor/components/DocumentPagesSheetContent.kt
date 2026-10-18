package com.joseleandro.scaniqo.ui.documentEditor.components

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
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
import androidx.compose.ui.graphics.Color
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
) {
    val workspace = state.workspace ?: return
    val pageIds = remember(workspace.pages) { workspace.pages.map { it.id } }
    val listState = rememberLazyListState()
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
        if (!currentCanEdit || drag != null || currentPageIds.size < 2) return
        val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == pageId } ?: return
        drag = PageReorderDrag(currentPageIds, pageId, item.offset + item.size / 2f, item.size)
    }

    fun finishDrag() {
        val finished = drag ?: return
        val committed = finished.committedOrder()
        if (currentCanEdit && finished.initialOrder == currentPageIds && committed != null) {
            drag = null
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
            .pointerInput(workspace.workspaceId, state.canEdit, pageIds.size > 1) {
                // O detector fica no pai estável: reciclar um item não interrompe o gesto.
                if (state.canEdit && pageIds.size > 1) detectDragGesturesAfterLongPress(
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
            contentPadding = PaddingValues(top = 4.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            userScrollEnabled = drag == null,
        ) {
            // Chaves próprias mantêm os cabeçalhos fora dos cálculos de reordenação das páginas.
            item(key = PageListHeader.NUMBERING, contentType = PageListHeader.NUMBERING) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {

                    ListItem(
                        modifier = Modifier.height(intrinsicSize = IntrinsicSize.Min),
                        colors = ListItemDefaults.colors(
                            containerColor = Color.Transparent
                        ),
                        headlineContent = {
                            Text(
                                stringResource(R.string.pages_numbering),
                                style = MaterialTheme.typography.titleSmall,
                            )
                        },
                        supportingContent = {
                            Text(
                                modifier = Modifier.padding(top = 12.dp),
                                text = stringResource(R.string.pages_numbering_global_hint),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        trailingContent = {
                            Column(
                                modifier = Modifier.fillMaxHeight(),
                                verticalArrangement = Arrangement.Center
                            ) {
                                Switch(
                                    checked = workspace.showPageNumbers,
                                    onCheckedChange = { onEvent(DocumentToolsEvent.SetNumbering(it)) },
                                    enabled = controlsEnabled,
                                )
                            }
                        }
                    )
                }
            }
            item(key = PageListHeader.MOVE, contentType = PageListHeader.MOVE) {
                Column(Modifier.padding(bottom = 16.dp)) {
                    DirectPageMove(
                        pageIds = pageIds,
                        selectedPageId = state.selectedPageId,
                        enabled = controlsEnabled && pageIds.size > 1,
                        onMove = { id, index -> onEvent(DocumentToolsEvent.MovePageTo(id, index)) },
                    )
                }
            }
            item(key = PageListHeader.ORDER, contentType = PageListHeader.ORDER) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .padding(bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            pluralStringResource(
                                R.plurals.document_page_count,
                                pageIds.size,
                                pageIds.size
                            ),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            stringResource(if (pageIds.size == 1) R.string.pages_one_page else R.string.pages_drag_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    // O espaço reservado mantém a altura estável durante o arraste.
                    if (drag != null) {
                        IconButton(
                            onClick = { updateDrag(null) },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                painterResource(R.drawable.ic_toast_close),
                                stringResource(R.string.pages_drag_cancel)
                            )
                        }
                    } else {
                        Spacer(Modifier.size(48.dp))
                    }
                }
            }
            itemsIndexed(order, key = { _, id -> id }) { index, id ->
                val isDragged = drag?.itemId == id
                PageOrderRow(
                    position = index + 1,
                    selected = id == state.selectedPageId,
                    thumbnail = {
                        DocumentWorkspaceThumbnail(
                            state,
                            id,
                            onEvent,
                            Modifier.fillMaxSize()
                        )
                    },
                    enabled = controlsEnabled,
                    canDelete = pageIds.size > 1,
                    onSelect = { onEvent(DocumentToolsEvent.SelectPage(id)) },
                    onDelete = { onEvent(DocumentToolsEvent.RequestDeletePage(id)) },
                    modifier = Modifier.graphicsLayer { alpha = if (isDragged) 0f else 1f },
                    dragHandle = {
                        val moveUp = stringResource(R.string.pages_move_up)
                        val moveDown = stringResource(R.string.pages_move_down)
                        val actions = if (!controlsEnabled) emptyList() else buildList {
                            if (index > 0) add(CustomAccessibilityAction(moveUp) {
                                onEvent(DocumentToolsEvent.MovePageTo(id, index - 1)); true
                            })
                            if (index < order.lastIndex) add(CustomAccessibilityAction(moveDown) {
                                onEvent(DocumentToolsEvent.MovePageTo(id, index + 1)); true
                            })
                        }
                        PageDragHandle(
                            position = index + 1,
                            enabled = state.canEdit && pageIds.size > 1,
                            accessibilityActions = actions,
                        )
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
                    .clearAndSetSemantics {},
                floating = true,
                dragHandle = {
                    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                        Icon(painterResource(R.drawable.ic_document_drag), null)
                    }
                },
            )
        }
    }
}

private enum class PageListHeader { NUMBERING, MOVE, ORDER }

private fun LazyListState.pageBounds(): List<PageDragBounds> =
    layoutInfo.visibleItemsInfo.mapNotNull { item ->
        (item.key as? String)?.let { PageDragBounds(it, item.offset, item.size) }
    }

@Composable
private fun DirectPageMove(
    pageIds: List<String>,
    selectedPageId: String?,
    enabled: Boolean,
    onMove: (String, Int) -> Unit,
) {
    var from by rememberSaveable(pageIds, selectedPageId) {
        mutableStateOf((pageIds.indexOf(selectedPageId).coerceAtLeast(0) + 1).toString())
    }
    var to by rememberSaveable(pageIds, selectedPageId) { mutableStateOf("") }
    val fromIndex = from.toIntOrNull()?.minus(1)
    val toIndex = to.toIntOrNull()?.minus(1)
    val fromValid = fromIndex != null && fromIndex in pageIds.indices
    val toValid = toIndex != null && toIndex in pageIds.indices
    val canMove = enabled && fromValid && toValid && fromIndex != toIndex
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    fun move() {
        if (!canMove) return
        keyboard?.hide()
        focus.clearFocus()
        onMove(pageIds[requireNotNull(fromIndex)], requireNotNull(toIndex))
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            stringResource(R.string.pages_move_title),
            style = MaterialTheme.typography.titleMedium
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = from,
                onValueChange = { value ->
                    if (value.length <= 3 && value.all(Char::isDigit)) from = value
                },
                enabled = enabled,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 56.dp),
                singleLine = true,
                label = { Text(stringResource(R.string.pages_move_from)) },
                isError = from.isNotEmpty() && !fromValid,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Next
                ),
            )
            OutlinedTextField(
                value = to,
                onValueChange = { value ->
                    if (value.length <= 3 && value.all(Char::isDigit)) to = value
                },
                enabled = enabled,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 56.dp),
                singleLine = true,
                label = { Text(stringResource(R.string.pages_move_to)) },
                isError = to.isNotEmpty() && !toValid,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { move() }),
            )
            FilledIconButton(
                onClick = { move() },
                enabled = canMove,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    painterResource(R.drawable.arrow_back_24px),
                    stringResource(R.string.pages_move_action),
                    modifier = Modifier.graphicsLayer { rotationZ = 180f },
                )
            }
        }
        val invalidRange = (from.isNotEmpty() && !fromValid) || (to.isNotEmpty() && !toValid)
        val samePosition = fromValid && toValid && fromIndex == toIndex
        Text(
            when {
                invalidRange -> stringResource(R.string.pages_move_range, pageIds.size)
                samePosition -> stringResource(R.string.pages_move_same)
                else -> stringResource(R.string.pages_move_hint)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = if (invalidRange || samePosition) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
            IconButton(onClick = onDelete, enabled = enabled && canDelete) {
                Icon(
                    painterResource(R.drawable.delete_24px),
                    stringResource(R.string.tools_delete_page, position),
                    tint = if (enabled && canDelete) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(
                        alpha = .38f
                    ),
                )
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
private fun DocumentPagesSheetPreview() {
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
                )
            }
        }
    }
}
