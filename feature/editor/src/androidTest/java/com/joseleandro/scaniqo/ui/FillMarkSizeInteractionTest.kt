package com.joseleandro.scaniqo.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.joseleandro.scaniqo.domain.model.PdfOverlay
import com.joseleandro.scaniqo.domain.model.PdfOverlayKind
import com.joseleandro.scaniqo.ui.documentEditor.components.FillMarkSizeSlider
import com.joseleandro.scaniqo.ui.documentEditor.components.FillMarkTransformContent
import com.joseleandro.scaniqo.ui.documentEditor.components.fittedFillMarkText
import com.joseleandro.scaniqo.ui.documentEditor.components.rotatedFillMark
import com.joseleandro.scaniqo.ui.theme.ScaniqoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.math.roundToInt

class FillMarkSizeInteractionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun markSizeDragSurvivesRecompositionAndReturnsToTheSameSize() {
        exerciseContinuousDrag(mark(), transform = false)
    }

    @Test fun textFontDragSurvivesRecompositionAndReturnsToTheSameSize() {
        exerciseContinuousDrag(text().fittedFillMarkText(), transform = false)
    }

    @Test fun manualTextFontSliderUsesWholePointsWithinItsAvailableSpace() {
        exerciseContinuousDrag(text().copy(
            autoSizeText = false, width = .45f, height = .2f, rotationDegrees = 27f,
        ), transform = false)
    }

    @Test fun legacyFractionalFontChangesOnlyWhenTheUserDragsTheSlider() {
        exerciseContinuousDrag(text().copy(fontSize = 16.5f).fittedFillMarkText(), transform = false)
    }

    @Test fun markTransformDragSurvivesBothExtremesWithoutRestarting() {
        exerciseContinuousDrag(mark(), transform = true)
    }

    @Test fun wrappedAutoSizedTextTransformDoesNotDriftWhenReturningFromTheExtremes() {
        val wrapped = text().copy(
            x = .65f, width = .35f,
            text = "Nome completo de exemplo para preencher o formulário",
        ).fittedFillMarkText()
        exerciseContinuousDrag(wrapped, transform = true)
    }

    @Test fun manuallySizedTextTransformPreservesItsProportionsAcrossFrames() {
        val manual = text().copy(
            autoSizeText = false, width = .28f, height = .12f,
            rotationDegrees = 27f,
        )
        val fixture = exerciseContinuousDrag(manual, transform = true)
        compose.runOnIdle {
            fixture.updates.forEach { overlay ->
                assertEquals(manual.width / manual.height, overlay.width / overlay.height, .002f)
                assertEquals(manual.rotationDegrees, overlay.rotationDegrees, 0f)
                assertEquals(manual.text, overlay.text)
            }
        }
    }

    /**
     * Separate injection calls deliberately let Compose render each update before the next
     * pointer move. A single swipe injects all moves together and can hide a recreated SliderState.
     */
    private fun exerciseContinuousDrag(original: PdfOverlay, transform: Boolean): Fixture {
        val fixture = Fixture(original, transform)
        showSlider(fixture, transform)
        compose.runOnIdle {
            assertEquals("Opening the controls must preserve existing sizes, including legacy fractions",
                original, fixture.draft)
            assertTrue(fixture.updates.isEmpty())
        }
        val slider = compose.onNodeWithTag("fill_size_slider").assertIsDisplayed().assertIsEnabled()
        val info = slider.fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo]
        val bounds = slider.fetchSemanticsNode().boundsInRoot
        val thumbRadius = with(compose.density) { 10.dp.toPx() }
        val trackWidth = bounds.width - 2f * thumbRadius
        val initialFraction = ((info.current - info.range.start) /
            (info.range.endInclusive - info.range.start)).coerceIn(0f, 1f)
        fun point(fraction: Float) = Offset(thumbRadius + trackWidth * fraction, bounds.height / 2f)
        fun move(fraction: Float): PdfOverlay {
            slider.performTouchInput { moveTo(point(fraction), delayMillis = 32L) }
            compose.waitForIdle()
            return compose.runOnIdle {
                assertTrue("The same pointer must still own the drag after recomposition", fixture.dragging)
                assertTrue("Every preview must remain valid", fixture.draft.isValid())
                fixture.draft
            }
        }
        fun measure(overlay: PdfOverlay) =
            if (original.kind == PdfOverlayKind.TEXT) overlay.fontSize else overlay.width

        slider.performTouchInput { down(point(initialFraction)) }
        compose.waitForIdle()
        try {
            move(.72f) // Cross touch slop and allow a first recomposition before comparing positions.
            val reference = move(.58f)
            val smaller = move(.25f)
            assertTrue("Dragging left must shrink the element", measure(smaller) < measure(reference))
            val returned = move(.58f)
            assertSameSize(reference, returned)

            val largest = move(1.08f)
            assertTrue("The upper end must grow the element", measure(largest) > measure(reference))
            val afterUpperEnd = move(.58f)
            assertSameSize(reference, afterUpperEnd)

            val smallest = move(-.08f)
            assertTrue("The lower end must shrink the element", measure(smallest) < measure(reference))
            val afterLowerEnd = move(.58f)
            assertSameSize(reference, afterLowerEnd)

            val last = move(.82f)
            assertTrue("The drag must still respond after crossing both ends", measure(last) > measure(reference))
        } finally {
            slider.performTouchInput { up() }
            compose.waitForIdle()
        }
        compose.runOnIdle {
            assertFalse(fixture.dragging)
            assertEquals("The gesture must not stop and restart between updates",
                listOf(false, true, false), fixture.gestureTransitions)
            assertTrue("The preview must update throughout the gesture", fixture.updates.size >= 8)
            if (original.kind == PdfOverlayKind.TEXT) fixture.updates.forEach { overlay ->
                assertEquals("Dragging a font slider must never emit fractional points",
                    overlay.fontSize.roundToInt().toFloat(), overlay.fontSize, 0f)
            }
        }
        return fixture
    }

    private fun assertSameSize(expected: PdfOverlay, actual: PdfOverlay) {
        assertEquals("Same pointer position must produce the same width", expected.width, actual.width, .0005f)
        assertEquals("Same pointer position must produce the same height", expected.height, actual.height, .0005f)
        assertEquals("Same pointer position must produce the same font size", expected.fontSize, actual.fontSize, .02f)
    }

    private fun showSlider(fixture: Fixture, transform: Boolean) {
        compose.setContent {
            ScaniqoTheme {
                Surface {
                    if (transform) {
                        FillMarkTransformContent(
                            draft = fixture.draft,
                            editable = true,
                            onUpdate = fixture::update,
                            onCancel = {},
                            onApply = {},
                            maxHeight = 420.dp,
                            onGestureActiveChange = fixture::gestureChanged,
                        )
                    } else {
                        Column(Modifier.fillMaxWidth().padding(24.dp)) {
                            FillMarkSizeSlider(
                                draft = fixture.draft,
                                enabled = true,
                                onUpdate = fixture::update,
                                onGestureActiveChange = fixture::gestureChanged,
                            )
                        }
                    }
                }
            }
        }
        if (transform) compose.onNodeWithTag("fill_transform_SIZE").performClick()
    }

    private fun mark() = PdfOverlay(
        id = "mark-size-fixture", kind = PdfOverlayKind.CHECKMARK,
        x = .45f, y = .4f, width = .04f, height = .03f,
    )

    private fun text() = PdfOverlay(
        id = "text-size-fixture", kind = PdfOverlayKind.TEXT,
        text = "Nome de exemplo", x = .2f, y = .25f, width = .6f, height = .1f,
        autoSizeText = true, wrapText = true,
    )

    private class Fixture(original: PdfOverlay, private val transform: Boolean) {
        var draft by mutableStateOf(original)
        val updates = mutableListOf<PdfOverlay>()
        var dragging = false
        val gestureTransitions = mutableListOf(false)

        // Match the editor: font properties refit automatic text; Transform preserves the
        // proportional box so measurement cannot feed a different size back into the slider.
        fun update(value: PdfOverlay) {
            val old = draft
            val measured = if (!transform && value.kind == PdfOverlayKind.TEXT && value.autoSizeText &&
                (value.text != old.text || value.fontSize != old.fontSize ||
                    value.bold != old.bold || value.italic != old.italic || !old.autoSizeText)
            ) value.fittedFillMarkText() else value
            draft = if (measured.rotationDegrees != 0f) measured.rotatedFillMark(measured.rotationDegrees) else measured
            updates += draft
        }

        fun gestureChanged(active: Boolean) {
            dragging = active
            if (gestureTransitions.last() != active) gestureTransitions += active
        }
    }
}
