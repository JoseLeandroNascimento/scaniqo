package com.joseleandro.scaniqo.ui.documentMerge

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joseleandro.scaniqo.R
import com.joseleandro.scaniqo.domain.model.DocumentModel
import com.joseleandro.scaniqo.ui.documentCommon.components.DocumentMergeSelectionContent
import com.joseleandro.scaniqo.ui.documentCommon.model.DocumentMergeItem
import com.joseleandro.scaniqo.ui.documentCommon.model.toMergeItem
import com.joseleandro.scaniqo.ui.documentCommon.state.DocumentMergeSelectionState
import com.joseleandro.scaniqo.ui.documentMerge.model.DocumentMergeTarget
import com.joseleandro.scaniqo.ui.theme.ScaniqoTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DocumentMergeScreen(
    documents: List<DocumentModel>,
    selection: DocumentMergeSelectionState,
    target: DocumentMergeTarget?,
    enabled: Boolean,
    canNavigateBack: Boolean,
    canConfirm: Boolean,
    isLoading: Boolean,
    loadError: Boolean,
    error: String?,
    onRetry: () -> Unit,
    snackbarHost: @Composable () -> Unit = {},
    onUnlock: (() -> Unit)? = null,
    onBack: () -> Unit,
    onPickFiles: () -> Unit,
    onConfirm: (List<DocumentMergeItem>) -> Unit,
) {
    Scaffold(
        snackbarHost = snackbarHost,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.merge_selection_title), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = canNavigateBack) {
                        Icon(painterResource(R.drawable.arrow_back_24px), stringResource(R.string.merge_screen_back))
                    }
                },
                actions = {
                    IconButton(onClick = onPickFiles, enabled = enabled) {
                        Icon(
                            painterResource(R.drawable.ic_document_import),
                            contentDescription = stringResource(R.string.merge_screen_add_files),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()
                .padding(horizontal = 16.dp).padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (isLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (loadError) {
                Row(Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(R.string.merge_screen_library_error),
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    TextButton(onClick = onRetry) { Text(stringResource(R.string.documents_retry)) }
                }
            }
            if (error != null) Text(error, color = MaterialTheme.colorScheme.error)
            if (onUnlock != null) {
                OutlinedButton(onClick = onUnlock, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.tools_unlock))
                }
            }
            DocumentMergeSelectionContent(
                documents = documents,
                selection = selection,
                currentDocumentId = target?.documentId,
                enabled = enabled,
                minimumSelection = 2,
                canConfirm = canConfirm,
                footerLabel = if (target == null) stringResource(R.string.merge_result_new_document) else null,
                modifier = Modifier.weight(1f),
                onPickFiles = onPickFiles,
                onConfirm = onConfirm,
            )
        }
    }
}

@Preview(name = "Mesclar PDFs", widthDp = 390, heightDp = 780, showBackground = true)
@Composable
private fun DocumentMergeScreenPreview() {
    DocumentMergePreviewContent()
}

@Preview(name = "Inserir PDFs — fonte ampliada", widthDp = 360, heightDp = 780, fontScale = 1.3f, showBackground = true)
@Preview(name = "Inserir PDFs — paisagem", widthDp = 720, heightDp = 360, showBackground = true)
@Composable
private fun DocumentMergeEditorScreenPreview() {
    DocumentMergePreviewContent(DocumentMergeTarget(documentId = 4, pageCount = 8, selectedPageIndex = 2))
}

@Composable
private fun DocumentMergePreviewContent(target: DocumentMergeTarget? = null) {
    val documents = listOf(
        DocumentModel(1, "Contrato", "file:///contract.pdf", 3, 0, 0),
        DocumentModel(2, "Comprovantes", "file:///receipts.pdf", 5, 0, 0),
        DocumentModel(3, "Notas", "file:///notes.pdf", 2, 0, 0),
    )
    val current = target?.let {
        DocumentMergeItem("file:///current.pdf", "Documento em edição", it.documentId, it.pageCount, isCurrentDocument = true)
    }
    val selection = remember {
        DocumentMergeSelectionState(listOfNotNull(current) + documents.take(2).map(DocumentModel::toMergeItem))
    }
    ScaniqoTheme {
        DocumentMergeScreen(
            documents, selection, target,
            enabled = true, canNavigateBack = true, canConfirm = true,
            isLoading = false, loadError = false, error = null,
            onRetry = {},
            onBack = {}, onPickFiles = {}, onConfirm = {},
        )
    }
}

@Preview(name = "Mesclar — seleção vazia", widthDp = 390, heightDp = 780, showBackground = true)
@Composable
private fun DocumentMergeEmptyScreenPreview() {
    val selection = remember { DocumentMergeSelectionState() }
    ScaniqoTheme {
        DocumentMergeScreen(
            documents = emptyList(), selection = selection, target = null,
            enabled = true, canNavigateBack = true, canConfirm = true,
            isLoading = false, loadError = false, error = null,
            onRetry = {}, onBack = {}, onPickFiles = {}, onConfirm = {},
        )
    }
}
