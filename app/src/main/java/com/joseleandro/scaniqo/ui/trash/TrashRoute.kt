package com.joseleandro.scaniqo.ui.trash

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.joseleandro.scaniqo.R
import com.joseleandro.scaniqo.ui.components.toast.AppToastHost
import com.joseleandro.scaniqo.ui.components.toast.AppToastType
import com.joseleandro.scaniqo.ui.components.toast.rememberAppToastState
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun TrashRoute(onBack: () -> Unit, viewModel: TrashViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val toast = rememberAppToastState()
    val message = state.message?.let {
        stringResource(when (it) {
            TrashMessage.RESTORED -> R.string.trash_restored
            TrashMessage.DELETED -> R.string.trash_deleted
            TrashMessage.RESTORE_ERROR -> R.string.trash_restore_error
            TrashMessage.CLEARED -> R.string.trash_cleared
        })
    }
    LaunchedEffect(state.message) {
        if (message != null) {
            toast.show(message, if (state.message == TrashMessage.RESTORE_ERROR) AppToastType.ERROR else AppToastType.SUCCESS)
            viewModel.dismissMessage()
        }
    }
    BackHandler(enabled = state.isBusy) {}
    TrashScreen(
        state = state,
        onBack = onBack,
        onRetry = viewModel::load,
        onRestore = viewModel::restore,
        onRequestDeletion = viewModel::requestDeletion,
        onConfirmDeletion = viewModel::confirmDeletion,
        onDismissDeletion = viewModel::dismissDeletion,
        onRequestClearAll = viewModel::requestClearAll,
        onConfirmClearAll = viewModel::confirmClearAll,
        onDismissClearAll = viewModel::dismissClearAll,
        snackbarHost = { AppToastHost(toast) },
    )
}
