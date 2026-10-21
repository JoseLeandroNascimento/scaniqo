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

/** A barra ocupa uma única linha; nenhum campo de texto compete com a página. */
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
) {
    Surface(modifier, color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (active) {
                IconButton(onClick = onCancel, enabled = editable) {
                    Icon(painterResource(R.drawable.ic_toast_close), stringResource(R.string.fill_mark_cancel))
                }
                if (textElement) IconButton(onClick = onEditText, enabled = editable) {
                    Icon(painterResource(if (editingText) R.drawable.ic_check_24 else R.drawable.ic_tool_fill_page),
                        stringResource(if (editingText) R.string.fill_inline_hide_keyboard else R.string.fill_inline_edit_text))
                }
                IconButton(onClick = onProperties, enabled = editable) {
                    Icon(painterResource(R.drawable.ic_tool_adjust), stringResource(R.string.fill_inline_properties))
                }
                IconButton(onClick = onMore, enabled = editable) {
                    Icon(painterResource(R.drawable.ic_app_menu), stringResource(R.string.fill_inline_more))
                }
                Button(onClick = onApply, enabled = canApply, modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)) {
                    Text(stringResource(R.string.fill_mark_apply), maxLines = 1)
                }
            } else {
                FilledTonalIconToggleButton(checked = mode == PdfOverlayKind.TEXT,
                    onCheckedChange = { onMode(PdfOverlayKind.TEXT) }, enabled = editable) {
                    Icon(painterResource(R.drawable.ic_tool_fill_page), stringResource(R.string.fill_inline_text_mode))
                }
                FilledTonalIconToggleButton(checked = mode == PdfOverlayKind.CHECKMARK,
                    onCheckedChange = { onMode(PdfOverlayKind.CHECKMARK) }, enabled = editable) {
                    Icon(painterResource(R.drawable.ic_check_24), stringResource(R.string.overlay_checkmark))
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onMore, enabled = editable) {
                    Icon(painterResource(R.drawable.ic_app_menu), stringResource(R.string.fill_inline_more))
                }
                TextButton(onClick = onDone, enabled = editable) {
                    Text(stringResource(R.string.editor_tool_done), maxLines = 1)
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
            Text(stringResource(R.string.fill_mark_position), style = MaterialTheme.typography.titleSmall)
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
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val smaller = stringResource(R.string.fill_mark_font_smaller)
                val larger = stringResource(R.string.fill_mark_font_larger)
                FilledTonalIconButton(onClick = { onUpdate(draft.copy(fontSize = (draft.fontSize - 1f).coerceAtLeast(6f))) },
                    enabled = editable && draft.fontSize > 6f, modifier = Modifier.semantics { contentDescription = smaller }) {
                    Text("−", style = MaterialTheme.typography.titleLarge)
                }
                Text(stringResource(R.string.fill_mark_font_points, draft.fontSize.roundToInt()), style = MaterialTheme.typography.labelLarge)
                FilledTonalIconButton(onClick = { onUpdate(draft.copy(fontSize = (draft.fontSize + 1f).coerceAtMost(72f))) },
                    enabled = editable && draft.fontSize < 72f, modifier = Modifier.semantics { contentDescription = larger }) {
                    Text("+", style = MaterialTheme.typography.titleLarge)
                }
                listOf(12f, 16f, 24f).forEach { size ->
                    FilterChip(draft.fontSize == size, { onUpdate(draft.copy(fontSize = size)) }, enabled = editable,
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
        Text(stringResource(R.string.fill_mark_resize), style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
