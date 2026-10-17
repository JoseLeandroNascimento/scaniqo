package com.joseleandro.scaniqo.ui.tags

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel

/** Conecta a tela ao ViewModel sem expor injeção de dependências aos componentes visuais. */
@Composable
fun TagsRoute(
    onNavigationBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TagsViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    TagsScreen(
        onNavigationBack = onNavigationBack,
        modifier = modifier,
        uiState = uiState,
        onEvent = viewModel::onEvent,
    )
}
