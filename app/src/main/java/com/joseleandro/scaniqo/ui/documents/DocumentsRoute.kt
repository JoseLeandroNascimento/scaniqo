package com.joseleandro.scaniqo.ui.documents

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle

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
        onDocumentAction = viewModel::onDocumentAction,
        onOpenMenu = onOpenMenu,
        onImportPdf = onImportPdf,
        onOpenDocument = onOpenDocument,
        modifier = modifier,
    )
}
