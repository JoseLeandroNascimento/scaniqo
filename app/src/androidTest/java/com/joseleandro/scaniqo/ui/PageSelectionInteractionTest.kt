package com.joseleandro.scaniqo.ui

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
import com.joseleandro.scaniqo.R
import com.joseleandro.scaniqo.domain.model.*
import com.joseleandro.scaniqo.ui.documentEditor.components.DocumentPagesSheetContent
import com.joseleandro.scaniqo.ui.documentEditor.components.PageReorderMode
import com.joseleandro.scaniqo.ui.documentEditor.event.DocumentToolsEvent
import com.joseleandro.scaniqo.ui.documentEditor.state.DocumentToolsUiState
import com.joseleandro.scaniqo.ui.pdfTools.*
import com.joseleandro.scaniqo.ui.theme.ScaniqoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PageSelectionInteractionTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun text(id: Int, vararg arguments: Any) = context.getString(id, *arguments)

    @Test fun reorderModesAreExclusiveAndDirectMoveUsesSelectedPage() {
        val events = mutableListOf<DocumentToolsEvent>()
        compose.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                ScaniqoTheme {
                    DocumentPagesSheetContent(
                        state = DocumentToolsUiState(
                            workspace = PdfWorkspace(1, "pages", "preview",
                                (1..3).map { PdfPageSpec("page-$it", "preview", it - 1) }),
                            selectedPageId = "page-2",
                        ),
                        onEvent = { events += it },
                    )
                }
            }
        }
        compose.onNodeWithText(text(R.string.pages_v2_final_position)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.pages_v2_direct_mode)).performClick()
        compose.onNodeWithText(text(R.string.pages_v2_final_position)).assertExists()
        compose.onNodeWithText(text(R.string.pages_move_from)).assertDoesNotExist()
        compose.onAllNodesWithContentDescription(text(R.string.pages_drag_handle, 1)).assertCountEquals(0)
        compose.onNodeWithText(text(R.string.pages_v2_drag_mode)).performClick()
        compose.onNodeWithText(text(R.string.pages_v2_final_position)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.pages_v2_direct_mode)).performClick()
        compose.onNodeWithText(text(R.string.pages_v2_final_position)).performTextInput("1")
        compose.onNodeWithText(text(R.string.pages_v2_move_to, 1)).performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(listOf(DocumentToolsEvent.MovePageTo("page-2", 0)),
                events.filterIsInstance<DocumentToolsEvent.MovePageTo>())
        }
    }

    @Test fun reorderUndoNoticeDisappearsAfterNumberingChanges() {
        val initial = PdfWorkspace(1, "pages", "preview",
            (1..3).map { PdfPageSpec("page-$it", "preview", it - 1) })
        val state = mutableStateOf(DocumentToolsUiState(workspace = initial, selectedPageId = "page-2"))
        val undoEvents = mutableListOf<DocumentToolsEvent>()
        compose.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                ScaniqoTheme {
                    DocumentPagesSheetContent(
                        state = state.value, initialMode = PageReorderMode.DIRECT,
                        onEvent = { event ->
                            when (event) {
                                is DocumentToolsEvent.SelectPage ->
                                    state.value = state.value.copy(selectedPageId = event.pageId)
                                is DocumentToolsEvent.MovePageTo -> {
                                    val workspace = requireNotNull(state.value.workspace)
                                    val pages = workspace.pages.toMutableList()
                                    val page = pages.removeAt(pages.indexOfFirst { it.id == event.pageId })
                                    pages.add(event.targetIndex, page)
                                    state.value = state.value.copy(workspace = workspace.copy(pages = pages), canUndo = true)
                                }
                                is DocumentToolsEvent.SetNumbering -> state.value = state.value.copy(
                                    workspace = requireNotNull(state.value.workspace).copy(showPageNumbers = event.enabled),
                                )
                                DocumentToolsEvent.Undo -> undoEvents += event
                                else -> Unit
                            }
                        },
                    )
                }
            }
        }
        compose.onNodeWithText(text(R.string.pages_v2_final_position)).performTextInput("1")
        compose.onNodeWithText(text(R.string.pages_v2_move_to, 1)).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.pages_v2_moved, 1)).assertExists()
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
        compose.onNodeWithText(text(R.string.pages_v2_numbering)).performClick()
        compose.onNode(isToggleable()).performClick()
        compose.onNodeWithText(text(R.string.pages_v2_moved, 1)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.viewer_undo_description)).assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(true, state.value.workspace?.showPageNumbers)
            assertEquals(0, undoEvents.size)
        }
    }

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
        compose.onNodeWithText(text(R.string.utility_pages)).performScrollTo().performTextReplacement("5, 2-4, 1")
        compose.onNodeWithText(text(R.string.pages_v2_confirm_selection)).assertIsEnabled().performClick()
        val reorderedAction = context.resources.getQuantityString(R.plurals.pages_v2_create_pdf, 5, 5)
        compose.onNodeWithText(reorderedAction).assertIsEnabled()
    }
}
