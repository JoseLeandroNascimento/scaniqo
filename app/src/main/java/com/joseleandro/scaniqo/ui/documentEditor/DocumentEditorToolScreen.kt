package com.joseleandro.scaniqo.ui.documentEditor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joseleandro.scaniqo.R
import com.joseleandro.scaniqo.domain.model.DocumentModel
import com.joseleandro.scaniqo.domain.model.PdfPageSpec
import com.joseleandro.scaniqo.domain.model.PdfWorkspace
import com.joseleandro.scaniqo.ui.documentCommon.components.DocumentStatus
import com.joseleandro.scaniqo.ui.documentEditor.components.DocumentCorrectionsSheetContent
import com.joseleandro.scaniqo.ui.documentEditor.components.DocumentLayoutSheetContent
import com.joseleandro.scaniqo.ui.documentEditor.components.DocumentPagesSheetContent
import com.joseleandro.scaniqo.ui.documentEditor.event.DocumentToolsEvent
import com.joseleandro.scaniqo.ui.documentEditor.model.DocumentTool
import com.joseleandro.scaniqo.ui.documentEditor.state.DocumentToolsUiState
import com.joseleandro.scaniqo.ui.documentEditor.util.titleRes
import com.joseleandro.scaniqo.ui.theme.ScaniqoTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DocumentEditorToolScreen(
    documentId: Long,
    tool: DocumentTool,
    state: DocumentToolsUiState,
    documentLoading: Boolean,
    documentError: Boolean,
    enabled: Boolean,
    onEvent: (DocumentToolsEvent) -> Unit,
    onDraftChanged: (Boolean) -> Unit,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    snackbarHost: @Composable () -> Unit = {},
) {
    val sameSession = state.document?.id == documentId
    val workspace = state.workspace?.takeIf { sameSession }
    val drafts = rememberSaveableStateHolder()
    val interactive = if (enabled) state else state.copy(isLoading = true)
    val canBack = enabled && (!state.isBusy || state.isLoading)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(tool.titleRes())) },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = canBack) {
                        Icon(painterResource(R.drawable.arrow_back_24px), stringResource(R.string.editor_tool_back))
                    }
                },
                actions = {
                    if (tool == DocumentTool.PAGES && state.canUndo && sameSession) {
                        IconButton(onClick = { onEvent(DocumentToolsEvent.Undo) }, enabled = enabled && state.canEdit) {
                            Icon(painterResource(R.drawable.ic_document_undo), stringResource(R.string.viewer_undo_description))
                        }
                    }
                },
            )
        },
        snackbarHost = snackbarHost,
    ) { padding ->
        Box(
            Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            when {
                sameSession && state.isLocked -> DocumentStatus(
                    title = stringResource(R.string.tools_locked_title),
                    description = stringResource(if (state.recoveryRequired) R.string.tools_recovery_description else R.string.tools_locked_description),
                    actionLabel = stringResource(if (state.recoveryRequired) R.string.tools_recovery_action else R.string.tools_unlock),
                    onAction = {
                        onEvent(if (state.recoveryRequired) DocumentToolsEvent.RequestPasswordRecovery else DocumentToolsEvent.Unlock)
                    },
                )
                documentError || (sameSession && state.loadError) -> DocumentStatus(
                    title = stringResource(R.string.tools_load_error),
                    description = stringResource(R.string.documents_error_body),
                    actionLabel = stringResource(R.string.documents_retry),
                    onAction = onRetry,
                )
                documentLoading || workspace == null || state.isLoading -> Box(
                    Modifier.fillMaxSize(), contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
                else -> drafts.SaveableStateProvider("${workspace.workspaceId}:${tool.name}") {
                    when (tool) {
                        DocumentTool.LAYOUT -> DocumentLayoutSheetContent(
                            interactive, onEvent, Modifier.fillMaxSize(), onDraftChanged,
                        )
                        DocumentTool.CORRECTIONS -> DocumentCorrectionsSheetContent(
                            interactive, onEvent, Modifier.fillMaxSize(), onDraftChanged,
                        )
                        DocumentTool.PAGES -> Column(
                            Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            DocumentPagesSheetContent(interactive, onEvent, Modifier.weight(1f))
                            Button(onClick = onBack, enabled = canBack, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.editor_tool_done))
                            }
                        }
                        else -> Unit
                    }
                }
            }
        }
    }
}

@Preview(name = "Ajustar — tela", widthDp = 390, heightDp = 780, showBackground = true)
@Preview(name = "Ajustar — paisagem", widthDp = 760, heightDp = 390, showBackground = true)
@Composable
private fun DocumentLayoutScreenPreview() = DocumentEditorToolPreview(DocumentTool.LAYOUT)

@Preview(name = "Corrigir — tela", widthDp = 390, heightDp = 780, showBackground = true)
@Preview(name = "Corrigir — paisagem", widthDp = 760, heightDp = 390, showBackground = true)
@Composable
private fun DocumentCorrectionsScreenPreview() = DocumentEditorToolPreview(DocumentTool.CORRECTIONS)

@Preview(name = "Páginas — tela", widthDp = 390, heightDp = 780, showBackground = true)
@Preview(name = "Páginas — fonte ampliada", widthDp = 360, heightDp = 780, fontScale = 1.3f, showBackground = true)
@Composable
private fun DocumentPagesScreenPreview() = DocumentEditorToolPreview(DocumentTool.PAGES)

@Composable
private fun DocumentEditorToolPreview(tool: DocumentTool) {
    ScaniqoTheme {
        DocumentEditorToolScreen(
            documentId = 1,
            tool = tool,
            state = DocumentToolsUiState(
                document = DocumentModel(1, "Contrato", "preview.pdf", 4, 0, 0),
                workspace = PdfWorkspace(1, "preview", "preview", (1..4).map { PdfPageSpec("page-$it", "preview.pdf", it - 1) }),
                selectedPageId = "page-1",
                tool = tool,
            ),
            documentLoading = false, documentError = false, enabled = true,
            onEvent = {}, onDraftChanged = {}, onBack = {}, onRetry = {},
        )
    }
}
