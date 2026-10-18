package com.joseleandro.scaniqo.ui.backup

import androidx.lifecycle.ViewModelStore
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
    private val viewModel by lazy {
        DriveBackupViewModel(local, remote, documents).also { store.put("backup", it) }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
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
        assertEquals(listOf(remote.uploaded, remote.existing), viewModel.uiState.value.backups)
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
        assertEquals(listOf(remote.existing), viewModel.uiState.value.backups)
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

        assertTrue(viewModel.uiState.value.backups.isEmpty())
        assertNull(viewModel.uiState.value.problem)
        assertEquals(DriveBackupPhase.IDLE, viewModel.uiState.value.phase)
    }

    @Test
    fun aLateUploadResultAfterCancellationDoesNotPublishSuccess() = runTest(dispatcher) {
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

        assertEquals(listOf(remote.existing), viewModel.uiState.value.backups)
        assertNull(viewModel.uiState.value.backedUpCount)
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
        assertTrue(viewModel.uiState.value.backups.isEmpty())
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
        assertTrue(viewModel.uiState.value.backups.isEmpty())
        assertEquals(DriveBackupProblem.AUTHORIZATION, viewModel.uiState.value.problem)
        assertEquals(listOf(local.prepared.file), local.discarded)
        assertFalse(viewModel.uiState.value.isBusy)
    }

    @Test
    fun successfulDisconnectClearsRemoteStateButPreservesTheLocalDocumentCount() = runTest(dispatcher) {
        start()
        connectAccount()
        viewModel.disconnect()
        val revoke = effects.filterIsInstance<DriveBackupEffect.Revoke>().single()
        assertEquals(remote.email, revoke.email)
        assertEquals(DriveBackupPhase.DISCONNECTING, viewModel.uiState.value.phase)
        viewModel.cancel()
        assertEquals(DriveBackupPhase.DISCONNECTING, viewModel.uiState.value.phase)
        viewModel.disconnected(revoke.requestId + 1, true)
        assertEquals(DriveBackupPhase.DISCONNECTING, viewModel.uiState.value.phase)

        viewModel.disconnected(revoke.requestId, true)
        assertNull(viewModel.uiState.value.accountEmail)
        assertTrue(viewModel.uiState.value.backups.isEmpty())
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
        val revoke = effects.filterIsInstance<DriveBackupEffect.Revoke>().single()
        viewModel.disconnected(revoke.requestId, false)

        assertEquals(remote.email, viewModel.uiState.value.accountEmail)
        assertEquals(listOf(remote.existing), viewModel.uiState.value.backups)
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

    private fun TestScope.start(documentCount: Int = 2) {
        documents.documents.value = List(documentCount) { index ->
            DocumentModel(index.toLong() + 1, "Document $index", "content://documents/$index.pdf", 1, 1L, 1024L)
        }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.collect(effects::add) }
        runCurrent()
    }

    private fun TestScope.connectAccount() {
        viewModel.connect()
        viewModel.authorized(authorizationId(), "connect-token")
        runCurrent()
        assertEquals(remote.email, viewModel.uiState.value.accountEmail)
        assertFalse(viewModel.uiState.value.isBusy)
    }

    private fun authorizationId() = effects.filterIsInstance<DriveBackupEffect.Authorize>().last().requestId

    private class FakeLocalBackup : DocumentBackupRepository {
        val prepared = PreparedDocumentBackup(File("temporary-backup.zip"), 2, 1, 1)
        val restoreFile = File("temporary-restore.zip")
        val discarded = mutableListOf<File>()
        val restored = mutableListOf<File>()
        var createCalls = 0
        var createRestoreCalls = 0
        var createFailure: Exception? = null
        var restoreFailure: Exception? = null
        var restoreGate: CompletableDeferred<Unit>? = null
        var discardGate: CompletableDeferred<Unit>? = null
        var onRestoreCommitted: (() -> Unit)? = null

        override suspend fun createBackup(onProgress: (DocumentBackupProgress) -> Unit): PreparedDocumentBackup {
            createCalls++
            onProgress(DocumentBackupProgress(1, 2))
            createFailure?.let { throw it }
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
        }

        fun release() {
            restoreGate?.complete(Unit)
            discardGate?.complete(Unit)
        }
    }

    private data class Upload(val token: String, val file: File, val name: String)
    private data class Download(val token: String, val entry: DriveBackupEntry, val file: File)

    private class FakeDrive : DriveBackupRemote {
        val existing = DriveBackupEntry("existing", "Scaniqo-existing.zip", 100L, 500L)
        val uploaded = DriveBackupEntry("uploaded", "Scaniqo-uploaded.zip", 200L, 800L)
        val accountTokens = mutableListOf<String>()
        val listTokens = mutableListOf<String>()
        val uploads = mutableListOf<Upload>()
        val downloads = mutableListOf<Download>()
        var email: String? = "owner@example.com"
        var uploadFailure: Exception? = null
        var downloadFailure: Exception? = null
        var accountGate: CompletableDeferred<Unit>? = null
        var listGate: CompletableDeferred<Unit>? = null
        var uploadGate: CompletableDeferred<Unit>? = null
        var downloadGate: CompletableDeferred<Unit>? = null
        var ignoreAccountCancellation = false
        var ignoreListCancellation = false
        var ignoreUploadCancellation = false

        override suspend fun accountEmail(accessToken: String): String? {
            accountTokens += accessToken
            if (ignoreAccountCancellation) withContext(NonCancellable) { accountGate?.await() }
            else accountGate?.await()
            return email
        }

        override suspend fun listBackups(accessToken: String): List<DriveBackupEntry> {
            listTokens += accessToken
            if (ignoreListCancellation) withContext(NonCancellable) { listGate?.await() }
            else listGate?.await()
            return listOf(existing)
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
        }

        fun release() {
            accountGate?.complete(Unit)
            listGate?.complete(Unit)
            uploadGate?.complete(Unit)
            downloadGate?.complete(Unit)
        }
    }
}
