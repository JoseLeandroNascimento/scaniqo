package com.joseleandro.scaniqo.data.local

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.joseleandro.scaniqo.data.local.document.DocumentEntity
import com.joseleandro.scaniqo.data.local.tag.TagEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, manifest = Config.NONE)
class DocumentTrashPersistenceTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private var database: ScaniqoDatabase? = null
    private val databaseName = "document-trash-test.db"

    @Before fun prepare() {
        context.deleteDatabase(databaseName)
    }

    @After fun cleanUp() {
        database?.close()
        context.deleteDatabase(databaseName)
    }

    @Test fun trashSurvivesDatabaseRecreationAndRestoresOriginalMetadata() = runBlocking {
        val initial = open()
        val tagId = initial.tagDao().insert(TagEntity(name = "Recibos", colorArgb = 0, iconKey = "folder"))
        val entity = document(1L).copy(tagId = tagId, protectionKeyId = "vault-key")
        initial.documentDao().insert(entity)
        initial.documentDao().insert(document(2L))
        assertEquals(1, initial.documentDao().moveToTrash(entity.id, 500L))
        initial.close()

        val reopened = open().documentDao()
        assertNull(reopened.findById(entity.id))
        assertEquals(listOf(2L), reopened.observeAll().first().map { it.id })
        assertEquals(listOf(entity.copy(deletedAt = 500L)), reopened.observeTrashed().first())
        assertEquals(entity.copy(deletedAt = 500L), reopened.findByPdfUri(entity.pdfUri))
        assertEquals(1, reopened.restoreFromTrash(entity.id))
        assertEquals(entity, reopened.findById(entity.id))
        assertTrue(reopened.observeTrashed().first().isEmpty())
    }

    @Test fun removingATagWhileTrashedAllowsRestoringWithoutTheDeletedTag() = runBlocking {
        val db = open()
        val tag = TagEntity(id = 3L, name = "Recibos", colorArgb = 0, iconKey = "folder")
        db.tagDao().insert(tag)
        val entity = document(1L).copy(tagId = tag.id)
        db.documentDao().insert(entity)
        db.documentDao().moveToTrash(entity.id, 500L)

        db.tagDao().delete(tag)
        assertEquals(1, db.documentDao().restoreFromTrash(entity.id))

        assertEquals(entity.copy(tagId = null), db.documentDao().findById(entity.id))
    }

    @Test fun staleEditsCannotModifyTrashAndPermanentDeletionCannotRemoveAnActiveDocument() = runBlocking {
        val dao = open().documentDao()
        val entity = document(1L)
        dao.insert(entity)
        assertEquals(0, dao.deleteTrashed(entity.id))
        assertEquals(0, dao.restoreFromTrash(entity.id))
        assertEquals(1, dao.moveToTrash(entity.id, 500L))
        assertEquals(0, dao.moveToTrash(entity.id, 900L))

        assertEquals(0, dao.updateMetadata(entity.id, "Alterado", null))
        assertEquals(0, dao.replaceContent(entity.id, "file:///outro.pdf", 9, 900L, null, null))
        assertEquals(entity.copy(deletedAt = 500L), dao.findTrashedById(entity.id))
        assertEquals(1, dao.deleteTrashed(entity.id))
        assertNull(dao.findByPdfUri(entity.pdfUri))
        assertTrue(dao.observeTrashed().first().isEmpty())
        assertEquals(0, dao.restoreFromTrash(entity.id))
    }

    @Test fun migrationFromVersionFiveKeepsExistingDocumentsActiveWithTheirTagsAndProtection() = runBlocking {
        // Reproduz o schema publicado em schemas/...ScaniqoDatabase/5.json.
        context.openOrCreateDatabase(databaseName, Context.MODE_PRIVATE, null).use { old ->
            old.execSQL(
                """CREATE TABLE tags (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    name TEXT NOT NULL, color_argb INTEGER NOT NULL, icon_key TEXT NOT NULL)"""
            )
            old.execSQL(
                """CREATE TABLE documents (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    name TEXT NOT NULL, pdfUri TEXT NOT NULL, pageCount INTEGER NOT NULL,
                    createdAt INTEGER NOT NULL, sizeBytes INTEGER NOT NULL DEFAULT 0,
                    thumbnailUri TEXT, tagId INTEGER, protectionKeyId TEXT,
                    FOREIGN KEY(tagId) REFERENCES tags(id) ON UPDATE NO ACTION ON DELETE SET NULL)"""
            )
            old.execSQL("CREATE INDEX index_documents_tagId ON documents (tagId)")
            old.execSQL("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)")
            old.execSQL("INSERT INTO room_master_table VALUES(42, '8391d5e6a608d620724bd9c3ea3f3458')")
            old.execSQL("INSERT INTO tags VALUES(3, 'Trabalho', 0, 'folder')")
            old.execSQL(
                """INSERT INTO documents VALUES(7, 'Contrato', 'file:///document.pdf', 2,
                    123, 4096, 'file:///thumbnail.jpg', 3, 'vault-key')"""
            )
            old.version = 5
        }

        val dao = open().documentDao()
        val migrated = requireNotNull(dao.findById(7L))

        assertEquals("Contrato", migrated.name)
        assertEquals("file:///document.pdf", migrated.pdfUri)
        assertEquals("file:///thumbnail.jpg", migrated.thumbnailUri)
        assertEquals(2, migrated.pageCount)
        assertEquals(123L, migrated.createdAt)
        assertEquals(4096L, migrated.sizeBytes)
        assertEquals(3L, migrated.tagId)
        assertEquals("vault-key", migrated.protectionKeyId)
        assertNull(migrated.deletedAt)
        assertEquals(listOf(migrated), dao.observeAll().first())
        assertTrue(dao.observeTrashed().first().isEmpty())
        assertEquals(1, dao.moveToTrash(migrated.id, 500L))
        assertEquals(1, dao.restoreFromTrash(migrated.id))
        assertEquals(migrated, dao.findById(migrated.id))
    }

    private fun open(): ScaniqoDatabase =
        Room.databaseBuilder(context, ScaniqoDatabase::class.java, databaseName)
            .allowMainThreadQueries()
            .build()
            .also { database = it }

    private fun document(id: Long) = DocumentEntity(
        id = id,
        name = "Documento $id",
        pdfUri = "file:///documents/$id/document.pdf",
        pageCount = 2,
        createdAt = 100L + id,
        sizeBytes = 4096L,
        thumbnailUri = "file:///documents/$id/thumbnail.jpg",
    )
}
