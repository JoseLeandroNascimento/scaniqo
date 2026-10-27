package com.joseleandro.scaniqo.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import com.joseleandro.scaniqo.R
import com.joseleandro.scaniqo.ui.documents.state.DocumentsUiState
import com.joseleandro.scaniqo.ui.theme.ScaniqoTheme
import com.joseleandro.scaniqo.ui.toolsHub.ToolsHubScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ToolsHubBannerInteractionTest {
    @get:Rule val compose = createComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun text(id: Int) = context.getString(id)

    @Test
    fun debugBuildUsesGoogleSampleApplicationAndAllPlacementIds() {
        assertEquals(
            "ca-app-pub-3940256099942544~3347511713",
            text(R.string.admob_app_id),
        )
        assertEquals(
            "ca-app-pub-3940256099942544/9214589741",
            text(R.string.admob_banner_unit_id),
        )
        assertEquals(
            "ca-app-pub-3940256099942544/6300978111",
            text(R.string.admob_editor_banner_unit_id),
        )
        assertEquals(
            "ca-app-pub-3940256099942544/2247696110",
            text(R.string.admob_documents_native_unit_id),
        )
        assertEquals(
            "ca-app-pub-3940256099942544/5224354917",
            text(R.string.admob_tools_rewarded_unit_id),
        )
    }

    @Test
    fun bannerIsHiddenDuringSearchAndReturnsWhenSearchCloses() {
        compose.setContent { TestToolsHub() }
        val banner = compose.onNodeWithText(FAKE_BANNER)
        banner.assertIsDisplayed()

        compose.onNodeWithContentDescription(text(R.string.tool_catalog_search)).performClick()
        banner.assertDoesNotExist()

        compose.onNodeWithContentDescription(text(R.string.tools_hub_back)).performClick()
        banner.assertIsDisplayed()
    }

    @Test
    fun bannerIsHiddenDuringDocumentSelectionAndReturnsToTheCatalog() {
        compose.setContent { TestToolsHub() }
        val banner = compose.onNodeWithText(FAKE_BANNER)
        banner.assertIsDisplayed()

        compose.onNodeWithText(text(R.string.tool_catalog_fill)).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.quick_tools_sign_hint)).performClick()
        banner.assertDoesNotExist()
        compose.onNodeWithContentDescription(text(R.string.tool_catalog_search)).assertDoesNotExist()

        compose.onNodeWithContentDescription(text(R.string.tools_hub_back)).performClick()
        banner.assertIsDisplayed()
        compose.onNodeWithContentDescription(text(R.string.tool_catalog_search)).assertIsDisplayed()
    }

    @Test
    fun bannerIsRemovedWhileToolsAreDisabled() {
        var enabled by mutableStateOf(true)
        compose.setContent { TestToolsHub(enabled) }
        val banner = compose.onNodeWithText(FAKE_BANNER)
        banner.assertIsDisplayed()

        compose.runOnIdle { enabled = false }
        banner.assertDoesNotExist()

        compose.runOnIdle { enabled = true }
        banner.assertIsDisplayed()
    }

    @Composable
    private fun TestToolsHub(enabled: Boolean = true) {
        ScaniqoTheme {
            ToolsHubScreen(
                documents = DocumentsUiState(isLoading = false),
                enabled = enabled,
                onBack = {},
                onImport = {},
                onMerge = {},
                onSignDocument = {},
                onImportForSign = {},
                onUtility = {},
                onEdit = { _, _ -> },
                onImportForEditor = {},
                onRetryDocuments = {},
                banner = { Text(FAKE_BANNER) },
            )
        }
    }

    private companion object {
        const val FAKE_BANNER = "Fake banner slot"
    }
}
