package com.joseleandro.scaniqo.domain.repository

import com.joseleandro.scaniqo.domain.model.BackupHistoryEntry
import kotlinx.coroutines.flow.StateFlow

interface BackupHistoryRepository {
    /** Chaves são e-mails sem espaços externos, em minúsculas. */
    val entries: StateFlow<Map<String, List<BackupHistoryEntry>>>

    suspend fun record(accountEmail: String, entries: List<BackupHistoryEntry>)

    /** Remove somente o registro local e impede que uma sincronização o importe novamente. */
    suspend fun delete(accountEmail: String, id: String)
}
