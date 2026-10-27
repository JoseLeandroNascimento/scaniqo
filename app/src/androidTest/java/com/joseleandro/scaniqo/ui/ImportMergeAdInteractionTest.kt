package com.joseleandro.scaniqo.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.test.platform.app.InstrumentationRegistry
import com.joseleandro.scaniqo.R
import com.joseleandro.scaniqo.ui.documentCommon.components.DocumentMergeSelectionContent
import com.joseleandro.scaniqo.ui.documentCommon.components.DocumentMetadataSheet
import com.joseleandro.scaniqo.ui.documentCommon.model.DocumentMergeItem
import com.joseleandro.scaniqo.ui.documentCommon.state.DocumentMergeSelectionState
import com.joseleandro.scaniqo.ui.documentCommon.state.DocumentMetadataUiState
import com.joseleandro.scaniqo.ui.theme.ScaniqoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ImportMergeAdInteractionTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun text(id: Int) = context.getString(id)

    @Test fun importVideoStartsOnlyFromExplicitSaveButtonAndNeverFromImeDone() {
        var saves = 0
        compose.setContent {
            ScaniqoTheme {
                DocumentMetadataSheet(
                    editor = DocumentMetadataUiState(name = "Imported contract", requiresImportAd = true),
                    tags = emptyList(),
                    onNameChange = {},
                    onTagChange = {},
                    onSave = { saves++ },
                    onDismiss = {},
                    saveShowsVideo = true,
                )
            }
        }
        compose.onNodeWithText(text(R.string.tool_ad_watch_save)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.tool_ad_reward_hint)).assertIsDisplayed()
        compose.onNode(hasSetTextAction()).performImeAction()
        compose.runOnIdle { assertEquals(0, saves) }
        compose.onNodeWithText(text(R.string.tool_ad_watch_save)).performClick()
        compose.runOnIdle { assertEquals(1, saves) }
    }

    @Test fun ordinaryMetadataSaveKeepsNormalActionAndImeShortcut() {
        var saves = 0
        compose.setContent {
            ScaniqoTheme {
                DocumentMetadataSheet(
                    editor = DocumentMetadataUiState(documentId = 9, name = "Contract"),
                    tags = emptyList(),
                    onNameChange = {},
                    onTagChange = {},
                    onSave = { saves++ },
                    onDismiss = {},
                )
            }
        }
        compose.onNodeWithText(text(R.string.tool_ad_watch_save)).assertDoesNotExist()
        compose.onNode(hasSetTextAction()).performImeAction()
        compose.runOnIdle { assertEquals(1, saves) }
    }

    @Test fun mergeVideoUsesExistingApplyButtonAndKeepsSelectedOrderWithoutNewDialog() {
        val selected = listOf(
            DocumentMergeItem("content://second", "Second PDF"),
            DocumentMergeItem("content://first", "First PDF"),
        )
        var delivered = emptyList<DocumentMergeItem>()
        compose.setContent {
            ScaniqoTheme {
                Surface {
                    DocumentMergeSelectionContent(
                        documents = emptyList(),
                        selection = DocumentMergeSelectionState(selected),
                        minimumSelection = 2,
                        modifier = Modifier.fillMaxSize(),
                        onPickFiles = {},
                        onConfirm = { delivered = it },
                        actionShowsVideo = true,
                    )
                }
            }
        }
        compose.onAllNodes(isDialog()).assertCountEquals(0)
        compose.onNodeWithText(text(R.string.tool_ad_watch_apply)).assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(selected, delivered) }
        compose.onAllNodes(isDialog()).assertCountEquals(0)
    }
}
