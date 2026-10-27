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
