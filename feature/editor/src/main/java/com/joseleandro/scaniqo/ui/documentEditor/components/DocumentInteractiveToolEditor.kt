package com.joseleandro.scaniqo.ui.documentEditor.components

import com.joseleandro.scaniqo.core.resources.R as CoreResourcesR
import com.joseleandro.scaniqo.feature.editor.R

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.joseleandro.scaniqo.domain.model.PdfInteractivePixels
import com.joseleandro.scaniqo.domain.model.PdfPageLayout
import com.joseleandro.scaniqo.domain.model.PdfPagePreview
import com.joseleandro.scaniqo.domain.model.PdfPageSpec
import com.joseleandro.scaniqo.ui.documentCommon.components.DocumentPdfPage

/** Percorre apenas os alvos, mantendo a numeração original mesmo em seleções descontínuas. */
@Composable
internal fun DocumentToolTargetNavigation(
    pages: List<PdfPageSpec>,
    targetIds: Set<String>,
    previewPageId: String,
    onPageChange: (String) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val targets = remember(pages, targetIds) { pages.filter { it.id in targetIds } }
    val targetIndex = targets.indexOfFirst { it.id == previewPageId }.coerceAtLeast(0)
    val pageNumber = pages.indexOfFirst { it.id == previewPageId } + 1
    var choosing by remember { mutableStateOf(false) }
    val description = stringResource(
        R.string.interactive_preview_page_description, pageNumber, targetIndex + 1, targets.size,
    )
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (targets.size > 1) {
            IconButton(
                onClick = { targets.getOrNull(targetIndex - 1)?.let { onPageChange(it.id) } },
                enabled = enabled && targetIndex > 0,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    painterResource(CoreResourcesR.drawable.arrow_back_24px),
                    stringResource(R.string.corrections_preview_previous)
                )
            }
        }
        TextButton(
            onClick = { choosing = true },
            enabled = enabled && targets.size > 1,
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
            modifier = Modifier
                .heightIn(min = 48.dp)
                .semantics { contentDescription = description },
        ) {
            Text(
                stringResource(R.string.interactive_preview_page, pageNumber),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (targets.size > 1) {
            IconButton(
                onClick = { targets.getOrNull(targetIndex + 1)?.let { onPageChange(it.id) } },
                enabled = enabled && targetIndex < targets.lastIndex,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    painterResource(CoreResourcesR.drawable.arrow_back_24px),
                    stringResource(R.string.corrections_preview_next),
                    Modifier.rotate(180f),
                )
            }
        }
    }
    if (choosing) {
        DocumentPagePickerDialog(
            pages = targets,
            selectedIds = setOf(previewPageId),
            allowMultiple = false,
            onDismiss = { choosing = false },
            onConfirm = { ids -> ids.firstOrNull()?.let(onPageChange); choosing = false },
            pageNumbers = pages.mapIndexed { index, page -> page.id to index + 1 }.toMap(),
        )
    }
}

/** Mantém a captura em memória durante os gestos e usa o PDF quando ela não está disponível. */
@Composable
internal fun DocumentInteractivePagePreview(
    page: PdfPageSpec,
    preview: PdfPagePreview?,
    fallback: PdfPagePreview?,
    isLoading: Boolean,
    hasError: Boolean,
    onRetry: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    pageNumber: Int = page.sourcePageIndex + 1,
    reserveHandleSpace: Boolean = false,
    livePixels: PdfInteractivePixels? = null,
    liveLayout: PdfPageLayout = page.layout,
    workspaceId: String? = null,
    pageCount: Int = 1,
    showPageNumbers: Boolean = false,
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    val handleMargin = if (reserveHandleSpace) 24.dp else 6.dp
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .padding(handleMargin),
        contentAlignment = Alignment.Center
    ) {
        val paperWidth = minOf(maxWidth, maxHeight * (595f / 842f)).coerceAtLeast(1.dp)
        Box(Modifier
            .width(paperWidth)
            .aspectRatio(595f / 842f)) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = RectangleShape,
                color = Color.White,
                shadowElevation = 3.dp,
                border = BorderStroke(.5.dp, Color.Black.copy(alpha = .16f)),
            ) {
                key(workspaceId, page.id, page.sourceUri, page.sourcePageIndex) {
                    if (livePixels != null) {
                        DocumentLivePagePreview(
                            pixels = livePixels,
                            layout = liveLayout,
                            signatures = page.signatures,
                            pageNumber = pageNumber,
                            pageCount = pageCount,
                            showPageNumbers = showPageNumbers,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        DocumentPdfPage(
                            pdfUri = preview?.uri ?: fallback?.uri ?: page.sourceUri,
                            pageIndex = preview?.pageIndex ?: fallback?.pageIndex
                            ?: page.sourcePageIndex,
                            contentKey = "interactive-$workspaceId-${page.id}",
                            modifier = Modifier.fillMaxSize(),
                            pageNumber = pageNumber,
                        )
                    }
                }
            }
            // As alças são irmãs do papel: não são recortadas pela Surface da folha.
            overlay()
        }
        if (isLoading) {
            val description = stringResource(R.string.interactive_preview_busy)
            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = MaterialTheme.shapes.small,
            ) {
                CircularProgressIndicator(
                    Modifier
                        .padding(6.dp)
                        .size(16.dp)
                        .semantics { contentDescription = description },
                    strokeWidth = 2.dp,
                )
            }
        }
        if (hasError) {
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter),
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ) {
                Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        stringResource(R.string.corrections_preview_error),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                    )
                    TextButton(onClick = onRetry, enabled = enabled) {
                        Text(stringResource(R.string.corrections_preview_retry))
                    }
                }
            }
        }
    }
}
