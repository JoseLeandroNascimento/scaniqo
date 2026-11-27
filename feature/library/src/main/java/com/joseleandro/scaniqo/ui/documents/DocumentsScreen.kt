package com.joseleandro.scaniqo.ui.documents

import com.joseleandro.scaniqo.core.resources.R as CoreResourcesR
import com.joseleandro.scaniqo.feature.library.R

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.joseleandro.scaniqo.ui.ads.DocumentAdPlacementPolicy
import com.joseleandro.scaniqo.ui.ads.DocumentListRow
import com.joseleandro.scaniqo.ui.ads.DocumentsNativeAd
import com.joseleandro.scaniqo.ui.ads.rememberDocumentsNativeAds
import com.joseleandro.scaniqo.ui.components.toast.AppToastHost
import com.joseleandro.scaniqo.ui.documentCommon.components.DocumentStatus
import com.joseleandro.scaniqo.ui.components.EmptyStateIllustration
import com.joseleandro.scaniqo.ui.components.ScaniqoEmptyState
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
    adsEnabled: Boolean = false,
    showNavigationMenu: Boolean = true,
) {
    val toastState = rememberDocumentToastState(actionsState.message) {
        onDocumentAction(DocumentActionEvent.DismissMessage)
    }
    val scanBusy = actionsState.isScanning || actionsState.isPreparingScan
    val scanLabel = stringResource(
        when {
            actionsState.isPreparingScan -> CoreResourcesR.string.documents_preparing
            actionsState.isScanning -> CoreResourcesR.string.documents_scanning
            else -> R.string.documents_scan
        },
    )
    val actionsBusy = actionsState.isBusy
    val visibleDocuments = uiState.visibleDocuments
    val adPositions = DocumentAdPlacementPolicy.afterDocumentCounts(
        documentCount = visibleDocuments.size,
        enabled = adsEnabled,
        isLoading = uiState.isLoading,
        hasError = uiState.loadError,
        hasBlockingAction = actionsBusy || actionsState.editor != null ||
            actionsState.pendingDeletion != null || actionsState.pendingCreation != null ||
            actionsState.generatedResult != null,
    )
    val nativeAds = rememberDocumentsNativeAds(adPositions.size)
    val loadedSlots = nativeAds.keys.toSet()
    val documentRows = remember(visibleDocuments, adPositions, loadedSlots) {
        DocumentAdPlacementPolicy.rows(
            documentIds = visibleDocuments.map { it.id },
            afterDocumentCounts = adPositions,
            loadedSlots = loadedSlots,
        )
    }
    val density = LocalDensity.current
    var scanButtonHeight by remember { mutableStateOf(56.dp) }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(CoreResourcesR.string.documents_title)) },
                navigationIcon = {
                    if (showNavigationMenu) {
                        IconButton(onClick = onOpenMenu, enabled = !actionsBusy) {
                            Icon(painterResource(R.drawable.ic_app_menu), "Abrir menu")
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onSearch, enabled = !actionsBusy) {
                        Icon(
                            painterResource(CoreResourcesR.drawable.ic_document_search),
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
                modifier = Modifier
                    .onSizeChanged { scanButtonHeight = with(density) { it.height.toDp() } }
                    .semantics {
                        contentDescription = scanLabel
                        if (actionsBusy) disabled()
                    },
                icon = {
                    if (scanBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            painterResource(CoreResourcesR.drawable.ic_document_scan),
                            contentDescription = null
                        )
                    }
                },
                text = { Text(scanLabel) },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(innerPadding).consumeWindowInsets(innerPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            BoxWithConstraints(Modifier.widthIn(max = 1200.dp).fillMaxSize()) {
                val usePreviewCards = maxWidth >= 600.dp && density.fontScale <= 1.5f
                val horizontalPadding = if (maxWidth >= 600.dp) 24.dp else 16.dp
                LazyVerticalGrid(
                    columns = if (usePreviewCards) {
                        GridCells.Adaptive(240.dp * density.fontScale.coerceAtLeast(1f))
                    } else {
                        GridCells.Adaptive(336.dp * density.fontScale.coerceAtLeast(1f))
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = if (nativeAds.isNotEmpty()) scanButtonHeight + 32.dp else 0.dp),
                    contentPadding = PaddingValues(
                        start = horizontalPadding,
                        end = horizontalPadding,
                        top = 16.dp,
                        bottom = if (nativeAds.isNotEmpty()) 16.dp else scanButtonHeight + 48.dp,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    item(key = "quick_tools", span = { GridItemSpan(maxLineSpan) }) {
                        DocumentQuickTools(
                            enabled = !actionsBusy && actionsState.editor == null && actionsState.pendingDeletion == null,
                            onImport = onImportPdf,
                            onMerge = onMergePdfs,
                            onSign = onSignDocument,
                            onAllTools = onAllTools,
                            contentPadding = PaddingValues(0.dp),
                        )
                    }
                    item(key = "filters", span = { GridItemSpan(maxLineSpan) }) {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text(
                                text = stringResource(R.string.documents_subtitle),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            DocumentTagFilters(
                                tags = uiState.tags,
                                selectedTagId = uiState.selectedTagId,
                                onSelect = { onEvent(DocumentsEvent.SelectTag(it)) },
                                contentPadding = PaddingValues(0.dp),
                            )
                            if (!uiState.isLoading && !uiState.loadError) {
                                Text(
                                    text = pluralStringResource(
                                        R.plurals.documents_count,
                                        visibleDocuments.size,
                                        visibleDocuments.size,
                                    ),
                                    modifier = Modifier.padding(vertical = 4.dp),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    when {
                        uiState.isLoading -> item(key = "loading", span = { GridItemSpan(maxLineSpan) }) {
                            DocumentsLoading()
                        }

                        uiState.loadError -> item(key = "error", span = { GridItemSpan(maxLineSpan) }) {
                            DocumentStatus(
                                title = stringResource(CoreResourcesR.string.documents_error_title),
                                description = stringResource(CoreResourcesR.string.documents_error_body),
                                actionLabel = stringResource(CoreResourcesR.string.documents_retry),
                                onAction = { onEvent(DocumentsEvent.RetryLoad) },
                            )
                        }

                        visibleDocuments.isEmpty() -> item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                            val filtered = uiState.selectedTagId != null
                            ScaniqoEmptyState(
                                title = stringResource(if (filtered) R.string.documents_filter_empty_title else R.string.documents_empty_title),
                                description = stringResource(if (filtered) R.string.documents_filter_empty_body else R.string.documents_empty_body),
                                illustration = if (filtered) EmptyStateIllustration.FILTERED_DOCUMENTS else EmptyStateIllustration.DOCUMENTS,
                                actionLabel = if (filtered) stringResource(R.string.documents_clear_filter) else null,
                                onAction = if (filtered) {
                                    { onEvent(DocumentsEvent.SelectTag(null)) }
                                } else null,
                            )
                        }

                        else -> items(
                            items = documentRows,
                            key = DocumentListRow::key,
                            span = { row ->
                                GridItemSpan(if (row is DocumentListRow.NativeAdSlot) maxLineSpan else 1)
                            },
                        ) { row ->
                            when (row) {
                                is DocumentListRow.Document -> {
                                    val document = visibleDocuments[row.documentIndex]
                                    DocumentCard(
                                        document = document,
                                        tag = uiState.tagsById[document.tagId],
                                        isBusy = actionsState.busyDocumentId == document.id,
                                        actionsEnabled = !actionsBusy,
                                        onOpen = { if (!actionsBusy) onOpenDocument(document.id) },
                                        onEvent = onDocumentAction,
                                        showLargePreview = usePreviewCards,
                                    )
                                }

                                is DocumentListRow.NativeAdSlot -> nativeAds[row.slot]?.let { ad ->
                                    DocumentsNativeAd(ad = ad)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
