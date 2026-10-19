package com.joseleandro.scaniqo.ui.documentSearch

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.joseleandro.scaniqo.ui.documentSearch.state.DocumentSearchUiState
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun DocumentSearchRoute(
    onBack: () -> Unit,
    onOpenDocument: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DocumentSearchViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    // A digitação não espera o fechamento de um PDF da consulta anterior.
    val visibleState = if (query == uiState.query) uiState else {
        DocumentSearchUiState(query = query, isSearching = query.isNotBlank())
    }
    DocumentSearchScreen(
        uiState = visibleState,
        onQueryChanged = viewModel::onQueryChanged,
        onRetry = viewModel::retry,
        onBack = onBack,
        onOpenDocument = onOpenDocument,
        modifier = modifier,
    )
}
