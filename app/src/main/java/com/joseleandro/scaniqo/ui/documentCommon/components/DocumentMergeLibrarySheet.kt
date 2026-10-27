package com.joseleandro.scaniqo.ui.documentCommon.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.joseleandro.scaniqo.R
import com.joseleandro.scaniqo.domain.model.DocumentModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DocumentMergeLibrarySheet(
    documents: List<DocumentModel>,
    selectedIds: Set<Long>,
    selectedCount: Int = selectedIds.size,
    isLoading: Boolean = false,
    loadError: Boolean = false,
    enabled: Boolean,
    onRetry: () -> Unit = {},
    onSelect: (DocumentModel) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val focus = LocalFocusManager.current
    val matches = documents.filter { it.name.contains(query.trim(), ignoreCase = true) }
    val searchLabel = stringResource(R.string.merge_motion_search)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = modifier,
    ) {
        Column(
            Modifier.fillMaxWidth().imePadding().padding(horizontal = 20.dp).padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.merge_selection_library_title),
                    Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                AnimatedContent(
                    targetState = selectedCount,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    label = "Selected documents count",
                ) { count ->
                    Text(stringResource(R.string.merge_motion_selected, count),
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
            if (!isLoading && !loadError && documents.isNotEmpty()) {
                OutlinedTextField(
                    value = query, onValueChange = { query = it },
                    placeholder = { Text(searchLabel) },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_document_search), null, Modifier.size(20.dp)) },
                    singleLine = true, enabled = enabled,
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = searchLabel },
                    shape = MaterialTheme.shapes.large,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                )
            }
            when {
                isLoading -> Box(Modifier.fillMaxWidth().heightIn(min = 112.dp),
                    contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                loadError -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.documents_error_title),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    TextButton(onClick = onRetry, enabled = enabled) {
                        Text(stringResource(R.string.documents_retry))
                    }
                }
                else -> LazyColumn(
                    Modifier.fillMaxWidth().heightIn(min = 96.dp, max = 360.dp).weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (matches.isEmpty()) item {
                        Text(stringResource(if (documents.isEmpty()) R.string.merge_selection_library_empty
                            else R.string.merge_app_no_results), Modifier.padding(vertical = 20.dp),
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    items(matches, key = { it.id }) { document ->
                        DocumentMergeAvailableRow(
                            document = document, enabled = enabled,
                            onSelect = { onSelect(document) },
                            selected = document.id in selectedIds,
                        )
                    }
                }
            }
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Text(stringResource(R.string.merge_motion_done))
            }
        }
    }
}
