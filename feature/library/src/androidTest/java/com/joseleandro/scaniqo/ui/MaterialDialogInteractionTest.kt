package com.joseleandro.scaniqo.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.joseleandro.scaniqo.ui.components.DialogActions
import com.joseleandro.scaniqo.ui.theme.ScaniqoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Exercises the shared actions in an isolated activity, without accessing app data. */
class MaterialDialogInteractionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var inputModeManager: InputModeManager

    @Test
    fun shortActionsShareOneRowAndConfirmationFollowsLayoutDirection() {
        val direction = mutableStateOf(LayoutDirection.Ltr)
        setCompactContent(fontScale = 1f) {
            CompositionLocalProvider(LocalLayoutDirection provides direction.value) {
                DialogActions(
                    confirmText = "Descartar", onConfirm = {},
                    dismissText = "Continuar", onDismiss = {},
                    destructive = true,
                )
            }
        }

        fun assertOrder(rtl: Boolean) {
            val confirm = compose.onNodeWithText("Descartar").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            val dismiss = compose.onNodeWithText("Continuar").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertEquals("Actions must share one row when their labels fit", dismiss.center.y, confirm.center.y, 1f)
            assertTrue("Confirmation must be at the trailing edge",
                if (rtl) confirm.right <= dismiss.left else confirm.left >= dismiss.right)
            assertLabelFits("Descartar")
            assertLabelFits("Continuar")
        }

        assertOrder(rtl = false)
        compose.runOnIdle { direction.value = LayoutDirection.Rtl }
        assertOrder(rtl = true)
    }

    @Test
    fun longActionsRemainReadableAndOperableInCompactWindowWithLargeFonts() {
        val confirmLabel = "Excluir documentos permanentemente"
        val dismissLabel = "Continuar revisando documentos"
        var confirmations = 0
        var dismissals = 0
        setCompactContent {
            DialogActions(
                confirmText = confirmLabel,
                onConfirm = { confirmations++ },
                dismissText = dismissLabel,
                onDismiss = { dismissals++ },
                destructive = true,
            )
        }

        val confirm = compose.onNodeWithText(confirmLabel)
        val dismiss = compose.onNodeWithText(dismissLabel)
        confirm.assertIsDisplayed().assertIsEnabled()
        dismiss.assertIsDisplayed().assertIsEnabled()

        val confirmBounds = confirm.fetchSemanticsNode().boundsInRoot
        val dismissBounds = dismiss.fetchSemanticsNode().boundsInRoot
        assertFalse("Action touch targets must not overlap", confirmBounds.overlaps(dismissBounds))
        assertTrue("Confirmation comes first when actions need separate rows", confirmBounds.bottom <= dismissBounds.top)
        assertLabelFits(confirmLabel)
        assertLabelFits(dismissLabel)

        dismiss.performClick()
        confirm.performClick()
        compose.runOnIdle {
            assertTrue("Keyboard input mode must be available", inputModeManager.requestInputMode(InputMode.Keyboard))
        }
        dismiss.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            .assertIsFocused()
            .performKeyInput { pressKey(Key.Enter) }
        compose.runOnIdle {
            assertEquals(1, confirmations)
            assertEquals(2, dismissals)
        }
    }

    @Test
    fun confirmingBlocksRepeatedConfirmationAndHonorsDismissEnabled() {
        val confirming = mutableStateOf(false)
        val dismissEnabled = mutableStateOf(true)
        var confirmations = 0
        var dismissals = 0
        setCompactContent {
            DialogActions(
                confirmText = "Salvar alterações",
                onConfirm = {
                    confirmations++
                    confirming.value = true
                },
                dismissText = "Cancelar operação",
                onDismiss = { dismissals++ },
                confirming = confirming.value,
                dismissEnabled = dismissEnabled.value,
            )
        }

        val confirm = compose.onNodeWithText("Salvar alterações")
        val dismiss = compose.onNodeWithText("Cancelar operação")
        confirm.assertIsEnabled().performClick()
        confirm.assertIsDisplayed().assertIsNotEnabled()
            .performTouchInput { click() }
        dismiss.assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals(1, confirmations)
            assertEquals(1, dismissals)
            dismissEnabled.value = false
        }

        dismiss.assertIsDisplayed().assertIsNotEnabled()
            .performTouchInput { click() }
        compose.runOnIdle {
            assertEquals(1, confirmations)
            assertEquals(1, dismissals)
            confirming.value = false
            dismissEnabled.value = true
        }
        confirm.assertIsEnabled()
        dismiss.assertIsEnabled()
    }

    private fun assertLabelFits(label: String) {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(label, useUnmergedTree = true)
            .assertIsDisplayed()
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue("The action label must have a measured text layout", layouts.isNotEmpty())
        val layout = layouts.single()
        // Foundation 1.12 recreates the semantic paragraph using the parent maxWidth,
        // while retaining the actual, narrower text size. Check glyph advances instead
        // of didOverflowWidth, which also counts that extra alignment space.
        assertFalse("Action text must fit vertically: $label", layout.didOverflowHeight)
        repeat(layout.lineCount) { line ->
            assertFalse("Action text must not be ellipsized: $label", layout.isLineEllipsized(line))
            assertTrue(
                "Action text must fit horizontally: $label",
                layout.getLineRight(line) - layout.getLineLeft(line) <= layout.size.width,
            )
        }
        assertEquals("The complete label must be laid out", label.length, layout.getLineEnd(layout.lineCount - 1))
    }

    private fun setCompactContent(fontScale: Float = 2f, content: @Composable () -> Unit) {
        compose.setContent {
            val currentInputModeManager = LocalInputModeManager.current
            SideEffect { inputModeManager = currentInputModeManager }
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(320.dp, 640.dp))) {
                DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(fontScale)) {
                    ScaniqoTheme(dynamicColor = false) {
                        Surface(
                            modifier = Modifier.fillMaxWidth().padding(20.dp),
                            shape = MaterialTheme.shapes.extraLarge,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ) {
                            Box(Modifier.padding(24.dp)) {
                                content()
                            }
                        }
                    }
                }
            }
        }
    }
}
