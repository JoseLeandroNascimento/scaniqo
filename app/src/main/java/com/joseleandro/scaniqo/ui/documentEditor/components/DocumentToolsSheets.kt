package com.joseleandro.scaniqo.ui.documentEditor.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import com.joseleandro.scaniqo.ui.components.DialogActions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import com.joseleandro.scaniqo.R
import com.joseleandro.scaniqo.domain.model.PdfPageSpec
import com.joseleandro.scaniqo.domain.model.PdfWorkspace
import com.joseleandro.scaniqo.ui.documentEditor.event.DocumentToolsEvent
import com.joseleandro.scaniqo.ui.documentEditor.model.DocumentTool
import com.joseleandro.scaniqo.ui.documentEditor.state.DocumentToolsUiState
import com.joseleandro.scaniqo.ui.documentEditor.util.titleRes
import com.joseleandro.scaniqo.ui.theme.ScaniqoTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DocumentToolsSheets(
    state: DocumentToolsUiState,
    onEvent: (DocumentToolsEvent) -> Unit,
) {
    val tool = state.tool
    if ((tool == DocumentTool.QUALITY || tool == DocumentTool.PROTECTION) &&
        state.workspace != null && !state.isLocked) {
        ModalBottomSheet(
            onDismissRequest = { if (!state.isBusy) onEvent(DocumentToolsEvent.DismissTool) },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            DocumentToolSheetContent(state, onEvent)
        }
    }
    if (state.pendingDeletePageId != null) {
        AlertDialog(
            onDismissRequest = { onEvent(DocumentToolsEvent.DismissPageDeletion) },
            title = { Text(stringResource(R.string.tools_delete_title)) },
            text = { Text(stringResource(R.string.tools_delete_body)) },
            confirmButton = {
                DialogActions(
                    confirmText = stringResource(R.string.tools_delete),
                    onConfirm = { onEvent(DocumentToolsEvent.ConfirmDeletePage) },
                    dismissText = stringResource(R.string.tools_cancel),
                    onDismiss = { onEvent(DocumentToolsEvent.DismissPageDeletion) },
                    confirmEnabled = state.canEdit,
                    destructive = true,
                )
            },
        )
    }
    if (state.confirmDiscard) {
        AlertDialog(
            onDismissRequest = { onEvent(DocumentToolsEvent.CancelDiscard) },
            title = { Text(stringResource(R.string.tools_discard_title)) },
            text = { Text(stringResource(R.string.tools_discard_body)) },
            confirmButton = {
                DialogActions(
                    confirmText = stringResource(R.string.tools_discard),
                    onConfirm = { onEvent(DocumentToolsEvent.ConfirmDiscard) },
                    dismissText = stringResource(R.string.tools_keep_editing),
                    onDismiss = { onEvent(DocumentToolsEvent.CancelDiscard) },
                    confirmEnabled = !state.isBusy,
                    destructive = true,
                )
            },
        )
    }
}

@Composable
private fun DocumentToolSheetContent(
    state: DocumentToolsUiState,
    onEvent: (DocumentToolsEvent) -> Unit,
) {
    val tool = state.tool ?: return
    Column(
        modifier = Modifier.fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(
                    when (tool) {
                        DocumentTool.FILTERS -> R.string.tools_filters_title
                        DocumentTool.MERGE -> R.string.merge_position_title
                        else -> tool.titleRes()
                    },
                ),
                modifier = Modifier.weight(1f).semantics { heading() },
                style = MaterialTheme.typography.titleLarge,
            )
            IconButton(
                onClick = { onEvent(DocumentToolsEvent.DismissTool) },
                enabled = !state.isBusy,
            ) {
                Icon(painterResource(R.drawable.ic_toast_close), stringResource(R.string.tool_sheet_close))
            }
        }
        when (tool) {
            DocumentTool.LAYOUT -> Unit // Ajustar usa a tela dedicada.
            DocumentTool.FILTERS -> Unit // Os filtros ficam na faixa do visualizador.
            DocumentTool.CORRECTIONS -> Unit // Corrigir usa a tela dedicada.
            DocumentTool.QUALITY -> DocumentQualitySheetContent(state, onEvent)
            DocumentTool.PAGES -> Unit // Páginas usa a tela dedicada.
            DocumentTool.MERGE -> Unit // Mesclagem usa a tela compartilhada.
            DocumentTool.SIGNATURES -> Unit // Assinaturas usam a tela dedicada.
            DocumentTool.PROTECTION -> ProtectionOptions(state, onEvent)
            DocumentTool.FORMS, DocumentTool.ANNOTATIONS, DocumentTool.WATERMARK, DocumentTool.REDACTION -> Unit
        }
    }
}

@Composable
private fun ProtectionOptions(state: DocumentToolsUiState, onEvent: (DocumentToolsEvent) -> Unit) {
    val workspace = state.workspace ?: return
    Column(
        Modifier.fillMaxWidth().heightIn(max = 640.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(
                Modifier.fillMaxWidth()
                    .toggleable(
                        value = workspace.isProtected,
                        enabled = state.canEdit,
                        role = Role.Switch,
                        onValueChange = { onEvent(DocumentToolsEvent.SetProtection(it)) },
                    ).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        stringResource(R.string.tools_protect_title),
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Switch(checked = workspace.isProtected, onCheckedChange = null, enabled = state.canEdit)
                }
                Text(
                    stringResource(R.string.tools_protect_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        OutlinedButton(
            onClick = { onEvent(DocumentToolsEvent.PasswordSettings) },
            enabled = state.canEdit,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        ) { Text(stringResource(R.string.tools_password_settings)) }
    }
}

/** O mesmo conteúdo do sheet é verificado com pouco espaço e fonte ampliada. */
private class DocumentToolPreviewProvider : PreviewParameterProvider<DocumentTool> {
    override val values = sequenceOf(
        DocumentTool.QUALITY, DocumentTool.PROTECTION,
    )
}

@Preview(name = "Ferramentas — tela compacta", widthDp = 360, heightDp = 640, fontScale = 1.3f, showBackground = true)
@Composable
private fun DocumentToolSheetContentPreview(
    @PreviewParameter(DocumentToolPreviewProvider::class) tool: DocumentTool,
) {
    ScaniqoTheme {
        Surface {
            DocumentToolSheetContent(
                state = DocumentToolsUiState(
                    workspace = PdfWorkspace(
                        1, "preview", "preview",
                        (1..8).map { PdfPageSpec("page-$it", "preview", it - 1) },
                    ),
                    selectedPageId = "page-1",
                    tool = tool,
                ),
                onEvent = {},
            )
        }
    }
}
