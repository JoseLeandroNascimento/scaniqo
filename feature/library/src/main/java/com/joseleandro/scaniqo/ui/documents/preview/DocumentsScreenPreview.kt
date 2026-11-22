package com.joseleandro.scaniqo.ui.documents.preview

import androidx.compose.ui.graphics.toArgb

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import com.joseleandro.scaniqo.domain.model.DocumentModel
import com.joseleandro.scaniqo.domain.model.TagIcon
import com.joseleandro.scaniqo.domain.model.TagModel
import com.joseleandro.scaniqo.ui.documentCommon.state.DocumentActionsUiState
import com.joseleandro.scaniqo.ui.documents.DocumentsScreen
import com.joseleandro.scaniqo.ui.documents.state.DocumentsUiState
import com.joseleandro.scaniqo.ui.theme.ScaniqoTheme

@Preview(name = "Documentos claro", showBackground = true)
@Preview(
    name = "Documentos escuro",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Preview(name = "Documentos tela externa", widthDp = 320, heightDp = 720, locale = "pt-rBR")
@Preview(name = "Documentos dobrável", widthDp = 673, heightDp = 841, locale = "pt-rBR")
@Preview(name = "Documentos tablet", widthDp = 1280, heightDp = 800, locale = "pt-rBR")
@Preview(name = "Documentos texto grande", widthDp = 320, heightDp = 640, fontScale = 2f, locale = "pt-rBR")
@Composable
private fun DocumentsScreenPreview() {
    ScaniqoTheme {
        DocumentsScreen(
            uiState = DocumentsUiState(
                isLoading = false,
                tags = listOf(
                    TagModel(
                        id = 1,
                        name = "Trabalho",
                        colorArgb = Color(0xFF4F7CAC).toArgb(),
                        iconKey = TagIcon.WORK.key
                    )
                ),
                documents = List(6) { index ->
                    DocumentModel(
                        id = index.toLong() + 1,
                        name = "Contrato de prestação de serviços",
                        pdfUri = "preview.pdf",
                        pageCount = 3,
                        createdAt = 1781431200000L,
                        sizeBytes = 248_000L,
                        thumbnailUri = null,
                        tagId = 1,
                    )
                },
            ),
            actionsState = DocumentActionsUiState(isLoadingTags = false),
            onEvent = {},
            onDocumentAction = {},
            onOpenMenu = {},
            onImportPdf = {},
            onSearch = {},
            onOpenDocument = {},
            onMergePdfs = {},
            onSignDocument = {},
            onAllTools = {},
        )
    }
}

@Preview(name = "Biblioteca vazia", widthDp = 390, heightDp = 780, locale = "pt-rBR")
@Preview(name = "Biblioteca vazia tablet", widthDp = 1280, heightDp = 800, locale = "pt-rBR")
@Preview(name = "Biblioteca vazia fonte ampliada", widthDp = 320, heightDp = 640, fontScale = 1.5f, locale = "pt-rBR")
@Composable
private fun DocumentsEmptyPreview() {
    ScaniqoTheme {
        DocumentsScreen(
            uiState = DocumentsUiState(isLoading = false),
            actionsState = DocumentActionsUiState(isLoadingTags = false),
            onEvent = {},
            onDocumentAction = {},
            onOpenMenu = {},
            onImportPdf = {},
            onSearch = {},
            onOpenDocument = {},
            onMergePdfs = {},
            onSignDocument = {},
            onAllTools = {},
        )
    }
}

@Preview(name = "Tag sem documentos", widthDp = 390, heightDp = 780, locale = "pt-rBR")
@Composable
private fun DocumentsFilteredEmptyPreview() {
    ScaniqoTheme {
        DocumentsScreen(
            uiState = DocumentsUiState(
                isLoading = false,
                selectedTagId = 1,
                tags = listOf(
                    TagModel(
                        id = 1,
                        name = "Trabalho",
                        colorArgb = Color(0xFF4F7CAC).toArgb(),
                        iconKey = TagIcon.WORK.key,
                    ),
                ),
            ),
            actionsState = DocumentActionsUiState(isLoadingTags = false),
            onEvent = {},
            onDocumentAction = {},
            onOpenMenu = {},
            onImportPdf = {},
            onSearch = {},
            onOpenDocument = {},
            onMergePdfs = {},
            onSignDocument = {},
            onAllTools = {},
        )
    }
}
