package com.joseleandro.scaniqo.ui.documentCommon.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDirection
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
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .6f)),
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .65f),
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                    Text("${index + 1}", style = MaterialTheme.typography.labelLarge.copy(textDirection = TextDirection.Ltr))
                }
            }
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (item.protectionKeyId != null) {
                Icon(painterResource(R.drawable.ic_document_lock),
                    stringResource(R.string.merge_app_protected), Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box {
                IconButton(onClick = { menuExpanded = true }, enabled = enabled) {
                    Icon(
                        painterResource(R.drawable.ic_document_drag),
                        stringResource(R.string.merge_selection_drag_handle, bidiArgument(item.name), index + 1, count),
                        Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.merge_move_up)) },
                        enabled = enabled && index > 0,
                        onClick = { menuExpanded = false; onMove(index - 1) },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.merge_move_down)) },
                        enabled = enabled && index < count - 1,
                        onClick = { menuExpanded = false; onMove(index + 1) },
                    )
                    if (!item.isCurrentDocument) {
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.merge_remove)) },
                            enabled = enabled,
                            leadingIcon = { Icon(painterResource(R.drawable.ic_toast_close), null, Modifier.size(18.dp)) },
                            onClick = { menuExpanded = false; onRemove() },
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun DocumentMergeAvailableRow(
    document: DocumentModel,
    enabled: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
) {
    val background by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = .45f)
        else MaterialTheme.colorScheme.surfaceContainerLow,
        animationSpec = tween(180), label = "Document selection",
    )
    Surface(
        modifier = modifier.fillMaxWidth().toggleable(
            value = selected, enabled = enabled, role = Role.Checkbox, onValueChange = { onSelect() },
        ),
        shape = MaterialTheme.shapes.large,
        color = background,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 68.dp).padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(painterResource(R.drawable.ic_tag_document), null, Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(document.name, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall.copy(textDirection = TextDirection.ContentOrLtr))
                Text(
                    pluralStringResource(R.plurals.document_page_count, document.pageCount, document.pageCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (document.isProtected) {
                Icon(painterResource(R.drawable.ic_document_lock),
                    stringResource(R.string.merge_app_protected), Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(painterResource(if (selected) R.drawable.ic_check_24 else R.drawable.add_24px), null,
                Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        }
    }
}
