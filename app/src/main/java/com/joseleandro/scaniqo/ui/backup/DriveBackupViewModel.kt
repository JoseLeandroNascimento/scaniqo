package com.joseleandro.scaniqo.ui.backup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joseleandro.scaniqo.domain.model.DriveBackupEntry
import com.joseleandro.scaniqo.domain.model.DriveBackupException
import com.joseleandro.scaniqo.domain.repository.DocumentBackupRepository
import com.joseleandro.scaniqo.domain.repository.DocumentRepository
import com.joseleandro.scaniqo.domain.repository.DriveBackupRemote
import com.joseleandro.scaniqo.ui.backup.effect.DriveBackupEffect
import com.joseleandro.scaniqo.ui.backup.state.DriveBackupPhase
import com.joseleandro.scaniqo.ui.backup.state.DriveBackupProblem
import com.joseleandro.scaniqo.ui.backup.state.DriveBackupUiState
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
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DriveBackupViewModel(
    private val local: DocumentBackupRepository,
    private val remote: DriveBackupRemote,
    documents: DocumentRepository,
) : ViewModel() {
    private val state = MutableStateFlow(DriveBackupUiState())
    val uiState = state.asStateFlow()
    private val channel = Channel<DriveBackupEffect>(Channel.UNLIMITED)
    val effects = channel.receiveAsFlow()
    private var generation = 0L
    private var job: Job? = null
    private var pending: Action? = null

    private sealed interface Action {
        data object Connect : Action
        data object Backup : Action
        data class Restore(val entry: DriveBackupEntry) : Action
    }

    init {
        viewModelScope.launch {
            documents.observeDocuments().catch {
                state.update { it.copy(localDocumentCount = null, problem = DriveBackupProblem.LOCAL_STORAGE) }
            }.collect { list -> state.update { it.copy(localDocumentCount = list.size) } }
        }
    }

    fun connect() = request(Action.Connect)

    fun backup() {
        if (state.value.accountEmail == null || (state.value.localDocumentCount ?: 0) == 0) return
        request(Action.Backup)
    }

    fun restore(entry: DriveBackupEntry) {
        if (state.value.accountEmail == null || entry !in state.value.backups) return
        request(Action.Restore(entry))
    }

    private fun request(action: Action) {
        if (state.value.isBusy) return
        pending = action
        val id = ++generation
        state.update { it.copy(phase = DriveBackupPhase.AUTHORIZING, problem = null, progress = null, backedUpCount = null, restoredCount = null) }
        channel.trySend(DriveBackupEffect.Authorize(id))
    }

    fun awaitingAuthorizationRequestId(): Long? = generation.takeIf { isAwaitingAuthorization(it) }

    fun isAwaitingAuthorization(requestId: Long): Boolean =
        requestId == generation && pending != null && state.value.phase == DriveBackupPhase.AUTHORIZING

    fun authorizationFailed(requestId: Long, problem: DriveBackupProblem? = null) {
        if (!isAwaitingAuthorization(requestId)) return
        pending = null
        state.update { it.copy(phase = DriveBackupPhase.IDLE, problem = problem, progress = null) }
    }

    fun authorized(requestId: Long, token: String) {
        if (!isAwaitingAuthorization(requestId)) return
        if (token.isBlank()) { authorizationFailed(requestId, DriveBackupProblem.AUTHORIZATION); return }
        val action = pending ?: return
        pending = null
        val expectedAccount = state.value.accountEmail
        state.update { it.copy(phase = DriveBackupPhase.CONNECTING) }
        job = viewModelScope.launch {
            var temporary: File? = null
            try {
                val email = remote.accountEmail(token)?.takeIf { it.isNotBlank() }
                    ?: throw DriveBackupException.Remote()
                currentCoroutineContext().ensureActive()
                // Uma conta escolhida diferente nunca recebe um envio/restauração já confirmado.
                if (expectedAccount != null && !expectedAccount.equals(email, ignoreCase = true)) {
                    state.update { it.copy(accountEmail = null, backups = emptyList(), problem = DriveBackupProblem.ACCOUNT_CHANGED) }
                    return@launch
                }
                state.update { it.copy(accountEmail = email) }
                when (action) {
                    Action.Connect -> {
                        val backups = remote.listBackups(token)
                        currentCoroutineContext().ensureActive()
                        state.update { it.copy(backups = backups) }
                    }
                    Action.Backup -> {
                        state.update { it.copy(phase = DriveBackupPhase.PREPARING) }
                        val prepared = local.createBackup { updateProgress(it.completedDocuments.toLong(), it.totalDocuments.toLong()) }
                        temporary = prepared.file
                        currentCoroutineContext().ensureActive()
                        state.update { it.copy(phase = DriveBackupPhase.UPLOADING, progress = 0f) }
                        val name = "Scaniqo-" + SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.ROOT).format(Date()) + ".zip"
                        val uploaded = remote.upload(token, prepared.file, name, ::updateProgress)
                        currentCoroutineContext().ensureActive()
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
                val problem = when (error) {
                    is DriveBackupException.Unauthorized -> DriveBackupProblem.AUTHORIZATION
                    is DriveBackupException.Quota -> DriveBackupProblem.QUOTA
                    is DriveBackupException.Network -> DriveBackupProblem.NETWORK
                    is DriveBackupException -> DriveBackupProblem.REMOTE
                    is IOException, is IllegalArgumentException, is IllegalStateException -> DriveBackupProblem.LOCAL_STORAGE
                    else -> DriveBackupProblem.REMOTE
                }
                if (problem == DriveBackupProblem.AUTHORIZATION) {
                    channel.trySend(DriveBackupEffect.ClearToken(token))
                    state.update { it.copy(accountEmail = null, backups = emptyList()) }
                }
                state.update { it.copy(problem = problem) }
            } finally {
                temporary?.let { file -> withContext(NonCancellable) { runCatching { local.discard(file) } } }
            }
        }
        // Também executa se o job for cancelado antes de começar seu corpo.
        job?.invokeOnCompletion {
            if (requestId == generation) state.update { it.copy(phase = DriveBackupPhase.IDLE, progress = null) }
        }
    }

    fun disconnect() {
        val email = state.value.accountEmail ?: return
        if (state.value.isBusy) return
        val id = ++generation
        state.update { it.copy(phase = DriveBackupPhase.DISCONNECTING, problem = null, backedUpCount = null, restoredCount = null) }
        channel.trySend(DriveBackupEffect.Revoke(id, email))
    }

    fun disconnected(requestId: Long, success: Boolean) {
        if (requestId != generation || state.value.phase != DriveBackupPhase.DISCONNECTING) return
        state.update {
            if (success) DriveBackupUiState(localDocumentCount = it.localDocumentCount)
            else it.copy(phase = DriveBackupPhase.IDLE, problem = DriveBackupProblem.AUTHORIZATION)
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

    private fun updateProgress(done: Long, total: Long) {
        state.update { it.copy(progress = if (total > 0) (done.toDouble() / total).toFloat().coerceIn(0f, 1f) else null) }
    }
}
