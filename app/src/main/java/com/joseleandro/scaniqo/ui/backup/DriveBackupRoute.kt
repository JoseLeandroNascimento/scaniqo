package com.joseleandro.scaniqo.ui.backup

import androidx.activity.compose.BackHandler
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.joseleandro.scaniqo.R
import com.joseleandro.scaniqo.ui.backup.platform.DriveAuthorizationHandler
import com.joseleandro.scaniqo.ui.backup.state.DriveBackupPhase
import com.joseleandro.scaniqo.ui.components.DialogActions
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun DriveBackupRoute(
    onBack: () -> Unit,
    viewModel: DriveBackupViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var confirmLeave by rememberSaveable { mutableStateOf(false) }
    var leaveWhenIdle by rememberSaveable { mutableStateOf(false) }
    DriveAuthorizationHandler(viewModel)
    LaunchedEffect(viewModel) { viewModel.refreshIfConnected() }
    LaunchedEffect(state.isBusy, leaveWhenIdle) {
        if (leaveWhenIdle && !state.isBusy) onBack()
    }
    val leave = {
        if (state.phase != DriveBackupPhase.DELETING) {
            if (state.isBusy) confirmLeave = true else onBack()
        }
    }
    BackHandler(onBack = leave)
    DriveBackupScreen(
        state = state,
        onBack = leave,
        onConnect = viewModel::connect,
        onBackup = viewModel::backup,
        onRestore = viewModel::restore,
        onDisconnect = viewModel::disconnect,
        onCancel = viewModel::cancel,
        onAutomaticEnabledChange = viewModel::setAutomaticEnabled,
        onFrequencyChange = viewModel::setFrequency,
        onWifiOnlyChange = viewModel::setWifiOnly,
        onChargingOnlyChange = viewModel::setChargingOnly,
        onRetry = viewModel::retry,
        onDelete = viewModel::deleteHistory,
    )
    if (confirmLeave && state.isBusy) AlertDialog(
        onDismissRequest = { confirmLeave = false },
        title = { Text(stringResource(R.string.backup_leave_title)) },
        text = { Text(stringResource(R.string.backup_leave_body)) },
        confirmButton = {
            DialogActions(
                confirmText = stringResource(R.string.backup_leave_confirm),
                onConfirm = {
                    confirmLeave = false
                    leaveWhenIdle = true
                    viewModel.cancel()
                },
                dismissText = stringResource(R.string.backup_leave_stay),
                onDismiss = { confirmLeave = false },
                confirmEnabled = state.phase != DriveBackupPhase.DISCONNECTING &&
                    state.phase != DriveBackupPhase.CANCELLING && state.phase != DriveBackupPhase.DELETING,
            )
        },
    )
}
