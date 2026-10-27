package com.joseleandro.scaniqo.data.backup

import com.joseleandro.scaniqo.domain.model.BackupHistoryEntry
import com.joseleandro.scaniqo.domain.model.DriveBackupEntry
import com.joseleandro.scaniqo.domain.repository.BackupHistoryRepository
import com.joseleandro.scaniqo.domain.repository.DriveBackupRemote
import java.io.File
import java.util.Locale
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

/** Os chamadores compartilham o BackupOperationGate para serializar operações da conta. */
class BackupArchiveStore(
    private val remote: DriveBackupRemote,
    private val history: BackupHistoryRepository,
) {
    private val currentState = MutableStateFlow<Map<String, DriveBackupEntry?>>(emptyMap())
    val currentBackups = currentState.asStateFlow()

    suspend fun loadCurrent(accessToken: String, accountEmail: String): DriveBackupEntry? {
        val current = remote.listBackups(accessToken).ordered().firstOrNull()
        currentCoroutineContext().ensureActive()
        publishCurrent(accountEmail, current)
        return current
    }

    suspend fun synchronize(accessToken: String, accountEmail: String): DriveBackupEntry? {
        val backups = remote.listBackups(accessToken).ordered()
        currentCoroutineContext().ensureActive()
        publishCurrent(accountEmail, backups.firstOrNull())
        history.record(accountEmail, backups.map { it.historyEntry() })
        val current = backups.firstOrNull() ?: return null
        removePrevious(accessToken, backups, current.id)
        return current
    }

    suspend fun upload(
        accessToken: String,
        accountEmail: String,
        archive: File,
        name: String,
        documentCount: Int,
        onProgress: (Long, Long) -> Unit = { _, _ -> },
        onUploaded: suspend (DriveBackupEntry) -> Unit = {},
    ): DriveBackupEntry {
        require(documentCount >= 0)
        val previous = remote.listBackups(accessToken).ordered()
        currentCoroutineContext().ensureActive()
        publishCurrent(accountEmail, previous.firstOrNull())
        history.record(accountEmail, previous.map { it.historyEntry() })
        currentCoroutineContext().ensureActive()
        val uploaded = remote.upload(accessToken, archive, name, onProgress)
        publishCurrent(accountEmail, uploaded)
        // A cópia confirmada e seu histórico precisam sobreviver ao cancelamento da tela/worker.
        withContext(NonCancellable) {
            onUploaded(uploaded)
            history.record(
                accountEmail,
                previous.filterNot { it.id == uploaded.id }.map { it.historyEntry() } +
                    uploaded.historyEntry(documentCount),
            )
        }
        currentCoroutineContext().ensureActive()
        removePrevious(accessToken, previous, uploaded.id)
        return uploaded
    }

    private fun publishCurrent(accountEmail: String, entry: DriveBackupEntry?) {
        val key = accountEmail.trim().lowercase(Locale.ROOT)
        require(key.isNotEmpty())
        currentState.update { it + (key to entry) }
    }

    private suspend fun removePrevious(accessToken: String, entries: List<DriveBackupEntry>, currentId: String) {
        entries.filterNot { it.id == currentId }.forEach { entry ->
            currentCoroutineContext().ensureActive()
            remote.delete(accessToken, entry)
        }
    }
}

private fun List<DriveBackupEntry>.ordered() =
    sortedWith(compareByDescending<DriveBackupEntry> { it.modifiedTimeMillis }.thenByDescending { it.id })
        .distinctBy(DriveBackupEntry::id)

private fun DriveBackupEntry.historyEntry(documentCount: Int? = null) = BackupHistoryEntry(
    id = id,
    name = name,
    createdAtMillis = modifiedTimeMillis,
    sizeBytes = sizeBytes,
    documentCount = documentCount,
)
