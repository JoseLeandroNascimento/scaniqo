package com.joseleandro.scaniqo.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.joseleandro.scaniqo.domain.model.PdfExportQuality
import com.joseleandro.scaniqo.domain.model.PdfPageSpec
import com.joseleandro.scaniqo.domain.model.PdfWorkspace
import com.joseleandro.scaniqo.feature.editor.R
import com.joseleandro.scaniqo.ui.documentEditor.components.DocumentQualitySheetContent
import com.joseleandro.scaniqo.ui.documentEditor.event.DocumentToolsEvent
import com.joseleandro.scaniqo.ui.documentEditor.state.DocumentToolsUiState
import com.joseleandro.scaniqo.ui.theme.ScaniqoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class QualityScreenInteractionTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun optionsStayLocalUntilApplyAndFooterRemainsVisibleInShortWindow() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val events = mutableListOf<DocumentToolsEvent>()
        var draftChanged = false
        var finished = false
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(600.dp, 400.dp))) {
                DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(1.5f)) {
                    ScaniqoTheme {
                        DocumentQualitySheetContent(
                            state = DocumentToolsUiState(
                                workspace = PdfWorkspace(
                                    1, "quality-test", "preview",
                                    listOf(PdfPageSpec("page", "preview.pdf", 0)),
                                    exportQuality = PdfExportQuality.HIGH_QUALITY,
                                    ocrEnabled = false,
                                ),
                                selectedPageId = "page",
                            ),
                            onEvent = events::add,
                            modifier = Modifier.fillMaxSize(),
                            onDraftChanged = { draftChanged = it },
                            onFinish = { finished = true },
                        )
                    }
                }
            }
        }

        val apply = compose.onNodeWithText(context.getString(R.string.quality_export_apply))
        apply.assertIsDisplayed()
        val footerBefore = apply.fetchSemanticsNode().boundsInRoot
        compose.onNodeWithText(context.getString(R.string.quality_export_high)).assertIsSelected()
        compose.onNodeWithText(context.getString(R.string.quality_export_small))
            .performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithText(context.getString(R.string.quality_export_high)).assertIsNotSelected()
        compose.onNodeWithText(context.getString(R.string.quality_ocr_title))
            .performScrollTo().performClick().assertIsOn()

        compose.runOnIdle {
            assertTrue(draftChanged)
            assertTrue(events.isEmpty())
            assertFalse(finished)
        }
        apply.assertIsDisplayed()
        assertEquals(footerBefore.top, apply.fetchSemanticsNode().boundsInRoot.top, 1f)
        apply.performClick()
        compose.runOnIdle {
            assertEquals(listOf(DocumentToolsEvent.SetExportOptions(PdfExportQuality.SMALL, true)), events)
            assertFalse(draftChanged)
            assertTrue(finished)
        }
    }

    @Test
    fun largeFontsKeepUsageDescriptionsReadableAndApplyReachable() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val events = mutableListOf<DocumentToolsEvent>()
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(320.dp, 640.dp))) {
                DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(2f)) {
                    ScaniqoTheme {
                        DocumentQualitySheetContent(
                            state = DocumentToolsUiState(
                                workspace = PdfWorkspace(
                                    1, "quality-font-test", "preview",
                                    listOf(PdfPageSpec("page", "preview.pdf", 0)),
                                ),
                                selectedPageId = "page",
                            ),
                            onEvent = events::add,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }

        val apply = compose.onNodeWithText(context.getString(R.string.quality_export_apply))
        apply.assertIsDisplayed()
        val footerBefore = apply.fetchSemanticsNode().boundsInRoot
        listOf(
            R.string.quality_visual_high_use,
            R.string.quality_visual_balanced_use,
            R.string.quality_visual_small_use,
            R.string.quality_visual_ocr_description,
        ).forEach { description ->
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(context.getString(description), useUnmergedTree = true)
                .performScrollTo().assertIsDisplayed()
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertFalse("Description must not be clipped: ${context.getString(description)}", layouts.single().hasVisualOverflow)
            apply.assertIsDisplayed()
            assertEquals(footerBefore.top, apply.fetchSemanticsNode().boundsInRoot.top, 1f)
        }
        compose.runOnIdle { assertTrue(events.isEmpty()) }
    }
}
