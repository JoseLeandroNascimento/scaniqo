package com.joseleandro.scaniqo.domain.model

import org.junit.Assert.*
import org.junit.Test

class PdfOverlayTest {
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
