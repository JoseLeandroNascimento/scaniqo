package com.joseleandro.scaniqo.ui.documents.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.joseleandro.scaniqo.R
import com.joseleandro.scaniqo.domain.model.DocumentModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DocumentSignaturePicker(
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
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.quick_sign_choose), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.quick_sign_description), style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                label = { Text(stringResource(R.string.quick_sign_search)) },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            LazyColumn(
                Modifier.fillMaxWidth().heightIn(max = 480.dp),
                contentPadding = PaddingValues(bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when {
                    isLoading -> item { CircularProgressIndicator(Modifier.padding(20.dp)) }
                    loadError -> item {
                        Text(stringResource(R.string.documents_error_title))
                        TextButton(onClick = onRetry) { Text(stringResource(R.string.documents_retry)) }
                    }
                    visible.isEmpty() -> item {
                        Text(stringResource(if (documents.isEmpty()) R.string.quick_sign_empty else R.string.quick_sign_no_results))
                    }
                    else -> items(visible, key = { it.id }) { document ->
                        Surface(
                            onClick = { onSelect(document.id) },
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                        ) {
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Icon(painterResource(R.drawable.ic_tool_signature), null, Modifier.size(24.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(document.name, style = MaterialTheme.typography.titleSmall)
                                    Text(pluralStringResource(R.plurals.document_page_count, document.pageCount, document.pageCount),
                                        style = MaterialTheme.typography.bodySmall)
                                }
                                if (document.isProtected) Icon(painterResource(R.drawable.ic_document_lock),
                                    stringResource(R.string.merge_app_protected))
                            }
                        }
                    }
                }
            }
            TextButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.quick_tools_import))
            }
        }
    }
}
