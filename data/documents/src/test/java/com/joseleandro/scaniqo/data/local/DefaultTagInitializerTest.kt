package com.joseleandro.scaniqo.data.local

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import com.joseleandro.scaniqo.data.datasource.TagDatasourceImpl
import com.joseleandro.scaniqo.data.local.document.DocumentEntity
import com.joseleandro.scaniqo.data.local.tag.DefaultTagInitializer
import com.joseleandro.scaniqo.data.local.tag.LocalizedDefaultTags
import com.joseleandro.scaniqo.data.local.tag.TagEntity
import com.joseleandro.scaniqo.data.repository.TagRepositoryImpl
import java.util.Locale
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class DefaultTagInitializerTest {
    private val context get() = RuntimeEnvironment.getApplication()
    @get:Rule val databaseDirectory = TemporaryFolder()
    // Keep SQLite/WAL paths short on Windows, independent of Robolectric's method-based sandbox path.
    private val databaseName get() = File(databaseDirectory.root, "default-tags-test.db").absolutePath
    private lateinit var database: ScaniqoDatabase
    private val defaults = listOf(
        TagEntity(name = "Trabalho", colorArgb = 1, iconKey = "work"),
        TagEntity(name = "Pessoal", colorArgb = 2, iconKey = "person"),
        TagEntity(name = "Saúde", colorArgb = 3, iconKey = "health"),
    )

    @Before fun prepare() {
        context.deleteDatabase(databaseName)
        database = openDatabase()
    }

    @After fun cleanUp() {
        database.close()
        context.deleteDatabase(databaseName)
    }

    @Test fun firstObservationAlreadyIncludesDefaultsAndRepeatedObservationDoesNotDuplicate() = runBlocking {
        val initializer = DefaultTagInitializer(database) { defaults }
        val repository = TagRepositoryImpl(TagDatasourceImpl(database.tagDao()), initializer::initialize)

        val first = repository.observeTags().first()
        val again = repository.observeTags().first()

        assertEquals(defaults.map { it.name }.toSet(), first.map { it.name }.toSet())
        assertEquals(first, again)
        assertEquals(3, database.tagDao().findAll().size)
        assertTrue(database.tagDao().defaultsInitialized())
    }

    @Test fun existingNamesAreMatchedWithoutChangingIdsColorsIconsOrSpelling() = runBlocking {
        val dao = database.tagDao()
        val existing = listOf(
            TagEntity(id = 21, name = "  TRABALHO  ", colorArgb = 99, iconKey = "star"),
            TagEntity(id = 22, name = "saude", colorArgb = 88, iconKey = "heart"),
            TagEntity(id = 23, name = "Minha categoria", colorArgb = 77, iconKey = "folder"),
        )
        existing.forEach { dao.insert(it) }

        DefaultTagInitializer(database) { defaults }.initialize()

        assertEquals(4, dao.findAll().size)
        existing.forEach { assertEquals(it, dao.findById(it.id)) }
        assertEquals(1, dao.findAll().count { it.name == "Pessoal" })
    }

    @Test fun versionSevenUpgradePreservesCustomTagAndAssociatedDocumentBeforeAddingMissingDefaults() = runBlocking {
        database.close()
        context.deleteDatabase(databaseName)
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
            old.execSQL("CREATE INDEX index_documents_pdfUri ON documents (pdfUri)")
            old.execSQL("CREATE INDEX index_documents_deletedAt_createdAt_id ON documents (deletedAt, createdAt, id)")
            old.execSQL("CREATE INDEX index_documents_deletedAt_id ON documents (deletedAt, id)")
            old.execSQL("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)")
            old.execSQL("INSERT INTO room_master_table VALUES(42, '3646d7118d18f00e8127f816bc146389')")
            old.execSQL("INSERT INTO tags VALUES(21, 'TRABALHO', 99, 'star')")
            old.execSQL(
                """INSERT INTO documents VALUES
                   (31, 'Contrato existente', 'file:///existing.pdf', 2, 123, 4096,
                   'file:///thumb.jpg', 21, 'existing-key', NULL)""",
            )
            old.version = 7
        }
        database = openDatabase()
        val expectedTag = TagEntity(id = 21, name = "TRABALHO", colorArgb = 99, iconKey = "star")
        val expectedDocument = DocumentEntity(
            id = 31, name = "Contrato existente", pdfUri = "file:///existing.pdf",
            pageCount = 2, createdAt = 123, sizeBytes = 4096,
            thumbnailUri = "file:///thumb.jpg", tagId = 21, protectionKeyId = "existing-key",
        )

        assertEquals(expectedTag, database.tagDao().findById(21))
        assertEquals(expectedDocument, database.documentDao().findById(31))
        assertFalse(database.tagDao().defaultsInitialized())
        DefaultTagInitializer(database) { defaults }.initialize()

        assertEquals(8, database.openHelper.readableDatabase.version)
        assertEquals(expectedTag, database.tagDao().findById(21))
        assertEquals(expectedDocument, database.documentDao().findById(31))
        assertEquals(setOf("TRABALHO", "Pessoal", "Saúde"), database.tagDao().findAll().map { it.name }.toSet())
        assertTrue(database.tagDao().defaultsInitialized())
    }

    @Test fun nonLatinCombiningMarksRemainDistinctWhenMatchingExistingNames() = runBlocking {
        val existing = TagEntity(id = 21, name = "कम", colorArgb = 99, iconKey = "star")
        database.tagDao().insert(existing)

        DefaultTagInitializer(database) {
            listOf(TagEntity(name = "काम", colorArgb = 1, iconKey = "work"))
        }.initialize()

        assertEquals(setOf("कम", "काम"), database.tagDao().findAll().map { it.name }.toSet())
        assertEquals(existing, database.tagDao().findById(21))
    }

    @Test fun deletionAndCustomizationRemainAfterReopeningDatabaseAndChangingLocale() = runBlocking {
        DefaultTagInitializer(database) { defaults }.initialize()
        val dao = database.tagDao()
        val work = dao.findAll().first { it.name == "Trabalho" }
        val personal = dao.findAll().first { it.name == "Pessoal" }
        val custom = personal.copy(name = "Meus documentos", colorArgb = 42, iconKey = "shield")
        dao.delete(work)
        dao.update(custom)
        database.close()
        database = openDatabase()

        DefaultTagInitializer(database) {
            listOf(TagEntity(name = "Work", colorArgb = 1, iconKey = "work"))
        }.initialize()

        val restored = database.tagDao().findAll()
        assertEquals(2, restored.size)
        assertEquals(custom, restored.first { it.id == personal.id })
        assertFalse(restored.any { it.name == "Trabalho" || it.name == "Work" })
    }

    @Test fun concurrentInitializersCreateEachDefaultOnlyOnce() = runBlocking {
        coroutineScope {
            List(8) { async { DefaultTagInitializer(database) { defaults }.initialize() } }.awaitAll()
        }

        assertEquals(3, database.tagDao().findAll().size)
        assertTrue(database.tagDao().defaultsInitialized())
    }

    @Test fun failedInsertionRollsBackBothTagsAndMarkerSoNextAttemptCanSucceed() = runBlocking {
        val invalid = defaults.take(2).map { it.copy(id = 7) }
        try {
            DefaultTagInitializer(database) { invalid }.initialize()
            fail("A duplicate primary key should abort the seed transaction.")
        } catch (_: SQLiteConstraintException) {
            // A segunda inserção falha depois de a primeira ter sido executada.
        }

        assertTrue(database.tagDao().findAll().isEmpty())
        assertFalse(database.tagDao().defaultsInitialized())
        DefaultTagInitializer(database) { defaults }.initialize()
        assertEquals(3, database.tagDao().findAll().size)
        assertTrue(database.tagDao().defaultsInitialized())
    }

    @Test fun defaultNamesUseCurrentLocaleAndExistingIcons() {
        val localizedContext = context.createConfigurationContext(
            Configuration(context.resources.configuration).apply {
                setLocale(Locale.forLanguageTag("pt-BR"))
            },
        )

        val tags = LocalizedDefaultTags(localizedContext).tags()

        assertEquals(
            listOf("Trabalho", "Pessoal", "Estudos", "Saúde", "Finanças", "Recibos", "Contratos", "Casa"),
            tags.map { it.name },
        )
        assertEquals(listOf("work", "person", "study", "health", "money", "receipt", "document", "home"),
            tags.map { it.iconKey })
        assertEquals(8, tags.map { it.colorArgb }.distinct().size)
    }

    private fun openDatabase() = Room.databaseBuilder(context, ScaniqoDatabase::class.java, databaseName)
        .allowMainThreadQueries().build()
}
