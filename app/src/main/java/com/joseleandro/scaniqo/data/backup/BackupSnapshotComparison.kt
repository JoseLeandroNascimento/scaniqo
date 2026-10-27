package com.joseleandro.scaniqo.data.backup

import com.joseleandro.scaniqo.domain.model.BackupSettings
import com.joseleandro.scaniqo.domain.model.DriveBackupEntry
import com.joseleandro.scaniqo.domain.model.matchesCurrentBackup
import com.joseleandro.scaniqo.domain.repository.DocumentBackupRepository
import com.joseleandro.scaniqo.domain.repository.DriveBackupRemote
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Confere cópias antigas ou alteradas no Drive antes de decidir se outro envio é necessário. */
internal suspend fun matchesBackupSnapshot(
    snapshot: String,
    current: DriveBackupEntry?,
    saved: BackupSettings,
    fingerprint: BackupSnapshotFingerprint,
    local: DocumentBackupRepository,
    remote: DriveBackupRemote,
    accessToken: String,
    beforeDownload: suspend () -> Unit = {},
): Boolean {
    current ?: return false
    if (saved.matchesCurrentBackup(current, snapshot)) return true
    if (saved.lastBackupId == current.id && saved.lastBackupAt == current.modifiedTimeMillis &&
        saved.lastBackupBytes == current.sizeBytes &&
        BackupSnapshotFingerprint.recognizes(saved.lastSnapshotFingerprint)) return false

    beforeDownload()
    val temporary = local.createRestoreFile()
    try {
        remote.download(accessToken, current, temporary)
        currentCoroutineContext().ensureActive()
        val remoteSnapshot = fingerprint.fromArchive(temporary)
        check(snapshot == fingerprint.current()) { "Os PDFs mudaram durante a verificação do backup." }
        return remoteSnapshot == snapshot
    } finally {
        withContext(NonCancellable) { runCatching { local.discard(temporary) } }
    }
}
