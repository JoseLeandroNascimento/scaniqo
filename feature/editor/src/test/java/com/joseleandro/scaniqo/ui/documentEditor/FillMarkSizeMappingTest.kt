package com.joseleandro.scaniqo.ui.documentEditor

import com.joseleandro.scaniqo.domain.model.PdfOverlay
import com.joseleandro.scaniqo.domain.model.PdfOverlayKind
import com.joseleandro.scaniqo.domain.model.PdfTextHorizontalAlignment
import com.joseleandro.scaniqo.domain.model.PdfTextVerticalAlignment
import com.joseleandro.scaniqo.ui.documentEditor.components.FillMarkSizeMapping
import com.joseleandro.scaniqo.ui.documentEditor.components.withFillMarkFontSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class FillMarkSizeMappingTest {
    private val text = PdfOverlay(
        "text", PdfOverlayKind.TEXT, text = "Nome de exemplo",
        x = .1f, y = .2f, width = .2f, height = .05f, fontSize = 16f,
        autoSizeText = true, wrapText = true,
    )
    private val mark = PdfOverlay(
        "mark", PdfOverlayKind.CHECKMARK, x = .4f, y = .4f,
        width = .04f, height = .03f,
    )

    @Test fun theSameSliderPositionProducesTheSameSizeAfterEitherExtreme() {
        val scenarios = listOf(
            text to false, text to true, text.copy(autoSizeText = false) to false,
            mark to false, mark.copy(kind = PdfOverlayKind.CROSSMARK) to true,
        )
        scenarios.forEach { (baseline, proportional) ->
            val mapping = FillMarkSizeMapping(baseline, proportional)
            val range = mapping.valueRange
            val middle = range.start + (range.endInclusive - range.start) * .57f
            val expected = mapping.resize(baseline, middle)
            var current = expected
            repeat(20) { iteration ->
                current = mapping.resize(current, if (iteration % 2 == 0) range.start else range.endInclusive)
                assertTrue(current.isValid())
                current = mapping.resize(current, middle)
                assertGeometry(expected, current)
                assertEquals("Changing the draft must not change the slider's limits", range, mapping.valueRange)
            }
        }
    }

    @Test fun aMarkCanGrowAcrossThePageWithoutAnInvisibleFontLimit() {
        val smallestFont = mark.copy(fontSize = 6f)
        val largestFont = mark.copy(fontSize = 72f)
        val smallMapping = FillMarkSizeMapping(smallestFont)
        val largeMapping = FillMarkSizeMapping(largestFont)
        assertEquals(smallMapping.valueRange, largeMapping.valueRange)
        assertEquals(1f, smallMapping.valueRange.endInclusive, .00001f)
        assertEquals(0, smallMapping.steps)

        listOf(smallestFont, largestFont).forEach { baseline ->
            val grown = FillMarkSizeMapping(baseline).resize(baseline, .8f)
            assertEquals(.8f, grown.width, .00001f)
            assertEquals(.6f, grown.height, .00001f)
            assertEquals(baseline.fontSize, grown.fontSize, 0f)
            assertTrue(grown.isValid())
        }
    }

    @Test fun returningToTheOriginalSizeRestoresItsAnchorAfterClampingAtThePageEdge() {
        val baselines = listOf(
            mark.copy(x = .86f, y = .88f, width = .1f, height = .06f, rotationDegrees = 25f),
            text.copy(x = .7f, y = .75f, width = .2f, height = .1f, autoSizeText = false),
        )
        baselines.forEach { baseline ->
            val mapping = FillMarkSizeMapping(baseline, proportionalText = true)
            val expanded = mapping.resize(baseline, mapping.valueRange.endInclusive)
            assertTrue(expanded.isValid())
            assertTrue(expanded.x < baseline.x)
            val restored = mapping.resize(expanded, mapping.value(baseline))
            assertGeometry(baseline, restored)
        }
    }

    @Test fun fontSlidersEmitWholePointsForAutomaticManualAndProportionalText() {
        val scenarios = listOf(text to false, text.copy(autoSizeText = false) to false, text to true)
        scenarios.forEach { (baseline, proportional) ->
            val mapping = FillMarkSizeMapping(baseline, proportional)
            assertEquals(6f, mapping.valueRange.start, 0f)
            assertEquals(72f, mapping.valueRange.endInclusive, 0f)
            assertEquals(65, mapping.steps)
            var current = baseline
            listOf(
                6.1f to 6f, 6.4f to 6f, 6.5f to 7f,
                10.75f to 11f, 71.9f to 72f, 100f to 72f, -10f to 6f,
            ).forEach { (request, expected) ->
                current = mapping.resize(current, request)
                assertEquals(expected, current.fontSize, 0f)
                assertTrue(current.isValid())
            }
        }
    }

    @Test fun legacyFractionalSizeIsRetainedUntilTheSliderIsUsedAndNumericInputStillAcceptsIt() {
        val legacy = text.copy(fontSize = 16.5f)
        val mapping = FillMarkSizeMapping(legacy)
        assertEquals(16.5f, mapping.value(legacy), 0f)
        assertEquals(17f, mapping.resize(legacy, 16.6f).fontSize, 0f)
        assertEquals(20.25f, legacy.withFillMarkFontSize(20.25f).fontSize, 0f)
    }

    @Test fun repeatingAFontValueKeepsTheEditorsAlreadyMeasuredBox() {
        val mapping = FillMarkSizeMapping(text)
        val resized = mapping.resize(text, 24f)
        // The text measurement happens after the slider callback and can change both dimensions.
        val measured = resized.copy(width = .37f, height = .085f, x = .11f, y = .22f)
        assertSame(measured, mapping.resize(measured, 24f))
        assertSame(measured, mapping.resize(measured, 24.2f))
    }

    @Test fun resizingPreservesCurrentContentStyleAlignmentAndIdentity() {
        val mapping = FillMarkSizeMapping(text, proportionalText = true)
        val current = text.copy(
            id = "same-selection",
            text = "Conteúdo editado",
            color = 0xFF1565C0.toInt(), opacity = .55f,
            bold = true, italic = true, underlined = true,
            textHorizontalAlignment = PdfTextHorizontalAlignment.END,
            textVerticalAlignment = PdfTextVerticalAlignment.BOTTOM,
            autoSizeText = false, wrapText = false,
            rotationDegrees = 30f,
        )
        val resized = mapping.resize(current, 24f)
        assertTrue(resized.width > current.width)
        assertEquals(current, resized.copy(
            x = current.x, y = current.y, width = current.width, height = current.height,
            fontSize = current.fontSize, points = current.points,
        ))
    }

    @Test fun manualFontLimitsRespectTheAnchoredCornerWhileTransformCanRepositionTheBox() {
        val edge = text.copy(x = .7f, width = .2f, autoSizeText = false)
        val fontProperty = FillMarkSizeMapping(edge)
        val transform = FillMarkSizeMapping(edge, proportionalText = true)
        val automatic = FillMarkSizeMapping(edge.copy(autoSizeText = true))
        assertEquals(24f, fontProperty.valueRange.endInclusive, 0f)
        assertEquals(72f, transform.valueRange.endInclusive, 0f)
        assertEquals(72f, automatic.valueRange.endInclusive, 0f)

        val anchored = fontProperty.resize(edge, 72f)
        assertEquals(24f, anchored.fontSize, 0f)
        assertEquals(edge.x, anchored.x, .00001f)
        assertTrue(anchored.x + anchored.width <= 1.00001f)
        assertTrue(anchored.isValid())

        val proportional = transform.resize(edge, 72f)
        assertEquals(72f, proportional.fontSize, 0f)
        assertTrue(proportional.x < edge.x)
        assertEquals(edge.width / edge.height, proportional.width / proportional.height, .00001f)
        assertTrue(proportional.isValid())
    }

    @Test fun nonFiniteRequestsLeaveTheCurrentDraftUntouched() {
        val mapping = FillMarkSizeMapping(text)
        listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY).forEach {
            assertSame(text, mapping.resize(text, it))
        }
    }

    private fun assertGeometry(expected: PdfOverlay, actual: PdfOverlay) {
        assertEquals(expected.x, actual.x, .00001f)
        assertEquals(expected.y, actual.y, .00001f)
        assertEquals(expected.width, actual.width, .00001f)
        assertEquals(expected.height, actual.height, .00001f)
        assertEquals(expected.fontSize, actual.fontSize, .00001f)
        assertEquals(expected.rotationDegrees, actual.rotationDegrees, 0f)
    }
}
