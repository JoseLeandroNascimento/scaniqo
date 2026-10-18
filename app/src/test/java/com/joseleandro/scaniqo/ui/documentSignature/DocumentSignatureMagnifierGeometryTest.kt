package com.joseleandro.scaniqo.ui.documentSignature

import com.joseleandro.scaniqo.domain.model.PdfSignaturePlacement
import com.joseleandro.scaniqo.ui.documentEditor.util.placeCaptureMagnifier
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentSignatureMagnifierGeometryTest {
    private val signature = PdfSignaturePlacement("draft", "signature", .25f, .6f, .25f, .08f)

    @Test fun focusesTheSignatureCenterRatherThanTheFinger() {
        val result = requireNotNull(signatureMagnifierViewport(signature, 300f, 420f, 224f, 160f))
        assertEquals(112f, result.translateX + result.centerX * result.zoom, .001f)
        assertEquals(80f, result.translateY + result.centerY * result.zoom, .001f)
        assertEquals(112.5f, result.centerX, .001f)
        assertEquals(268.8f, result.centerY, .001f)
        assertTrue(result.zoom > 1f)
    }

    @Test fun rotatedSignatureAndSurroundingDocumentFitTogetherAtEveryAngle() {
        for (angle in listOf(0f, 30f, 90f, -55f, 180f)) {
            val current = signature.copy(rotationDegrees = angle)
            val result = requireNotNull(signatureMagnifierViewport(current, 300f, 420f, 224f, 160f))
            val radians = Math.toRadians(angle.toDouble())
            val width = abs(current.width * 300f * cos(radians)) + abs(current.height * 420f * sin(radians))
            val height = abs(current.width * 300f * sin(radians)) + abs(current.height * 420f * cos(radians))
            assertTrue(width * result.zoom * 1.45f <= 224.01f)
            assertTrue(height * result.zoom * 1.45f <= 160.01f)
        }
    }

    @Test fun largeSignatureKeepsARealMagnificationCenteredOnItsContext() {
        val result = requireNotNull(signatureMagnifierViewport(
            signature.copy(width = .9f, height = .8f), 300f, 420f, 180f, 120f))
        assertEquals(1.25f, result.zoom, 0f)
        assertEquals(90f, result.translateX + result.centerX * result.zoom, .001f)
        assertEquals(60f, result.translateY + result.centerY * result.zoom, .001f)
    }

    @Test fun aTinySignatureHasABoundedMagnification() {
        val result = requireNotNull(signatureMagnifierViewport(
            signature.copy(width = .01f, height = .01f), 300f, 420f, 224f, 160f))
        assertEquals(3f, result.zoom, 0f)
    }

    @Test fun legacyInkFitsAndIndependentDimensionsFillTheSameSelectionBounds() {
        val current = signature.copy(width = .4f, height = .2f)
        val fitted = requireNotNull(signatureMagnifierImageBounds(current, 300f, 420f, 400, 100))
        val stretched = requireNotNull(signatureMagnifierImageBounds(
            current.copy(stretchToBounds = true), 300f, 420f, 400, 100))
        assertEquals(120f, fitted.width, .001f)
        assertEquals(30f, fitted.height, .001f)
        assertEquals(120f, stretched.width, .001f)
        assertEquals(84f, stretched.height, .001f)
        assertEquals(fitted.left + fitted.width / 2f, stretched.left + stretched.width / 2f, .001f)
        assertEquals(fitted.top + fitted.height / 2f, stretched.top + stretched.height / 2f, .001f)
    }

    @Test fun pageEdgeRemainsAtItsTruePositionInsideTheContextView() {
        val current = signature.copy(x = 0f, y = 0f)
        val result = requireNotNull(signatureMagnifierViewport(current, 300f, 420f, 224f, 160f))
        assertTrue(result.translateX > 0f)
        assertTrue(result.translateY > 0f)
    }

    @Test fun lensStaysWithinTheStageAndAwayFromTheFinger() {
        for ((x, y) in listOf(10f to 10f, 350f to 450f, 180f to 240f)) {
            val lens = placeCaptureMagnifier(360f, 480f, x, y, 224f, 160f, 8f, 40f, 128f, 96f)
            assertNotNull(lens)
            requireNotNull(lens)
            assertTrue(lens.left >= 8f && lens.top >= 8f)
            assertTrue(lens.left + lens.width <= 352.01f && lens.top + lens.height <= 472.01f)
            val dx = maxOf(lens.left - x, 0f, x - lens.left - lens.width)
            val dy = maxOf(lens.top - y, 0f, y - lens.top - lens.height)
            assertTrue(dx * dx + dy * dy >= 1599.99f)
        }
    }

    @Test fun invalidGeometryDoesNotProduceAnUnboundedCanvasTransform() {
        assertNull(signatureMagnifierViewport(signature, 0f, 420f, 224f, 160f))
        assertNull(signatureMagnifierViewport(signature.copy(rotationDegrees = Float.NaN), 300f, 420f, 224f, 160f))
        assertNull(signatureMagnifierImageBounds(signature, 300f, 420f, 0, 100))
    }
}
