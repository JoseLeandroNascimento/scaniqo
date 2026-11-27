package com.joseleandro.scaniqo.ui.documentEditor.components

import com.joseleandro.scaniqo.feature.editor.R

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.joseleandro.scaniqo.domain.model.DocumentModel
import com.joseleandro.scaniqo.ui.documentCommon.components.DocumentPdfPage
import com.joseleandro.scaniqo.ui.documentCommon.components.DocumentPreviewZoom

/** Mantém a indicação da página discreta enquanto a prévia é atualizada. */
@Composable
internal fun DocumentPagePreviewHeader(
    pageNumber: Int,
    pageCount: Int,
    selected: Boolean,
    updating: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DocumentViewerPageHeader(pageNumber, pageCount, selected)
        if (updating) {
            CircularProgressIndicator(
                modifier = Modifier.padding(start = 8.dp).size(12.dp),
                strokeWidth = 1.5.dp,
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
internal fun DocumentSavedPage(
    document: DocumentModel,
    pageIndex: Int,
    modifier: Modifier = Modifier,
    maxPageWidth: Dp = 840.dp,
) {
    Box(modifier
        .fillMaxWidth()
        .padding(horizontal = 4.dp), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .widthIn(max = maxPageWidth)
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
                DocumentPreviewZoom(Modifier.fillMaxSize(), contentKey = "saved-${document.id}-$pageIndex",
                    singleFingerPan = true, contentAspectRatio = 595f / 842f) {
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
