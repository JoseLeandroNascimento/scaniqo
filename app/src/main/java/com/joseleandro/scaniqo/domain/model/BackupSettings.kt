package com.joseleandro.scaniqo.domain.model

enum class BackupFrequency { DAILY, WEEKLY }

enum class BackupAutomaticProblem { NETWORK, QUOTA, LOCAL_STORAGE, REMOTE, CONFIGURATION }

data class BackupSettings(
    val accountEmail: String? = null,
    val automaticEnabled: Boolean = false,
    val frequency: BackupFrequency = BackupFrequency.DAILY,
    val wifiOnly: Boolean = true,
    val chargingOnly: Boolean = false,
    val lastBackupAt: Long? = null,
    val lastBackupBytes: Long = 0,
    val lastBackupDocumentCount: Int = 0,
    val lastSnapshotFingerprint: String? = null,
    val needsAuthorization: Boolean = false,
    val lastAutomaticProblem: BackupAutomaticProblem? = null,
)
