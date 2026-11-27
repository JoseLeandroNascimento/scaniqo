package com.joseleandro.scaniqo.ui.documents.components

import com.joseleandro.scaniqo.core.resources.R as CoreResourcesR
import com.joseleandro.scaniqo.feature.library.R

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import com.joseleandro.scaniqo.ui.components.ScaniqoLoadingIndicator
import com.joseleandro.scaniqo.ui.components.ScaniqoFieldDefaults
import com.joseleandro.scaniqo.ui.theme.ScaniqoTheme
import com.joseleandro.scaniqo.ui.documentCommon.components.DocumentThumbnail
import com.joseleandro.scaniqo.ui.documentCommon.components.PdfSelectionCard
import com.joseleandro.scaniqo.ui.documentCommon.components.PdfSelectAction
import com.joseleandro.scaniqo.ui.documentCommon.components.pdfSelectionMetadata
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import com.joseleandro.scaniqo.domain.model.DocumentModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentSignaturePicker(
    documents: List<DocumentModel>,
    isLoading: Boolean,
    loadError: Boolean,
    onRetry: () -> Unit,
    onImport: () -> Unit,
    onDismiss: () -> Unit,
    onSelect: (Long) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val visible = documents.filter { it.name.contains(query.trim(), ignoreCase = true) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().imePadding(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "header") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.quick_sign_choose), style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.quick_sign_description), style = MaterialTheme.typography.bodyMedium)
                }
            }
            item(key = "search") {
                OutlinedTextField(
                    value = query, onValueChange = { query = it },
                    label = { Text(stringResource(R.string.quick_sign_search)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    shape = ScaniqoFieldDefaults.shape,
                )
            }
            item(key = "import") {
                PdfSelectAction(
                    title = stringResource(CoreResourcesR.string.quick_tools_import),
                    hint = stringResource(CoreResourcesR.string.quick_tools_import_hint),
                    onClick = onImport,
                )
            }
            when {
                isLoading -> item {
                    ScaniqoLoadingIndicator(Modifier.fillMaxWidth().padding(vertical = 24.dp))
                }
                loadError -> item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(CoreResourcesR.string.documents_error_title))
                        TextButton(onClick = onRetry) { Text(stringResource(CoreResourcesR.string.documents_retry)) }
                    }
                }
                visible.isEmpty() -> item {
                    Text(stringResource(if (documents.isEmpty()) R.string.quick_sign_empty else R.string.quick_sign_no_results))
                }
                else -> items(visible, key = { it.id }) { document ->
                    PdfSelectionCard(
                        name = document.name,
                        metadata = pdfSelectionMetadata(document.pageCount, document.sizeBytes),
                        thumbnail = { DocumentThumbnail(document = document, modifier = it) },
                        onClick = { onSelect(document.id) },
                        trailing = if (document.isProtected) {
                            {
                                Icon(
                                    painter = painterResource(CoreResourcesR.drawable.ic_document_lock),
                                    contentDescription = stringResource(CoreResourcesR.string.merge_app_protected),
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                        } else null,
                    )
                }
            }
        }
    }
}

@Preview(name = "Escolher documento para assinar", widthDp = 390, heightDp = 780, locale = "pt-rBR")
@Composable
private fun DocumentSignaturePickerPreview() {
    ScaniqoTheme {
        DocumentSignaturePicker(
            documents = listOf(
                DocumentModel(
                    id = 1,
                    name = "Contrato de prestação de serviços",
                    pdfUri = "preview.pdf",
                    pageCount = 3,
                    createdAt = 1_790_000_000_000L,
                    sizeBytes = 482_000L,
                ),
                DocumentModel(
                    id = 2,
                    name = "Comprovante de pagamento",
                    pdfUri = "preview.pdf",
                    pageCount = 1,
                    createdAt = 1_790_000_000_000L,
                    sizeBytes = 124_000L,
                    protectionKeyId = "preview",
                ),
            ),
            isLoading = false,
            loadError = false,
            onRetry = {},
            onImport = {},
            onDismiss = {},
            onSelect = {},
        )
    }
}
