package com.joseleandro.scaniqo.data.backup

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.joseleandro.scaniqo.domain.model.BackupFrequency
import com.joseleandro.scaniqo.domain.model.BackupSettings
import com.joseleandro.scaniqo.domain.repository.BackupScheduler
import java.util.concurrent.TimeUnit

class WorkManagerBackupScheduler(context: Context) : BackupScheduler {
    private val workManager = WorkManager.getInstance(context.applicationContext)

    override fun schedule(settings: BackupSettings) {
        if (!settings.automaticEnabled || settings.accountEmail.isNullOrBlank() || settings.needsAuthorization) {
            cancel()
            return
        }
        workManager.enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            createRequest(settings),
        )
    }

    override fun cancel() {
        workManager.cancelUniqueWork(UNIQUE_WORK_NAME)
    }

    internal companion object {
        const val UNIQUE_WORK_NAME = "automatic_document_backup"

        fun createRequest(settings: BackupSettings): PeriodicWorkRequest =
            PeriodicWorkRequestBuilder<AutomaticBackupWorker>(
                if (settings.frequency == BackupFrequency.WEEKLY) 7L else 1L,
                TimeUnit.DAYS,
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(if (settings.wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                        .setRequiresCharging(settings.chargingOnly)
                        .setRequiresBatteryNotLow(true)
                        .setRequiresStorageNotLow(true)
                        .build(),
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1L, TimeUnit.MINUTES)
                .build()
    }
}
