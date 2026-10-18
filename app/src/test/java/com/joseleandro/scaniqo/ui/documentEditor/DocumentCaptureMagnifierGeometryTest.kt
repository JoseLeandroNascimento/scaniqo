package com.joseleandro.scaniqo.ui.documentEditor

import com.joseleandro.scaniqo.domain.model.PdfPoint
import com.joseleandro.scaniqo.ui.documentEditor.util.CaptureMagnifierPlacement
import com.joseleandro.scaniqo.ui.documentEditor.util.captureMagnifierTransform
import com.joseleandro.scaniqo.ui.documentEditor.util.placeCaptureMagnifier
import kotlin.math.max
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentCaptureMagnifierGeometryTest {
    @Test
    fun normalPositionIsAboveTheFingerWithClearance() {
        val placement = place(320f, 420f, 160f, 240f)!!
        assertEquals(104f, placement.left, .001f)
        assertEquals(104f, placement.top, .001f)
        assertSafe(placement, 320f, 420f, 160f, 240f)
    }

    @Test
    fun topCornerMovesTheLensBelowTheFinger() {
        val placement = place(320f, 420f, 24f, 24f)!!
        assertTrue(placement.top > 24f)
        assertSafe(placement, 320f, 420f, 24f, 24f)
    }

    @Test
    fun bottomCornerKeepsTheLensInsideTheCanvas() {
        val placement = place(320f, 420f, 300f, 396f)!!
        assertTrue(placement.top + placement.height < 396f)
        assertSafe(placement, 320f, 420f, 300f, 396f)
    }

    @Test
    fun landscapeUsesTheSideWhenThereIsNoVerticalRoom() {
        val placement = place(420f, 150f, 210f, 75f)!!
        assertTrue(placement.left + placement.width <= 170f || placement.left >= 250f)
        assertSafe(placement, 420f, 150f, 210f, 75f)
    }

    @Test
    fun allCornerAndCenterPositionsAvoidTheFingerInCommonViewports() {
        for ((width, height) in listOf(280f to 400f, 420f to 220f, 240f to 300f)) {
            for (x in listOf(24f, width / 2f, width - 24f)) {
                for (y in listOf(24f, height / 2f, height - 24f)) {
                    val placement = place(width, height, x, y)
                    assertNotNull(placement)
                    assertSafe(placement!!, width, height, x, y)
                }
            }
        }
    }

    @Test
    fun insufficientSpaceHidesTheLensWithoutCoveringTheFinger() {
        assertNull(place(100f, 100f, 50f, 50f))
        assertNull(place(50f, 200f, 25f, 100f))
    }

    @Test
    fun invalidGeometryHasNoPlacement() {
        assertNull(place(0f, 200f, 20f, 20f))
        assertNull(place(300f, 400f, Float.NaN, 20f))
    }

    @Test
    fun theSelectedPixelAlwaysRemainsUnderTheCrosshair() {
        for (focus in listOf(PdfPoint(0f, 0f), PdfPoint(.35f, .7f), PdfPoint(1f, 1f))) {
            val transform = captureMagnifierTransform(focus, 240f, 320f, 960, 1280, 112f, 96f)!!
            assertEquals(56f, focus.x * 960 * transform.scaleX + transform.translateX, .001f)
            assertEquals(48f, focus.y * 1280 * transform.scaleY + transform.translateY, .001f)
        }
    }

    @Test
    fun zoomIsRelativeToTheImageOnScreenRatherThanItsBitmapResolution() {
        val low = captureMagnifierTransform(PdfPoint(.5f, .5f), 200f, 300f, 200, 300, 112f, 96f)!!
        val high = captureMagnifierTransform(PdfPoint(.5f, .5f), 200f, 300f, 1000, 1500, 112f, 96f)!!
        assertEquals(low.scaleX * 200, high.scaleX * 1000, .001f)
        assertEquals(low.translateX, high.translateX, 0f)
        assertEquals(low.translateY, high.translateY, 0f)
    }

    @Test
    fun emptyBitmapCannotProduceASamplingTransform() {
        assertNull(captureMagnifierTransform(PdfPoint(.5f, .5f), 200f, 300f, 0, 10, 112f, 96f))
    }

    private fun place(width: Float, height: Float, x: Float, y: Float) = placeCaptureMagnifier(
        width, height, x, y, 112f, 96f, 8f, 40f, 72f, 64f,
    )

    private fun assertSafe(lens: CaptureMagnifierPlacement, width: Float, height: Float, x: Float, y: Float) {
        assertTrue(lens.left >= 8f && lens.top >= 8f)
        assertTrue(lens.left + lens.width <= width - 8f + .01f)
        assertTrue(lens.top + lens.height <= height - 8f + .01f)
        val dx = max(max(lens.left - x, 0f), x - lens.left - lens.width)
        val dy = max(max(lens.top - y, 0f), y - lens.top - lens.height)
        assertTrue(dx * dx + dy * dy >= 40f * 40f - .01f)
    }
}
