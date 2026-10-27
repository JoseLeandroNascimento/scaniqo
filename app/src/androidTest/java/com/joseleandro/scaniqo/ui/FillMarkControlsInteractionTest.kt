package com.joseleandro.scaniqo.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.joseleandro.scaniqo.R
import com.joseleandro.scaniqo.domain.model.PdfOverlay
import com.joseleandro.scaniqo.domain.model.PdfOverlayKind
import com.joseleandro.scaniqo.ui.documentEditor.components.FillMarkControls
import com.joseleandro.scaniqo.ui.documentEditor.components.FillMarkPanel
import com.joseleandro.scaniqo.ui.theme.ScaniqoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class FillMarkControlsInteractionTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun colorAndFormatEditTheSameSelectedText() {
        val original = PdfOverlay(
            id = "selected-text", kind = PdfOverlayKind.TEXT, text = "Cliente",
            color = 0xFF202124.toInt(), fontSize = 16f,
        )
        var draft by mutableStateOf(original)
        var panel by mutableStateOf(FillMarkPanel.NONE)
        var updates = 0
        compose.setContent {
            ScaniqoTheme {
                FillMarkControls(
                    active = true, continuousInsertion = false,
                    mode = PdfOverlayKind.TEXT, editingText = false, editable = true,
                    draft = draft, panel = panel, canUndo = false, canRedo = false,
                    onMode = {}, onPanel = { panel = it }, onEditText = {}, onMore = {},
                    onUndo = {}, onRedo = {},
                    onUpdate = { draft = it; updates++ },
                    onSelectColor = { draft = draft.copy(color = it); updates++ },
                    recentColors = emptyList(),
                    numericSizeBuffer = null, onStartNumericSize = {},
                    onNumericSizeChange = {}, onConfirmNumericSize = {},
                    onCancelNumericSize = {}, numericSizeError = false,
                    allPages = false, pageCount = 1, onAllPages = {}, errorText = null,
                )
            }
        }

        val colorCategory = context.getString(R.string.fill_v4_color) + ", " +
            context.getString(R.string.overlay_black)
        compose.onNodeWithContentDescription(colorCategory).performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.overlay_blue)).performClick()
        compose.runOnIdle {
            assertEquals(FillMarkPanel.COLOR, panel)
            assertEquals(0xFF1565C0.toInt(), draft.color)
            assertEquals(original.id, draft.id)
            assertEquals(original.text, draft.text)
        }

        val formatCategory = context.getString(R.string.fill_v4_format)
        compose.onNodeWithContentDescription(formatCategory).performClick()
        compose.onNodeWithText(context.getString(R.string.fill_v4_bold)).performClick()
        compose.runOnIdle {
            assertEquals(FillMarkPanel.FORMAT, panel)
            assertTrue(draft.bold)
            assertEquals(0xFF1565C0.toInt(), draft.color)
            assertEquals(original.id, draft.id)
            assertEquals(original.kind, draft.kind)
            assertEquals(2, updates)
        }
    }

    @Test fun markToolOffersCheckAndCrossInsideOneAction() {
        var inserting by mutableStateOf(false)
        var nextKind by mutableStateOf(PdfOverlayKind.CHECKMARK)
        var selected: PdfOverlay? by mutableStateOf(null)
        compose.setContent {
            ScaniqoTheme {
                FillMarkControls(
                    active = selected != null, continuousInsertion = inserting,
                    mode = nextKind, editingText = false, editable = true,
                    draft = selected ?: if (inserting) PdfOverlay("next", nextKind) else null,
                    panel = FillMarkPanel.NONE, canUndo = false, canRedo = false,
                    onMode = { kind ->
                        if (selected != null) selected = selected?.copy(kind = kind) else nextKind = kind
                    },
                    onCreateMode = { inserting = it != PdfOverlayKind.TEXT; selected = null },
                    onStopInsertion = { inserting = false },
                    onPanel = {}, onEditText = {}, onMore = {}, onUndo = {}, onRedo = {},
                    onUpdate = {}, onSelectColor = {}, recentColors = emptyList(),
                    numericSizeBuffer = null, onStartNumericSize = {},
                    onNumericSizeChange = {}, onConfirmNumericSize = {}, onCancelNumericSize = {},
                    numericSizeError = false, allPages = false, pageCount = 1,
                    onAllPages = {}, errorText = null, showHistoryActions = false,
                )
            }
        }

        compose.onNodeWithText(context.getString(R.string.fill_v4_tool_text)).assertExists()
        compose.onNodeWithText(context.getString(R.string.fill_v4_tool_mark)).assertExists()
        compose.onNodeWithText(context.getString(R.string.fill_v4_mark_cross)).assertDoesNotExist()
        compose.onNodeWithContentDescription(context.getString(R.string.fill_v4_add_mark)).performClick()
        compose.onNodeWithText(context.getString(R.string.fill_v4_mark_check)).assertExists()
        compose.onNodeWithText(context.getString(R.string.fill_v4_mark_cross)).performClick()
        compose.runOnIdle { assertEquals(PdfOverlayKind.CROSSMARK, nextKind) }
        compose.onNodeWithText(context.getString(R.string.fill_v4_stop_inserting)).performClick()
        compose.onNodeWithText(context.getString(R.string.fill_v4_mark_cross)).assertDoesNotExist()
    }
}
