package com.joseleandro.scaniqo.ui.documentEditor.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.joseleandro.scaniqo.domain.model.PdfOverlay
import com.joseleandro.scaniqo.domain.model.PdfOverlayKind
import com.joseleandro.scaniqo.domain.model.PdfPagePreview
import com.joseleandro.scaniqo.domain.model.PdfPageSpec
import com.joseleandro.scaniqo.domain.model.PdfWorkspace
import com.joseleandro.scaniqo.ui.documentEditor.model.DocumentTool
import com.joseleandro.scaniqo.ui.documentEditor.state.DocumentPagePreviewState
import com.joseleandro.scaniqo.ui.documentEditor.state.DocumentToolsUiState
import com.joseleandro.scaniqo.ui.theme.ScaniqoTheme

/** O tamanho reduzido representa o espaço disponível acima do teclado aberto. */
@Preview(name = "Preencher — digitando", widthDp = 360, heightDp = 400, locale = "pt-rBR")
@Preview(name = "Preencher — digitando compacto", widthDp = 320, heightDp = 360, fontScale = 1.5f, locale = "pt-rBR")
@Composable
private fun DocumentInlineFillPreview() {
    val page = PdfPageSpec("inline-preview", "preview.pdf", 0)
    val draft = PdfOverlay("inline-text", PdfOverlayKind.TEXT, text = "Maria Silva",
        x = .18f, y = .57f, width = .58f, height = .06f, color = 0xFF1565C0.toInt())
    val state = DocumentToolsUiState(
        workspace = PdfWorkspace(1, "inline-workspace", "preview", listOf(page)),
        selectedPageId = page.id, tool = DocumentTool.FORMS,
        pagePreviews = mapOf(page.id to DocumentPagePreviewState(PdfPagePreview("preview.pdf"))),
    )
    ScaniqoTheme {
        Surface {
            Column(Modifier.fillMaxSize()) {
                DocumentFillMarkStage(
                    state, page, draft, dirty = true,
                    excludedId = null, editingText = true, focusRequest = 1,
                    onUpdate = {}, onSelect = {}, onInsert = {}, onStopTyping = {}, onEvent = {},
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
                FillMarkControls(
                    active = true, mode = PdfOverlayKind.TEXT, textElement = true, editingText = true,
                    editable = true, canApply = true, onMode = {}, onApply = {}, onCancel = {},
                    onProperties = {}, onEditText = {}, onMore = {}, onDone = {},
                    modifier = Modifier.fillMaxWidth(), draft = draft,
                )
            }
        }
    }
}
