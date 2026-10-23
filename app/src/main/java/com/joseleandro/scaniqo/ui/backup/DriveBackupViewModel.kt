package com.joseleandro.scaniqo.ui.backup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joseleandro.scaniqo.data.backup.BackupOperationGate
import com.joseleandro.scaniqo.data.backup.BackupSnapshotFingerprint
import com.joseleandro.scaniqo.domain.model.BackupFrequency
import com.joseleandro.scaniqo.domain.model.BackupSettings
import com.joseleandro.scaniqo.domain.model.DriveBackupEntry
import com.joseleandro.scaniqo.domain.model.DriveBackupException
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
) : ViewModel() {
    private val state = MutableStateFlow(DriveBackupUiState(
        settings = preferences.settings.value,
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
    }

    init {
        viewModelScope.launch {
            documents.observeDocuments().catch {
                state.update { it.copy(localDocumentCount = null, problem = DriveBackupProblem.LOCAL_STORAGE) }
            }.collect { list ->
                state.update { it.copy(localDocumentCount = list.size, localSizeBytes = list.sumOf { document -> document.sizeBytes }) }
            }
        }
        viewModelScope.launch {
            preferences.settings.collect { settings ->
                state.update { it.copy(
                    settings = settings,
                    accountEmail = settings.takeUnless { value -> value.needsAuthorization }?.accountEmail,
                ) }
            }
        }
        viewModelScope.launch {
            gate.automaticRunning.collect { running -> state.update { it.copy(automaticRunning = running) } }
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
        if (state.value.accountEmail == null || state.value.settings.needsAuthorization) connect()
        else when (val action = lastAction) {
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
        if (state.value.accountEmail == null || entry !in state.value.backups) return
        request(Action.Restore(entry))
    }

    private fun request(action: Action, silent: Boolean = false) {
        if (state.value.isBusy || state.value.isSavingSettings || gate.automaticRunning.value) return
        pending = action
        lastAction = action
        silentAuthorization = silent
        val id = ++generation
        state.update { it.copy(phase = DriveBackupPhase.AUTHORIZING, problem = null, progress = null, backedUpCount = null, restoredCount = null) }
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
        state.update { it.copy(phase = DriveBackupPhase.IDLE, problem = problem, progress = null) }
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
                        state.update { it.copy(accountEmail = null, backups = emptyList(),
                            hasLoadedBackups = false, problem = DriveBackupProblem.ACCOUNT_CHANGED) }
                        return@withLock
                    }
                    preferences.update { it.copy(accountEmail = email, needsAuthorization = false) }
                    state.update { it.copy(accountEmail = email) }
                    when (action) {
                        Action.Connect -> {
                            val backups = remote.listBackups(token)
                            currentCoroutineContext().ensureActive()
                            preferences.update { it.copy(lastAutomaticProblem = null) }
                            scheduler.schedule(preferences.settings.value)
                            state.update { it.copy(backups = backups, hasLoadedBackups = true) }
                        }
                        Action.Backup -> {
                            state.update { it.copy(phase = DriveBackupPhase.PREPARING) }
                            val snapshot = fingerprint.current()
                            val prepared = local.createBackup {
                                updateProgress(it.completedDocuments.toLong(), it.totalDocuments.toLong())
                            }
                            temporary = prepared.file
                            currentCoroutineContext().ensureActive()
                            state.update { it.copy(phase = DriveBackupPhase.UPLOADING, progress = 0f) }
                            val name = "Scaniqo-" + SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.ROOT).format(Date()) + ".zip"
                            val uploaded = remote.upload(token, prepared.file, name, ::updateProgress)
                            currentCoroutineContext().ensureActive()
                            val unchangedSnapshot = snapshot?.takeIf { it == fingerprint.current() }
                            withContext(NonCancellable) {
                                preferences.update { it.copy(
                                    lastBackupAt = System.currentTimeMillis(),
                                    lastBackupBytes = uploaded.sizeBytes,
                                    lastBackupDocumentCount = prepared.documentCount,
                                    lastSnapshotFingerprint = unchangedSnapshot,
                                    lastAutomaticProblem = null,
                                ) }
                            }
                            state.update { it.copy(
                                backups = (listOf(uploaded) + it.backups).distinctBy { entry -> entry.id },
                                backedUpCount = prepared.documentCount,
                            ) }
                        }
                        is Action.Restore -> {
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
                        state.update { it.copy(accountEmail = null, backups = emptyList(), hasLoadedBackups = false) }
                    }
                    state.update { it.copy(problem = problem) }
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
            state.update { it.copy(problem = DriveBackupProblem.AUTHORIZATION) }
            return
        }
        updateSettings { it.copy(automaticEnabled = enabled, lastAutomaticProblem = null) }
    }

    fun setFrequency(frequency: BackupFrequency) = updateSettings { it.copy(frequency = frequency) }
    fun setWifiOnly(enabled: Boolean) = updateSettings { it.copy(wifiOnly = enabled) }
    fun setChargingOnly(enabled: Boolean) = updateSettings { it.copy(chargingOnly = enabled) }

    private fun updateSettings(transform: (BackupSettings) -> BackupSettings) {
        if (state.value.isBusy || state.value.isSavingSettings) return
        state.update { it.copy(isSavingSettings = true, problem = null) }
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
        state.update { it.copy(phase = DriveBackupPhase.DISCONNECTING, problem = null, backedUpCount = null, restoredCount = null) }
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
        // A revogação pertence ao Google e não pode ser desfeita por um cancelamento local.
        if (state.value.phase == DriveBackupPhase.DISCONNECTING || state.value.phase == DriveBackupPhase.CANCELLING) return
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

    private fun updateProgress(done: Long, total: Long) {
        state.update { it.copy(progress = if (total > 0) (done.toDouble() / total).toFloat().coerceIn(0f, 1f) else null) }
    }
}

private fun BackupSettings.withoutAccount() = copy(
    accountEmail = null, automaticEnabled = false, needsAuthorization = false,
    lastBackupAt = null, lastBackupBytes = 0, lastBackupDocumentCount = 0,
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
