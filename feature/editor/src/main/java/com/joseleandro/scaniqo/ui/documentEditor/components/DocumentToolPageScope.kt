package com.joseleandro.scaniqo.ui.documentEditor.components

import com.joseleandro.scaniqo.feature.editor.R

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.joseleandro.scaniqo.domain.model.PdfPageSpec
import com.joseleandro.scaniqo.ui.theme.ScaniqoTheme


/** A grade é virtualizada para continuar leve mesmo em arquivos com muitas páginas. */
@Composable
internal fun DocumentPagePickerDialog(
    pages: List<PdfPageSpec>,
    selectedIds: Set<String>,
    allowMultiple: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit,
    pageNumbers: Map<String, Int> = emptyMap(),
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        DocumentPagePickerContent(pages, selectedIds, allowMultiple, onDismiss, onConfirm, pageNumbers)
    }
}

/** O mesmo painel é usado na janela e nas prévias, respeitando os limites do espaço disponível. */
@Composable
private fun DocumentPagePickerContent(
    pages: List<PdfPageSpec>,
    selectedIds: Set<String>,
    allowMultiple: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit,
    pageNumbers: Map<String, Int> = emptyMap(),
    modifier: Modifier = Modifier,
) {
    val available = remember(pages) { pages.mapTo(mutableSetOf()) { it.id } }
    val minimumCellWidth = 128.dp * LocalDensity.current.fontScale.coerceAtLeast(1f)
    var selected by remember(available, selectedIds) { mutableStateOf(selectedIds.intersect(available)) }
    BoxWithConstraints(
        modifier = modifier.fillMaxWidth().safeDrawingPadding().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        val showHint = maxHeight >= 400.dp
        Surface(
            modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth()
                .heightIn(max = minOf(maxHeight, 680.dp)),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
        ) {
            Column {
                // O cabeçalho acompanha a grade ao rolar; fontes grandes não escondem as ações.
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minimumCellWidth),
                    modifier = Modifier.fillMaxWidth().weight(1f, fill = false)
                        .then(if (allowMultiple) Modifier else Modifier.selectableGroup()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 16.dp),
                ) {
                    item(key = "title", span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            stringResource(if (allowMultiple) R.string.document_scope_title else R.string.corrections_preview_pages),
                            modifier = Modifier.padding(bottom = 8.dp).semantics { heading() },
                            style = MaterialTheme.typography.headlineSmall,
                        )
                    }
                    if (allowMultiple) {
                        if (showHint) {
                            item(key = "hint", span = { GridItemSpan(maxLineSpan) }) {
                                Text(
                                    stringResource(R.string.document_scope_hint),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        item(key = "selection_actions", span = { GridItemSpan(maxLineSpan) }) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(onClick = { selected = available }) {
                                    Text(stringResource(R.string.document_scope_select_all))
                                }
                                TextButton(onClick = { selected = emptySet() }) {
                                    Text(stringResource(R.string.document_scope_clear))
                                }
                            }
                        }
                        item(key = "selection_count", span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                if (selected.isEmpty()) stringResource(R.string.document_scope_empty)
                                else pluralStringResource(R.plurals.document_scope_count, selected.size, selected.size),
                                modifier = Modifier.padding(bottom = 4.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (selected.isEmpty()) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    itemsIndexed(pages, key = { _, page -> "page-${page.id}" }) { index, page ->
                        val checked = page.id in selected
                        Surface(
                            modifier = Modifier.fillMaxWidth().then(
                                if (allowMultiple) Modifier.toggleable(
                                    value = checked,
                                    role = Role.Checkbox,
                                    onValueChange = { checkedNow ->
                                        selected = if (checkedNow) selected + page.id else selected - page.id
                                    },
                                ) else Modifier.selectable(
                                    selected = checked,
                                    role = Role.RadioButton,
                                    onClick = { onConfirm(setOf(page.id)) },
                                ),
                            ),
                            shape = MaterialTheme.shapes.medium,
                            color = if (checked) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surface,
                            contentColor = if (checked) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurface,
                        ) {
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 56.dp)
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (allowMultiple) Checkbox(checked, onCheckedChange = null)
                                else RadioButton(checked, onClick = null)
                                Text(
                                    stringResource(R.string.document_scope_page, pageNumbers[page.id] ?: (index + 1)),
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.labelLarge.copy(textDirection = TextDirection.ContentOrLtr),
                                )
                            }
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.document_scope_cancel))
                    }
                    if (allowMultiple) {
                        TextButton(onClick = { onConfirm(selected) }, enabled = selected.isNotEmpty()) {
                            Text(stringResource(R.string.document_scope_confirm))
                        }
                    }
                }
            }
        }
    }
}

@Preview(name = "Seleção de páginas", widthDp = 390, heightDp = 760, showBackground = true)
@Preview(name = "Seleção escuro", widthDp = 390, heightDp = 760, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Seleção compacta e fonte ampliada", widthDp = 320, heightDp = 640, fontScale = 1.3f)
@Preview(name = "Seleção em pouca altura", widthDp = 640, heightDp = 360, fontScale = 1.3f)
@Composable
private fun DocumentPagePickerPreview() {
    ScaniqoTheme {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .6f)), contentAlignment = Alignment.Center) {
            DocumentPagePickerContent(
                pages = (1..25).map { PdfPageSpec("page-$it", "preview", it - 1) },
                selectedIds = setOf("page-1", "page-3", "page-5"),
                allowMultiple = true,
                onDismiss = {},
                onConfirm = {},
            )
        }
    }
}
