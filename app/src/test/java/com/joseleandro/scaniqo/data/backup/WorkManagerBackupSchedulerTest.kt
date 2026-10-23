package com.joseleandro.scaniqo.data.backup

import android.app.Application
import androidx.work.BackoffPolicy
import androidx.work.NetworkType
import com.joseleandro.scaniqo.domain.model.BackupFrequency
import com.joseleandro.scaniqo.domain.model.BackupSettings
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, manifest = Config.NONE)
class WorkManagerBackupSchedulerTest {
    @Test fun dailyDefaultDefersUntilUnmeteredNetworkBatteryAndStorageAreAvailable() {
        val spec = WorkManagerBackupScheduler.createRequest(BackupSettings()).workSpec
        assertEquals(TimeUnit.DAYS.toMillis(1), spec.intervalDuration)
        assertEquals(NetworkType.UNMETERED, spec.constraints.requiredNetworkType)
        assertFalse(spec.constraints.requiresCharging())
        assertTrue(spec.constraints.requiresBatteryNotLow())
        assertTrue(spec.constraints.requiresStorageNotLow())
        assertEquals(BackoffPolicy.EXPONENTIAL, spec.backoffPolicy)
        assertEquals(TimeUnit.MINUTES.toMillis(1), spec.backoffDelayDuration)
    }

    @Test fun weeklyChargingAndMobileDataPreferencesReachWorkManager() {
        val spec = WorkManagerBackupScheduler.createRequest(
            BackupSettings(frequency = BackupFrequency.WEEKLY, wifiOnly = false, chargingOnly = true),
        ).workSpec
        assertEquals(TimeUnit.DAYS.toMillis(7), spec.intervalDuration)
        assertEquals(NetworkType.CONNECTED, spec.constraints.requiredNetworkType)
        assertTrue(spec.constraints.requiresCharging())
        assertEquals(AutomaticBackupWorker::class.java.name, spec.workerClassName)
    }
}
