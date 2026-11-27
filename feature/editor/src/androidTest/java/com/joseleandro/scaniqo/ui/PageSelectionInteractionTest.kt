package com.joseleandro.scaniqo.ui

import com.joseleandro.scaniqo.core.resources.R as CoreResourcesR
import com.joseleandro.scaniqo.feature.editor.R

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.joseleandro.scaniqo.domain.model.*
import com.joseleandro.scaniqo.ui.documentEditor.components.DocumentPagesSheetContent
import com.joseleandro.scaniqo.ui.documentEditor.components.PageReorderMode
import com.joseleandro.scaniqo.ui.documentEditor.event.DocumentToolsEvent
import com.joseleandro.scaniqo.ui.documentEditor.state.DocumentToolsUiState
import com.joseleandro.scaniqo.ui.theme.ScaniqoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
        compose.onNodeWithText(text(CoreResourcesR.string.pages_move_from)).assertDoesNotExist()
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

    @Test fun draggedPageKeepsItsSizeAndRemainsInsideTheViewportAtBothEdges() {
        val events = mutableListOf<DocumentToolsEvent>()
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(400.dp, 640.dp))) {
                CompositionLocalProvider(LocalInspectionMode provides true) {
                    ScaniqoTheme {
                        DocumentPagesSheetContent(
                            state = DocumentToolsUiState(
                                workspace = PdfWorkspace(1, "drag-pages", "preview",
                                    (1..8).map { PdfPageSpec("page-$it", "preview", it - 1) }),
                                selectedPageId = "page-1",
                            ),
                            onEvent = { events += it },
                        )
                    }
                }
            }
        }
        val viewport = compose.onNodeWithTag("page_reorder_viewport")
        val page = compose.onNodeWithText(text(R.string.pages_page, 1)).fetchSemanticsNode().boundsInRoot
        val bounds = viewport.fetchSemanticsNode().boundsInRoot
        compose.mainClock.autoAdvance = false
        viewport.performTouchInput {
            down(Offset(page.center.x - bounds.left, page.center.y - bounds.top))
        }
        compose.mainClock.advanceTimeBy(700)
        viewport.performTouchInput { moveBy(Offset(0f, 24f), delayMillis = 32) }
        compose.mainClock.advanceTimeBy(32)
        val floating = compose.onNodeWithTag("page_reorder_drag_preview", useUnmergedTree = true)
        floating.assertExists()
        val original = floating.getUnclippedBoundsInRoot()
        fun assertInside() {
            val frame = viewport.getUnclippedBoundsInRoot()
            val card = floating.getUnclippedBoundsInRoot()
            assertTrue(card.left > frame.left)
            assertTrue(card.right < frame.right)
            assertTrue(card.top >= frame.top)
            assertTrue(card.bottom <= frame.bottom)
            assertEquals(original.right - original.left, card.right - card.left)
            assertEquals(original.bottom - original.top, card.bottom - card.top)
        }
        assertInside()
        viewport.performTouchInput { moveTo(Offset(center.x, -100f), delayMillis = 32) }
        compose.mainClock.advanceTimeBy(32)
        assertInside()
        viewport.performTouchInput { moveTo(Offset(center.x, height + 100f), delayMillis = 32) }
        compose.mainClock.advanceTimeBy(32)
        assertInside()
        viewport.performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        floating.assertDoesNotExist()
        compose.runOnIdle {
            assertTrue(events.filterIsInstance<DocumentToolsEvent.ReorderPages>().size <= 1)
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

}
