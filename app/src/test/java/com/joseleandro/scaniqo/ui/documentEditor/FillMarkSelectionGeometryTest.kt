package com.joseleandro.scaniqo.ui.documentEditor

import com.joseleandro.scaniqo.domain.model.PdfOverlay
import com.joseleandro.scaniqo.domain.model.PdfOverlayKind
import com.joseleandro.scaniqo.domain.model.PdfPoint
import com.joseleandro.scaniqo.ui.documentEditor.components.fillMarkSelectionGeometry
import com.joseleandro.scaniqo.ui.documentEditor.components.scaledFillMarkAtCorner
import com.joseleandro.scaniqo.ui.documentEditor.components.scaledFillMarkFromCorner
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class FillMarkSelectionGeometryTest {
    private val text = PdfOverlay("text", PdfOverlayKind.TEXT, text = "Maria Silva",
        x = .2f, y = .3f, width = .4f, height = .1f, fontSize = 16f)

    @Test fun everyCornerKeepsItsOppositeAnchorAndScalesTextUniformly() {
        for (corner in 0..3) {
            val result = text.scaledFillMarkAtCorner(corner, 1.5f)
            val fromLeft = corner == 0 || corner == 3
            val fromTop = corner < 2
            assertEquals(if (fromLeft) text.x + text.width else text.x,
                if (fromLeft) result.x + result.width else result.x, .00001f)
            assertEquals(if (fromTop) text.y + text.height else text.y,
                if (fromTop) result.y + result.height else result.y, .00001f)
            assertEquals(text.width / text.height, result.width / result.height, .00001f)
            assertEquals(24f, result.fontSize, .00001f)
            assertTrue(result.isValid())
        }
    }

    @Test fun pageZoomDoesNotChangeTheDocumentScale() {
        val normal = text.scaledFillMarkFromCorner(2, 120f, 42.5f, 600f, 850f)
        val zoomed = text.scaledFillMarkFromCorner(2, 360f, 127.5f, 1800f, 2550f)
        assertEquals(normal, zoomed)
        assertEquals(24f, normal.fontSize, .00001f)
    }

    @Test fun touchingAnOffsetHandleWithoutMovingDoesNotChangeTheText() {
        assertEquals(text, text.scaledFillMarkFromCorner(0, 0f, 0f, 600f, 850f))
    }

    @Test fun shrinkingPastTheOppositeCornerCannotInvertOrEraseTheText() {
        for (corner in 0..3) {
            val result = text.scaledFillMarkAtCorner(corner, -10f)
            assertEquals(6f, result.fontSize, .00001f)
            assertTrue(result.width > 0f && result.height > 0f)
            assertTrue(result.isValid())
        }
    }

    @Test fun scalingStopsAtThePageEdgeWhilePreservingFontProportion() {
        val result = text.scaledFillMarkAtCorner(0, 50f)
        assertEquals(0f, result.x, .00001f)
        assertEquals(1.5f, result.width / text.width, .00001f)
        assertEquals(result.width / text.width, result.fontSize / text.fontSize, .00001f)
        assertTrue(result.isValid())
    }

    @Test fun scalingRespectsMaximumFontSize() {
        val small = text.copy(x = .2f, y = .2f, width = .04f, height = .01f, fontSize = 60f)
        val result = small.scaledFillMarkAtCorner(2, 20f)
        assertEquals(72f, result.fontSize, .00001f)
        assertEquals(1.2f, result.width / small.width, .00001f)
        assertTrue(result.isValid())
    }

    @Test fun invalidPointersDoNotCorruptDraft() {
        assertEquals(text, text.scaledFillMarkFromCorner(2, Float.NaN, 0f, 600f, 850f))
        assertEquals(text, text.scaledFillMarkFromCorner(2, 50f, 0f, 0f, 850f))
        assertEquals(text, text.scaledFillMarkAtCorner(4, 2f))
        assertEquals(text, text.scaledFillMarkAtCorner(2, Float.POSITIVE_INFINITY))
    }

    @Test fun drawingCoordinatesFollowTheUniformScaleAndAnchor() {
        val drawing = text.copy(kind = PdfOverlayKind.DRAWING,
            points = listOf(PdfPoint(.2f, .3f), PdfPoint(.6f, .4f)))
        val result = drawing.scaledFillMarkAtCorner(0, 1.5f)
        assertEquals(result.x, result.points.first().x, .00001f)
        assertEquals(result.y, result.points.first().y, .00001f)
        assertEquals(.6f, result.points.last().x, .00001f)
        assertEquals(.4f, result.points.last().y, .00001f)
    }

    @Test fun thinTextKeepsFiveSeparateTouchTargetsWithoutChangingItsBox() {
        val thin = text.copy(width = .5f, height = .015f)
        val geometry = fillMarkSelectionGeometry(thin, 0f, 0f, 320f, 450f, 320f, 240f, 48f, 4f, 48f)
        assertEquals(5, geometry.visibleHandles.size)
        geometry.handles.forEachIndexed { index, handle ->
            assertTrue(handle.x in 24f..296f && handle.y in 24f..216f)
            assertEquals(index, geometry.handleAt(handle))
            geometry.handles.drop(index + 1).forEach { other ->
                assertTrue(abs(handle.x - other.x) >= 51.99f || abs(handle.y - other.y) >= 51.99f)
            }
        }
        assertEquals(.015f, thin.height, .00001f)
    }

    @Test fun partiallyVisibleTextStillHasAnAccessibleMoveHandle() {
        val geometry = fillMarkSelectionGeometry(text, -500f, -100f, 1800f, 2400f,
            320f, 800f, 48f, 4f, 48f)
        assertTrue(4 in geometry.visibleHandles)
        assertTrue(geometry.handles[4].x in 24f..296f)
        assertEquals(4, geometry.handleAt(geometry.handles[4]))
    }

    @Test fun offscreenTextDoesNotLeaveDetachedHandlesOnThePage() {
        val geometry = fillMarkSelectionGeometry(text, -2000f, -2000f, 600f, 850f,
            320f, 400f, 48f, 4f, 48f)
        assertTrue(geometry.visibleHandles.isEmpty())
        assertNull(geometry.handleAt(geometry.handles[4]))
    }
}
