package com.joseleandro.scaniqo.ui.documentEditor.components

import com.joseleandro.scaniqo.core.resources.R as CoreResourcesR
import com.joseleandro.scaniqo.feature.editor.R

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joseleandro.scaniqo.domain.model.PdfOverlay
import com.joseleandro.scaniqo.domain.model.PdfOverlayKind
import com.joseleandro.scaniqo.ui.documentCommon.components.DocumentSelectionToolbar
import com.joseleandro.scaniqo.ui.documentCommon.components.DocumentSelectionToolbarAction
import com.joseleandro.scaniqo.ui.theme.ScaniqoTheme

internal enum class FillMarkProperty { SIZE, COLOR, OPACITY, STYLE, ALIGNMENT, BOX, TYPE, SCOPE, TRANSFORM }

@Composable
internal fun FillMarkContextToolbar(
    draft: PdfOverlay,
    enabled: Boolean,
    onEditText: () -> Unit,
    onProperty: (FillMarkProperty) -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onSaveValue: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
    canSaveValue: Boolean = true,
    canReset: Boolean = true,
    canDuplicate: Boolean = true,
    pageCount: Int = 1,
) {
    val isText = draft.kind in textKinds
    var expanded by remember(draft.id) { mutableStateOf(false) }
    DocumentSelectionToolbar(
        description = stringResource(R.string.fill_property_actions),
        modifier = modifier.testTag("fill_context_toolbar"),
    ) {
        DocumentSelectionToolbarAction(
            label = stringResource(if (isText) R.string.fill_v4_edit_text else R.string.fill_property_type),
            icon = if (isText) CoreResourcesR.drawable.edit_24px else R.drawable.ic_fill_mark,
            enabled = enabled,
            onClick = { if (isText) onEditText() else onProperty(FillMarkProperty.TYPE) },
            primary = true,
            modifier = Modifier.testTag(if (isText) "fill_action_edit" else "fill_action_type"),
        )
        DocumentSelectionToolbarAction(
            label = stringResource(R.string.fill_v4_size),
            icon = if (isText) R.drawable.ic_fill_text_size else CoreResourcesR.drawable.ic_layout_size,
            enabled = enabled,
            onClick = { onProperty(FillMarkProperty.SIZE) },
            modifier = Modifier.testTag("fill_action_size"),
        )
        DocumentSelectionToolbarAction(
            label = stringResource(R.string.fill_v4_color),
            icon = CoreResourcesR.drawable.ic_settings_palette,
            enabled = enabled,
            onClick = { onProperty(FillMarkProperty.COLOR) },
            swatch = Color(draft.color),
            modifier = Modifier.testTag("fill_action_color"),
        )
        Box {
            DocumentSelectionToolbarAction(
                label = stringResource(R.string.fill_v4_more_actions),
                icon = CoreResourcesR.drawable.more_vert_24px,
                enabled = enabled,
                onClick = { expanded = true },
                modifier = Modifier.testTag("fill_action_more"),
            )
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.testTag("fill_context_menu"),
            ) {
                fun run(action: () -> Unit) {
                    expanded = false
                    action()
                }
                if (isText) {
                    FillContextMenuItem(R.string.fill_property_style, R.drawable.ic_fill_bold, enabled,
                        onClick = { run { onProperty(FillMarkProperty.STYLE) } }, tag = "fill_action_style")
                    FillContextMenuItem(R.string.fill_text_align_action, R.drawable.ic_fill_text_center, enabled,
                        onClick = { run { onProperty(FillMarkProperty.ALIGNMENT) } }, tag = "fill_action_alignment")
                    FillContextMenuItem(R.string.fill_v3_text_box, CoreResourcesR.drawable.ic_fill_text, enabled,
                        onClick = { run { onProperty(FillMarkProperty.BOX) } }, tag = "fill_action_box")
                }
                FillContextMenuItem(R.string.fill_property_opacity, R.drawable.ic_tool_watermark, enabled,
                    onClick = { run { onProperty(FillMarkProperty.OPACITY) } }, tag = "fill_action_opacity")
                FillContextMenuItem(R.string.fill_property_transform, CoreResourcesR.drawable.ic_layout_position, enabled,
                    onClick = { run { onProperty(FillMarkProperty.TRANSFORM) } }, tag = "fill_action_transform")
                if (pageCount > 1) FillContextMenuItem(R.string.fill_v3_repeat_pages, R.drawable.ic_fill_duplicate, enabled,
                    onClick = { run { onProperty(FillMarkProperty.SCOPE) } }, tag = "fill_action_scope")
                FillContextMenuItem(R.string.fill_v4_duplicate, R.drawable.ic_fill_duplicate, enabled && canDuplicate,
                    onClick = { run(onDuplicate) }, tag = "fill_action_duplicate")
                if (isText && canSaveValue) FillContextMenuItem(R.string.overlay_save_value, CoreResourcesR.drawable.ic_document_download,
                    enabled, onClick = { run(onSaveValue) }, tag = "fill_action_save")
                FillContextMenuItem(R.string.fill_property_revert, CoreResourcesR.drawable.ic_document_undo,
                    enabled && canReset, onClick = { run(onReset) }, tag = "fill_action_revert")
                FillContextMenuItem(R.string.fill_v4_delete, CoreResourcesR.drawable.delete_24px, enabled,
                    onClick = { run(onDelete) }, tag = "fill_action_delete", destructive = true)
            }
        }
    }
}

@Composable
private fun FillContextMenuItem(
    label: Int,
    icon: Int,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tag: String = "",
    destructive: Boolean = false,
) {
    DropdownMenuItem(
        text = { Text(stringResource(label)) },
        leadingIcon = { Icon(painterResource(icon), null) },
        enabled = enabled,
        onClick = onClick,
        modifier = modifier.testTag(tag),
        colors = if (destructive) MenuDefaults.itemColors(
            textColor = MaterialTheme.colorScheme.error,
            leadingIconColor = MaterialTheme.colorScheme.error,
        ) else MenuDefaults.itemColors(),
    )
}

@Preview(name = "Ações do texto", widthDp = 280, heightDp = 88, locale = "pt-rBR")
@Preview(name = "Ações escuras", widthDp = 280, heightDp = 88, locale = "pt-rBR", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Ações com fonte ampliada", widthDp = 260, heightDp = 88, fontScale = 2f, locale = "pt-rBR")
@Composable
private fun FillMarkContextToolbarPreview() {
    ScaniqoTheme {
        Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
            Box(Modifier.padding(16.dp)) {
                FillMarkContextToolbar(
                    draft = PdfOverlay("preview", PdfOverlayKind.TEXT, text = "Maria Silva"),
                    enabled = true,
                    onEditText = {}, onProperty = {}, onDuplicate = {}, onDelete = {},
                    onSaveValue = {}, onReset = {},
                )
            }
        }
    }
}
