package com.joseleandro.scaniqo.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.joseleandro.scaniqo.core.documentui.R
import com.joseleandro.scaniqo.ui.documentCommon.components.DocumentMergeSelectionContent
import com.joseleandro.scaniqo.ui.documentCommon.model.DocumentMergeItem
import com.joseleandro.scaniqo.ui.documentCommon.state.DocumentMergeSelectionState
import com.joseleandro.scaniqo.ui.theme.ScaniqoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DocumentMergeDragInteractionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun draggedPdfKeepsItsWholeCardInsideTheViewportAndCommitsTheChosenOrder() {
        val items = (1..3).map { DocumentMergeItem("content://fixture/$it", "PDF $it", pageCount = it) }
        val selection = DocumentMergeSelectionState(items)
        val confirmed = mutableListOf<List<DocumentMergeItem>>()
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(400.dp, 640.dp))) {
                CompositionLocalProvider(LocalInspectionMode provides true) {
                    ScaniqoTheme {
                        Surface(Modifier.fillMaxSize()) {
                            DocumentMergeSelectionContent(
                                documents = emptyList(), selection = selection, minimumSelection = 2,
                                modifier = Modifier.fillMaxSize(), onPickFiles = {}, onConfirm = { confirmed += it },
                            )
                        }
                    }
                }
            }
        }
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(3)
        val firstCard = compose.onNode(
            SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions) and hasAnyDescendant(hasText("PDF 1")),
        )
        val original = firstCard.getUnclippedBoundsInRoot()
        val originalPixels = firstCard.fetchSemanticsNode().boundsInRoot
        val viewport = compose.onNodeWithTag("merge_reorder_viewport")
        val viewportPixels = viewport.fetchSemanticsNode().boundsInRoot
        compose.mainClock.autoAdvance = false
        viewport.performTouchInput {
            down(Offset(originalPixels.center.x - viewportPixels.left, originalPixels.center.y - viewportPixels.top))
        }
        compose.mainClock.advanceTimeBy(700)
        viewport.performTouchInput { moveBy(Offset(0f, 24f), delayMillis = 32) }
        compose.mainClock.advanceTimeBy(32)
        val floating = compose.onNodeWithTag("merge_reorder_drag_preview", useUnmergedTree = true)
        floating.assertExists()

        fun assertWholeCardIsVisible() {
            val frame = viewport.getUnclippedBoundsInRoot()
            val card = floating.getUnclippedBoundsInRoot()
            assertTrue("The left edge must have room inside the clipped viewport", card.left > frame.left)
            assertTrue("The right edge must have room inside the clipped viewport", card.right < frame.right)
            assertTrue("The top edge must remain visible", card.top >= frame.top)
            assertTrue("The bottom edge must remain visible", card.bottom <= frame.bottom)
            assertEquals(original.right - original.left, card.right - card.left)
            assertEquals(original.bottom - original.top, card.bottom - card.top)

            // A área efetivamente visível também deve manter o tamanho; limites não recortados sozinhos não bastam.
            val visible = floating.fetchSemanticsNode().boundsInRoot
            assertEquals(originalPixels.width, visible.width, 1f)
            assertEquals(originalPixels.height, visible.height, 1f)
        }

        assertWholeCardIsVisible()
        viewport.performTouchInput { moveTo(Offset(center.x, -100f), delayMillis = 32) }
        compose.mainClock.advanceTimeBy(32)
        assertWholeCardIsVisible()
        viewport.performTouchInput { moveTo(Offset(center.x, height + 100f), delayMillis = 32) }
        compose.mainClock.advanceTimeBy(32)
        assertWholeCardIsVisible()
        compose.runOnIdle {
            assertEquals(items, selection.items)
            assertTrue(confirmed.isEmpty())
        }

        viewport.performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        floating.assertDoesNotExist()
        val expected = listOf(items[1], items[2], items[0])
        compose.runOnIdle { assertEquals(expected, selection.items) }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val confirmLabel = context.resources.getQuantityString(R.plurals.merge_app_confirm_count, 3, 3)
        compose.onNodeWithText(confirmLabel).assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(listOf(expected), confirmed) }
    }
}
