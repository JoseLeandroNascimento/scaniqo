package com.joseleandro.scaniqo.ui.documentMerge

import android.content.res.Configuration
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joseleandro.scaniqo.R
import com.joseleandro.scaniqo.core.localization.bidiArgument
import com.joseleandro.scaniqo.domain.model.DocumentModel
import com.joseleandro.scaniqo.ui.documentCommon.components.DocumentMergeIllustration
import com.joseleandro.scaniqo.ui.documentCommon.components.DocumentMergeLibrarySheet
import com.joseleandro.scaniqo.ui.documentCommon.components.DocumentSourceHeader
import com.joseleandro.scaniqo.ui.documentCommon.components.DocumentSourceOrigins
import com.joseleandro.scaniqo.ui.documentCommon.components.DocumentThumbnail
import com.joseleandro.scaniqo.ui.documentCommon.model.DocumentMergeItem
import com.joseleandro.scaniqo.ui.documentCommon.model.toMergeItem
import com.joseleandro.scaniqo.ui.documentCommon.state.DocumentMergeSelectionState
import com.joseleandro.scaniqo.ui.theme.ScaniqoTheme

@Composable
internal fun DocumentMergeSourceStep(
    documents: List<DocumentModel>,
    selection: DocumentMergeSelectionState,
    enabled: Boolean,
    canContinue: Boolean,
    isLoading: Boolean,
    loadError: Boolean,
    onRetry: () -> Unit,
    onPickFiles: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showLibrary by rememberSaveable { mutableStateOf(false) }
    val selected = selection.resolve(documents)
    val selectedIds = selected.mapNotNull(DocumentMergeItem::documentId).toSet()

    BoxWithConstraints(modifier.fillMaxSize()) {
        val compact = maxHeight < 700.dp || LocalDensity.current.fontScale > 1.3f
        Column(Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(vertical = if (compact) 8.dp else 16.dp),
                verticalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 16.dp),
            ) {
                item(key = "intro") {
                    DocumentSourceHeader(
                        toolTitle = stringResource(R.string.merge_selection_title),
                        heading = stringResource(R.string.merge_motion_empty),
                        subtitle = stringResource(R.string.merge_motion_hint),
                        compact = compact,
                        hero = { DocumentMergeIllustration(
                            selectedCount = selected.size,
                            isWorking = false,
                            modifier = it,
                        ) },
                    )
                }
                item(key = "origins") {
                    DocumentSourceOrigins(
                        enabled = enabled,
                        onPickApp = { showLibrary = true },
                        onPickDevice = onPickFiles,
                    )
                }
                if (selected.isNotEmpty()) {
                    item(key = "selected-count") {
                        AnimatedContent(
                            targetState = selected.size,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                            label = "Merge source selected count",
                        ) { count ->
                            Text(
                                pluralStringResource(R.plurals.merge_app_selected_count, count, count),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    items(selected, key = DocumentMergeItem::key) { item ->
                        MergeChosenSourceRow(
                            item = item,
                            document = documents.firstOrNull { it.id == item.documentId },
                            enabled = enabled,
                            onRemove = { selection.remove(item.key) },
                            modifier = Modifier.animateItem(
                                fadeInSpec = tween(180),
                                placementSpec = tween(200),
                                fadeOutSpec = tween(120),
                            ),
                        )
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Button(
                onClick = onContinue,
                enabled = enabled && canContinue && selected.size >= 2,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp).heightIn(min = 56.dp),
            ) {
                if (selected.size < 2) {
                    Text(pluralStringResource(R.plurals.merge_selection_minimum, 2, 2))
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.quick_merge_continue))
                        Text(
                            pluralStringResource(R.plurals.merge_app_selected_count, selected.size, selected.size),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }
    }
    if (showLibrary) {
        DocumentMergeLibrarySheet(
            documents = documents,
            selectedIds = selectedIds,
            selectedCount = selected.size,
            isLoading = isLoading,
            loadError = loadError,
            enabled = enabled,
            onRetry = onRetry,
            onSelect = { selection.toggle(it.toMergeItem()) },
            onDismiss = { showLibrary = false },
        )
    }
}

@Composable
private fun MergeChosenSourceRow(
    item: DocumentMergeItem,
    document: DocumentModel?,
    enabled: Boolean,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .6f)),
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 68.dp)
                .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (document != null) {
                DocumentThumbnail(
                    document,
                    Modifier.width(36.dp).aspectRatio(1f / 1.4142f).clip(MaterialTheme.shapes.extraSmall),
                )
            } else {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Icon(
                        painterResource(R.drawable.ic_document_import),
                        contentDescription = null,
                        modifier = Modifier.padding(12.dp).size(24.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    item.name,
                    style = MaterialTheme.typography.titleSmall.copy(textDirection = TextDirection.ContentOrLtr),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val pages = item.pageCount?.let { pluralStringResource(R.plurals.document_page_count, it, it) }
                    ?: stringResource(R.string.merge_selection_external)
                Text(
                    if (item.isCurrentDocument) stringResource(R.string.merge_selection_current, pages) else pages,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            if (!item.isCurrentDocument) {
                IconButton(onClick = onRemove, enabled = enabled) {
                    Icon(
                        painterResource(R.drawable.ic_toast_close),
                        contentDescription = stringResource(R.string.merge_selection_remove, bidiArgument(item.name)),
                    )
                }
            }
        }
    }
}

@Preview(name = "Mesclar · escolher", widthDp = 390, heightDp = 780, locale = "pt-rBR")
@Preview(name = "Mesclar · fonte ampliada", widthDp = 320, heightDp = 640, fontScale = 1.5f, locale = "pt-rBR")
@Preview(name = "Mesclar · escolher escuro", widthDp = 390, heightDp = 780, locale = "pt-rBR",
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DocumentMergeSourcePreview() {
    val docs = listOf(
        DocumentModel(1, "Contrato", "file:///contract.pdf", 3, 0, 0),
        DocumentModel(2, "Comprovantes", "file:///receipts.pdf", 5, 0, 0),
    )
    val selection = remember { DocumentMergeSelectionState(listOf(docs.first().toMergeItem())) }
    ScaniqoTheme {
        DocumentMergeSourceStep(
            documents = docs,
            selection = selection,
            enabled = true,
            canContinue = true,
            isLoading = false,
            loadError = false,
            onRetry = {},
            onPickFiles = {},
            onContinue = {},
            modifier = Modifier.padding(horizontal = 20.dp),
        )
    }
}
