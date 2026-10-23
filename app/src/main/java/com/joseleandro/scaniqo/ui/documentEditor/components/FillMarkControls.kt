package com.joseleandro.scaniqo.ui.documentEditor.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import com.joseleandro.scaniqo.R
import com.joseleandro.scaniqo.domain.model.PdfOverlay
import com.joseleandro.scaniqo.domain.model.PdfOverlayKind
import kotlin.math.roundToInt

/** Ações do elemento ficam separadas do tamanho e da cor; a página mantém o foco. */
@Composable
internal fun FillMarkControls(
    active: Boolean,
    mode: PdfOverlayKind,
    textElement: Boolean,
    editingText: Boolean,
    editable: Boolean,
    canApply: Boolean,
    onMode: (PdfOverlayKind) -> Unit,
    onApply: () -> Unit,
    onCancel: () -> Unit,
    onProperties: () -> Unit,
    onEditText: () -> Unit,
    onMore: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    draft: PdfOverlay? = null,
    onUpdate: (PdfOverlay) -> Unit = {},
    canDuplicate: Boolean = false,
    onDuplicate: () -> Unit = {},
    onDelete: () -> Unit = {},
) {
    Surface(modifier, color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp) {
        val smallerLabel = stringResource(R.string.fill_mark_font_smaller)
        val largerLabel = stringResource(R.string.fill_mark_font_larger)
        Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            if (active) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(
                        Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (textElement && !editingText) TextButton(onClick = onEditText, enabled = editable) {
                            Icon(painterResource(R.drawable.edit_24px), null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.overlay_text))
                        }
                        if (textElement && draft != null) {
                            FillMarkTextAlignmentMenu(draft, editable, onUpdate)
                            IconButton(
                                onClick = { onUpdate(draft.withFillMarkFontSize(draft.fontSize - 1f)) },
                                enabled = editable && draft.fontSize > 6f,
                            ) {
                                Text("A−", style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.semantics { contentDescription = smallerLabel })
                            }
                            Text(stringResource(R.string.fill_mark_font_points, draft.fontSize.roundToInt()),
                                style = MaterialTheme.typography.labelMedium)
                            IconButton(
                                onClick = { onUpdate(draft.withFillMarkFontSize(draft.fontSize + 1f)) },
                                enabled = editable && draft.fontSize < 72f,
                            ) {
                                Text("A+", style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.semantics { contentDescription = largerLabel })
                            }
                        }
                        if (draft != null) FillMarkQuickColors(draft, editable, onUpdate)
                    }
                    if (canDuplicate) IconButton(onClick = onDuplicate, enabled = editable) {
                        Icon(painterResource(R.drawable.ic_fill_duplicate), stringResource(R.string.fill_mark_duplicate))
                    }
                    IconButton(onClick = onDelete, enabled = editable) {
                        Icon(painterResource(R.drawable.delete_24px), stringResource(R.string.overlay_delete),
                            tint = MaterialTheme.colorScheme.error)
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .6f))
                Row(
                    Modifier.fillMaxWidth().padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    IconButton(onClick = onCancel, enabled = editable) {
                        Icon(painterResource(R.drawable.ic_toast_close), stringResource(R.string.fill_mark_cancel))
                    }
                    OutlinedButton(onClick = onProperties, enabled = editable, modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp)) {
                        Text(stringResource(R.string.fill_mark_style), maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    }
                    Button(onClick = onApply, enabled = canApply, modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp)) {
                        Text(stringResource(R.string.fill_mark_apply), maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = onMore, enabled = editable) {
                        Icon(painterResource(R.drawable.ic_app_menu), stringResource(R.string.fill_inline_more))
                    }
                }
            } else {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = mode == PdfOverlayKind.TEXT,
                            onClick = { onMode(PdfOverlayKind.TEXT) }, enabled = editable,
                            label = { Text(stringResource(R.string.fill_inline_text_mode)) },
                            leadingIcon = { Icon(painterResource(R.drawable.edit_24px), null, Modifier.size(18.dp)) },
                        )
                        FilterChip(
                            selected = mode == PdfOverlayKind.CHECKMARK,
                            onClick = { onMode(PdfOverlayKind.CHECKMARK) }, enabled = editable,
                            label = { Text(stringResource(R.string.overlay_checkmark)) },
                            leadingIcon = { Icon(painterResource(R.drawable.ic_check_24), null, Modifier.size(18.dp)) },
                        )
                    }
                    IconButton(onClick = onMore, enabled = editable) {
                        Icon(painterResource(R.drawable.ic_app_menu), stringResource(R.string.fill_inline_more))
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(if (mode == PdfOverlayKind.CHECKMARK) R.string.fill_tap_to_mark else R.string.fill_tap_to_write),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
                    TextButton(onClick = onDone, enabled = editable) {
                        Text(stringResource(R.string.editor_tool_done), maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun FillMarkQuickColors(draft: PdfOverlay, editable: Boolean, onUpdate: (PdfOverlay) -> Unit) {
    listOf(
        0xFF202124.toInt() to R.string.overlay_black,
        0xFF1565C0.toInt() to R.string.overlay_blue,
        0xFFC62828.toInt() to R.string.overlay_red,
    ).forEach { (color, name) ->
        val label = stringResource(name)
        val isSelected = draft.color == color
        Surface(
            onClick = { onUpdate(draft.copy(color = color)) }, enabled = editable,
            shape = CircleShape, color = Color.Transparent,
            modifier = Modifier.size(48.dp).semantics { contentDescription = label; selected = isSelected },
        ) {
            Box(Modifier.padding(10.dp), contentAlignment = Alignment.Center) {
                Surface(
                    Modifier.fillMaxSize(), shape = CircleShape, color = Color(color),
                    border = BorderStroke(if (isSelected) 3.dp else 1.dp,
                        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                ) {
                    if (isSelected) Icon(painterResource(R.drawable.ic_check_24), null,
                        Modifier.padding(5.dp), tint = Color.White)
                }
            }
        }
    }
}

@Composable
internal fun FillMarkProperties(
    draft: PdfOverlay,
    allPages: Boolean,
    editable: Boolean,
    onUpdate: (PdfOverlay) -> Unit,
    onAllPages: (Boolean) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.heightIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.fill_inline_properties), style = MaterialTheme.typography.titleLarge)
            FillMarkStyle(draft, editable, onUpdate)
            HorizontalDivider()
            Text(stringResource(R.string.fill_text_page_position), style = MaterialTheme.typography.titleSmall)
            FillMarkPosition(draft, editable, onUpdate)
            HorizontalDivider()
            Text(stringResource(R.string.fill_mark_scope_hint), style = MaterialTheme.typography.titleSmall)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(!allPages, { onAllPages(false) }, enabled = editable,
                    label = { Text(stringResource(R.string.fill_mark_this_page)) })
                FilterChip(allPages, { onAllPages(true) }, enabled = editable,
                    label = { Text(stringResource(R.string.fill_mark_every_page)) })
            }
        }
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
            Text(stringResource(R.string.fill_inline_back_to_page))
        }
    }
}

