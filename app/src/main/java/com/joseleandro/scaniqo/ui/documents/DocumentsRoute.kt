package com.joseleandro.scaniqo.ui.documents

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.joseleandro.scaniqo.ui.documentEditor.DocumentToolsViewModel
import com.joseleandro.scaniqo.ui.documentEditor.DocumentViewerScreen

/** A lista cuida dos metadados; o editor mantém sua própria sessão de trabalho. */
@Composable
fun DocumentsRoute(
    viewModel: DocumentsViewModel,
    onOpenMenu: () -> Unit,
    onImportPdf: () -> Unit,
    onOpenDocument: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    DocumentsScreen(
        uiState = uiState,
        onEvent = viewModel::onEvent,
        onOpenMenu = onOpenMenu,
        onImportPdf = onImportPdf,
        onOpenDocument = onOpenDocument,
        modifier = modifier,
    )
}

@Composable
fun DocumentViewerRoute(
    documentId: Long,
    viewModel: DocumentsViewModel,
    toolsViewModel: DocumentToolsViewModel,
    onNavigationBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val toolsState by toolsViewModel.uiState.collectAsStateWithLifecycle()
    val document = uiState.documents.firstOrNull { it.id == documentId }
    LaunchedEffect(document?.id, document?.pdfUri) {
        document?.let(toolsViewModel::load)
    }
    DocumentViewerScreen(
        documentId = documentId,
        uiState = uiState,
        onEvent = viewModel::onEvent,
        toolsState = toolsState,
        onToolsEvent = toolsViewModel::onEvent,
        onNavigationBack = onNavigationBack,
        modifier = modifier,
    )
}
