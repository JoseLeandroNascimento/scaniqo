package com.joseleandro.scaniqo.ui.backup.state

import com.joseleandro.scaniqo.domain.model.BackupHistoryEntry
import com.joseleandro.scaniqo.domain.model.BackupSettings
import com.joseleandro.scaniqo.domain.model.DriveBackupEntry

enum class DriveBackupPhase {
    IDLE, AUTHORIZING, CONNECTING, PREPARING, UPLOADING, DOWNLOADING, RESTORING, DELETING, DISCONNECTING, CANCELLING
}

enum class DriveBackupProblem {
    AUTHORIZATION, CONFIGURATION, API_DISABLED, ACCESS_BLOCKED, PERMISSION, NETWORK, QUOTA, LOCAL_STORAGE, REMOTE, ACCOUNT_CHANGED
}

data class DriveBackupUiState(
    val phase: DriveBackupPhase = DriveBackupPhase.IDLE,
    val accountEmail: String? = null,
    val localDocumentCount: Int? = null,
    val localSizeBytes: Long = 0,
    val currentBackup: DriveBackupEntry? = null,
    val history: List<BackupHistoryEntry> = emptyList(),
    val hasLoadedBackups: Boolean = false,
    val progress: Float? = null,
    val problem: DriveBackupProblem? = null,
    val backedUpCount: Int? = null,
    val backupUnchanged: Boolean = false,
    val restoredCount: Int? = null,
    val deletedBackup: Boolean = false,
    val settings: BackupSettings = BackupSettings(),
    val isSavingSettings: Boolean = false,
    val automaticRunning: Boolean = false,
) {
    val isBusy: Boolean get() = phase != DriveBackupPhase.IDLE
}