@Composable
private fun FillMarkStyle(draft: PdfOverlay, editable: Boolean, onUpdate: (PdfOverlay) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (draft.kind in textKinds) {
            FillMarkTextAlignmentControls(draft, editable, onUpdate)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val smaller = stringResource(R.string.fill_mark_font_smaller)
                val larger = stringResource(R.string.fill_mark_font_larger)
                FilledTonalIconButton(onClick = { onUpdate(draft.withFillMarkFontSize(draft.fontSize - 1f)) },
                    enabled = editable && draft.fontSize > 6f, modifier = Modifier.semantics { contentDescription = smaller }) {
                    Text("−", style = MaterialTheme.typography.titleLarge)
                }
                Text(stringResource(R.string.fill_mark_font_points, draft.fontSize.roundToInt()), style = MaterialTheme.typography.labelLarge)
                FilledTonalIconButton(onClick = { onUpdate(draft.withFillMarkFontSize(draft.fontSize + 1f)) },
                    enabled = editable && draft.fontSize < 72f, modifier = Modifier.semantics { contentDescription = larger }) {
                    Text("+", style = MaterialTheme.typography.titleLarge)
                }
                listOf(12f, 16f, 24f).forEach { size ->
                    FilterChip(draft.fontSize == size, { onUpdate(draft.withFillMarkFontSize(size)) }, enabled = editable,
                        label = { Text(stringResource(R.string.fill_mark_font_points, size.toInt())) })
                }
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.overlay_color), style = MaterialTheme.typography.labelMedium)
            listOf(0xFF202124.toInt() to R.string.overlay_black, 0xFF1565C0.toInt() to R.string.overlay_blue,
                0xFFC62828.toInt() to R.string.overlay_red, 0xFFFFCA28.toInt() to R.string.overlay_yellow).forEach { (color, name) ->
                val label = stringResource(name)
                val isSelected = draft.color == color
                Surface(onClick = { onUpdate(draft.copy(color = color)) }, enabled = editable,
                    shape = CircleShape, color = Color.Transparent,
                    modifier = Modifier.size(48.dp).semantics { contentDescription = label; selected = isSelected }) {
                    Box(Modifier.padding(9.dp), contentAlignment = Alignment.Center) {
                        Surface(Modifier.fillMaxSize(), shape = CircleShape, color = Color(color),
                            border = BorderStroke(if (isSelected) 3.dp else 1.dp,
                                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)) {
                            if (isSelected) Icon(painterResource(R.drawable.ic_check_24), null,
                                Modifier.padding(5.dp), tint = if (name == R.string.overlay_yellow) Color.Black else Color.White)
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.fill_mark_opacity_label), style = MaterialTheme.typography.labelMedium)
            listOf(.25f, .5f, .75f, 1f).forEach { opacity ->
                FilterChip(selected = kotlin.math.abs(draft.opacity - opacity) < .005f,
                    onClick = { onUpdate(draft.copy(opacity = opacity)) }, enabled = editable,
                    label = { Text(stringResource(R.string.fill_mark_opacity_value, (opacity * 100).roundToInt())) })
            }
        }
    }
}

@Composable
private fun FillMarkPosition(draft: PdfOverlay, editable: Boolean, onUpdate: (PdfOverlay) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(
                Triple(R.string.fill_mark_move_left, 0f, -1 to 0),
                Triple(R.string.fill_mark_move_up, 90f, 0 to -1),
                Triple(R.string.fill_mark_move_down, 270f, 0 to 1),
                Triple(R.string.fill_mark_move_right, 180f, 1 to 0),
            ).forEach { (label, rotation, delta) ->
                FilledTonalIconButton(onClick = { onUpdate(draft.movedTo(draft.x + delta.first * .005f, draft.y + delta.second * .005f)) },
                    enabled = editable) {
                    Icon(painterResource(R.drawable.ic_physical_arrow_left), stringResource(label), Modifier.rotate(rotation))
                }
            }
            OutlinedIconButton(onClick = { onUpdate(draft.movedTo((1f - draft.width) / 2f, (1f - draft.height) / 2f)) },
                enabled = editable) {
                Icon(painterResource(R.drawable.ic_layout_align_center), stringResource(R.string.tools_align_center))
            }
        }
    }
}
