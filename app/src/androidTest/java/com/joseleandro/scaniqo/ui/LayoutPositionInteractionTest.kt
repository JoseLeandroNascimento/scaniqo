package com.joseleandro.scaniqo.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.test.platform.app.InstrumentationRegistry
import com.joseleandro.scaniqo.R
import com.joseleandro.scaniqo.domain.model.PdfContentPosition
import com.joseleandro.scaniqo.domain.model.PdfPageLayout
import com.joseleandro.scaniqo.domain.model.PdfPagePreview
import com.joseleandro.scaniqo.domain.model.PdfPageSpec
import com.joseleandro.scaniqo.domain.model.PdfWorkspace
import com.joseleandro.scaniqo.ui.documentEditor.components.DocumentLayoutSheetContent
import com.joseleandro.scaniqo.ui.documentEditor.event.DocumentToolsEvent
import com.joseleandro.scaniqo.ui.documentEditor.state.DocumentToolPreviewState
import com.joseleandro.scaniqo.ui.documentEditor.state.DocumentToolsUiState
import com.joseleandro.scaniqo.ui.theme.ScaniqoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test

class LayoutPositionInteractionTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun text(id: Int) = context.getString(id)

    @Test fun anchorsRemainAccessibleAndHandleTapDoesNotRecenter() {
        val events = mutableListOf<DocumentToolsEvent>()
        var primary: (() -> Unit)? = null
        val page = PdfPageSpec(
            id = "page-1",
            sourceUri = "preview",
            sourcePageIndex = 0,
            layout = PdfPageLayout(contentScale = .75f),
        )
        compose.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                ScaniqoTheme {
                    DocumentLayoutSheetContent(
                        state = DocumentToolsUiState(
                            workspace = PdfWorkspace(1, "layout-test", "preview", listOf(page)),
                            selectedPageId = page.id,
                            layoutPreview = DocumentToolPreviewState(
                                preview = PdfPagePreview("preview", 0), pageId = page.id,
                            ),
                        ),
                        onEvent = { events += it },
                        registerPrimaryAction = { primary = it },
                    )
                }
            }
        }

        val top = text(R.string.tools_align_top)
        val bottom = text(R.string.tools_align_bottom)
        val left = text(R.string.tools_align_left)
        val center = text(R.string.tools_align_center)
        val right = text(R.string.tools_align_right)
        val anchors = listOf(
            top to left,
            top to center,
            top to right,
            center to left,
            center to center,
            center to right,
            bottom to left,
            bottom to center,
            bottom to right,
        )
        anchors.forEach { (vertical, horizontal) ->
            val description = context.getString(R.string.layout_v3_align_target, vertical, horizontal)
            compose.onNodeWithContentDescription(description)
                .assertIsEnabled().performScrollTo().performClick().assertIsSelected()
            compose.runOnIdle {
                assertEquals(0, events.filterIsInstance<DocumentToolsEvent.ApplyLayout>().size)
            }
        }

        val topLeft = context.getString(R.string.layout_v3_align_target, top, left)
        compose.onNodeWithContentDescription(topLeft).performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithContentDescription(text(R.string.gesture_move_handle)).performTouchInput { click() }
        compose.onNodeWithContentDescription(topLeft).assertIsSelected()
        compose.runOnIdle {
            assertNotNull(primary)
            primary?.invoke()
        }
        compose.runOnIdle {
            val applied = events.filterIsInstance<DocumentToolsEvent.ApplyLayout>()
            assertEquals(1, applied.size)
            assertEquals(PdfContentPosition(0f, 0f), applied.single().contentPosition)
            assertEquals(setOf(page.id), applied.single().pageIds)
            assertEquals(.75f, applied.single().contentScale)
            assertFalse(applied.single().allPages)
        }
    }
}
