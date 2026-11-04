package com.joseleandro.scaniqo.ui.backup.effect

sealed interface DriveBackupEffect {
    data class Authorize(val requestId: Long) : DriveBackupEffect
    data class Revoke(val requestId: Long, val email: String) : DriveBackupEffect
    /** Efêmero: nunca entra no SavedStateHandle, log, banco ou arquivo. */
    class ClearToken(val token: String) : DriveBackupEffect
}
