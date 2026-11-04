package com.joseleandro.scaniqo.ui

import com.joseleandro.scaniqo.feature.editor.R

import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.click
import androidx.compose.ui.text.input.TextFieldValue
import com.joseleandro.scaniqo.domain.model.PdfOverlay
import com.joseleandro.scaniqo.domain.model.PdfOverlayKind
import com.joseleandro.scaniqo.domain.model.PdfPagePreview
import com.joseleandro.scaniqo.domain.model.PdfPageSpec
import com.joseleandro.scaniqo.domain.model.PdfWorkspace
import com.joseleandro.scaniqo.ui.documentEditor.components.DocumentFillMarkStage
import com.joseleandro.scaniqo.ui.documentEditor.model.DocumentTool
import com.joseleandro.scaniqo.ui.documentEditor.state.DocumentPagePreviewState
import com.joseleandro.scaniqo.ui.documentEditor.state.DocumentToolsUiState
import com.joseleandro.scaniqo.ui.theme.ScaniqoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Exercises pointer dispatch, including the parent stage and its contextual toolbar. */
class FillMarkGestureInteractionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun draggingTheBodyMovesTheElementAndCommitsOnce() {
        val fixture = showStage()
        val original = fixture.draft
        val start = selectionCenterInViewport()
        val distance = 36f * compose.activity.resources.displayMetrics.density

        viewport().performTouchInput {
            swipe(start, start + Offset(distance, distance), durationMillis = 320)
        }

        compose.runOnIdle {
            assertEquals(1, fixture.updates)
            assertTrue(fixture.draft.x > original.x)
            assertTrue(fixture.draft.y > original.y)
            assertEquals(original.width, fixture.draft.width, .00001f)
            assertEquals(original.height, fixture.draft.height, .00001f)
            assertEquals(0, fixture.selections)
            assertEquals(0, fixture.insertions)
        }
        compose.onNodeWithTag("gesture_context_action").assertIsDisplayed()
    }

    @Test fun draggingTextCornerChangesItsBoxWithoutChangingTheFont() {
        val fixture = showStage()
        val original = fixture.draft
        val distance = 32f * compose.activity.resources.displayMetrics.density

        compose.onNodeWithTag("fill_selection_handle_2").assertIsDisplayed().performTouchInput {
            swipe(center, center + Offset(distance, distance), durationMillis = 320)
        }

        compose.runOnIdle {
            assertEquals(1, fixture.updates)
            assertTrue(fixture.draft.width > original.width)
            assertTrue(fixture.draft.height > original.height)
            assertEquals(original.fontSize, fixture.draft.fontSize, 0f)
            assertEquals(original.x, fixture.draft.x, .00001f)
            assertEquals(original.y, fixture.draft.y, .00001f)
            assertTrue(fixture.draft.wrapText)
        }
    }

    @Test fun rotationHandleRotatesAroundTheElementCenter() {
        val fixture = showStage()
        val original = fixture.draft
        val viewportOrigin = viewport().fetchSemanticsNode().boundsInRoot.topLeft
        val center = selectionCenterInViewport()
        val start = compose.onNodeWithTag("fill_selection_handle_4")
            .assertIsDisplayed().fetchSemanticsNode().boundsInRoot.center - viewportOrigin
        val radius = (start - center).getDistance()
        val end = center + Offset(radius, 0f)

        viewport().performTouchInput {
            swipe(start, end, durationMillis = 400)
        }

        compose.runOnIdle {
            assertEquals(1, fixture.updates)
            assertEquals(90f, fixture.draft.rotationDegrees, 1f)
            assertEquals(original.x + original.width / 2f,
                fixture.draft.x + fixture.draft.width / 2f, .00001f)
            assertEquals(original.y + original.height / 2f,
                fixture.draft.y + fixture.draft.height / 2f, .00001f)
            assertTrue(fixture.draft.isValid())
        }
        compose.onNodeWithTag("gesture_context_action").assertIsDisplayed()
    }

    @Test fun tappingContextualActionDoesNotStartAnElementOrPageGesture() {
        val fixture = showStage()
        val original = fixture.draft

        // Pointer input is intentional: semantic performClick bypasses the stage detector.
        compose.onNodeWithTag("gesture_context_action").assertIsDisplayed().performTouchInput {
            click(center)
        }

        compose.runOnIdle {
            assertEquals(1, fixture.toolbarClicks)
            assertEquals(0, fixture.updates)
            assertEquals(0, fixture.selections)
            assertEquals(0, fixture.insertions)
            assertEquals(0, fixture.dismissals)
            assertEquals(original, fixture.draft)
        }
        compose.onNodeWithTag("gesture_context_action").assertIsDisplayed()
    }

    private fun viewport() = compose.onNodeWithContentDescription(
        compose.activity.getString(R.string.fill_v4_page_hint_selected),
    )

    private fun selectionCenterInViewport(): Offset {
        val topLeft = compose.onNodeWithTag("fill_selection_handle_0")
            .fetchSemanticsNode().boundsInRoot.center
        val bottomRight = compose.onNodeWithTag("fill_selection_handle_2")
            .fetchSemanticsNode().boundsInRoot.center
        return (topLeft + bottomRight) / 2f - viewport().fetchSemanticsNode().boundsInRoot.topLeft
    }

    private fun showStage(): Fixture {
        val fixture = Fixture()
        compose.activityRule.scenario.onActivity { it.enableEdgeToEdge() }
        compose.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                ScaniqoTheme {
                    Surface(Modifier.fillMaxSize().safeDrawingPadding()) {
                        DocumentFillMarkStage(
                            state = fixture.state,
                            page = fixture.page,
                            draft = fixture.draft,
                            excludedId = null,
                            editingText = false,
                            focusRequest = 0,
                            textValue = TextFieldValue(fixture.draft.text),
                            onTextValueChange = {},
                            onUpdate = { fixture.draft = it; fixture.updates++ },
                            onSelect = { fixture.selections++ },
                            onInsert = { fixture.insertions++ },
                            onStopTyping = {},
                            onNavigatePage = {},
                            onEvent = {},
                            modifier = Modifier.fillMaxSize(),
                            onDismissSelection = { fixture.dismissals++ },
                            selectionActions = {
                                TextButton(
                                    onClick = { fixture.toolbarClicks++ },
                                    modifier = Modifier.testTag("gesture_context_action"),
                                ) {
                                    Text("Ajustar")
                                }
                            },
                        )
                    }
                }
            }
        }
        compose.onNodeWithTag("fill_selection_handle_4").assertIsDisplayed()
        compose.onNodeWithTag("gesture_context_action").assertIsDisplayed()
        return fixture
    }

    private class Fixture {
        var draft by mutableStateOf(PdfOverlay(
            id = "gesture-text", kind = PdfOverlayKind.TEXT, text = "Maria Silva",
            x = .25f, y = .36f, width = .5f, height = .25f, fontSize = 18f,
        ))
        var updates = 0
        var selections = 0
        var insertions = 0
        var dismissals = 0
        var toolbarClicks = 0
        val page = PdfPageSpec("gesture-page", "fixture.pdf", 0)
        val state = DocumentToolsUiState(
            workspace = PdfWorkspace(1, "fill-gesture", "fixture", listOf(page)),
            selectedPageId = page.id,
            tool = DocumentTool.FORMS,
            pagePreviews = mapOf(page.id to DocumentPagePreviewState(
                preview = PdfPagePreview("fixture.pdf"),
                renderedOverlayIds = emptySet(),
            )),
        )
    }
}
