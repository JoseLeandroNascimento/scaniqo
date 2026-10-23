package com.joseleandro.scaniqo.data.backup

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.joseleandro.scaniqo.domain.model.DriveBackupException
import kotlinx.coroutines.CancellationException
import org.koin.core.context.GlobalContext

class AutomaticBackupWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val runner = GlobalContext.get().get<AutomaticBackupRunner>()
        val outcome = runner.run(runAttemptCount) {
            try {
                setForeground(createBackupForegroundInfo(applicationContext))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: SecurityException) {
                throw DriveBackupException.Configuration()
            } catch (_: IllegalStateException) {
                // O Android pode restringir serviços nesta execução; a próxima tentativa respeita o backoff.
                throw AutomaticBackupDeferredException()
            }
        }
        return when (outcome) {
            AutomaticBackupOutcome.COMPLETED -> Result.success()
            AutomaticBackupOutcome.RETRY -> Result.retry()
        }
    }
}

internal class AutomaticBackupDeferredException : Exception()
