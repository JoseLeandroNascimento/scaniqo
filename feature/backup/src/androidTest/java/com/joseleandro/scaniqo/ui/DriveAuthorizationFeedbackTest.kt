package com.joseleandro.scaniqo.ui

import com.joseleandro.scaniqo.feature.backup.R

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.platform.app.InstrumentationRegistry
import com.joseleandro.scaniqo.ui.backup.DriveBackupScreen
import com.joseleandro.scaniqo.ui.backup.state.DriveBackupProblem
import com.joseleandro.scaniqo.ui.backup.state.DriveBackupUiState
import com.joseleandro.scaniqo.ui.theme.ScaniqoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class DriveAuthorizationFeedbackTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun canceledAuthorizationKeepsTheReasonVisibleAndAllowsRetry() {
        var state by mutableStateOf(
            DriveBackupUiState(problem = DriveBackupProblem.AUTHORIZATION_CANCELED),
        )
        var retries = 0
        val message = context.getString(R.string.backup_error_auth_canceled)
        val retry = context.getString(R.string.backup_retry)

        compose.setContent {
            ScaniqoTheme {
                DriveBackupScreen(
                    state = state,
                    onBack = {},
                    onConnect = {},
                    onBackup = {},
                    onRestore = {},
                    onDisconnect = {},
                    onCancel = {},
                    onRetry = { retries++ },
                )
            }
        }

        compose.onNodeWithTag("backup_screen").performScrollToNode(hasText(message))
        compose.onNodeWithText(message).assertIsDisplayed()

        compose.runOnIdle { state = state.copy(localDocumentCount = 0) }
        compose.onNodeWithTag("backup_screen").performScrollToNode(hasText(message))
        compose.onNodeWithText(message).assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, retries) }

        compose.onNodeWithTag("backup_screen").performScrollToNode(hasText(retry))
        compose.onNodeWithText(retry).assertIsDisplayed().assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, retries) }
    }
}
