package com.joseleandro.scaniqo.ui.documents

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.joseleandro.scaniqo.R
import com.joseleandro.scaniqo.ui.components.toast.AppToastHost
import com.joseleandro.scaniqo.ui.documentCommon.components.DocumentActionOverlays
import com.joseleandro.scaniqo.ui.documentCommon.components.DocumentStatus
import com.joseleandro.scaniqo.ui.documentCommon.event.DocumentActionEvent
import com.joseleandro.scaniqo.ui.documentCommon.feedback.rememberDocumentToastState
import com.joseleandro.scaniqo.ui.documentCommon.state.DocumentActionsUiState
import com.joseleandro.scaniqo.ui.documents.components.DocumentCard
import com.joseleandro.scaniqo.ui.documents.components.DocumentQuickTools
import com.joseleandro.scaniqo.ui.documents.components.DocumentTagFilters
import com.joseleandro.scaniqo.ui.documents.components.DocumentsLoading
import com.joseleandro.scaniqo.ui.documents.event.DocumentsEvent
import com.joseleandro.scaniqo.ui.documents.state.DocumentsUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentsScreen(
    uiState: DocumentsUiState,
    actionsState: DocumentActionsUiState,
    onEvent: (DocumentsEvent) -> Unit,
    onDocumentAction: (DocumentActionEvent) -> Unit,
    onOpenMenu: () -> Unit,
    onImportPdf: () -> Unit,
    onSearch: () -> Unit,
    onOpenDocument: (Long) -> Unit,
    onMergePdfs: () -> Unit,
    onSignDocument: () -> Unit,
    onAllTools: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val toastState = rememberDocumentToastState(actionsState.message) {
        onDocumentAction(DocumentActionEvent.DismissMessage)
    }
    val scanBusy = actionsState.isScanning || actionsState.isPreparingScan
    val actionsBusy = actionsState.isBusy
    val visibleDocuments = uiState.visibleDocuments
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.documents_title)) },
                navigationIcon = {
                    IconButton(onClick = onOpenMenu, enabled = !actionsBusy) {
                        Icon(painterResource(R.drawable.ic_app_menu), "Abrir menu")
                    }
                },
                actions = {
                    IconButton(onClick = onSearch, enabled = !actionsBusy) {
                        Icon(
                            painterResource(R.drawable.ic_document_search),
                            contentDescription = stringResource(R.string.document_search_title),
                        )
                    }
                },
            )
        },
        snackbarHost = { AppToastHost(toastState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    if (!actionsBusy) onDocumentAction(
                        DocumentActionEvent.StartScan(
                            defaultTagId = uiState.selectedTagId
                        )
                    )
                },
                modifier = Modifier.semantics { if (actionsBusy) disabled() },
                icon = {
                    if (scanBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            painterResource(R.drawable.ic_document_scan),
                            contentDescription = null
                        )
                    }
                },
                text = {
                    Text(
                        stringResource(
                            when {
                                actionsState.isPreparingScan -> R.string.documents_preparing
                                actionsState.isScanning -> R.string.documents_scanning
                                else -> R.string.documents_scan
                            },
                        ),
                    )
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
            contentPadding = PaddingValues(top = 8.dp, bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "quick_tools") {
                DocumentQuickTools(
                    enabled = !actionsBusy && actionsState.editor == null && actionsState.pendingDeletion == null,
                    onImport = onImportPdf,
                    onMerge = onMergePdfs,
                    onSign = onSignDocument,
                    onAllTools = onAllTools,
                )
            }
            item(key = "filters") {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        text = stringResource(R.string.documents_subtitle),
                        modifier = Modifier.padding(horizontal = 16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    DocumentTagFilters(
                        tags = uiState.tags,
                        selectedTagId = uiState.selectedTagId,
                        onSelect = { onEvent(DocumentsEvent.SelectTag(it)) },
                    )
                    if (!uiState.isLoading && !uiState.loadError) {
                        Text(
                            text = pluralStringResource(
                                R.plurals.documents_count,
                                visibleDocuments.size,
                                visibleDocuments.size,
                            ),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            when {
                uiState.isLoading -> item(key = "loading") {
                    DocumentsLoading()
                }

                uiState.loadError -> item(key = "error") {
                    DocumentStatus(
                        title = stringResource(R.string.documents_error_title),
                        description = stringResource(R.string.documents_error_body),
                        actionLabel = stringResource(R.string.documents_retry),
                        onAction = { onEvent(DocumentsEvent.RetryLoad) },
                    )
                }

                visibleDocuments.isEmpty() -> item(key = "empty") {
                    val filtered = uiState.selectedTagId != null
                    DocumentStatus(
                        title = stringResource(if (filtered) R.string.documents_filter_empty_title else R.string.documents_empty_title),
                        description = stringResource(if (filtered) R.string.documents_filter_empty_body else R.string.documents_empty_body),
                        actionLabel = if (filtered) stringResource(R.string.documents_clear_filter) else null,
                        onAction = { onEvent(DocumentsEvent.SelectTag(null)) },
                    )
                }

                else -> items(visibleDocuments, key = { it.id }) { document ->
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        DocumentCard(
                            document = document,
                            tag = uiState.tagsById[document.tagId],
                            isBusy = actionsState.busyDocumentId == document.id,
                            actionsEnabled = !actionsBusy,
                            onOpen = { if (!actionsBusy) onOpenDocument(document.id) },
                            onEvent = onDocumentAction,
                        )
                    }
                }
            }
        }
    }
    DocumentActionOverlays(actionsState, onDocumentAction)
}
