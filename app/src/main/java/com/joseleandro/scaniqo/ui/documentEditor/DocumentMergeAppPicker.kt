package com.joseleandro.scaniqo.ui.documentEditor

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joseleandro.scaniqo.R
import com.joseleandro.scaniqo.domain.model.DocumentModel
import com.joseleandro.scaniqo.ui.theme.ScaniqoTheme

/** Guarda apenas IDs; a confirmação sempre usa os documentos mais recentes da biblioteca. */
@Composable
internal fun DocumentMergeAppPicker(
    documents: List<DocumentModel>,
    currentDocumentId: Long?,
    resultProtected: Boolean,
    enabled: Boolean,
    onCancel: () -> Unit,
    onConfirm: (List<DocumentModel>) -> Unit,
) {
    var query by rememberSaveable(currentDocumentId) { mutableStateOf("") }
    var selectedIds by rememberSaveable(currentDocumentId) { mutableStateOf(emptyList<Long>()) }
    val availableDocuments = documents.filter { it.id != currentDocumentId }.distinctBy { it.id }
    val documentsById = availableDocuments.associateBy { it.id }
    val selectedDocuments = selectedIds.mapNotNull(documentsById::get)
    val selectionOrder = selectedDocuments.mapIndexed { index, document -> document.id to index + 1 }.toMap()
    val visibleDocuments = availableDocuments.filter { it.name.contains(query.trim(), ignoreCase = true) }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    Column(
        modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.merge_app_title), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.merge_app_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text(stringResource(R.string.merge_app_search)) },
            singleLine = true,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                focus.clearFocus()
                keyboard?.hide()
            }),
        )
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (visibleDocuments.isEmpty()) {
                item {
                    Text(
                        text = stringResource(
                            if (availableDocuments.isEmpty()) R.string.merge_app_empty
                            else R.string.merge_app_no_results,
                        ),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(visibleDocuments, key = { it.id }) { document ->
                DocumentMergeAppRow(
                    document = document,
                    order = selectionOrder[document.id],
                    enabled = enabled,
                    onToggle = {
                        selectedIds = if (document.id in selectedIds) {
                            selectedIds - document.id
                        } else {
                            selectedIds + document.id
                        }
                    },
                )
            }
        }
        Text(
            pluralStringResource(
                R.plurals.merge_app_selected_count,
                selectedDocuments.size,
                selectedDocuments.size,
            ),
            style = MaterialTheme.typography.labelLarge,
        )
        if (selectedDocuments.any { it.isProtected }) {
            Text(
                stringResource(
                    if (resultProtected) R.string.merge_app_result_protected
                    else R.string.merge_app_result_unprotected,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = {
                    focus.clearFocus()
                    keyboard?.hide()
                    onCancel()
                },
                modifier = Modifier.weight(1f).heightIn(min = 52.dp),
            ) {
                Text(stringResource(R.string.cancel))
            }
            Button(
                onClick = {
                    // Não conserva cópias dos modelos entre seleção e confirmação.
                    val latestSelection = selectedIds.mapNotNull(documentsById::get)
                    if (latestSelection.isNotEmpty()) {
                        focus.clearFocus()
                        keyboard?.hide()
                        onConfirm(latestSelection)
                    }
                },
                enabled = enabled && selectedDocuments.isNotEmpty(),
                modifier = Modifier.weight(1f).heightIn(min = 52.dp),
            ) {
                Text(
                    pluralStringResource(
                        R.plurals.merge_app_confirm_count,
                        selectedDocuments.size,
                        selectedDocuments.size,
                    ),
                )
            }
        }
    }
}

@Composable
private fun DocumentMergeAppRow(
    document: DocumentModel,
    order: Int?,
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    val selected = order != null
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().toggleable(
            value = selected,
            enabled = enabled,
            role = Role.Checkbox,
            onValueChange = { onToggle() },
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = selected, onCheckedChange = null, enabled = enabled)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    document.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    pluralStringResource(R.plurals.document_page_count, document.pageCount, document.pageCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (order != null) {
                    Text(
                        stringResource(R.string.merge_app_selection_order, order),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            if (document.isProtected) {
                Icon(
                    painter = painterResource(R.drawable.ic_document_lock),
                    contentDescription = stringResource(R.string.merge_app_protected),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

@Preview(name = "Mesclar do app", widthDp = 390, heightDp = 700, showBackground = true)
@Preview(
    name = "Mesclar do app escuro",
    widthDp = 390,
    heightDp = 700,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun DocumentMergeAppPickerPreview() {
    ScaniqoTheme {
        Surface {
            Column(Modifier.padding(20.dp)) {
                DocumentMergeAppPicker(
                    documents = listOf(
                        DocumentModel(1, "Contrato", "", 3, 0, 0),
                        DocumentModel(2, "Comprovantes", "", 5, 0, 0),
                        DocumentModel(3, "Documento protegido", "", 2, 0, 0, protectionKeyId = "key"),
                    ),
                    currentDocumentId = 1,
                    resultProtected = false,
                    enabled = true,
                    onCancel = {},
                    onConfirm = {},
                )
            }
        }
    }
}
