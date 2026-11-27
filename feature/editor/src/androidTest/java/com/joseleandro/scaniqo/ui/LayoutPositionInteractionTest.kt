package com.joseleandro.scaniqo.ui

import com.joseleandro.scaniqo.feature.editor.R

import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
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
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
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
import java.util.Locale
import kotlin.math.abs

class LayoutPositionInteractionTest {
    @get:Rule val compose = createComposeRule()
    private var localizedContext: Context? = null
    private val context get() = localizedContext ?: InstrumentationRegistry.getInstrumentation().targetContext
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
        val events = showEditor(fontScale = 2f, narrowWindow = true, locale = Locale.forLanguageTag("pt-BR"))
        compose.onNodeWithTag("layout_open_controls").assertIsDisplayed().performClick()
        compose.onNodeWithTag("layout_close_controls").assertIsDisplayed()
        compose.onNodeWithTag("layout_control_POSITION").assertIsSelected()
        assertCompleteLabel(R.string.layout_interactive_position)
        selectAlignment("vertical", 2)
        compose.onNodeWithTag("layout_control_SIZE").scrollToIfScrollable().assertIsDisplayed().performClick().assertIsSelected()
        assertCompleteLabel(R.string.tools_size_compact)
        listOf(R.string.layout_with_margin, R.string.layout_v3_without_margin, R.string.tools_fill_compact)
            .forEach { assertCompleteLabel(it) }
        compose.onNodeWithTag("layout_control_ROTATION").scrollToIfScrollable().assertIsDisplayed().performClick().assertIsSelected()
        assertCompleteLabel(R.string.layout_interactive_rotate)
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
        narrowWindow: Boolean = false,
        locale: Locale? = null,
    ): MutableList<DocumentToolsEvent> {
        localizedContext = locale?.let {
            val base = InstrumentationRegistry.getInstrumentation().targetContext
            base.createConfigurationContext(Configuration(base.resources.configuration).apply { setLocale(it) })
        }
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
                LocalContext provides (localizedContext ?: LocalContext.current),
                LocalConfiguration provides (localizedContext?.resources?.configuration ?: LocalConfiguration.current),
                LocalInspectionMode provides true,
                LocalDensity provides Density(density, fontScale = fontScale),
            ) {
                ScaniqoTheme {
                    Box(if (narrowWindow) Modifier.width(320.dp) else Modifier) {
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

    private fun assertCompleteLabel(labelRes: Int) {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text(labelRes), useUnmergedTree = true)
            .scrollToIfScrollable().assertIsDisplayed()
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val layout = layouts.single()
        assertEquals("Label must remain on one line: ${text(labelRes)}", 1, layout.lineCount)
        assertFalse(
            "Label must not be clipped: ${text(labelRes)}; " +
                "widthOverflow=${layout.didOverflowWidth}, heightOverflow=${layout.didOverflowHeight}, " +
                "size=${layout.size}, constraints=${layout.layoutInput.constraints}, " +
                "lineRight=${layout.getLineRight(0)}, lineBottom=${layout.getLineBottom(0)}, " +
                "fontScale=${layout.layoutInput.density.fontScale}, style=${layout.layoutInput.style}",
            layout.hasVisualOverflow,
        )
    }

    private fun SemanticsNodeInteraction.scrollToIfScrollable(): SemanticsNodeInteraction {
        val node = fetchSemanticsNode()
        val canScroll = generateSequence(node.parent) { it.parent }
            .any { it.config.getOrNull(SemanticsActions.ScrollBy) != null }
        return if (canScroll) performScrollTo() else this
    }

    private fun assertTabsFillWidth() {
        val row = compose.onNodeWithTag("layout_control_tabs")
            .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val tabs = listOf("POSITION", "SIZE", "ROTATION").map { control ->
            compose.onNodeWithTag("layout_control_$control")
                .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        }
        // Material 3 PrimaryTabRow divides its pixel width using integer arithmetic.
        // A 1280px row has three 426px tabs and leaves the 2px remainder at the end.
        val tabWidth = (row.width.toInt() / tabs.size).toFloat()
        assertEquals(row.left, tabs.first().left, 1f)
        assertEquals(row.left + tabWidth * tabs.size, tabs.last().right, 1f)
        tabs.forEach { assertEquals(tabWidth, it.width, 1f) }
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
