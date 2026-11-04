package com.joseleandro.scaniqo.ui.documentEditor.components

import com.joseleandro.scaniqo.core.resources.R as CoreResourcesR
import com.joseleandro.scaniqo.feature.editor.R

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joseleandro.scaniqo.ui.documentCommon.components.DocumentPreviewZoom
import com.joseleandro.scaniqo.ui.documentEditor.model.DocumentLayoutControl
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DocumentLayoutControlsSheet(
    control: DocumentLayoutControl,
    enabled: Boolean,
    canUndo: Boolean,
    canRedo: Boolean,
    onControlChange: (DocumentLayoutControl) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onControlsClosed: () -> Unit,
    previewKey: Any,
    onGestureStart: () -> Unit,
    onTransformStart: () -> Unit,
    preview: @Composable BoxScope.() -> Unit,
    controls: @Composable (compact: Boolean, shortHeight: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    navigation: (@Composable () -> Unit)? = null,
    initiallyExpanded: Boolean = false,
) {
    val density = LocalDensity.current
    val positionalThreshold = with(density) { 56.dp.toPx() }
    val velocityThreshold = with(density) { 125.dp.toPx() }
    val sheetState = rememberSaveable(
        density,
        saver = SheetState.Saver(
            skipPartiallyExpanded = true,
            positionalThreshold = { positionalThreshold },
            velocityThreshold = { velocityThreshold },
            confirmValueChange = { true },
            skipHiddenState = false,
        ),
    ) {
        SheetState(
            skipPartiallyExpanded = true,
            positionalThreshold = { positionalThreshold },
            velocityThreshold = { velocityThreshold },
            initialValue = if (initiallyExpanded) SheetValue.Expanded else SheetValue.Hidden,
        )
    }
    val scaffoldState = rememberBottomSheetScaffoldState(bottomSheetState = sheetState)
    val coroutineScope = rememberCoroutineScope()
    val currentOnClosed by rememberUpdatedState(onControlsClosed)
    val controlsOpen = sheetState.currentValue != SheetValue.Hidden ||
        sheetState.targetValue != SheetValue.Hidden
    LaunchedEffect(sheetState.currentValue) {
        if (sheetState.currentValue == SheetValue.Hidden) currentOnClosed()
    }
    BackHandler(controlsOpen) { coroutineScope.launch { sheetState.hide() } }
    BoxWithConstraints(modifier.fillMaxSize().clipToBounds()) {
        val compactHeight = maxHeight < 400.dp && maxWidth >= 600.dp
        val shortHeight = maxHeight < 600.dp
        val sheetMaxHeight = if (compactHeight) {
            (maxHeight * .65f).coerceAtLeast(240.dp).coerceAtMost(maxHeight)
        } else {
            (maxHeight * .6f).coerceAtMost(424.dp)
        }
        BottomSheetScaffold(
            scaffoldState = scaffoldState,
            sheetPeekHeight = 0.dp,
            sheetDragHandle = null,
            sheetContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            containerColor = MaterialTheme.colorScheme.surface,
            sheetContent = {
                Column(
                    Modifier.fillMaxWidth()
                        .heightIn(max = sheetMaxHeight)
                        .testTag("layout_controls_sheet"),
                ) {
                    LayoutControlsHeader(
                        enabled, canUndo, canRedo, onUndo, onRedo,
                        onClose = { coroutineScope.launch { sheetState.hide() } },
                    )
                    LayoutControlTabs(control, enabled, onControlChange, compact = compactHeight)
                    LayoutControlsBody(
                        compactHeight = compactHeight,
                        shortHeight = shortHeight,
                        controls = controls,
                        modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                    )
                }
            },
        ) {
            Column(
                Modifier.fillMaxSize().layout { measurable, constraints ->
                    val offset = runCatching { sheetState.requireOffset() }
                        .getOrDefault(constraints.maxHeight.toFloat())
                    val visibleSheet = (constraints.maxHeight - offset).roundToInt()
                        .coerceIn(0, constraints.maxHeight)
                    val availableHeight = constraints.maxHeight - visibleSheet
                    val content = measurable.measure(
                        constraints.copy(minHeight = availableHeight, maxHeight = availableHeight),
                    )
                    layout(constraints.maxWidth, constraints.maxHeight) { content.placeRelative(0, 0) }
                },
            ) {
                DocumentPreviewZoom(
                    Modifier.weight(1f).fillMaxWidth(),
                    contentKey = previewKey,
                    transformGesturesEnabled = control == DocumentLayoutControl.POSITION,
                    onGestureStart = onGestureStart,
                    onTransformStart = onTransformStart,
                    content = preview,
                )
                if (!controlsOpen || navigation != null) {
                    FlowRow(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        navigation?.invoke()
                        if (!controlsOpen) {
                            FilledTonalButton(
                                onClick = { coroutineScope.launch { sheetState.expand() } },
                                enabled = enabled,
                                shape = RoundedCornerShape(20.dp),
                                elevation = ButtonDefaults.filledTonalButtonElevation(defaultElevation = 2.dp),
                                contentPadding = PaddingValues(start = 8.dp, end = 20.dp, top = 6.dp, bottom = 6.dp),
                                modifier = Modifier.heightIn(min = 52.dp).testTag("layout_open_controls"),
                            ) {
                                Box(
                                    Modifier.size(36.dp).background(
                                        MaterialTheme.colorScheme.primary.copy(alpha = .1f),
                                        RoundedCornerShape(12.dp),
                                    ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(painterResource(CoreResourcesR.drawable.ic_tool_adjust), null, Modifier.size(20.dp))
                                }
                                Spacer(Modifier.width(10.dp))
                                Text(stringResource(R.string.layout_controls_open), style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LayoutControlsHeader(
    enabled: Boolean,
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp)) {
        Row(Modifier.align(Alignment.CenterStart), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            IconButton(onClick = onUndo, enabled = enabled && canUndo) {
                Icon(painterResource(CoreResourcesR.drawable.ic_document_undo), stringResource(R.string.viewer_undo_description))
            }
            IconButton(onClick = onRedo, enabled = enabled && canRedo) {
                Icon(painterResource(R.drawable.ic_document_redo), stringResource(R.string.viewer_redo_description))
            }
        }
        BottomSheetDefaults.DragHandle(Modifier.align(Alignment.Center))
        IconButton(
            onClick = onClose,
            modifier = Modifier.align(Alignment.CenterEnd).testTag("layout_close_controls"),
        ) {
            Icon(painterResource(CoreResourcesR.drawable.ic_toast_close), stringResource(R.string.tool_sheet_close))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LayoutControlTabs(
    control: DocumentLayoutControl,
    enabled: Boolean,
    onControlChange: (DocumentLayoutControl) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val tabs = listOf(
        DocumentLayoutControl.POSITION,
        DocumentLayoutControl.SIZE,
        DocumentLayoutControl.ROTATION,
    )
    PrimaryTabRow(
        selectedTabIndex = tabs.indexOf(control).coerceAtLeast(0),
        modifier = modifier.fillMaxWidth().testTag("layout_control_tabs"),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        divider = {},
    ) {
        tabs.forEach { option ->
            Tab(
                selected = control == option,
                enabled = enabled,
                onClick = { onControlChange(option) },
                modifier = Modifier.testTag("layout_control_${option.name}"),
            ) {
                val icon: @Composable () -> Unit = {
                    Icon(
                        painterResource(when (option) {
                            DocumentLayoutControl.POSITION -> CoreResourcesR.drawable.ic_layout_position
                            DocumentLayoutControl.ROTATION -> CoreResourcesR.drawable.ic_layout_rotate_right
                            else -> CoreResourcesR.drawable.ic_layout_size
                        }),
                        null,
                        Modifier.size(20.dp),
                    )
                }
                val label: @Composable () -> Unit = {
                    Text(
                        stringResource(when (option) {
                            DocumentLayoutControl.POSITION -> R.string.layout_interactive_position
                            DocumentLayoutControl.ROTATION -> R.string.layout_interactive_rotate
                            else -> R.string.tools_size_compact
                        }),
                        style = MaterialTheme.typography.labelLarge,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = 14.sp),
                    )
                }
                if (compact) {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        icon()
                        label()
                    }
                } else {
                    Column(
                        Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(horizontal = 4.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        icon()
                        label()
                    }
                }
            }
        }
    }
}

@Composable
private fun LayoutControlsBody(
    compactHeight: Boolean,
    shortHeight: Boolean,
    controls: @Composable (compact: Boolean, shortHeight: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = if (compactHeight) 8.dp else 16.dp),
    ) { controls(compactHeight, shortHeight) }
}
