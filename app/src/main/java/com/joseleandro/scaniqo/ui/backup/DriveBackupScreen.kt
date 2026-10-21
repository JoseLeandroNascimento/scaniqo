package com.joseleandro.scaniqo.ui.backup

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import com.joseleandro.scaniqo.ui.components.DialogActions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joseleandro.scaniqo.R
import com.joseleandro.scaniqo.core.localization.bidiArgument
import com.joseleandro.scaniqo.domain.model.DriveBackupEntry
import com.joseleandro.scaniqo.ui.backup.state.DriveBackupPhase
import com.joseleandro.scaniqo.ui.backup.state.DriveBackupProblem
import com.joseleandro.scaniqo.ui.backup.state.DriveBackupUiState
import com.joseleandro.scaniqo.ui.theme.ScaniqoTheme
import com.joseleandro.scaniqo.core.localization.localizedDateTime
import androidx.compose.ui.platform.LocalConfiguration

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DriveBackupScreen(
    state: DriveBackupUiState,
    onBack: () -> Unit,
    onConnect: () -> Unit,
    onBackup: () -> Unit,
    onRestore: (DriveBackupEntry) -> Unit,
    onDisconnect: () -> Unit,
    onCancel: () -> Unit,
) {
    var confirmBackup by rememberSaveable { mutableStateOf(false) }
    var confirmDisconnect by rememberSaveable { mutableStateOf(false) }
    var restore by remember { mutableStateOf<DriveBackupEntry?>(null) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.backup_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.arrow_back_24px), stringResource(R.string.backup_back))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(painterResource(R.drawable.ic_cloud_backup), null, Modifier.size(40.dp))
                        Text(stringResource(R.string.backup_heading), style = MaterialTheme.typography.headlineSmall)
                        Text(stringResource(R.string.backup_intro), style = MaterialTheme.typography.bodyMedium)
                        state.accountEmail?.let { email ->
                            Text(email, fontWeight = FontWeight.SemiBold)
                            state.localDocumentCount?.let { Text(stringResource(R.string.backup_count, it), style = MaterialTheme.typography.bodyMedium) }
                            Button(
                                onClick = { confirmBackup = true },
                                enabled = !state.isBusy && (state.localDocumentCount ?: 0) > 0,
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(stringResource(R.string.backup_now)) }
                            if (state.localDocumentCount == 0) {
                                Text(stringResource(R.string.backup_no_documents), style = MaterialTheme.typography.bodySmall)
                            }
                        } ?: Button(onClick = onConnect, enabled = !state.isBusy, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.backup_connect))
                        }
                    }
                }
            }
            if (state.isBusy) item {
                Card {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(state.phase.label()), style = MaterialTheme.typography.titleMedium)
                        val progress = state.progress
                        if (progress == null) LinearProgressIndicator(Modifier.fillMaxWidth())
                        else LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                        Text(stringResource(R.string.backup_keep_open), style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = onCancel, enabled = state.phase != DriveBackupPhase.CANCELLING && state.phase != DriveBackupPhase.DISCONNECTING) {
                            Text(stringResource(R.string.backup_cancel))
                        }
                    }
                }
            }
            state.problem?.let { problem -> item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Text(stringResource(problem.label()), Modifier.fillMaxWidth().padding(16.dp))
                }
            } }
            state.backedUpCount?.let { count -> item {
                Text(stringResource(R.string.backup_saved, count), color = MaterialTheme.colorScheme.primary)
            } }
            state.restoredCount?.let { count -> item {
                Text(stringResource(R.string.backup_restored, count), color = MaterialTheme.colorScheme.primary)
            } }
            if (state.accountEmail != null) {
                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.backup_list_title), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        TextButton(onClick = onConnect, enabled = !state.isBusy) { Text(stringResource(R.string.backup_refresh)) }
                    }
                }
                if (state.backups.isEmpty() && !state.isBusy) item {
                    Text(stringResource(R.string.backup_empty), Modifier.padding(vertical = 20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                items(state.backups, key = { it.id }) { entry ->
                    BackupEntryCard(entry, enabled = !state.isBusy, onRestore = { restore = entry })
                }
                item {
                    Text(stringResource(R.string.backup_visibility), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { confirmDisconnect = true }, enabled = !state.isBusy) {
                        Text(stringResource(R.string.backup_disconnect))
                    }
                }
            }
        }
    }
    if (confirmBackup && !state.isBusy) AlertDialog(
        onDismissRequest = { confirmBackup = false },
        title = { Text(stringResource(R.string.backup_confirm_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.backup_confirm_body, bidiArgument(state.accountEmail.orEmpty())))
                Text(stringResource(R.string.backup_scope_note))
            }
        },
        confirmButton = {
            DialogActions(
                confirmText = stringResource(R.string.backup_confirm),
                onConfirm = { confirmBackup = false; onBackup() },
                dismissText = stringResource(R.string.backup_cancel),
                onDismiss = { confirmBackup = false },
            )
        },
    )
    restore?.let { entry ->
        if (!state.isBusy) AlertDialog(
            onDismissRequest = { restore = null },
            title = { Text(stringResource(R.string.backup_restore_title)) },
            text = { Text(stringResource(R.string.backup_restore_body)) },
            confirmButton = {
                DialogActions(
                    confirmText = stringResource(R.string.backup_restore),
                    onConfirm = { restore = null; onRestore(entry) },
                    dismissText = stringResource(R.string.backup_cancel),
                    onDismiss = { restore = null },
                )
            },
        )
    }
    if (confirmDisconnect && !state.isBusy) AlertDialog(
        onDismissRequest = { confirmDisconnect = false },
        title = { Text(stringResource(R.string.backup_disconnect_title)) },
        text = { Text(stringResource(R.string.backup_disconnect_body)) },
        confirmButton = {
            DialogActions(
                confirmText = stringResource(R.string.backup_disconnect),
                onConfirm = { confirmDisconnect = false; onDisconnect() },
                dismissText = stringResource(R.string.backup_cancel),
                onDismiss = { confirmDisconnect = false },
            )
        },
    )
}

