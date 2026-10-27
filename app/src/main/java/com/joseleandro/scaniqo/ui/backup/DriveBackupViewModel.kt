package com.joseleandro.scaniqo.ui.backup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joseleandro.scaniqo.data.backup.BackupArchiveStore
import com.joseleandro.scaniqo.data.backup.BackupOperationGate
import com.joseleandro.scaniqo.data.backup.BackupSnapshotFingerprint
import com.joseleandro.scaniqo.data.backup.matchesBackupSnapshot
import com.joseleandro.scaniqo.domain.model.BackupHistoryEntry
import com.joseleandro.scaniqo.domain.model.BackupFrequency
import com.joseleandro.scaniqo.domain.model.BackupSettings
import com.joseleandro.scaniqo.domain.model.DriveBackupEntry
import com.joseleandro.scaniqo.domain.model.DriveBackupException
import com.joseleandro.scaniqo.domain.repository.BackupHistoryRepository
import com.joseleandro.scaniqo.domain.repository.BackupScheduler
import com.joseleandro.scaniqo.domain.repository.BackupSettingsRepository
import com.joseleandro.scaniqo.domain.repository.DocumentBackupRepository
import com.joseleandro.scaniqo.domain.repository.DocumentRepository
import com.joseleandro.scaniqo.domain.repository.DriveBackupRemote
import com.joseleandro.scaniqo.ui.backup.effect.DriveBackupEffect
import com.joseleandro.scaniqo.ui.backup.state.DriveBackupPhase
import com.joseleandro.scaniqo.ui.backup.state.DriveBackupProblem
import com.joseleandro.scaniqo.ui.backup.state.DriveBackupUiState
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class DriveBackupViewModel(
    private val local: DocumentBackupRepository,
    private val remote: DriveBackupRemote,
    documents: DocumentRepository,
    private val preferences: BackupSettingsRepository,
    private val scheduler: BackupScheduler,
    private val gate: BackupOperationGate,
    private val fingerprint: BackupSnapshotFingerprint,
    private val archives: BackupArchiveStore,
    private val historyRepository: BackupHistoryRepository,
) : ViewModel() {
    private val state = MutableStateFlow(DriveBackupUiState(
        settings = preferences.settings.value,
        history = historyFor(preferences.settings.value.accountEmail),
        accountEmail = preferences.settings.value.takeUnless { it.needsAuthorization }?.accountEmail,
    ))
    val uiState = state.asStateFlow()
    private val channel = Channel<DriveBackupEffect>(Channel.UNLIMITED)
    val effects = channel.receiveAsFlow()
    private var generation = 0L
    private var job: Job? = null
    private var pending: Action? = null
    private var lastAction: Action = Action.Connect
    private var silentAuthorization = false
    private var refreshedOnOpen = false
    private val disconnectOwner = Any()
    private var disconnectLocked = false

    private sealed interface Action {
        data object Connect : Action
        data object Backup : Action
        data class Restore(val entry: DriveBackupEntry) : Action
        data class DeleteHistory(val email: String, val entry: BackupHistoryEntry) : Action
    }

    init {
        viewModelScope.launch {
            documents.observeDocuments().catch {
                state.update { it.copy(localDocumentCount = null, problem = DriveBackupProblem.LOCAL_STORAGE, backupUnchanged = false) }
            }.collect { list ->
                state.update { it.copy(localDocumentCount = list.size, localSizeBytes = list.sumOf { document -> document.sizeBytes }, backupUnchanged = false) }
            }
        }
        viewModelScope.launch {
            combine(preferences.settings, historyRepository.entries, archives.currentBackups) { settings, history, current ->
                val account = settings.accountEmail?.trim()?.lowercase(Locale.ROOT)
                Triple(settings, history[account].orEmpty(), current[account])
            }.collect { (settings, history, current) ->
                state.update { it.copy(
                    settings = settings,
                    accountEmail = settings.takeUnless { it.needsAuthorization }?.accountEmail,
                    currentBackup = current.takeUnless { settings.needsAuthorization },
                    history = history,
                ) }
            }
        }
        viewModelScope.launch {
            gate.automaticRunning.collect { running -> state.update { it.copy(automaticRunning = running, backupUnchanged = it.backupUnchanged && !running) } }
        }
    }

    fun connect() = request(Action.Connect)

    fun refreshIfConnected() {
        if (refreshedOnOpen) return
        refreshedOnOpen = true
        if (state.value.accountEmail != null && !state.value.settings.needsAuthorization) {
            request(Action.Connect, silent = true)
        }
    }

    fun retry() {
        val action = lastAction
        if (action is Action.DeleteHistory) {
            if (preferences.settings.value.accountEmail.equals(action.email, ignoreCase = true)) {
                deleteHistory(action.entry)
            }
        } else if (state.value.accountEmail == null || state.value.settings.needsAuthorization) connect()
        else when (action) {
            Action.Connect -> connect()
            Action.Backup -> backup()
            is Action.Restore -> restore(action.entry)
        }
    }

    fun backup() {
        if (state.value.accountEmail == null || (state.value.localDocumentCount ?: 0) == 0) return
        request(Action.Backup)
    }

    fun restore(entry: DriveBackupEntry) {
        if (state.value.accountEmail == null || entry != state.value.currentBackup) return
        request(Action.Restore(entry))
    }

    fun deleteHistory(entry: BackupHistoryEntry) {
        val email = preferences.settings.value.accountEmail ?: return
        if (state.value.isBusy || state.value.isSavingSettings || gate.automaticRunning.value ||
            entry !in historyFor(email)) return
        lastAction = Action.DeleteHistory(email, entry)
        val id = ++generation
        state.update { it.copy(
            phase = DriveBackupPhase.DELETING, problem = null, progress = null,
            backedUpCount = null, backupUnchanged = false, restoredCount = null, deletedBackup = false,
        ) }
        job = viewModelScope.launch {
            gate.mutex.withLock {
                try {
                    withContext(NonCancellable) {
                        historyRepository.delete(email, entry.id)
                        lastAction = Action.Connect
                        state.update { it.copy(history = historyFor(email), deletedBackup = true) }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    state.update { it.copy(problem = DriveBackupProblem.LOCAL_STORAGE) }
                }
            }
        }
        job?.invokeOnCompletion {
            if (id == generation) state.update { it.copy(phase = DriveBackupPhase.IDLE) }
        }
    }

    private fun request(action: Action, silent: Boolean = false) {
        if (state.value.isBusy || state.value.isSavingSettings || gate.automaticRunning.value) return
        pending = action
        lastAction = action
        silentAuthorization = silent
        val id = ++generation
        state.update { it.copy(phase = DriveBackupPhase.AUTHORIZING, problem = null, progress = null, backedUpCount = null, backupUnchanged = false, restoredCount = null, deletedBackup = false) }
        channel.trySend(DriveBackupEffect.Authorize(id))
    }

    fun awaitingAuthorizationRequestId(): Long? = generation.takeIf { isAwaitingAuthorization(it) }

    fun authorizationAccount(requestId: Long): String? =
        preferences.settings.value.accountEmail.takeIf { isAwaitingAuthorization(requestId) }

    fun authorizationMayShowUi(requestId: Long): Boolean =
        isAwaitingAuthorization(requestId) && !silentAuthorization

    fun isAwaitingAuthorization(requestId: Long): Boolean =
        requestId == generation && pending != null && state.value.phase == DriveBackupPhase.AUTHORIZING

    fun authorizationFailed(requestId: Long, problem: DriveBackupProblem? = null) {
        if (!isAwaitingAuthorization(requestId)) return
        pending = null
        state.update { it.copy(phase = DriveBackupPhase.IDLE, problem = problem, progress = null, backupUnchanged = false) }
        if (problem == DriveBackupProblem.AUTHORIZATION || problem == DriveBackupProblem.PERMISSION) {
            viewModelScope.launch {
                runCatching { preferences.update { it.copy(needsAuthorization = it.accountEmail != null) } }
            }
        }
    }

    fun authorized(requestId: Long, token: String) {
        if (!isAwaitingAuthorization(requestId)) return
        if (token.isBlank()) { authorizationFailed(requestId, DriveBackupProblem.AUTHORIZATION); return }
        val action = pending ?: return
        pending = null
        val expectedAccount = preferences.settings.value.accountEmail
        state.update { it.copy(phase = DriveBackupPhase.CONNECTING) }
        job = viewModelScope.launch {
            gate.mutex.withLock {
                var temporary: File? = null
                try {
                    val email = remote.accountEmail(token)?.takeIf { it.isNotBlank() }
                        ?: throw DriveBackupException.Remote()
                    currentCoroutineContext().ensureActive()
                    if (expectedAccount != null && !expectedAccount.equals(email, ignoreCase = true)) {
                        scheduler.cancel()
                        preferences.update { it.withoutAccount() }
                        state.update { it.copy(accountEmail = null, currentBackup = null, history = emptyList(),
                            hasLoadedBackups = false, problem = DriveBackupProblem.ACCOUNT_CHANGED) }
                        return@withLock
                    }
                    preferences.update { it.copy(accountEmail = email, needsAuthorization = false) }
                    state.update { it.copy(accountEmail = email, settings = preferences.settings.value, history = historyFor(email)) }
                    when (action) {
                        Action.Connect -> {
                            val current = archives.synchronize(token, email)
                            currentCoroutineContext().ensureActive()
                            preferences.update { it.copy(lastAutomaticProblem = null) }
                            scheduler.schedule(preferences.settings.value)
                            state.update { it.copy(currentBackup = current, history = historyFor(email), hasLoadedBackups = true) }
                        }
                        Action.Backup -> {
                            val current = archives.synchronize(token, email)
                            currentCoroutineContext().ensureActive()
                            state.update { it.copy(currentBackup = current, history = historyFor(email), hasLoadedBackups = true) }
                            state.update { it.copy(phase = DriveBackupPhase.PREPARING) }
                            val snapshot = fingerprint.current() ?: return@withLock
                            val saved = preferences.settings.value
                            if (current != null && matchesBackupSnapshot(snapshot, current, saved, fingerprint, local, remote, token)) {
                                preferences.update { settings ->
                                    if (settings.accountEmail.equals(email, ignoreCase = true)) settings.copy(
                                        lastBackupId = current.id,
                                        lastBackupAt = current.modifiedTimeMillis,
                                        lastBackupBytes = current.sizeBytes,
                                        lastSnapshotFingerprint = snapshot,
                                        lastAutomaticProblem = null,
                                    ) else settings
                                }
                                lastAction = Action.Connect
                                state.update { it.copy(backupUnchanged = true) }
                                return@withLock
                            }
                            val prepared = local.createBackup {
                                updateProgress(it.completedDocuments.toLong(), it.totalDocuments.toLong())
                            }
                            temporary = prepared.file
                            check(snapshot == fingerprint.current()) { "Os PDFs mudaram durante a preparação do backup." }
                            currentCoroutineContext().ensureActive()
                            state.update { it.copy(phase = DriveBackupPhase.UPLOADING, progress = 0f) }
                            val name = "Scaniqo-" + SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.ROOT).format(Date()) + ".zip"
                            archives.upload(
                                token, email, prepared.file, name, prepared.documentCount, ::updateProgress,
                                onUploaded = { uploaded ->
                                    lastAction = Action.Connect
                                    preferences.update { current ->
                                        if (current.accountEmail.equals(email, ignoreCase = true)) current.copy(
                                            lastBackupId = uploaded.id,
                                            lastBackupAt = uploaded.modifiedTimeMillis,
                                            lastBackupBytes = uploaded.sizeBytes,
                                            lastBackupDocumentCount = prepared.documentCount,
                                            lastSnapshotFingerprint = snapshot,
                                            lastAutomaticProblem = null,
                                        ) else current
                                    }
                                    if (preferences.settings.value.accountEmail.equals(email, ignoreCase = true)) {
                                        state.update { it.copy(
                                            currentBackup = uploaded, history = historyFor(email), hasLoadedBackups = true,
                                            backedUpCount = prepared.documentCount,
                                        ) }
                                    }
                                },
                            )
                        }
                        is Action.Restore -> {
                            val current = archives.loadCurrent(token, email)
                            currentCoroutineContext().ensureActive()
                            state.update { it.copy(currentBackup = current, history = historyFor(email), hasLoadedBackups = true) }
                            if (current != action.entry) {
                                lastAction = Action.Connect
                                throw DriveBackupException.Remote()
                            }
                            state.update { it.copy(phase = DriveBackupPhase.DOWNLOADING, progress = 0f) }
                            val file = local.createRestoreFile()
                            temporary = file
                            remote.download(token, action.entry, file, ::updateProgress)
                            currentCoroutineContext().ensureActive()
                            state.update { it.copy(phase = DriveBackupPhase.RESTORING, progress = null) }
                            val restored = local.restoreBackup(file) {
                                updateProgress(it.completedDocuments.toLong(), it.totalDocuments.toLong())
                            }
                            // A transação local já foi concluída. Não converter sucesso em cancelamento.
                            state.update { it.copy(restoredCount = restored.documentCount) }
                        }
                        is Action.DeleteHistory -> Unit
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    currentCoroutineContext().ensureActive()
                    val problem = error.toProblem()
                    if (problem == DriveBackupProblem.AUTHORIZATION) {
                        channel.trySend(DriveBackupEffect.ClearToken(token))
                        withContext(NonCancellable) {
                            runCatching { preferences.update { it.copy(needsAuthorization = it.accountEmail != null) } }
                        }
                        state.update { it.copy(accountEmail = null, currentBackup = null, hasLoadedBackups = false) }
                    }
                    state.update { it.copy(problem = problem, backupUnchanged = false) }
                } finally {
                    temporary?.let { file -> withContext(NonCancellable) { runCatching { local.discard(file) } } }
                }
            }
        }
        // Também executa se o job for cancelado antes de começar seu corpo.
        job?.invokeOnCompletion {
            if (requestId == generation) state.update { it.copy(phase = DriveBackupPhase.IDLE, progress = null) }
        }
    }

    fun setAutomaticEnabled(enabled: Boolean) {
        if (enabled && (state.value.accountEmail == null || state.value.settings.needsAuthorization)) {
            state.update { it.copy(problem = DriveBackupProblem.AUTHORIZATION, backupUnchanged = false) }
            return
        }
        updateSettings { it.copy(automaticEnabled = enabled, lastAutomaticProblem = null) }
    }

    fun setFrequency(frequency: BackupFrequency) = updateSettings { it.copy(frequency = frequency) }
    fun setWifiOnly(enabled: Boolean) = updateSettings { it.copy(wifiOnly = enabled) }
    fun setChargingOnly(enabled: Boolean) = updateSettings { it.copy(chargingOnly = enabled) }

    private fun updateSettings(transform: (BackupSettings) -> BackupSettings) {
        if (state.value.isBusy || state.value.isSavingSettings) return
        // Uma falha nas preferências não deve repetir uma exclusão anterior.
        if (lastAction is Action.DeleteHistory) lastAction = Action.Connect
        state.update { it.copy(isSavingSettings = true, problem = null, backupUnchanged = false) }
        viewModelScope.launch {
            try {
                preferences.update(transform)
                scheduler.schedule(preferences.settings.value)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                state.update { it.copy(problem = DriveBackupProblem.LOCAL_STORAGE) }
            } finally {
                state.update { it.copy(isSavingSettings = false) }
            }
        }
    }

    fun disconnect() {
        val email = preferences.settings.value.accountEmail ?: return
        if (state.value.isBusy || state.value.isSavingSettings || gate.automaticRunning.value ||
            !gate.mutex.tryLock(disconnectOwner)) return
        disconnectLocked = true
        val id = ++generation
        state.update { it.copy(phase = DriveBackupPhase.DISCONNECTING, problem = null, backedUpCount = null, backupUnchanged = false, restoredCount = null, deletedBackup = false) }
        job = viewModelScope.launch {
            try {
                preferences.update { it.copy(automaticEnabled = false) }
                scheduler.cancel()
                channel.trySend(DriveBackupEffect.Revoke(id, email))
            } catch (cancelled: CancellationException) {
                releaseDisconnectLock()
                throw cancelled
            } catch (_: Exception) {
                releaseDisconnectLock()
                state.update { it.copy(phase = DriveBackupPhase.IDLE, problem = DriveBackupProblem.LOCAL_STORAGE) }
            }
        }
    }

    fun disconnected(requestId: Long, success: Boolean) {
        if (requestId != generation || state.value.phase != DriveBackupPhase.DISCONNECTING) return
        if (!success) {
            releaseDisconnectLock()
            state.update { it.copy(phase = DriveBackupPhase.IDLE, problem = DriveBackupProblem.AUTHORIZATION) }
            return
        }
        job = viewModelScope.launch {
            try {
                preferences.update { it.withoutAccount() }
                scheduler.cancel()
                state.update { DriveBackupUiState(
                    localDocumentCount = it.localDocumentCount, localSizeBytes = it.localSizeBytes,
                    settings = preferences.settings.value,
                ) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                state.update { it.copy(phase = DriveBackupPhase.IDLE, problem = DriveBackupProblem.LOCAL_STORAGE) }
            } finally {
                releaseDisconnectLock()
            }
        }
    }

    fun cancel() {
        // A gravação do histórico e a revogação precisam terminar antes de liberar outras ações.
        if (state.value.phase == DriveBackupPhase.DELETING ||
            state.value.phase == DriveBackupPhase.DISCONNECTING ||
            state.value.phase == DriveBackupPhase.CANCELLING) return
        if (lastAction is Action.DeleteHistory) lastAction = Action.Connect
        pending = null
        val running = job
        if (running?.isActive == true) {
            state.update { it.copy(phase = DriveBackupPhase.CANCELLING) }
            running.cancel()
        } else {
            generation++
            state.update { it.copy(phase = DriveBackupPhase.IDLE, progress = null) }
        }
    }

    override fun onCleared() {
        releaseDisconnectLock()
        super.onCleared()
    }

    private fun releaseDisconnectLock() {
        if (disconnectLocked) {
            disconnectLocked = false
            gate.mutex.unlock(disconnectOwner)
        }
    }

    private fun historyFor(email: String?): List<BackupHistoryEntry> =
        historyRepository.entries.value[email?.trim()?.lowercase(Locale.ROOT)].orEmpty()

    private fun updateProgress(done: Long, total: Long) {
        state.update { it.copy(progress = if (total > 0) (done.toDouble() / total).toFloat().coerceIn(0f, 1f) else null) }
    }
}

private fun BackupSettings.withoutAccount() = copy(
    accountEmail = null, automaticEnabled = false, needsAuthorization = false,
    lastBackupId = null, lastBackupAt = null, lastBackupBytes = 0, lastBackupDocumentCount = 0,
    lastSnapshotFingerprint = null, lastAutomaticProblem = null,
)

private fun Exception.toProblem(): DriveBackupProblem = when (this) {
    is DriveBackupException.Unauthorized -> DriveBackupProblem.AUTHORIZATION
    is DriveBackupException.Configuration -> DriveBackupProblem.CONFIGURATION
    is DriveBackupException.ApiDisabled -> DriveBackupProblem.API_DISABLED
    is DriveBackupException.AccessBlocked -> DriveBackupProblem.ACCESS_BLOCKED
    is DriveBackupException.Quota -> DriveBackupProblem.QUOTA
    is DriveBackupException.Network -> DriveBackupProblem.NETWORK
    is DriveBackupException -> DriveBackupProblem.REMOTE
    is IOException, is IllegalArgumentException, is IllegalStateException -> DriveBackupProblem.LOCAL_STORAGE
    else -> DriveBackupProblem.REMOTE
}
