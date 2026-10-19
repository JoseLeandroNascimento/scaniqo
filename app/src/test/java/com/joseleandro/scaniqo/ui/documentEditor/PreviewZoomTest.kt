package com.joseleandro.scaniqo.ui.documentEditor

import androidx.compose.ui.geometry.Offset
import com.joseleandro.scaniqo.ui.documentCommon.components.PreviewZoom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewZoomTest {
    @Test fun pinchKeepsDocumentPointUnderFinger() {
        val width = 400f
        val height = 800f
        val before = PreviewZoom(2f, .1f, -.2f)
        val centroid = Offset(240f, 320f)
        val after = before.transformed(centroid, Offset.Zero, 1.5f, width, height)
        fun documentPoint(value: PreviewZoom) = Offset(
            (centroid.x / width - .5f - value.x) / value.scale,
            (centroid.y / height - .5f - value.y) / value.scale,
        )
        assertEquals(documentPoint(before).x, documentPoint(after).x, .0001f)
        assertEquals(documentPoint(before).y, documentPoint(after).y, .0001f)
    }

    @Test fun returningToFitRemovesPanAndNeverExposesOutsideContent() {
        val value = PreviewZoom(4f, 1f, -1f)
            .transformed(Offset(200f, 400f), Offset(300f, 100f), .1f, 400f, 800f)
        assertEquals(PreviewZoom(), value)
    }

    @Test fun pinchAndPanAreBoundedAtAllEdges() {
        val value = PreviewZoom().transformed(Offset.Zero, Offset(9000f, -9000f), 20f, 400f, 800f)
        assertEquals(6f, value.scale)
        assertEquals(2.5f, value.x)
        assertEquals(-2.5f, value.y)
        assertTrue(value.scale.isFinite())
    }

    @Test fun panUsesScreenPixelsIndependentOfZoomAndWindowSize() {
        val value = PreviewZoom(2f).transformed(Offset(100f, 100f), Offset(40f, 80f), 1f, 400f, 800f)
        assertEquals(.1f, value.x, .0001f)
        assertEquals(.1f, value.y, .0001f)
    }

    @Test fun letterboxedPageCannotBePannedOutOfTheViewport() {
        val value = PreviewZoom().transformed(
            Offset(200f, 400f), Offset(9000f, -9000f), 2f,
            width = 400f, height = 800f, contentWidth = 400f, contentHeight = 560f,
        )
        assertEquals(.5f, value.x, .0001f)
        assertEquals(-.2f, value.y, .0001f)
        val fitted = value.transformed(
            Offset(200f, 400f), Offset.Zero, .5f,
            width = 400f, height = 800f, contentWidth = 400f, contentHeight = 560f,
        )
        assertEquals(PreviewZoom(), fitted)
    }

    @Test fun invalidPointerValuesNeverCorruptPreview() {
        val value = PreviewZoom(2f, .1f, .2f)
        assertEquals(value, value.transformed(Offset.Unspecified, Offset.Zero, 2f, 400f, 800f))
        assertEquals(value, value.transformed(Offset.Zero, Offset.Zero, Float.NaN, 400f, 800f))
        assertEquals(value, value.transformed(Offset.Zero, Offset.Zero, 2f, 0f, 800f))
    }
}
