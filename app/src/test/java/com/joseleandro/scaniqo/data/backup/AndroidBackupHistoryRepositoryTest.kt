package com.joseleandro.scaniqo.data.backup

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import com.joseleandro.scaniqo.domain.model.BackupHistoryEntry
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, manifest = Config.NONE)
class AndroidBackupHistoryRepositoryTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val preferences get() = context.getSharedPreferences("backup_history", Context.MODE_PRIVATE)
    private val account = "owner@example.com"

    @Before fun reset() {
        check(preferences.edit().clear().commit())
    }

    @Test fun startsEmptyWithoutWritingPreferences() {
        assertTrue(AndroidBackupHistoryRepository(context).entries.value.isEmpty())
        assertTrue(preferences.all.isEmpty())
    }

    @Test fun historySurvivesRestartAndNormalizesAccountKeys() = runTest {
        val repository = AndroidBackupHistoryRepository(context)
        val old = entry("old", 100)
        val current = entry("current", 200).copy(documentCount = 8)
        repository.record(" Owner@Example.COM ", listOf(old, current))
        repository.record("other@example.com", listOf(entry("other", 300)))

        val restored = AndroidBackupHistoryRepository(context).entries.value
        assertEquals(listOf(current, old), restored[account])
        assertEquals(listOf(entry("other", 300)), restored["other@example.com"])
        assertEquals(2, restored.size)
        assertFalse(preferences.getString("history", "")!!.contains("token"))
    }

    @Test fun metadataRefreshPreservesKnownDocumentCountWithoutDuplicatingEntries() = runTest {
        val repository = AndroidBackupHistoryRepository(context)
        repository.record(account, listOf(entry("current", 100).copy(documentCount = 12)))
        repository.record(account, listOf(entry("current", 100).copy(name = "renamed.zip", sizeBytes = 250)))
        val restored = AndroidBackupHistoryRepository(context).entries.value.getValue(account)
        assertEquals(listOf(entry("current", 100).copy(name = "renamed.zip", sizeBytes = 250, documentCount = 12)), restored)
    }

    @Test fun deletingARecordSurvivesRestartAndRemoteReimport() = runTest {
        val original = entry("current", 100).copy(documentCount = 4)
        val repository = AndroidBackupHistoryRepository(context)
        repository.record(account, listOf(original, entry("old", 50)))
        repository.delete(" OWNER@example.com ", original.id)

        val restored = AndroidBackupHistoryRepository(context)
        restored.record(account, listOf(original))
        assertEquals(listOf(entry("old", 50)), restored.entries.value[account])
        assertEquals(restored.entries.value, AndroidBackupHistoryRepository(context).entries.value)
    }

    @Test fun deletingTheLastRecordDoesNotRemoveItsTombstone() = runTest {
        val original = entry("current", 100)
        val repository = AndroidBackupHistoryRepository(context)
        repository.record(account, listOf(original))
        repository.delete(account, original.id)

        val restored = AndroidBackupHistoryRepository(context)
        restored.record(account, listOf(original, entry("next", 200)))
        assertEquals(listOf(entry("next", 200)), restored.entries.value[account])
    }

    @Test fun deletedIdsAreScopedToTheirAccount() = runTest {
        val original = entry("same-id", 100)
        val repository = AndroidBackupHistoryRepository(context)
        repository.record(account, listOf(original))
        repository.record("other@example.com", listOf(original))
        repository.delete(account, original.id)
        repository.record("other@example.com", listOf(original))

        assertEquals(emptyList<BackupHistoryEntry>(), repository.entries.value[account])
        assertEquals(listOf(original), repository.entries.value["other@example.com"])
    }

    @Test fun fullHistoryHasNoFiveEntryStorageLimit() = runTest {
        val repository = AndroidBackupHistoryRepository(context)
        val history = (1..150).map { entry("backup-$it", it.toLong()) }
        repository.record(account, history)
        assertEquals(history.reversed(), AndroidBackupHistoryRepository(context).entries.value[account])
    }

    @Test fun concurrentRecordsDoNotOverwriteEachOther() = runTest {
        val repository = AndroidBackupHistoryRepository(context)
        coroutineScope {
            (1..32).map { index ->
                launch(Dispatchers.Default) {
                    repository.record(account, listOf(entry("backup-$index", index.toLong())))
                }
            }.joinAll()
        }
        assertEquals(32, repository.entries.value.getValue(account).size)
        assertEquals(repository.entries.value, AndroidBackupHistoryRepository(context).entries.value)
    }

    @Test fun failedCommitDoesNotPublishOrForgetExistingHistory() = runTest {
        val existing = entry("old", 100)
        AndroidBackupHistoryRepository(context).record(account, listOf(existing))
        val repository = AndroidBackupHistoryRepository(failingPreferences(preferences))
        val error = runCatching { repository.record(account, listOf(entry("new", 200))) }.exceptionOrNull()

        assertTrue(error is IOException)
        assertEquals(listOf(existing), repository.entries.value[account])
        assertEquals(listOf(existing), AndroidBackupHistoryRepository(context).entries.value[account])
    }

    @Test fun failedDeleteCommitKeepsEntryVisibleAndAllowsLaterReimport() = runTest {
        val existing = entry("old", 100)
        AndroidBackupHistoryRepository(context).record(account, listOf(existing))
        val repository = AndroidBackupHistoryRepository(failingPreferences(preferences))
        assertTrue(runCatching { repository.delete(account, existing.id) }.exceptionOrNull() is IOException)
        assertEquals(listOf(existing), repository.entries.value[account])
        val restored = AndroidBackupHistoryRepository(context)
        restored.record(account, listOf(existing))
        assertEquals(listOf(existing), restored.entries.value[account])
    }

    @Test fun unreadableHistoryIsPreservedAndBlocksDestructiveReconciliation() = runTest {
        val corrupted = "{broken-history"
        check(preferences.edit().putString("history", corrupted).commit())
        val repository = AndroidBackupHistoryRepository(context)

        assertTrue(runCatching { repository.record(account, listOf(entry("new", 200))) }.exceptionOrNull() is IOException)
        assertTrue(runCatching { repository.delete(account, "old") }.exceptionOrNull() is IOException)
        assertEquals(corrupted, preferences.getString("history", null))
    }

    @Test fun unsupportedHistoryVersionIsNotSilentlyOverwritten() = runTest {
        val future = """{"version":2,"accounts":{}}"""
        check(preferences.edit().putString("history", future).commit())
        val repository = AndroidBackupHistoryRepository(context)
        assertTrue(runCatching { repository.record(account, listOf(entry("new", 200))) }.exceptionOrNull() is IOException)
        assertEquals(future, preferences.getString("history", null))
    }

    private fun entry(id: String, time: Long) = BackupHistoryEntry(id, "$id.zip", time, 100)

    private fun failingPreferences(delegate: SharedPreferences) = object : SharedPreferences by delegate {
        override fun edit(): SharedPreferences.Editor {
            val editor = delegate.edit()
            return object : SharedPreferences.Editor by editor {
                override fun putString(key: String?, value: String?): SharedPreferences.Editor {
                    editor.putString(key, value)
                    return this
                }
                override fun commit() = false
            }
        }
    }
}
