package com.joseleandro.scaniqo.ui.documentEditor.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import com.joseleandro.scaniqo.R
import com.joseleandro.scaniqo.domain.model.DocumentModel
import com.joseleandro.scaniqo.domain.model.PdfPagePreview
import com.joseleandro.scaniqo.domain.model.PdfPageSpec
import com.joseleandro.scaniqo.domain.model.PdfWorkspace
import com.joseleandro.scaniqo.domain.model.SignatureModel
import com.joseleandro.scaniqo.ui.documentEditor.event.DocumentToolsEvent
import com.joseleandro.scaniqo.ui.documentEditor.model.DocumentTool
import com.joseleandro.scaniqo.ui.documentEditor.state.DocumentToolsUiState
import com.joseleandro.scaniqo.ui.documentEditor.util.titleRes
import com.joseleandro.scaniqo.ui.signatures.SignatureImage
import com.joseleandro.scaniqo.ui.theme.ScaniqoTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DocumentToolsSheets(
    state: DocumentToolsUiState,
    onEvent: (DocumentToolsEvent) -> Unit,
    documents: List<DocumentModel> = emptyList(),
) {
    val tool = state.tool
    if (tool != null && tool != DocumentTool.FILTERS && state.workspace != null && !state.isLocked) {
        ModalBottomSheet(
            onDismissRequest = { if (!state.isBusy) onEvent(DocumentToolsEvent.DismissTool) },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            DocumentToolSheetContent(state, onEvent, documents)
        }
    }
    if (state.pendingDeletePageId != null) {
        AlertDialog(
            onDismissRequest = { onEvent(DocumentToolsEvent.DismissPageDeletion) },
            title = { Text(stringResource(R.string.tools_delete_title)) },
            text = { Text(stringResource(R.string.tools_delete_body)) },
            confirmButton = {
                TextButton(onClick = { onEvent(DocumentToolsEvent.ConfirmDeletePage) }, enabled = state.canEdit) {
                    Text(stringResource(R.string.tools_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { onEvent(DocumentToolsEvent.DismissPageDeletion) }) {
                    Text(stringResource(R.string.tools_cancel))
                }
            },
        )
    }
    if (state.confirmDiscard) {
        AlertDialog(
            onDismissRequest = { onEvent(DocumentToolsEvent.CancelDiscard) },
            title = { Text(stringResource(R.string.tools_discard_title)) },
            text = { Text(stringResource(R.string.tools_discard_body)) },
            confirmButton = {
                TextButton(onClick = { onEvent(DocumentToolsEvent.ConfirmDiscard) }, enabled = !state.isBusy) {
                    Text(stringResource(R.string.tools_discard), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { onEvent(DocumentToolsEvent.CancelDiscard) }) {
                    Text(stringResource(R.string.tools_keep_editing))
                }
            },
        )
    }
}

@Composable
private fun DocumentToolSheetContent(
    state: DocumentToolsUiState,
    onEvent: (DocumentToolsEvent) -> Unit,
    documents: List<DocumentModel> = emptyList(),
) {
    val tool = state.tool ?: return
    val interactive = tool == DocumentTool.LAYOUT || tool == DocumentTool.CORRECTIONS
    Column(
        modifier = Modifier.fillMaxWidth()
            .padding(horizontal = if (interactive) 12.dp else 20.dp)
            .padding(bottom = if (interactive) 8.dp else 20.dp),
        verticalArrangement = Arrangement.spacedBy(if (interactive) 8.dp else 20.dp),
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
            DocumentTool.LAYOUT -> DocumentLayoutSheetContent(state, onEvent)
            DocumentTool.FILTERS -> Unit // Os filtros ficam na faixa do visualizador.
            DocumentTool.CORRECTIONS -> DocumentCorrectionsSheetContent(state, onEvent)
            DocumentTool.QUALITY -> DocumentQualitySheetContent(state, onEvent)
            DocumentTool.PAGES -> DocumentPagesSheetContent(state, onEvent)
            DocumentTool.MERGE -> DocumentMergeSheetContent(state, onEvent, documents)
            DocumentTool.SIGNATURES -> SignatureOptions(state, onEvent)
            DocumentTool.PROTECTION -> ProtectionOptions(state, onEvent)
        }
    }
}

/** A biblioteca e as assinaturas da página rolam juntas, inclusive com fonte ampliada. */
@Composable
private fun SignatureOptions(state: DocumentToolsUiState, onEvent: (DocumentToolsEvent) -> Unit) {
    val placed = state.workspace?.pages?.getOrNull(state.selectedPageIndex)?.signatures.orEmpty()
    LazyColumn(
        modifier = Modifier.fillMaxWidth().heightIn(max = 660.dp),
        contentPadding = PaddingValues(bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                stringResource(R.string.tools_signatures_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            OutlinedButton(
                onClick = { onEvent(DocumentToolsEvent.ManageSignatures) },
                enabled = state.canEdit,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) { Text(stringResource(R.string.tools_manage_signatures)) }
        }
        item {
            Text(
                stringResource(R.string.tool_sheet_saved_signatures),
                modifier = Modifier.padding(top = 12.dp).semantics { heading() },
                style = MaterialTheme.typography.titleSmall,
            )
        }
        if (state.signatures.isEmpty()) {
            item {
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Text(
                        stringResource(R.string.tools_signatures_empty),
                        Modifier.fillMaxWidth().padding(20.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        items(state.signatures, key = { "library_" + it.id }) { signature ->
            Surface(
                onClick = { onEvent(DocumentToolsEvent.InsertSignature(signature.id)) },
                enabled = state.canEdit,
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp).padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SignatureImage(signature.imageUri, Modifier.width(80.dp).height(48.dp))
                    Text(
                        signature.name,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Icon(painterResource(R.drawable.add_24px), null)
                }
            }
        }
        if (placed.isNotEmpty()) {
            item {
                Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    HorizontalDivider()
                    Text(
                        stringResource(R.string.tool_sheet_placed_signatures),
                        Modifier.semantics { heading() },
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
            }
        }
        items(placed, key = { "placed_" + it.id }) { placement ->
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp).padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SignatureImage(placement.imageUri, Modifier.weight(1f).height(56.dp))
                    IconButton(
                        onClick = { onEvent(DocumentToolsEvent.EditSignature(state.selectedPageId ?: return@IconButton, placement.id)) },
                        enabled = state.canEdit,
                    ) {
                        Icon(painterResource(R.drawable.edit_24px), stringResource(R.string.tools_signature_edit, placed.indexOf(placement) + 1))
                    }
                    IconButton(
                        onClick = { onEvent(DocumentToolsEvent.RemoveSignature(state.selectedPageId ?: return@IconButton, placement.id)) },
                        enabled = state.canEdit,
                    ) {
                        Icon(painterResource(R.drawable.delete_24px), stringResource(R.string.tools_signature_remove), tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
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
        DocumentTool.LAYOUT, DocumentTool.CORRECTIONS,
        DocumentTool.PAGES, DocumentTool.MERGE, DocumentTool.QUALITY,
        DocumentTool.SIGNATURES, DocumentTool.PROTECTION,
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
                    correctionsPreview = PdfPagePreview("preview", 0),
                    correctionsPreviewPageId = "page-1",
                    signatures = listOf(SignatureModel("signature", "Assinatura pessoal", "preview", 3f)),
                ),
                onEvent = {},
            )
        }
    }
}
