package com.joseleandro.scaniqo.ui

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import com.joseleandro.scaniqo.R
import com.joseleandro.scaniqo.domain.model.PdfUtility
import com.joseleandro.scaniqo.ui.documents.state.DocumentsUiState
import com.joseleandro.scaniqo.ui.theme.ScaniqoTheme
import com.joseleandro.scaniqo.ui.toolsHub.ToolsHubScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ToolsHubSearchInteractionTest {
    @get:Rule val compose = createComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun text(id: Int) = context.getString(id)

    @Test fun searchFiltersRestoresClearsAndKeepsCategoryWhenClosed() {
        var backCalls = 0
        var mergeCalls = 0
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            ScaniqoTheme {
                ToolsHubScreen(
                    documents = DocumentsUiState(isLoading = false),
                    enabled = true,
                    onBack = { backCalls++ },
                    onImport = {},
                    onMerge = { mergeCalls++ },
                    onSignDocument = {},
                    onImportForSign = {},
                    onUtility = {},
                    onEdit = { _, _ -> },
                    onImportForEditor = {},
                    onRetryDocuments = {},
                )
            }
        }

        val organizeChip = compose.onNodeWithText(text(R.string.tool_catalog_organize))
        organizeChip.performClick()
        organizeChip.assertIsSelected()
        val searchLabel = text(R.string.tool_catalog_search)
        compose.onNodeWithContentDescription(searchLabel).performClick()
        val field = compose.onNode(hasSetTextAction() and hasContentDescription(searchLabel))
        field.assertExists()
        val mergeTitle = text(R.string.quick_tools_merge)
        val mergeHint = text(R.string.quick_tools_merge_hint)
        val importHint = text(R.string.quick_tools_import_hint)
        field.performTextInput(mergeTitle)
        field.performImeAction()
        compose.onNodeWithText(mergeHint).assertExists()
        compose.onNodeWithText(importHint).assertDoesNotExist()

        restoration.emulateSavedInstanceStateRestore()
        field.assertExists()
        compose.onNodeWithText(mergeHint).assertExists()
        compose.onNodeWithText(importHint).assertDoesNotExist()

        compose.onNodeWithContentDescription(text(R.string.tool_catalog_clear)).performClick()
        field.assertExists()
        field.performImeAction()
        compose.onNodeWithText(importHint).assertExists()

        compose.onNodeWithContentDescription(text(R.string.tools_hub_back)).performClick()
        field.assertDoesNotExist()
        compose.onNodeWithContentDescription(searchLabel).assertExists()
        compose.onNodeWithText(importHint).assertExists()
        organizeChip.assertIsSelected()
        compose.runOnIdle { assertEquals(0, backCalls) }

        compose.onNodeWithText(mergeHint).performClick()
        compose.runOnIdle { assertEquals(1, mergeCalls) }
    }

    @Test fun searchResultOpensTheMatchingPdfUtility() {
        val opened = mutableListOf<PdfUtility>()
        compose.setContent {
            ScaniqoTheme {
                ToolsHubScreen(
                    documents = DocumentsUiState(isLoading = false),
                    enabled = true,
                    onBack = {},
                    onImport = {},
                    onMerge = {},
                    onSignDocument = {},
                    onImportForSign = {},
                    onUtility = { opened += it },
                    onEdit = { _, _ -> },
                    onImportForEditor = {},
                    onRetryDocuments = {},
                )
            }
        }

        val searchLabel = text(R.string.tool_catalog_search)
        compose.onNodeWithContentDescription(searchLabel).performClick()
        compose.onNode(hasSetTextAction() and hasContentDescription(searchLabel))
            .performTextInput(text(R.string.tool_catalog_compress))
        compose.onNode(hasSetTextAction() and hasContentDescription(searchLabel)).performImeAction()
        compose.onNodeWithText(text(R.string.tool_catalog_compress_hint)).performClick()
        compose.runOnIdle { assertEquals(listOf(PdfUtility.COMPRESS_PDF), opened) }
    }
}
