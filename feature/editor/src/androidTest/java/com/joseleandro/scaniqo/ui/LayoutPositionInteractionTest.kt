package com.joseleandro.scaniqo.ui

import com.joseleandro.scaniqo.feature.editor.R

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import com.joseleandro.scaniqo.domain.model.DocumentModel
import com.joseleandro.scaniqo.domain.model.PdfContentPosition
import com.joseleandro.scaniqo.domain.model.PdfPageLayout
import com.joseleandro.scaniqo.domain.model.PdfPagePreview
import com.joseleandro.scaniqo.domain.model.PdfPageSpec
import com.joseleandro.scaniqo.domain.model.PdfWorkspace
import com.joseleandro.scaniqo.ui.documentEditor.DocumentEditorToolScreen
import com.joseleandro.scaniqo.ui.documentEditor.event.DocumentToolsEvent
import com.joseleandro.scaniqo.ui.documentEditor.model.DocumentTool
import com.joseleandro.scaniqo.ui.documentEditor.state.DocumentToolPreviewState
import com.joseleandro.scaniqo.ui.documentEditor.state.DocumentToolsUiState
import com.joseleandro.scaniqo.ui.theme.ScaniqoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs

class LayoutPositionInteractionTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun text(id: Int) = context.getString(id)

    @Test fun accessibleAlignmentActionsPreviewEachAxisAndKeepTheDraftWhenReopened() {
        val events = showEditor(initialPosition = PdfContentPosition(.23f, .72f))
        compose.onNodeWithTag("layout_open_controls").assertIsDisplayed()
        compose.onNodeWithTag("layout_position_pad").assertIsNotDisplayed()
        compose.onNodeWithTag("layout_scope_CURRENT").assertDoesNotExist()

        openControls()
        listOf(0f, .5f, 1f).forEachIndexed { index, horizontal ->
            selectAlignment("horizontal", index)
            assertPreviewPosition(events, horizontal, .72f)
        }
        listOf(0f, .5f, 1f).forEachIndexed { index, vertical ->
            selectAlignment("vertical", index)
            assertPreviewPosition(events, 1f, vertical)
        }

        closeControls()
        compose.onNodeWithTag("layout_position_pad").assertIsNotDisplayed()
        openControls()
        compose.onNodeWithTag("layout_position_pad").performScrollTo().assertIsDisplayed()
        assertPreviewPosition(events, 1f, 1f)
        closeControls()
        apply()

        compose.runOnIdle {
            val applied = events.filterIsInstance<DocumentToolsEvent.ApplyLayout>().single()
            assertEquals(setOf("page-1"), applied.pageIds)
            assertEquals(PdfContentPosition(1f, 1f), applied.contentPosition)
            assertEquals(.75f, applied.contentScale)
            assertFalse(applied.allPages)
        }
    }

    @Test fun draggingTheDocumentUpdatesThePreviewAndCreatesOneUndoStep() {
        val events = showEditor(initialPosition = PdfContentPosition(.23f, .72f))
        openControls()
        val pad = compose.onNodeWithTag("layout_position_pad").performScrollTo().assertIsEnabled()
        val padBounds = pad.fetchSemanticsNode().boundsInRoot
        val documentBounds = compose.onNodeWithTag(
            "layout_position_document",
            useUnmergedTree = true,
        ).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val start = documentBounds.center - padBounds.topLeft

        pad.performTouchInput {
            swipe(start, Offset(width - 1f, height - 1f), durationMillis = 400)
        }
        assertPreviewPosition(events, 1f, 1f)

        compose.onNodeWithContentDescription(text(R.string.viewer_undo_description))
            .assertIsEnabled().performClick()
        assertPreviewPosition(events, .23f, .72f)
        compose.onNodeWithContentDescription(text(R.string.viewer_undo_description)).assertIsNotEnabled()

        compose.onNodeWithContentDescription(text(R.string.viewer_redo_description))
            .assertIsEnabled().performClick()
        assertPreviewPosition(events, 1f, 1f)
    }

    @Test fun allPagesScopeIsSelectedThroughOverflowAndPreservedUntilApply() {
        val events = showEditor()
        compose.onNodeWithTag("layout_apply_scope").assertDoesNotExist()
        compose.onNodeWithTag("layout_scope_ALL").assertDoesNotExist()
        openScope()
        compose.onNodeWithTag("layout_scope_CURRENT").assertIsSelected()
        compose.onNodeWithTag("layout_scope_ALL").performClick()
        compose.onNodeWithTag("layout_scope_ALL").assertDoesNotExist()

        openScope()
        compose.onNodeWithTag("layout_scope_ALL").assertIsSelected().performClick()
        openControls()
        selectAlignment("horizontal", 0)
        closeControls()
        compose.onNodeWithTag("layout_scope_ALL").assertDoesNotExist()
        apply()

        compose.runOnIdle {
            val applied = events.filterIsInstance<DocumentToolsEvent.ApplyLayout>().single()
            assertTrue(applied.allPages)
            assertEquals(setOf("page-1", "page-2", "page-3"), applied.pageIds)
            assertEquals(0f, applied.contentPosition!!.horizontal, .001f)
        }
    }

    @Test fun selectedPagesScopeUsesOnlyConfirmedPageIds() {
        val events = showEditor()
        openScope()
        compose.onNodeWithTag("layout_scope_SELECTED").performClick()
        compose.onNodeWithText(context.getString(R.string.document_scope_page, 1))
            .performScrollTo().performClick()
        compose.onNodeWithText(context.getString(R.string.document_scope_page, 2))
            .performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.document_scope_confirm)).performClick()
        compose.onNodeWithTag("layout_scope_SELECTED").assertDoesNotExist()

        openControls()
        selectAlignment("vertical", 0)
        closeControls()
        apply()
        compose.runOnIdle {
            val applied = events.filterIsInstance<DocumentToolsEvent.ApplyLayout>().single()
            assertFalse(applied.allPages)
            assertEquals(setOf("page-2"), applied.pageIds)
            assertEquals(0f, applied.contentPosition!!.vertical, .001f)
        }
    }

    @Test fun sizeAndRotationShareTheSameLiveDraft() {
        val events = showEditor()
        openControls()
        compose.onNodeWithTag("layout_control_SIZE").assertIsDisplayed().performClick().assertIsSelected()
        setProgress("layout_size_slider", 1.37f)
        compose.onNodeWithTag("layout_control_ROTATION").assertIsDisplayed().performClick().assertIsSelected()
        compose.onNodeWithTag("layout_rotate_clockwise").performScrollTo().performClick()
        compose.runOnIdle {
            val preview = events.filterIsInstance<DocumentToolsEvent.PreviewLayout>().last()
            assertEquals(1.37f, preview.layout.contentScale, .001f)
            assertEquals(1, preview.layout.rotationQuarterTurns)
            assertTrue(events.none { it is DocumentToolsEvent.ApplyLayout })
        }
        closeControls()
        openControls()
        compose.onNodeWithTag("layout_control_ROTATION").assertIsSelected()
        compose.onNodeWithTag("layout_control_SIZE").assertIsDisplayed().performClick()
        assertProgress("layout_size_slider", 1.37f)
        closeControls()
        apply()
        compose.runOnIdle {
            val applied = events.filterIsInstance<DocumentToolsEvent.ApplyLayout>().single()
            assertEquals(1.37f, applied.contentScale, .001f)
            assertEquals(1, applied.rotationQuarterTurns)
        }
    }

    @Test fun circularRotationSnapsToQuarterTurnsAndEachChangeCanBeUndone() {
        val events = showEditor()
        openControls()
        compose.onNodeWithTag("layout_control_ROTATION").assertIsDisplayed().performClick()
        setProgress("layout_rotation_dial", 134f)
        assertProgress("layout_rotation_dial", 90f)
        assertPreviewRotation(events, 1)

        setProgress("layout_rotation_dial", 226f)
        assertProgress("layout_rotation_dial", 270f)
        assertPreviewRotation(events, 3)

        compose.onNodeWithContentDescription(text(R.string.viewer_undo_description))
            .assertIsEnabled().performClick()
        assertProgress("layout_rotation_dial", 90f)
        assertPreviewRotation(events, 1)
        compose.onNodeWithContentDescription(text(R.string.viewer_undo_description))
            .assertIsEnabled().performClick()
        assertProgress("layout_rotation_dial", 0f)
        assertPreviewRotation(events, 0)
        compose.onNodeWithContentDescription(text(R.string.viewer_undo_description)).assertIsNotEnabled()
    }

    @Test fun largeFontsKeepControlsAndCloseActionReachable() {
        val events = showEditor(fontScale = 2f)
        compose.onNodeWithTag("layout_open_controls").assertIsDisplayed().performClick()
        compose.onNodeWithTag("layout_close_controls").assertIsDisplayed()
        compose.onNodeWithTag("layout_control_POSITION").assertIsSelected()
        assertTabsFillWidth()
        selectAlignment("vertical", 2)
        compose.onNodeWithTag("layout_control_SIZE").assertIsDisplayed().performClick().assertIsSelected()
        compose.onNodeWithTag("layout_control_ROTATION").assertIsDisplayed().performClick().assertIsSelected()
        closeControls()
        compose.onNodeWithTag("layout_open_controls").assertIsDisplayed()
        compose.runOnIdle {
            assertTrue(events.filterIsInstance<DocumentToolsEvent.PreviewLayout>().any {
                it.layout.contentPosition?.vertical == 1f
            })
            assertTrue(events.none { it is DocumentToolsEvent.ApplyLayout })
        }
    }

    private fun showEditor(
        fontScale: Float = 1f,
        initialPosition: PdfContentPosition? = null,
    ): MutableList<DocumentToolsEvent> {
        val events = mutableListOf<DocumentToolsEvent>()
        val pages = (1..3).map {
            PdfPageSpec(
                "page-$it", "preview", it - 1,
                layout = PdfPageLayout(contentScale = .75f, contentPosition = initialPosition),
            )
        }
        val state = DocumentToolsUiState(
            document = DocumentModel(1, "Documento", "preview", 3, 0, 0),
            workspace = PdfWorkspace(1, "layout-test", "preview", pages),
            selectedPageId = "page-1",
            tool = DocumentTool.LAYOUT,
            layoutPreview = DocumentToolPreviewState(
                preview = PdfPagePreview("preview", 0),
                pageId = "page-1",
            ),
        )
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(
                LocalInspectionMode provides true,
                LocalDensity provides Density(density, fontScale = fontScale),
            ) {
                ScaniqoTheme {
                    DocumentEditorToolScreen(
                        documentId = 1,
                        tool = DocumentTool.LAYOUT,
                        state = state,
                        documentLoading = false,
                        documentError = false,
                        enabled = true,
                        onEvent = { events += it },
                        onDraftChanged = {},
                        onBack = {},
                        onRetry = {},
                    )
                }
            }
        }
        return events
    }

    private fun openControls() {
        compose.onNodeWithTag("layout_open_controls").assertIsEnabled().performClick()
        compose.onNodeWithTag("layout_close_controls").assertIsDisplayed()
        assertTabsFillWidth()
    }

    private fun closeControls() {
        compose.onNodeWithTag("layout_close_controls").assertIsDisplayed().performClick()
        compose.onNodeWithTag("layout_open_controls").assertIsDisplayed()
    }

    private fun openScope() {
        compose.onNodeWithTag("layout_more").assertIsEnabled().performClick()
        compose.onNodeWithTag("layout_apply_scope").performClick()
    }

    private fun apply() {
        compose.onNodeWithText(text(R.string.tools_apply_compact)).assertIsEnabled().performClick()
    }

    private fun selectAlignment(axis: String, index: Int) {
        val actionIndex = if (axis == "horizontal") index else index + 3
        val actions = compose.onNodeWithTag("layout_position_pad")
            .performScrollTo().assertIsEnabled().fetchSemanticsNode()
            .config[SemanticsActions.CustomActions]
        assertEquals(6, actions.size)
        compose.runOnIdle { assertTrue(actions[actionIndex].action()) }
    }

    private fun assertTabsFillWidth() {
        val row = compose.onNodeWithTag("layout_control_tabs")
            .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val tabs = listOf("POSITION", "SIZE", "ROTATION").map { control ->
            compose.onNodeWithTag("layout_control_$control")
                .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        }
        assertEquals(row.left, tabs.first().left, 1f)
        assertEquals(row.right, tabs.last().right, 1f)
        tabs.forEach { assertEquals(row.width / tabs.size, it.width, 1f) }
        tabs.zipWithNext().forEach { (left, right) ->
            assertEquals(left.right, right.left, 1f)
        }
    }

    private fun assertPreviewPosition(
        events: List<DocumentToolsEvent>,
        horizontal: Float,
        vertical: Float,
    ) {
        compose.runOnIdle {
            val preview = events.filterIsInstance<DocumentToolsEvent.PreviewLayout>().last()
            assertEquals("page-1", preview.pageId)
            assertEquals(PdfContentPosition(horizontal, vertical), preview.layout.contentPosition)
            assertTrue(events.none { it is DocumentToolsEvent.ApplyLayout })
        }
    }

    private fun assertPreviewRotation(events: List<DocumentToolsEvent>, quarterTurns: Int) {
        compose.runOnIdle {
            val preview = events.filterIsInstance<DocumentToolsEvent.PreviewLayout>().last()
            assertEquals(quarterTurns, preview.layout.rotationQuarterTurns)
            assertTrue(events.none { it is DocumentToolsEvent.ApplyLayout })
        }
    }

    private fun setProgress(tag: String, value: Float) {
        compose.onNodeWithTag(tag).performScrollTo().assertIsEnabled()
            .performSemanticsAction(SemanticsActions.SetProgress) { it(value) }
    }

    private fun assertProgress(tag: String, value: Float) {
        compose.onNodeWithTag(tag).performScrollTo().assert(
            SemanticsMatcher("Progress is $value") {
                val progress = it.config.getOrNull(SemanticsProperties.ProgressBarRangeInfo)?.current
                progress != null && abs(progress - value) < .001f
            },
        )
    }
}
