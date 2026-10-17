package com.joseleandro.scaniqo.ui.documentEditor

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joseleandro.scaniqo.R
import com.joseleandro.scaniqo.core.security.SecureWindow
import com.joseleandro.scaniqo.domain.model.DocumentModel
import com.joseleandro.scaniqo.domain.model.PdfImageFilter
import com.joseleandro.scaniqo.domain.model.PdfPageLayout
import com.joseleandro.scaniqo.domain.model.PdfPagePreview
import com.joseleandro.scaniqo.domain.model.PdfPageSpec
import com.joseleandro.scaniqo.domain.model.PdfWorkspace
import com.joseleandro.scaniqo.domain.model.TagModel
import com.joseleandro.scaniqo.ui.components.DocumentStatus
import com.joseleandro.scaniqo.ui.components.toast.AppToastHost
import com.joseleandro.scaniqo.ui.documents.DocumentsUiState
import com.joseleandro.scaniqo.ui.documents.components.DocumentOverlays
import com.joseleandro.scaniqo.ui.components.documentToastState
import com.joseleandro.scaniqo.ui.theme.ScaniqoTheme
import kotlinx.coroutines.flow.first

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentViewerScreen(
    documentId: Long,
    uiState: DocumentsUiState,
    onEvent: (DocumentsEvent) -> Unit,
    onNavigationBack: () -> Unit,
    toolsState: DocumentToolsUiState,
    onToolsEvent: (DocumentToolsEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val document = uiState.documents.firstOrNull { it.id == documentId }
    val sameSession = toolsState.document?.id == documentId
    val workspace = toolsState.workspace?.takeIf { sameSession }
    val locked = toolsState.isLocked || (document?.protectionKeyId != null && workspace == null)
    SecureWindow(enabled = document?.protectionKeyId != null || workspace?.isProtected == true)
    val toastState = documentToastState(uiState.message, onEvent)
    val documentBusy = uiState.busyDocumentId != null || uiState.isDeleting ||
            uiState.isScanning || uiState.isPreparingScan || uiState.editor?.isSaving == true
    val isBusy = documentBusy || toolsState.isBusy
    val showScanner =
        document != null && workspace != null && !locked && toolsState.signatureDraft == null
    val canScan = toolsState.canEdit && !documentBusy
    val scanning = toolsState.isScanning || toolsState.isAppending
    var showActions by remember(documentId, isBusy, locked) { mutableStateOf(false) }
    val processingDescription = stringResource(R.string.document_processing)
    val filtersOpen = toolsState.tool == DocumentTool.FILTERS
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = if (filtersOpen && workspace != null && !locked) toolsState.selectedPageIndex + 1 else 0,
    )
    DocumentToolsMessages(toolsState, onToolsEvent, toastState)

    var previousSelection by remember(documentId) { mutableStateOf<String?>(null) }
    LaunchedEffect(
        toolsState.selectedPageId,
        toolsState.selectedPageIndex,
        toolsState.signatureDraft?.id
    ) {
        val previous = previousSelection
        previousSelection = toolsState.selectedPageId
        // Atualizar a imagem preserva a posição de leitura. Só uma nova seleção move a lista.
        if (workspace != null && !locked && (previous != null || toolsState.signatureDraft != null)) {
            snapshotFlow { listState.layoutInfo.totalItemsCount }.first { it >= workspace.pages.size + 1 }
            listState.animateScrollToItem(toolsState.selectedPageIndex + 1)
        }
    }

    LaunchedEffect(filtersOpen) {
        // Ao abrir a faixa, a página escolhida ocupa a área livre para conferir o efeito.
        if (filtersOpen && workspace != null && !locked) {
            snapshotFlow { listState.layoutInfo.totalItemsCount }.first { it >= workspace.pages.size + 1 }
            listState.animateScrollToItem(toolsState.selectedPageIndex + 1)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surfaceDim,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.viewer_title),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                navigationIcon = {
                    IconButton(
                        onClick = { onToolsEvent(DocumentToolsEvent.RequestBack) },
                        enabled = !isBusy,
                    ) {
                        Icon(
                            painterResource(R.drawable.arrow_back_24px),
                            stringResource(R.string.document_back)
                        )
                    }
                },
                actions = {
                    if (document != null && sameSession && !locked && workspace != null) {
                        if (toolsState.canUndo && toolsState.signatureDraft == null) {
                            IconButton(
                                onClick = { onToolsEvent(DocumentToolsEvent.Undo) },
                                enabled = toolsState.canEdit && !documentBusy,
                            ) {
                                Icon(
                                    painterResource(R.drawable.ic_document_undo),
                                    contentDescription = stringResource(R.string.viewer_undo_description),
                                )
                            }
                        }
                        Box {
                            FilledTonalIconButton(
                                onClick = { showActions = true },
                                enabled = !isBusy,
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    contentColor = MaterialTheme.colorScheme.onSurface,
                                ),
                            ) {
                                if (isBusy) {
                                    CircularProgressIndicator(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .semantics {
                                                contentDescription = processingDescription
                                            },
                                        strokeWidth = 2.dp,
                                    )
                                } else {
                                    Icon(
                                        painterResource(R.drawable.more_vert_24px),
                                        contentDescription = stringResource(
                                            R.string.document_actions,
                                            document.name
                                        ),
                                    )
                                }
                            }
                            DropdownMenu(
                                expanded = showActions && !isBusy,
                                onDismissRequest = { showActions = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.document_metadata_edit)) },
                                    leadingIcon = {
                                        Icon(
                                            painterResource(R.drawable.edit_24px),
                                            null
                                        )
                                    },
                                    enabled = !isBusy && toolsState.signatureDraft == null,
                                    onClick = {
                                        showActions = false
                                        onEvent(DocumentsEvent.EditMetadata(document))
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.document_download)) },
                                    leadingIcon = {
                                        Icon(
                                            painterResource(R.drawable.ic_document_download),
                                            null
                                        )
                                    },
                                    enabled = !isBusy && toolsState.canExport,
                                    onClick = {
                                        showActions = false
                                        onEvent(DocumentsEvent.Download(document))
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.document_share)) },
                                    leadingIcon = {
                                        Icon(
                                            painterResource(R.drawable.ic_document_share),
                                            null
                                        )
                                    },
                                    enabled = !isBusy && toolsState.canExport,
                                    onClick = {
                                        showActions = false
                                        onEvent(DocumentsEvent.Share(document))
                                    },
                                )
                                if (toolsState.hasChanges || toolsState.signatureDraft != null) {
                                    Text(
                                        stringResource(R.string.tools_export_save_first),
                                        modifier = Modifier
                                            .widthIn(max = 260.dp)
                                            .padding(16.dp),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                },
            )
        },
        snackbarHost = { AppToastHost(toastState) },
        floatingActionButton = {
            if (showScanner) {
                ExtendedFloatingActionButton(
                    onClick = { if (canScan) onToolsEvent(DocumentToolsEvent.AddPages) },
                    modifier = Modifier.semantics { if (!canScan) disabled() },
                    containerColor = if (canScan || scanning) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = if (canScan || scanning) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else MaterialTheme.colorScheme.onSurface.copy(alpha = .38f),
                    icon = {
                        if (scanning) {
                            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
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
                                    toolsState.isScanning -> R.string.documents_scanning
                                    toolsState.isAppending -> R.string.documents_preparing
                                    else -> R.string.pages_scan
                                },
                            )
                        )
                    },
                )
            }
        },
        bottomBar = {
            if (workspace != null && !locked) {
                DocumentToolsBar(toolsState, onToolsEvent, enabled = !documentBusy)
            }
        },
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
            // Reserva espaço para rolar a última folha acima do botão de captura.
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp, top = 16.dp,
                bottom = if (showScanner) 104.dp else 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            when {
                toolsState.isLoading && sameSession && document != null && !document.isProtected &&
                        workspace == null && !toolsState.loadError && !locked -> {
                    item(key = "details") {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Column(
                                modifier = Modifier
                                    .widthIn(max = 840.dp)
                                    .fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                DocumentViewerHeader(
                                    document = document,
                                    tag = uiState.tags.firstOrNull { it.id == document.tagId },
                                    pageCount = document.pageCount,
                                    hasChanges = false,
                                )
                                Text(
                                    stringResource(R.string.viewer_preparing_tools),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                LinearProgressIndicator(Modifier.fillMaxWidth())
                            }
                        }
                    }
                    items(document.pageCount, key = { "saved_page_$it" }) { pageIndex ->
                        DocumentSavedPage(document, pageIndex)
                    }
                }

                toolsState.isLoading && sameSession -> item(key = "loading") {
                    ToolProgress(stringResource(R.string.tools_loading))
                }

                locked -> item(key = "locked") {
                    DocumentStatus(
                        title = stringResource(R.string.tools_locked_title),
                        description = stringResource(if (toolsState.recoveryRequired) R.string.tools_recovery_description else R.string.tools_locked_description),
                        actionLabel = stringResource(if (toolsState.recoveryRequired) R.string.tools_recovery_action else R.string.tools_unlock),
                        onAction = {
                            onToolsEvent(if (toolsState.recoveryRequired) DocumentToolsEvent.RequestPasswordRecovery else DocumentToolsEvent.Unlock)
                        },
                    )
                }

                toolsState.loadError && sameSession -> item(key = "load_error") {
                    DocumentStatus(
                        title = stringResource(R.string.tools_load_error),
                        description = stringResource(R.string.documents_error_body),
                        actionLabel = stringResource(R.string.tools_retry),
                        onAction = { onToolsEvent(DocumentToolsEvent.Retry) },
                    )
                }

                document != null && workspace != null -> {
                    item(key = "details") {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            DocumentViewerHeader(
                                document = document,
                                tag = uiState.tags.firstOrNull { it.id == document.tagId },
                                pageCount = workspace.pages.size,
                                hasChanges = toolsState.hasChanges,
                                modifier = Modifier.widthIn(max = 840.dp),
                            )
                        }
                    }
                    itemsIndexed(workspace.pages, key = { _, page -> page.id }) { pageIndex, page ->
                        DisposableEffect(workspace.workspaceId, page.id) {
                            onToolsEvent(DocumentToolsEvent.PageVisibility(page.id, true))
                            onDispose {
                                onToolsEvent(
                                    DocumentToolsEvent.PageVisibility(
                                        page.id,
                                        false
                                    )
                                )
                            }
                        }
                        val pagePreview = toolsState.pagePreviews[page.id]
                        val selected = page.id == toolsState.selectedPageId
                        val pageDescription =
                            stringResource(R.string.tools_select_page, pageIndex + 1)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(
                                modifier = Modifier
                                    .widthIn(max = 840.dp)
                                    .fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                DocumentPagePreviewHeader(
                                    pageId = page.id,
                                    pageNumber = pageIndex + 1,
                                    pageCount = workspace.pages.size,
                                    selected = selected,
                                    state = toolsState.copy(
                                        isRendering = pagePreview?.isRendering == true,
                                        displayedComparisonPageId = page.id.takeIf { pagePreview?.showingOriginal == true },
                                    ),
                                    onEvent = onToolsEvent,
                                    enabled = !documentBusy,
                                )
                                if (pagePreview?.error == true) {
                                    DocumentPreviewError(
                                        enabled = !isBusy && !pagePreview.isRendering,
                                        onRetry = {
                                            onToolsEvent(
                                                DocumentToolsEvent.RetryPagePreview(
                                                    page.id
                                                )
                                            )
                                        },
                                    )
                                }
                                // A folha mantém a proporção A4, sem cabeçalho ou margens artificiais no PDF.
                                Surface(
                                    onClick = { onToolsEvent(DocumentToolsEvent.SelectPage(page.id)) },
                                    enabled = toolsState.canEdit && !documentBusy && toolsState.signatureDraft == null,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(595f / 842f)
                                        .semantics {
                                            contentDescription = pageDescription
                                            this.selected = selected
                                        },
                                    shape = RectangleShape,
                                    color = Color.White,
                                    contentColor = Color.Black,
                                    shadowElevation = 6.dp,
                                    border = BorderStroke(0.5.dp, Color.Black.copy(alpha = .12f)),
                                ) {
                                    Box {
                                        val preview = pagePreview?.preview
                                        if (preview != null) {
                                            DocumentPdfPage(
                                                pdfUri = preview.uri,
                                                pageIndex = preview.pageIndex,
                                                contentKey = page.id,
                                                modifier = Modifier.fillMaxSize(),
                                            )
                                        } else if (pagePreview?.error != true) {
                                            Box(
                                                Modifier.fillMaxSize(),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                CircularProgressIndicator(
                                                    Modifier.size(28.dp),
                                                    strokeWidth = 2.dp
                                                )
                                            }
                                        }
                                        if (toolsState.signaturePageId == page.id && toolsState.signatureDraft != null &&
                                            preview != null && !pagePreview.isRendering && !pagePreview.error
                                        ) {
                                            DocumentSignatureOverlay(
                                                toolsState.signatureDraft,
                                                onToolsEvent,
                                                Modifier.fillMaxSize()
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                uiState.isLoading || document != null -> item(key = "loading") {
                    ToolProgress(stringResource(R.string.tools_loading))
                }

                uiState.loadError -> item(key = "error") {
                    DocumentStatus(
                        title = stringResource(R.string.documents_error_title),
                        description = stringResource(R.string.documents_error_body),
                        actionLabel = stringResource(R.string.documents_retry),
                        onAction = { onEvent(DocumentsEvent.RetryLoad) },
                    )
                }

                else -> item(key = "not_found") {
                    DocumentStatus(
                        title = stringResource(R.string.document_not_found_title),
                        description = stringResource(R.string.document_not_found_body),
                        actionLabel = stringResource(R.string.document_return),
                        onAction = onNavigationBack,
                    )
                }
            }
        }
    }
    if (sameSession && !locked) {
        DocumentOverlays(uiState, onEvent)
        DocumentToolsSheets(toolsState, onToolsEvent, documents = uiState.documents)
    } else if (toolsState.confirmDiscard) {
        DocumentToolsSheets(toolsState.copy(tool = null), onToolsEvent)
    }
}

/** Compara a folha selecionada no mesmo local onde o usuário confere o resultado. */
@Composable
private fun DocumentPagePreviewHeader(
    pageId: String,
    pageNumber: Int,
    pageCount: Int,
    selected: Boolean,
    state: DocumentToolsUiState,
    onEvent: (DocumentToolsEvent) -> Unit,
    enabled: Boolean,
) {
    val comparisonAvailable = selected && state.canCompare && state.signatureDraft == null
    val showingOriginal = state.displayedComparisonPageId == pageId
    val updating = selected && state.isRendering
    if (!comparisonAvailable && !showingOriginal && !updating) {
        Box(Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp), contentAlignment = Alignment.Center) {
            DocumentViewerPageHeader(pageNumber, pageCount, selected)
        }
        return
    }
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.CenterVertically)
                .padding(end = 8.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                stringResource(R.string.document_page_position, pageNumber, pageCount),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (updating) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 1.5.dp)
                    Text(
                        stringResource(R.string.viewer_preview_updating),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else if (showingOriginal && !comparisonAvailable) {
                Text(
                    stringResource(R.string.viewer_compare_before),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        if (comparisonAvailable) {
            DocumentQualityPreviewControls(
                state = state,
                onEvent = onEvent,
                enabled = enabled,
                showingOriginal = showingOriginal,
                modifier = Modifier.align(Alignment.CenterVertically),
            )
        } else {
            Text(
                stringResource(if (selected) R.string.viewer_page_selected else R.string.viewer_pdf_format),
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterVertically),
            )
        }
    }
}

@Composable
private fun DocumentPreviewError(enabled: Boolean, onRetry: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(R.string.viewer_preview_stale),
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
            )
            TextButton(onClick = onRetry, enabled = enabled) {
                Text(stringResource(R.string.tools_retry))
            }
        }
    }
}

