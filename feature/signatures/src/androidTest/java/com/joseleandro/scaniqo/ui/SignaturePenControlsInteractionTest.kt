package com.joseleandro.scaniqo.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import com.joseleandro.scaniqo.ui.signatures.SignaturePenColorSelector
import com.joseleandro.scaniqo.ui.signatures.SignatureTipSizeSelector
import com.joseleandro.scaniqo.ui.theme.ScaniqoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SignaturePenControlsInteractionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun choosingPenChangesOnlyItsColorAndRaisesTheSelectedPen() {
        val selected = mutableStateOf(0xFF18212B.toInt())
        val changes = mutableListOf<Int>()
        compose.setContent {
            ScaniqoTheme {
                SignaturePenColorSelector(selected.value, {
                    changes += it
                    selected.value = it
                })
            }
        }
        compose.onNodeWithTag("signature_pen_ff18212b").assertIsSelected()
            .assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        val originalBlackTop = penTop("ff18212b")
        assertTrue(originalBlackTop < penTop("ff183c9a"))
        compose.onNodeWithTag("signature_pen_ff183c9a").performClick().assertIsSelected()
        compose.waitForIdle()
        assertTrue(penTop("ff183c9a") < penTop("ff18212b"))
        compose.runOnIdle { assertEquals(listOf(0xFF183C9A.toInt()), changes) }
    }

    @Test fun customWidthHighlightsNearestPresetWithoutChangingItUntilTheUserChooses() {
        var width by mutableStateOf(.009f)
        val changes = mutableListOf<Float>()
        compose.setContent {
            ScaniqoTheme {
                SignatureTipSizeSelector(width, {
                    changes += it
                    width = it
                })
            }
        }
        compose.onNodeWithTag("signature_tip_3").assertIsSelected()
        compose.runOnIdle {
            assertEquals(.009f, width, 0f)
            assertTrue(changes.isEmpty())
        }
        compose.onNodeWithTag("signature_tip_0").assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp).performClick().assertIsSelected()
        compose.runOnIdle { assertEquals(listOf(.002f), changes) }
    }

    @Test fun disabledControlsKeepTheirSelectionAndNeverDispatchChanges() {
        val colors = mutableListOf<Int>()
        val widths = mutableListOf<Float>()
        compose.setContent {
            ScaniqoTheme {
                Column {
                    SignaturePenColorSelector(0xFF613D82.toInt(), colors::add, enabled = false)
                    SignatureTipSizeSelector(.004f, widths::add, enabled = false)
                }
            }
        }
        compose.onNodeWithTag("signature_pen_ff613d82").assertIsSelected().assertIsNotEnabled()
        compose.onNodeWithTag("signature_pen_ff18212b").assertIsNotEnabled().performClick()
        compose.onNodeWithTag("signature_tip_1").assertIsSelected().assertIsNotEnabled()
        compose.onNodeWithTag("signature_tip_2").assertIsNotEnabled().performClick()
        compose.runOnIdle {
            assertTrue(colors.isEmpty())
            assertTrue(widths.isEmpty())
        }
    }

    @Test fun narrowRowsCanScrollToEveryPenAndTipWithoutShrinkingTargets() {
        var color by mutableStateOf(0xFF18212B.toInt())
        var width by mutableStateOf(.004f)
        compose.setContent {
            ScaniqoTheme {
                Column(Modifier.width(196.dp)) {
                    SignaturePenColorSelector(color, { color = it })
                    SignatureTipSizeSelector(width, { width = it })
                }
            }
        }
        compose.onNodeWithTag("signature_pen_colors")
            .performScrollToNode(hasTestTag("signature_pen_ff2e7d32"))
        compose.onNodeWithTag("signature_pen_ff2e7d32").assertWidthIsAtLeast(48.dp)
            .performClick().assertIsSelected()
        compose.onNodeWithTag("signature_tip_sizes").performScrollToNode(hasTestTag("signature_tip_4"))
        compose.onNodeWithTag("signature_tip_4").assertWidthIsAtLeast(48.dp)
            .performClick().assertIsSelected()
        compose.runOnIdle {
            assertEquals(0xFF2E7D32.toInt(), color)
            assertEquals(.014f, width, 0f)
        }
    }

    private fun penTop(color: String) = compose
        .onNodeWithTag("signature_pen_visual_$color", useUnmergedTree = true)
        .fetchSemanticsNode().boundsInRoot.top
}
