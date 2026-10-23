package com.joseleandro.scaniqo.data.backup

import android.app.Application
import android.content.Context
import com.joseleandro.scaniqo.domain.model.BackupAutomaticProblem
import com.joseleandro.scaniqo.domain.model.BackupFrequency
import com.joseleandro.scaniqo.domain.model.BackupSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, manifest = Config.NONE)
class AndroidBackupSettingsRepositoryTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val preferences get() = context.getSharedPreferences("backup_settings", Context.MODE_PRIVATE)

    @Before fun reset() {
        check(preferences.edit().clear().commit())
    }

    @Test fun defaultsKeepAutomaticBackupOptInAndWifiOnly() {
        assertEquals(BackupSettings(), AndroidBackupSettingsRepository(context).settings.value)
        assertTrue(preferences.all.isEmpty())
    }

    @Test fun preferencesAndLastSuccessfulBackupSurviveRestart() = runTest {
        val expected = BackupSettings(
            accountEmail = "owner@example.com",
            automaticEnabled = true,
            frequency = BackupFrequency.WEEKLY,
            wifiOnly = false,
            chargingOnly = true,
            lastBackupAt = 1_000L,
            lastBackupBytes = 4_096L,
            lastBackupDocumentCount = 4,
            lastSnapshotFingerprint = "snapshot",
            needsAuthorization = true,
            lastAutomaticProblem = BackupAutomaticProblem.NETWORK,
        )
        val repository = AndroidBackupSettingsRepository(context)
        repository.update { expected }
        assertEquals(expected, repository.settings.value)
        assertEquals(expected, AndroidBackupSettingsRepository(context).settings.value)
        assertFalse(preferences.all.keys.any { "token" in it })
    }

    @Test fun disconnectCanClearAccountAndPreviousSnapshot() = runTest {
        val repository = AndroidBackupSettingsRepository(context)
        repository.update {
            it.copy(accountEmail = "owner@example.com", automaticEnabled = true, lastSnapshotFingerprint = "old")
        }
        repository.update { BackupSettings(frequency = it.frequency, wifiOnly = it.wifiOnly) }
        assertEquals(BackupSettings(), AndroidBackupSettingsRepository(context).settings.value)
        assertFalse(preferences.contains("account_email"))
        assertFalse(preferences.contains("last_snapshot_fingerprint"))
    }

    @Test fun corruptTypesAndUnknownEnumsFallBackIndependently() {
        check(preferences.edit()
            .putInt("account_email", 42)
            .putString("automatic_enabled", "bad")
            .putString("frequency", "MONTHLY")
            .putInt("wifi_only", 5)
            .putString("charging_only", "bad")
            .putLong("last_backup_at", -1)
            .putLong("last_backup_bytes", -3)
            .putInt("last_backup_document_count", -7)
            .putString("last_automatic_problem", "UNKNOWN")
            .commit())
        assertEquals(BackupSettings(), AndroidBackupSettingsRepository(context).settings.value)
    }

    @Test fun concurrentWorkerAndUiUpdatesPreserveBothChanges() = runTest {
        val repository = AndroidBackupSettingsRepository(context)
        coroutineScope {
            List(32) {
                launch(Dispatchers.Default) {
                    repository.update { it.copy(lastBackupDocumentCount = it.lastBackupDocumentCount + 1) }
                }
            }.joinAll()
        }
        repository.update { it.copy(frequency = BackupFrequency.WEEKLY) }
        val restored = AndroidBackupSettingsRepository(context).settings.value
        assertEquals(32, restored.lastBackupDocumentCount)
        assertEquals(BackupFrequency.WEEKLY, restored.frequency)
    }
}
