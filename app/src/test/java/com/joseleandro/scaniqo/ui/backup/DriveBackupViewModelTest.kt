package com.joseleandro.scaniqo.ui.backup

import androidx.lifecycle.ViewModelStore
import com.joseleandro.scaniqo.data.backup.BackupArchiveStore
import com.joseleandro.scaniqo.data.backup.BackupOperationGate
import com.joseleandro.scaniqo.data.backup.BackupSnapshotFingerprint
import com.joseleandro.scaniqo.data.backup.BackupDocument
import com.joseleandro.scaniqo.data.backup.BackupDocumentSource
import com.joseleandro.scaniqo.data.backup.DocumentBackupArchive
import com.joseleandro.scaniqo.domain.model.BackupHistoryEntry
import com.joseleandro.scaniqo.domain.model.BackupSettings
import com.joseleandro.scaniqo.domain.model.BackupFrequency
import com.joseleandro.scaniqo.domain.repository.BackupHistoryRepository
import com.joseleandro.scaniqo.domain.repository.BackupSettingsRepository
import com.joseleandro.scaniqo.domain.repository.BackupScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import com.joseleandro.scaniqo.domain.model.DocumentBackupProgress
import com.joseleandro.scaniqo.domain.model.DocumentBackupRestoreResult
import com.joseleandro.scaniqo.domain.model.DocumentModel
import com.joseleandro.scaniqo.domain.model.DriveBackupEntry
import com.joseleandro.scaniqo.domain.model.DriveBackupException
import com.joseleandro.scaniqo.domain.model.PreparedDocumentBackup
import com.joseleandro.scaniqo.domain.repository.DocumentBackupRepository
import com.joseleandro.scaniqo.domain.repository.DriveBackupRemote
import com.joseleandro.scaniqo.ui.backup.effect.DriveBackupEffect
import com.joseleandro.scaniqo.ui.backup.state.DriveBackupPhase
import com.joseleandro.scaniqo.ui.backup.state.DriveBackupProblem
import com.joseleandro.scaniqo.ui.documentCommon.FakeDocumentRepository
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DriveBackupViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val documents = FakeDocumentRepository()
    private val local = FakeLocalBackup()
    private val remote = FakeDrive()
    private val effects = mutableListOf<DriveBackupEffect>()
    @get:Rule val temporary = TemporaryFolder()
    private val preferences = FakeBackupSettings()
    private val scheduler = FakeBackupScheduler()
    private val gate = BackupOperationGate()
    private val history = FakeHistory()
    private val archives = BackupArchiveStore(remote, history)
    private val viewModel by lazy {
        DriveBackupViewModel(local, remote, documents, preferences, scheduler, gate,
            BackupSnapshotFingerprint(documents, dispatcher), archives, history).also { store.put("backup", it) }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        history.deleteGate?.complete(Unit)
        local.release()
        remote.release()
        store.clear()
        dispatcher.scheduler.runCurrent()
        Dispatchers.resetMain()
    }

    @Test
    fun pendingAuthorizationCanBeResumedBeforeOrAfterItsEffectIsConsumed() = runTest(dispatcher) {
        viewModel.connect()
        val id = requireNotNull(viewModel.awaitingAuthorizationRequestId())
        // A composição pode ser recriada antes de receber o efeito ou enquanto a Task do Google responde.
        assertTrue(viewModel.isAwaitingAuthorization(id))
        start()
        assertEquals(id, authorizationId())
        assertEquals(id, viewModel.awaitingAuthorizationRequestId())
        viewModel.authorized(id, "token")
        assertNull(viewModel.awaitingAuthorizationRequestId())
        runCurrent()

        viewModel.connect()
        assertTrue(requireNotNull(viewModel.awaitingAuthorizationRequestId()) > id)
        viewModel.cancel()
        assertNull(viewModel.awaitingAuthorizationRequestId())
    }

    @Test
    fun cancelledAuthorizationIgnoresItsLateResult() = runTest(dispatcher) {
        start()
        viewModel.connect()
        val id = authorizationId()
        viewModel.authorizationFailed(id)
        viewModel.authorized(id, "late-token")
        runCurrent()

        assertEquals(DriveBackupPhase.IDLE, viewModel.uiState.value.phase)
        assertFalse(viewModel.isAwaitingAuthorization(id))
        assertNull(viewModel.uiState.value.accountEmail)
        assertNull(viewModel.uiState.value.problem)
        assertTrue(remote.accountTokens.isEmpty())
    }

    @Test
    fun cancellingThenStartingAgainAcceptsOnlyTheNewAuthorization() = runTest(dispatcher) {
        start()
        viewModel.connect()
        val first = authorizationId()
        viewModel.cancel()
        viewModel.connect()
        val second = authorizationId()
        assertTrue(second > first)

        viewModel.authorized(first, "old-token")
        viewModel.authorizationFailed(first, DriveBackupProblem.PERMISSION)
        assertTrue(viewModel.isAwaitingAuthorization(second))
        viewModel.authorized(second, "current-token")
        runCurrent()

        assertEquals(listOf("current-token"), remote.accountTokens)
        assertEquals(remote.email, viewModel.uiState.value.accountEmail)
        assertNull(viewModel.uiState.value.problem)
    }

    @Test
    fun duplicateAuthorizationDoesNotRepeatTheRemoteOperation() = runTest(dispatcher) {
        start()
        viewModel.connect()
        val id = authorizationId()
        viewModel.authorized(id, "token")
        viewModel.authorized(id, "duplicate")
        runCurrent()
        viewModel.authorized(id, "after-completion")
        runCurrent()

        assertEquals(listOf("token"), remote.accountTokens)
        assertEquals(listOf("token"), remote.listTokens)
        assertEquals(DriveBackupPhase.IDLE, viewModel.uiState.value.phase)
    }

    @Test
    fun emptyTokenReturnsAnAuthorizationErrorWithoutContactingDrive() = runTest(dispatcher) {
        start()
        viewModel.connect()
        viewModel.authorized(authorizationId(), "  ")
        runCurrent()

        assertEquals(DriveBackupProblem.AUTHORIZATION, viewModel.uiState.value.problem)
        assertFalse(viewModel.uiState.value.isBusy)
        assertTrue(remote.accountTokens.isEmpty())
    }

    @Test
    fun concurrentRequestsCannotReplaceThePendingAuthorization() = runTest(dispatcher) {
        start()
        connectAccount()
        viewModel.backup()
        val id = authorizationId()
        val effectCount = effects.size
        viewModel.connect()
        viewModel.backup()
        viewModel.restore(remote.existing)
        viewModel.deleteHistory(remote.existing.historyEntry())
        viewModel.disconnect()

        assertEquals(effectCount, effects.size)
        assertTrue(viewModel.isAwaitingAuthorization(id))
        viewModel.authorized(id, "backup-token")
        runCurrent()
        assertEquals(1, remote.uploads.size)
        assertTrue(remote.downloads.isEmpty())
    }

    @Test
    fun backupPublishesTheUploadedEntryAndDiscardsThePreparedFile() = runTest(dispatcher) {
        start()
        connectAccount()
        viewModel.backup()
        viewModel.authorized(authorizationId(), "backup-token")
        viewModel.authorized(authorizationId(), "duplicate-token")
        runCurrent()

        assertEquals(1, local.createCalls)
        assertEquals(local.prepared.file, remote.uploads.single().file)
        assertEquals("backup-token", remote.uploads.single().token)
        assertTrue(remote.uploads.single().name.startsWith("Scaniqo-"))
        assertTrue(remote.uploads.single().name.endsWith(".zip"))
        assertEquals(remote.uploaded, viewModel.uiState.value.currentBackup)
        assertEquals(listOf(remote.uploaded.id, remote.existing.id), viewModel.uiState.value.history.map { it.id })
        assertEquals(listOf(remote.uploaded), remote.listedBackups)
        assertEquals(2, viewModel.uiState.value.backedUpCount)
        assertEquals(listOf(local.prepared.file), local.discarded)
        assertNull(viewModel.uiState.value.progress)
        assertNull(viewModel.uiState.value.problem)
        assertFalse(viewModel.uiState.value.isBusy)
    }

    @Test
    fun failedUploadDiscardsTheArchiveAndPreservesTheExistingBackupList() = runTest(dispatcher) {
        start()
        connectAccount()
        remote.uploadFailure = DriveBackupException.Quota()
        viewModel.backup()
        viewModel.authorized(authorizationId(), "backup-token")
        runCurrent()

        assertEquals(listOf(local.prepared.file), local.discarded)
        assertEquals(remote.existing, viewModel.uiState.value.currentBackup)
        assertEquals(DriveBackupProblem.QUOTA, viewModel.uiState.value.problem)
        assertNull(viewModel.uiState.value.backedUpCount)
        assertFalse(viewModel.uiState.value.isBusy)
    }

    @Test
    fun failedPreparationNeverUploadsAnArchiveThatWasNotProduced() = runTest(dispatcher) {
        start()
        connectAccount()
        local.createFailure = IOException("Local storage unavailable")
        viewModel.backup()
        viewModel.authorized(authorizationId(), "backup-token")
        runCurrent()

        assertEquals(DriveBackupProblem.LOCAL_STORAGE, viewModel.uiState.value.problem)
        assertTrue(remote.uploads.isEmpty())
        assertTrue(local.discarded.isEmpty())
        assertFalse(viewModel.uiState.value.isBusy)
    }

    @Test
    fun cancellationWaitsForNonCancellableCleanupAndBlocksNewActions() = runTest(dispatcher) {
        start()
        connectAccount()
        remote.uploadGate = CompletableDeferred()
        local.discardGate = CompletableDeferred()
        viewModel.backup()
        viewModel.authorized(authorizationId(), "backup-token")
        runCurrent()
        assertEquals(DriveBackupPhase.UPLOADING, viewModel.uiState.value.phase)
        assertEquals(.4f, viewModel.uiState.value.progress!!, .001f)

        viewModel.cancel()
        runCurrent()
        assertEquals(DriveBackupPhase.CANCELLING, viewModel.uiState.value.phase)
        assertEquals(listOf(local.prepared.file), local.discarded)
        viewModel.cancel()
        assertEquals(DriveBackupPhase.CANCELLING, viewModel.uiState.value.phase)
        val effectCount = effects.size
        viewModel.connect()
        viewModel.backup()
        viewModel.restore(remote.existing)
        viewModel.deleteHistory(remote.existing.historyEntry())
        viewModel.disconnect()
        assertEquals(effectCount, effects.size)

        local.discardGate!!.complete(Unit)
        runCurrent()
        assertEquals(DriveBackupPhase.IDLE, viewModel.uiState.value.phase)
        assertNull(viewModel.uiState.value.backedUpCount)
        assertNull(viewModel.uiState.value.problem)
        assertNull(viewModel.uiState.value.progress)
    }

    @Test
    fun cancellingImmediatelyAfterAuthorizationCannotLeaveTheScreenBusy() = runTest(dispatcher) {
        start()
        viewModel.connect()
        viewModel.authorized(authorizationId(), "token")
        viewModel.cancel()
        runCurrent()

        assertEquals(DriveBackupPhase.IDLE, viewModel.uiState.value.phase)
        assertFalse(viewModel.uiState.value.isBusy)
        assertTrue(remote.accountTokens.isEmpty())
    }

    @Test
    fun aLateAccountLookupAfterCancellationCannotConnectTheAccount() = runTest(dispatcher) {
        start()
        remote.accountGate = CompletableDeferred()
        remote.ignoreAccountCancellation = true
        viewModel.connect()
        viewModel.authorized(authorizationId(), "token")
        runCurrent()
        viewModel.cancel()
        runCurrent()
        remote.accountGate!!.complete(Unit)
        runCurrent()

        assertNull(viewModel.uiState.value.accountEmail)
        assertTrue(remote.listTokens.isEmpty())
        assertEquals(DriveBackupPhase.IDLE, viewModel.uiState.value.phase)
    }

    @Test
    fun aLateBackupListAfterCancellationCannotPublishStaleEntries() = runTest(dispatcher) {
        start()
        remote.listGate = CompletableDeferred()
        remote.ignoreListCancellation = true
        viewModel.connect()
        viewModel.authorized(authorizationId(), "token")
        runCurrent()
        viewModel.cancel()
        runCurrent()
        remote.listGate!!.complete(Unit)
        runCurrent()

        assertNull(viewModel.uiState.value.currentBackup)
        assertNull(viewModel.uiState.value.problem)
        assertEquals(DriveBackupPhase.IDLE, viewModel.uiState.value.phase)
    }

    @Test
    fun confirmedUploadPersistsItsResultEvenWhenCancellationArrivesAfterRemoteCommit() = runTest(dispatcher) {
        start()
        connectAccount()
        remote.uploadGate = CompletableDeferred()
        remote.ignoreUploadCancellation = true
        viewModel.backup()
        viewModel.authorized(authorizationId(), "backup-token")
        runCurrent()
        viewModel.cancel()
        runCurrent()
        remote.uploadGate!!.complete(Unit)
        runCurrent()

        assertEquals(remote.uploaded, viewModel.uiState.value.currentBackup)
        assertEquals(2, viewModel.uiState.value.backedUpCount)
        assertEquals(listOf(local.prepared.file), local.discarded)
        assertNull(viewModel.uiState.value.problem)
        assertFalse(viewModel.uiState.value.isBusy)
    }

    @Test
    fun restoreDownloadsOnlyTheSelectedEntryAndAlwaysDiscardsItsTemporaryFile() = runTest(dispatcher) {
        start()
        connectAccount()
        viewModel.restore(remote.existing)
        viewModel.authorized(authorizationId(), "restore-token")
        runCurrent()

        assertEquals(listOf(Download("restore-token", remote.existing, local.restoreFile)), remote.downloads)
        assertEquals(listOf(local.restoreFile), local.restored)
        assertEquals(listOf(local.restoreFile), local.discarded)
        assertEquals(2, viewModel.uiState.value.restoredCount)
        assertNull(viewModel.uiState.value.problem)
        assertFalse(viewModel.uiState.value.isBusy)
    }

    @Test
    fun failedDownloadDiscardsTheTemporaryFileAndDoesNotRestorePartialData() = runTest(dispatcher) {
        start()
        connectAccount()
        remote.downloadFailure = DriveBackupException.Network()
        viewModel.restore(remote.existing)
        viewModel.authorized(authorizationId(), "restore-token")
        runCurrent()

        assertEquals(listOf(local.restoreFile), local.discarded)
        assertTrue(local.restored.isEmpty())
        assertEquals(DriveBackupProblem.NETWORK, viewModel.uiState.value.problem)
        assertNull(viewModel.uiState.value.restoredCount)
    }

    @Test
    fun invalidArchiveStillDiscardsTheDownloadedFile() = runTest(dispatcher) {
        start()
        connectAccount()
        local.restoreFailure = IllegalArgumentException("Invalid archive")
        viewModel.restore(remote.existing)
        viewModel.authorized(authorizationId(), "restore-token")
        runCurrent()

        assertEquals(listOf(local.restoreFile), local.discarded)
        assertEquals(DriveBackupProblem.LOCAL_STORAGE, viewModel.uiState.value.problem)
        assertNull(viewModel.uiState.value.restoredCount)
        assertFalse(viewModel.uiState.value.isBusy)
    }

    @Test
    fun cancellingDownloadDiscardsItsFileWithoutStartingRestore() = runTest(dispatcher) {
        start()
        connectAccount()
        remote.downloadGate = CompletableDeferred()
        viewModel.restore(remote.existing)
        viewModel.authorized(authorizationId(), "restore-token")
        runCurrent()
        assertEquals(DriveBackupPhase.DOWNLOADING, viewModel.uiState.value.phase)
        viewModel.cancel()
        runCurrent()

        assertEquals(listOf(local.restoreFile), local.discarded)
        assertTrue(local.restored.isEmpty())
        assertNull(viewModel.uiState.value.restoredCount)
        assertNull(viewModel.uiState.value.problem)
        assertFalse(viewModel.uiState.value.isBusy)
    }

    @Test
    fun cancellingRestoreBeforeCommitDiscardsItsFileWithoutReportingSuccess() = runTest(dispatcher) {
        start()
        connectAccount()
        local.restoreGate = CompletableDeferred()
        viewModel.restore(remote.existing)
        viewModel.authorized(authorizationId(), "restore-token")
        runCurrent()
        assertEquals(DriveBackupPhase.RESTORING, viewModel.uiState.value.phase)
        viewModel.cancel()
        runCurrent()

        assertEquals(listOf(local.restoreFile), local.discarded)
        assertNull(viewModel.uiState.value.restoredCount)
        assertNull(viewModel.uiState.value.problem)
        assertFalse(viewModel.uiState.value.isBusy)
    }

    @Test
    fun cancellationAfterTheLocalCommitKeepsTheSuccessfulRestoreResult() = runTest(dispatcher) {
        start()
        connectAccount()
        local.onRestoreCommitted = { viewModel.cancel() }
        viewModel.restore(remote.existing)
        viewModel.authorized(authorizationId(), "restore-token")
        runCurrent()

        assertEquals(2, viewModel.uiState.value.restoredCount)
        assertEquals(listOf(local.restoreFile), local.discarded)
        assertNull(viewModel.uiState.value.problem)
        assertFalse(viewModel.uiState.value.isBusy)
    }

    @Test
    fun switchingAccountNeverSendsTheAlreadyConfirmedBackup() = runTest(dispatcher) {
        start()
        connectAccount()
        remote.email = "different@example.com"
        viewModel.backup()
        viewModel.authorized(authorizationId(), "different-account-token")
        runCurrent()

        assertEquals(0, local.createCalls)
        assertTrue(remote.uploads.isEmpty())
        assertNull(viewModel.uiState.value.accountEmail)
        assertNull(viewModel.uiState.value.currentBackup)
        assertEquals(DriveBackupProblem.ACCOUNT_CHANGED, viewModel.uiState.value.problem)
        assertFalse(viewModel.uiState.value.isBusy)
    }

    @Test
    fun switchingAccountNeverDownloadsThePreviouslySelectedBackup() = runTest(dispatcher) {
        start()
        connectAccount()
        remote.email = "different@example.com"
        viewModel.restore(remote.existing)
        viewModel.authorized(authorizationId(), "different-account-token")
        runCurrent()

        assertEquals(0, local.createRestoreCalls)
        assertTrue(remote.downloads.isEmpty())
        assertTrue(local.restored.isEmpty())
        assertEquals(DriveBackupProblem.ACCOUNT_CHANGED, viewModel.uiState.value.problem)
    }

    @Test
    fun accountEmailCaseAloneDoesNotBlockABackup() = runTest(dispatcher) {
        start()
        connectAccount()
        remote.email = remote.email!!.uppercase()
        viewModel.backup()
        viewModel.authorized(authorizationId(), "backup-token")
        runCurrent()

        assertEquals(1, remote.uploads.size)
        assertNull(viewModel.uiState.value.problem)
    }

    @Test
    fun unauthorizedUploadClearsTheTokenAndAccountAndDiscardsTheArchive() = runTest(dispatcher) {
        start()
        connectAccount()
        remote.uploadFailure = DriveBackupException.Unauthorized()
        viewModel.backup()
        viewModel.authorized(authorizationId(), "expired-token")
        runCurrent()

        assertEquals("expired-token", effects.filterIsInstance<DriveBackupEffect.ClearToken>().single().token)
        assertNull(viewModel.uiState.value.accountEmail)
        assertNull(viewModel.uiState.value.currentBackup)
        assertEquals(DriveBackupProblem.AUTHORIZATION, viewModel.uiState.value.problem)
        assertEquals(listOf(local.prepared.file), local.discarded)
        assertFalse(viewModel.uiState.value.isBusy)
    }

    @Test
    fun successfulDisconnectClearsRemoteStateButPreservesTheLocalDocumentCount() = runTest(dispatcher) {
        start()
        connectAccount()
        viewModel.disconnect()
        runCurrent()
        val revoke = effects.filterIsInstance<DriveBackupEffect.Revoke>().single()
        assertEquals(remote.email, revoke.email)
        assertEquals(DriveBackupPhase.DISCONNECTING, viewModel.uiState.value.phase)
        viewModel.cancel()
        assertEquals(DriveBackupPhase.DISCONNECTING, viewModel.uiState.value.phase)
        viewModel.disconnected(revoke.requestId + 1, true)
        assertEquals(DriveBackupPhase.DISCONNECTING, viewModel.uiState.value.phase)

        viewModel.disconnected(revoke.requestId, true)
        runCurrent()
        assertNull(viewModel.uiState.value.accountEmail)
        assertNull(viewModel.uiState.value.currentBackup)
        assertEquals(2, viewModel.uiState.value.localDocumentCount)
        assertFalse(viewModel.uiState.value.isBusy)

        connectAccount()
        viewModel.disconnected(revoke.requestId, true)
        assertEquals(remote.email, viewModel.uiState.value.accountEmail)
    }

    @Test
    fun failedDisconnectKeepsTheAccountAvailableForRetry() = runTest(dispatcher) {
        start()
        connectAccount()
        viewModel.disconnect()
        runCurrent()
        val revoke = effects.filterIsInstance<DriveBackupEffect.Revoke>().single()
        viewModel.disconnected(revoke.requestId, false)

        assertEquals(remote.email, viewModel.uiState.value.accountEmail)
        assertEquals(remote.existing, viewModel.uiState.value.currentBackup)
        assertEquals(DriveBackupProblem.AUTHORIZATION, viewModel.uiState.value.problem)
        assertFalse(viewModel.uiState.value.isBusy)
    }

    @Test
    fun backupRequiresLocalDocumentsAndRestoreRequiresAKnownEntry() = runTest(dispatcher) {
        start(documentCount = 0)
        connectAccount()
        val count = effects.size
        viewModel.backup()
        viewModel.restore(remote.existing.copy(id = "not-listed"))
        assertEquals(count, effects.size)
        assertEquals(0, local.createCalls)
        assertTrue(remote.downloads.isEmpty())
    }

    @Test
    fun clearingTheViewModelCancelsUploadAndCleansTheTemporaryFile() = runTest(dispatcher) {
        start()
        connectAccount()
        remote.uploadGate = CompletableDeferred()
        viewModel.backup()
        viewModel.authorized(authorizationId(), "backup-token")
        runCurrent()
        store.clear()
        runCurrent()

        assertEquals(listOf(local.prepared.file), local.discarded)
        assertEquals(0, documents.activeObservers)
        assertNull(viewModel.uiState.value.backedUpCount)
    }

    @Test
    fun connectionIsRememberedAndSilentRefreshDoesNotRequestConsentUi() = runTest(dispatcher) {
        preferences.settings.value = BackupSettings(accountEmail = "owner@example.com")
        start()
        assertEquals("owner@example.com", viewModel.uiState.value.accountEmail)
        viewModel.refreshIfConnected()
        val id = authorizationId()
        assertEquals("owner@example.com", viewModel.authorizationAccount(id))
        assertFalse(viewModel.authorizationMayShowUi(id))
        viewModel.authorized(id, "refresh-token")
        runCurrent()
        assertTrue(viewModel.uiState.value.hasLoadedBackups)
        val count = effects.size
        viewModel.refreshIfConnected()
        assertEquals(count, effects.size)
    }

    @Test
    fun enablingAutomaticBackupRequiresAConnectedAccountAndPersistsOptions() = runTest(dispatcher) {
        start()
        viewModel.setAutomaticEnabled(true)
        runCurrent()
        assertFalse(preferences.settings.value.automaticEnabled)
        assertTrue(scheduler.scheduled.isEmpty())
        connectAccount()
        viewModel.setAutomaticEnabled(true)
        runCurrent()
        assertTrue(preferences.settings.value.automaticEnabled)
        assertTrue(scheduler.scheduled.last().automaticEnabled)
        viewModel.setFrequency(BackupFrequency.WEEKLY)
        runCurrent()
        viewModel.setWifiOnly(false)
        runCurrent()
        viewModel.setChargingOnly(true)
        runCurrent()
        assertEquals(BackupFrequency.WEEKLY, preferences.settings.value.frequency)
        assertFalse(preferences.settings.value.wifiOnly)
        assertTrue(preferences.settings.value.chargingOnly)
    }

    @Test
    fun automaticExecutionBlocksManualActionsButCanBeDisabled() = runTest(dispatcher) {
        start()
        connectAccount()
        preferences.settings.value = preferences.settings.value.copy(automaticEnabled = true)
        gate.automaticRunning.value = true
        runCurrent()
        val count = effects.size
        viewModel.backup()
        viewModel.restore(remote.existing)
        viewModel.deleteHistory(remote.existing.historyEntry())
        viewModel.disconnect()
        assertEquals(count, effects.size)
        viewModel.setAutomaticEnabled(false)
        runCurrent()
        assertFalse(preferences.settings.value.automaticEnabled)
        assertFalse(scheduler.scheduled.last().automaticEnabled)
    }

    @Test
    fun successfulManualBackupRecordsHistoryAndFingerprintForAutomaticDeduplication() = runTest(dispatcher) {
        start()
        connectAccount()
        viewModel.backup()
        viewModel.authorized(authorizationId(), "backup-token")
        runCurrent()
        assertTrue(requireNotNull(preferences.settings.value.lastBackupAt) > 0)
        assertEquals(remote.uploaded.id, preferences.settings.value.lastBackupId)
        assertEquals(800L, preferences.settings.value.lastBackupBytes)
        assertEquals(2, preferences.settings.value.lastBackupDocumentCount)
        assertEquals(
            BackupSnapshotFingerprint(documents, dispatcher).current(),
            preferences.settings.value.lastSnapshotFingerprint,
        )
    }

    @Test
    fun apiDisabledHasAnActionableErrorAndDoesNotClaimAnEmptyHistory() = runTest(dispatcher) {
        start()
        remote.accountFailure = DriveBackupException.ApiDisabled()
        viewModel.connect()
        viewModel.authorized(authorizationId(), "token")
        runCurrent()
        assertEquals(DriveBackupProblem.API_DISABLED, viewModel.uiState.value.problem)
        assertFalse(viewModel.uiState.value.hasLoadedBackups)
        assertNull(preferences.settings.value.accountEmail)
    }

    @Test
    fun failedSettingsWriteDoesNotEnableOrScheduleAutomaticBackup() = runTest(dispatcher) {
        start()
        connectAccount()
        val count = scheduler.scheduled.size
        preferences.failure = IOException("settings unavailable")
        viewModel.setAutomaticEnabled(true)
        runCurrent()
        assertFalse(preferences.settings.value.automaticEnabled)
        assertEquals(count, scheduler.scheduled.size)
        assertEquals(DriveBackupProblem.LOCAL_STORAGE, viewModel.uiState.value.problem)
        assertFalse(viewModel.uiState.value.isSavingSettings)
    }

    @Test
    fun disconnectDisablesAutomaticBackupAndClearsRememberedHistory() = runTest(dispatcher) {
        start()
        connectAccount()
        preferences.settings.value = preferences.settings.value.copy(
            automaticEnabled = true, lastBackupId = "previous-file", lastBackupAt = 123,
            lastSnapshotFingerprint = "snapshot",
        )
        runCurrent()
        viewModel.disconnect()
        runCurrent()
        assertFalse(preferences.settings.value.automaticEnabled)
        assertTrue(scheduler.cancellations > 0)
        val revoke = effects.filterIsInstance<DriveBackupEffect.Revoke>().single()
        viewModel.disconnected(revoke.requestId, true)
        runCurrent()
        assertNull(preferences.settings.value.accountEmail)
        assertNull(preferences.settings.value.lastBackupId)
        assertNull(preferences.settings.value.lastBackupAt)
        assertNull(preferences.settings.value.lastSnapshotFingerprint)
        assertFalse(gate.mutex.isLocked)
    }


    @Test
    fun connectPreservesLegacyMetadataAndRetainsOnlyLatestRemoteArchive() = runTest(dispatcher) {
        remote.listedBackups = listOf(remote.existing, remote.uploaded)
        start()
        connectAccount()

        assertEquals(remote.uploaded, viewModel.uiState.value.currentBackup)
        assertEquals(listOf(remote.uploaded.id, remote.existing.id), viewModel.uiState.value.history.map { it.id })
        assertEquals(listOf(remote.uploaded), remote.listedBackups)
        assertEquals(listOf(Delete("connect-token", remote.existing)), remote.deletes)
    }

    @Test
    fun restoreRejectsOldHistoryBeforeRequestingAuthorization() = runTest(dispatcher) {
        remote.listedBackups = listOf(remote.existing, remote.uploaded)
        start()
        connectAccount()
        val effectsBefore = effects.size

        viewModel.restore(remote.existing)
        runCurrent()

        assertEquals(effectsBefore, effects.size)
        assertTrue(remote.downloads.isEmpty())
        assertEquals(2, viewModel.uiState.value.history.size)
    }

    @Test
    fun restoreRechecksLatestVersionAfterAuthorizationAndRejectsStaleSelection() = runTest(dispatcher) {
        start()
        connectAccount()
        viewModel.restore(remote.existing)
        remote.listedBackups = listOf(remote.uploaded, remote.existing)
        viewModel.authorized(authorizationId(), "restore-token")
        runCurrent()

        assertEquals(remote.uploaded, viewModel.uiState.value.currentBackup)
        assertTrue(remote.downloads.isEmpty())
        assertEquals(0, local.createRestoreCalls)
        assertEquals(DriveBackupProblem.REMOTE, viewModel.uiState.value.problem)
    }

    @Test
    fun restoringCurrentBackupDoesNotDependOnDeletingInaccessibleOldArchivesOrSavingHistory() = runTest(dispatcher) {
        start()
        connectAccount()
        remote.listedBackups = listOf(remote.existing, remote.existing.copy(id = "inaccessible-old", modifiedTimeMillis = 1L))
        remote.deleteFailure = DriveBackupException.Remote(403)
        history.failure = IOException("History unavailable")

        viewModel.restore(remote.existing)
        viewModel.authorized(authorizationId(), "restore-token")
        runCurrent()

        assertEquals(remote.existing, remote.downloads.single().entry)
        assertEquals(listOf(local.restoreFile), local.restored)
        assertEquals(2, viewModel.uiState.value.restoredCount)
        assertTrue(remote.deletes.isEmpty())
        assertNull(viewModel.uiState.value.problem)
    }

    @Test
    fun historyDeletionDoesNotAuthorizeTouchDriveOrInvalidateSnapshot() = runTest(dispatcher) {
        start()
        connectAccount()
        preferences.settings.value = preferences.settings.value.copy(lastSnapshotFingerprint = "snapshot", lastBackupAt = 100L)
        runCurrent()
        val saved = preferences.settings.value
        val effectsBefore = effects.size
        val accountCalls = remote.accountTokens.size

        viewModel.deleteHistory(viewModel.uiState.value.history.single())
        runCurrent()

        assertEquals(effectsBefore, effects.size)
        assertEquals(accountCalls, remote.accountTokens.size)
        assertTrue(remote.deletes.isEmpty())
        assertTrue(viewModel.uiState.value.history.isEmpty())
        assertEquals(remote.existing, viewModel.uiState.value.currentBackup)
        assertEquals(saved, preferences.settings.value)
        assertTrue(viewModel.uiState.value.deletedBackup)
        assertTrue(local.restored.isEmpty())

        viewModel.restore(remote.existing)
        viewModel.authorized(authorizationId(), "restore-token")
        runCurrent()
        assertEquals(remote.existing, remote.downloads.single().entry)
        assertTrue(viewModel.uiState.value.history.isEmpty())
    }

    @Test
    fun localHistorySurvivesExpiredAuthorizationAndCanBeDeletedOffline() = runTest(dispatcher) {
        preferences.settings.value = BackupSettings(accountEmail = "OWNER@example.com", needsAuthorization = true)
        history.record("owner@example.com", listOf(remote.existing.historyEntry()))
        start()

        assertNull(viewModel.uiState.value.accountEmail)
        assertEquals(listOf(remote.existing.historyEntry()), viewModel.uiState.value.history)
        viewModel.deleteHistory(viewModel.uiState.value.history.single())
        runCurrent()

        assertTrue(viewModel.uiState.value.history.isEmpty())
        assertTrue(effects.isEmpty())
        assertTrue(remote.accountTokens.isEmpty())
        assertTrue(viewModel.uiState.value.deletedBackup)
    }

    @Test
    fun historyDoesNotLeakBetweenAccountsAndReconnectShowsSavedRecords() = runTest(dispatcher) {
        history.record("owner@example.com", listOf(remote.existing.historyEntry()))
        history.record("another@example.com", listOf(remote.uploaded.historyEntry()))
        preferences.settings.value = BackupSettings(accountEmail = "owner@example.com")
        start()
        assertEquals(listOf(remote.existing.id), viewModel.uiState.value.history.map { it.id })

        preferences.settings.value = BackupSettings(accountEmail = "another@example.com")
        runCurrent()
        assertEquals(listOf(remote.uploaded.id), viewModel.uiState.value.history.map { it.id })
        preferences.settings.value = BackupSettings()
        runCurrent()
        assertTrue(viewModel.uiState.value.history.isEmpty())
        assertEquals(2, history.entries.value.size)

        preferences.settings.value = BackupSettings(accountEmail = "owner@example.com")
        runCurrent()
        assertEquals(listOf(remote.existing.id), viewModel.uiState.value.history.map { it.id })
    }

    @Test
    fun accountSwitchCannotDeleteAStaleDisplayedHistoryEntry() = runTest(dispatcher) {
        start()
        connectAccount()
        val staleEntry = viewModel.uiState.value.history.single()
        preferences.settings.value = BackupSettings(accountEmail = "another@example.com")
        viewModel.deleteHistory(staleEntry)
        runCurrent()

        assertFalse(viewModel.uiState.value.deletedBackup)
        assertEquals(listOf(staleEntry), history.entries.value["owner@example.com"])
        assertNull(history.entries.value["another@example.com"])
    }

    @Test
    fun failedLocalHistoryDeletionKeepsRecordAndRetriesWithoutAuthorization() = runTest(dispatcher) {
        start()
        connectAccount()
        val effectsBefore = effects.size
        history.failure = IOException("Disk full")
        viewModel.deleteHistory(viewModel.uiState.value.history.single())
        runCurrent()

        assertEquals(DriveBackupProblem.LOCAL_STORAGE, viewModel.uiState.value.problem)
        assertEquals(1, viewModel.uiState.value.history.size)
        assertFalse(viewModel.uiState.value.deletedBackup)
        history.failure = null
        viewModel.retry()
        runCurrent()

        assertEquals(effectsBefore, effects.size)
        assertTrue(viewModel.uiState.value.history.isEmpty())
        assertTrue(viewModel.uiState.value.deletedBackup)
        assertNull(viewModel.uiState.value.problem)
        assertTrue(remote.deletes.isEmpty())
    }

    @Test
    fun historyDeletionRequiresExactCurrentAccountRecordAndBlocksUntilCommit() = runTest(dispatcher) {
        start()
        connectAccount()
        val entry = viewModel.uiState.value.history.single()
        viewModel.deleteHistory(entry.copy(name = "tampered"))
        runCurrent()
        assertEquals(1, viewModel.uiState.value.history.size)
        history.deleteGate = CompletableDeferred()
        viewModel.deleteHistory(entry)
        runCurrent()
        assertEquals(DriveBackupPhase.DELETING, viewModel.uiState.value.phase)

        val effectsBefore = effects.size
        viewModel.cancel()
        viewModel.connect()
        viewModel.restore(remote.existing)
        viewModel.backup()
        viewModel.disconnect()
        assertEquals(effectsBefore, effects.size)
        assertEquals(DriveBackupPhase.DELETING, viewModel.uiState.value.phase)
        history.deleteGate!!.complete(Unit)
        runCurrent()
        assertTrue(viewModel.uiState.value.deletedBackup)
        assertFalse(viewModel.uiState.value.isBusy)
        assertFalse(gate.mutex.isLocked)
    }

    @Test
    fun failedRetentionAfterUploadRetriesCleanupWithoutUploadingAnotherArchive() = runTest(dispatcher) {
        start()
        connectAccount()
        remote.deleteFailure = DriveBackupException.Network()
        viewModel.backup()
        viewModel.authorized(authorizationId(), "upload-token")
        runCurrent()

        assertEquals(remote.uploaded, viewModel.uiState.value.currentBackup)
        assertEquals(2, viewModel.uiState.value.backedUpCount)
        assertEquals(DriveBackupProblem.NETWORK, viewModel.uiState.value.problem)
        assertEquals(2, viewModel.uiState.value.history.size)
        assertEquals(200L, preferences.settings.value.lastBackupAt)
        assertEquals(1, remote.uploads.size)

        remote.deleteFailure = null
        viewModel.retry()
        viewModel.authorized(authorizationId(), "retry-token")
        runCurrent()
        assertEquals(1, remote.uploads.size)
        assertEquals(listOf(remote.uploaded), remote.listedBackups)
        assertNull(viewModel.uiState.value.problem)
    }

    @Test
    fun failedHistoryPersistenceDoesNotRemoveExistingRemoteBackup() = runTest(dispatcher) {
        start()
        connectAccount()
        history.failure = IOException("History unavailable")
        viewModel.backup()
        viewModel.authorized(authorizationId(), "upload-token")
        runCurrent()

        assertEquals(DriveBackupProblem.LOCAL_STORAGE, viewModel.uiState.value.problem)
        assertTrue(remote.listedBackups.contains(remote.existing))
        assertTrue(remote.deletes.isEmpty())
        assertEquals("pdf-content-v2:" + "0".repeat(64), preferences.settings.value.lastSnapshotFingerprint)
    }

    @Test
    fun backgroundUploadUpdatesCurrentBackupEvenWhenItsHistoryRecordWasRemoved() = runTest(dispatcher) {
        start()
        connectAccount()
        history.delete("owner@example.com", remote.uploaded.id)
        gate.automaticRunning.value = true
        runCurrent()

        archives.upload("worker-token", "owner@example.com", local.prepared.file, "automatic.zip", 2)
        gate.automaticRunning.value = false
        runCurrent()

        assertEquals(remote.uploaded, viewModel.uiState.value.currentBackup)
        assertEquals(listOf(remote.existing.id), viewModel.uiState.value.history.map { it.id })
        assertFalse(viewModel.uiState.value.automaticRunning)
        viewModel.restore(remote.uploaded)
        viewModel.authorized(authorizationId(), "restore-token")
        runCurrent()
        assertEquals(remote.uploaded, remote.downloads.single().entry)
    }

    @Test
    fun repeatedUnchangedManualBackupDoesNotPrepareUploadOrAppendHistory() = runTest(dispatcher) {
        start()
        connectAccount()
        backupSuccessfully()
        val saved = preferences.settings.value
        val recorded = viewModel.uiState.value.history

        backupSuccessfully()

        assertEquals(1, local.createCalls)
        assertEquals(1, remote.uploads.size)
        assertEquals(recorded, viewModel.uiState.value.history)
        assertEquals(saved, preferences.settings.value)
        assertTrue(viewModel.uiState.value.backupUnchanged)
        assertNull(viewModel.uiState.value.backedUpCount)
        assertNull(viewModel.uiState.value.problem)
        assertFalse(viewModel.uiState.value.isBusy)
    }

    @Test
    fun changingPdfBytesWithSameLengthAndTimestampCreatesANewManualBackup() = runTest(dispatcher) {
        start()
        connectAccount()
        backupSuccessfully()
        val previousSnapshot = preferences.settings.value.lastSnapshotFingerprint
        val pdf = File(java.net.URI(documents.documents.value.first().pdfUri))
        val timestamp = pdf.lastModified()
        pdf.writeBytes(pdf.readBytes().apply { this[0] = (this[0] + 1).toByte() })
        assertTrue(pdf.setLastModified(timestamp))

        backupSuccessfully()

        assertEquals(2, local.createCalls)
        assertEquals(2, remote.uploads.size)
        assertTrue(previousSnapshot != preferences.settings.value.lastSnapshotFingerprint)
        assertFalse(viewModel.uiState.value.backupUnchanged)
        assertEquals(2, viewModel.uiState.value.backedUpCount)
    }

    @Test
    fun changingBackedUpDocumentMetadataCreatesANewManualBackup() = runTest(dispatcher) {
        start()
        connectAccount()
        backupSuccessfully()
        val previousSnapshot = preferences.settings.value.lastSnapshotFingerprint
        documents.documents.value = documents.documents.value.mapIndexed { index, document ->
            if (index == 0) document.copy(name = "Renamed document") else document
        }
        runCurrent()

        backupSuccessfully()

        assertEquals(2, local.createCalls)
        assertEquals(2, remote.uploads.size)
        assertTrue(previousSnapshot != preferences.settings.value.lastSnapshotFingerprint)
        assertFalse(viewModel.uiState.value.backupUnchanged)
    }

    @Test
    fun deletingEveryHistoryRecordDoesNotRecreateAnUnchangedCloudBackup() = runTest(dispatcher) {
        start()
        connectAccount()
        backupSuccessfully()
        val saved = preferences.settings.value
        viewModel.uiState.value.history.toList().forEach { entry ->
            viewModel.deleteHistory(entry)
            runCurrent()
        }
        assertTrue(viewModel.uiState.value.history.isEmpty())

        backupSuccessfully()

        assertEquals(1, local.createCalls)
        assertEquals(1, remote.uploads.size)
        assertEquals(saved, preferences.settings.value)
        assertTrue(viewModel.uiState.value.history.isEmpty())
        assertEquals(remote.uploaded, viewModel.uiState.value.currentBackup)
        assertTrue(viewModel.uiState.value.backupUnchanged)
        assertFalse(viewModel.uiState.value.deletedBackup)
    }

    @Test
    fun removedCloudBackupIsRecreatedEvenWithUnchangedLocalFiles() = runTest(dispatcher) {
        start()
        connectAccount()
        backupSuccessfully()
        remote.listedBackups = emptyList()

        backupSuccessfully()

        assertEquals(2, local.createCalls)
        assertEquals(2, remote.uploads.size)
        assertEquals(listOf(remote.uploaded), remote.listedBackups)
        assertFalse(viewModel.uiState.value.backupUnchanged)
    }

    @Test
    fun cloudBackupReplacedByAnotherDeviceDoesNotSkipLocalBackup() = runTest(dispatcher) {
        start()
        connectAccount()
        backupSuccessfully()
        val otherDevice = remote.uploaded.copy(id = "another-device", modifiedTimeMillis = 300L)
        remote.listedBackups = listOf(otherDevice)

        backupSuccessfully()

        assertEquals(2, local.createCalls)
        assertEquals(2, remote.uploads.size)
        assertEquals(listOf(remote.uploaded), remote.listedBackups)
        assertTrue(remote.deletes.any { it.entry == otherDevice })
        assertFalse(viewModel.uiState.value.backupUnchanged)
    }

    @Test
    fun changesDuringManualPreparationDiscardArchiveWithoutRecordingSuccess() = runTest(dispatcher) {
        start()
        connectAccount()
        local.onCreate = {
            documents.documents.value = documents.documents.value.map { it.copy(name = "Changed during preparation") }
        }
        viewModel.backup()
        viewModel.authorized(authorizationId(), "backup-token")
        runCurrent()

        assertTrue(remote.uploads.isEmpty())
        assertEquals(listOf(local.prepared.file), local.discarded)
        assertEquals("pdf-content-v2:" + "0".repeat(64), preferences.settings.value.lastSnapshotFingerprint)
        assertEquals(remote.existing.modifiedTimeMillis, preferences.settings.value.lastBackupAt)
        assertEquals(DriveBackupProblem.LOCAL_STORAGE, viewModel.uiState.value.problem)
        assertFalse(viewModel.uiState.value.backupUnchanged)

        local.onCreate = {}
        viewModel.retry()
        viewModel.authorized(authorizationId(), "retry-token")
        runCurrent()
        assertEquals(1, remote.uploads.size)
        assertNull(viewModel.uiState.value.problem)
    }

    @Test
    fun changesDuringUploadAreDetectedByTheNextManualBackup() = runTest(dispatcher) {
        start()
        connectAccount()
        remote.uploadGate = CompletableDeferred()
        viewModel.backup()
        viewModel.authorized(authorizationId(), "backup-token")
        runCurrent()
        assertEquals(DriveBackupPhase.UPLOADING, viewModel.uiState.value.phase)
        documents.documents.value = documents.documents.value.map { it.copy(pageCount = it.pageCount + 1) }
        remote.uploadGate!!.complete(Unit)
        runCurrent()
        val uploadedSnapshot = preferences.settings.value.lastSnapshotFingerprint

        backupSuccessfully()

        assertEquals(2, remote.uploads.size)
        assertTrue(uploadedSnapshot != preferences.settings.value.lastSnapshotFingerprint)
        assertFalse(viewModel.uiState.value.backupUnchanged)
    }

    @Test
    fun unchangedMessageClearsOnNewActionsAndBackgroundWork() = runTest(dispatcher) {
        start()
        connectAccount()
        backupSuccessfully()
        backupSuccessfully()
        assertTrue(viewModel.uiState.value.backupUnchanged)

        viewModel.setWifiOnly(false)
        runCurrent()
        assertFalse(viewModel.uiState.value.backupUnchanged)
        backupSuccessfully()
        assertTrue(viewModel.uiState.value.backupUnchanged)

        gate.automaticRunning.value = true
        runCurrent()
        assertFalse(viewModel.uiState.value.backupUnchanged)
        gate.automaticRunning.value = false
        runCurrent()
        backupSuccessfully()
        assertTrue(viewModel.uiState.value.backupUnchanged)

        viewModel.connect()
        assertFalse(viewModel.uiState.value.backupUnchanged)
        viewModel.authorizationFailed(authorizationId(), DriveBackupProblem.AUTHORIZATION)
        runCurrent()
        assertFalse(viewModel.uiState.value.backupUnchanged)
    }

    @Test
    fun replacingCloudContentsUnderTheSameIdStillRequiresAManualBackup() = runTest(dispatcher) {
        start()
        connectAccount()
        backupSuccessfully()
        remote.listedBackups = listOf(remote.uploaded.copy(modifiedTimeMillis = 300L))

        backupSuccessfully()

        assertEquals(2, local.createCalls)
        assertEquals(2, remote.uploads.size)
        assertFalse(viewModel.uiState.value.backupUnchanged)

        remote.listedBackups = listOf(remote.uploaded.copy(sizeBytes = 900L))
        backupSuccessfully()

        assertEquals(3, local.createCalls)
        assertEquals(3, remote.uploads.size)
        assertFalse(viewModel.uiState.value.backupUnchanged)
    }

    @Test
    fun documentChangesClearThePreviousUnchangedMessage() = runTest(dispatcher) {
        start()
        connectAccount()
        backupSuccessfully()
        backupSuccessfully()
        assertTrue(viewModel.uiState.value.backupUnchanged)

        documents.documents.value = documents.documents.value.map { it.copy(name = "Updated document") }
        runCurrent()

        assertFalse(viewModel.uiState.value.backupUnchanged)
    }

    @Test
    fun legacyFingerprintWithIdenticalRemoteArchiveMigratesWithoutAnotherUpload() = runTest(dispatcher) {
        start()
        connectAccount()
        remote.downloadArchive = snapshotArchive(documents.documents.value)
        preferences.settings.value = preferences.settings.value.copy(lastSnapshotFingerprint = "old-metadata-fingerprint")
        val previousDate = preferences.settings.value.lastBackupAt
        val recorded = viewModel.uiState.value.history

        backupSuccessfully()

        assertEquals(0, local.createCalls)
        assertTrue(remote.uploads.isEmpty())
        assertEquals(1, remote.downloads.size)
        assertTrue(local.restored.isEmpty())
        assertEquals(previousDate, preferences.settings.value.lastBackupAt)
        assertEquals(recorded, viewModel.uiState.value.history)
        assertEquals(BackupSnapshotFingerprint(documents, dispatcher).current(), preferences.settings.value.lastSnapshotFingerprint)
        assertTrue(viewModel.uiState.value.backupUnchanged)
        assertEquals(listOf(local.restoreFile), local.discarded)
        assertFalse(local.restoreFile.exists())

        backupSuccessfully()
        assertEquals(1, remote.downloads.size)
        assertTrue(remote.uploads.isEmpty())
    }

    @Test
    fun unknownFingerprintWithIdenticalRemoteArchiveEstablishesBaselineWithoutUpload() = runTest(dispatcher) {
        start()
        connectAccount(knownSnapshot = false)
        remote.downloadArchive = snapshotArchive(documents.documents.value)
        assertNull(preferences.settings.value.lastBackupId)
        val recorded = viewModel.uiState.value.history

        backupSuccessfully()

        assertEquals(0, local.createCalls)
        assertTrue(remote.uploads.isEmpty())
        assertTrue(viewModel.uiState.value.backupUnchanged)
        assertEquals(remote.existing.id, preferences.settings.value.lastBackupId)
        assertEquals(remote.existing.modifiedTimeMillis, preferences.settings.value.lastBackupAt)
        assertEquals(recorded, viewModel.uiState.value.history)
        assertFalse(local.restoreFile.exists())
        assertTrue(local.restored.isEmpty())
    }

    @Test
    fun legacyFingerprintWithDifferentRemoteArchiveUploadsOnlyAfterComparing() = runTest(dispatcher) {
        start()
        connectAccount()
        preferences.settings.value = preferences.settings.value.copy(lastSnapshotFingerprint = "old-fingerprint")

        backupSuccessfully()

        assertEquals(1, remote.downloads.size)
        assertEquals(1, local.createCalls)
        assertEquals(1, remote.uploads.size)
        assertEquals(listOf(local.restoreFile, local.prepared.file), local.discarded)
        assertFalse(local.restoreFile.exists())
        assertFalse(viewModel.uiState.value.backupUnchanged)
    }

    @Test
    fun failedLegacyComparisonDoesNotUploadAndCanRetryComparison() = runTest(dispatcher) {
        start()
        connectAccount(knownSnapshot = false)
        remote.downloadArchive = snapshotArchive(documents.documents.value)
        remote.downloadFailure = DriveBackupException.Network()
        viewModel.backup()
        viewModel.authorized(authorizationId(), "comparison-token")
        runCurrent()

        assertEquals(DriveBackupProblem.NETWORK, viewModel.uiState.value.problem)
        assertEquals(0, local.createCalls)
        assertTrue(remote.uploads.isEmpty())
        assertNull(preferences.settings.value.lastSnapshotFingerprint)
        assertNull(preferences.settings.value.lastBackupAt)
        assertFalse(local.restoreFile.exists())

        remote.downloadFailure = null
        viewModel.retry()
        viewModel.authorized(authorizationId(), "retry-token")
        runCurrent()
        assertTrue(viewModel.uiState.value.backupUnchanged)
        assertNull(viewModel.uiState.value.problem)
        assertEquals(2, remote.downloads.size)
        assertTrue(remote.uploads.isEmpty())
    }

    @Test
    fun cancellingLegacyComparisonCleansDownloadWithoutUploadOrNewBaseline() = runTest(dispatcher) {
        start()
        connectAccount(knownSnapshot = false)
        remote.downloadGate = CompletableDeferred()
        viewModel.backup()
        viewModel.authorized(authorizationId(), "comparison-token")
        runCurrent()
        assertEquals(1, remote.downloads.size)

        viewModel.cancel()
        runCurrent()

        assertEquals(0, local.createCalls)
        assertTrue(remote.uploads.isEmpty())
        assertNull(preferences.settings.value.lastSnapshotFingerprint)
        assertNull(preferences.settings.value.lastBackupAt)
        assertFalse(local.restoreFile.exists())
        assertEquals(listOf(local.restoreFile), local.discarded)
        assertFalse(viewModel.uiState.value.isBusy)
        assertFalse(viewModel.uiState.value.backupUnchanged)
    }

    @Test
    fun anotherDeviceUploadingIdenticalDocumentsDoesNotCauseAnotherManualUpload() = runTest(dispatcher) {
        start()
        connectAccount()
        backupSuccessfully()
        val anotherDevice = remote.uploaded.copy(id = "same-content-other-device", modifiedTimeMillis = 300L)
        remote.listedBackups = listOf(anotherDevice)
        remote.downloadArchive = snapshotArchive(documents.documents.value)

        backupSuccessfully()

        assertEquals(1, local.createCalls)
        assertEquals(1, remote.uploads.size)
        assertEquals(1, remote.downloads.size)
        assertEquals(anotherDevice.id, preferences.settings.value.lastBackupId)
        assertEquals(anotherDevice.modifiedTimeMillis, preferences.settings.value.lastBackupAt)
        assertTrue(viewModel.uiState.value.backupUnchanged)
        assertTrue(local.restored.isEmpty())
    }

    private fun TestScope.backupSuccessfully() {
        viewModel.backup()
        viewModel.authorized(authorizationId(), "backup-token")
        runCurrent()
        assertNull(viewModel.uiState.value.problem)
        assertFalse(viewModel.uiState.value.isBusy)
    }

    private fun DriveBackupEntry.historyEntry() = BackupHistoryEntry(id, name, modifiedTimeMillis, sizeBytes)

    private class FakeHistory : BackupHistoryRepository {
        override val entries = MutableStateFlow<Map<String, List<BackupHistoryEntry>>>(emptyMap())
        private val deleted = mutableMapOf<String, MutableSet<String>>()
        var failure: Exception? = null
        var deleteGate: CompletableDeferred<Unit>? = null

        override suspend fun record(email: String, entries: List<BackupHistoryEntry>) {
            failure?.let { throw it }
            val key = email.trim().lowercase(java.util.Locale.ROOT)
            val old = this.entries.value[key].orEmpty()
            val combined = (entries.map { entry ->
                entry.copy(documentCount = entry.documentCount ?: old.find { it.id == entry.id }?.documentCount)
            } + old).distinctBy { it.id }.filterNot { it.id in deleted[key].orEmpty() }
                .sortedByDescending { it.createdAtMillis }
            this.entries.value = this.entries.value + (key to combined)
        }

        override suspend fun delete(email: String, id: String) {
            deleteGate?.await()
            failure?.let { throw it }
            val key = email.trim().lowercase(java.util.Locale.ROOT)
            deleted.getOrPut(key) { mutableSetOf() }.add(id)
            entries.value = entries.value + (key to entries.value[key].orEmpty().filterNot { it.id == id })
        }
    }

    private suspend fun TestScope.start(documentCount: Int = 2) {
        documents.documents.value = List(documentCount) { index ->
            val pdf = temporary.newFile("document-$index.pdf").apply { writeText("PDF fixture $index") }
            DocumentModel(index.toLong() + 1, "Document $index", pdf.toURI().toString(), 1, 1L, pdf.length())
        }
        local.restoreFile = temporary.newFile("remote-comparison.zip")
        remote.downloadArchive = snapshotArchive(
            listOf(documents.documents.value.firstOrNull()?.copy(name = "Different remote document"))
                .filterNotNull(),
        ).takeIf { documentCount > 0 }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.collect(effects::add) }
        runCurrent()
    }

    private fun TestScope.connectAccount(knownSnapshot: Boolean = true) {
        viewModel.connect()
        viewModel.authorized(authorizationId(), "connect-token")
        runCurrent()
        assertEquals(remote.email, viewModel.uiState.value.accountEmail)
        assertFalse(viewModel.uiState.value.isBusy)
        if (knownSnapshot) {
            viewModel.uiState.value.currentBackup?.let { current ->
                preferences.settings.value = preferences.settings.value.copy(
                    lastBackupId = current.id, lastBackupAt = current.modifiedTimeMillis,
                    lastBackupBytes = current.sizeBytes,
                    lastSnapshotFingerprint = "pdf-content-v2:" + "0".repeat(64),
                )
            }
            runCurrent()
        }
    }

    private suspend fun snapshotArchive(snapshot: List<DocumentModel>): File? {
        if (snapshot.isEmpty()) return null
        val sources = snapshot.map { document ->
            val directory = temporary.newFolder()
            File(java.net.URI(document.pdfUri)).copyTo(File(directory, "document.pdf"))
            BackupDocumentSource(
                BackupDocument(
                    key = java.util.UUID.randomUUID().toString(), name = document.name,
                    pageCount = document.pageCount, createdAt = document.createdAt,
                    tagKey = null, protected = document.isProtected, editableProject = false,
                ),
                directory, listOf("document.pdf"),
            )
        }
        return temporary.newFile().also { DocumentBackupArchive().write(it, sources) }
    }

    private fun authorizationId() = effects.filterIsInstance<DriveBackupEffect.Authorize>().last().requestId

    private class FakeBackupSettings : BackupSettingsRepository {
        override val settings = MutableStateFlow(BackupSettings())
        var failure: Exception? = null
        var updateGate: CompletableDeferred<Unit>? = null
        override suspend fun update(transform: (BackupSettings) -> BackupSettings) {
            updateGate?.await()
            failure?.let { throw it }
            settings.value = transform(settings.value)
        }
    }

    private class FakeBackupScheduler : BackupScheduler {
        val scheduled = mutableListOf<BackupSettings>()
        var cancellations = 0
        override fun schedule(settings: BackupSettings) { scheduled += settings }
        override fun cancel() { cancellations++ }
    }

    private class FakeLocalBackup : DocumentBackupRepository {
        val prepared = PreparedDocumentBackup(File("temporary-backup.zip"), 2, 1, 1)
        var restoreFile = File("temporary-restore.zip")
        val discarded = mutableListOf<File>()
        val restored = mutableListOf<File>()
        var createCalls = 0
        var createRestoreCalls = 0
        var createFailure: Exception? = null
        var onCreate: () -> Unit = {}
        var restoreFailure: Exception? = null
        var restoreGate: CompletableDeferred<Unit>? = null
        var discardGate: CompletableDeferred<Unit>? = null
        var onRestoreCommitted: (() -> Unit)? = null

        override suspend fun createBackup(onProgress: (DocumentBackupProgress) -> Unit): PreparedDocumentBackup {
            createCalls++
            onProgress(DocumentBackupProgress(1, 2))
            createFailure?.let { throw it }
            onCreate()
            return prepared
        }

        override suspend fun createRestoreFile(): File {
            createRestoreCalls++
            return restoreFile
        }

        override suspend fun restoreBackup(file: File, onProgress: (DocumentBackupProgress) -> Unit): DocumentBackupRestoreResult {
            restored += file
            onProgress(DocumentBackupProgress(1, 2))
            restoreGate?.await()
            restoreFailure?.let { throw it }
            onRestoreCommitted?.invoke()
            return DocumentBackupRestoreResult(2, 1, 1)
        }

        override suspend fun discard(file: File) {
            discarded += file
            discardGate?.await()
            if (file == restoreFile) file.delete()
        }

        fun release() {
            restoreGate?.complete(Unit)
            discardGate?.complete(Unit)
        }
    }

    private data class Upload(val token: String, val file: File, val name: String)
    private data class Download(val token: String, val entry: DriveBackupEntry, val file: File)
    private data class Delete(val token: String, val entry: DriveBackupEntry)

    private class FakeDrive : DriveBackupRemote {
        val existing = DriveBackupEntry("existing", "Scaniqo-existing.zip", 100L, 500L)
        val uploaded = DriveBackupEntry("uploaded", "Scaniqo-uploaded.zip", 200L, 800L)
        val accountTokens = mutableListOf<String>()
        val listTokens = mutableListOf<String>()
        val uploads = mutableListOf<Upload>()
        val downloads = mutableListOf<Download>()
        val deletes = mutableListOf<Delete>()
        var listedBackups = listOf(existing)
        var email: String? = "owner@example.com"
        var accountFailure: Exception? = null
        var uploadFailure: Exception? = null
        var downloadFailure: Exception? = null
        var downloadArchive: File? = null
        var deleteFailure: Exception? = null
        var onDeleteCommitted: (() -> Unit)? = null
        var accountGate: CompletableDeferred<Unit>? = null
        var listGate: CompletableDeferred<Unit>? = null
        var uploadGate: CompletableDeferred<Unit>? = null
        var downloadGate: CompletableDeferred<Unit>? = null
        var deleteGate: CompletableDeferred<Unit>? = null
        var ignoreAccountCancellation = false
        var ignoreListCancellation = false
        var ignoreUploadCancellation = false

        override suspend fun accountEmail(accessToken: String): String? {
            accountTokens += accessToken
            accountFailure?.let { throw it }
            if (ignoreAccountCancellation) withContext(NonCancellable) { accountGate?.await() }
            else accountGate?.await()
            return email
        }

        override suspend fun listBackups(accessToken: String): List<DriveBackupEntry> {
            listTokens += accessToken
            if (ignoreListCancellation) withContext(NonCancellable) { listGate?.await() }
            else listGate?.await()
            return listedBackups
        }

        override suspend fun upload(
            accessToken: String, archive: File, name: String,
            onProgress: (transferredBytes: Long, totalBytes: Long) -> Unit,
        ): DriveBackupEntry {
            uploads += Upload(accessToken, archive, name)
            onProgress(40L, 100L)
            if (ignoreUploadCancellation) withContext(NonCancellable) { uploadGate?.await() }
            else uploadGate?.await()
            uploadFailure?.let { throw it }
            listedBackups = (listOf(uploaded) + listedBackups).distinctBy { it.id }
            return uploaded
        }

        override suspend fun download(
            accessToken: String, entry: DriveBackupEntry, destination: File,
            onProgress: (transferredBytes: Long, totalBytes: Long) -> Unit,
        ) {
            downloads += Download(accessToken, entry, destination)
            onProgress(40L, 100L)
            downloadGate?.await()
            downloadFailure?.let { throw it }
            downloadArchive?.copyTo(destination, overwrite = true)
        }

        override suspend fun delete(accessToken: String, entry: DriveBackupEntry) {
            deletes += Delete(accessToken, entry)
            deleteGate?.await()
            deleteFailure?.let { throw it }
            listedBackups = listedBackups.filterNot { it.id == entry.id }
            onDeleteCommitted?.invoke()
        }

        fun release() {
            accountGate?.complete(Unit)
            listGate?.complete(Unit)
            uploadGate?.complete(Unit)
            downloadGate?.complete(Unit)
            deleteGate?.complete(Unit)
        }
    }
}
