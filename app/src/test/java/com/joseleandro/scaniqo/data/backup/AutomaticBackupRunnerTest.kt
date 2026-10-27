package com.joseleandro.scaniqo.data.backup

import com.joseleandro.scaniqo.domain.model.BackupHistoryEntry
import com.joseleandro.scaniqo.domain.model.BackupAutomaticProblem
import com.joseleandro.scaniqo.domain.model.BackupSettings
import com.joseleandro.scaniqo.domain.model.DocumentBackupProgress
import com.joseleandro.scaniqo.domain.model.DocumentBackupRestoreResult
import com.joseleandro.scaniqo.domain.model.DocumentModel
import com.joseleandro.scaniqo.domain.model.DriveBackupEntry
import com.joseleandro.scaniqo.domain.model.DriveBackupException
import com.joseleandro.scaniqo.domain.model.PreparedDocumentBackup
import com.joseleandro.scaniqo.domain.model.ScannedDocument
import com.joseleandro.scaniqo.domain.repository.BackupHistoryRepository
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

    @Test fun unchangedPdfSnapshotSynchronizesRetentionWithoutUploadingAgain() = runTest {
        val h = Harness(this)
        h.remote.listed = listOf(DriveBackupEntry("current", "current.zip", 100L, 3L))
        val snapshot = h.fingerprint.current()
        h.settings.update { it.copy(lastBackupId = "current", lastBackupAt = 100L, lastBackupBytes = 3L, lastSnapshotFingerprint = snapshot) }
        assertEquals(AutomaticBackupOutcome.COMPLETED, h.runner.run(0))
        assertEquals(1, h.authorizationCalls)
        assertEquals(1, h.remote.lists)
        assertEquals(0, h.remote.uploads)
        assertEquals(0, h.backups.creations)
        assertEquals(100L, h.settings.settings.value.lastBackupAt)
    }

    @Test fun emptyLibrarySynchronizesRetentionWithoutOverwritingLastSuccessfulBackup() = runTest {
        val h = Harness(this)
        h.documents.state.value = emptyList()
        h.settings.update { it.copy(lastBackupAt = 99L, lastSnapshotFingerprint = "previous") }
        assertEquals(AutomaticBackupOutcome.COMPLETED, h.runner.run(0))
        assertEquals(1, h.authorizationCalls)
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
            assertEquals("backup-1", lastBackupId)
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
        val timestamp = h.pdf.lastModified()
        assertTrue(h.pdf.setLastModified(timestamp + 10_000L))
        assertEquals(initial, h.fingerprint.current())
        h.pdf.writeBytes(byteArrayOf(3, 2, 1))
        assertTrue(h.pdf.setLastModified(timestamp))
        val modified = h.fingerprint.current()
        assertNotEquals(initial, modified)
        h.documents.state.value = h.documents.state.value.map { it.copy(protectionKeyId = "protected") }
        assertNotEquals(modified, h.fingerprint.current())
    }


    @Test fun unchangedSnapshotMigratesExistingArchivesIntoLocalHistory() = runTest {
        val h = Harness(this)
        val old = DriveBackupEntry("old", "old.zip", 100L, 3L)
        val latest = DriveBackupEntry("latest", "latest.zip", 200L, 3L)
        h.remote.listed = listOf(old, latest)
        val snapshot = h.fingerprint.current()
        h.settings.update { it.copy(lastBackupId = latest.id, lastBackupAt = latest.modifiedTimeMillis, lastBackupBytes = latest.sizeBytes, lastSnapshotFingerprint = snapshot) }

        assertEquals(AutomaticBackupOutcome.COMPLETED, h.runner.run(0))
        assertEquals(listOf(latest), h.remote.listed)
        assertEquals(listOf(old), h.remote.deleted)
        assertEquals(listOf("latest", "old"), h.history.entries.value["owner@example.com"]!!.map { it.id })
        assertEquals(0, h.remote.uploads)
        assertEquals(0, h.backups.creations)
    }

    @Test fun failedPostUploadRetentionRetriesWithoutCreatingDuplicateBackup() = runTest {
        val h = Harness(this)
        val old = DriveBackupEntry("old", "old.zip", 100L, 3L)
        h.remote.listed = listOf(old)
        h.remote.deleteFailure = DriveBackupException.Network()
        val snapshot = h.fingerprint.current()

        assertEquals(AutomaticBackupOutcome.RETRY, h.runner.run(0))
        assertEquals(snapshot, h.settings.settings.value.lastSnapshotFingerprint)
        assertEquals(1, h.remote.uploads)
        assertEquals(2, h.history.entries.value["owner@example.com"]!!.size)
        assertEquals(BackupAutomaticProblem.NETWORK, h.settings.settings.value.lastAutomaticProblem)

        h.remote.deleteFailure = null
        assertEquals(AutomaticBackupOutcome.COMPLETED, h.runner.run(1))
        assertEquals(1, h.remote.uploads)
        assertEquals(1, h.backups.creations)
        assertEquals(1, h.remote.listed.size)
        assertEquals(listOf(old), h.remote.deleted)
        assertNull(h.settings.settings.value.lastAutomaticProblem)
    }

    @Test fun unchangedSnapshotRecreatesBackupWhenDriveCopyWasRemoved() = runTest {
        val h = Harness(this)
        val snapshot = h.fingerprint.current()
        h.settings.update { it.copy(lastBackupId = "removed-file", lastSnapshotFingerprint = snapshot) }

        assertEquals(AutomaticBackupOutcome.COMPLETED, h.runner.run(0))
        assertEquals(1, h.remote.uploads)
        assertEquals(1, h.remote.listed.size)
        assertEquals(snapshot, h.settings.settings.value.lastSnapshotFingerprint)
    }

    @Test fun anotherDeviceReplacingCurrentArchiveRequiresNewBackupOfUnchangedLocalSnapshot() = runTest {
        val h = Harness(this)
        val snapshot = h.fingerprint.current()
        val otherDevice = DriveBackupEntry("another-device", "other.zip", 4_000L, 3L)
        h.remote.listed = listOf(otherDevice)
        h.settings.update { it.copy(lastBackupId = "my-previous-backup", lastSnapshotFingerprint = snapshot) }

        assertEquals(AutomaticBackupOutcome.COMPLETED, h.runner.run(0))
        assertEquals(1, h.remote.uploads)
        assertEquals("backup-1", h.settings.settings.value.lastBackupId)
        assertEquals(snapshot, h.settings.settings.value.lastSnapshotFingerprint)
        assertEquals(listOf(otherDevice), h.remote.deleted)
        assertEquals("backup-1", h.remote.listed.single().id)
    }

    @Test fun legacySnapshotWithoutRemoteIdUploadsOnceThenDeduplicates() = runTest {
        val h = Harness(this)
        val snapshot = h.fingerprint.current()
        h.remote.listed = listOf(DriveBackupEntry("legacy", "legacy.zip", 100L, 3L))
        h.settings.update { it.copy(lastSnapshotFingerprint = snapshot) }
        assertNull(h.settings.settings.value.lastBackupId)

        assertEquals(AutomaticBackupOutcome.COMPLETED, h.runner.run(0))
        assertEquals("backup-1", h.settings.settings.value.lastBackupId)
        assertEquals(1, h.remote.uploads)
        assertEquals(AutomaticBackupOutcome.COMPLETED, h.runner.run(0))
        assertEquals(1, h.remote.uploads)
        assertEquals("backup-1", h.remote.listed.single().id)
    }

    @Test fun localHistoryFailurePreventsPruningAndPreservesPreviousSuccess() = runTest {
        val h = Harness(this)
        val old = DriveBackupEntry("old", "old.zip", 100L, 3L)
        val latest = DriveBackupEntry("latest", "latest.zip", 200L, 3L)
        h.remote.listed = listOf(old, latest)
        h.settings.update { it.copy(lastBackupAt = 99L) }
        h.history.failure = java.io.IOException("Disk full")

        assertEquals(AutomaticBackupOutcome.COMPLETED, h.runner.run(0))
        assertEquals(BackupAutomaticProblem.LOCAL_STORAGE, h.settings.settings.value.lastAutomaticProblem)
        assertEquals(99L, h.settings.settings.value.lastBackupAt)
        assertEquals(listOf(old, latest), h.remote.listed)
        assertTrue(h.remote.deleted.isEmpty())
        assertEquals(0, h.remote.uploads)
        assertEquals(0, h.backups.creations)
    }

    @Test fun repeatedAutomaticBackupPreservesTimestampAndDeletedHistory() = runTest {
        val h = Harness(this)
        assertEquals(AutomaticBackupOutcome.COMPLETED, h.runner.run(0))
        val saved = h.settings.settings.value
        h.history.delete("owner@example.com", h.remote.listed.single().id)

        assertEquals(AutomaticBackupOutcome.COMPLETED, h.runner.run(0))

        assertEquals(1, h.remote.uploads)
        assertEquals(1, h.backups.creations)
        assertEquals(saved, h.settings.settings.value)
        assertTrue(h.history.entries.value["owner@example.com"]!!.isEmpty())
    }

    @Test fun sameLengthChangedPdfWithOriginalTimestampTriggersAutomaticBackup() = runTest {
        val h = Harness(this)
        assertEquals(AutomaticBackupOutcome.COMPLETED, h.runner.run(0))
        val previousSnapshot = h.settings.settings.value.lastSnapshotFingerprint
        val timestamp = h.pdf.lastModified()
        h.pdf.writeBytes(byteArrayOf(3, 2, 1))
        assertTrue(h.pdf.setLastModified(timestamp))

        assertEquals(AutomaticBackupOutcome.COMPLETED, h.runner.run(0))

        assertEquals(2, h.remote.uploads)
        assertEquals(2, h.backups.creations)
        assertNotEquals(previousSnapshot, h.settings.settings.value.lastSnapshotFingerprint)
    }

    @Test fun timestampAndPreviewOnlyChangesDoNotCreateAutomaticBackup() = runTest {
        val h = Harness(this)
        assertEquals(AutomaticBackupOutcome.COMPLETED, h.runner.run(0))
        val saved = h.settings.settings.value
        val recorded = h.history.entries.value
        assertTrue(h.pdf.setLastModified(h.pdf.lastModified() + 10_000L))
        h.documents.state.value = h.documents.state.value.map { it.copy(tagId = 99, thumbnailUri = "new-preview") }

        assertEquals(AutomaticBackupOutcome.COMPLETED, h.runner.run(0))

        assertEquals(1, h.remote.uploads)
        assertEquals(1, h.backups.creations)
        assertEquals(saved, h.settings.settings.value)
        assertEquals(recorded, h.history.entries.value)
    }

    @Test fun overwrittenCloudFileWithSameIdRequiresAutomaticRepair() = runTest {
        val h = Harness(this)
        assertEquals(AutomaticBackupOutcome.COMPLETED, h.runner.run(0))
        h.remote.listed = listOf(h.remote.listed.single().copy(modifiedTimeMillis = 6_000L))

        assertEquals(AutomaticBackupOutcome.COMPLETED, h.runner.run(0))

        assertEquals(2, h.remote.uploads)
        h.remote.listed = listOf(h.remote.listed.single().copy(sizeBytes = 4L))

        assertEquals(AutomaticBackupOutcome.COMPLETED, h.runner.run(0))

        assertEquals(3, h.remote.uploads)
        assertEquals(3, h.backups.creations)
        assertEquals("backup-3", h.settings.settings.value.lastBackupId)
    }

    @Test fun documentChangesDuringUploadRequireAnotherAutomaticBackup() = runTest {
        val h = Harness(this)
        val initial = h.fingerprint.current()
        h.remote.onUpload = {
            h.documents.state.value = h.documents.state.value.map { it.copy(pageCount = it.pageCount + 1) }
        }

        assertEquals(AutomaticBackupOutcome.COMPLETED, h.runner.run(0))
        assertEquals(initial, h.settings.settings.value.lastSnapshotFingerprint)

        h.remote.onUpload = {}
        assertEquals(AutomaticBackupOutcome.COMPLETED, h.runner.run(0))

        assertEquals(2, h.remote.uploads)
        assertNotEquals(initial, h.settings.settings.value.lastSnapshotFingerprint)
    }

    @Test fun identicalLegacyArchiveMigratesBaselineWithoutUploadOrNewHistory() = runTest {
        val h = Harness(this)
        val current = DriveBackupEntry("legacy", "legacy.zip", 100L, 3L)
        h.remote.listed = listOf(current)
        h.remote.downloadArchive = h.snapshotArchive()
        h.settings.update { it.copy(
            lastBackupId = current.id, lastBackupAt = current.modifiedTimeMillis,
            lastBackupBytes = current.sizeBytes, lastSnapshotFingerprint = "metadata-v1",
        ) }
        h.history.record("owner@example.com", listOf(BackupHistoryEntry(current.id, current.name, current.modifiedTimeMillis, current.sizeBytes)))
        val recorded = h.history.entries.value
        var foregroundStarts = 0

        assertEquals(AutomaticBackupOutcome.COMPLETED, h.runner.run(0) { foregroundStarts++ })

        assertEquals(0, h.backups.creations)
        assertEquals(0, h.remote.uploads)
        assertEquals(1, h.remote.downloads)
        assertEquals(1, foregroundStarts)
        assertEquals(recorded, h.history.entries.value)
        assertEquals(100L, h.settings.settings.value.lastBackupAt)
        assertEquals(h.fingerprint.current(), h.settings.settings.value.lastSnapshotFingerprint)
        assertFalse(h.backups.lastRestoreFile!!.exists())

        assertEquals(AutomaticBackupOutcome.COMPLETED, h.runner.run(0) { foregroundStarts++ })
        assertEquals(1, h.remote.downloads)
        assertEquals(1, foregroundStarts)
        assertEquals(0, h.remote.uploads)
    }

    @Test fun identicalUnknownCloudArchiveEstablishesBaselineWithoutUploading() = runTest {
        val h = Harness(this)
        val current = DriveBackupEntry("current", "current.zip", 100L, 3L)
        h.remote.listed = listOf(current)
        h.remote.downloadArchive = h.snapshotArchive()

        assertEquals(AutomaticBackupOutcome.COMPLETED, h.runner.run(0))

        assertEquals(0, h.remote.uploads)
        assertEquals(0, h.backups.creations)
        assertEquals(1, h.remote.downloads)
        assertEquals(current.id, h.settings.settings.value.lastBackupId)
        assertEquals(current.modifiedTimeMillis, h.settings.settings.value.lastBackupAt)
        assertEquals(h.fingerprint.current(), h.settings.settings.value.lastSnapshotFingerprint)
        assertFalse(h.backups.lastRestoreFile!!.exists())
    }

    @Test fun failedLegacyComparisonRetriesWithoutUploadOrOverwritingBaseline() = runTest {
        val h = Harness(this)
        h.remote.listed = listOf(DriveBackupEntry("legacy", "legacy.zip", 100L, 3L))
        h.settings.update { it.copy(lastBackupAt = 100L, lastSnapshotFingerprint = "metadata-v1") }
        h.remote.downloadFailure = DriveBackupException.Network()

        assertEquals(AutomaticBackupOutcome.RETRY, h.runner.run(0))

        assertEquals(0, h.remote.uploads)
        assertEquals(0, h.backups.creations)
        assertEquals("metadata-v1", h.settings.settings.value.lastSnapshotFingerprint)
        assertEquals(100L, h.settings.settings.value.lastBackupAt)
        assertEquals(BackupAutomaticProblem.NETWORK, h.settings.settings.value.lastAutomaticProblem)
        assertFalse(h.backups.lastRestoreFile!!.exists())
    }

    @Test fun cancellingLegacyComparisonDiscardsDownloadWithoutUploading() = runTest {
        val h = Harness(this)
        h.remote.listed = listOf(DriveBackupEntry("legacy", "legacy.zip", 100L, 3L))
        val downloading = CompletableDeferred<Unit>()
        h.remote.onDownload = {
            downloading.complete(Unit)
            awaitCancellation()
        }
        val job = launch { h.runner.run(0) }
        downloading.await()

        job.cancelAndJoin()

        assertEquals(0, h.remote.uploads)
        assertEquals(0, h.backups.creations)
        assertNull(h.settings.settings.value.lastSnapshotFingerprint)
        assertNull(h.settings.settings.value.lastBackupAt)
        assertFalse(h.backups.lastRestoreFile!!.exists())
        assertFalse(h.gate.mutex.isLocked)
        assertFalse(h.gate.automaticRunning.value)
    }

    @Test fun foregroundRestrictionDefersLegacyComparisonBeforeDownload() = runTest {
        val h = Harness(this)
        h.remote.listed = listOf(DriveBackupEntry("legacy", "legacy.zip", 100L, 3L))

        assertEquals(AutomaticBackupOutcome.RETRY, h.runner.run(0) { throw AutomaticBackupDeferredException() })

        assertEquals(0, h.remote.downloads)
        assertEquals(0, h.remote.uploads)
        assertEquals(0, h.backups.creations)
        assertNull(h.backups.lastRestoreFile)
        assertNull(h.settings.settings.value.lastSnapshotFingerprint)
    }

    private class FakeHistory : BackupHistoryRepository {
        override val entries = MutableStateFlow<Map<String, List<BackupHistoryEntry>>>(emptyMap())
        private val deleted = mutableMapOf<String, MutableSet<String>>()
        var failure: Exception? = null
        override suspend fun record(accountEmail: String, entries: List<BackupHistoryEntry>) {
            failure?.let { throw it }
            val key = accountEmail.trim().lowercase(java.util.Locale.ROOT)
            val previous = this.entries.value[key].orEmpty()
            this.entries.value = this.entries.value + (key to (entries.map { entry ->
                entry.copy(documentCount = entry.documentCount ?: previous.find { it.id == entry.id }?.documentCount)
            } + previous).distinctBy { it.id }.filterNot { it.id in deleted[key].orEmpty() }.sortedByDescending { it.createdAtMillis })
        }
        override suspend fun delete(accountEmail: String, id: String) {
            val key = accountEmail.trim().lowercase(java.util.Locale.ROOT)
            deleted.getOrPut(key) { mutableSetOf() }.add(id)
            entries.value = entries.value + (key to entries.value[key].orEmpty().filterNot { it.id == id })
        }
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
        val remote = FakeRemote(directory)
        val gate = BackupOperationGate()
        val history = FakeHistory()
        val archives = BackupArchiveStore(remote, history)
        val fingerprint = BackupSnapshotFingerprint(documents, StandardTestDispatcher(scope.testScheduler))
        suspend fun snapshotArchive(): File {
            val sources = documents.state.value.map { document ->
                val source = temporary.newFolder()
                File(java.net.URI(document.pdfUri)).copyTo(File(source, "document.pdf"))
                BackupDocumentSource(
                    BackupDocument(
                        key = java.util.UUID.randomUUID().toString(), name = document.name,
                        pageCount = document.pageCount, createdAt = document.createdAt,
                        tagKey = null, protected = document.isProtected, editableProject = false,
                    ),
                    source, listOf("document.pdf"),
                )
            }
            return temporary.newFile().also { DocumentBackupArchive().write(it, sources) }
        }
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
            fingerprint, gate, archives, now = { 5_000L },
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
        var lastRestoreFile: File? = null
        val discarded = mutableListOf<File>()
        var onCreate: suspend () -> Unit = {}
        override suspend fun createBackup(onProgress: (DocumentBackupProgress) -> Unit): PreparedDocumentBackup {
            creations++
            onCreate()
            val file = File.createTempFile("snapshot", ".zip", directory).apply { writeBytes(byteArrayOf(1, 2, 3)) }
            lastArchive = file
            return PreparedDocumentBackup(file, 1, 0, 0)
        }
        override suspend fun discard(file: File) { discarded += file; check(file.delete()) }
        override suspend fun createRestoreFile(): File = File.createTempFile("comparison", ".zip", directory).also { lastRestoreFile = it }
        override suspend fun restoreBackup(file: File, onProgress: (DocumentBackupProgress) -> Unit): DocumentBackupRestoreResult =
            error("unused")
    }

    private class FakeRemote(private val directory: File) : DriveBackupRemote {
        var uploads = 0
        var uploadedName: String? = null
        var listed = emptyList<DriveBackupEntry>()
        var lists = 0
        var deleteFailure: Exception? = null
        val deleted = mutableListOf<DriveBackupEntry>()
        var email = "owner@example.com"
        var failure: Exception? = null
        var onUpload: suspend () -> Unit = {}
        var onDownload: suspend () -> Unit = {}
        var downloadArchive: File? = null
        var downloadFailure: Exception? = null
        var downloads = 0
        override suspend fun accountEmail(accessToken: String) = email
        override suspend fun upload(
            accessToken: String, archive: File, name: String, onProgress: (Long, Long) -> Unit,
        ): DriveBackupEntry {
            uploads++
            uploadedName = name
            onUpload()
            failure?.let { throw it }
            return DriveBackupEntry("backup-$uploads", name, 5_000L, archive.length()).also { listed = listOf(it) + listed }
        }
        override suspend fun listBackups(accessToken: String): List<DriveBackupEntry> {
            lists++
            return listed
        }
        override suspend fun delete(accessToken: String, entry: DriveBackupEntry) {
            deleteFailure?.let { throw it }
            deleted += entry
            listed = listed.filterNot { it.id == entry.id }
        }
        override suspend fun download(accessToken: String, entry: DriveBackupEntry, destination: File, onProgress: (Long, Long) -> Unit) {
            downloads++
            onDownload()
            downloadFailure?.let { throw it }
            val payload = downloadArchive
            if (payload != null) {
                payload.copyTo(destination, overwrite = true)
            } else {
                val source = File(directory, "different-remote").apply { mkdirs() }
                File(source, "document.pdf").writeText("Different remote PDF")
                DocumentBackupArchive().write(
                    destination,
                    listOf(BackupDocumentSource(
                        BackupDocument(
                            key = java.util.UUID.randomUUID().toString(), name = "Different remote",
                            pageCount = 1, createdAt = 1L, tagKey = null, protected = false, editableProject = false,
                        ),
                        source, listOf("document.pdf"),
                    )),
                )
            }
        }
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
