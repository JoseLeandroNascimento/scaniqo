package com.joseleandro.scaniqo.data.backup

import com.joseleandro.scaniqo.domain.model.BackupAutomaticProblem
import com.joseleandro.scaniqo.domain.model.DriveBackupException
import com.joseleandro.scaniqo.domain.repository.BackupSettingsRepository
import com.joseleandro.scaniqo.domain.repository.DocumentBackupRepository
import com.joseleandro.scaniqo.domain.repository.DriveBackupRemote
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

enum class AutomaticBackupOutcome { COMPLETED, RETRY }

/** Executa o mesmo backup de PDFs da tela, sem solicitar interação ou guardar credenciais. */
class AutomaticBackupRunner(
    private val settings: BackupSettingsRepository,
    private val backups: DocumentBackupRepository,
    private val remote: DriveBackupRemote,
    private val authorization: BackupAuthorizationProvider,
    private val fingerprint: BackupSnapshotFingerprint,
    private val gate: BackupOperationGate,
    private val now: () -> Long = System::currentTimeMillis,
) {
    suspend fun run(
        runAttemptCount: Int,
        onBackupStarting: suspend () -> Unit = {},
    ): AutomaticBackupOutcome = gate.mutex.withLock {
        val saved = settings.settings.value
        val email = saved.accountEmail
        if (!saved.automaticEnabled || email.isNullOrBlank() || saved.needsAuthorization) {
            return@withLock AutomaticBackupOutcome.COMPLETED
        }
        gate.automaticRunning.value = true
        var archive: File? = null
        var issuedToken: String? = null
        try {
            val snapshot = fingerprint.current() ?: return@withLock AutomaticBackupOutcome.COMPLETED
            if (snapshot == saved.lastSnapshotFingerprint) return@withLock AutomaticBackupOutcome.COMPLETED

            val token = try {
                withTimeout(60_000L) { authorization.accessToken(email) }
            } catch (_: TimeoutCancellationException) {
                throw DriveBackupException.Network()
            }
            issuedToken = token
            // A autorização é vinculada à conta salva; conferimos também o destino real do token.
            if (!email.equals(remote.accountEmail(token), ignoreCase = true)) {
                throw DriveBackupException.Unauthorized()
            }
            if (!isStillEnabled(email)) return@withLock AutomaticBackupOutcome.COMPLETED

            onBackupStarting()
            val prepared = backups.createBackup()
            archive = prepared.file
            if (snapshot != fingerprint.current()) return@withLock retryOrFinish(runAttemptCount)
            currentCoroutineContext().ensureActive()
            if (!isStillEnabled(email)) return@withLock AutomaticBackupOutcome.COMPLETED
            val size = prepared.file.length()
            remote.upload(token, prepared.file, backupName(now()))
            // O Drive já confirmou a cópia. Desligar o automático agora não deve perder esse resultado
            // nem provocar outra cópia idêntica ao reativar; preservamos a preferência atual.
            withContext(NonCancellable) {
                settings.update { current ->
                    if (current.accountEmail.equals(email, ignoreCase = true)) {
                        current.copy(
                            lastBackupAt = now(),
                            lastBackupBytes = size,
                            lastBackupDocumentCount = prepared.documentCount,
                            lastSnapshotFingerprint = snapshot,
                            needsAuthorization = false,
                            lastAutomaticProblem = null,
                        )
                    } else current
                }
            }
            AutomaticBackupOutcome.COMPLETED
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: DriveBackupException.Unauthorized) {
            recordFailure(email, needsAuthorization = true)
            issuedToken?.let { token ->
                withTimeoutOrNull(10_000L) {
                    try { authorization.invalidate(token) }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { /* A tela ainda poderá reconectar mesmo se a limpeza falhar. */ }
                }
            }
            AutomaticBackupOutcome.COMPLETED
        } catch (_: DriveBackupException.Quota) {
            recordFailure(email, BackupAutomaticProblem.QUOTA)
            AutomaticBackupOutcome.COMPLETED
        } catch (_: DriveBackupException.Configuration) {
            recordFailure(email, BackupAutomaticProblem.CONFIGURATION)
            AutomaticBackupOutcome.COMPLETED
        } catch (_: DriveBackupException.ApiDisabled) {
            recordFailure(email, BackupAutomaticProblem.CONFIGURATION)
            AutomaticBackupOutcome.COMPLETED
        } catch (_: DriveBackupException.AccessBlocked) {
            recordFailure(email, BackupAutomaticProblem.REMOTE)
            AutomaticBackupOutcome.COMPLETED
        } catch (_: DriveBackupException.Network) {
            recordFailure(email, BackupAutomaticProblem.NETWORK)
            retryOrFinish(runAttemptCount)
        } catch (error: DriveBackupException.Remote) {
            recordFailure(email, BackupAutomaticProblem.REMOTE)
            if (error.statusCode == 408 || error.statusCode == 429 || error.statusCode in 500..599) {
                retryOrFinish(runAttemptCount)
            } else AutomaticBackupOutcome.COMPLETED
        } catch (_: AutomaticBackupDeferredException) {
            retryOrFinish(runAttemptCount)
        } catch (_: Exception) {
            recordFailure(email, BackupAutomaticProblem.LOCAL_STORAGE)
            AutomaticBackupOutcome.COMPLETED
        } finally {
            try {
                archive?.let { file ->
                    withContext(NonCancellable) { runCatching { backups.discard(file) } }
                }
            } finally {
                gate.automaticRunning.value = false
            }
        }
    }

    private fun isStillEnabled(email: String): Boolean = settings.settings.value.let {
        it.automaticEnabled && !it.needsAuthorization && it.accountEmail.equals(email, ignoreCase = true)
    }

    private suspend fun recordFailure(
        email: String,
        problem: BackupAutomaticProblem? = null,
        needsAuthorization: Boolean = false,
    ) {
        try {
            settings.update { current ->
                if (current.accountEmail.equals(email, ignoreCase = true)) {
                    current.copy(needsAuthorization = needsAuthorization, lastAutomaticProblem = problem)
                } else current
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Uma falha do armazenamento de preferências não pode encerrar o trabalho periódico.
        }
    }

    private fun retryOrFinish(attempt: Int): AutomaticBackupOutcome =
        if (attempt < MAX_ATTEMPTS - 1) AutomaticBackupOutcome.RETRY else AutomaticBackupOutcome.COMPLETED

    private fun backupName(timestamp: Long): String =
        "Scaniqo-" + SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date(timestamp)) + ".zip"

    private companion object {
        const val MAX_ATTEMPTS = 3
    }
}
