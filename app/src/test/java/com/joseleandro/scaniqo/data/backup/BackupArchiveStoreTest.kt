package com.joseleandro.scaniqo.data.backup

import com.joseleandro.scaniqo.domain.model.BackupHistoryEntry
import com.joseleandro.scaniqo.domain.model.DriveBackupEntry
import com.joseleandro.scaniqo.domain.model.DriveBackupException
import com.joseleandro.scaniqo.domain.repository.BackupHistoryRepository
import com.joseleandro.scaniqo.domain.repository.DriveBackupRemote
import java.io.File
import java.io.IOException
import java.util.Locale
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BackupArchiveStoreTest {
    @get:Rule val temporary = TemporaryFolder()
    private val account = "owner@example.com"
    private val token = "ephemeral-token"

    @Test fun loadingCurrentForRestoreDoesNotDependOnHistoryOrOldCopyCleanup() = runTest {
        val h = Harness()
        val latest = entry("latest", 300)
        h.remote.backups = listOf(entry("old", 100), latest, entry("middle", 200))
        h.history.failureAtRecord = 1
        h.remote.deleteFailure = DriveBackupException.Remote(403)

        assertEquals(latest, h.store.loadCurrent(token, " OWNER@example.com "))
        assertEquals(latest, h.store.currentBackups.value[account])
        assertEquals(0, h.history.records)
        assertTrue(h.history.entries.value.isEmpty())
        assertTrue(h.remote.deleted.isEmpty())
        assertEquals(3, h.remote.backups.size)
    }

    @Test fun canceledRestoreListingDoesNotPublishItsLateResult() = runTest {
        val h = Harness()
        val previous = entry("previous", 100)
        h.remote.backups = listOf(previous)
        h.store.loadCurrent(token, account)
        h.remote.backups = listOf(entry("latest", 200), previous)
        h.remote.onList = { currentCoroutineContext().cancel() }

        val job = launch { h.store.loadCurrent(token, account) }
        job.join()
        assertTrue(job.isCancelled)
        assertEquals(previous, h.store.currentBackups.value[account])
        assertEquals(0, h.history.records)
        assertTrue(h.remote.deleted.isEmpty())
    }

    @Test fun loadingAnEmptyDriveClearsCurrentWithoutChangingHistory() = runTest {
        val h = Harness()
        val previous = entry("previous", 100)
        h.remote.backups = listOf(previous)
        h.store.synchronize(token, account)
        val historyBefore = h.history.entries.value
        val recordsBefore = h.history.records
        h.remote.backups = emptyList()

        assertNull(h.store.loadCurrent(token, account))
        assertTrue(h.store.currentBackups.value.containsKey(account))
        assertNull(h.store.currentBackups.value[account])
        assertEquals(recordsBefore, h.history.records)
        assertEquals(historyBefore, h.history.entries.value)
    }

    @Test fun migrationPersistsEveryRemoteRecordBeforeRemovingOldCopies() = runTest {
        val h = Harness()
        val old = entry("old", 100)
        val current = entry("current", 300)
        val middle = entry("middle", 200)
        h.remote.backups = listOf(old, current, middle, old)
        h.remote.onDelete = {
            assertEquals(setOf("old", "current", "middle"), h.history.entries.value.getValue(account).map { it.id }.toSet())
        }

        assertEquals(current, h.store.synchronize(token, account))
        assertEquals(listOf(current), h.remote.backups)
        assertEquals(listOf("middle", "old"), h.remote.deleted)
        assertEquals(3, h.history.entries.value.getValue(account).size)
    }

    @Test fun confirmedCurrentCopyIsPublishedIndependentlyOfLocalHistory() = runTest {
        val h = Harness()
        h.remote.backups = listOf(entry("current", 100))
        h.store.synchronize(token, " OWNER@example.com ")
        assertEquals(entry("current", 100), h.store.currentBackups.value[account])
        h.upload()
        assertEquals("new", h.store.currentBackups.value[account]?.id)
        h.remote.backups = emptyList()
        h.store.synchronize(token, account)
        assertTrue(h.store.currentBackups.value.containsKey(account))
        assertNull(h.store.currentBackups.value[account])
        assertEquals(2, h.history.entries.value.getValue(account).size)
    }

    @Test fun failedListingDoesNotPublishAnEmptyCurrentCopy() = runTest {
        val h = Harness()
        h.remote.backups = listOf(entry("current", 100))
        h.store.synchronize(token, account)
        h.remote.listFailure = DriveBackupException.Network()
        assertTrue(runCatching { h.store.synchronize(token, account) }.exceptionOrNull() is DriveBackupException.Network)
        assertEquals(entry("current", 100), h.store.currentBackups.value[account])
    }

    @Test fun canceledListResponseDoesNotPublishCurrentOrImportHistory() = runTest {
        val h = Harness()
        h.remote.backups = listOf(entry("current", 100), entry("old", 50))
        h.remote.onList = { currentCoroutineContext().cancel() }
        val job = launch { h.store.synchronize(token, account) }
        job.join()
        assertTrue(job.isCancelled)
        assertTrue(h.store.currentBackups.value.isEmpty())
        assertTrue(h.history.entries.value.isEmpty())
        assertTrue(h.remote.deleted.isEmpty())
    }

    @Test fun canceledPreUploadListingDoesNotPublishOrStartUpload() = runTest {
        val h = Harness()
        h.remote.backups = listOf(entry("old", 100))
        h.remote.onList = { currentCoroutineContext().cancel() }
        val job = launch { h.upload() }
        job.join()
        assertTrue(job.isCancelled)
        assertTrue(h.store.currentBackups.value.isEmpty())
        assertTrue(h.history.entries.value.isEmpty())
        assertEquals(0, h.remote.uploads)
    }

    @Test fun equalTimestampsUseStableIdTieBreakAndNeverDeleteTheSelectedCurrent() = runTest {
        val h = Harness()
        h.remote.backups = listOf(entry("a", 100), entry("z", 100))
        assertEquals("z", h.store.synchronize(token, account)?.id)
        assertEquals(listOf("a"), h.remote.deleted)
    }

    @Test fun emptyDriveDoesNotEraseLocalHistoricalRecords() = runTest {
        val h = Harness()
        h.history.record(account, listOf(historyEntry("old", 100)))
        assertNull(h.store.synchronize(token, account))
        assertEquals(listOf(historyEntry("old", 100)), h.history.entries.value[account])
        assertTrue(h.remote.deleted.isEmpty())
    }

    @Test fun historyPersistenceFailurePreventsAnyRemoteDeletion() = runTest {
        val h = Harness()
        h.remote.backups = listOf(entry("old", 100), entry("current", 200))
        h.history.failureAtRecord = 1
        assertTrue(runCatching { h.store.synchronize(token, account) }.exceptionOrNull() is IOException)
        assertEquals(2, h.remote.backups.size)
        assertTrue(h.remote.deleted.isEmpty())
    }

    @Test fun unreadableHistoryPreventsCreatingYetAnotherRemoteCopy() = runTest {
        val h = Harness()
        h.remote.backups = listOf(entry("old", 100))
        h.history.failureAtRecord = 1
        assertTrue(runCatching { h.upload() }.exceptionOrNull() is IOException)
        assertEquals(0, h.remote.uploads)
        assertEquals(listOf(entry("old", 100)), h.remote.backups)
    }

    @Test fun uploadRetainsThePreviousCopyUntilNewArchiveAndHistoryAreConfirmed() = runTest {
        val h = Harness()
        val old = entry("old", 100)
        h.remote.backups = listOf(old)
        h.remote.onUpload = {
            assertEquals(listOf(old), h.remote.backups)
            assertEquals(listOf(historyEntry("old", 100)), h.history.entries.value[account])
        }
        var callbackInvoked = false
        h.remote.onDelete = {
            assertTrue(callbackInvoked)
            assertEquals(7, h.history.entries.value.getValue(account).first { it.id == "new" }.documentCount)
        }

        val uploaded = h.upload {
            callbackInvoked = true
            assertEquals(2, h.remote.backups.size)
            assertEquals("new", it.id)
        }
        assertTrue(callbackInvoked)
        assertEquals(uploaded, h.remote.backups.single())
        assertEquals(listOf("old"), h.remote.deleted)
        assertEquals(2, h.history.entries.value.getValue(account).size)
    }

    @Test fun failedUploadPreservesLastGoodCopyAndDoesNotReportSuccess() = runTest {
        val h = Harness()
        val old = entry("old", 100)
        h.remote.backups = listOf(old)
        h.remote.uploadFailure = DriveBackupException.Network()
        var called = false
        assertTrue(runCatching { h.upload { called = true } }.exceptionOrNull() is DriveBackupException.Network)
        assertFalse(called)
        assertEquals(listOf(old), h.remote.backups)
        assertTrue(h.remote.deleted.isEmpty())
    }

    @Test fun failedPostUploadHistoryCommitPreservesBothCopiesForReconciliation() = runTest {
        val h = Harness()
        h.remote.backups = listOf(entry("old", 100))
        h.history.failureAtRecord = 2
        var called = false
        assertTrue(runCatching { h.upload { called = true } }.exceptionOrNull() is IOException)
        assertTrue(called)
        assertEquals(setOf("old", "new"), h.remote.backups.map { it.id }.toSet())
        assertTrue(h.remote.deleted.isEmpty())
        h.history.failureAtRecord = null
        assertEquals("new", h.store.synchronize(token, account)?.id)
        assertEquals(2, h.history.entries.value.getValue(account).size)
        assertEquals(listOf("new"), h.remote.backups.map { it.id })
    }

    @Test fun failedSuccessCallbackPreservesBothCopies() = runTest {
        val h = Harness()
        h.remote.backups = listOf(entry("old", 100))
        assertTrue(runCatching { h.upload { throw IOException("settings unavailable") } }.exceptionOrNull() is IOException)
        assertEquals(2, h.remote.backups.size)
        assertEquals(1, h.history.entries.value.getValue(account).size)
        assertTrue(h.remote.deleted.isEmpty())
    }

    @Test fun cleanupFailureCanRetryWithoutUploadingAnotherArchive() = runTest {
        val h = Harness()
        h.remote.backups = listOf(entry("old", 100))
        h.remote.deleteFailure = DriveBackupException.Network()
        var committed: DriveBackupEntry? = null
        assertTrue(runCatching { h.upload { committed = it } }.exceptionOrNull() is DriveBackupException.Network)
        assertEquals("new", committed?.id)
        assertEquals(2, h.remote.backups.size)

        h.remote.deleteFailure = null
        assertEquals(committed, h.store.synchronize(token, account))
        assertEquals(1, h.remote.uploads)
        assertEquals(listOf("new"), h.remote.backups.map { it.id })
        assertEquals(7, h.history.entries.value.getValue(account).first { it.id == "new" }.documentCount)
    }

    @Test fun canceledUploadKeepsPreviousCopyAndDoesNotCreateHistoryForIncompleteBackup() = runTest {
        val h = Harness()
        h.remote.backups = listOf(entry("old", 100))
        val uploading = CompletableDeferred<Unit>()
        h.remote.onUpload = {
            uploading.complete(Unit)
            awaitCancellation()
        }
        val job = launch { h.upload() }
        uploading.await()
        job.cancelAndJoin()
        assertEquals(listOf("old"), h.remote.backups.map { it.id })
        assertEquals(listOf("old"), h.history.entries.value.getValue(account).map { it.id })
        assertTrue(h.remote.deleted.isEmpty())
    }

    @Test fun cancellationAfterConfirmedUploadPersistsSuccessAndDefersCleanupUntilSync() = runTest {
        val h = Harness()
        h.remote.backups = listOf(entry("old", 100))
        h.remote.onUpload = { currentCoroutineContext().cancel() }
        var called = false
        val job = launch { h.upload { called = true } }
        job.join()
        assertTrue(job.isCancelled)
        assertTrue(called)
        assertEquals(setOf("old", "new"), h.remote.backups.map { it.id }.toSet())
        assertEquals(2, h.history.entries.value.getValue(account).size)
        assertEquals("new", h.store.currentBackups.value[account]?.id)
        assertTrue(h.remote.deleted.isEmpty())
        assertEquals("new", h.store.synchronize(token, account)?.id)
        assertEquals(listOf("new"), h.remote.backups.map { it.id })
    }

    @Test fun confirmedUploadIsProtectedEvenIfItsTimestampIsBehindAnOldEntry() = runTest {
        val h = Harness()
        h.remote.backups = listOf(entry("old", 9_000))
        h.remote.uploadedTime = 500
        assertEquals("new", h.upload().id)
        assertEquals(listOf("new"), h.remote.backups.map { it.id })
    }

    @Test fun reusingAnIdNeverDeletesTheConfirmedUpload() = runTest {
        val h = Harness()
        h.remote.backups = listOf(entry("new", 100))
        h.upload()
        assertTrue(h.remote.deleted.isEmpty())
        assertEquals(7, h.history.entries.value.getValue(account).single().documentCount)
    }

    private inner class Harness {
        val history = FakeHistory()
        val remote = FakeRemote()
        val store = BackupArchiveStore(remote, history)
        val archive = temporary.newFile().apply { writeBytes(byteArrayOf(1, 2, 3)) }
        suspend fun upload(onUploaded: suspend (DriveBackupEntry) -> Unit = {}) =
            store.upload(token, account, archive, "new.zip", 7, onUploaded = onUploaded)
    }

    private class FakeHistory : BackupHistoryRepository {
        override val entries = MutableStateFlow<Map<String, List<BackupHistoryEntry>>>(emptyMap())
        var records = 0
        var failureAtRecord: Int? = null
        override suspend fun record(accountEmail: String, entries: List<BackupHistoryEntry>) {
            records++
            if (records == failureAtRecord) throw IOException("history unavailable")
            val key = accountEmail.trim().lowercase(Locale.ROOT)
            val merged = this.entries.value[key].orEmpty().associateBy { it.id }.toMutableMap()
            entries.forEach { incoming ->
                merged[incoming.id] = incoming.copy(documentCount = incoming.documentCount ?: merged[incoming.id]?.documentCount)
            }
            this.entries.value += key to merged.values.sortedByDescending { it.createdAtMillis }
        }
        override suspend fun delete(accountEmail: String, id: String) = error("unused")
    }

    private class FakeRemote : DriveBackupRemote {
        var backups = emptyList<DriveBackupEntry>()
        val deleted = mutableListOf<String>()
        var uploads = 0
        var uploadedTime = 1_000L
        var uploadFailure: Exception? = null
        var deleteFailure: Exception? = null
        var listFailure: Exception? = null
        var onUpload: suspend () -> Unit = {}
        var onDelete: suspend () -> Unit = {}
        var onList: suspend () -> Unit = {}
        override suspend fun listBackups(accessToken: String): List<DriveBackupEntry> {
            onList()
            listFailure?.let { throw it }
            return backups
        }
        override suspend fun accountEmail(accessToken: String) = "owner@example.com"
        override suspend fun upload(
            accessToken: String,
            archive: File,
            name: String,
            onProgress: (Long, Long) -> Unit,
        ): DriveBackupEntry {
            uploads++
            onUpload()
            uploadFailure?.let { throw it }
            return DriveBackupEntry("new", name, uploadedTime, archive.length()).also { uploaded ->
                backups = backups.filterNot { it.id == uploaded.id } + uploaded
            }
        }
        override suspend fun delete(accessToken: String, entry: DriveBackupEntry) {
            onDelete()
            deleteFailure?.let { throw it }
            deleted += entry.id
            backups = backups.filterNot { it.id == entry.id }
        }
        override suspend fun download(
            accessToken: String, entry: DriveBackupEntry, destination: File, onProgress: (Long, Long) -> Unit,
        ) = error("unused")
    }

    private fun entry(id: String, time: Long) = DriveBackupEntry(id, "$id.zip", time, 100)
    private fun historyEntry(id: String, time: Long) = BackupHistoryEntry(id, "$id.zip", time, 100)
}
