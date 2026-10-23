package com.joseleandro.scaniqo.data.backup

import android.content.Context
import android.content.SharedPreferences
import com.joseleandro.scaniqo.domain.model.BackupAutomaticProblem
import com.joseleandro.scaniqo.domain.model.BackupFrequency
import com.joseleandro.scaniqo.domain.model.BackupSettings
import com.joseleandro.scaniqo.domain.repository.BackupSettingsRepository
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Guarda somente preferências e o resultado do backup. Tokens nunca são persistidos. */
class AndroidBackupSettingsRepository(context: Context) : BackupSettingsRepository {
    private val preferences = context.applicationContext.getSharedPreferences("backup_settings", Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private val state = MutableStateFlow(readSettings())
    override val settings = state.asStateFlow()

    override suspend fun update(transform: (BackupSettings) -> BackupSettings) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val next = transform(state.value)
            if (next == state.value) return@withLock
            val saved = preferences.edit()
                .putString("account_email", next.accountEmail)
                .putBoolean("automatic_enabled", next.automaticEnabled)
                .putString("frequency", next.frequency.name)
                .putBoolean("wifi_only", next.wifiOnly)
                .putBoolean("charging_only", next.chargingOnly)
                .putLong("last_backup_at", next.lastBackupAt ?: 0L)
                .putLong("last_backup_bytes", next.lastBackupBytes)
                .putInt("last_backup_document_count", next.lastBackupDocumentCount)
                .putString("last_snapshot_fingerprint", next.lastSnapshotFingerprint)
                .putBoolean("needs_authorization", next.needsAuthorization)
                .putString("last_automatic_problem", next.lastAutomaticProblem?.name)
                .commit()
            if (!saved) throw IOException("Não foi possível salvar as preferências de backup.")
            state.value = next
        }
    }

    private fun readSettings() = BackupSettings(
        accountEmail = preferences.text("account_email")?.takeIf(String::isNotBlank),
        automaticEnabled = preferences.boolean("automatic_enabled", false),
        frequency = preferences.enum<BackupFrequency>("frequency") ?: BackupFrequency.DAILY,
        wifiOnly = preferences.boolean("wifi_only", true),
        chargingOnly = preferences.boolean("charging_only", false),
        lastBackupAt = preferences.long("last_backup_at").takeIf { it > 0L },
        lastBackupBytes = preferences.long("last_backup_bytes").coerceAtLeast(0),
        lastBackupDocumentCount = runCatching { preferences.getInt("last_backup_document_count", 0) }
            .getOrDefault(0).coerceAtLeast(0),
        lastSnapshotFingerprint = preferences.text("last_snapshot_fingerprint"),
        needsAuthorization = preferences.boolean("needs_authorization", false),
        lastAutomaticProblem = preferences.enum<BackupAutomaticProblem>("last_automatic_problem"),
    )
}

private inline fun <reified T : Enum<T>> SharedPreferences.enum(key: String): T? =
    runCatching { getString(key, null)?.let { enumValueOf<T>(it) } }.getOrNull()

private fun SharedPreferences.text(key: String): String? =
    runCatching { getString(key, null) }.getOrNull()

private fun SharedPreferences.boolean(key: String, fallback: Boolean): Boolean =
    runCatching { getBoolean(key, fallback) }.getOrDefault(fallback)

private fun SharedPreferences.long(key: String): Long =
    runCatching { getLong(key, 0L) }.getOrDefault(0L)
