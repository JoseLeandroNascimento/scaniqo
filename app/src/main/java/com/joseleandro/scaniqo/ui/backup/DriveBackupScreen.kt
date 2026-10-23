package com.joseleandro.scaniqo.ui.backup

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joseleandro.scaniqo.R
import com.joseleandro.scaniqo.core.localization.bidiArgument
import com.joseleandro.scaniqo.core.localization.localizedDateTime
import com.joseleandro.scaniqo.domain.model.BackupAutomaticProblem
import com.joseleandro.scaniqo.domain.model.BackupFrequency
import com.joseleandro.scaniqo.domain.model.BackupSettings
import com.joseleandro.scaniqo.domain.model.DriveBackupEntry
import com.joseleandro.scaniqo.ui.backup.state.DriveBackupPhase
import com.joseleandro.scaniqo.ui.backup.state.DriveBackupProblem
import com.joseleandro.scaniqo.ui.backup.state.DriveBackupUiState
import com.joseleandro.scaniqo.ui.components.DialogActions
import com.joseleandro.scaniqo.ui.theme.ScaniqoTheme

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
    modifier: Modifier = Modifier,
    onAutomaticEnabledChange: (Boolean) -> Unit = {},
    onFrequencyChange: (BackupFrequency) -> Unit = {},
    onWifiOnlyChange: (Boolean) -> Unit = {},
    onChargingOnlyChange: (Boolean) -> Unit = {},
    onRetry: () -> Unit = {},
) {
    var confirmBackup by rememberSaveable { mutableStateOf(false) }
    var confirmDisconnect by rememberSaveable { mutableStateOf(false) }
    var restoreId by rememberSaveable { mutableStateOf<String?>(null) }
    var chooseFrequency by rememberSaveable { mutableStateOf(false) }
    val busy = state.isBusy || state.automaticRunning
    val accountEmail = state.accountEmail ?: state.settings.accountEmail
    val authorized = state.accountEmail != null && !state.settings.needsAuthorization
    val settingsEnabled = !busy && !state.isSavingSettings
    val restore = state.backups.firstOrNull { it.id == restoreId }
    Scaffold(
        modifier = modifier,
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
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "account") {
                BackupAccountCard(
                    email = accountEmail,
                    needsAuthorization = accountEmail != null && !authorized,
                    enabled = !busy,
                    onConnect = onConnect,
                    onDisconnect = { confirmDisconnect = true },
                )
            }
            if (accountEmail != null) item(key = "summary") {
                BackupSummaryCard(state, enabled = !busy && authorized, onBackup = { confirmBackup = true })
            }
            if (state.isBusy) item(key = "progress") {
                BackupProgressCard(state, onCancel)
            }
            if (state.automaticRunning && !state.isBusy) item(key = "automatic_progress") {
                Card {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.backup_automatic_running), style = MaterialTheme.typography.titleSmall)
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text(stringResource(R.string.backup_automatic_background), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            state.problem?.let { problem ->
                item(key = "problem") {
                    val reconnect = problem == DriveBackupProblem.AUTHORIZATION ||
                        problem == DriveBackupProblem.PERMISSION || problem == DriveBackupProblem.ACCOUNT_CHANGED
                    BackupProblemCard(
                        message = stringResource(problem.label()),
                        action = stringResource(if (reconnect) R.string.backup_reconnect else R.string.backup_retry),
                        enabled = !busy,
                        onAction = if (reconnect) onConnect else onRetry,
                    )
                }
            }
            state.backedUpCount?.let { count ->
                item(key = "backup_success") { BackupSuccess(stringResource(R.string.backup_saved, count)) }
            }
            state.restoredCount?.let { count ->
                item(key = "restore_success") { BackupSuccess(stringResource(R.string.backup_restored, count)) }
            }
            item(key = "scope") {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(painterResource(R.drawable.ic_tag_document), null, tint = MaterialTheme.colorScheme.primary)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.backup_scope_title), style = MaterialTheme.typography.titleSmall)
                        Text(
                            stringResource(R.string.backup_scope_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (accountEmail != null) {
                item(key = "automatic") {
                    BackupAutomaticCard(
                        settings = state.settings,
                        enabled = settingsEnabled,
                        canToggle = !state.isBusy && !state.isSavingSettings &&
                            (state.settings.automaticEnabled || (authorized && !state.automaticRunning)),
                        onAutomaticEnabledChange = onAutomaticEnabledChange,
                        onFrequency = { chooseFrequency = true },
                        onWifiOnlyChange = onWifiOnlyChange,
                        onChargingOnlyChange = onChargingOnlyChange,
                    )
                }
                if (state.settings.needsAuthorization && state.problem == null) item(key = "automatic_authorization") {
                    BackupProblemCard(
                        message = stringResource(R.string.backup_automatic_authorize),
                        action = stringResource(R.string.backup_reconnect),
                        enabled = !busy,
                        onAction = onConnect,
                    )
                } else if (state.settings.lastAutomaticProblem != null && state.problem == null) {
                    item(key = "automatic_problem") {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(stringResource(R.string.backup_automatic_failed), style = MaterialTheme.typography.titleSmall)
                            Text(
                                stringResource(state.settings.lastAutomaticProblem.label()),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                item(key = "history_heading") {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.backup_list_title),
                            Modifier.weight(1f).semantics { heading() },
                            style = MaterialTheme.typography.titleMedium,
                        )
                        TextButton(onClick = onConnect, enabled = !busy) { Text(stringResource(R.string.backup_refresh)) }
                    }
                }
                if (state.backups.isEmpty() && !busy && authorized && state.problem == null) {
                    item(key = "empty") {
                        Card {
                            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    stringResource(if (state.hasLoadedBackups) R.string.backup_empty_title else R.string.backup_history_unloaded),
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Text(
                                    stringResource(if (state.hasLoadedBackups) R.string.backup_empty else R.string.backup_history_unloaded_body),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                items(state.backups, key = { it.id }) { entry ->
                    BackupEntryCard(entry, enabled = !busy && authorized, onRestore = { restoreId = entry.id })
                }
                item(key = "visibility") {
                    Text(
                        stringResource(R.string.backup_visibility),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
    if (confirmBackup && !busy && authorized) AlertDialog(
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
    if (restore != null && !busy && authorized) AlertDialog(
        onDismissRequest = { restoreId = null },
        title = { Text(stringResource(R.string.backup_restore_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(restore.name, style = MaterialTheme.typography.titleSmall)
                Text(backupDate(restore.modifiedTimeMillis), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.backup_restore_body))
            }
        },
        confirmButton = {
            DialogActions(
                confirmText = stringResource(R.string.backup_restore),
                onConfirm = { restoreId = null; onRestore(restore) },
                dismissText = stringResource(R.string.backup_cancel),
                onDismiss = { restoreId = null },
            )
        },
    )
    if (confirmDisconnect && !busy) AlertDialog(
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
    if (chooseFrequency && settingsEnabled) AlertDialog(
        onDismissRequest = { chooseFrequency = false },
        title = { Text(stringResource(R.string.backup_frequency)) },
        text = {
            Column(Modifier.selectableGroup()) {
                BackupFrequency.entries.forEach { frequency ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 56.dp)
                            .selectable(
                                selected = state.settings.frequency == frequency,
                                role = Role.RadioButton,
                                onClick = { chooseFrequency = false; onFrequencyChange(frequency) },
                            ).padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        RadioButton(selected = state.settings.frequency == frequency, onClick = null)
                        Text(stringResource(frequency.label()), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { chooseFrequency = false }) { Text(stringResource(R.string.backup_cancel)) }
        },
    )
}

@Composable
private fun BackupAccountCard(
    email: String?,
    needsAuthorization: Boolean,
    enabled: Boolean,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showMenu by remember { mutableStateOf(false) }
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.secondaryContainer) {
                    Icon(
                        painterResource(R.drawable.ic_cloud_backup), null,
                        Modifier.padding(10.dp).size(24.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.backup_provider), style = MaterialTheme.typography.titleMedium)
                    Text(
                        email ?: stringResource(R.string.backup_not_connected),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (needsAuthorization) Text(
                        stringResource(R.string.backup_authorization_required),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (email != null) Box {
                    IconButton(onClick = { showMenu = true }, enabled = enabled) {
                        Icon(painterResource(R.drawable.more_vert_24px), stringResource(R.string.backup_account_options))
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.backup_disconnect)) },
                            enabled = enabled,
                            onClick = { showMenu = false; onDisconnect() },
                        )
                    }
                }
            }
            if (email == null) {
                Text(stringResource(R.string.backup_intro), style = MaterialTheme.typography.bodyMedium)
                Button(onClick = onConnect, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.backup_connect))
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(painterResource(R.drawable.ic_tag_folder), null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.backup_folder), style = MaterialTheme.typography.bodySmall)
                }
                if (needsAuthorization) Button(
                    onClick = onConnect,
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.backup_reconnect)) }
            }
        }
    }
}

@Composable
private fun BackupSummaryCard(
    state: DriveBackupUiState,
    enabled: Boolean,
    onBackup: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lastBackup = state.settings.lastBackupAt ?: state.backups.firstOrNull()?.modifiedTimeMillis
    val lastBytes = if (state.settings.lastBackupAt != null) state.settings.lastBackupBytes
        else state.backups.firstOrNull()?.sizeBytes ?: 0L
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.backup_last), style = MaterialTheme.typography.labelLarge)
            Text(
                lastBackup?.let { backupDate(it) } ?: stringResource(
                    if (state.hasLoadedBackups) R.string.backup_never else R.string.backup_history_unloaded,
                ),
                style = MaterialTheme.typography.titleLarge,
            )
            if (lastBackup != null) {
                val size = Formatter.formatFileSize(context, lastBytes)
                Text(
                    if (state.settings.lastBackupAt != null) stringResource(
                        R.string.backup_summary, state.settings.lastBackupDocumentCount, bidiArgument(size),
                    ) else size,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .16f))
            Text(
                state.localDocumentCount?.let {
                    stringResource(R.string.backup_local_summary, it, bidiArgument(Formatter.formatFileSize(context, state.localSizeBytes)))
                } ?: stringResource(R.string.backup_local_loading),
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(
                onClick = onBackup,
                enabled = enabled && (state.localDocumentCount ?: 0) > 0,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.backup_now)) }
            if (state.localDocumentCount == 0) {
                Text(stringResource(R.string.backup_no_documents), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun BackupProgressCard(state: DriveBackupUiState, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(state.phase.label()),
                Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.titleSmall,
            )
            state.progress?.let { progress ->
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            } ?: LinearProgressIndicator(Modifier.fillMaxWidth())
            if (state.phase != DriveBackupPhase.DISCONNECTING) {
                Text(stringResource(R.string.backup_keep_open), style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = onCancel, enabled = state.phase != DriveBackupPhase.CANCELLING) {
                    Text(stringResource(R.string.backup_cancel))
                }
            }
        }
    }
}

