package com.joseleandro.scaniqo.ui.documentSignature

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.joseleandro.scaniqo.domain.model.DocumentModel
import com.joseleandro.scaniqo.domain.model.PdfPagePreview
import com.joseleandro.scaniqo.domain.model.PdfPageSpec
import com.joseleandro.scaniqo.domain.model.PdfSignaturePlacement
import com.joseleandro.scaniqo.domain.model.PdfWorkspace
import com.joseleandro.scaniqo.domain.model.SignatureModel
import com.joseleandro.scaniqo.ui.documentEditor.state.DocumentPagePreviewState
import com.joseleandro.scaniqo.ui.documentEditor.state.DocumentToolsUiState
import com.joseleandro.scaniqo.ui.theme.ScaniqoTheme

@Preview(name = "Biblioteca de assinaturas", widthDp = 390, heightDp = 850, locale = "pt-rBR")
@Preview(name = "Biblioteca tablet", widthDp = 1280, heightDp = 800, locale = "pt-rBR")
@Composable
private fun DocumentSignatureLibraryPreview() {
    SignaturePreviewContent(hasDraft = false)
}

@Preview(name = "Posicionar assinatura", widthDp = 390, heightDp = 850, locale = "pt-rBR")
@Preview(
    name = "Assinatura escuro", widthDp = 390, heightDp = 850,
    uiMode = Configuration.UI_MODE_NIGHT_YES, locale = "pt-rBR",
)
@Preview(
    name = "Assinatura fonte ampliada", widthDp = 360, heightDp = 800,
    fontScale = 1.3f, locale = "pt-rBR",
)
@Preview(name = "Assinatura horizontal", widthDp = 850, heightDp = 390, locale = "pt-rBR")
@Preview(name = "Assinatura tablet", widthDp = 1280, heightDp = 800, locale = "pt-rBR")
@Composable
private fun DocumentSignaturePlacementPreview() {
    SignaturePreviewContent(hasDraft = true)
}

@Preview(name = "Escolher posição tablet", widthDp = 1280, heightDp = 800, locale = "pt-rBR")
@Preview(
    name = "Escolher posição fonte ampliada", widthDp = 360, heightDp = 800,
    fontScale = 2f, locale = "pt-rBR",
)
@Composable
private fun DocumentSignatureAwaitingPlacementPreview() {
    SignaturePreviewContent(hasDraft = true, awaitingPlacement = true)
}

@Preview(name = "Tamanho da assinatura", widthDp = 390, heightDp = 850, locale = "pt-rBR")
@Preview(
    name = "Tamanho fonte ampliada", widthDp = 360, heightDp = 640,
    fontScale = 1.3f, locale = "pt-rBR",
)
@Preview(name = "Tamanho horizontal", widthDp = 850, heightDp = 390, locale = "pt-rBR")
@Composable
private fun DocumentSignatureSizePreview() {
    SignaturePreviewContent(hasDraft = true, adjustmentMode = SignatureAdjustmentMode.SIZE)
}

@Preview(name = "Girar assinatura", widthDp = 390, heightDp = 850, locale = "pt-rBR")
@Preview(
    name = "Girar fonte ampliada", widthDp = 360, heightDp = 640,
    fontScale = 1.3f, locale = "pt-rBR",
)
@Preview(name = "Girar horizontal", widthDp = 850, heightDp = 390, locale = "pt-rBR")
@Composable
private fun DocumentSignatureRotationPreview() {
    SignaturePreviewContent(hasDraft = true, adjustmentMode = SignatureAdjustmentMode.ROTATE)
}

@Preview(name = "Carregando documento", widthDp = 390, heightDp = 850, locale = "pt-rBR")
@Preview(name = "Carregando documento tablet", widthDp = 1280, heightDp = 800, locale = "pt-rBR")
@Composable
private fun DocumentSignatureLoadingPreview() {
    SignaturePreviewContent(hasDraft = false, loadingDocument = true)
}

@Preview(name = "Preparando página", widthDp = 390, heightDp = 850, locale = "pt-rBR")
@Preview(name = "Preparando página escuro", widthDp = 390, heightDp = 850, locale = "pt-rBR", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Preparando página fonte ampliada", widthDp = 360, heightDp = 800, locale = "pt-rBR", fontScale = 2f)
@Composable
private fun DocumentSignaturePageLoadingPreview() {
    SignaturePreviewContent(hasDraft = false, loadingPage = true)
}

@Composable
private fun SignaturePreviewContent(
    hasDraft: Boolean,
    adjustmentMode: SignatureAdjustmentMode = SignatureAdjustmentMode.MOVE,
    awaitingPlacement: Boolean = false,
    loadingDocument: Boolean = false,
    loadingPage: Boolean = false,
) {
    val document = DocumentModel(
        id = 1, name = "Contrato de aluguel", pdfUri = "preview.pdf", pageCount = 2,
        createdAt = 1781431200000L, sizeBytes = 248_000L, thumbnailUri = null, tagId = null,
    )
    val draft = PdfSignaturePlacement(
        id = "signature", imageUri = "preview-signature.png",
        x = .24f, y = .56f, width = .52f, height = .12f, rotationDegrees = -12f,
    )
    val pages = listOf(
        PdfPageSpec(id = "one", sourceUri = "preview.pdf", sourcePageIndex = 0),
        PdfPageSpec(id = "two", sourceUri = "preview.pdf", sourcePageIndex = 1),
    )
    ScaniqoTheme {
        DocumentSignatureScreen(
            documentId = 1,
            state = DocumentToolsUiState(
                document = document,
                workspace = PdfWorkspace(
                    documentId = 1, workspaceId = "preview", directoryUri = "preview", pages = pages,
                ),
                selectedPageId = "one",
                pagePreviews = if (loadingPage) emptyMap() else mapOf("one" to DocumentPagePreviewState(PdfPagePreview("preview.pdf", 0))),
                signatures = listOf(
                    SignatureModel("one", "Minha assinatura", "preview-signature.png", 3.2f),
                    SignatureModel("two", "Rubrica", "preview-signature.png", 1.8f),
                ),
                signatureDraft = draft.takeIf { hasDraft },
                signaturePageId = "one".takeIf { hasDraft },
                signatureAwaitingPlacement = awaitingPlacement,
            ),
            documentName = document.name,
            isLoadingDocument = loadingDocument,
            documentError = false,
            onEvent = {},
            onBack = {},
            onCreateSignature = {},
            onRetry = {},
            onApply = {},
            initialAdjustmentMode = adjustmentMode,
            initiallyShowControls = hasDraft && adjustmentMode != SignatureAdjustmentMode.MOVE,
        )
    }
}
