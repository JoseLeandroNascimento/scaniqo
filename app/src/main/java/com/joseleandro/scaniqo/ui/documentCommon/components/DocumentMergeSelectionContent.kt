package com.joseleandro.scaniqo.ui.documentCommon.components

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.joseleandro.scaniqo.R
import com.joseleandro.scaniqo.domain.model.DocumentModel
import com.joseleandro.scaniqo.ui.documentCommon.model.DocumentDragBounds
import com.joseleandro.scaniqo.ui.documentCommon.model.DocumentMergeItem
import com.joseleandro.scaniqo.ui.documentCommon.model.DocumentReorderDrag
import com.joseleandro.scaniqo.ui.documentCommon.model.toMergeItem
import com.joseleandro.scaniqo.ui.documentCommon.state.DocumentMergeSelectionState
import com.joseleandro.scaniqo.ui.documentCommon.util.documentDragScrollSpeed
import com.joseleandro.scaniqo.ui.documentCommon.util.moveDocumentOrder
import kotlin.math.roundToInt
import kotlinx.coroutines.isActive

private enum class MergeSelectionHeader { SEARCH, ORDER, EMPTY, LIBRARY, LIBRARY_EMPTY }

/** Seleção e ordem são compartilhadas pela criação de PDFs e pela mesclagem no editor. */
@Composable
fun DocumentMergeSelectionContent(
    documents: List<DocumentModel>,
    selection: DocumentMergeSelectionState,
    currentDocumentId: Long? = null,
    enabled: Boolean = true,
    minimumSelection: Int = 1,
    canConfirm: Boolean = true,
    footerLabel: String? = null,
    modifier: Modifier = Modifier,
    onPickFiles: () -> Unit,
    onConfirm: (List<DocumentMergeItem>) -> Unit,
) {
    var query by rememberSaveable(currentDocumentId) { mutableStateOf("") }
    val available = documents.filter { it.id != currentDocumentId }.distinctBy { it.id }
    val selected = selection.resolve(available)
    val selectedByKey = selected.associateBy { it.key }
    val selectedKeys = selected.map { it.key }
    val selectedIds = selected.mapNotNull { it.documentId }.toSet()
    val unselected = available.filterNot { it.id in selectedIds }
    val matches = unselected.filter { it.name.contains(query.trim(), ignoreCase = true) }
    val listState = rememberLazyListState()
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val density = LocalDensity.current
    val edgeSize = with(density) { 56.dp.toPx() }
    val maxScrollSpeed = with(density) { 720.dp.toPx() }
    var drag by remember(selection) { mutableStateOf<DocumentReorderDrag?>(null) }
    val latestKeys by rememberUpdatedState(selectedKeys)
    val latestEnabled by rememberUpdatedState(enabled)
    val order = drag?.order ?: selectedKeys
    val controlsEnabled = enabled && drag == null

    fun updateDrag(updated: DocumentReorderDrag?) {
        val previousOrder = drag?.order ?: latestKeys
        val nextOrder = updated?.order ?: latestKeys
        if (previousOrder != nextOrder) {
            listState.requestScrollToItem(
                listState.firstVisibleItemIndex,
                listState.firstVisibleItemScrollOffset,
            )
        }
        drag = updated
    }

    fun finishDrag() {
        val finished = drag ?: return
        if (latestEnabled && finished.initialOrder == latestKeys) {
            finished.committedOrder()?.let(selection::reorder)
        }
        updateDrag(null)
    }

    LaunchedEffect(selectedKeys, enabled) {
        if (!enabled || drag?.initialOrder?.let { it != selectedKeys } == true) updateDrag(null)
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
            val speed = documentDragScrollSpeed(
                moving.centerY,
                info.viewportStartOffset.toFloat(),
                info.viewportEndOffset.toFloat(),
                edgeSize,
                maxScrollSpeed,
            )
            if (speed != 0f && listState.scrollBy(speed * seconds) != 0f) {
                updateDrag(drag?.retarget(listState.mergeBounds(latestKeys)))
            }
        }
    }

    Column(
        modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .clipToBounds()
                .pointerInput(selection, enabled, selectedKeys.size > 1) {
                    if (enabled && selectedKeys.size > 1) detectDragGesturesAfterLongPress(
                        onDragStart = { pointer ->
                            val item = listState.layoutInfo.visibleItemsInfo.firstOrNull {
                                it.key in latestKeys && pointer.y >= it.offset && pointer.y < it.offset + it.size
                            }
                            if (item != null && latestEnabled) {
                                focus.clearFocus()
                                keyboard?.hide()
                                drag = DocumentReorderDrag(
                                    latestKeys,
                                    item.key as String,
                                    item.offset + item.size / 2f,
                                    item.size,
                                )
                            }
                        },
                        onDragEnd = { finishDrag() },
                        onDragCancel = { updateDrag(null) },
                        onDrag = { change, amount ->
                            if (drag != null) {
                                change.consume()
                                updateDrag(drag?.moveBy(amount.y, listState.mergeBounds(latestKeys)))
                            }
                        },
                    )
                },
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                userScrollEnabled = drag == null,
            ) {
                if (order.isNotEmpty()) item(key = MergeSelectionHeader.ORDER) {
                    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.merge_selection_order_title), style = MaterialTheme.typography.titleMedium)
                        if (order.size > 1) Text(
                            stringResource(R.string.merge_selection_drag_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (order.isEmpty()) {
                    item(key = MergeSelectionHeader.EMPTY) {
                        DocumentMergeEmptySelection(enabled = controlsEnabled, onPickFiles = onPickFiles)
                    }
                }
                itemsIndexed(order, key = { _, key -> key }) { index, key ->
                    selectedByKey[key]?.let { item ->
                        val isDragged = drag?.itemId == key
                        DocumentMergeSelectionRow(
                            item = item,
                            index = index,
                            count = order.size,
                            enabled = controlsEnabled,
                            onMove = { selection.reorder(moveDocumentOrder(selectedKeys, key, it)) },
                            onRemove = { selection.remove(key) },
                            modifier = if (isDragged) {
                                Modifier.graphicsLayer { alpha = 0f }.clearAndSetSemantics {}
                            } else Modifier,
                        )
                    }
                }
                item(key = MergeSelectionHeader.LIBRARY) {
                    Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        HorizontalDivider()
                        Text(stringResource(R.string.merge_selection_library_title), style = MaterialTheme.typography.titleMedium)
                    }
                }
                item(key = MergeSelectionHeader.SEARCH) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text(stringResource(R.string.merge_app_search)) },
                        singleLine = true,
                        enabled = controlsEnabled,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            focus.clearFocus()
                            keyboard?.hide()
                        }),
                    )
                }
                if (matches.isEmpty()) {
                    item(key = MergeSelectionHeader.LIBRARY_EMPTY) {
                        Text(
                            stringResource(
                                when {
                                    available.isEmpty() -> R.string.merge_selection_library_empty
                                    unselected.isEmpty() -> R.string.merge_selection_all_added
                                    else -> R.string.merge_app_no_results
                                },
                            ),
                            Modifier.padding(vertical = 12.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(matches, key = { "available:" + it.id }) { document ->
                    DocumentMergeAvailableRow(
                        document = document,
                        enabled = controlsEnabled,
                        onSelect = { selection.toggle(document.toMergeItem()) },
                    )
                }
            }
            drag?.let { moving ->
                selectedByKey[moving.itemId]?.let { item ->
                    DocumentMergeSelectionRow(
                        item = item,
                        index = moving.order.indexOf(moving.itemId),
                        count = moving.order.size,
                        enabled = false,
                        onMove = {},
                        onRemove = {},
                        modifier = Modifier
                            .offset { IntOffset(0, (moving.centerY - moving.height / 2f).roundToInt()) }
                            .height(with(density) { moving.height.toDp() })
                            .graphicsLayer { shadowElevation = 8.dp.toPx() }
                            .clearAndSetSemantics {},
                    )
                }
            }
        }
        if (footerLabel != null) Text(
            footerLabel,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = {
                val latest = selection.resolve(available)
                if (latest.size >= minimumSelection && canConfirm) {
                    focus.clearFocus()
                    keyboard?.hide()
                    onConfirm(latest)
                }
            },
            enabled = controlsEnabled && canConfirm && selected.size >= minimumSelection,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        ) {
            Text(
                if (selected.size < minimumSelection) {
                    pluralStringResource(R.plurals.merge_selection_minimum, minimumSelection, minimumSelection)
                } else {
                    pluralStringResource(R.plurals.merge_app_confirm_count, selected.size, selected.size)
                },
            )
        }
    }
}

private fun LazyListState.mergeBounds(keys: List<String>): List<DocumentDragBounds> =
    layoutInfo.visibleItemsInfo.mapNotNull { item ->
        (item.key as? String)?.takeIf { it in keys }?.let { DocumentDragBounds(it, item.offset, item.size) }
    }
