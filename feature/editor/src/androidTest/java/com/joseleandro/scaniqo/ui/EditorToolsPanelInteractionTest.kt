package com.joseleandro.scaniqo.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.joseleandro.scaniqo.domain.model.PdfFilterThumbnails
import com.joseleandro.scaniqo.domain.model.PdfPageSpec
import com.joseleandro.scaniqo.domain.model.PdfWorkspace
import com.joseleandro.scaniqo.feature.editor.R
import com.joseleandro.scaniqo.ui.documentEditor.components.DocumentToolsBar
import com.joseleandro.scaniqo.ui.documentEditor.event.DocumentToolsEvent
import com.joseleandro.scaniqo.ui.documentEditor.model.DocumentTool
import com.joseleandro.scaniqo.ui.documentEditor.model.documentFilterChoices
import com.joseleandro.scaniqo.ui.documentEditor.state.DocumentFilterThumbnailState
import com.joseleandro.scaniqo.ui.documentEditor.state.DocumentToolsUiState
import com.joseleandro.scaniqo.ui.documentEditor.util.titleRes
import com.joseleandro.scaniqo.ui.documentEditor.util.titleResource
import com.joseleandro.scaniqo.ui.theme.ScaniqoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class EditorToolsPanelInteractionTest {
    @get:Rule val compose = createComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun saveRemainsVisibleAndClickableWhileToolsScrollWithLargeFonts() {
        val events = mutableListOf<DocumentToolsEvent>()
        setPanel(readyTools(), events::add)

        val save = compose.onNodeWithText(context.getString(R.string.tools_save))
        save.assertIsDisplayed()
        val beforeScroll = save.fetchSemanticsNode().boundsInRoot
        save.performTouchInput { click() }
        compose.runOnIdle { assertEquals(1, events.count { it == DocumentToolsEvent.Save }) }

        compose.onNodeWithText(context.getString(DocumentTool.FORMS.titleRes()))
            .performScrollTo().assertIsDisplayed()

        save.assertIsDisplayed()
        val afterScroll = save.fetchSemanticsNode().boundsInRoot
        assertEquals("Save must stay outside the scrolling tool area", beforeScroll.top, afterScroll.top, 1f)
        assertEquals(beforeScroll.bottom, afterScroll.bottom, 1f)
        save.performTouchInput { click() }
        compose.runOnIdle { assertEquals(2, events.count { it == DocumentToolsEvent.Save }) }
    }

    @Test
    fun filtersUseTwoColumnsAndKeepCloseAndSaveReachableAfterScrolling() {
        val events = mutableListOf<DocumentToolsEvent>()
        setPanel(readyTools().copy(tool = DocumentTool.FILTERS), events::add)

        val close = compose.onNodeWithContentDescription(context.getString(R.string.filter_bar_close))
        val save = compose.onNodeWithText(context.getString(R.string.tools_save))
        close.assertIsDisplayed()
        save.assertIsDisplayed()
        val initialClose = close.fetchSemanticsNode().boundsInRoot
        val initialSave = save.fetchSemanticsNode().boundsInRoot
        val firstFilter = compose.onNodeWithText(
            context.getString(documentFilterChoices[0].titleResource()),
        ).fetchSemanticsNode().boundsInRoot
        val secondFilter = compose.onNodeWithText(
            context.getString(documentFilterChoices[1].titleResource()),
        ).fetchSemanticsNode().boundsInRoot
        assertEquals("The first two filters must share a grid row", firstFilter.top, secondFilter.top, 1f)
        assertTrue(
            "Filter cards must occupy separate columns",
            firstFilter.right <= secondFilter.left || secondFilter.right <= firstFilter.left,
        )

        val lastFilter = documentFilterChoices.last()
        val lastLabel = context.getString(lastFilter.titleResource())
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(lastLabel))
        compose.onNodeWithText(lastLabel).assertIsDisplayed().performTouchInput { click() }

        close.assertIsDisplayed()
        save.assertIsDisplayed()
        assertEquals(initialClose.top, close.fetchSemanticsNode().boundsInRoot.top, 1f)
        assertEquals(initialSave.top, save.fetchSemanticsNode().boundsInRoot.top, 1f)
        save.performTouchInput { click() }
        close.performTouchInput { click() }
        compose.runOnIdle {
            assertTrue(events.contains(DocumentToolsEvent.SelectGlobalFilter(lastFilter)))
            assertTrue(events.contains(DocumentToolsEvent.Save))
            assertTrue(events.contains(DocumentToolsEvent.DismissTool))
        }
    }

    private fun setPanel(
        state: DocumentToolsUiState,
        onEvent: (DocumentToolsEvent) -> Unit,
    ) {
        compose.setContent {
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.ForcedSize(DpSize(320.dp, 400.dp)),
            ) {
                DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(1.5f)) {
                    CompositionLocalProvider(LocalInspectionMode provides true) {
                        ScaniqoTheme {
                            DocumentToolsBar(
                                state = state,
                                onEvent = onEvent,
                                enabled = true,
                                modifier = Modifier.fillMaxSize(),
                                vertical = true,
                                // A document title and metadata occupy this space in the editor.
                                header = { Text("Document metadata", Modifier.height(120.dp)) },
                            )
                        }
                    }
                }
            }
        }
    }

    private fun readyTools() = DocumentToolsUiState(
        workspace = PdfWorkspace(
            documentId = 1L,
            workspaceId = "panel-test",
            directoryUri = "preview",
            pages = listOf(
                PdfPageSpec(id = "page", sourceUri = "preview.pdf", sourcePageIndex = 0),
            ),
        ),
        selectedPageId = "page",
        hasChanges = true,
        filterThumbnails = DocumentFilterThumbnailState(
            pageId = "page",
            result = PdfFilterThumbnails(
                images = documentFilterChoices.associateWith { "preview-${it.name}" },
            ),
        ),
    )
}
