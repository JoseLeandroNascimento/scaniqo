package com.joseleandro.scaniqo.ui.documentCommon.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.joseleandro.scaniqo.R
import com.joseleandro.scaniqo.domain.model.DocumentModel
import com.joseleandro.scaniqo.ui.ads.ToolAdActionContent
import com.joseleandro.scaniqo.ui.documentCommon.model.DocumentDragBounds
import com.joseleandro.scaniqo.ui.documentCommon.model.DocumentMergeItem
import com.joseleandro.scaniqo.ui.documentCommon.model.DocumentReorderDrag
import com.joseleandro.scaniqo.ui.documentCommon.model.toMergeItem
import com.joseleandro.scaniqo.ui.documentCommon.state.DocumentMergeSelectionState
import com.joseleandro.scaniqo.ui.documentCommon.util.documentDragScrollSpeed
import com.joseleandro.scaniqo.ui.documentCommon.util.moveDocumentOrder
import kotlin.math.roundToInt
import kotlinx.coroutines.isActive

private enum class MergeSelectionHeader { HERO, SOURCES, ORDER }

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
    actionShowsVideo: Boolean = false,
) {
    var showLibrary by rememberSaveable(currentDocumentId) { mutableStateOf(false) }
    var sourceMenu by remember { mutableStateOf(false) }
    val available = documents.filter { it.id != currentDocumentId }.distinctBy { it.id }
    val selected = selection.resolve(available)
    val selectedByKey = selected.associateBy { it.key }
    val selectedKeys = selected.map { it.key }
    val selectedIds = selected.mapNotNull { it.documentId }.toSet()
    val listState = rememberLazyListState()
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val haptics = LocalHapticFeedback.current
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

    LaunchedEffect(enabled) {
        if (!enabled) {
            showLibrary = false
            sourceMenu = false
        }
    }

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val compactHero = maxHeight < 360.dp
        Column(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.fillMaxWidth().weight(1f).clipToBounds()
                    .pointerInput(selection, enabled, selectedKeys.size > 1) {
                        if (enabled && selectedKeys.size > 1) detectDragGesturesAfterLongPress(
                            onDragStart = { pointer ->
                                val item = listState.layoutInfo.visibleItemsInfo.firstOrNull {
                                    it.key in latestKeys && pointer.y >= it.offset && pointer.y < it.offset + it.size
                                }
                                if (item != null && latestEnabled) {
                                    focus.clearFocus()
                                    keyboard?.hide()
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    drag = DocumentReorderDrag(
                                        latestKeys, item.key as String,
                                        item.offset + item.size / 2f, item.size,
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
                    state = listState, modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    userScrollEnabled = drag == null,
                ) {
                    item(key = MergeSelectionHeader.HERO) {
                        MergeSelectionHero(
                            count = selected.size,
                            modifier = Modifier.fillMaxWidth().padding(
                                top = if (compactHero) 0.dp else 8.dp,
                                bottom = if (compactHero) 4.dp else 16.dp,
                            ),
                            compact = compactHero,
                        )
                    }
                    item(key = MergeSelectionHeader.SOURCES) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                stringResource(if (order.isEmpty()) R.string.merge_motion_empty else R.string.merge_selection_order_title),
                                Modifier.weight(1f), style = MaterialTheme.typography.titleSmall,
                            )
                            Box {
                                TextButton(
                                    onClick = { sourceMenu = true }, enabled = controlsEnabled,
                                    modifier = Modifier.heightIn(min = 48.dp),
                                ) {
                                    Icon(painterResource(R.drawable.add_24px), null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(stringResource(R.string.merge_motion_add))
                                }
                                DropdownMenu(expanded = sourceMenu, onDismissRequest = { sourceMenu = false }) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.merge_motion_library)) },
                                        leadingIcon = { Icon(painterResource(R.drawable.ic_tag_document), null, Modifier.size(20.dp)) },
                                        enabled = controlsEnabled,
                                        onClick = { sourceMenu = false; showLibrary = true },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.merge_motion_device)) },
                                        leadingIcon = { Icon(painterResource(R.drawable.ic_document_import), null, Modifier.size(20.dp)) },
                                        enabled = controlsEnabled,
                                        onClick = { sourceMenu = false; onPickFiles() },
                                    )
                                }
                            }
                        }
                    }
                    if (order.size > 1) item(key = MergeSelectionHeader.ORDER) {
                        Text(stringResource(R.string.merge_selection_drag_hint),
                            Modifier.padding(bottom = 4.dp), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    itemsIndexed(order, key = { _, key -> key }) { index, key ->
                        selectedByKey[key]?.let { item ->
                            val isDragged = drag?.itemId == key
                            val motion = Modifier.animateItem(
                                fadeInSpec = tween(220),
                                placementSpec = if (drag == null) spring(stiffness = 450f) else null,
                                fadeOutSpec = tween(150),
                            )
                            DocumentMergeSelectionRow(
                                item = item, index = index, count = order.size,
                                enabled = controlsEnabled,
                                onMove = { selection.reorder(moveDocumentOrder(selectedKeys, key, it)) },
                                onRemove = { selection.remove(key) },
                                modifier = if (isDragged) {
                                    motion.graphicsLayer { alpha = 0f }.clearAndSetSemantics {}
                                } else motion,
                            )
                        }
                    }
                }
                drag?.let { moving ->
                    selectedByKey[moving.itemId]?.let { item ->
                        DocumentMergeSelectionRow(
                            item = item, index = moving.order.indexOf(moving.itemId),
                            count = moving.order.size, enabled = false, onMove = {}, onRemove = {},
                            modifier = Modifier
                                .offset { IntOffset(0, (moving.centerY - moving.height / 2f).roundToInt()) }
                                .height(with(density) { moving.height.toDp() })
                                .graphicsLayer {
                                    shadowElevation = 8.dp.toPx()
                                    scaleX = 1.015f
                                    scaleY = 1.015f
                                }
                                .clearAndSetSemantics {},
                        )
                    }
                }
            }
            Column(
                Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = {
                        val latest = selection.resolve(available)
                        if (controlsEnabled && latest.size >= minimumSelection && canConfirm) {
                            focus.clearFocus()
                            keyboard?.hide()
                            onConfirm(latest)
                        }
                    },
                    enabled = controlsEnabled && canConfirm && selected.size >= minimumSelection,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                ) {
                    val showVideo = actionShowsVideo && selected.size >= minimumSelection
                    if (!showVideo) {
                        Icon(painterResource(R.drawable.ic_tool_merge), null, Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                    }
                    ToolAdActionContent(
                        isVideo = showVideo,
                        label = when {
                            showVideo -> stringResource(R.string.tool_ad_watch_apply)
                            selected.size < minimumSelection ->
                                pluralStringResource(R.plurals.merge_selection_minimum, minimumSelection, minimumSelection)
                            else -> pluralStringResource(R.plurals.merge_app_confirm_count, selected.size, selected.size)
                        },
                    )
                }
                if (footerLabel != null) Text(
                    footerLabel, style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                )
            }
        }
    }
    if (showLibrary) {
        DocumentMergeLibrarySheet(
            documents = available, selectedIds = selectedIds, enabled = controlsEnabled,
            onSelect = { selection.toggle(it.toMergeItem()) },
            onDismiss = { showLibrary = false },
        )
    }
}

