package com.joseleandro.scaniqo.data.backup

import com.joseleandro.scaniqo.domain.model.DocumentModel
import com.joseleandro.scaniqo.domain.model.ScannedDocument
import com.joseleandro.scaniqo.domain.repository.DocumentRepository
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class BackupSnapshotFingerprintTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun emptyAndTrashedLibrariesHaveNoSnapshot() = runTest {
        val h = Harness(this)
        h.documents.state.value = emptyList()
        assertNull(h.fingerprint.current())
        h.documents.state.value = listOf(h.document.copy(deletedAt = 1L))
        assertTrue(h.pdf.delete())
        assertNull(h.fingerprint.current())
    }

    @Test fun contentChangeWithSameSizeAndTimestampIsDetected() = runTest {
        val h = Harness(this)
        val timestamp = 1_700_000_000_000L
        assertTrue(h.pdf.setLastModified(timestamp))
        val previous = h.fingerprint.current()

        h.pdf.writeBytes(byteArrayOf(3, 2, 1))
        assertTrue(h.pdf.setLastModified(timestamp))

        assertEquals(3L, h.pdf.length())
        assertEquals(timestamp, h.pdf.lastModified())
        assertNotEquals(previous, h.fingerprint.current())
    }

    @Test fun touchingUnchangedFileDoesNotChangeSnapshot() = runTest {
        val h = Harness(this)
        assertTrue(h.pdf.setLastModified(1_700_000_000_000L))
        val previous = h.fingerprint.current()
        assertTrue(h.pdf.setLastModified(1_700_001_000_000L))
        assertEquals(previous, h.fingerprint.current())
    }

    @Test fun tagsThumbnailsStoredSizeAndDatabaseIdsDoNotChangeSnapshot() = runTest {
        val h = Harness(this)
        val previous = h.fingerprint.current()
        h.documents.state.value = listOf(h.document.copy(
            id = 50L, tagId = 9L, thumbnailUri = "file:/another-thumbnail.jpg", sizeBytes = 1_024L,
        ))
        assertEquals(previous, h.fingerprint.current())
    }

    @Test fun movingIdenticalPdfToAnotherPathDoesNotChangeSnapshot() = runTest {
        val h = Harness(this)
        val previous = h.fingerprint.current()
        val moved = File(temporary.newFolder(), "renamed-on-disk.pdf")
        assertTrue(h.pdf.renameTo(moved))
        h.documents.state.value = listOf(h.document.copy(pdfUri = moved.toURI().toString()))
        assertEquals(previous, h.fingerprint.current())
    }

    @Test fun namePageCountCreationDateAndProtectionAreIncluded() = runTest {
        val h = Harness(this)
        val previous = h.fingerprint.current()
        val changed = listOf(
            h.document.copy(name = "Renamed"),
            h.document.copy(pageCount = 2),
            h.document.copy(createdAt = 200L),
            h.document.copy(protectionKeyId = "protected-key"),
        )
        changed.forEach { document ->
            h.documents.state.value = listOf(document)
            assertNotEquals(previous, h.fingerprint.current())
        }
        val protected = h.fingerprint.current()
        h.documents.state.value = listOf(h.document.copy(protectionKeyId = "rotated-key"))
        assertEquals(protected, h.fingerprint.current())
    }

    @Test fun addingRemovingAndDuplicatingDocumentsChangesSnapshot() = runTest {
        val h = Harness(this)
        val previous = h.fingerprint.current()
        h.documents.state.value = listOf(h.document, h.document.copy(id = 2L))
        val duplicated = h.fingerprint.current()
        assertNotEquals(previous, duplicated)

        h.documents.state.value = listOf(h.document, h.document.copy(id = 2L, name = "Second"))
        assertNotEquals(duplicated, h.fingerprint.current())

        h.documents.state.value = listOf(h.document)
        assertEquals(previous, h.fingerprint.current())
    }

    @Test fun repositoryOrderDoesNotChangeSnapshot() = runTest {
        val h = Harness(this)
        val other = File(temporary.newFolder(), "document.pdf").apply { writeBytes(byteArrayOf(4, 5, 6)) }
        val second = h.document.copy(id = 2L, name = "Second", pdfUri = other.toURI().toString())
        h.documents.state.value = listOf(h.document, second)
        val previous = h.fingerprint.current()
        h.documents.state.value = listOf(second, h.document)
        assertEquals(previous, h.fingerprint.current())
    }

    @Test fun missingPdfFailsInsteadOfReportingUnchanged() = runTest {
        val h = Harness(this)
        assertTrue(h.pdf.delete())
        val failure = runCatching { h.fingerprint.current() }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
    }

    @Test fun metadataChangedDuringScanDoesNotReturnMixedSnapshot() = runTest {
        val h = Harness(this)
        h.documents.onObserve = { count ->
            if (count == 2) h.documents.state.value = listOf(h.document.copy(name = "Changed during scan"))
        }
        val failure = runCatching { h.fingerprint.current() }.exceptionOrNull()
        assertTrue(failure is IllegalStateException)
    }

    @Test fun fileChangedDuringScanDoesNotReturnMixedSnapshot() = runTest {
        val h = Harness(this)
        h.documents.onObserve = { count ->
            if (count == 2) h.pdf.appendBytes(byteArrayOf(4))
        }
        val failure = runCatching { h.fingerprint.current() }.exceptionOrNull()
        assertTrue(failure is IllegalStateException)
    }

    @Test fun unrelatedMetadataChangedDuringScanDoesNotInvalidateSnapshot() = runTest {
        val h = Harness(this)
        val previous = h.fingerprint.current()
        h.documents.onObserve = { count ->
            if (count == 4) h.documents.state.value = listOf(h.document.copy(tagId = 9L))
        }
        assertEquals(previous, h.fingerprint.current())
    }

    @Test fun cancellationIsPropagatedWithoutReturningFingerprint() = runTest {
        val h = Harness(this)
        var returned = false
        h.documents.onObserve = { currentCoroutineContext().cancel() }
        val job = launch {
            h.fingerprint.current()
            returned = true
        }
        job.join()
        assertTrue(job.isCancelled)
        assertFalse(returned)
    }

    @Test fun streamedLargeFileDetectsChangesAfterFirstBuffer() = runTest {
        val h = Harness(this)
        val block = ByteArray(64 * 1024) { (it % 127).toByte() }
        h.pdf.outputStream().use { output -> repeat(32) { output.write(block) } }
        val previous = h.fingerprint.current()
        val timestamp = h.pdf.lastModified()
        java.io.RandomAccessFile(h.pdf, "rw").use { file ->
            file.seek(h.pdf.length() - 1)
            file.write(255)
        }
        assertTrue(h.pdf.setLastModified(timestamp))
        assertNotEquals(previous, h.fingerprint.current())
    }

    @Test fun contentSignatureIsVersionedAndStableAcrossInstances() = runTest {
        val h = Harness(this)
        val fingerprint = h.fingerprint.current()
        assertTrue(requireNotNull(fingerprint).matches(Regex("pdf-content-v2:[a-f0-9]{64}")))
        val another = BackupSnapshotFingerprint(h.documents, StandardTestDispatcher(testScheduler))
        assertEquals(fingerprint, another.current())
    }

    @Test fun downloadedCurrentArchiveMatchesLocalContentWithoutExtracting() = runTest {
        val h = Harness(this)
        val target = temporary.newFile()
        DocumentBackupArchive().write(target, listOf(h.source()))
        val filesBefore = temporary.root.walkTopDown().map { it.relativeTo(temporary.root).path }.toList()

        assertEquals(h.fingerprint.current(), h.fingerprint.fromArchive(target))

        assertEquals(filesBefore, temporary.root.walkTopDown().map { it.relativeTo(temporary.root).path }.toList())
    }

    @Test fun legacyArchiveTagsAndEditingProjectsDoNotCreateAnotherBackup() = runTest {
        val h = Harness(this)
        File(h.pdf.parentFile, "sources.pdf").writeText("editing source")
        File(h.pdf.parentFile, "recipe.json").writeText("{}")
        File(h.pdf.parentFile, "thumbnail.jpg").writeText("thumbnail")
        val tag = BackupTag(java.util.UUID.randomUUID().toString(), "Old tag", 1, "folder")
        val source = h.source().let {
            it.copy(
                document = it.document.copy(tagKey = tag.key, editableProject = true),
                filenames = listOf("document.pdf", "sources.pdf", "recipe.json", "thumbnail.jpg"),
            )
        }
        val target = temporary.newFile()
        writeLegacyDocumentBackup(target, listOf(source), listOf(tag))

        assertEquals(h.fingerprint.current(), h.fingerprint.fromArchive(target))
    }

    @Test fun archiveComparisonDetectsChangedContentAndMetadata() = runTest {
        val h = Harness(this)
        val target = temporary.newFile()
        DocumentBackupArchive().write(target, listOf(h.source()))
        val archived = h.fingerprint.fromArchive(target)

        h.pdf.writeBytes(byteArrayOf(3, 2, 1))
        assertNotEquals(archived, h.fingerprint.current())
        h.pdf.writeBytes(byteArrayOf(1, 2, 3))
        h.documents.state.value = listOf(h.document.copy(name = "Changed name"))
        assertNotEquals(archived, h.fingerprint.current())
    }

    @Test fun archiveComparisonPreservesDuplicateDocuments() = runTest {
        val h = Harness(this)
        val target = temporary.newFile()
        DocumentBackupArchive().write(target, listOf(h.source(), h.source()))
        val archived = h.fingerprint.fromArchive(target)
        assertNotEquals(archived, h.fingerprint.current())

        h.documents.state.value = listOf(h.document, h.document.copy(id = 2L))
        assertEquals(archived, h.fingerprint.current())
    }

    @Test fun onlyCompleteVersionedSignaturesAreRecognized() {
        assertFalse(BackupSnapshotFingerprint.recognizes(null))
        assertFalse(BackupSnapshotFingerprint.recognizes("a".repeat(64)))
        assertFalse(BackupSnapshotFingerprint.recognizes("pdf-content-v2:"))
        assertFalse(BackupSnapshotFingerprint.recognizes("pdf-content-v2:" + "z".repeat(64)))
        assertTrue(BackupSnapshotFingerprint.recognizes("pdf-content-v2:" + "a".repeat(64)))
    }

    private inner class Harness(scope: TestScope) {
        val pdf = File(temporary.newFolder(), "document.pdf").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val document = DocumentModel(
            id = 1L, name = "Saved PDF", pdfUri = pdf.toURI().toString(),
            pageCount = 1, createdAt = 100L, sizeBytes = pdf.length(),
        )
        val documents = FakeDocuments().apply { state.value = listOf(document) }
        val fingerprint = BackupSnapshotFingerprint(documents, StandardTestDispatcher(scope.testScheduler))

        fun source() = BackupDocumentSource(
            BackupDocument(
                java.util.UUID.randomUUID().toString(), document.name, document.pageCount,
                document.createdAt, null, document.isProtected, editableProject = false,
            ),
            requireNotNull(pdf.parentFile), listOf("document.pdf"),
        )
    }

    private class FakeDocuments : DocumentRepository {
        val state = MutableStateFlow<List<DocumentModel>>(emptyList())
        private var observations = 0
        var onObserve: suspend (Int) -> Unit = {}
        override fun observeDocuments() = flow {
            observations++
            onObserve(observations)
            emit(state.value)
        }
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
