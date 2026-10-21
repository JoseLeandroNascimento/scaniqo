package com.joseleandro.scaniqo.ui.documentCommon.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.joseleandro.scaniqo.R
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
) {
    val up = stringResource(R.string.merge_selection_move_up, bidiArgument(item.name))
    val down = stringResource(R.string.merge_selection_move_down, bidiArgument(item.name))
    val remove = stringResource(R.string.merge_selection_remove, bidiArgument(item.name))
    val actions = if (!enabled) emptyList() else buildList {
        if (index > 0) add(CustomAccessibilityAction(up) { onMove(index - 1); true })
        if (index < count - 1) add(CustomAccessibilityAction(down) { onMove(index + 1); true })
        if (!item.isCurrentDocument) add(CustomAccessibilityAction(remove) { onRemove(); true })
    }
    var menuExpanded by remember(item.key) { mutableStateOf(false) }
    LaunchedEffect(enabled) { if (!enabled) menuExpanded = false }
    Surface(
        modifier = modifier.fillMaxWidth().semantics { customActions = actions },
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 76.dp).padding(start = 4.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box {
                IconButton(onClick = { menuExpanded = true }, enabled = enabled) {
                    Icon(
                        painterResource(R.drawable.ic_document_drag),
                        stringResource(R.string.merge_selection_drag_handle, bidiArgument(item.name), index + 1, count),
                    )
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.merge_move_up)) },
                        enabled = index > 0,
                        onClick = { menuExpanded = false; onMove(index - 1) },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.merge_move_down)) },
                        enabled = index < count - 1,
                        onClick = { menuExpanded = false; onMove(index + 1) },
                    )
                }
            }
            Text("${index + 1}", style = MaterialTheme.typography.titleMedium.copy(textDirection = TextDirection.Ltr))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    item.name,
                    style = MaterialTheme.typography.titleSmall.copy(textDirection = TextDirection.ContentOrLtr),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val pages = item.pageCount?.let { pluralStringResource(R.plurals.document_page_count, it, it) }
                    ?: stringResource(R.string.merge_selection_external)
                Text(
                    if (item.isCurrentDocument) stringResource(R.string.merge_selection_current, pages) else pages,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (item.protectionKeyId != null) {
                Icon(
                    painterResource(R.drawable.ic_document_lock),
                    stringResource(R.string.merge_app_protected),
                    Modifier.size(20.dp),
                )
            }
            if (!item.isCurrentDocument) IconButton(onClick = onRemove, enabled = enabled) {
                Icon(
                    painterResource(R.drawable.delete_24px),
                    contentDescription = remove,
                    tint = if (enabled) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = .38f),
                )
            }
        }
    }
}

@Composable
internal fun DocumentMergeAvailableRow(
    document: DocumentModel,
    enabled: Boolean,
    onSelect: () -> Unit,
) {
    Surface(
        onClick = onSelect,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Checkbox(checked = false, onCheckedChange = null, enabled = enabled)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(document.name, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.ContentOrLtr))
                Text(
                    pluralStringResource(R.plurals.document_page_count, document.pageCount, document.pageCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (document.isProtected) {
                Icon(
                    painterResource(R.drawable.ic_document_lock),
                    stringResource(R.string.merge_app_protected),
                    Modifier.size(24.dp),
                )
            }
        }
    }
}
