package com.joseleandro.scaniqo.ui.pdfTools

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.joseleandro.scaniqo.R
import com.joseleandro.scaniqo.core.localization.localizedRuntimeMessage

@Composable
internal fun PdfUtilityPageSelectionControls(
    state: PdfUtilityPageSelectionState,
    pageCount: Int,
    enabled: Boolean,
    onChange: (PdfUtilityPageSelectionState) -> Unit,
    onGoToPage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val selected = if (state.draftActive && state.mode == UtilityPageSelectionMode.INTERVALS) {
        runCatching { UtilityPageSelection.fromText(state.draft, pageCount).resolve(pageCount) }
            .getOrNull() ?: state.selection.resolve(pageCount)
    } else state.selection.resolve(pageCount)
    Column(modifier.animateContentSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val selectionActions: @Composable () -> Unit = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { onChange(state.select(UtilityPageSelection(), pageCount)) },
                    enabled = enabled && (selected.size != pageCount || state.draftActive)) {
                    Text(stringResource(R.string.utility_workspace_select_all))
                }
                TextButton(onClick = { onChange(state.select(UtilityPageSelection.none(), pageCount)) },
                    enabled = enabled && (selected.isNotEmpty() || state.draftActive)) {
                    Text(stringResource(R.string.utility_workspace_clear))
                }
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            if (maxWidth / LocalDensity.current.fontScale < 280.dp) {
                Column(Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.utility_workspace_selection, selected.size, pageCount),
                        style = MaterialTheme.typography.titleSmall)
                    Box(Modifier.align(Alignment.End)) { selectionActions() }
                }
            } else Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.utility_workspace_selection, selected.size, pageCount),
                    Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                selectionActions()
            }
        }
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            UtilityPageSelectionMode.entries.forEachIndexed { index, mode ->
                SegmentedButton(
                    modifier = Modifier.fillMaxHeight(),
                    selected = state.mode == mode,
                    onClick = { onChange(state.show(mode, pageCount)) },
                    shape = SegmentedButtonDefaults.itemShape(index, 2),
                    enabled = enabled,
                ) {
                    Text(stringResource(if (mode == UtilityPageSelectionMode.THUMBNAILS)
                        R.string.pages_v2_thumbnails else R.string.pages_v2_intervals))
                }
            }
        }
        if (state.selection.customOrder) {
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.secondaryContainer) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Text(stringResource(R.string.pages_v2_order,
                        selected.take(12).joinToString(" → ") { (it + 1).toString() } +
                            if (selected.size > 12) " …" else ""),
                        style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Ltr))
                    TextButton(onClick = {
                        onChange(state.select(state.selection.inDocumentOrder(pageCount), pageCount))
                    }, enabled = enabled) { Text(stringResource(R.string.pages_v2_document_order)) }
                }
            }
        }
        AnimatedVisibility(state.mode == UtilityPageSelectionMode.INTERVALS) {
            PageRangeEditor(state, pageCount, enabled, onChange)
        }
        if (state.mode == UtilityPageSelectionMode.THUMBNAILS && pageCount > 12) {
            TextButton(onClick = onGoToPage, enabled = enabled) {
                Icon(painterResource(R.drawable.ic_preview_expand), null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.pages_v2_go_to_page))
            }
        }
        if (selected.isEmpty() && !state.draftActive) {
            Text(stringResource(R.string.utility_workspace_empty_selection),
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        if ((state.draftActive || state.from.isNotBlank() || state.until.isNotBlank()) && state.mode == UtilityPageSelectionMode.THUMBNAILS) {
            Text(stringResource(R.string.pages_v2_pending_selection),
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { onChange(state.cancel(pageCount)) }, enabled = enabled) {
                Text(stringResource(R.string.pages_v2_cancel_edit))
            }
        }
    }
}

