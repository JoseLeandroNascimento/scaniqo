package com.joseleandro.scaniqo.data.local

import android.app.Application
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import com.joseleandro.scaniqo.data.local.document.DocumentEntity
import kotlin.system.measureNanoTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Opt-in volume check. Every row lives in a temporary in-memory database. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, manifest = Config.NONE)
class DocumentDatabaseLoadTest {
    private var database: ScaniqoDatabase? = null

    @After fun close() {
        database?.close()
    }

    @Test
    fun oneHundredThousandRowsKeepOrderingLookupAndTrashIntegrity() = runBlocking {
        val room = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(), ScaniqoDatabase::class.java,
        ).allowMainThreadQueries().build().also { database = it }
        val sql = room.openHelper.writableDatabase
        val insertion = measureNanoTime { populate(sql) }
        val activePlan = plan(sql, ACTIVE_SQL)
        val trashPlan = plan(sql, TRASH_SQL)
        val uriPlan = plan(sql, "SELECT * FROM documents WHERE pdfUri = 'file:///load-fixture/99999.pdf' LIMIT 1")
        val dao = room.documentDao()
        lateinit var active: List<DocumentEntity>
        lateinit var trash: List<DocumentEntity>
        val activeNanos = measureNanoTime { active = dao.observeAll().first() }
        val trashNanos = measureNanoTime { trash = dao.observeTrashed().first() }
        assertEquals(80_000, active.size)
        assertEquals(20_000, trash.size)
        assertTrue(active.all { it.deletedAt == null })
        assertTrue(trash.all { it.deletedAt != null })
        assertOrdered(active) { it.createdAt }
        assertOrdered(trash) { requireNotNull(it.deletedAt) }

        val lookupNanos = measureNanoTime {
            repeat(25) { index ->
                val id = 100_000L - index
                val found = dao.findByPdfUri("file:///load-fixture/$id.pdf")
                assertEquals(id, found?.id)
                assertEquals("Receipt تقرير $id", found?.name)
            }
            assertNull(dao.findByPdfUri("file:///load-fixture/missing.pdf"))
        }
        val stored = requireNotNull(dao.findById(99_999L))
        assertEquals(1, dao.moveToTrash(stored.id, 9_000_000L))
        assertNull(dao.findById(stored.id))
        assertNotNull(dao.findTrashedById(stored.id))
        assertEquals(1, dao.restoreFromTrash(stored.id))
        assertEquals(stored, dao.findById(stored.id))
        println(
            "LOAD_METRIC room documents=100000 insertMs=${insertion / 1_000_000.0} " +
                "activeMs=${activeNanos / 1_000_000.0} trashMs=${trashNanos / 1_000_000.0} " +
                "lookup26Ms=${lookupNanos / 1_000_000.0}",
        )
        println("LOAD_PLAN active=$activePlan")
        println("LOAD_PLAN trash=$trashPlan")
        println("LOAD_PLAN uri=$uriPlan")
        assertTrue(activePlan, activePlan.contains("index_documents_deletedAt_createdAt_id"))
        assertTrue(trashPlan, trashPlan.contains("index_documents_deletedAt_id"))
        assertTrue(uriPlan, uriPlan.contains("index_documents_pdfUri"))
        assertTrue(activePlan, !activePlan.contains("TEMP B-TREE"))
        assertTrue(trashPlan, !trashPlan.contains("TEMP B-TREE"))
    }

    private fun populate(sql: SupportSQLiteDatabase) {
        sql.beginTransaction()
        try {
            sql.execSQL("INSERT INTO tags (id, name, color_argb, icon_key) VALUES (1, 'Load fixture', 0, 'folder')")
            sql.compileStatement(
                """INSERT INTO documents
                   (id, name, pdfUri, pageCount, createdAt, sizeBytes, thumbnailUri, tagId, protectionKeyId, deletedAt)
                   VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
            ).use { insert ->
                repeat(100_000) { index ->
                    val id = index + 1L
                    insert.bindLong(1, id)
                    insert.bindString(2, "Receipt تقرير $id")
                    insert.bindString(3, "file:///load-fixture/$id.pdf")
                    insert.bindLong(4, id % 12 + 1)
                    insert.bindLong(5, id % 257)
                    insert.bindLong(6, 4096L)
                    insert.bindString(7, "file:///load-fixture/$id.jpg")
                    insert.bindLong(8, 1L)
                    if (id % 7 == 0L) insert.bindString(9, "load-key") else insert.bindNull(9)
                    if (id % 5 == 0L) insert.bindLong(10, id % 97) else insert.bindNull(10)
                    insert.executeInsert()
                }
            }
            sql.setTransactionSuccessful()
        } finally {
            sql.endTransaction()
        }
    }

    private fun plan(sql: SupportSQLiteDatabase, query: String): String =
        sql.query("EXPLAIN QUERY PLAN $query").use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getString(3)) }.joinToString(" | ")
        }

    private fun assertOrdered(documents: List<DocumentEntity>, timestamp: (DocumentEntity) -> Long) {
        for (index in 1 until documents.size) {
            val previous = documents[index - 1]
            val current = documents[index]
            assertTrue(
                timestamp(previous) > timestamp(current) ||
                    (timestamp(previous) == timestamp(current) && previous.id > current.id),
            )
        }
    }

    private companion object {
        const val ACTIVE_SQL = "SELECT * FROM documents WHERE deletedAt IS NULL ORDER BY createdAt DESC, id DESC"
        const val TRASH_SQL = "SELECT * FROM documents WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC, id DESC"
    }
}
