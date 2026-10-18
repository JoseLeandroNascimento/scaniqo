package com.joseleandro.scaniqo.ui.backup

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.joseleandro.scaniqo.ui.backup.platform.DriveAuthorizationHandler
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun DriveBackupRoute(
    onBack: () -> Unit,
    viewModel: DriveBackupViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DriveAuthorizationHandler(viewModel)
    val leave = { viewModel.cancel(); onBack() }
    BackHandler(onBack = leave)
    DriveBackupScreen(
        state = state,
        onBack = leave,
        onConnect = viewModel::connect,
        onBackup = viewModel::backup,
        onRestore = viewModel::restore,
        onDisconnect = viewModel::disconnect,
        onCancel = viewModel::cancel,
    )
}
