package com.joseleandro.scaniqo.ui.documentEditor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.unit.dp
import com.joseleandro.scaniqo.R
import com.joseleandro.scaniqo.core.security.SecureWindow
import com.joseleandro.scaniqo.ui.components.toast.AppToastHost
import com.joseleandro.scaniqo.ui.documentCommon.components.DocumentActionOverlays
import com.joseleandro.scaniqo.ui.documentCommon.components.DocumentPdfPage
import com.joseleandro.scaniqo.ui.documentCommon.components.DocumentPreviewZoom
import com.joseleandro.scaniqo.ui.documentCommon.components.DocumentStatus
import com.joseleandro.scaniqo.ui.documentCommon.event.DocumentActionEvent
import com.joseleandro.scaniqo.ui.documentCommon.feedback.rememberDocumentToastState
import com.joseleandro.scaniqo.ui.documentEditor.components.DocumentPagePreviewHeader
import com.joseleandro.scaniqo.ui.documentEditor.components.DocumentPreviewError
import com.joseleandro.scaniqo.ui.documentEditor.components.DocumentSavedPage
import com.joseleandro.scaniqo.ui.documentEditor.components.DocumentSignatureOverlay
import com.joseleandro.scaniqo.ui.documentEditor.components.DocumentToolsBar
import com.joseleandro.scaniqo.ui.documentEditor.components.DocumentToolsMessages
import com.joseleandro.scaniqo.ui.documentEditor.components.DocumentToolsSheets
import com.joseleandro.scaniqo.ui.documentEditor.components.DocumentViewerHeader
import com.joseleandro.scaniqo.ui.documentEditor.components.DocumentViewerTopBar
import com.joseleandro.scaniqo.ui.documentEditor.components.ToolProgress
import com.joseleandro.scaniqo.ui.documentEditor.event.DocumentToolsEvent
import com.joseleandro.scaniqo.ui.documentEditor.model.DocumentTool
import com.joseleandro.scaniqo.ui.documentEditor.state.DocumentToolsUiState
import com.joseleandro.scaniqo.ui.documentEditor.state.DocumentViewerUiState
import kotlinx.coroutines.flow.first

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentViewerScreen(
    documentId: Long,
    uiState: DocumentViewerUiState,
    onDocumentAction: (DocumentActionEvent) -> Unit,
    onRetryLoad: () -> Unit,
    onNavigationBack: () -> Unit,
    toolsState: DocumentToolsUiState,
    onToolsEvent: (DocumentToolsEvent) -> Unit,
    modifier: Modifier = Modifier,
    initialPageNumber: Int? = null,
    banner: @Composable () -> Unit = {},
    onSave: (() -> Unit)? = null,
    saveShowsVideo: Boolean = false,
    saveInProgress: Boolean = false,
) {
    val document = uiState.document
    val sameSession = toolsState.document?.id == documentId
    val workspace = toolsState.workspace?.takeIf { sameSession }
    val locked = toolsState.isLocked || (document?.protectionKeyId != null && workspace == null)
    SecureWindow(enabled = document?.protectionKeyId != null || workspace?.isProtected == true)
    val toastState = rememberDocumentToastState(uiState.actions.message) {
        onDocumentAction(DocumentActionEvent.DismissMessage)
    }
    val documentBusy = uiState.actions.isBusy
    val isBusy = documentBusy || toolsState.isBusy || saveInProgress
    val showScanner =
        document != null && workspace != null && !locked && toolsState.signatureDraft == null
    val canScan = toolsState.canEdit && !documentBusy && !saveInProgress
    val scanning = toolsState.isScanning || toolsState.isAppending
    val showBanner = document?.id == documentId && workspace?.pages?.isNotEmpty() == true &&
        !uiState.isLoading && !uiState.loadError && !locked && !isBusy &&
        !toolsState.isRendering && !toolsState.hasChanges && toolsState.tool == null &&
        toolsState.signatureDraft == null && !toolsState.signatureAwaitingPlacement &&
        !toolsState.isComparingOriginal && toolsState.pendingDeletePageId == null &&
        !toolsState.confirmDiscard && uiState.actions.editor == null &&
        uiState.actions.pendingDeletion == null && uiState.actions.pendingCreation == null &&
        uiState.actions.generatedResult == null

    val filtersOpen = toolsState.tool == DocumentTool.FILTERS
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = if (filtersOpen && workspace != null && !locked) toolsState.selectedPageIndex + 1 else 0,
    )
    DocumentToolsMessages(toolsState, onToolsEvent, toastState)

    var initialPageApplied by rememberSaveable(documentId, initialPageNumber) {
        mutableStateOf(initialPageNumber == null)
    }
    LaunchedEffect(
        document?.id, uiState.isLoading, initialPageNumber,
        workspace?.workspaceId, workspace?.pages?.size, toolsState.canEdit,
    ) {
        if (initialPageApplied || document == null || uiState.isLoading ||
            workspace == null || !toolsState.canEdit || locked) return@LaunchedEffect
        val pageIndex = initialViewerPageIndex(initialPageNumber, workspace.pages.size)
            ?: return@LaunchedEffect
        // Aguarda as páginas definitivas; a prévia temporária usa outras chaves na lista.
        snapshotFlow { listState.layoutInfo.totalItemsCount }.first { it >= workspace.pages.size + 1 }
        onToolsEvent(DocumentToolsEvent.SelectPage(workspace.pages[pageIndex].id))
        listState.scrollToItem(pageIndex + 1) // O primeiro item contém os dados do documento.
        initialPageApplied = true
    }

    var previousSelection by remember(documentId) { mutableStateOf<String?>(null) }
    LaunchedEffect(
        toolsState.selectedPageId,
        toolsState.selectedPageIndex,
        toolsState.signatureDraft?.id
    ) {
        val previous = previousSelection
        previousSelection = toolsState.selectedPageId
        // Atualizar a imagem preserva a posição de leitura. Só uma nova seleção move a lista.
        if (initialPageApplied && workspace != null && !locked &&
            (previous != null || toolsState.signatureDraft != null)) {
            snapshotFlow { listState.layoutInfo.totalItemsCount }.first { it >= workspace.pages.size + 1 }
            listState.animateScrollToItem(toolsState.selectedPageIndex + 1)
        }
    }

    LaunchedEffect(filtersOpen) {
        // Ao abrir a faixa, a página escolhida ocupa a área livre para conferir o efeito.
        if (initialPageApplied && filtersOpen && workspace != null && !locked) {
            snapshotFlow { listState.layoutInfo.totalItemsCount }.first { it >= workspace.pages.size + 1 }
            listState.animateScrollToItem(toolsState.selectedPageIndex + 1)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surfaceDim,
        topBar = {
            DocumentViewerTopBar(
                document = document,
                showDocumentActions = sameSession && !locked && workspace != null,
                toolsState = toolsState,
                onDocumentAction = onDocumentAction,
                onToolsEvent = onToolsEvent,
                documentBusy = documentBusy,
                isBusy = isBusy,
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
                Column {
                    // The banner is a separate measured row; it never covers the PDF or controls.
                    if (showBanner) banner()
                    DocumentToolsBar(
                        toolsState, onToolsEvent, enabled = !documentBusy && !saveInProgress,
                        onSave = onSave, saveShowsVideo = saveShowsVideo, saveInProgress = saveInProgress,
                    )
                }
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
                                    tag = uiState.actions.tags.firstOrNull { it.id == document.tagId },
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
                                tag = uiState.actions.tags.firstOrNull { it.id == document.tagId },
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
                        var signatureBeforeGesture by remember(page.id) { mutableStateOf(toolsState.signatureDraft) }
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
                                    DocumentPreviewZoom(
                                        modifier = Modifier.fillMaxSize(), contentKey = page.id,
                                        singleFingerPan = toolsState.signatureDraft == null,
                                        contentAspectRatio = 595f / 842f,
                                        onGestureStart = {
                                            signatureBeforeGesture = toolsState.signatureDraft
                                                ?.takeIf { toolsState.signaturePageId == page.id }
                                        },
                                        onTransformStart = {
                                            signatureBeforeGesture?.takeIf { it.id == toolsState.signatureDraft?.id }
                                                ?.let { original ->
                                                    onToolsEvent(DocumentToolsEvent.TransformSignature(
                                                        x = original.x, y = original.y, width = original.width,
                                                        height = original.height, rotationDegrees = original.rotationDegrees,
                                                        stretchToBounds = original.stretchToBounds,
                                                    ))
                                                }
                                        },
                                    ) {
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
                        onAction = onRetryLoad,
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
        DocumentToolsSheets(toolsState, onToolsEvent)
    } else if (toolsState.confirmDiscard) {
        DocumentToolsSheets(toolsState.copy(tool = null), onToolsEvent)
    }
}
