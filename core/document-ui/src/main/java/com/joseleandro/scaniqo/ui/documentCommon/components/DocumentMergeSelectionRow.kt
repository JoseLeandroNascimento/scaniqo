package com.joseleandro.scaniqo.ui.documentCommon.components

import com.joseleandro.scaniqo.core.documentui.R
import com.joseleandro.scaniqo.core.resources.R as CoreResourcesR

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.joseleandro.scaniqo.core.localization.bidiArgument
import com.joseleandro.scaniqo.domain.model.DocumentModel
import com.joseleandro.scaniqo.ui.documentCommon.model.DocumentMergeItem

@Composable
internal fun DocumentMergeSelectionRow(
    item: DocumentMergeItem,
    index: Int,
    count: Int,
    enabled: Boolean,
    onMove: (Int) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    document: DocumentModel? = null,
    floating: Boolean = false,
) {
    val up = stringResource(R.string.merge_selection_move_up, bidiArgument(item.name))
    val down = stringResource(R.string.merge_selection_move_down, bidiArgument(item.name))
    val remove = stringResource(CoreResourcesR.string.merge_selection_remove, bidiArgument(item.name))
    val actions = if (!enabled) emptyList() else buildList {
        if (index > 0) add(CustomAccessibilityAction(up) { onMove(index - 1); true })
        if (index < count - 1) add(CustomAccessibilityAction(down) { onMove(index + 1); true })
        if (!item.isCurrentDocument) add(CustomAccessibilityAction(remove) { onRemove(); true })
    }
    var menuExpanded by remember(item.key) { mutableStateOf(false) }
    LaunchedEffect(enabled) { if (!enabled) menuExpanded = false }
    val pages = pdfSelectionMetadata(item.pageCount, document?.sizeBytes)
    PdfSelectionCard(
        name = item.name,
        metadata = if (item.isCurrentDocument) stringResource(CoreResourcesR.string.merge_selection_current, pages) else pages,
        thumbnail = { DocumentMergeThumbnail(item, it, document) },
        modifier = modifier.semantics { customActions = actions },
        selected = floating,
        leading = {
            Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                Text("${index + 1}", style = MaterialTheme.typography.labelLarge.copy(textDirection = TextDirection.Ltr),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        trailing = {
            Box {
                IconButton(onClick = { menuExpanded = true }, enabled = enabled) {
                    Icon(
                        painterResource(CoreResourcesR.drawable.more_vert_24px),
                        stringResource(CoreResourcesR.string.document_actions, bidiArgument(item.name)),
                        Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.merge_move_up)) },
                        leadingIcon = { Icon(painterResource(CoreResourcesR.drawable.ic_document_move_up), null, Modifier.size(18.dp)) },
                        enabled = enabled && index > 0,
                        onClick = { menuExpanded = false; onMove(index - 1) },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.merge_move_down)) },
                        leadingIcon = { Icon(painterResource(CoreResourcesR.drawable.ic_document_move_down), null, Modifier.size(18.dp)) },
                        enabled = enabled && index < count - 1,
                        onClick = { menuExpanded = false; onMove(index + 1) },
                    )
                    if (!item.isCurrentDocument) {
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.merge_remove)) },
                            enabled = enabled,
                            leadingIcon = { Icon(painterResource(CoreResourcesR.drawable.ic_toast_close), null, Modifier.size(18.dp)) },
                            onClick = { menuExpanded = false; onRemove() },
                        )
                    }
                }
            }
        },
    )
}

@Composable
internal fun DocumentMergeAvailableRow(
    document: DocumentModel,
    enabled: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
) {
    PdfSelectionCard(
        name = document.name,
        metadata = pdfSelectionMetadata(document.pageCount, document.sizeBytes),
        thumbnail = { DocumentThumbnail(document, it) },
        modifier = modifier.toggleable(
            value = selected, enabled = enabled, role = Role.Checkbox, onValueChange = { onSelect() },
        ),
        selected = selected,
        trailing = { Checkbox(checked = selected, onCheckedChange = null, enabled = enabled) },
    )
}

@Composable
fun DocumentMergeThumbnail(
    item: DocumentMergeItem,
    modifier: Modifier = Modifier,
    document: DocumentModel? = null,
) {
    when {
        document != null -> DocumentThumbnail(document, modifier)
        item.protectionKeyId != null -> Box(modifier, contentAlignment = Alignment.Center) {
            Icon(painterResource(CoreResourcesR.drawable.ic_document_lock),
                stringResource(CoreResourcesR.string.merge_app_protected),
                Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
        }
        else -> DocumentPageThumbnail(item.uri, 0, modifier)
    }
}
