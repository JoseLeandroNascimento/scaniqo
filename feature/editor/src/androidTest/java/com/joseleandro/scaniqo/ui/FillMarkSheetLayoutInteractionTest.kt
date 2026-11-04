package com.joseleandro.scaniqo.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.joseleandro.scaniqo.domain.model.PdfOverlay
import com.joseleandro.scaniqo.domain.model.PdfOverlayKind
import com.joseleandro.scaniqo.ui.documentEditor.components.FillMarkTransformSheet
import com.joseleandro.scaniqo.ui.theme.ScaniqoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class FillMarkSheetLayoutInteractionTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun shortNativeSheetKeepsFooterAndTabsFixedWhileChangingTabsAndScrolling() {
        var draft by mutableStateOf(PdfOverlay("layout-test", PdfOverlayKind.TEXT, text = "Cliente"))
        var applies = 0
        var cancels = 0
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale = 1.5f)) {
                ScaniqoTheme {
                    FillMarkTransformSheet(
                        draft = draft,
                        editable = true,
                        onUpdate = { draft = it },
                        onCancel = { cancels++ },
                        onApply = { applies++ },
                        maxHeight = 260.dp,
                    )
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("fill_transform_MOVE").assertIsSelected()
        val fixedLayout = fixedLayout()
        assertFixedLayout(fixedLayout)

        // The position pad and Center action exceed this short viewport.
        scrollToCenter()
        assertFixedLayout(fixedLayout)

        listOf("SIZE", "ROTATE", "MOVE").forEach { tab ->
            compose.onNodeWithTag("fill_transform_$tab")
                .assertIsDisplayed().performClick().assertIsSelected()
            compose.waitForIdle()
            assertEquals("A new tab starts at its first control", 0f, scrollOffset(), 1f)
            assertFixedLayout(fixedLayout)
        }

        // Returning to Move starts at the top, and scrolling again only moves its body.
        scrollToCenter()
        assertFixedLayout(fixedLayout)
        compose.runOnIdle {
            assertEquals(0, applies)
            assertEquals(0, cancels)
        }
        compose.onNodeWithTag("fill_transform_cancel").performClick()
        compose.onNodeWithTag("fill_transform_apply").performClick()
        compose.runOnIdle {
            assertEquals(1, cancels)
            assertEquals(1, applies)
        }
    }

    private fun scrollToCenter() {
        compose.onNodeWithTag("fill_transform_body").performTouchInput {
            // Start in the content padding, away from the draggable position pad.
            swipe(
                start = Offset(4f, height * .85f),
                end = Offset(4f, height * .15f),
                durationMillis = 400,
            )
        }
        compose.waitForIdle()
        assertTrue("Dragging the body must scroll its content", scrollOffset() > 0f)
        compose.onNodeWithTag("fill_transform_body")
            .performScrollToNode(hasTestTag("fill_position_center"))
        compose.onNodeWithTag("fill_position_center").assertIsDisplayed()
        assertTrue("The test must scroll overflowing content", scrollOffset() > 0f)
    }

    private fun scrollOffset(): Float = compose.onNodeWithTag("fill_transform_body")
        .fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()

    private fun bounds(tag: String): Rect = compose.onNodeWithTag(tag)
        .assertIsDisplayed().fetchSemanticsNode().boundsInRoot

    private fun fixedLayout() = FixedLayout(
        sheet = bounds("fill_transform_sheet"),
        tabs = bounds("fill_transform_tabs"),
        footer = bounds("fill_transform_footer"),
        cancel = bounds("fill_transform_cancel"),
        apply = bounds("fill_transform_apply"),
    )

    private fun assertFixedLayout(expected: FixedLayout) {
        val actual = fixedLayout()
        assertSameBounds("Sheet", expected.sheet, actual.sheet)
        assertSameBounds("Tabs", expected.tabs, actual.tabs)
        assertSameBounds("Footer", expected.footer, actual.footer)
        assertSameBounds("Cancel", expected.cancel, actual.cancel)
        assertSameBounds("Apply", expected.apply, actual.apply)
        assertEquals("The footer stays at the bottom of the sheet", actual.sheet.bottom, actual.footer.bottom, 1f)
        val body = bounds("fill_transform_body")
        assertEquals("Only the content between tabs and footer scrolls", actual.tabs.bottom, body.top, 1f)
        assertEquals("The body fills all space above the footer", actual.footer.top, body.bottom, 1f)
    }

    private fun assertSameBounds(label: String, expected: Rect, actual: Rect) {
        assertEquals("$label left", expected.left, actual.left, 1f)
        assertEquals("$label top", expected.top, actual.top, 1f)
        assertEquals("$label right", expected.right, actual.right, 1f)
        assertEquals("$label bottom", expected.bottom, actual.bottom, 1f)
    }

    private data class FixedLayout(
        val sheet: Rect,
        val tabs: Rect,
        val footer: Rect,
        val cancel: Rect,
        val apply: Rect,
    )
}
