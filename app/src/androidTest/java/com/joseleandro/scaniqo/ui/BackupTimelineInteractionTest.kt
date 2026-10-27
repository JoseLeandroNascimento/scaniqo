package com.joseleandro.scaniqo.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.platform.app.InstrumentationRegistry
import com.joseleandro.scaniqo.R
import com.joseleandro.scaniqo.core.localization.appLocale
import com.joseleandro.scaniqo.core.localization.bidiArgument
import com.joseleandro.scaniqo.core.localization.localizedDateTime
import com.joseleandro.scaniqo.domain.model.BackupHistoryEntry
import com.joseleandro.scaniqo.domain.model.BackupSettings
import com.joseleandro.scaniqo.domain.model.DriveBackupEntry
import com.joseleandro.scaniqo.ui.backup.DriveBackupScreen
import com.joseleandro.scaniqo.ui.backup.state.DriveBackupPhase
import com.joseleandro.scaniqo.ui.backup.state.DriveBackupUiState
import com.joseleandro.scaniqo.ui.theme.ScaniqoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class BackupTimelineInteractionTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val newest = DriveBackupEntry("newest", "Scaniqo-newest.zip", 1_720_000_000_000L, 800L)
    private val newestRecord = BackupHistoryEntry(newest.id, newest.name, newest.modifiedTimeMillis, newest.sizeBytes, 2)
    private val older = BackupHistoryEntry("older", "Scaniqo-older.zip", 1_719_913_600_000L, 500L, 1)
    private val connectedState = DriveBackupUiState(
        accountEmail = "owner@example.com",
        localDocumentCount = 2,
        localSizeBytes = 1_300L,
        currentBackup = newest,
        history = listOf(newestRecord, older),
        hasLoadedBackups = true,
        settings = BackupSettings(accountEmail = "owner@example.com"),
    )

    @Test
    fun deleteWaitsForConfirmationAndCancellationDoesNotDeleteAnotherEntry() {
        val deletions = mutableListOf<BackupHistoryEntry>()
        val restores = mutableListOf<DriveBackupEntry>()
        showScreen(onDelete = deletions::add, onRestore = restores::add)

        openMenu(newestRecord)
        compose.onNodeWithText(text(R.string.backup_delete)).performClick()
        compose.onNodeWithText(text(R.string.backup_delete_title)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.backup_delete_body)).assertIsDisplayed()
        compose.runOnIdle { assertTrue(deletions.isEmpty()) }

        compose.onNodeWithText(text(R.string.backup_cancel)).performClick()
        compose.onNodeWithText(text(R.string.backup_delete_title)).assertDoesNotExist()
        compose.runOnIdle { assertTrue(deletions.isEmpty()) }

        openMenu(older)
        compose.onNodeWithText(text(R.string.backup_delete)).performClick()
        compose.onNodeWithText(older.name).assertIsDisplayed()
        compose.runOnIdle { assertTrue(deletions.isEmpty()) }
        compose.onNodeWithText(text(R.string.backup_delete)).assertIsEnabled().performClick()

        compose.onNodeWithText(text(R.string.backup_delete_title)).assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(listOf(older), deletions)
            assertTrue(restores.isEmpty())
        }
    }

    @Test
    fun historyHasNoRestoreActionAndOnlyCurrentBackupCanBeRestored() {
        val restores = mutableListOf<DriveBackupEntry>()
        showScreen(onRestore = restores::add)

        listOf(older, newestRecord).forEach { entry ->
            openMenu(entry)
            compose.onNodeWithText(text(R.string.backup_restore)).assertDoesNotExist()
            compose.onNodeWithText(text(R.string.backup_delete)).performClick()
            compose.onNodeWithText(text(R.string.backup_cancel)).performClick()
        }

        openCurrentRestore()
        compose.onNodeWithText(text(R.string.backup_restore_title)).assertIsDisplayed()
        compose.onNodeWithText(newest.name).assertIsDisplayed()
        compose.runOnIdle { assertTrue(restores.isEmpty()) }
        compose.onNodeWithText(text(R.string.backup_cancel)).performClick()
        compose.runOnIdle { assertTrue(restores.isEmpty()) }

        openCurrentRestore()
        compose.onNodeWithText(text(R.string.backup_restore)).assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(listOf(newest), restores) }
    }

    @Test
    fun screenShowsFiveRecordsAndFullSheetCanReachAndDeleteOlderRecords() {
        val history = listOf(newestRecord) + List(7) { index ->
            BackupHistoryEntry("old-$index", "Scaniqo-$index.zip", newest.modifiedTimeMillis - (index + 1) * 86_400_000L, 400L)
        }
        val deletions = mutableListOf<BackupHistoryEntry>()
        showScreen(state = { connectedState.copy(history = history) }, onDelete = deletions::add)

        scrollToEntry(history[4])
        compose.onNodeWithContentDescription(optionsDescription(history[4])).assertIsDisplayed()
        compose.onNodeWithContentDescription(optionsDescription(history[5])).assertDoesNotExist()
        compose.onNodeWithTag("backup_screen").performScrollToNode(hasText(text(R.string.backup_history_all)))
        compose.onNodeWithText(text(R.string.backup_history_all)).performClick()
        compose.onNodeWithText(text(R.string.backup_history_title)).assertIsDisplayed()

        compose.onNodeWithTag("backup_history")
            .performScrollToNode(hasContentDescription(optionsDescription(history.last())))
        compose.onNodeWithContentDescription(optionsDescription(history.last())).performClick()
        compose.onNodeWithText(text(R.string.backup_restore)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.backup_delete)).performClick()
        compose.onNodeWithText(history.last().name).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.backup_delete)).performClick()
        compose.runOnIdle { assertEquals(listOf(history.last()), deletions) }

        compose.onNodeWithContentDescription(text(R.string.backup_history_close)).performClick()
        compose.onNodeWithTag("backup_history").assertDoesNotExist()
    }

    @Test
    fun deletingCurrentHistoryRecordKeepsCurrentRestoreAvailable() {
        var state by mutableStateOf(connectedState)
        val restores = mutableListOf<DriveBackupEntry>()
        showScreen(
            state = { state },
            onDelete = { record -> state = state.copy(history = state.history.filterNot { it.id == record.id }) },
            onRestore = restores::add,
        )
        openMenu(newestRecord)
        compose.onNodeWithText(text(R.string.backup_delete)).performClick()
        compose.onNodeWithText(text(R.string.backup_delete)).performClick()
        openCurrentRestore()
        compose.onNodeWithText(newest.name).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.backup_restore)).performClick()
        compose.runOnIdle { assertEquals(listOf(newest), restores) }
    }

    @Test
    fun localHistoryCanBeDeletedWithoutDriveAuthorization() {
        val deletions = mutableListOf<BackupHistoryEntry>()
        showScreen(
            state = { connectedState.copy(accountEmail = null, currentBackup = null, settings = connectedState.settings.copy(needsAuthorization = true)) },
            onDelete = deletions::add,
        )
        compose.onNodeWithText(text(R.string.backup_restore_latest)).assertDoesNotExist()
        openMenu(older)
        compose.onNodeWithText(text(R.string.backup_delete)).performClick()
        compose.onNodeWithText(text(R.string.backup_delete)).performClick()
        compose.runOnIdle { assertEquals(listOf(older), deletions) }
    }

    @Test
    fun timelineMenusAreDisabledDuringManualAutomaticAndSettingsOperations() {
        var state by mutableStateOf(connectedState.copy(phase = DriveBackupPhase.DELETING))
        showScreen(state = { state })
        listOf(
            connectedState.copy(phase = DriveBackupPhase.DELETING),
            connectedState.copy(phase = DriveBackupPhase.UPLOADING),
            connectedState.copy(automaticRunning = true),
            connectedState.copy(isSavingSettings = true),
        ).forEach { busyState ->
            compose.runOnIdle { state = busyState }
            listOf(newestRecord, older).forEach { entry ->
                scrollToEntry(entry)
                compose.onNodeWithContentDescription(optionsDescription(entry))
                    .assertIsDisplayed().assertIsNotEnabled()
            }
            compose.onNodeWithText(text(R.string.backup_delete)).assertDoesNotExist()
        }
    }

    private fun showScreen(
        state: () -> DriveBackupUiState = { connectedState },
        onDelete: (BackupHistoryEntry) -> Unit = {},
        onRestore: (DriveBackupEntry) -> Unit = {},
    ) {
        compose.setContent {
            ScaniqoTheme {
                DriveBackupScreen(
                    state = state(),
                    onBack = {},
                    onConnect = {},
                    onBackup = {},
                    onRestore = onRestore,
                    onDisconnect = {},
                    onCancel = {},
                    onDelete = onDelete,
                )
            }
        }
    }

    private fun openCurrentRestore() {
        compose.onNodeWithTag("backup_screen").performScrollToNode(hasText(text(R.string.backup_restore_latest)))
        compose.onNodeWithText(text(R.string.backup_restore_latest)).performClick()
    }

    private fun openMenu(entry: BackupHistoryEntry) {
        scrollToEntry(entry)
        compose.onNodeWithContentDescription(optionsDescription(entry)).assertIsEnabled().performClick()
    }

    private fun scrollToEntry(entry: BackupHistoryEntry) {
        compose.onNodeWithTag("backup_screen").performScrollToNode(hasContentDescription(optionsDescription(entry)))
    }

    private fun optionsDescription(entry: BackupHistoryEntry): String = context.getString(
        R.string.backup_entry_options,
        bidiArgument(context.localizedDateTime(entry.createdAtMillis), context.appLocale()),
    )

    private fun text(id: Int): String = context.getString(id)
}
