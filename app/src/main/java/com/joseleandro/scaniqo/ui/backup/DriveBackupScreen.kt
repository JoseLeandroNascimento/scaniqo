package com.joseleandro.scaniqo.ui.backup

import android.text.format.Formatter
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.joseleandro.scaniqo.R
import com.joseleandro.scaniqo.core.localization.bidiArgument
import com.joseleandro.scaniqo.core.localization.localizedDateTime
import com.joseleandro.scaniqo.domain.model.BackupAutomaticProblem
import com.joseleandro.scaniqo.domain.model.BackupFrequency
import com.joseleandro.scaniqo.domain.model.BackupHistoryEntry
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
    onDelete: (BackupHistoryEntry) -> Unit = {},
) {
    var confirmBackup by rememberSaveable { mutableStateOf(false) }
    var confirmDisconnect by rememberSaveable { mutableStateOf(false) }
    var restoreId by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteId by rememberSaveable { mutableStateOf<String?>(null) }
    var chooseFrequency by rememberSaveable { mutableStateOf(false) }
    var showAbout by rememberSaveable { mutableStateOf(false) }
    var showHistory by rememberSaveable(state.settings.accountEmail) { mutableStateOf(false) }
    val busy = state.isBusy || state.automaticRunning
    val accountEmail = state.accountEmail ?: state.settings.accountEmail
    val authorized = state.accountEmail != null && !state.settings.needsAuthorization
    val settingsEnabled = !busy && !state.isSavingSettings
    val restore = state.currentBackup?.takeIf { it.id == restoreId }
    val deleting = state.history.firstOrNull { it.id == deleteId }
    val recentHistory = state.history.take(5)
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.backup_screen_title)) },
                actions = {
                    IconButton(onClick = { showAbout = true }) {
                        Icon(painterResource(R.drawable.ic_toast_info), stringResource(R.string.backup_about))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = state.phase != DriveBackupPhase.DELETING) {
                        Icon(painterResource(R.drawable.arrow_back_24px), stringResource(R.string.backup_back))
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier.widthIn(max = 600.dp).fillMaxSize().testTag("backup_screen"),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item(key = "hero") {
                    BackupHeroCard(
                        state = state,
                        connected = accountEmail != null,
                        authorized = authorized,
                        enabled = !busy,
                        onConnect = onConnect,
                        onBackup = { confirmBackup = true },
                        onRestore = { restoreId = state.currentBackup?.id },
                    )
                }
                if (accountEmail != null) item(key = "account") {
                    BackupAccountCard(
                        email = accountEmail,
                        needsAuthorization = !authorized,
                        enabled = !busy,
                        onDisconnect = { confirmDisconnect = true },
                    )
                }
                if (state.isBusy) item(key = "progress") {
                    BackupProgressCard(state, onCancel, Modifier.animateItem())
                }
                if (state.automaticRunning && !state.isBusy) item(key = "automatic_progress") {
                    Card(
                        modifier = Modifier.animateItem(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    ) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                stringResource(R.string.backup_automatic_running),
                                Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                                style = MaterialTheme.typography.titleSmall,
                            )
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
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
                state.backedUpCount?.let { count ->
                    item(key = "backup_success") {
                        BackupSuccess(stringResource(R.string.backup_saved, count), Modifier.animateItem())
                    }
                }
                state.restoredCount?.let { count ->
                    item(key = "restore_success") {
                        BackupSuccess(stringResource(R.string.backup_restored, count), Modifier.animateItem())
                    }
                }
                if (state.deletedBackup) item(key = "delete_success") {
                    BackupSuccess(stringResource(R.string.backup_deleted), Modifier.animateItem())
                }
                if (state.backupUnchanged) item(key = "backup_unchanged") {
                    BackupSuccess(stringResource(R.string.backup_unchanged), Modifier.animateItem())
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
                            modifier = Modifier.animateItem(),
                        )
                    }
                    if (!state.settings.needsAuthorization && state.settings.lastAutomaticProblem != null && state.problem == null) {
                        item(key = "automatic_problem") {
                            Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                    if (state.history.isEmpty() && !busy && state.problem == null) {
                        item(key = "empty") {
                            BackupHistoryEmptyState(Modifier.animateItem())
                        }
                    }
                    itemsIndexed(recentHistory, key = { _, entry -> entry.id }) { index, entry ->
                        BackupTimelineEntry(
                            entry = entry,
                            isCurrent = entry.id == state.currentBackup?.id,
                            isFirst = index == 0,
                            isLast = index == recentHistory.lastIndex,
                            enabled = settingsEnabled,
                            onDelete = { deleteId = entry.id },
                            modifier = Modifier.animateItem(),
                        )
                    }
                    if (state.history.isNotEmpty()) item(key = "history_all") {
                        TextButton(
                            onClick = { showHistory = true },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        ) {
                            Text(stringResource(R.string.backup_history_all), textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }
    }
    if (showHistory && accountEmail != null) {
        ModalBottomSheet(
            onDismissRequest = { showHistory = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            modifier = Modifier.fillMaxSize(),
            sheetMaxWidth = 600.dp,
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            BackupHistoryContent(
                history = state.history,
                currentId = state.currentBackup?.id,
                enabled = settingsEnabled,
                onDelete = { deleteId = it.id },
                onClose = { showHistory = false },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
    if (showAbout) AlertDialog(
        onDismissRequest = { showAbout = false },
        icon = { Icon(painterResource(R.drawable.ic_cloud_backup), null) },
        title = { Text(stringResource(R.string.backup_about)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.backup_scope_note))
                Text(stringResource(R.string.backup_visibility))
                Text(stringResource(R.string.backup_schedule_note))
            }
        },
        confirmButton = {
            TextButton(onClick = { showAbout = false }) { Text(stringResource(R.string.backup_close)) }
        },
    )
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
    if (deleting != null && settingsEnabled) AlertDialog(
        onDismissRequest = { deleteId = null },
        icon = { Icon(painterResource(R.drawable.delete_24px), null, tint = MaterialTheme.colorScheme.error) },
        title = { Text(stringResource(R.string.backup_delete_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(backupDate(deleting.createdAtMillis), style = MaterialTheme.typography.titleSmall)
                Text(deleting.name, style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.backup_delete_body))
            }
        },
        confirmButton = {
            DialogActions(
                confirmText = stringResource(R.string.backup_delete),
                onConfirm = { deleteId = null; onDelete(deleting) },
                dismissText = stringResource(R.string.backup_cancel),
                onDismiss = { deleteId = null },
                confirmEnabled = !state.isSavingSettings,
                destructive = true,
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
private fun BackupHeroCard(
    state: DriveBackupUiState,
    connected: Boolean,
    authorized: Boolean,
    enabled: Boolean,
    onConnect: () -> Unit,
    onBackup: () -> Unit,
    modifier: Modifier = Modifier,
    onRestore: () -> Unit = {},
) {
    val context = LocalContext.current
    val lastBackup = state.currentBackup?.modifiedTimeMillis ?: state.settings.lastBackupAt
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BackupCloudIllustration(isWorking = state.isBusy || state.automaticRunning)
            Text(
                stringResource(R.string.backup_hero_title),
                Modifier.semantics { heading() },
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Text(
                if (!connected) stringResource(R.string.backup_intro)
                else state.localDocumentCount?.let {
                    stringResource(R.string.backup_summary, it, bidiArgument(Formatter.formatFileSize(context, state.localSizeBytes)))
                } ?: stringResource(R.string.backup_local_loading),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            Button(
                onClick = if (authorized) onBackup else onConnect,
                enabled = enabled && (!authorized || (state.localDocumentCount ?: 0) > 0),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = 52.dp),
            ) {
                Text(
                    stringResource(
                        when {
                            !connected -> R.string.backup_connect
                            !authorized -> R.string.backup_reconnect
                            else -> R.string.backup_now
                        },
                    ),
                    textAlign = TextAlign.Center,
                )
            }
            if (authorized && state.currentBackup != null) {
                TextButton(
                    onClick = onRestore,
                    enabled = enabled && !state.isSavingSettings,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Icon(painterResource(R.drawable.ic_document_download), null, Modifier.size(20.dp))
                    Text(
                        stringResource(R.string.backup_restore_latest),
                        Modifier.padding(start = 8.dp),
                        textAlign = TextAlign.Center,
                    )
                }
            }
            if (connected && state.localDocumentCount == 0) {
                Text(
                    stringResource(R.string.backup_no_documents),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                )
            } else if (connected && authorized) {
                Column(
                    Modifier.padding(top = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (lastBackup != null) {
                        Text(stringResource(R.string.backup_last), style = MaterialTheme.typography.labelSmall)
                    }
                    Text(
                        lastBackup?.let { backupDate(it) } ?: stringResource(
                            if (state.hasLoadedBackups) R.string.backup_never else R.string.backup_history_unloaded,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun BackupAccountCard(
    email: String,
    needsAuthorization: Boolean,
    enabled: Boolean,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showMenu by remember { mutableStateOf(false) }
    Row(
        modifier.fillMaxWidth().padding(start = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
            Icon(
                painterResource(R.drawable.ic_cloud_backup), null,
                Modifier.padding(12.dp).size(24.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.backup_provider), style = MaterialTheme.typography.titleSmall)
            Text(
                email,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (needsAuthorization) Text(
                stringResource(R.string.backup_authorization_required),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Box {
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
}

@Composable
private fun BackupProgressCard(state: DriveBackupUiState, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AnimatedContent(
                targetState = state.phase,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                label = "backup phase",
            ) { phase ->
                Text(stringResource(phase.label()), style = MaterialTheme.typography.titleSmall)
            }
            state.progress?.let { progress ->
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            } ?: LinearProgressIndicator(Modifier.fillMaxWidth())
            if (state.phase != DriveBackupPhase.DISCONNECTING && state.phase != DriveBackupPhase.DELETING) {
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
    var expanded by rememberSaveable { mutableStateOf(false) }
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, tween(220), label = "backup preferences")
    val frequency = stringResource(settings.frequency.label())
    val preferencesState = stringResource(
        if (expanded) R.string.backup_preferences_expanded else R.string.backup_preferences_collapsed,
    )
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        BackupSwitchRow(
            title = stringResource(R.string.backup_automatic_title),
            checked = settings.automaticEnabled,
            enabled = canToggle,
            onChange = onAutomaticEnabledChange,
            summary = if (settings.automaticEnabled) frequency else null,
        )
        AnimatedVisibility(visible = settings.automaticEnabled) {
            Column {
                HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                Surface(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.semantics { stateDescription = preferencesState },
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(painterResource(R.drawable.ic_tool_adjust), null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                        Text(
                            stringResource(R.string.backup_preferences),
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Icon(painterResource(R.drawable.ic_fill_collapse), null, Modifier.size(20.dp).rotate(rotation))
                    }
                }
                AnimatedVisibility(visible = expanded) {
                    Column {
                        Surface(
                            onClick = onFrequency,
                            enabled = enabled,
                            modifier = Modifier.semantics { stateDescription = frequency },
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                        ) {
                            Column(
                                Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(stringResource(R.string.backup_frequency), style = MaterialTheme.typography.titleSmall)
                                Text(frequency, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        BackupSwitchRow(
                            title = stringResource(R.string.backup_wifi_only),
                            checked = settings.wifiOnly,
                            enabled = enabled,
                            onChange = onWifiOnlyChange,
                        )
                        BackupSwitchRow(
                            title = stringResource(R.string.backup_charging_only),
                            checked = settings.chargingOnly,
                            enabled = enabled,
                            onChange = onChargingOnlyChange,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BackupSwitchRow(
    title: String,
    checked: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 64.dp)
            .toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (summary != null) Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    Surface(
        modifier = modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Row(
            Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(R.drawable.ic_check_24), null, Modifier.size(24.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun BackupHistoryEmptyState(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth().testTag("backup_history_empty"),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            Modifier.padding(horizontal = 24.dp, vertical = 20.dp).semantics(mergeDescendants = true) {},
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BackupHistoryIllustration()
            Text(
                stringResource(R.string.backup_empty),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Text(
                stringResource(R.string.backup_empty_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun BackupHistoryContent(
    history: List<BackupHistoryEntry>,
    currentId: String?,
    enabled: Boolean,
    onDelete: (BackupHistoryEntry) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Row(
            Modifier.fillMaxWidth().padding(start = 24.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(R.string.backup_history_title),
                Modifier.weight(1f).semantics { heading() },
                style = MaterialTheme.typography.titleLarge,
            )
            IconButton(onClick = onClose) {
                Icon(painterResource(R.drawable.ic_toast_close), stringResource(R.string.backup_history_close))
            }
        }
        Text(
            stringResource(R.string.backup_history_note),
            Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f).testTag("backup_history"),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (history.isEmpty()) item(key = "empty") {
                BackupHistoryEmptyState(Modifier.animateItem())
            }
            itemsIndexed(history, key = { _, entry -> entry.id }) { index, entry ->
                BackupTimelineEntry(
                    entry = entry,
                    isCurrent = entry.id == currentId,
                    isFirst = index == 0,
                    isLast = index == history.lastIndex,
                    enabled = enabled,
                    onDelete = { onDelete(entry) },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

@Composable
private fun BackupTimelineEntry(
    entry: BackupHistoryEntry,
    isCurrent: Boolean,
    isFirst: Boolean,
    isLast: Boolean,
    enabled: Boolean,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val date = backupDate(entry.createdAtMillis)
    val colors = MaterialTheme.colorScheme
    var showMenu by remember(entry.id) { mutableStateOf(false) }
    Row(
        modifier.fillMaxWidth().drawBehind {
            val x = if (layoutDirection == LayoutDirection.Ltr) 16.dp.toPx() else size.width - 16.dp.toPx()
            val markerY = 24.dp.toPx()
            drawLine(
                color = colors.outlineVariant,
                start = Offset(x, if (isFirst) markerY else 0f),
                end = Offset(x, if (isLast) markerY else size.height + 16.dp.toPx()),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 6.dp.toPx())),
            )
        }.heightIn(min = 64.dp).padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Surface(
            shape = CircleShape,
            color = if (isCurrent) colors.secondaryContainer else colors.surfaceContainerHigh,
            contentColor = if (isCurrent) colors.onSecondaryContainer else colors.onSurfaceVariant,
        ) {
            Icon(
                painterResource(if (isCurrent) R.drawable.ic_tag_star else R.drawable.ic_tag_document),
                null, Modifier.padding(8.dp).size(16.dp),
            )
        }
        Column(
            Modifier.weight(1f).padding(vertical = 2.dp).semantics(mergeDescendants = true) {},
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (isCurrent) Text(
                stringResource(R.string.backup_latest),
                style = MaterialTheme.typography.labelSmall,
                color = colors.primary,
            )
            Text(date, style = MaterialTheme.typography.titleSmall)
            Text(
                entry.documentCount?.let { count ->
                    stringResource(R.string.backup_summary, count, bidiArgument(Formatter.formatFileSize(context, entry.sizeBytes)))
                } ?: Formatter.formatFileSize(context, entry.sizeBytes),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
        Box {
            IconButton(onClick = { showMenu = true }, enabled = enabled) {
                Icon(
                    painterResource(R.drawable.more_vert_24px),
                    stringResource(R.string.backup_entry_options, bidiArgument(date)),
                )
            }
            DropdownMenu(expanded = showMenu && enabled, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.backup_delete), color = colors.error) },
                    leadingIcon = { Icon(painterResource(R.drawable.delete_24px), null, tint = colors.error) },
                    onClick = { showMenu = false; onDelete() },
                )
            }
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
    DriveBackupPhase.DELETING -> R.string.backup_deleting
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
@Preview(name = "Backup · escuro", widthDp = 390, heightDp = 900, locale = "pt-rBR", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
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
                currentBackup = DriveBackupEntry("preview-0", "Scaniqo-2026-10-17.zip", 1792230000000L, 12_340_000),
                history = previewBackupHistory(),
            ), {}, {}, {}, {}, {}, {},
        )
    }
}

@Preview(name = "Timeline · histórico", widthDp = 390, heightDp = 340, locale = "pt-rBR")
@Preview(name = "Timeline · compacto", widthDp = 320, heightDp = 420, fontScale = 1.3f, locale = "pt-rBR")
@Preview(name = "Timeline · escuro", widthDp = 390, heightDp = 340, locale = "pt-rBR", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun BackupTimelinePreview() {
    ScaniqoTheme {
        Surface {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.backup_list_title), style = MaterialTheme.typography.titleMedium)
                repeat(3) { index ->
                    BackupTimelineEntry(
                        entry = BackupHistoryEntry(
                            "preview-$index", "Scaniqo.zip",
                            1792230000000L - index * 86_400_000L, 12_340_000L - index * 1_200_000L,
                        ),
                        isCurrent = index == 0,
                        isFirst = index == 0,
                        isLast = index == 2,
                        enabled = true,
                        onDelete = {},
                    )
                }
            }
        }
    }
}

private fun previewBackupHistory(): List<BackupHistoryEntry> = List(8) { index ->
    BackupHistoryEntry(
        id = "preview-$index",
        name = "Scaniqo.zip",
        createdAtMillis = 1792230000000L - index * 86_400_000L,
        sizeBytes = 12_340_000L - index * 1_200_000L,
        documentCount = 12 - index,
    )
}

@Preview(name = "Histórico completo", widthDp = 390, heightDp = 800, locale = "pt-rBR")
@Preview(name = "Histórico · fonte grande", widthDp = 320, heightDp = 640, fontScale = 1.5f, locale = "pt-rBR")
@Composable
private fun BackupHistoryPreview() {
    ScaniqoTheme {
        Surface {
            BackupHistoryContent(
                history = previewBackupHistory(),
                currentId = "preview-0",
                enabled = true,
                onDelete = {},
                onClose = {},
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Preview(name = "Histórico vazio · claro", widthDp = 390, heightDp = 320, locale = "pt-rBR")
@Preview(name = "Histórico vazio · escuro", widthDp = 390, heightDp = 320, locale = "pt-rBR", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Histórico vazio · fonte grande", widthDp = 320, heightDp = 400, fontScale = 1.5f, locale = "pt-rBR")
@Composable
private fun BackupHistoryEmptyPreview() {
    ScaniqoTheme {
        Surface {
            Box(Modifier.padding(16.dp), contentAlignment = Alignment.Center) {
                BackupHistoryEmptyState()
            }
        }
    }
}

@Preview(name = "Histórico completo · vazio", widthDp = 390, heightDp = 700, locale = "pt-rBR")
@Composable
private fun BackupHistoryEmptySheetPreview() {
    ScaniqoTheme {
        Surface {
            BackupHistoryContent(
                history = emptyList(),
                currentId = null,
                enabled = true,
                onDelete = {},
                onClose = {},
                modifier = Modifier.fillMaxSize(),
            )
        }
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