@Composable
private fun BackupAutomaticCard(
    settings: BackupSettings,
    enabled: Boolean,
    canToggle: Boolean,
    onAutomaticEnabledChange: (Boolean) -> Unit,
    onFrequency: () -> Unit,
    onWifiOnlyChange: (Boolean) -> Unit,
    onChargingOnlyChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.backup_automatic_title),
            Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleMedium,
        )
        Card {
            Column {
                BackupSwitchRow(
                    title = stringResource(R.string.backup_automatic_enable),
                    summary = stringResource(R.string.backup_automatic_body),
                    checked = settings.automaticEnabled,
                    enabled = canToggle,
                    onChange = onAutomaticEnabledChange,
                )
                if (settings.automaticEnabled) {
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    Surface(
                        onClick = onFrequency,
                        enabled = enabled,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ) {
                        Column(
                            Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(stringResource(R.string.backup_frequency), style = MaterialTheme.typography.titleSmall)
                            Text(
                                stringResource(settings.frequency.label()),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    BackupSwitchRow(
                        title = stringResource(R.string.backup_wifi_only),
                        summary = stringResource(R.string.backup_wifi_body),
                        checked = settings.wifiOnly,
                        enabled = enabled,
                        onChange = onWifiOnlyChange,
                    )
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    BackupSwitchRow(
                        title = stringResource(R.string.backup_charging_only),
                        summary = stringResource(R.string.backup_charging_body),
                        checked = settings.chargingOnly,
                        enabled = enabled,
                        onChange = onChargingOnlyChange,
                    )
                }
            }
        }
        if (settings.automaticEnabled) Text(
            stringResource(R.string.backup_schedule_note),
            Modifier.padding(horizontal = 4.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BackupSwitchRow(
    title: String,
    summary: String,
    checked: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
private fun BackupProblemCard(
    message: String,
    action: String,
    enabled: Boolean,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.backup_problem_title), style = MaterialTheme.typography.titleSmall)
            Text(message, style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onAction, enabled = enabled) { Text(action) }
        }
    }
}

@Composable
private fun BackupSuccess(message: String, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ic_check_24), null, tint = MaterialTheme.colorScheme.primary)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun BackupEntryCard(
    entry: DriveBackupEntry,
    enabled: Boolean,
    onRestore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Card(modifier = modifier) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(backupDate(entry.modifiedTimeMillis), style = MaterialTheme.typography.titleSmall)
            Text(entry.name, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(Formatter.formatFileSize(context, entry.sizeBytes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = onRestore, enabled = enabled) { Text(stringResource(R.string.backup_restore)) }
        }
    }
}

@Composable
private fun backupDate(timestamp: Long): String {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    return remember(timestamp, configuration) { context.localizedDateTime(timestamp) }
}

private fun BackupFrequency.label(): Int = when (this) {
    BackupFrequency.DAILY -> R.string.backup_frequency_daily
    BackupFrequency.WEEKLY -> R.string.backup_frequency_weekly
}

private fun BackupAutomaticProblem.label(): Int = when (this) {
    BackupAutomaticProblem.NETWORK -> R.string.backup_error_network
    BackupAutomaticProblem.QUOTA -> R.string.backup_error_quota
    BackupAutomaticProblem.LOCAL_STORAGE -> R.string.backup_error_local
    BackupAutomaticProblem.CONFIGURATION -> R.string.backup_error_configuration
    BackupAutomaticProblem.REMOTE -> R.string.backup_error_remote
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
    DriveBackupProblem.API_DISABLED -> R.string.backup_error_api_disabled
    DriveBackupProblem.ACCESS_BLOCKED -> R.string.backup_error_access_blocked
    DriveBackupProblem.PERMISSION -> R.string.backup_error_permission
    DriveBackupProblem.NETWORK -> R.string.backup_error_network
    DriveBackupProblem.QUOTA -> R.string.backup_error_quota
    DriveBackupProblem.LOCAL_STORAGE -> R.string.backup_error_local
    DriveBackupProblem.REMOTE -> R.string.backup_error_remote
    DriveBackupProblem.ACCOUNT_CHANGED -> R.string.backup_error_account
}

@Preview(name = "Backup · conectado", widthDp = 390, heightDp = 900, locale = "pt-rBR")
@Preview(name = "Backup · compacto", widthDp = 320, heightDp = 640, fontScale = 1.3f, locale = "pt-rBR")
@Composable
private fun DriveBackupConnectedPreview() {
    ScaniqoTheme {
        DriveBackupScreen(
            DriveBackupUiState(
                accountEmail = "leandro@gmail.com",
                localDocumentCount = 12,
                localSizeBytes = 12_340_000,
                hasLoadedBackups = true,
                settings = BackupSettings(
                    automaticEnabled = true,
                    lastBackupAt = 1792230000000L,
                    lastBackupBytes = 12_340_000,
                    lastBackupDocumentCount = 12,
                ),
                backups = listOf(DriveBackupEntry("preview", "Scaniqo-2026-10-17", 1792230000000L, 12_340_000)),
            ), {}, {}, {}, {}, {}, {},
        )
    }
}

@Preview(name = "Backup · conectar", widthDp = 390, heightDp = 700, locale = "pt-rBR")
@Composable
private fun DriveBackupDisconnectedPreview() {
    ScaniqoTheme { DriveBackupScreen(DriveBackupUiState(localDocumentCount = 12), {}, {}, {}, {}, {}, {}) }
}

@Preview(name = "Backup · erro", widthDp = 390, heightDp = 850, locale = "pt-rBR")
@Composable
private fun DriveBackupErrorPreview() {
    ScaniqoTheme {
        DriveBackupScreen(
            DriveBackupUiState(localDocumentCount = 12, problem = DriveBackupProblem.API_DISABLED),
            {}, {}, {}, {}, {}, {},
        )
    }
}