@Composable
private fun PageRangeEditor(
    state: PdfUtilityPageSelectionState,
    pageCount: Int,
    enabled: Boolean,
    onChange: (PdfUtilityPageSelectionState) -> Unit,
    modifier: Modifier = Modifier,
) {
    val parsed = runCatching { UtilityPageSelection.fromText(state.draft, pageCount) }
    val indices = parsed.getOrNull()?.resolve(pageCount).orEmpty()
    val ranges = utilityPageRanges(indices)
    val from = state.from.map { Character.digit(it, 10) }.joinToString("").toIntOrNull()
    val until = state.until.map { Character.digit(it, 10) }.joinToString("").toIntOrNull()
    val rangeError = when {
        state.from.isBlank() || state.until.isBlank() -> stringResource(R.string.pages_v2_range_incomplete)
        from == null || until == null || from !in 1..pageCount || until !in 1..pageCount ->
            stringResource(R.string.pages_v2_range_bounds, pageCount)
        from > until -> stringResource(R.string.pages_v2_range_reversed)
        else -> null
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.pages_v2_intervals), Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall)
            TextButton(onClick = { onChange(state.showAdvanced(!state.advanced)) }, enabled = enabled) {
                Text(stringResource(if (state.advanced) R.string.pages_v2_add_range else R.string.pages_v2_edit_list))
            }
        }
        if (state.advanced) {
            OutlinedTextField(
                value = state.draft, onValueChange = { onChange(state.edit(it)) },
                modifier = Modifier.fillMaxWidth(), enabled = enabled,
                textStyle = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Ltr),
                label = { Text(stringResource(R.string.utility_pages)) },
                supportingText = {
                    Text(if (state.draftActive && parsed.isFailure)
                        localizedRuntimeMessage(parsed.exceptionOrNull()?.message.orEmpty())
                    else stringResource(R.string.pages_v2_list_hint))
                },
                isError = state.draftActive && parsed.isFailure,
                minLines = 1, maxLines = 3,
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = state.from,
                    onValueChange = { value ->
                        if (value.length <= pageCount.toString().length && value.all { Character.digit(it, 10) >= 0 })
                            onChange(state.copy(from = value, attemptedRange = false))
                    },
                    modifier = Modifier.weight(1f), enabled = enabled, singleLine = true,
                    label = { Text(stringResource(R.string.pages_v2_from)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Ltr),
                    isError = state.attemptedRange && rangeError != null,
                )
                OutlinedTextField(
                    value = state.until,
                    onValueChange = { value ->
                        if (value.length <= pageCount.toString().length && value.all { Character.digit(it, 10) >= 0 })
                            onChange(state.copy(until = value, attemptedRange = false))
                    },
                    modifier = Modifier.weight(1f), enabled = enabled, singleLine = true,
                    label = { Text(stringResource(R.string.pages_v2_until)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Ltr),
                    isError = state.attemptedRange && rangeError != null,
                )
            }
            if (state.attemptedRange && rangeError != null) {
                Text(rangeError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            OutlinedButton(
                onClick = {
                    if (rangeError == null) {
                        runCatching { state.addRange(pageCount) }.onSuccess(onChange)
                    } else onChange(state.copy(attemptedRange = true))
                },
                enabled = enabled && (state.draft.isBlank() || parsed.isSuccess),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Text(stringResource(if (state.editingRange == null) R.string.pages_v2_add_range else R.string.pages_v2_update_range))
            }
            if (state.draft.isNotBlank() && parsed.isFailure) {
                Text(localizedRuntimeMessage(parsed.exceptionOrNull()?.message.orEmpty()),
                    color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (ranges.isNotEmpty()) FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ranges.forEachIndexed { index, range ->
                val label = utilityPageRangeText(range)
                val editLabel = stringResource(R.string.pages_v2_edit_range, label)
                Surface(shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.secondaryContainer) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(
                            onClick = {
                                onChange(state.copy(from = (range.first() + 1).toString(),
                                    until = (range.last() + 1).toString(), editingRange = index,
                                    advanced = false, attemptedRange = false))
                            },
                            enabled = enabled, modifier = Modifier.semantics { contentDescription = editLabel },
                        ) { Text(label, style = MaterialTheme.typography.labelLarge.copy(textDirection = TextDirection.Ltr)) }
                        IconButton(onClick = { onChange(state.removeRange(index, pageCount)) }, enabled = enabled) {
                            Icon(painterResource(R.drawable.ic_toast_close),
                                stringResource(R.string.pages_v2_remove_range, label), Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
        if (state.draftActive || state.from.isNotBlank() || state.until.isNotBlank()) {
            if (parsed.getOrNull()?.customOrder == true) {
                Text(stringResource(R.string.pages_v2_order,
                    indices.take(12).joinToString(" → ") { (it + 1).toString() } + if (indices.size > 12) " …" else ""),
                    style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Ltr))
            }

        }
    }
}
