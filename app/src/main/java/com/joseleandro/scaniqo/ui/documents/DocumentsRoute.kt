package com.joseleandro.scaniqo.ui.documents

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.joseleandro.scaniqo.ui.documentCommon.DocumentActionsViewModel
import com.joseleandro.scaniqo.ui.documents.event.DocumentsEvent

/** A lista controla o filtro e compartilha apenas o responsável pelas ações de documento. */
@Composable
fun DocumentsRoute(
    viewModel: DocumentsViewModel,
    actionsViewModel: DocumentActionsViewModel,
    onOpenMenu: () -> Unit,
    onImportPdf: () -> Unit,
    onOpenDocument: (Long) -> Unit,
    onMergePdfs: () -> Unit,
    onSignDocument: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val actionsState by actionsViewModel.uiState.collectAsStateWithLifecycle()
    DocumentsScreen(
        uiState = uiState,
        actionsState = actionsState,
        onEvent = { event ->
            viewModel.onEvent(event)
            if (event == DocumentsEvent.RetryLoad) actionsViewModel.retryLoadingTags()
        },
        onDocumentAction = actionsViewModel::onDocumentAction,
        onOpenMenu = onOpenMenu,
        onImportPdf = onImportPdf,
        onOpenDocument = onOpenDocument,
        onMergePdfs = onMergePdfs,
        onSignDocument = onSignDocument,
        modifier = modifier,
    )
}
