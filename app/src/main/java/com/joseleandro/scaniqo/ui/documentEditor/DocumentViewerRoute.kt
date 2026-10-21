package com.joseleandro.scaniqo.ui.documentEditor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.joseleandro.scaniqo.ui.documentCommon.DocumentActionsViewModel

/** O visualizador carrega seus dados e compartilha as ações sem depender da lista. */
@Composable
fun DocumentViewerRoute(
    documentId: Long,
    viewModel: DocumentViewerViewModel,
    actionsViewModel: DocumentActionsViewModel,
    toolsViewModel: DocumentToolsViewModel,
    onNavigationBack: () -> Unit,
    modifier: Modifier = Modifier,
    initialPageNumber: Int? = null,
) {
    val viewerState by viewModel.uiState.collectAsStateWithLifecycle()
    val actionsState by actionsViewModel.uiState.collectAsStateWithLifecycle()
    val toolsState by toolsViewModel.uiState.collectAsStateWithLifecycle()
    // Ao trocar a rota, o frame anterior não pode apresentar outro documento.
    val document = viewerState.document?.takeIf { it.id == documentId }
    LaunchedEffect(documentId) {
        viewModel.load(documentId)
    }
    LaunchedEffect(document?.id, document?.pdfUri) {
        document?.let(toolsViewModel::load)
    }
    DocumentViewerScreen(
        documentId = documentId,
        initialPageNumber = initialPageNumber,
        uiState = viewerState.copy(
            document = document,
            actions = actionsState,
            isLoading = viewerState.isLoading || viewerState.requestedDocumentId != documentId,
            loadError = viewerState.loadError && viewerState.requestedDocumentId == documentId,
        ),
        onDocumentAction = actionsViewModel::onDocumentAction,
        onRetryLoad = {
            viewModel.retry()
            actionsViewModel.retryLoadingTags()
        },
        toolsState = toolsState,
        onToolsEvent = toolsViewModel::onEvent,
        onNavigationBack = onNavigationBack,
        modifier = modifier,
    )
}
