package com.joseleandro.scaniqo.data.local

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.room.Room
import androidx.room.RoomDatabase
import com.joseleandro.scaniqo.data.datasource.DocumentDatasourceImpl
import com.joseleandro.scaniqo.data.local.document.AndroidDocumentFileStore
import com.joseleandro.scaniqo.data.repository.DocumentRepositoryImpl
import com.joseleandro.scaniqo.domain.model.DocumentModel
import com.joseleandro.scaniqo.domain.model.ScannedDocument
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import kotlin.system.measureNanoTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Real PDF files + on-disk Room + production repository/file ownership rules; no mass rendering. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, manifest = Config.NONE)
class PhysicalDocumentLibraryLoadTest {
    @get:Rule val temporary = TemporaryFolder()
    private var database: ScaniqoDatabase? = null

    @After fun closeDatabaseBeforeTemporaryFilesAreRemoved() {
        database?.close()
    }

    @Test fun tenThousandPhysicalPdfsSurviveCatalogLookupTrashRestoreAndLateDiscard() = runBlocking {
        val context = isolatedContext()
        val databaseFile = File(temporary.root, "physical-library.db")
        val room = openDatabase(context, databaseFile)
        val datasource = DocumentDatasourceImpl(room.documentDao())
        val fileStore = AndroidDocumentFileStore(context)
        val repository = DocumentRepositoryImpl(datasource, fileStore, nowMillis = { 1_000_000L })
        val template = onePagePdf()
        // Kept outside the app-owned documents tree, just like an original selected in a provider.
        val original = temporary.newFile("external-original.pdf").apply { writeBytes(template) }
        val documentsRoot = File(context.filesDir, "documents").apply { check(mkdirs()) }
        val physicalFiles = ArrayList<File>(DOCUMENTS)
        val drafts = ArrayList<ScannedDocument>(DOCUMENTS)

        val fileCreationNanos = measureNanoTime {
            repeat(DOCUMENTS) { index ->
                val directory = File(documentsRoot, UUID.randomUUID().toString()).apply { check(mkdir()) }
                val file = File(directory, "document.pdf").apply { writeBytes(template) }
                physicalFiles += file
                drafts += ScannedDocument(
                    pdfUri = file.toURI().toASCIIString(),
                    pageCount = 1,
                    createdAt = index.toLong() + 1L,
                    sizeBytes = file.length(),
                )
            }
        }
        assertEquals(DOCUMENTS, physicalFiles.count { it.isFile })
        // Uri.fromFile interprets a Windows drive path as an Android authority. Use the host's
        // file URI encoding so the production path/ownership guard sees the actual temporary file.
        assertEquals(
            physicalFiles.first().canonicalFile,
            File(requireNotNull(Uri.parse(drafts.first().pdfUri).path)).canonicalFile,
        )
        for (index in listOf(0, DOCUMENTS / 2, DOCUMENTS - 1)) {
            PDDocument.load(physicalFiles[index]).use { pdf -> assertEquals(1, pdf.numberOfPages) }
        }

        // Each call uses the public production repository and its actual Room lookup/insert.
        val ids = LongArray(DOCUMENTS)
        val registrationNanos = measureNanoTime {
            drafts.forEachIndexed { index, scan ->
                ids[index] = repository.create(scan, "Physical PDF $index", null)
            }
        }
        lateinit var listed: List<DocumentModel>
        val listingNanos = measureNanoTime { listed = repository.observeDocuments().first() }
        assertEquals(DOCUMENTS, listed.size)
        assertEquals(DOCUMENTS, listed.map { it.id }.toSet().size)
        assertEquals(ids.last(), listed.first().id)
        assertEquals(ids.first(), listed.last().id)
        assertTrue(listed.all { it.sizeBytes == template.size.toLong() && it.pageCount == 1 })

        val lookupNanos = measureNanoTime {
            // Spread lookups/repeated deliveries over the whole catalog, including both ends.
            repeat(256) { sample ->
                val index = sample * (DOCUMENTS - 1) / 255
                val scan = drafts[index]
                assertEquals(ids[index], datasource.findByPdfUri(scan.pdfUri)?.id)
                assertEquals(scan.pdfUri, repository.findById(ids[index])?.pdfUri)
                assertEquals(ids[index], repository.create(scan, "Physical PDF $index", null))
                repository.discardScan(scan) // Late UI cancellation must not delete an adopted file.
                assertTrue(physicalFiles[index].isFile)
            }
        }
        assertEquals(DOCUMENTS, repository.observeDocuments().first().size)

        val selected = (0 until DOCUMENTS step 100).toList()
        val trashRestoreNanos = measureNanoTime {
            selected.forEach { repository.moveToTrash(ids[it]) }
            assertEquals(DOCUMENTS - selected.size, repository.observeDocuments().first().size)
            val trashed = repository.observeTrashedDocuments().first()
            assertEquals(selected.map { ids[it] }.toSet(), trashed.map { it.id }.toSet())
            selected.forEach { index ->
                assertNull(repository.findById(ids[index]))
                assertEquals(ids[index], datasource.findByPdfUri(drafts[index].pdfUri)?.id)
                repository.discardScan(drafts[index]) // Trash still owns the original bytes.
                assertArrayEquals(template, physicalFiles[index].readBytes())
                repository.restoreFromTrash(ids[index])
                val restored = requireNotNull(repository.findById(ids[index]))
                assertEquals(drafts[index].pdfUri, restored.pdfUri)
                assertEquals(drafts[index].createdAt, restored.createdAt)
            }
        }
        assertEquals(DOCUMENTS, repository.observeDocuments().first().size)
        assertTrue(repository.observeTrashedDocuments().first().isEmpty())

        val activeDeletion = runCatching { repository.deletePermanently(ids.first()) }.exceptionOrNull()
        assertTrue(activeDeletion is IllegalStateException)
        assertTrue(physicalFiles.first().isFile)
        repository.moveToTrash(ids.last())
        repository.deletePermanently(ids.last())
        assertFalse(physicalFiles.last().exists())
        assertNull(datasource.findByPdfUri(drafts.last().pdfUri))
        // An unowned source URI must never authorize deleting an external original.
        repository.discardScan(drafts.first().copy(pdfUri = original.toURI().toASCIIString()))
        assertArrayEquals(template, original.readBytes())
        physicalFiles.dropLast(1).forEach { assertArrayEquals(template, it.readBytes()) }
        assertEquals(DOCUMENTS - 1, documentsRoot.listFiles().orEmpty().size)

        room.close()
        val reopened = openDatabase(context, databaseFile)
        val reopenedRepository = DocumentRepositoryImpl(
            DocumentDatasourceImpl(reopened.documentDao()), fileStore,
        )
        assertEquals(DOCUMENTS - 1, reopenedRepository.observeDocuments().first().size)
        assertEquals(drafts.first().pdfUri, reopenedRepository.findById(ids.first())?.pdfUri)
        assertTrue(reopenedRepository.observeTrashedDocuments().first().isEmpty())
        assertTrue(databaseFile.isFile)

        println(
            "LOAD_METRIC physicalFiles=$DOCUMENTS catalogCreations=$DOCUMENTS templatePdfBytes=${template.size} " +
                "ownedPdfBytes=${template.size.toLong() * DOCUMENTS} " +
                "fileCreationMs=${fileCreationNanos / 1_000_000.0} " +
                "registrationMs=${registrationNanos / 1_000_000.0} " +
                "listingMs=${listingNanos / 1_000_000.0} " +
                "lookupAndIdempotence256Ms=${lookupNanos / 1_000_000.0} " +
                "trashRestore100Ms=${trashRestoreNanos / 1_000_000.0}",
        )
    }

    private fun openDatabase(context: Context, file: File): ScaniqoDatabase =
        Room.databaseBuilder(context, ScaniqoDatabase::class.java, file.absolutePath)
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .build()
            .also { database = it }

    private fun isolatedContext(): Context {
        val files = temporary.newFolder("app-files")
        val cache = temporary.newFolder("app-cache")
        return object : ContextWrapper(RuntimeEnvironment.getApplication()) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir(): File = files
            override fun getCacheDir(): File = cache
        }
    }

    private fun onePagePdf(): ByteArray = PDDocument().use { pdf ->
        pdf.addPage(PDPage(PDRectangle.A4))
        ByteArrayOutputStream().use { output ->
            pdf.save(output)
            output.toByteArray()
        }
    }

    private companion object { const val DOCUMENTS = 10_000 }
}
