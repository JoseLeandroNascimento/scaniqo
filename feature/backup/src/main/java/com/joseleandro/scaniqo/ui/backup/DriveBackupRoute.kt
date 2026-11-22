package com.joseleandro.scaniqo.ui.backup

import com.joseleandro.scaniqo.feature.backup.R

import androidx.activity.compose.BackHandler
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.joseleandro.scaniqo.ui.backup.platform.DriveAuthorizationHandler
import com.joseleandro.scaniqo.ui.backup.state.DriveBackupPhase
import com.joseleandro.scaniqo.ui.components.DialogActions
import com.joseleandro.scaniqo.ui.components.toast.AppToastHost
import com.joseleandro.scaniqo.ui.components.toast.AppToastType
import com.joseleandro.scaniqo.ui.components.toast.rememberAppToastState
import kotlinx.coroutines.flow.collectLatest
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun DriveBackupRoute(
    onBack: () -> Unit,
    viewModel: DriveBackupViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val toast = rememberAppToastState()
    val resources = LocalResources.current
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(viewModel, toast, resources, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.connectionErrors.collectLatest { problem ->
                toast.show(resources.getString(problem.messageResource()), AppToastType.ERROR)
            }
        }
    }
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
        onConnect = {
            toast.dismiss()
            viewModel.connect()
        },
        onBackup = viewModel::backup,
        onRestore = viewModel::restore,
        onDisconnect = viewModel::disconnect,
        onCancel = viewModel::cancel,
        onAutomaticEnabledChange = viewModel::setAutomaticEnabled,
        onFrequencyChange = viewModel::setFrequency,
        onWifiOnlyChange = viewModel::setWifiOnly,
        onChargingOnlyChange = viewModel::setChargingOnly,
        onRetry = {
            toast.dismiss()
            viewModel.retry()
        },
        onDelete = viewModel::deleteHistory,
        snackbarHost = { AppToastHost(toast) },
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
