package com.joseleandro.scaniqo.data.local

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.joseleandro.scaniqo.data.local.document.DocumentEntity
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
class DocumentIndexMigrationTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val databaseName = "document-index-migration-test.db"
    private var database: ScaniqoDatabase? = null

    @Before fun prepare() { context.deleteDatabase(databaseName) }
    @After fun cleanUp() {
        database?.close()
        context.deleteDatabase(databaseName)
    }

    @Test fun versionSixKeepsActiveAndTrashedMetadataIncludingSharedUris() = runBlocking {
        context.openOrCreateDatabase(databaseName, Context.MODE_PRIVATE, null).use { old ->
            old.execSQL(
                """CREATE TABLE tags (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                   name TEXT NOT NULL, color_argb INTEGER NOT NULL, icon_key TEXT NOT NULL)""",
            )
            old.execSQL(
                """CREATE TABLE documents (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                   name TEXT NOT NULL, pdfUri TEXT NOT NULL, pageCount INTEGER NOT NULL,
                   createdAt INTEGER NOT NULL, sizeBytes INTEGER NOT NULL DEFAULT 0,
                   thumbnailUri TEXT, tagId INTEGER, protectionKeyId TEXT, deletedAt INTEGER,
                   FOREIGN KEY(tagId) REFERENCES tags(id) ON UPDATE NO ACTION ON DELETE SET NULL)""",
            )
            old.execSQL("CREATE INDEX index_documents_tagId ON documents (tagId)")
            old.execSQL("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)")
            old.execSQL("INSERT INTO room_master_table VALUES(42, '429c213611f51a4d8ea02ffd48e5bdff')")
            old.execSQL("INSERT INTO tags VALUES(3, 'Work', 0, 'folder')")
            old.execSQL(
                """INSERT INTO documents VALUES
                   (7, 'Contract تقرير', 'file:///shared.pdf', 2, 123, 4096, 'file:///thumb.jpg', 3, 'vault-key', NULL),
                   (8, 'Archived', 'file:///shared.pdf', 3, 124, 8192, NULL, 3, 'older-key', 456)""",
            )
            old.version = 6
        }
        val room = Room.databaseBuilder(context, ScaniqoDatabase::class.java, databaseName)
            .allowMainThreadQueries().build().also { database = it }
        val dao = room.documentDao()
        val active = DocumentEntity(
            id = 7, name = "Contract تقرير", pdfUri = "file:///shared.pdf", pageCount = 2,
            createdAt = 123, sizeBytes = 4096, thumbnailUri = "file:///thumb.jpg",
            tagId = 3, protectionKeyId = "vault-key",
        )
        val trashed = DocumentEntity(
            id = 8, name = "Archived", pdfUri = "file:///shared.pdf", pageCount = 3,
            createdAt = 124, sizeBytes = 8192, tagId = 3,
            protectionKeyId = "older-key", deletedAt = 456,
        )
        assertEquals(listOf(active), dao.observeAll().first())
        assertEquals(listOf(trashed), dao.observeTrashed().first())
        assertNull(dao.findById(8))
        val indexes = room.openHelper.readableDatabase.query("PRAGMA index_list('documents')").use { cursor ->
            buildSet { while (cursor.moveToNext()) add(cursor.getString(1)) }
        }
        assertTrue(indexes.containsAll(listOf(
            "index_documents_tagId", "index_documents_pdfUri",
            "index_documents_deletedAt_createdAt_id", "index_documents_deletedAt_id",
        )))
        assertEquals(1, dao.restoreFromTrash(8))
        assertEquals(trashed.copy(deletedAt = null), dao.findById(8))
        assertEquals(active, dao.findById(7))
        assertEquals(7, room.openHelper.readableDatabase.version)
    }
}
