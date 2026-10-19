package com.joseleandro.scaniqo.ui.documents.preview

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
                        color = Color(0xFF4F7CAC),
                        iconKey = TagIcon.WORK.key
                    )
                ),
                documents = listOf(
                    DocumentModel(
                        id = 1,
                        name = "Contrato de prestação de serviços",
                        pdfUri = "preview.pdf",
                        pageCount = 3,
                        createdAt = 1781431200000L,
                        sizeBytes = 248_000L,
                        thumbnailUri = null,
                        tagId = 1,
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
