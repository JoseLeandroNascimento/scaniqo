package com.joseleandro.scaniqo.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.swipe
import androidx.test.platform.app.InstrumentationRegistry
import com.joseleandro.scaniqo.R
import com.joseleandro.scaniqo.domain.model.PdfCropBounds
import com.joseleandro.scaniqo.domain.model.PdfPageSpec
import com.joseleandro.scaniqo.domain.model.PdfWorkspace
import com.joseleandro.scaniqo.ui.documentCommon.components.DocumentPreviewZoom
import com.joseleandro.scaniqo.ui.documentEditor.components.DocumentCorrectionsSheetContent
import com.joseleandro.scaniqo.ui.documentEditor.components.DocumentManualCropEditor
import com.joseleandro.scaniqo.ui.documentEditor.event.DocumentToolsEvent
import com.joseleandro.scaniqo.ui.documentEditor.model.CorrectionEditorTool
import com.joseleandro.scaniqo.ui.documentEditor.model.DocumentTool
import com.joseleandro.scaniqo.ui.documentEditor.state.DocumentToolsUiState
import com.joseleandro.scaniqo.ui.theme.ScaniqoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CorrectionInteractionTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun backgroundWaitsForExplicitDetection() {
        val events = mutableListOf<DocumentToolsEvent>()
        val page = PdfPageSpec("page", "preview", 0)
        compose.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                ScaniqoTheme {
                    DocumentCorrectionsSheetContent(
                        state = DocumentToolsUiState(
                            workspace = PdfWorkspace(1, "correction-test", "preview", listOf(page)),
                            selectedPageId = page.id,
                            tool = DocumentTool.CORRECTIONS,
                        ),
                        onEvent = { events += it },
                        initialTool = CorrectionEditorTool.BACKGROUND,
                    )
                }
            }
        }

        compose.runOnIdle {
            assertTrue(events.none { it is DocumentToolsEvent.AnalyzeCorrectionPage })
        }
        compose.onNodeWithText(context.getString(R.string.correction_v3_detect)).performClick()
        compose.runOnIdle {
            assertEquals(1, events.filterIsInstance<DocumentToolsEvent.AnalyzeCorrectionPage>().size)
        }
    }

    @Test fun pinchZoomsTheViewportWithoutChangingTheCrop() {
        val page = PdfPageSpec("page", "preview", 0)
        var changes by mutableIntStateOf(0)
        compose.setContent {
            var crop by mutableStateOf(PdfCropBounds(.15f, .15f, .85f, .85f))
            CompositionLocalProvider(LocalInspectionMode provides true) {
                ScaniqoTheme {
                    DocumentPreviewZoom(
                        modifier = Modifier.testTag("correction-zoom"),
                        transformGesturesEnabled = true,
                    ) {
                        DocumentManualCropEditor(
                            page = page,
                            crop = crop,
                            enabled = true,
                            onChange = { crop = it; changes++ },
                            pageNumber = 1,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }

        compose.onNodeWithTag("correction-zoom").performTouchInput {
            pinch(
                start0 = center + Offset(-25f, 0f),
                end0 = center + Offset(-80f, 0f),
                start1 = center + Offset(25f, 0f),
                end1 = center + Offset(80f, 0f),
                durationMillis = 300,
            )
        }
        compose.runOnIdle { assertEquals(0, changes) }
        compose.onNodeWithContentDescription(context.getString(R.string.preview_zoom_reset)).performClick()
        compose.onNodeWithTag("correction-zoom").performTouchInput {
            swipe(center, center + Offset(28f, 18f), durationMillis = 300)
        }
        compose.runOnIdle { assertTrue(changes > 0) }
    }
}
