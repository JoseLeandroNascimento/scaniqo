package com.joseleandro.scaniqo.ui.documentEditor.components

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joseleandro.scaniqo.R
import com.joseleandro.scaniqo.domain.model.PdfOverlay
import com.joseleandro.scaniqo.domain.model.PdfOverlayKind
import com.joseleandro.scaniqo.domain.model.PdfTextHorizontalAlignment
import com.joseleandro.scaniqo.domain.model.PdfTextVerticalAlignment
import com.joseleandro.scaniqo.ui.theme.ScaniqoTheme
import kotlin.math.abs
import kotlin.math.roundToInt

internal enum class FillMarkPanel { NONE, SIZE, COLOR, FORMAT, BOX, POSITION, SCOPE }

@Composable
internal fun FillMarkControls(
    active: Boolean,
    continuousInsertion: Boolean,
    mode: PdfOverlayKind,
    editingText: Boolean,
    editable: Boolean,
    draft: PdfOverlay?,
    panel: FillMarkPanel,
    canUndo: Boolean,
    canRedo: Boolean,
    onMode: (PdfOverlayKind) -> Unit,
    onPanel: (FillMarkPanel) -> Unit,
    onEditText: () -> Unit,
    onMore: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onUpdate: (PdfOverlay) -> Unit,
    onSelectColor: (Int) -> Unit,
    recentColors: List<Int>,
    numericSizeBuffer: String?,
    onStartNumericSize: () -> Unit,
    onNumericSizeChange: (String) -> Unit,
    onConfirmNumericSize: () -> Unit,
    onCancelNumericSize: () -> Unit,
    numericSizeError: Boolean,
    allPages: Boolean,
    pageCount: Int,
    onAllPages: (Boolean) -> Unit,
    errorText: String?,
    modifier: Modifier = Modifier,
    onCreateMode: (PdfOverlayKind) -> Unit = onMode,
    onStopInsertion: () -> Unit = {},
    textInsertionArmed: Boolean = false,
    showHistoryActions: Boolean = true,
) {
    val selectedText = active && !continuousInsertion && (draft?.kind in textKinds)
    val selectedMark = active && !continuousInsertion && (draft?.kind in binaryFillMarkKinds)
    val markContext = continuousInsertion || selectedMark
    val stopInContext = continuousInsertion && LocalConfiguration.current.fontScale >= 1.8f
    val surfaceColor by animateColorAsState(
        if (markContext || selectedText || textInsertionArmed)
            MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surface,
        animationSpec = tween(160),
        label = "fill toolbar surface",
    )
    Surface(modifier, color = surfaceColor, tonalElevation = 2.dp) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (showHistoryActions) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    IconButton(onClick = onUndo, enabled = editable && canUndo) {
                        Icon(painterResource(R.drawable.ic_document_undo), stringResource(R.string.viewer_undo_description))
                    }
                    IconButton(onClick = onRedo, enabled = editable && canRedo) {
                        Icon(painterResource(R.drawable.ic_document_redo), stringResource(R.string.viewer_redo_description))
                    }
                }
            }
            if (editingText && selectedText && draft != null) {
                FillMarkTextActions(
                    draft = draft, panel = panel, editable = editable, editingText = true,
                    onEditText = onEditText, onPanel = onPanel,
                )
            } else {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FillMarkToolButton(
                        label = stringResource(R.string.fill_v4_tool_text),
                        actionDescription = stringResource(R.string.fill_v4_add_text),
                        icon = R.drawable.ic_fill_text,
                        selected = textInsertionArmed,
                        enabled = editable,
                        onClick = { onCreateMode(PdfOverlayKind.TEXT) },
                        modifier = Modifier.weight(1f),
                    )
                    FillMarkToolButton(
                        label = stringResource(R.string.fill_v4_tool_mark),
                        actionDescription = stringResource(R.string.fill_v4_add_mark),
                        icon = R.drawable.ic_fill_mark,
                        selected = continuousInsertion,
                        enabled = editable,
                        onClick = {
                            onCreateMode(if (mode in binaryFillMarkKinds) mode else PdfOverlayKind.CHECKMARK)
                        },
                        modifier = Modifier.weight(1f),
                    )
                    if (continuousInsertion && !stopInContext) {
                        TextButton(
                            onClick = onStopInsertion,
                            enabled = editable,
                            modifier = Modifier.heightIn(min = 48.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp),
                        ) {
                            Text(stringResource(R.string.fill_v4_stop_inserting), maxLines = 1)
                        }
                    } else if (!continuousInsertion) {
                        IconButton(onClick = onMore, enabled = editable) {
                            Icon(painterResource(R.drawable.more_vert_24px), stringResource(R.string.fill_v4_more_actions))
                        }
                    }
                }
                if (markContext) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            stringResource(if (selectedMark) R.string.fill_v4_edit_mark else R.string.fill_v4_add_mark),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (stopInContext) TextButton(
                            onClick = onStopInsertion,
                            enabled = editable,
                            modifier = Modifier.heightIn(min = 48.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp),
                        ) {
                            Text(stringResource(R.string.fill_v4_stop_inserting), maxLines = 1)
                        }
                    }
                    FillMarkMarkActions(
                        kind = if (selectedMark) draft?.kind ?: mode else mode,
                        draft = draft,
                        panel = panel,
                        editable = editable,
                        onMode = onMode,
                        onPanel = onPanel,
                    )
                } else if (selectedText && draft != null) {
                    FillMarkTextActions(
                        draft = draft, panel = panel, editable = editable, editingText = false,
                        onEditText = onEditText, onPanel = onPanel,
                    )
                }
            }

            AnimatedVisibility(
                visible = draft != null && panel != FillMarkPanel.NONE,
                enter = fadeIn(tween(160)) + expandVertically(tween(160)),
                exit = fadeOut(tween(120)) + shrinkVertically(tween(120)),
            ) {
                val panelDraft = draft
                if (panelDraft != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                stringResource(
                                    when (panel) {
                                        FillMarkPanel.SIZE -> if (panelDraft.kind in textKinds)
                                            R.string.fill_v4_size else R.string.fill_mark_mark_size
                                        FillMarkPanel.COLOR -> R.string.fill_v4_color
                                        FillMarkPanel.FORMAT -> R.string.fill_v4_format
                                        FillMarkPanel.BOX -> R.string.fill_v3_text_box
                                        FillMarkPanel.POSITION -> R.string.fill_v3_precise_position
                                        FillMarkPanel.SCOPE -> R.string.fill_v3_repeat_pages
                                        FillMarkPanel.NONE -> R.string.fill_v4_size
                                    },
                                ),
                                style = MaterialTheme.typography.titleSmall,
                            )
                            TextButton(onClick = { onPanel(panel) }, modifier = Modifier.heightIn(min = 48.dp)) {
                                Icon(painterResource(R.drawable.ic_fill_collapse), null, Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.fill_v4_collapse))
                            }
                        }
                        val maxPanelHeight = (LocalConfiguration.current.screenHeightDp * .32f)
                            .dp.coerceIn(120.dp, 300.dp)
                        Box(Modifier.fillMaxWidth().heightIn(max = maxPanelHeight)) {
                            Crossfade(targetState = panel, animationSpec = tween(160), label = "fill property") { current ->
                                Column(
                                    Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    when (current) {
                                        FillMarkPanel.SIZE -> FillMarkSizePanel(
                                            panelDraft, editable, numericSizeBuffer, onStartNumericSize,
                                            onNumericSizeChange, onConfirmNumericSize, onCancelNumericSize,
                                            numericSizeError, onUpdate,
                                        )
                                        FillMarkPanel.COLOR -> FillMarkColorPanel(
                                            panelDraft, editable, recentColors, onSelectColor, onUpdate,
                                        )
                                        FillMarkPanel.FORMAT -> if (panelDraft.kind in textKinds)
                                            FillMarkFormatPanel(panelDraft, editable, onUpdate)
                                        FillMarkPanel.BOX -> if (panelDraft.kind in textKinds)
                                            FillMarkBoxPanel(panelDraft, editable, onUpdate)
                                        FillMarkPanel.POSITION -> FillMarkPositionPanel(panelDraft, editable, onUpdate)
                                        FillMarkPanel.SCOPE -> FillMarkScopePanel(
                                            allPages, pageCount, editable, onAllPages,
                                        )
                                        FillMarkPanel.NONE -> Unit
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (errorText != null) Text(
                errorText, style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun FillMarkToolButton(
    label: String,
    actionDescription: String,
    icon: Int,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.large,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainerHigh,
        border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = modifier.heightIn(min = 56.dp).semantics {
            this.selected = selected
            contentDescription = actionDescription
        },
    ) {
        if (LocalConfiguration.current.fontScale > 1.2f) {
            Column(
                Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(painterResource(icon), null, Modifier.size(20.dp))
                Text(
                    label,
                    style = if (LocalConfiguration.current.fontScale >= 1.8f)
                        MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        } else {
            Row(
                Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(painterResource(icon), null, Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun FillMarkTextActions(
    draft: PdfOverlay,
    panel: FillMarkPanel,
    editable: Boolean,
    editingText: Boolean,
    onEditText: () -> Unit,
    onPanel: (FillMarkPanel) -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = LocalConfiguration.current.locales[0]
    val size = stringResource(R.string.fill_v3_size_value, formatFillMarkSize(draft.fontSize, locale))
    val colorName = fillMarkColorName(draft.color)
    val sizeLabel = stringResource(R.string.fill_v4_size) + "\n" + size
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val stacked = maxWidth < 350.dp || LocalConfiguration.current.fontScale > 1.3f
        if (stacked) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FillMarkPropertyButton(
                        label = stringResource(if (editingText) R.string.fill_v3_hide_keyboard else R.string.fill_v4_edit_text),
                        description = stringResource(if (editingText) R.string.fill_v3_hide_keyboard else R.string.fill_v4_edit_text),
                        icon = if (editingText) R.drawable.ic_fill_collapse else R.drawable.edit_24px,
                        selected = false, enabled = editable, onClick = onEditText,
                        modifier = Modifier.weight(1f),
                    )
                    FillMarkPropertyButton(
                        label = sizeLabel,
                        description = stringResource(R.string.fill_v4_size) + ", " + size,
                        icon = R.drawable.ic_fill_text_size, selected = panel == FillMarkPanel.SIZE,
                        enabled = editable, onClick = { onPanel(FillMarkPanel.SIZE) },
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FillMarkPropertyButton(
                        label = stringResource(R.string.fill_v4_color) + "\n" + colorName,
                        description = stringResource(R.string.fill_v4_color) + ", " + colorName,
                        icon = R.drawable.ic_settings_palette, selected = panel == FillMarkPanel.COLOR,
                        enabled = editable, onClick = { onPanel(FillMarkPanel.COLOR) },
                        modifier = Modifier.weight(1f), swatch = Color(draft.color),
                    )
                    FillMarkPropertyButton(
                        label = stringResource(R.string.fill_v4_format),
                        description = stringResource(R.string.fill_v4_format),
                        icon = R.drawable.ic_fill_format, selected = panel == FillMarkPanel.FORMAT,
                        enabled = editable, onClick = { onPanel(FillMarkPanel.FORMAT) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FillMarkPropertyButton(
                    label = stringResource(if (editingText) R.string.fill_v3_hide_keyboard else R.string.fill_v4_edit_text),
                    description = stringResource(if (editingText) R.string.fill_v3_hide_keyboard else R.string.fill_v4_edit_text),
                    icon = if (editingText) R.drawable.ic_fill_collapse else R.drawable.edit_24px,
                    selected = false, enabled = editable, onClick = onEditText,
                    modifier = Modifier.weight(1f),
                )
                FillMarkPropertyButton(
                    label = sizeLabel,
                    description = stringResource(R.string.fill_v4_size) + ", " + size,
                    icon = R.drawable.ic_fill_text_size, selected = panel == FillMarkPanel.SIZE,
                    enabled = editable, onClick = { onPanel(FillMarkPanel.SIZE) },
                    modifier = Modifier.weight(1f),
                )
                FillMarkPropertyButton(
                    label = stringResource(R.string.fill_v4_color) + "\n" + colorName,
                    description = stringResource(R.string.fill_v4_color) + ", " + colorName,
                    icon = R.drawable.ic_settings_palette, selected = panel == FillMarkPanel.COLOR,
                    enabled = editable, onClick = { onPanel(FillMarkPanel.COLOR) },
                    modifier = Modifier.weight(1f), swatch = Color(draft.color),
                )
                FillMarkPropertyButton(
                    label = stringResource(R.string.fill_v4_format),
                    description = stringResource(R.string.fill_v4_format),
                    icon = R.drawable.ic_fill_format, selected = panel == FillMarkPanel.FORMAT,
                    enabled = editable, onClick = { onPanel(FillMarkPanel.FORMAT) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun FillMarkMarkActions(
    kind: PdfOverlayKind,
    draft: PdfOverlay?,
    panel: FillMarkPanel,
    editable: Boolean,
    onMode: (PdfOverlayKind) -> Unit,
    onPanel: (FillMarkPanel) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val stacked = maxWidth < 360.dp || LocalConfiguration.current.fontScale > 1.3f
        if (stacked) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FillMarkTypeSelector(kind, editable, onMode, Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FillMarkMarkPropertyButtons(
                        draft = draft, panel = panel, editable = editable,
                        onPanel = onPanel,
                    )
                }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FillMarkTypeSelector(kind, editable, onMode, Modifier.weight(2f))
                FillMarkMarkPropertyButtons(
                    draft = draft, panel = panel, editable = editable,
                    onPanel = onPanel,
                )
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.FillMarkMarkPropertyButtons(
    draft: PdfOverlay?,
    panel: FillMarkPanel,
    editable: Boolean,
    onPanel: (FillMarkPanel) -> Unit,
    modifier: Modifier = Modifier,
) {
    FillMarkPropertyButton(
        label = stringResource(R.string.fill_v4_size),
        description = stringResource(R.string.fill_v4_size),
        icon = R.drawable.ic_gesture_resize,
        selected = panel == FillMarkPanel.SIZE,
        enabled = editable && draft != null,
        onClick = { onPanel(FillMarkPanel.SIZE) },
        modifier = modifier.weight(1f),
    )
    FillMarkPropertyButton(
        label = stringResource(R.string.fill_v4_color),
        description = stringResource(R.string.fill_v4_color) + ", " +
            (draft?.let { fillMarkColorName(it.color) } ?: ""),
        icon = R.drawable.ic_settings_palette,
        selected = panel == FillMarkPanel.COLOR,
        enabled = editable && draft != null,
        onClick = { onPanel(FillMarkPanel.COLOR) },
        modifier = Modifier.weight(1f),
        swatch = draft?.let { Color(it.color) },
    )
}

@Composable
private fun FillMarkTypeSelector(
    kind: PdfOverlayKind,
    editable: Boolean,
    onMode: (PdfOverlayKind) -> Unit,
    modifier: Modifier = Modifier,
) {
    SingleChoiceSegmentedButtonRow(modifier) {
        listOf(PdfOverlayKind.CHECKMARK, PdfOverlayKind.CROSSMARK).forEachIndexed { index, choice ->
            SegmentedButton(
                selected = kind == choice,
                onClick = { onMode(choice) },
                enabled = editable,
                shape = SegmentedButtonDefaults.itemShape(index, 2),
                modifier = Modifier.heightIn(min = 48.dp),
                label = {
                    Text(
                        stringResource(if (choice == PdfOverlayKind.CHECKMARK)
                            R.string.fill_v4_mark_check else R.string.fill_v4_mark_cross),
                        maxLines = 1,
                    )
                },
                icon = {
                    Icon(
                        painterResource(if (choice == PdfOverlayKind.CHECKMARK)
                            R.drawable.ic_check_24 else R.drawable.ic_fill_crossmark),
                        null,
                        Modifier.size(18.dp),
                    )
                },
            )
        }
    }
}

@Composable
private fun FillMarkPropertyButton(
    label: String,
    description: String,
    icon: Int,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    swatch: Color? = null,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceContainer,
        border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = modifier.heightIn(min = 56.dp).semantics {
            this.selected = selected
            contentDescription = description
        },
    ) {
        Column(
            Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(contentAlignment = Alignment.BottomEnd) {
                Icon(painterResource(icon), null, Modifier.size(20.dp))
                if (swatch != null) Surface(
                    modifier = Modifier.size(9.dp),
                    shape = CircleShape,
                    color = swatch,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                ) {}
            }
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun FillMarkSizePanel(
    draft: PdfOverlay,
    editable: Boolean,
    numericSizeBuffer: String?,
    onStartNumericSize: () -> Unit,
    onNumericSizeChange: (String) -> Unit,
    onConfirmNumericSize: () -> Unit,
    onCancelNumericSize: () -> Unit,
    numericSizeError: Boolean,
    onUpdate: (PdfOverlay) -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    val sizeFieldFocus = remember(draft.id) { FocusRequester() }
    LaunchedEffect(numericSizeBuffer != null) {
        if (numericSizeBuffer != null) sizeFieldFocus.requestFocus()
    }
    if (draft.kind in textKinds) {
        val smaller = stringResource(R.string.fill_mark_font_smaller)
        val larger = stringResource(R.string.fill_mark_font_larger)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(
                onClick = { onUpdate(draft.withFillMarkFontSize(draft.fontSize - 1f)) },
                enabled = editable && draft.fontSize > 6f,
                modifier = Modifier.size(48.dp),
                contentPadding = PaddingValues(0.dp),
            ) {
                Icon(painterResource(R.drawable.ic_fill_decrease), smaller, Modifier.size(20.dp))
            }
            if (numericSizeBuffer == null) OutlinedButton(
                onClick = onStartNumericSize,
                enabled = editable,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.fill_v3_size_value, formatFillMarkSize(draft.fontSize, locale)))
            } else OutlinedTextField(
                value = numericSizeBuffer,
                onValueChange = onNumericSizeChange,
                enabled = editable,
                singleLine = true,
                isError = numericSizeError,
                modifier = Modifier.weight(1f).focusRequester(sizeFieldFocus),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onConfirmNumericSize() }),
                label = { Text(stringResource(R.string.fill_v3_points)) },
            )
            FilledTonalButton(
                onClick = { onUpdate(draft.withFillMarkFontSize(draft.fontSize + 1f)) },
                enabled = editable && draft.fontSize < 72f,
                modifier = Modifier.size(48.dp),
                contentPadding = PaddingValues(0.dp),
            ) {
                Icon(painterResource(R.drawable.add_24px), larger, Modifier.size(20.dp))
            }
        }
        if (numericSizeBuffer != null) {
            if (numericSizeError) Text(
                stringResource(R.string.fill_v3_size_error),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
            TextButton(onClick = onCancelNumericSize) { Text(stringResource(R.string.tools_cancel)) }
        }
    } else {
        val smaller = stringResource(R.string.fill_mark_mark_smaller)
        val larger = stringResource(R.string.fill_mark_mark_larger)
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.size(48.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painterResource(if (draft.kind == PdfOverlayKind.CHECKMARK)
                            R.drawable.ic_check_24 else R.drawable.ic_fill_crossmark),
                        null, Modifier.size(24.dp), tint = Color(draft.color),
                    )
                }
            }
            FilledTonalButton(
                onClick = { onUpdate(draft.scaledFillMarkAtCorner(2, .9f)) },
                enabled = editable && draft.scaledFillMarkAtCorner(2, .9f) != draft,
                modifier = Modifier.size(48.dp),
                contentPadding = PaddingValues(0.dp),
            ) {
                Icon(painterResource(R.drawable.ic_fill_decrease), smaller, Modifier.size(20.dp))
            }
            FilledTonalButton(
                onClick = { onUpdate(draft.scaledFillMarkAtCorner(2, 1.1f)) },
                enabled = editable && draft.scaledFillMarkAtCorner(2, 1.1f) != draft,
                modifier = Modifier.size(48.dp),
                contentPadding = PaddingValues(0.dp),
            ) {
                Icon(painterResource(R.drawable.add_24px), larger, Modifier.size(20.dp))
            }
        }
    }
}

private val fillMarkPaletteColors = listOf(
    0xFF202124.toInt() to R.string.overlay_black,
    0xFF1565C0.toInt() to R.string.overlay_blue,
    0xFFC62828.toInt() to R.string.overlay_red,
    0xFFFFCA28.toInt() to R.string.overlay_yellow,
    0xFF2E7D32.toInt() to R.string.fill_mark_green,
    0xFF6A1B9A.toInt() to R.string.fill_mark_purple,
    0xFF616161.toInt() to R.string.fill_mark_gray,
    0xFFFFFFFF.toInt() to R.string.fill_mark_white,
)

@Composable
private fun fillMarkColorName(color: Int): String {
    val name = fillMarkPaletteColors.firstOrNull { it.first == color }?.second
    return if (name != null) stringResource(name) else stringResource(R.string.overlay_color)
}

@Composable
private fun FillMarkColorPanel(
    draft: PdfOverlay,
    editable: Boolean,
    recentColors: List<Int>,
    onSelectColor: (Int) -> Unit,
    onUpdate: (PdfOverlay) -> Unit,
) {
    var opacityOpen by remember(draft.id) { mutableStateOf(false) }
    val uniqueRecent = recentColors.distinct().take(4)
    if (uniqueRecent.isNotEmpty()) {
        Text(stringResource(R.string.fill_mark_recent_colors), style = MaterialTheme.typography.labelLarge)
        FillMarkColorGrid(
            uniqueRecent.map { color -> color to fillMarkColorName(color) },
            draft.color, editable, onSelectColor,
        )
    }
    Text(stringResource(R.string.fill_mark_palette_colors), style = MaterialTheme.typography.labelLarge)
    FillMarkColorGrid(
        fillMarkPaletteColors.map { it.first to stringResource(it.second) },
        draft.color, editable, onSelectColor,
    )
    TextButton(onClick = { opacityOpen = !opacityOpen }, enabled = editable) {
        Text(stringResource(R.string.fill_v3_opacity, (draft.opacity * 100).roundToInt()))
    }
    if (opacityOpen) {
        BoxWithConstraints {
            val values = listOf(.25f, .5f, .75f, 1f)
            val columns = if (maxWidth < 350.dp || LocalConfiguration.current.fontScale > 1.3f) 2 else 4
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                values.chunked(columns).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { opacity ->
                            FilterChip(
                                selected = abs(draft.opacity - opacity) < .005f,
                                onClick = { onUpdate(draft.copy(opacity = opacity)) },
                                enabled = editable,
                                label = { Text(stringResource(R.string.fill_mark_opacity_value, (opacity * 100).roundToInt())) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FillMarkColorGrid(
    colors: List<Pair<Int, String>>,
    selectedColor: Int,
    editable: Boolean,
    onSelectColor: (Int) -> Unit,
) {
    colors.chunked(4).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            row.forEach { (color, label) ->
                val borderWidth by animateDpAsState(
                    if (selectedColor == color) 3.dp else 1.dp,
                    animationSpec = tween(160), label = "fill color",
                )
                Surface(
                    onClick = { onSelectColor(color) }, enabled = editable, shape = CircleShape,
                    color = Color(color),
                    border = BorderStroke(borderWidth, if (selectedColor == color)
                        MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                    modifier = Modifier.size(48.dp).semantics {
                        contentDescription = label
                        selected = selectedColor == color
                    },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (selectedColor == color) Icon(
                            painterResource(R.drawable.ic_check_24), null, Modifier.size(18.dp),
                            tint = if (color == 0xFFFFFFFF.toInt() || color == 0xFFFFCA28.toInt())
                                Color.Black else Color.White,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FillMarkFormatPanel(
    draft: PdfOverlay,
    editable: Boolean,
    onUpdate: (PdfOverlay) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FillMarkFormatToggle(
            label = stringResource(R.string.fill_v4_bold),
            icon = R.drawable.ic_fill_bold,
            selected = draft.bold,
            enabled = editable,
            onClick = { onUpdate(draft.copy(bold = !draft.bold)) },
            modifier = Modifier.weight(1f),
        )
        FillMarkFormatToggle(
            label = stringResource(R.string.fill_v4_italic),
            icon = R.drawable.ic_fill_italic,
            selected = draft.italic,
            enabled = editable,
            onClick = { onUpdate(draft.copy(italic = !draft.italic)) },
            modifier = Modifier.weight(1f),
        )
        FillMarkFormatToggle(
            label = stringResource(R.string.fill_v4_underline),
            icon = R.drawable.ic_fill_underline,
            selected = draft.underlined,
            enabled = editable,
            onClick = { onUpdate(draft.copy(underlined = !draft.underlined)) },
            modifier = Modifier.weight(1f),
        )
    }
    Text(stringResource(R.string.fill_text_horizontal), style = MaterialTheme.typography.labelLarge)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            Triple(PdfTextHorizontalAlignment.START, R.string.fill_text_start, R.drawable.ic_fill_text_start),
            Triple(PdfTextHorizontalAlignment.CENTER, R.string.fill_text_center, R.drawable.ic_fill_text_center),
            Triple(PdfTextHorizontalAlignment.END, R.string.fill_text_end, R.drawable.ic_fill_text_end),
        ).forEach { (alignment, label, icon) ->
            FillMarkFormatToggle(
                label = stringResource(label),
                icon = icon,
                selected = draft.textHorizontalAlignment == alignment,
                enabled = editable,
                onClick = { onUpdate(draft.copy(textHorizontalAlignment = alignment)) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun FillMarkFormatToggle(
    label: String,
    icon: Int,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceContainer,
        border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = modifier.heightIn(min = 56.dp).semantics { this.selected = selected },
    ) {
        Column(
            Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(painterResource(icon), null, Modifier.size(20.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 2,
                overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun FillMarkBoxPanel(draft: PdfOverlay, editable: Boolean, onUpdate: (PdfOverlay) -> Unit) {
    Text(stringResource(R.string.fill_v3_text_box_hint), style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.fill_text_auto_box), modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium)
        Switch(
            checked = draft.autoSizeText, enabled = editable,
            onCheckedChange = { onUpdate(draft.copy(autoSizeText = it, wrapText = true)) },
        )
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.fill_v3_wrap), modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium)
        Switch(
            checked = draft.wrapText, enabled = editable && !draft.autoSizeText,
            onCheckedChange = { onUpdate(draft.copy(wrapText = it)) },
        )
    }
    Text(stringResource(R.string.fill_text_vertical), style = MaterialTheme.typography.labelLarge)
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        listOf(
            PdfTextVerticalAlignment.TOP to R.string.fill_text_top,
            PdfTextVerticalAlignment.CENTER to R.string.fill_text_middle,
            PdfTextVerticalAlignment.BOTTOM to R.string.fill_text_bottom,
        ).forEachIndexed { index, (alignment, label) ->
            SegmentedButton(
                selected = draft.textVerticalAlignment == alignment,
                onClick = { onUpdate(draft.copy(textVerticalAlignment = alignment)) },
                enabled = editable, shape = SegmentedButtonDefaults.itemShape(index, 3),
                label = { Text(stringResource(label), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            )
        }
    }
}

@Composable
private fun FillMarkPositionPanel(draft: PdfOverlay, editable: Boolean, onUpdate: (PdfOverlay) -> Unit) {
    val leftLabel = stringResource(R.string.fill_mark_move_left)
    val upLabel = stringResource(R.string.fill_mark_move_up)
    val downLabel = stringResource(R.string.fill_mark_move_down)
    val rightLabel = stringResource(R.string.fill_mark_move_right)
    Text(stringResource(R.string.fill_v3_position_hint), style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        listOf(
            Triple("←", leftLabel, -.005f to 0f),
            Triple("↑", upLabel, 0f to -.005f),
            Triple("↓", downLabel, 0f to .005f),
            Triple("→", rightLabel, .005f to 0f),
        ).forEach { (glyph, label, delta) ->
            FilledTonalButton(
                onClick = { onUpdate(draft.movedTo(draft.x + delta.first, draft.y + delta.second)) },
                enabled = editable, modifier = Modifier.size(48.dp), contentPadding = PaddingValues(0.dp),
            ) { Text(glyph, Modifier.semantics { contentDescription = label }) }
        }
    }
    OutlinedButton(
        onClick = { onUpdate(draft.movedTo((1f - draft.width) / 2f, (1f - draft.height) / 2f)) },
        enabled = editable,
    ) { Text(stringResource(R.string.tools_align_center)) }
}

@Composable
private fun FillMarkScopePanel(
    allPages: Boolean,
    pageCount: Int,
    editable: Boolean,
    onAllPages: (Boolean) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = !allPages, onClick = { onAllPages(false) }, enabled = editable,
            label = { Text(stringResource(R.string.fill_mark_this_page)) },
        )
        FilterChip(
            selected = allPages, onClick = { onAllPages(true) }, enabled = editable,
            label = { Text(stringResource(R.string.fill_mark_every_page)) },
        )
    }
    Text(
        stringResource(R.string.fill_v3_page_count, if (allPages) pageCount else 1),
        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Preview(name = "Preencher — texto selecionado", widthDp = 390, locale = "pt-rBR", showBackground = true)
@Preview(name = "Preencher — fonte ampliada", widthDp = 320, fontScale = 1.3f, locale = "pt-rBR", showBackground = true)
@Preview(name = "Preencher — texto fonte 2x", widthDp = 320, fontScale = 2f,
    locale = "pt-rBR", showBackground = true)
@Composable
private fun FillMarkControlsPreview() {
    ScaniqoTheme {
        FillMarkControls(
            active = true, continuousInsertion = false, mode = PdfOverlayKind.TEXT,
            editingText = false, editable = true,
            draft = PdfOverlay(id = "preview", kind = PdfOverlayKind.TEXT, text = "Cliente"),
            panel = FillMarkPanel.SIZE, canUndo = true, canRedo = false,
            onMode = {}, onPanel = {}, onEditText = {}, onMore = {}, onUndo = {}, onRedo = {},
            onUpdate = {}, onSelectColor = {}, recentColors = emptyList(),
            numericSizeBuffer = null, onStartNumericSize = {}, onNumericSizeChange = {},
            onConfirmNumericSize = {}, onCancelNumericSize = {}, numericSizeError = false,
            allPages = false, pageCount = 1, onAllPages = {}, errorText = null,
            showHistoryActions = false,
        )
    }
}

@Preview(name = "Preencher — digitando compacto", widthDp = 320, heightDp = 360,
    fontScale = 1.5f, locale = "pt-rBR", showBackground = true)
@Composable
private fun FillMarkControlsCompactPreview() {
    ScaniqoTheme {
        FillMarkControls(
            active = true, continuousInsertion = false, mode = PdfOverlayKind.TEXT,
            editingText = true, editable = true,
            draft = PdfOverlay(id = "compact-preview", kind = PdfOverlayKind.TEXT,
                text = "Maria Silva", color = 0xFF1565C0.toInt()),
            panel = FillMarkPanel.NONE, canUndo = true, canRedo = false,
            onMode = {}, onPanel = {}, onEditText = {}, onMore = {}, onUndo = {}, onRedo = {},
            onUpdate = {}, onSelectColor = {}, recentColors = emptyList(),
            numericSizeBuffer = null, onStartNumericSize = {}, onNumericSizeChange = {},
            onConfirmNumericSize = {}, onCancelNumericSize = {}, numericSizeError = false,
            allPages = false, pageCount = 1, onAllPages = {}, errorText = null,
            showHistoryActions = false,
        )
    }
}

@Preview(name = "Preencher — marcar", widthDp = 390, locale = "pt-rBR", showBackground = true)
@Preview(name = "Preencher — marcar compacto", widthDp = 320, fontScale = 1.5f,
    locale = "pt-rBR", showBackground = true)
@Preview(name = "Preencher — marcar fonte 2x", widthDp = 320, fontScale = 2f,
    locale = "pt-rBR", showBackground = true)
@Preview(name = "Preencher — marcar escuro", widthDp = 390,
    locale = "pt-rBR", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Preview(name = "Preencher — marcar RTL", widthDp = 390,
    locale = "ar", showBackground = true)
@Composable
private fun FillMarkControlsMarkPreview() {
    ScaniqoTheme {
        FillMarkControls(
            active = false, continuousInsertion = true, mode = PdfOverlayKind.CROSSMARK,
            editingText = false, editable = true,
            draft = PdfOverlay(id = "mark-preview", kind = PdfOverlayKind.CROSSMARK),
            panel = FillMarkPanel.NONE, canUndo = true, canRedo = false,
            onMode = {}, onPanel = {}, onEditText = {}, onMore = {}, onUndo = {}, onRedo = {},
            onUpdate = {}, onSelectColor = {}, recentColors = emptyList(),
            numericSizeBuffer = null, onStartNumericSize = {}, onNumericSizeChange = {},
            onConfirmNumericSize = {}, onCancelNumericSize = {}, numericSizeError = false,
            allPages = false, pageCount = 1, onAllPages = {}, errorText = null,
            showHistoryActions = false,
        )
    }
}