/** O arquivo salvo já pode ser lido enquanto as ferramentas preparam uma cópia de trabalho. */
@Composable
private fun DocumentSavedPage(document: DocumentModel, pageIndex: Int) {
    Box(Modifier
        .fillMaxWidth()
        .padding(horizontal = 4.dp), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .widthIn(max = 840.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.heightIn(min = 48.dp), contentAlignment = Alignment.Center) {
                DocumentViewerPageHeader(pageIndex + 1, document.pageCount, selected = false)
            }
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(595f / 842f),
                shape = RectangleShape,
                color = Color.White,
                shadowElevation = 6.dp,
                border = BorderStroke(.5.dp, Color.Black.copy(alpha = .12f)),
            ) {
                DocumentPdfPage(
                    pdfUri = document.pdfUri,
                    pageIndex = pageIndex,
                    contentKey = "saved-${document.id}-$pageIndex",
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun ToolProgress(text: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CircularProgressIndicator(Modifier.size(32.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Preview(name = "Visualizador claro", showBackground = true)
@Preview(
    name = "Visualizador escuro",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
private fun DocumentViewerPreview() {
    DocumentViewerPreviewContent()
}

@Preview(
    name = "Visualizador com fonte ampliada",
    widthDp = 360,
    heightDp = 800,
    fontScale = 1.3f,
    locale = "pt-rBR"
)
@Composable
private fun DocumentViewerLargeTextPreview() {
    DocumentViewerPreviewContent(
        name = "Contrato de aluguel do apartamento — versão assinada",
        hasChanges = true,
    )
}

@Preview(name = "Comparação junto da página", widthDp = 390, heightDp = 850, locale = "pt-rBR")
@Preview(
    name = "Comparação escuro",
    widthDp = 390,
    heightDp = 850,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    locale = "pt-rBR"
)
@Preview(
    name = "Comparação fonte ampliada",
    widthDp = 360,
    heightDp = 850,
    fontScale = 1.3f,
    locale = "pt-rBR"
)
@Composable
private fun DocumentViewerComparisonPreview() {
    DocumentViewerPreviewContent(hasChanges = true, improved = true)
}

@Preview(name = "Filtros na barra", widthDp = 390, heightDp = 850, locale = "pt-rBR")
@Preview(
    name = "Filtros na barra escuro",
    widthDp = 390,
    heightDp = 850,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    locale = "pt-rBR"
)
@Preview(
    name = "Filtros na barra fonte ampliada",
    widthDp = 360,
    heightDp = 800,
    fontScale = 1.3f,
    locale = "pt-rBR"
)
@Composable
private fun DocumentViewerFiltersPreview() {
    DocumentViewerPreviewContent(hasChanges = true, improved = true, filtersOpen = true)
}

@Composable
private fun DocumentViewerPreviewContent(
    name: String = "Contrato de aluguel",
    hasChanges: Boolean = false,
    improved: Boolean = false,
    filtersOpen: Boolean = false,
) {
    val document = DocumentModel(
        id = 1, name = name, pdfUri = "preview.pdf", pageCount = 2,
        createdAt = 1781431200000L, sizeBytes = 248_000L, thumbnailUri = null, tagId = 1,
    )
    ScaniqoTheme {
        DocumentViewerScreen(
            documentId = 1,
            uiState = DocumentsUiState(
                isLoading = false,
                documents = listOf(document),
                tags = listOf(TagModel(id = 1, name = "Contratos", color = Color(0xFFCCDFEF))),
            ),
            onEvent = {},
            onNavigationBack = {},
            toolsState = DocumentToolsUiState(
                document = document,
                workspace = PdfWorkspace(
                    documentId = 1, workspaceId = "preview", directoryUri = "preview",
                    pages = listOf(
                        PdfPageSpec(
                            id = "one", sourceUri = "preview.pdf", sourcePageIndex = 0,
                            layout = PdfPageLayout(imageFilter = if (improved) PdfImageFilter.ENHANCE else PdfImageFilter.ORIGINAL),
                        ),
                        PdfPageSpec(
                            id = "two", sourceUri = "preview.pdf", sourcePageIndex = 1,
                            layout = PdfPageLayout(
                                imageFilter = if (filtersOpen && improved) PdfImageFilter.ENHANCE else PdfImageFilter.ORIGINAL,
                            ),
                        ),
                    ),
                ),
                selectedPageId = "one",
                previewUri = "preview.pdf",
                pagePreviews = mapOf(
                    "one" to DocumentPagePreviewState(PdfPagePreview("preview.pdf", 0)),
                    "two" to DocumentPagePreviewState(PdfPagePreview("preview.pdf", 1)),
                ),
                hasChanges = hasChanges,
                canUndo = hasChanges,
                tool = DocumentTool.FILTERS.takeIf { filtersOpen },
                filterThumbnailsPageId = "one",
                filterThumbnails = documentFilterChoices.associateWith { "preview-$it" },
            ),
            onToolsEvent = {},
        )
    }
}
