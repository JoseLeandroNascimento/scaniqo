package com.joseleandro.scaniqo.ui.pdfTools

import com.joseleandro.scaniqo.core.resources.R as CoreResourcesR
import com.joseleandro.scaniqo.feature.pdftools.R

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.joseleandro.scaniqo.domain.model.*
import com.joseleandro.scaniqo.ui.theme.ScaniqoTheme
import org.junit.Rule
import org.junit.Test

class PdfUtilityPageSelectionInteractionTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun text(id: Int, vararg arguments: Any) = context.getString(id, *arguments)

    @Test fun clearedSelectionRemainsEmptyAfterRestorationAndRangesConfirmExplicitly() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            var selection by rememberSaveable(stateSaver = PdfUtilityPageSelectionSaver) {
                mutableStateOf(PdfUtilityPageSelectionState())
            }
            CompositionLocalProvider(LocalInspectionMode provides true) {
                ScaniqoTheme {
                    PdfUtilityWorkspace(
                        tool = PdfUtility.EXTRACT_PAGES, state = PdfUtilityUiState(loading = false),
                        document = DocumentModel(1, "Preview", "preview", 6, 0, 1200),
                        pageSelection = selection, password = "", format = PdfImageFormat.JPEG,
                        compression = PdfCompressionLevel.BALANCED, externalPicker = false,
                        onBack = {}, onChooseDocument = {}, onPageSelection = { selection = it },
                        onPassword = {}, onFormat = {}, onCompression = {}, onAction = {},
                        onRetry = {}, onOpenDocument = {},
                    )
                }
            }
        }
        compose.onNodeWithText(text(R.string.utility_workspace_clear)).performScrollTo().performClick()
        val emptyAction = text(R.string.utility_extract_action)
        compose.onNodeWithText(emptyAction).assertIsNotEnabled()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText(emptyAction).assertIsNotEnabled()
        compose.onNodeWithText(text(R.string.pages_v2_intervals)).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.pages_v2_from)).performScrollTo().performTextInput("2")
        compose.onNodeWithText(text(R.string.pages_v2_until)).performScrollTo().performTextInput("5")
        compose.onNodeWithText(text(R.string.pages_v2_add_range)).performScrollTo().performClick()
        compose.onNodeWithText(emptyAction).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.pages_v2_confirm_selection)).assertIsEnabled().performClick()
        val confirmedAction = context.resources.getQuantityString(R.plurals.pages_v2_create_pdf, 4, 4)
        compose.onNodeWithText(confirmedAction).assertIsEnabled()
        compose.onNodeWithContentDescription(text(R.string.pages_v2_edit_range, "2-5"))
            .performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.pages_v2_edit_list)).performScrollTo().performClick()
        compose.onNodeWithText(text(CoreResourcesR.string.utility_pages)).performScrollTo().performTextReplacement("5, 2-4, 1")
        compose.onNodeWithText(text(R.string.pages_v2_confirm_selection)).assertIsEnabled().performClick()
        val reorderedAction = context.resources.getQuantityString(R.plurals.pages_v2_create_pdf, 5, 5)
        compose.onNodeWithText(reorderedAction).assertIsEnabled()
    }
}
