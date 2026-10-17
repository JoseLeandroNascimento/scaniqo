package com.joseleandro.scaniqo.ui.documentEditor

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joseleandro.scaniqo.R
import com.joseleandro.scaniqo.domain.model.PdfImageFilter
import com.joseleandro.scaniqo.domain.model.PdfPageLayout
import com.joseleandro.scaniqo.domain.model.PdfPageSpec
import com.joseleandro.scaniqo.domain.model.PdfWorkspace
import com.joseleandro.scaniqo.ui.theme.ScaniqoTheme

/** A página do visualizador é a prévia; cada escolha modifica o documento inteiro. */
@Composable
internal fun DocumentFiltersBar(
    state: DocumentToolsUiState,
    onEvent: (DocumentToolsEvent) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val workspace = state.workspace ?: return
    val page = workspace.pages.getOrNull(state.selectedPageIndex) ?: return
    val currentEvent by rememberUpdatedState(onEvent)
    val canEdit = state.canEdit && enabled
    val thumbnailsForPage = state.filterThumbnailsPageId == page.id
    val thumbnails = state.filterThumbnails.takeIf { thumbnailsForPage }.orEmpty()
    val isLoading = state.isLoadingFilterThumbnails || !thumbnailsForPage
    val background = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)

    // As amostras partem da fonte original; trocar o filtro não invalida esta chave.
    val thumbnailSource = page.copy(
        layout = page.layout.copy(
            imageFilter = PdfImageFilter.ORIGINAL,
            filterIntensity = 1f,
            rasterizeText = true,
        ),
    )
    val numbering = if (workspace.showPageNumbers) state.selectedPageIndex to workspace.pages.size else null
    LaunchedEffect(workspace.workspaceId, thumbnailSource, numbering, canEdit) {
        if (canEdit) currentEvent(DocumentToolsEvent.LoadFilterThumbnails(page.id, rasterizeText = true))
    }
    DisposableEffect(workspace.workspaceId) {
        onDispose { currentEvent(DocumentToolsEvent.ClearFilterPreview) }
    }
    Surface(modifier = modifier.fillMaxWidth(), color = background) {
        Column(Modifier.padding(bottom = 8.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // O cabeçalho ocupa uma linha e pode quebrar com fontes maiores.
                FlowRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        stringResource(R.string.filter_bar_title),
                        Modifier.align(Alignment.CenterVertically).semantics { heading() },
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        stringResource(R.string.filter_bar_scope),
                        Modifier.align(Alignment.CenterVertically),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                IconButton(
                    onClick = { onEvent(DocumentToolsEvent.DismissTool) },
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        painterResource(R.drawable.ic_toast_close),
                        contentDescription = stringResource(R.string.filter_bar_close),
                    )
                }
            }
            DocumentFilterThumbnails(
                thumbnails = thumbnails,
                selectedFilter = state.globalImageFilter,
                loading = isLoading,
                enabled = canEdit,
                onSelect = { onEvent(DocumentToolsEvent.SelectGlobalFilter(it)) },
                compact = true,
                contentPadding = PaddingValues(horizontal = 16.dp),
                fadeColor = background,
            )
            if (thumbnailsForPage && state.filterThumbnailsError) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        stringResource(R.string.filter_bar_samples_error),
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(
                        onClick = {
                            onEvent(DocumentToolsEvent.LoadFilterThumbnails(page.id, rasterizeText = true, force = true))
                        },
                        enabled = canEdit,
                    ) {
                        Text(stringResource(R.string.filter_preview_retry))
                    }
                }
            }
            if (thumbnailsForPage && state.filterThumbnailsRequireRasterization) {
                Text(
                    stringResource(R.string.filter_bar_native_hint),
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Preview(name = "Faixa de filtros", widthDp = 360, showBackground = true)
@Preview(name = "Faixa de filtros escura", widthDp = 360, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Faixa de filtros fonte ampliada", widthDp = 360, fontScale = 1.3f)
@Composable
private fun DocumentFiltersBarPreview() {
    ScaniqoTheme {
        DocumentFiltersBar(
            state = DocumentToolsUiState(
                workspace = PdfWorkspace(
                    documentId = 1L,
                    workspaceId = "preview",
                    directoryUri = "preview",
                    pages = (1..3).map {
                        PdfPageSpec(
                            id = "page-$it",
                            sourceUri = "preview",
                            sourcePageIndex = it - 1,
                            layout = PdfPageLayout(imageFilter = PdfImageFilter.ENHANCE, rasterizeText = true),
                        )
                    },
                ),
                selectedPageId = "page-1",
                tool = DocumentTool.FILTERS,
                filterThumbnailsPageId = "page-1",
                filterThumbnails = documentFilterChoices.associateWith { "preview-$it" },
            ),
            onEvent = {},
        )
    }
}
