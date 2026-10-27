package com.joseleandro.scaniqo.ui.documentEditor.preview

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import com.joseleandro.scaniqo.domain.model.DocumentModel
import com.joseleandro.scaniqo.domain.model.PdfFilterThumbnails
import com.joseleandro.scaniqo.domain.model.PdfImageFilter
import com.joseleandro.scaniqo.domain.model.PdfPageLayout
import com.joseleandro.scaniqo.domain.model.PdfPagePreview
import com.joseleandro.scaniqo.domain.model.PdfPageSpec
import com.joseleandro.scaniqo.domain.model.PdfWorkspace
import com.joseleandro.scaniqo.domain.model.TagModel
import com.joseleandro.scaniqo.ui.ads.EditorBannerAd
import com.joseleandro.scaniqo.ui.documentCommon.state.DocumentActionsUiState
import com.joseleandro.scaniqo.ui.documentEditor.DocumentViewerScreen
import com.joseleandro.scaniqo.ui.documentEditor.model.DocumentTool
import com.joseleandro.scaniqo.ui.documentEditor.model.documentFilterChoices
import com.joseleandro.scaniqo.ui.documentEditor.state.DocumentFilterThumbnailState
import com.joseleandro.scaniqo.ui.documentEditor.state.DocumentPagePreviewState
import com.joseleandro.scaniqo.ui.documentEditor.state.DocumentToolsUiState
import com.joseleandro.scaniqo.ui.documentEditor.state.DocumentViewerUiState
import com.joseleandro.scaniqo.ui.theme.ScaniqoTheme

@Preview(name = "Visualizador claro", showBackground = true)
@Preview(
    name = "Visualizador escuro",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
private fun DocumentViewerPreview() {
    DocumentViewerPreviewContent()
}

@Preview(
    name = "Visualizador com fonte ampliada",
    widthDp = 360,
    heightDp = 800,
    fontScale = 1.3f,
    locale = "pt-rBR"
)
@Composable
private fun DocumentViewerLargeTextPreview() {
    DocumentViewerPreviewContent(
        name = "Contrato de aluguel do apartamento — versão assinada",
        hasChanges = true,
    )
}

@Preview(name = "Comparação junto da página", widthDp = 390, heightDp = 850, locale = "pt-rBR")
@Preview(
    name = "Comparação escuro",
    widthDp = 390,
    heightDp = 850,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    locale = "pt-rBR"
)
@Preview(
    name = "Comparação fonte ampliada",
    widthDp = 360,
    heightDp = 850,
    fontScale = 1.3f,
    locale = "pt-rBR"
)
@Composable
private fun DocumentViewerComparisonPreview() {
    DocumentViewerPreviewContent(hasChanges = true, improved = true)
}

@Preview(name = "Filtros na barra", widthDp = 390, heightDp = 850, locale = "pt-rBR")
@Preview(
    name = "Filtros na barra escuro",
    widthDp = 390,
    heightDp = 850,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    locale = "pt-rBR"
)
@Preview(
    name = "Filtros na barra fonte ampliada",
    widthDp = 360,
    heightDp = 800,
    fontScale = 1.3f,
    locale = "pt-rBR"
)
@Composable
private fun DocumentViewerFiltersPreview() {
    DocumentViewerPreviewContent(hasChanges = true, improved = true, filtersOpen = true)
}

@Composable
private fun DocumentViewerPreviewContent(
    name: String = "Contrato de aluguel",
    hasChanges: Boolean = false,
    improved: Boolean = false,
    filtersOpen: Boolean = false,
) {
    val document = DocumentModel(
        id = 1, name = name, pdfUri = "preview.pdf", pageCount = 2,
        createdAt = 1781431200000L, sizeBytes = 248_000L, thumbnailUri = null, tagId = 1,
    )
    ScaniqoTheme {
        DocumentViewerScreen(
            documentId = 1,
            uiState = DocumentViewerUiState(
                document = document,
                availableDocuments = listOf(document),
                isLoading = false,
                actions = DocumentActionsUiState(
                    tags = listOf(TagModel(id = 1, name = "Contratos", color = Color(0xFFCCDFEF))),
                ),
            ),
            onDocumentAction = {},
            onRetryLoad = {},
            onNavigationBack = {},
            toolsState = DocumentToolsUiState(
                document = document,
                workspace = PdfWorkspace(
                    documentId = 1, workspaceId = "preview", directoryUri = "preview",
                    pages = listOf(
                        PdfPageSpec(
                            id = "one", sourceUri = "preview.pdf", sourcePageIndex = 0,
                            layout = PdfPageLayout(imageFilter = if (improved) PdfImageFilter.ENHANCE else PdfImageFilter.ORIGINAL),
                        ),
                        PdfPageSpec(
                            id = "two", sourceUri = "preview.pdf", sourcePageIndex = 1,
                            layout = PdfPageLayout(
                                imageFilter = if (filtersOpen && improved) PdfImageFilter.ENHANCE else PdfImageFilter.ORIGINAL,
                            ),
                        ),
                    ),
                ),
                selectedPageId = "one",
                previewUri = "preview.pdf",
                pagePreviews = mapOf(
                    "one" to DocumentPagePreviewState(PdfPagePreview("preview.pdf", 0)),
                    "two" to DocumentPagePreviewState(PdfPagePreview("preview.pdf", 1)),
                ),
                hasChanges = hasChanges,
                canUndo = hasChanges,
                tool = DocumentTool.FILTERS.takeIf { filtersOpen },
                filterThumbnails = DocumentFilterThumbnailState(
                    pageId = "one",
                    result = PdfFilterThumbnails(documentFilterChoices.associateWith { "preview-$it" }),
                ),
            ),
            onToolsEvent = {},
            banner = { EditorBannerAd() },
        )
    }
}
