package com.joseleandro.scaniqo.ui.documentEditor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.joseleandro.scaniqo.R
import com.joseleandro.scaniqo.domain.model.DocumentModel
import com.joseleandro.scaniqo.domain.model.PdfPagePreview
import com.joseleandro.scaniqo.domain.model.PdfPageSpec
import com.joseleandro.scaniqo.domain.model.PdfWorkspace
import com.joseleandro.scaniqo.ui.documentCommon.components.DocumentStatus
import com.joseleandro.scaniqo.ui.documentEditor.components.DocumentCorrectionsSheetContent
import com.joseleandro.scaniqo.ui.documentEditor.components.DocumentLayoutSheetContent
import com.joseleandro.scaniqo.ui.documentEditor.components.DocumentPagesSheetContent
import com.joseleandro.scaniqo.ui.documentEditor.components.DocumentOverlayEditor
import com.joseleandro.scaniqo.ui.documentEditor.event.DocumentToolsEvent
import com.joseleandro.scaniqo.ui.documentEditor.model.DocumentTool
import com.joseleandro.scaniqo.ui.documentEditor.state.DocumentPagePreviewState
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
    onFinish: () -> Unit = onBack,
    snackbarHost: @Composable () -> Unit = {},
    hasLocalDraft: Boolean = false,
) {
    val sameSession = state.document?.id == documentId
    val workspace = state.workspace?.takeIf { sameSession }
    val drafts = rememberSaveableStateHolder()
    val interactive = if (enabled) state else state.copy(isLoading = true)
    val canBack = enabled && (!state.isBusy || state.isLoading)
    var primaryAction by remember(documentId, tool) { mutableStateOf<(() -> Unit)?>(null) }
    val registerPrimaryAction = remember(documentId, tool) {
        { action: (() -> Unit)? -> primaryAction = action }
    }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(tool.titleRes()), style = MaterialTheme.typography.titleMedium,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (imeVisible) {
                            keyboard?.hide(); focusManager.clearFocus()
                        } else onBack()
                    }, enabled = canBack) {
                        Icon(painterResource(R.drawable.arrow_back_24px), stringResource(R.string.editor_tool_back))
                    }
                },
                actions = {
                    if (tool in setOf(DocumentTool.PAGES, DocumentTool.ANNOTATIONS) && sameSession) {
                        val undoLabel = stringResource(R.string.viewer_undo_description)
                        TooltipBox(
                            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below),
                            tooltip = { PlainTooltip { Text(undoLabel) } },
                            state = rememberTooltipState(),
                        ) {
                            IconButton(
                                onClick = { onEvent(DocumentToolsEvent.Undo) },
                                enabled = enabled && state.canEdit && state.canUndo && !hasLocalDraft,
                            ) {
                                Icon(painterResource(R.drawable.ic_document_undo), undoLabel)
                            }
                        }
                        val redoLabel = stringResource(R.string.viewer_redo_description)
                        TooltipBox(
                            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below),
                            tooltip = { PlainTooltip { Text(redoLabel) } },
                            state = rememberTooltipState(),
                        ) {
                            IconButton(
                                onClick = { onEvent(DocumentToolsEvent.Redo) },
                                enabled = enabled && state.canEdit && state.canRedo && !hasLocalDraft,
                            ) {
                                Icon(painterResource(R.drawable.ic_document_redo), redoLabel)
                            }
                        }
                    }
                    if (tool in setOf(DocumentTool.FORMS, DocumentTool.WATERMARK,
                            DocumentTool.LAYOUT, DocumentTool.CORRECTIONS) && sameSession) {
                        TextButton(
                            onClick = { primaryAction?.invoke() },
                            enabled = enabled && state.canEdit && primaryAction != null,
                        ) {
                            Text(stringResource(if (tool == DocumentTool.LAYOUT || tool == DocumentTool.CORRECTIONS)
                                R.string.tools_apply_compact else R.string.editor_tool_done))
                        }
                    }
                },
            )
        },
        snackbarHost = snackbarHost,
    ) { padding ->
        Box(
            Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()
                .padding(horizontal = if (tool == DocumentTool.FORMS) 0.dp else 12.dp,
                    vertical = if (tool == DocumentTool.FORMS) 0.dp else 8.dp),
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
                            registerPrimaryAction = registerPrimaryAction,
                        )
                        DocumentTool.CORRECTIONS -> DocumentCorrectionsSheetContent(
                            interactive, onEvent, Modifier.fillMaxSize(), onDraftChanged,
                            registerPrimaryAction = registerPrimaryAction,
                        )
                        DocumentTool.PAGES -> Column(
                            Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            DocumentPagesSheetContent(interactive, onEvent, Modifier.weight(1f))
                            Button(onClick = onBack, enabled = canBack, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.editor_tool_done))
                            }
                        }
                        DocumentTool.FORMS, DocumentTool.ANNOTATIONS, DocumentTool.WATERMARK, DocumentTool.REDACTION ->
                            DocumentOverlayEditor(interactive, tool, onEvent, onDraftChanged, onBack,
                                registerPrimaryAction = registerPrimaryAction, onFinish = onFinish)
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

@Preview(name = "Preencher — tela", widthDp = 390, heightDp = 780, locale = "pt-rBR")
@Preview(name = "Preencher — fonte ampliada", widthDp = 320, heightDp = 640, fontScale = 1.5f, locale = "pt-rBR")
@Composable
private fun DocumentFormsScreenPreview() = DocumentEditorToolPreview(DocumentTool.FORMS)

@Preview(name = "Anotações — tela", widthDp = 390, heightDp = 780, locale = "pt-rBR")
@Composable
private fun DocumentAnnotationsScreenPreview() = DocumentEditorToolPreview(DocumentTool.ANNOTATIONS)

@Preview(name = "Marcas — tela", widthDp = 390, heightDp = 780, locale = "pt-rBR")
@Composable
private fun DocumentWatermarkScreenPreview() = DocumentEditorToolPreview(DocumentTool.WATERMARK)

@Preview(name = "Censurar — tela", widthDp = 390, heightDp = 780, locale = "pt-rBR")
@Composable
private fun DocumentRedactionScreenPreview() = DocumentEditorToolPreview(DocumentTool.REDACTION)

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
                pagePreviews = mapOf("page-1" to DocumentPagePreviewState(preview = PdfPagePreview("preview.pdf", 0))),
                tool = tool,
            ),
            documentLoading = false, documentError = false, enabled = true,
            onEvent = {}, onDraftChanged = {}, onBack = {}, onRetry = {},
        )
    }
}
