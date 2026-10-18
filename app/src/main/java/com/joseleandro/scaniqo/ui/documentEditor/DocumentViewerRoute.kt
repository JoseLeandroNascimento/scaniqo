package com.joseleandro.scaniqo.ui.documentEditor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.joseleandro.scaniqo.ui.documentEditor.state.DocumentViewerUiState
import com.joseleandro.scaniqo.ui.documents.DocumentsViewModel
import com.joseleandro.scaniqo.ui.documents.event.DocumentsEvent

/** Adapta os dados compartilhados para a tela sem acoplar seus componentes à lista. */
@Composable
fun DocumentViewerRoute(
    documentId: Long,
    viewModel: DocumentsViewModel,
    toolsViewModel: DocumentToolsViewModel,
    onNavigationBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val documentsState by viewModel.uiState.collectAsStateWithLifecycle()
    val toolsState by toolsViewModel.uiState.collectAsStateWithLifecycle()
    val document = documentsState.documents.firstOrNull { it.id == documentId }
    LaunchedEffect(document?.id, document?.pdfUri) {
        document?.let(toolsViewModel::load)
    }
    DocumentViewerScreen(
        documentId = documentId,
        uiState = DocumentViewerUiState(
            document = document,
            availableDocuments = documentsState.documents,
            actions = documentsState.actions,
            isLoading = documentsState.isLoading,
            loadError = documentsState.loadError,
        ),
        onDocumentAction = viewModel::onDocumentAction,
        onRetryLoad = { viewModel.onEvent(DocumentsEvent.RetryLoad) },
        toolsState = toolsState,
        onToolsEvent = toolsViewModel::onEvent,
        onNavigationBack = onNavigationBack,
        modifier = modifier,
    )
}
