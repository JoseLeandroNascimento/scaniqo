package com.joseleandro.scaniqo.ui.documentEditor.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.joseleandro.scaniqo.R
import com.joseleandro.scaniqo.domain.model.PdfPagePreview
import com.joseleandro.scaniqo.domain.model.PdfPageSpec
import com.joseleandro.scaniqo.ui.documentCommon.components.DocumentPdfPage

/** Prévia compartilhada por ferramentas; o estado entregue corresponde ao arquivo que está visível. */
@Composable
internal fun DocumentToolDraftPreview(
    page: PdfPageSpec,
    pageIndex: Int,
    pageCount: Int,
    previewHeight: Dp,
    preview: PdfPagePreview?,
    fallback: PdfPagePreview?,
    isLoading: Boolean,
    hasError: Boolean,
    showingOriginal: Boolean,
    enabled: Boolean,
    contentKey: String,
    onShowOriginal: (Boolean) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onChoosePage: () -> Unit,
    onRetry: () -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val fontScale = LocalDensity.current.fontScale
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            // Os controles dividem a linha somente quando os textos cabem com a fonte escolhida.
            if (maxWidth < 340.dp * fontScale) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DraftPageNavigation(
                        pageIndex, pageCount, enabled, onPrevious, onNext, onChoosePage,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    DraftComparisonSelector(
                        showingOriginal, enabled, onShowOriginal, modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    DraftPageNavigation(
                        pageIndex, pageCount, enabled, onPrevious, onNext, onChoosePage,
                        modifier = Modifier.weight(1f),
                    )
                    DraftComparisonSelector(
                        showingOriginal, enabled, onShowOriginal, modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        Surface(
            modifier = Modifier.fillMaxWidth().height(previewHeight + 28.dp),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Box(Modifier.padding(12.dp), contentAlignment = Alignment.Center) {
                Surface(
                    modifier = Modifier.height(previewHeight).aspectRatio(595f / 842f),
                    shape = RectangleShape,
                    color = Color.White,
                    shadowElevation = 2.dp,
                    border = BorderStroke(.5.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    DocumentPdfPage(
                        pdfUri = preview?.uri ?: fallback?.uri ?: page.sourceUri,
                        pageIndex = preview?.pageIndex ?: fallback?.pageIndex ?: page.sourcePageIndex,
                        modifier = Modifier.fillMaxSize(),
                        contentKey = contentKey,
                    )
                }
                if (isLoading) {
                    Surface(
                        modifier = Modifier.align(Alignment.BottomEnd),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = MaterialTheme.shapes.small,
                        shadowElevation = 1.dp,
                    ) {
                        Row(
                            Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 1.5.dp)
                            Text(stringResource(R.string.corrections_preview_updating), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
        if (hasError) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.corrections_preview_error),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                TextButton(onClick = onRetry, enabled = enabled) {
                    Text(stringResource(R.string.corrections_preview_retry))
                }
            }
        }
    }
}

@Composable
private fun DraftPageNavigation(
    pageIndex: Int,
    pageCount: Int,
    enabled: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onChoosePage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            onClick = onPrevious,
            enabled = enabled && pageIndex > 0,
            modifier = Modifier.size(48.dp),
        ) {
            Icon(
                painterResource(R.drawable.arrow_back_24px),
                stringResource(R.string.corrections_preview_previous),
            )
        }
        val chooseDescription = stringResource(
            R.string.corrections_preview_choose_position, pageIndex + 1, pageCount,
        )
        TextButton(
            onClick = onChoosePage,
            enabled = enabled && pageCount > 1,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
            modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                .semantics { contentDescription = chooseDescription },
        ) {
            Text(
                stringResource(R.string.corrections_preview_position, pageIndex + 1, pageCount),
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
            )
        }
        IconButton(
            onClick = onNext,
            enabled = enabled && pageIndex < pageCount - 1,
            modifier = Modifier.size(48.dp),
        ) {
            Icon(
                painterResource(R.drawable.arrow_back_24px),
                stringResource(R.string.corrections_preview_next),
                modifier = Modifier.rotate(180f),
            )
        }
    }
}

@Composable
private fun DraftComparisonSelector(
    showingOriginal: Boolean,
    enabled: Boolean,
    onShowOriginal: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    SingleChoiceSegmentedButtonRow(modifier.height(IntrinsicSize.Min)) {
        listOf(true, false).forEachIndexed { index, original ->
            val description = stringResource(
                if (original) R.string.corrections_preview_before_description
                else R.string.corrections_preview_after_description,
            )
            SegmentedButton(
                selected = showingOriginal == original,
                onClick = { onShowOriginal(original) },
                enabled = enabled,
                shape = SegmentedButtonDefaults.itemShape(index, 2),
                icon = {},
                modifier = Modifier.heightIn(min = 48.dp).fillMaxHeight()
                    .semantics { contentDescription = description },
            ) {
                Text(
                    stringResource(
                        if (original) R.string.corrections_preview_before
                        else R.string.corrections_preview_after,
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