@Composable
private fun BackupEntryCard(entry: DriveBackupEntry, enabled: Boolean, onRestore: () -> Unit) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val date = remember(entry.modifiedTimeMillis, configuration) { context.localizedDateTime(entry.modifiedTimeMillis) }
    Card {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(date, style = MaterialTheme.typography.titleSmall)
            Text(Formatter.formatFileSize(context, entry.sizeBytes), style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = onRestore, enabled = enabled) { Text(stringResource(R.string.backup_restore)) }
        }
    }
}

private fun DriveBackupPhase.label(): Int = when (this) {
    DriveBackupPhase.AUTHORIZING -> R.string.backup_authorizing
    DriveBackupPhase.CONNECTING -> R.string.backup_connecting
    DriveBackupPhase.PREPARING -> R.string.backup_preparing
    DriveBackupPhase.UPLOADING -> R.string.backup_uploading
    DriveBackupPhase.DOWNLOADING -> R.string.backup_downloading
    DriveBackupPhase.RESTORING -> R.string.backup_restoring
    DriveBackupPhase.DISCONNECTING -> R.string.backup_disconnecting
    DriveBackupPhase.CANCELLING -> R.string.backup_cancelling
    DriveBackupPhase.IDLE -> R.string.backup_title
}

private fun DriveBackupProblem.label(): Int = when (this) {
    DriveBackupProblem.AUTHORIZATION -> R.string.backup_error_auth
    DriveBackupProblem.CONFIGURATION -> R.string.backup_error_configuration
    DriveBackupProblem.PERMISSION -> R.string.backup_error_permission
    DriveBackupProblem.NETWORK -> R.string.backup_error_network
    DriveBackupProblem.QUOTA -> R.string.backup_error_quota
    DriveBackupProblem.LOCAL_STORAGE -> R.string.backup_error_local
    DriveBackupProblem.REMOTE -> R.string.backup_error_remote
    DriveBackupProblem.ACCOUNT_CHANGED -> R.string.backup_error_account
}

@Preview(name = "Backup · conectado", widthDp = 390, heightDp = 850, locale = "pt-rBR")
@Preview(name = "Backup · compacto", widthDp = 320, heightDp = 640, fontScale = 1.3f, locale = "pt-rBR")
@Composable
private fun DriveBackupConnectedPreview() {
    ScaniqoTheme {
        DriveBackupScreen(
            DriveBackupUiState(
                accountEmail = "leandro@gmail.com", localDocumentCount = 12,
                backups = listOf(DriveBackupEntry("preview", "Scaniqo.zip", 1792230000000L, 12_340_000)),
            ), {}, {}, {}, {}, {}, {},
        )
    }
}

@Preview(name = "Backup · conectar", widthDp = 390, heightDp = 700, locale = "pt-rBR")
@Composable
private fun DriveBackupDisconnectedPreview() {
    ScaniqoTheme { DriveBackupScreen(DriveBackupUiState(localDocumentCount = 12), {}, {}, {}, {}, {}, {}) }
}
