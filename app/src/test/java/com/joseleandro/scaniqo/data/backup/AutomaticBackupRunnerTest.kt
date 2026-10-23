package com.joseleandro.scaniqo.data.backup

import com.joseleandro.scaniqo.domain.model.BackupAutomaticProblem
import com.joseleandro.scaniqo.domain.model.BackupSettings
import com.joseleandro.scaniqo.domain.model.DocumentBackupProgress
import com.joseleandro.scaniqo.domain.model.DocumentBackupRestoreResult
import com.joseleandro.scaniqo.domain.model.DocumentModel
import com.joseleandro.scaniqo.domain.model.DriveBackupEntry
import com.joseleandro.scaniqo.domain.model.DriveBackupException
import com.joseleandro.scaniqo.domain.model.PreparedDocumentBackup
import com.joseleandro.scaniqo.domain.model.ScannedDocument
import com.joseleandro.scaniqo.domain.repository.BackupSettingsRepository
import com.joseleandro.scaniqo.domain.repository.DocumentBackupRepository
import com.joseleandro.scaniqo.domain.repository.DocumentRepository
import com.joseleandro.scaniqo.domain.repository.DriveBackupRemote
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class AutomaticBackupRunnerTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun disabledAndDisconnectedBackupsNeverRequestAuthorization() = runTest {
        val h = Harness(this)
        h.settings.update { it.copy(automaticEnabled = false) }
        assertEquals(AutomaticBackupOutcome.COMPLETED, h.runner.run(0))
        h.settings.update { it.copy(automaticEnabled = true, accountEmail = null) }
        assertEquals(AutomaticBackupOutcome.COMPLETED, h.runner.run(0))
        assertEquals(0, h.authorizationCalls)
        assertEquals(0, h.remote.uploads)
    }

    @Test fun unchangedPdfSnapshotDoesNotAuthorizeOrUploadAgain() = runTest {
        val h = Harness(this)
        val snapshot = h.fingerprint.current()
        h.settings.update { it.copy(lastSnapshotFingerprint = snapshot) }
        assertEquals(AutomaticBackupOutcome.COMPLETED, h.runner.run(0))
        assertEquals(0, h.authorizationCalls)
        assertEquals(0, h.remote.uploads)
    }

    @Test fun emptyLibraryDoesNotAuthorizeOrOverwriteLastSuccessfulBackup() = runTest {
        val h = Harness(this)
        h.documents.state.value = emptyList()
        h.settings.update { it.copy(lastBackupAt = 99L, lastSnapshotFingerprint = "previous") }
        assertEquals(AutomaticBackupOutcome.COMPLETED, h.runner.run(0))
        assertEquals(0, h.authorizationCalls)
        assertEquals(99L, h.settings.settings.value.lastBackupAt)
        assertEquals("previous", h.settings.settings.value.lastSnapshotFingerprint)
    }

    @Test fun successRecordsSnapshotAndDiscardsTemporaryArchive() = runTest {
        val h = Harness(this)
        val expectedFingerprint = h.fingerprint.current()
        assertEquals(AutomaticBackupOutcome.COMPLETED, h.runner.run(0))
        assertEquals("owner@example.com", h.authorizedEmail)
        assertEquals(1, h.remote.uploads)
        assertTrue(h.remote.uploadedName!!.endsWith(".zip"))
        with(h.settings.settings.value) {
            assertEquals(5_000L, lastBackupAt)
            assertEquals(1, lastBackupDocumentCount)
            assertEquals(3L, lastBackupBytes)
            assertEquals(expectedFingerprint, lastSnapshotFingerprint)
            assertFalse(needsAuthorization)
            assertNull(lastAutomaticProblem)
        }
        assertFalse(h.backups.lastArchive!!.exists())
        assertFalse(h.gate.automaticRunning.value)
        assertFalse(h.gate.mutex.isLocked)
    }

    @Test fun disablingAfterUploadConfirmationPreservesSnapshotAndPreventsDuplicateOnReenable() = runTest {
        val h = Harness(this)
        val snapshot = h.fingerprint.current()
        h.remote.onUpload = { h.settings.update { it.copy(automaticEnabled = false) } }
        assertEquals(AutomaticBackupOutcome.COMPLETED, h.runner.run(0))
        assertFalse(h.settings.settings.value.automaticEnabled)
        assertEquals(snapshot, h.settings.settings.value.lastSnapshotFingerprint)
        assertEquals(5_000L, h.settings.settings.value.lastBackupAt)
        h.settings.update { it.copy(automaticEnabled = true) }
        assertEquals(AutomaticBackupOutcome.COMPLETED, h.runner.run(0))
        assertEquals(1, h.remote.uploads)
    }

    @Test fun cancellationAfterConfirmedUploadStillPersistsItsResultAndCleansUp() = runTest {
        val h = Harness(this)
        val snapshot = h.fingerprint.current()
        h.remote.onUpload = { currentCoroutineContext().cancel() }
        val job = launch { h.runner.run(0) }
        job.join()
        assertTrue(job.isCancelled)
        assertEquals(snapshot, h.settings.settings.value.lastSnapshotFingerprint)
        assertEquals(5_000L, h.settings.settings.value.lastBackupAt)
        assertFalse(h.backups.lastArchive!!.exists())
        assertFalse(h.gate.automaticRunning.value)
        assertFalse(h.gate.mutex.isLocked)
    }

    @Test fun accountChangedDuringUploadNeverReceivesPreviousAccountsSnapshot() = runTest {
        val h = Harness(this)
        h.remote.onUpload = { h.settings.update { it.copy(accountEmail = "another@example.com") } }
        assertEquals(AutomaticBackupOutcome.COMPLETED, h.runner.run(0))
        assertEquals("another@example.com", h.settings.settings.value.accountEmail)
        assertNull(h.settings.settings.value.lastSnapshotFingerprint)
        assertNull(h.settings.settings.value.lastBackupAt)
    }

    @Test fun networkRetryIsLimitedAndPreservesLastSuccess() = runTest {
        val h = Harness(this)
        h.settings.update { it.copy(lastBackupAt = 11L) }
        h.remote.failure = DriveBackupException.Network()
        assertEquals(AutomaticBackupOutcome.RETRY, h.runner.run(0))
        assertEquals(AutomaticBackupOutcome.RETRY, h.runner.run(1))
        assertEquals(AutomaticBackupOutcome.COMPLETED, h.runner.run(2))
        assertEquals(BackupAutomaticProblem.NETWORK, h.settings.settings.value.lastAutomaticProblem)
        assertEquals(11L, h.settings.settings.value.lastBackupAt)
        assertNull(h.settings.settings.value.lastSnapshotFingerprint)
        assertFalse(h.backups.lastArchive!!.exists())
    }

    @Test fun quotaAndConfigurationFailuresDoNotRetry() = runTest {
        val h = Harness(this)
        h.remote.failure = DriveBackupException.Quota()
        assertEquals(AutomaticBackupOutcome.COMPLETED, h.runner.run(0))
        assertEquals(BackupAutomaticProblem.QUOTA, h.settings.settings.value.lastAutomaticProblem)
        h.remote.failure = DriveBackupException.ApiDisabled()
        assertEquals(AutomaticBackupOutcome.COMPLETED, h.runner.run(0))
        assertEquals(BackupAutomaticProblem.CONFIGURATION, h.settings.settings.value.lastAutomaticProblem)
        h.remote.failure = DriveBackupException.AccessBlocked()
        assertEquals(AutomaticBackupOutcome.COMPLETED, h.runner.run(0))
        assertEquals(BackupAutomaticProblem.REMOTE, h.settings.settings.value.lastAutomaticProblem)
    }

    @Test fun onlyTransientRemoteStatusesRetry() = runTest {
        val h = Harness(this)
        h.remote.failure = DriveBackupException.Remote(503)
        assertEquals(AutomaticBackupOutcome.RETRY, h.runner.run(0))
        h.remote.failure = DriveBackupException.Remote(403)
        assertEquals(AutomaticBackupOutcome.COMPLETED, h.runner.run(0))
    }

    @Test fun revokedAuthorizationPausesUntilUserReconnects() = runTest {
        val h = Harness(this)
        h.authorizationFailure = DriveBackupException.Unauthorized()
        assertEquals(AutomaticBackupOutcome.COMPLETED, h.runner.run(0))
        assertTrue(h.settings.settings.value.needsAuthorization)
        assertTrue(h.settings.settings.value.automaticEnabled)
        assertEquals(1, h.authorizationCalls)
        h.authorizationFailure = null
        assertEquals(AutomaticBackupOutcome.COMPLETED, h.runner.run(0))
        assertEquals(1, h.authorizationCalls)
        assertEquals(0, h.remote.uploads)
    }

    @Test fun driveUnauthorizedAlsoRequiresReconnectAndInvalidatesEphemeralToken() = runTest {
        val h = Harness(this)
        h.remote.failure = DriveBackupException.Unauthorized()
        assertEquals(AutomaticBackupOutcome.COMPLETED, h.runner.run(0))
        assertTrue(h.settings.settings.value.needsAuthorization)
        assertEquals("ephemeral-test-token", h.invalidatedToken)
        assertFalse(h.backups.lastArchive!!.exists())
    }

    @Test fun foregroundServiceStartsOnlyWhenThereAreChangedPdfsToUpload() = runTest {
        val h = Harness(this)
        var foregroundStarts = 0
        assertEquals(AutomaticBackupOutcome.COMPLETED, h.runner.run(0) { foregroundStarts++ })
        assertEquals(1, foregroundStarts)
        assertEquals(AutomaticBackupOutcome.COMPLETED, h.runner.run(0) { foregroundStarts++ })
        assertEquals(1, foregroundStarts)
    }

    @Test fun systemForegroundRestrictionsDeferWithoutCreatingArchiveOrRecordingSuccess() = runTest {
        val h = Harness(this)
        assertEquals(AutomaticBackupOutcome.RETRY, h.runner.run(0) { throw AutomaticBackupDeferredException() })
        assertEquals(AutomaticBackupOutcome.COMPLETED, h.runner.run(2) { throw AutomaticBackupDeferredException() })
        assertEquals(0, h.backups.creations)
        assertEquals(0, h.remote.uploads)
        assertNull(h.settings.settings.value.lastBackupAt)
        assertNull(h.settings.settings.value.lastAutomaticProblem)
        assertFalse(h.gate.automaticRunning.value)
    }

    @Test fun tokenForAnotherAccountIsRejectedBeforeCreatingBackup() = runTest {
        val h = Harness(this)
        h.remote.email = "another@example.com"
        assertEquals(AutomaticBackupOutcome.COMPLETED, h.runner.run(0))
        assertTrue(h.settings.settings.value.needsAuthorization)
        assertEquals(0, h.backups.creations)
        assertEquals(0, h.remote.uploads)
    }

    @Test fun accountChangedWhileAuthorizingDoesNotReceiveOldAccountBackup() = runTest {
        val h = Harness(this)
        h.onAuthorize = { h.settings.update { it.copy(accountEmail = "another@example.com") } }
        assertEquals(AutomaticBackupOutcome.COMPLETED, h.runner.run(0))
        assertEquals(0, h.backups.creations)
        assertEquals(0, h.remote.uploads)
        assertFalse(h.settings.settings.value.needsAuthorization)
    }

    @Test fun changingLibraryDuringPreparationDiscardsStaleArchive() = runTest {
        val h = Harness(this)
        h.backups.onCreate = {
            h.documents.state.value = h.documents.state.value.map { it.copy(name = "Renamed") }
        }
        assertEquals(AutomaticBackupOutcome.RETRY, h.runner.run(0))
        assertEquals(0, h.remote.uploads)
        assertFalse(h.backups.lastArchive!!.exists())
        assertNull(h.settings.settings.value.lastSnapshotFingerprint)
    }

    @Test fun cancellationReleasesSharedGateAndDeletesArchiveWithoutRecordingSuccess() = runTest {
        val h = Harness(this)
        val uploading = CompletableDeferred<Unit>()
        h.remote.onUpload = {
            uploading.complete(Unit)
            awaitCancellation()
        }
        val job = launch { h.runner.run(0) }
        uploading.await()
        assertTrue(h.gate.automaticRunning.value)
        job.cancelAndJoin()
        assertFalse(h.gate.automaticRunning.value)
        assertFalse(h.gate.mutex.isLocked)
        assertFalse(h.backups.lastArchive!!.exists())
        assertNull(h.settings.settings.value.lastBackupAt)
    }

    @Test fun manualOperationGateDelaysAutomaticWork() = runTest {
        val h = Harness(this)
        h.gate.mutex.lock()
        val job = launch { h.runner.run(0) }
        runCurrent()
        assertEquals(0, h.authorizationCalls)
        assertFalse(h.gate.automaticRunning.value)
        h.gate.mutex.unlock()
        job.join()
        assertEquals(1, h.remote.uploads)
    }

    @Test fun tagsDoNotChangeFingerprintButPdfChangesDo() = runTest {
        val h = Harness(this)
        val initial = h.fingerprint.current()
        h.documents.state.value = h.documents.state.value.map { it.copy(tagId = 99, thumbnailUri = "preview") }
        assertEquals(initial, h.fingerprint.current())
        h.pdf.setLastModified(h.pdf.lastModified() + 10_000L)
        val modified = h.fingerprint.current()
        assertNotEquals(initial, modified)
        h.documents.state.value = h.documents.state.value.map { it.copy(protectionKeyId = "protected") }
        assertNotEquals(modified, h.fingerprint.current())
    }

    private inner class Harness(scope: TestScope) {
        val directory = temporary.newFolder()
        val pdf = File(directory, "document.pdf").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val documents = FakeDocuments().apply {
            state.value = listOf(DocumentModel(
                id = 1L, name = "Generated", pdfUri = pdf.toURI().toString(),
                pageCount = 1, createdAt = 100L, sizeBytes = pdf.length(),
            ))
        }
        val settings = FakeSettings()
        val backups = FakeBackups(directory)
        val remote = FakeRemote()
        val gate = BackupOperationGate()
        val fingerprint = BackupSnapshotFingerprint(documents, StandardTestDispatcher(scope.testScheduler))
        var authorizationCalls = 0
        var authorizedEmail: String? = null
        var authorizationFailure: Exception? = null
        var invalidatedToken: String? = null
        var onAuthorize: suspend () -> Unit = {}
        val runner = AutomaticBackupRunner(
            settings, backups, remote,
            object : BackupAuthorizationProvider {
                override suspend fun accessToken(accountEmail: String): String {
                    authorizationCalls++
                    authorizedEmail = accountEmail
                    onAuthorize()
                    authorizationFailure?.let { throw it }
                    return "ephemeral-test-token"
                }
                override suspend fun invalidate(accessToken: String) {
                    invalidatedToken = accessToken
                }
            },
            fingerprint, gate, now = { 5_000L },
        )
    }

    private class FakeSettings : BackupSettingsRepository {
        override val settings = MutableStateFlow(BackupSettings(accountEmail = "owner@example.com", automaticEnabled = true))
        override suspend fun update(transform: (BackupSettings) -> BackupSettings) {
            settings.value = transform(settings.value)
        }
    }

    private class FakeBackups(private val directory: File) : DocumentBackupRepository {
        var creations = 0
        var lastArchive: File? = null
        var onCreate: suspend () -> Unit = {}
        override suspend fun createBackup(onProgress: (DocumentBackupProgress) -> Unit): PreparedDocumentBackup {
            creations++
            onCreate()
            val file = File.createTempFile("snapshot", ".zip", directory).apply { writeBytes(byteArrayOf(1, 2, 3)) }
            lastArchive = file
            return PreparedDocumentBackup(file, 1, 0, 0)
        }
        override suspend fun discard(file: File) { check(file.delete()) }
        override suspend fun createRestoreFile(): File = error("unused")
        override suspend fun restoreBackup(file: File, onProgress: (DocumentBackupProgress) -> Unit): DocumentBackupRestoreResult =
            error("unused")
    }

    private class FakeRemote : DriveBackupRemote {
        var uploads = 0
        var uploadedName: String? = null
        var email = "owner@example.com"
        var failure: Exception? = null
        var onUpload: suspend () -> Unit = {}
        override suspend fun accountEmail(accessToken: String) = email
        override suspend fun upload(
            accessToken: String, archive: File, name: String, onProgress: (Long, Long) -> Unit,
        ): DriveBackupEntry {
            uploads++
            uploadedName = name
            onUpload()
            failure?.let { throw it }
            return DriveBackupEntry("backup", name, 5_000L, archive.length())
        }
        override suspend fun listBackups(accessToken: String): List<DriveBackupEntry> = error("unused")
        override suspend fun download(accessToken: String, entry: DriveBackupEntry, destination: File, onProgress: (Long, Long) -> Unit) =
            error("unused")
    }

    private class FakeDocuments : DocumentRepository {
        val state = MutableStateFlow<List<DocumentModel>>(emptyList())
        override fun observeDocuments() = state
        override fun observeTrashedDocuments() = MutableStateFlow<List<DocumentModel>>(emptyList())
        override suspend fun findById(id: Long): DocumentModel? = state.value.find { it.id == id }
        override suspend fun prepareScan(pageUris: List<String>): ScannedDocument = error("unused")
        override suspend fun discardScan(scan: ScannedDocument) = Unit
        override suspend fun create(scan: ScannedDocument, name: String, tagId: Long?): Long = error("unused")
        override suspend fun update(id: Long, name: String, tagId: Long?) = error("unused")
        override suspend fun replaceContent(id: Long, scan: ScannedDocument, allowProtectionRemoval: Boolean): DocumentModel = error("unused")
        override suspend fun moveToTrash(id: Long) = error("unused")
        override suspend fun restoreFromTrash(id: Long) = error("unused")
        override suspend fun deletePermanently(id: Long) = error("unused")
        override suspend fun delete(document: DocumentModel) = error("unused")
        override suspend fun export(document: DocumentModel, targetUri: String) = error("unused")
        override suspend fun prepareShare(document: DocumentModel): String = error("unused")
    }
}
