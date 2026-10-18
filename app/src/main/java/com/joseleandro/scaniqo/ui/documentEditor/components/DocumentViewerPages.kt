package com.joseleandro.scaniqo.ui.documentEditor.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import com.joseleandro.scaniqo.R
import com.joseleandro.scaniqo.domain.model.DocumentModel
import com.joseleandro.scaniqo.ui.documentCommon.components.DocumentPdfPage
import com.joseleandro.scaniqo.ui.documentEditor.event.DocumentToolsEvent
import com.joseleandro.scaniqo.ui.documentEditor.state.DocumentToolsUiState

/** Compara a folha selecionada no mesmo local onde o usuário confere o resultado. */
@Composable
internal fun DocumentPagePreviewHeader(
    pageId: String,
    pageNumber: Int,
    pageCount: Int,
    selected: Boolean,
    state: DocumentToolsUiState,
    onEvent: (DocumentToolsEvent) -> Unit,
    enabled: Boolean,
) {
    val comparisonAvailable = selected && state.canCompare && state.signatureDraft == null
    val showingOriginal = state.displayedComparisonPageId == pageId
    val updating = selected && state.isRendering
    if (!comparisonAvailable && !showingOriginal && !updating) {
        Box(Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp), contentAlignment = Alignment.Center) {
            DocumentViewerPageHeader(pageNumber, pageCount, selected)
        }
        return
    }
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.CenterVertically)
                .padding(end = 8.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                stringResource(R.string.document_page_position, pageNumber, pageCount),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (updating) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 1.5.dp)
                    Text(
                        stringResource(R.string.viewer_preview_updating),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else if (showingOriginal && !comparisonAvailable) {
                Text(
                    stringResource(R.string.viewer_compare_before),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        if (comparisonAvailable) {
            DocumentQualityPreviewControls(
                state = state,
                onEvent = onEvent,
                enabled = enabled,
                showingOriginal = showingOriginal,
                modifier = Modifier.align(Alignment.CenterVertically),
            )
        } else {
            Text(
                stringResource(if (selected) R.string.viewer_page_selected else R.string.viewer_pdf_format),
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterVertically),
            )
        }
    }
}

@Composable
internal fun DocumentPreviewError(enabled: Boolean, onRetry: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(R.string.viewer_preview_stale),
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
            )
            TextButton(onClick = onRetry, enabled = enabled) {
                Text(stringResource(R.string.tools_retry))
            }
        }
    }
}

/** O arquivo salvo já pode ser lido enquanto as ferramentas preparam uma cópia de trabalho. */
@Composable
internal fun DocumentSavedPage(document: DocumentModel, pageIndex: Int) {
    Box(Modifier
        .fillMaxWidth()
        .padding(horizontal = 4.dp), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .widthIn(max = 840.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.heightIn(min = 48.dp), contentAlignment = Alignment.Center) {
                DocumentViewerPageHeader(pageIndex + 1, document.pageCount, selected = false)
            }
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(595f / 842f),
                shape = RectangleShape,
                color = Color.White,
                shadowElevation = 6.dp,
                border = BorderStroke(.5.dp, Color.Black.copy(alpha = .12f)),
            ) {
                DocumentPdfPage(
                    pdfUri = document.pdfUri,
                    pageIndex = pageIndex,
                    contentKey = "saved-${document.id}-$pageIndex",
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
internal fun ToolProgress(text: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CircularProgressIndicator(Modifier.size(32.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
