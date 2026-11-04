package com.joseleandro.scaniqo.ui

import com.joseleandro.scaniqo.core.resources.R as CoreResourcesR
import com.joseleandro.scaniqo.feature.editor.R

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.platform.app.InstrumentationRegistry
import com.joseleandro.scaniqo.domain.model.PdfOverlay
import com.joseleandro.scaniqo.domain.model.PdfOverlayKind
import com.joseleandro.scaniqo.ui.documentEditor.components.FillMarkContextToolbar
import com.joseleandro.scaniqo.ui.documentEditor.components.FillMarkProperty
import com.joseleandro.scaniqo.ui.documentEditor.components.FillMarkPropertyDialog
import com.joseleandro.scaniqo.ui.documentEditor.components.formatFillMarkSize
import com.joseleandro.scaniqo.ui.theme.ScaniqoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class FillMarkControlsInteractionTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun label(id: Int) = context.getString(id)

    @Test
    fun colorAndStyleUpdateTheSameSelectedTextImmediately() {
        val fixture = showControls(PdfOverlay("selected-text", PdfOverlayKind.TEXT, text = "Cliente"))
        compose.onNodeWithTag("fill_action_color").performClick()
        compose.onNodeWithContentDescription(label(R.string.overlay_blue)).performClick()
        compose.runOnIdle {
            assertEquals(FillMarkProperty.COLOR, fixture.property)
            assertEquals(0xFF1565C0.toInt(), fixture.draft.color)
            assertEquals("selected-text", fixture.draft.id)
            assertEquals("Cliente", fixture.draft.text)
        }
        apply()

        compose.onNodeWithTag("fill_action_more").performClick()
        compose.onNodeWithTag("fill_action_style").performClick()
        compose.onNodeWithText(label(R.string.fill_v4_bold)).performClick()
        compose.runOnIdle {
            assertTrue(fixture.draft.bold)
            assertEquals(0xFF1565C0.toInt(), fixture.draft.color)
            assertEquals("selected-text", fixture.draft.id)
            assertEquals(PdfOverlayKind.TEXT, fixture.draft.kind)
            assertEquals(2, fixture.updates)
        }
        apply()
        compose.onNodeWithTag("fill_property_dialog_STYLE").assertDoesNotExist()
    }

    @Test
    fun markTypeIsContextualAndCancelRestoresTheSameMark() {
        val original = PdfOverlay("selected-mark", PdfOverlayKind.CHECKMARK, width = .08f)
        val fixture = showControls(original)
        compose.onNodeWithTag("fill_action_edit").assertDoesNotExist()
        compose.onNodeWithText(label(R.string.fill_v4_mark_cross)).assertDoesNotExist()
        compose.onNodeWithTag("fill_action_type").performClick()
        compose.onNodeWithText(label(R.string.fill_v4_mark_cross)).performClick()
        compose.runOnIdle {
            assertEquals(PdfOverlayKind.CROSSMARK, fixture.draft.kind)
            assertEquals(original.id, fixture.draft.id)
        }
        compose.onNodeWithText(label(R.string.tools_cancel)).performClick()
        compose.runOnIdle { assertEquals(original, fixture.draft) }

        compose.onNodeWithTag("fill_action_type").performClick()
        compose.onNodeWithText(label(R.string.fill_v4_mark_cross)).performClick()
        apply()
        compose.runOnIdle {
            assertEquals(PdfOverlayKind.CROSSMARK, fixture.draft.kind)
            assertEquals(original.id, fixture.draft.id)
        }
    }

    @Test
    fun numericSizeRejectsInvalidValuesAndDialogCancelRestoresTheDraft() {
        val original = PdfOverlay("selected-text", PdfOverlayKind.TEXT, text = "Cliente")
        val fixture = showControls(original)
        compose.onNodeWithTag("fill_action_size").performClick()
        val sizeText = context.getString(
            R.string.fill_v3_size_value,
            formatFillMarkSize(original.fontSize, context.resources.configuration.locales[0]),
        )
        compose.onNodeWithText(sizeText).performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("999")
        compose.onNodeWithText(label(R.string.fill_mark_apply)).assertIsNotEnabled()
        compose.runOnIdle { assertEquals(original, fixture.draft) }
        compose.onNode(hasSetTextAction()).performTextReplacement("24")
        compose.runOnIdle { assertEquals(24f, fixture.draft.fontSize, .001f) }
        compose.onNodeWithText(label(R.string.tools_cancel)).performClick()
        compose.runOnIdle { assertEquals(original, fixture.draft) }
    }

    @Test
    fun colorChoicesStayInOnePaletteAfterSelectingAndReopening() {
        val fixture = showControls(PdfOverlay("palette-text", PdfOverlayKind.TEXT, text = "Cliente"))
        compose.onNodeWithTag("fill_action_color").performClick()
        assertPaletteSize(8)
        compose.onNodeWithContentDescription(label(R.string.overlay_black)).assertIsSelected()
        compose.onNodeWithContentDescription(label(R.string.overlay_blue)).performClick().assertIsSelected()
        compose.onNodeWithContentDescription(label(CoreResourcesR.string.fill_mark_green)).performClick().assertIsSelected()
        apply()

        compose.onNodeWithTag("fill_action_color").performClick()
        assertPaletteSize(8)
        compose.onNodeWithContentDescription(label(CoreResourcesR.string.fill_mark_green)).assertIsSelected()
        compose.onNodeWithContentDescription(label(R.string.overlay_blue)).assertIsNotSelected()
        compose.runOnIdle { assertEquals(0xFF2E7D32.toInt(), fixture.draft.color) }
    }

    @Test
    fun importedColorAppearsSelectedAndRemainsAvailableDuringTheEdit() {
        val original = PdfOverlay("custom-color", PdfOverlayKind.TEXT, text = "Cliente", color = 0xFFE1F5FE.toInt())
        val fixture = showControls(original)
        compose.onNodeWithTag("fill_action_color").performClick()
        assertPaletteSize(9)
        compose.onNodeWithTag("fill_color_option_ffe1f5fe").assertIsDisplayed().assertIsSelected()

        compose.onNodeWithContentDescription(label(R.string.overlay_blue)).performClick().assertIsSelected()
        compose.onNodeWithTag("fill_color_option_ffe1f5fe").assertIsNotSelected().performClick().assertIsSelected()
        assertPaletteSize(9)
        compose.runOnIdle { assertEquals(original, fixture.draft) }
    }

    private fun assertPaletteSize(expected: Int) {
        compose.onAllNodesWithTag("fill_color_palette").assertCountEquals(1)
        compose.onAllNodes(SemanticsMatcher("Color swatch") {
            it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("fill_color_option_") == true
        }).assertCountEquals(expected)
        compose.onAllNodesWithTag("fill_color_selected_check", useUnmergedTree = true).assertCountEquals(1)
        compose.onNodeWithTag("fill_color_selected_check", useUnmergedTree = true).assertIsDisplayed()
    }

    private fun apply() {
        compose.onNodeWithText(label(R.string.fill_mark_apply)).performClick()
    }

    private fun showControls(original: PdfOverlay): Fixture {
        val fixture = Fixture(original)
        compose.setContent {
            ScaniqoTheme {
                FillMarkContextToolbar(
                    draft = fixture.draft,
                    enabled = true,
                    onEditText = {},
                    onProperty = {
                        fixture.baseline = fixture.draft
                        fixture.property = it
                    },
                    onDuplicate = {}, onDelete = {}, onSaveValue = {}, onReset = {},
                )
                fixture.property?.let { property ->
                    FillMarkPropertyDialog(
                        property = property,
                        draft = fixture.draft,
                        editable = true,
                        onUpdate = { fixture.draft = it; fixture.updates++ },
                        onCancel = { fixture.draft = fixture.baseline; fixture.property = null },
                        onApply = { fixture.property = null },
                    )
                }
            }
        }
        return fixture
    }

    private class Fixture(original: PdfOverlay) {
        var draft by mutableStateOf(original)
        var property by mutableStateOf<FillMarkProperty?>(null)
        var baseline = original
        var updates = 0
    }
}
