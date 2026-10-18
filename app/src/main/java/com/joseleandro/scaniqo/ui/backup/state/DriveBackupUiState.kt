package com.joseleandro.scaniqo.ui.backup.state

import com.joseleandro.scaniqo.domain.model.DriveBackupEntry

enum class DriveBackupPhase {
    IDLE, AUTHORIZING, CONNECTING, PREPARING, UPLOADING, DOWNLOADING, RESTORING, DISCONNECTING, CANCELLING
}

enum class DriveBackupProblem {
    AUTHORIZATION, CONFIGURATION, PERMISSION, NETWORK, QUOTA, LOCAL_STORAGE, REMOTE, ACCOUNT_CHANGED
}

data class DriveBackupUiState(
    val phase: DriveBackupPhase = DriveBackupPhase.IDLE,
    val accountEmail: String? = null,
    val localDocumentCount: Int? = null,
    val backups: List<DriveBackupEntry> = emptyList(),
    val progress: Float? = null,
    val problem: DriveBackupProblem? = null,
    val backedUpCount: Int? = null,
    val restoredCount: Int? = null,
) {
    val isBusy: Boolean get() = phase != DriveBackupPhase.IDLE
}