@Composable
private fun MergeSelectionHero(
    count: Int,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    if (compact) {
        Row(
            modifier, horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DocumentMergeIllustration(
                selectedCount = count, isWorking = false, modifier = Modifier.size(90.dp, 60.dp),
            )
            Spacer(Modifier.width(12.dp))
            MergeSelectionHeroCaption(count = count, compact = true)
        }
    } else {
        Column(
            modifier, horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DocumentMergeIllustration(
                selectedCount = count, isWorking = false, modifier = Modifier.size(230.dp, 132.dp),
            )
            MergeSelectionHeroCaption(count = count, compact = false)
        }
    }
}

@Composable
private fun MergeSelectionHeroCaption(
    count: Int,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier,
        horizontalAlignment = if (compact) Alignment.Start else Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 8.dp),
    ) {
        Text(
            stringResource(R.string.merge_selection_empty_title),
            style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
            textAlign = if (compact) TextAlign.Start else TextAlign.Center,
        )
        val announcement = when {
            count > 1 -> stringResource(R.string.merge_motion_count_description, count)
            count == 1 -> stringResource(R.string.merge_motion_one)
            else -> stringResource(R.string.merge_motion_hint)
        }
        AnimatedContent(
            targetState = count,
            modifier = Modifier.clearAndSetSemantics {
                contentDescription = announcement
                liveRegion = LiveRegionMode.Polite
            },
            label = "PDFs coming together",
        ) { selectedCount ->
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = if (selectedCount > 1) MaterialTheme.colorScheme.primaryContainer.copy(alpha = .5f)
                    else MaterialTheme.colorScheme.surface,
            ) {
                Text(
                    text = when {
                        selectedCount > 1 -> stringResource(R.string.merge_motion_count, selectedCount)
                        selectedCount == 1 -> stringResource(R.string.merge_motion_one)
                        else -> stringResource(R.string.merge_motion_hint)
                    },
                    modifier = Modifier.padding(
                        horizontal = if (compact) 8.dp else 12.dp,
                        vertical = if (compact) 4.dp else 6.dp,
                    ),
                    style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

private fun LazyListState.mergeBounds(keys: List<String>): List<DocumentDragBounds> =
    layoutInfo.visibleItemsInfo.mapNotNull { item ->
        (item.key as? String)?.takeIf { it in keys }?.let { DocumentDragBounds(it, item.offset, item.size) }
    }
