package com.joseleandro.scaniqo.domain.model

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class PdfOverlayTest {
    @Test fun legacySerializedTextKeepsItsOriginalBoxAndRenderingDefaults() {
        val restored = Json.decodeFromString<PdfOverlay>(
            """{"id":"legacy","kind":"TEXT","text":"Nome","width":0.4,"height":0.1}""",
        )
        assertEquals(.4f, restored.width, .00001f)
        assertEquals(.1f, restored.height, .00001f)
        assertEquals(PdfTextHorizontalAlignment.START, restored.textHorizontalAlignment)
        assertEquals(PdfTextVerticalAlignment.TOP, restored.textVerticalAlignment)
        assertFalse(restored.autoSizeText)
        assertFalse(restored.wrapText)
        assertFalse(restored.bold)
        assertFalse(restored.italic)
        assertFalse(restored.underlined)
        assertEquals(0f, restored.rotationDegrees, .00001f)
    }

    @Test fun textAlignmentAndSizingSurviveRecipeSerialization() {
        val original = PdfOverlay(
            "aligned", PdfOverlayKind.TEXT, text = "Maria",
            textHorizontalAlignment = PdfTextHorizontalAlignment.END,
            textVerticalAlignment = PdfTextVerticalAlignment.BOTTOM,
            autoSizeText = true, wrapText = true,
        )
        assertEquals(original, Json.decodeFromString<PdfOverlay>(Json.encodeToString(original)))
    }

    @Test fun styledTextAndCrossmarkSurviveRecipeSerialization() {
        val styled = PdfOverlay("styled", PdfOverlayKind.TEXT, text = "São Paulo",
            bold = true, italic = true, underlined = true)
        val cross = PdfOverlay("cross", PdfOverlayKind.CROSSMARK, width = .04f, height = .03f)
        assertEquals(styled, Json.decodeFromString<PdfOverlay>(Json.encodeToString(styled)))
        assertEquals(cross, Json.decodeFromString<PdfOverlay>(Json.encodeToString(cross)))
        assertTrue(cross.isValid())
    }

    @Test fun rotatedTextAndMarksSurviveSerializationWithoutChangingTheirStyles() {
        val text = PdfOverlay(
            "rotated-text", PdfOverlayKind.TEXT, text = "Nome", rotationDegrees = -37.5f,
            bold = true, italic = true, underlined = true,
            textHorizontalAlignment = PdfTextHorizontalAlignment.END,
            textVerticalAlignment = PdfTextVerticalAlignment.BOTTOM,
        )
        val mark = PdfOverlay(
            "rotated-mark", PdfOverlayKind.CHECKMARK, width = .08f, height = .06f,
            rotationDegrees = 90f,
        )
        listOf(text, mark).forEach { overlay ->
            assertEquals(overlay, Json.decodeFromString<PdfOverlay>(Json.encodeToString(overlay)))
        }
    }

    @Test fun movingRotatedOverlayKeepsItsVisibleCornersInsideTheA4Page() {
        for (angle in listOf(-135f, -37f, 45f, 90f)) {
            val overlay = PdfOverlay(
                "rotated", PdfOverlayKind.TEXT, x = .25f, y = .4f,
                width = .3f, height = .2f, rotationDegrees = angle,
            )
            for (target in listOf(-1f to -1f, 2f to 2f)) {
                val moved = overlay.movedTo(target.first, target.second)
                assertEquals(overlay.width, moved.width)
                assertEquals(overlay.height, moved.height)
                assertEquals(angle, moved.rotationDegrees)
                val centerX = (moved.x + moved.width / 2f) * 595f
                val centerY = (moved.y + moved.height / 2f) * 842f
                val radians = Math.toRadians(angle.toDouble())
                val cosine = kotlin.math.cos(radians).toFloat()
                val sine = kotlin.math.sin(radians).toFloat()
                for (horizontal in listOf(-1f, 1f)) {
                    for (vertical in listOf(-1f, 1f)) {
                        val dx = horizontal * moved.width * 595f / 2f
                        val dy = vertical * moved.height * 842f / 2f
                        val x = centerX + cosine * dx - sine * dy
                        val y = centerY + sine * dx + cosine * dy
                        assertTrue("Rotated corner x=$x for $angle degrees", x in -.001f..595.001f)
                        assertTrue("Rotated corner y=$y for $angle degrees", y in -.001f..842.001f)
                    }
                }
                assertTrue(moved.isValid())
            }
        }
    }

    @Test fun movingDrawingTranslatesEveryPointAndClampsToPage() {
        val drawing = PdfOverlay("d", PdfOverlayKind.DRAWING, x = .1f, y = .2f, width = .3f, height = .2f,
            points = listOf(PdfPoint(.1f, .2f), PdfPoint(.4f, .4f)))
        val moved = drawing.movedTo(.9f, .95f)
        assertEquals(.7f, moved.x, .0001f)
        assertEquals(.8f, moved.y, .0001f)
        assertEquals(.7f, moved.points.first().x, .0001f)
        assertEquals(1f, moved.points.last().x, .0001f)
        assertEquals(1f, moved.points.last().y, .0001f)
        assertTrue(moved.isValid())
    }

    @Test fun rejectsNonFiniteOutOfBoundsAndOversizedOverlay() {
        val valid = PdfOverlay("text", PdfOverlayKind.TEXT)
        assertTrue(valid.isValid())
        assertFalse(valid.copy(opacity = Float.NaN).isValid())
        assertFalse(valid.copy(rotationDegrees = Float.NaN).isValid())
        assertFalse(valid.copy(rotationDegrees = Float.POSITIVE_INFINITY).isValid())
        assertFalse(valid.copy(x = .9f, width = .2f).isValid())
        assertFalse(valid.copy(fontSize = Float.POSITIVE_INFINITY).isValid())
        assertFalse(valid.copy(text = "x".repeat(2001)).isValid())
        assertFalse(valid.copy(points = listOf(PdfPoint(-.01f, .5f))).isValid())
    }

    @Test fun redactionRegionsRejectInvalidGeometryAndAllowFullPage() {
        val full = PdfRedactionArea("page", 0f, 0f, 1f, 1f)
        assertTrue(full.isValid())
        assertFalse(full.copy(width = 0f).isValid())
        assertFalse(full.copy(x = Float.NaN).isValid())
        assertFalse(full.copy(y = .1f).isValid())
        assertFalse(full.copy(pageId = "").isValid())
    }
}
