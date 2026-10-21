package com.joseleandro.scaniqo.data.backup

import androidx.compose.ui.graphics.Color
import com.joseleandro.scaniqo.data.pdf.PdfProjectRecipe
import com.joseleandro.scaniqo.data.pdf.PdfProtection
import com.joseleandro.scaniqo.data.pdf.RecipePage
import com.joseleandro.scaniqo.data.pdf.RecipeSignature
import com.joseleandro.scaniqo.domain.model.DocumentModel
import com.joseleandro.scaniqo.domain.model.PdfCropBounds
import com.joseleandro.scaniqo.domain.model.PdfPageLayout
import com.joseleandro.scaniqo.domain.model.ScannedDocument
import com.joseleandro.scaniqo.domain.model.TagModel
import com.joseleandro.scaniqo.domain.repository.DocumentRepository
import com.joseleandro.scaniqo.domain.repository.TagRepository
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import java.io.File
import java.net.URI
import java.util.Base64
import java.util.UUID
import java.util.zip.ZipFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AndroidDocumentBackupRepositoryTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun editableProjectRestoresAsANewCopyWithItsOriginalMetadataAndRelativeRecipe() = runBlocking {
        val fixture = fixture()
        val tag = TagModel(9L, "Recibos", Color(0xff556677.toInt()), "folder")
        fixture.tags.state.value = listOf(tag)
        val original = fixture.seed(editable = true, tagId = tag.id)
        val directory = File(URI(original.pdfUri)).parentFile!!
        val originalRecipe = File(directory, "recipe.json").readBytes()
        val backup = fixture.repository.createBackup()
        val result = fixture.repository.restoreBackup(backup.file)
        assertEquals(1, result.documentCount)
        assertEquals(1, result.editableProjectCount)
        val restored = fixture.documents.state.value.single { it.id != original.id }
        assertNotEquals(original.pdfUri, restored.pdfUri)
        assertEquals(original.createdAt, restored.createdAt)
        assertEquals(original.tagId, restored.tagId)
        assertEquals(1, fixture.tags.state.value.size)
        val newDirectory = File(URI(restored.pdfUri)).parentFile!!
        assertArrayEquals(originalRecipe, File(newDirectory, "recipe.json").readBytes())
        assertArrayEquals(File(directory, "sources.pdf").readBytes(), File(newDirectory, "sources.pdf").readBytes())
        assertTrue(File(newDirectory, "signature_1234.png").isFile)
        assertTrue(File(URI(original.pdfUri)).isFile)
        fixture.repository.discard(backup.file)
        assertFalse(backup.file.exists())
    }

    @Test fun backupIncludesOnlyActiveDocumentsAndPreservesTrashedProjectFiles() = runBlocking {
        val fixture = fixture()
        val active = fixture.seed()
        val trashed = fixture.seed(editable = true)
        fixture.documents.state.value = fixture.documents.state.value.map {
            if (it.id == trashed.id) it.copy(deletedAt = 500L) else it
        }
        val directory = File(URI(trashed.pdfUri)).parentFile!!
        val recipe = File(directory, "recipe.json").readBytes()

        val backup = fixture.repository.createBackup()
        val result = fixture.repository.restoreBackup(backup.file)

        assertEquals(1, backup.documentCount)
        assertEquals(1, result.documentCount)
        val restored = fixture.documents.state.value.single { it.id != active.id && it.id != trashed.id }
        assertEquals(active.name, restored.name)
        assertNull(restored.deletedAt)
        assertArrayEquals(recipe, File(directory, "recipe.json").readBytes())
        assertTrue(File(directory, "document.pdf").isFile)
        assertTrue(File(directory, "sources.pdf").isFile)
    }

    @Test fun protectedPdfRestoresWithoutDecryptingOrCopyingLocalVaultIdsAndSidecars() = runBlocking {
        val fixture = fixture()
        val original = fixture.seed(protected = true)
        val directory = File(URI(original.pdfUri)).parentFile!!
        File(directory, "recipe.json").writeText("private project metadata")
        File(directory, "signature_1234.png").writeText("private signature")
        File(directory, "thumbnail.jpg").writeText("private thumbnail")
        val backup = fixture.repository.createBackup()
        assertEquals(1, backup.protectedCount)
        assertEquals(0, backup.editableProjectCount)
        ZipFile(backup.file).use { assertEquals(2, it.size()) }
        val result = fixture.repository.restoreBackup(backup.file)
        assertEquals(1, result.protectedCount)
        val restored = fixture.documents.state.value.single { it.id != original.id }
        assertTrue(restored.isProtected)
        assertNotEquals(original.protectionKeyId, restored.protectionKeyId)
        assertTrue(restored.protectionKeyId!!.startsWith("restored:"))
        assertNull(restored.thumbnailUri)
        assertArrayEquals(File(URI(original.pdfUri)).readBytes(), File(URI(restored.pdfUri)).readBytes())
        assertEquals(listOf("document.pdf"), File(URI(restored.pdfUri)).parentFile!!.list()!!.toList())
    }

    @Test fun aLateDatabaseFailureRollsBackInsertedDocumentsAndNewTags() = runBlocking {
        val source = fixture()
        source.tags.state.value = listOf(TagModel(8L, "Trabalho", Color.Blue))
        source.seed(tagId = 8L)
        source.seed(tagId = 8L)
        val backup = source.repository.createBackup()
        val destination = fixture()
        val download = destination.repository.createRestoreFile()
        backup.file.copyTo(download, overwrite = true)
        destination.documents.failAfterInsertOnCall = 2
        expectFailure { destination.repository.restoreBackup(download) }
        assertTrue(destination.documents.state.value.isEmpty())
        assertTrue(destination.tags.state.value.isEmpty())
        assertTrue(destination.documentsRoot.listFiles().orEmpty().isEmpty())
        assertEquals(listOf(download.name), destination.cache.list()!!.toList())
    }

    @Test fun cancellationAfterTheFirstInsertRollsBackOnlyTheNewCopies() = runBlocking {
        val source = fixture()
        source.seed()
        source.seed()
        val backup = source.repository.createBackup()
        val destination = fixture()
        val existing = destination.seed()
        val download = destination.repository.createRestoreFile()
        backup.file.copyTo(download, overwrite = true)
        expectFailure {
            destination.repository.restoreBackup(download) {
                if (it.completedDocuments == 1) throw CancellationException("cancel")
            }
        }
        assertEquals(listOf(existing), destination.documents.state.value)
        assertTrue(File(URI(existing.pdfUri)).isFile)
        assertEquals(1, destination.documentsRoot.listFiles()!!.size)
    }

    @Test fun changingTheLibraryDuringBackupDiscardsTheInconsistentSnapshot() = runBlocking {
        val fixture = fixture()
        val original = fixture.seed()
        expectFailure {
            fixture.repository.createBackup {
                if (it.completedDocuments == 1) fixture.documents.state.value = listOf(original.copy(name = "Alterado"))
            }
        }
        assertTrue(fixture.cache.listFiles().orEmpty().isEmpty())
        assertTrue(File(URI(original.pdfUri)).isFile)
    }

    @Test fun invalidProjectIsRejectedBeforeAnyLibraryMutation() = runBlocking {
        val source = temporary.newFolder()
        File(source, "document.pdf").writeText("%PDF-invalid")
        val archive = temporary.newFile()
        DocumentBackupArchive().write(archive, listOf(BackupDocumentSource(
            BackupDocument(UUID.randomUUID().toString(), "Inválido", 1, 1L, null, false, false),
            source, listOf("document.pdf"))), emptyList())
        val destination = fixture()
        val download = destination.repository.createRestoreFile()
        archive.copyTo(download, overwrite = true)
        expectFailure { destination.repository.restoreBackup(download) }
        assertTrue(destination.documents.state.value.isEmpty())
        assertTrue(destination.documentsRoot.listFiles().orEmpty().isEmpty())
    }

    @Test fun temporaryCleanupCannotDeleteAnOwnedDocumentOrAnUnrelatedFile() = runBlocking {
        val fixture = fixture()
        val original = fixture.seed()
        expectFailure { fixture.repository.discard(File(URI(original.pdfUri))) }
        assertTrue(File(URI(original.pdfUri)).isFile)
        val download = fixture.repository.createRestoreFile()
        fixture.repository.discard(download)
        assertFalse(download.exists())
    }

    private fun fixture(): Fixture {
        val root = temporary.newFolder()
        val docsRoot = File(root, "documents").apply { mkdir() }
        val cache = File(root, "backup").apply { mkdir() }
        val documents = FakeDocuments()
        val tags = FakeTags()
        return Fixture(docsRoot, cache, documents, tags,
            AndroidDocumentBackupRepository(docsRoot, cache, documents, tags, Dispatchers.Unconfined))
    }

    private class Fixture(
        val documentsRoot: File,
        val cache: File,
        val documents: FakeDocuments,
        val tags: FakeTags,
        val repository: AndroidDocumentBackupRepository,
    ) {
        fun seed(editable: Boolean = false, protected: Boolean = false, tagId: Long? = null): DocumentModel {
            val directory = File(documentsRoot, UUID.randomUUID().toString()).apply { mkdir() }
            val pdf = File(directory, "document.pdf")
            PDDocument().use {
                it.addPage(PDPage())
                if (protected) PdfProtection.apply(it, "senha-original")
                it.save(pdf)
            }
            if (editable) {
                pdf.copyTo(File(directory, "sources.pdf"))
                val recipe = PdfProjectRecipe(pages = listOf(RecipePage(
                    "page", 0, PdfPageLayout(crop = PdfCropBounds(.1f, .2f, .9f, .8f)),
                    listOf(RecipeSignature("ink", "signature_1234.png", .1f, .7f, .4f, .1f, 12f, true)),
                )))
                File(directory, "recipe.json").writeText(Json.encodeToString(recipe))
                File(directory, "signature_1234.png").writeBytes(Base64.getDecoder().decode(
                    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVQIHWP4z8DwHwAFgAI/ScLbtAAAAABJRU5ErkJggg=="))
            }
            val model = DocumentModel(
                id = documents.nextId++, name = "Documento", pdfUri = pdf.toURI().toString(),
                pageCount = 1, createdAt = 123456L, sizeBytes = pdf.length(), tagId = tagId,
                protectionKeyId = if (protected) "old-device-key" else null,
            )
            documents.state.value += model
            return model
        }
    }

    private class FakeDocuments : DocumentRepository {
        val state = MutableStateFlow<List<DocumentModel>>(emptyList())
        var nextId = 1L
        var failAfterInsertOnCall: Int? = null
        private var createCalls = 0
        override fun observeDocuments() = state.map { list -> list.filter { it.deletedAt == null } }
        override fun observeTrashedDocuments() = state.map { list -> list.filter { it.deletedAt != null } }
        override suspend fun moveToTrash(id: Long) = error("unused")
        override suspend fun restoreFromTrash(id: Long) = error("unused")
        override suspend fun deletePermanently(id: Long) = error("unused")
        override suspend fun findById(id: Long) = state.value.find { it.id == id && it.deletedAt == null }
        override suspend fun create(scan: ScannedDocument, name: String, tagId: Long?): Long {
            val id = nextId++
            state.value += DocumentModel(id, name, scan.pdfUri, scan.pageCount, scan.createdAt,
                scan.sizeBytes, scan.thumbnailUri, tagId, scan.protectionKeyId)
            if (++createCalls == failAfterInsertOnCall) error("database failure after insertion")
            return id
        }
        override suspend fun delete(document: DocumentModel) { state.value = state.value.filterNot { it.id == document.id } }
        override suspend fun discardScan(scan: ScannedDocument) = Unit
        override suspend fun prepareScan(pageUris: List<String>): ScannedDocument = error("unused")
        override suspend fun update(id: Long, name: String, tagId: Long?) = error("unused")
        override suspend fun replaceContent(id: Long, scan: ScannedDocument, allowProtectionRemoval: Boolean): DocumentModel = error("unused")
        override suspend fun export(document: DocumentModel, targetUri: String) = error("unused")
        override suspend fun prepareShare(document: DocumentModel): String = error("unused")
    }

    private class FakeTags : TagRepository {
        val state = MutableStateFlow<List<TagModel>>(emptyList())
        private var nextId = 100L
        override fun observeTags() = state
        override suspend fun findById(id: Long) = state.value.find { it.id == id }
        override suspend fun create(tag: TagModel): Long {
            val id = nextId++
            state.value += tag.copy(id = id)
            return id
        }
        override suspend fun update(tag: TagModel) = error("unused")
        override suspend fun delete(tag: TagModel) { state.value = state.value.filterNot { it.id == tag.id } }
    }

    private suspend fun expectFailure(block: suspend () -> Unit) {
        var thrown = false
        try { block() } catch (_: Exception) { thrown = true }
        assertTrue("A operação deveria falhar.", thrown)
    }
}
