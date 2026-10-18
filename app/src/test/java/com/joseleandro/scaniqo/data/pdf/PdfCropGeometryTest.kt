package com.joseleandro.scaniqo.data.pdf

import com.joseleandro.scaniqo.domain.model.PdfCropBounds
import com.joseleandro.scaniqo.domain.model.PdfPageLayout
import com.joseleandro.scaniqo.ui.documentEditor.preview.DocumentLivePageGeometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class PdfCropGeometryTest {
    @Test fun cropChangesVisibleAspectWithoutChangingRelativePixelScale() {
        val viewport = PdfCropGeometry.viewport(1000f, 500f, PdfCropBounds(.25f, .1f, .75f, .9f), 4000)
        assertEquals(500f, viewport.width, .001f)
        assertEquals(400f, viewport.height, .001f)
        assertEquals(4000, viewport.pixelWidth)
        assertEquals(3200, viewport.pixelHeight)
        assertEquals(250f, viewport.left, .001f)
        assertEquals(50f, viewport.top, .001f)
        // Uma circunferência continua circular; o recorte não estica uma região para a razão da fonte.
        val horizontalDiameter = 25f / viewport.width * viewport.pixelWidth
        val verticalDiameter = 25f / viewport.height * viewport.pixelHeight
        assertEquals(horizontalDiameter, verticalDiameter, .001f)
        assertEquals(1.25f, viewport.width / viewport.height, .001f)
    }

    @Test fun tightCropUsesFinalResolutionInsteadOfEnlargingSmallThumbnail() {
        val viewport = PdfCropGeometry.viewport(1000f, 1600f, PdfCropBounds(.4f, .4f, .6f, .6f), 4000)
        assertEquals(2500, viewport.pixelWidth)
        assertEquals(4000, viewport.pixelHeight)
        assertEquals(12.5f, viewport.scale, .001f)
        assertEquals(200f, viewport.width, .001f)
        assertEquals(320f, viewport.height, .001f)
    }

    @Test fun nativeRotatedBoxesMatchDisplayedCropAndKeepUniformPlacement() {
        val crop = PdfCropBounds(.1f, .2f, .65f, .9f)
        for (rotation in listOf(0, 90, 180, 270, -90, 450)) {
            val rotated = abs(rotation % 180) == 90
            val displayWidth = if (rotated) 300f else 200f
            val displayHeight = if (rotated) 200f else 300f
            val box = PdfCropGeometry.sourceBox(11f, 23f, 200f, 300f, rotation, crop)
            val visibleWidth = displayWidth * (crop.right - crop.left)
            val visibleHeight = displayHeight * (crop.bottom - crop.top)
            val original = PdfPageGeometry.placement(
                11f, 23f, 200f, 300f, rotation, PdfPageLayout(), displayWidth, displayHeight, 0f,
            )
            val cropped = PdfPageGeometry.placement(
                box.x, box.y, box.width, box.height, rotation, PdfPageLayout(),
                visibleWidth, visibleHeight, 0f,
            )
            for (x in listOf(box.x, box.x + box.width)) {
                for (y in listOf(box.y, box.y + box.height)) {
                    assertEquals("x em $rotation", original.x(x, y) - displayWidth * crop.left, cropped.x(x, y), .001f)
                    assertEquals("y em $rotation", original.y(x, y) - displayHeight * (1f - crop.bottom), cropped.y(x, y), .001f)
                }
            }
            assertEquals(1f, abs(cropped.a) + abs(cropped.b), .001f)
            assertEquals(1f, abs(cropped.c) + abs(cropped.d), .001f)
        }
    }

    @Test fun cropFitsA4UniformlyInsteadOfStretchingToOriginalPageDimensions() {
        val viewport = PdfCropGeometry.viewport(800f, 1200f, PdfCropBounds(.1f, .35f, .9f, .65f), 2400)
        val placement = PdfPageGeometry.placement(
            0f, 0f, viewport.width, viewport.height, 0, PdfPageLayout(),
        )
        assertEquals(placement.a, placement.d, 0f)
        val printedWidth = placement.x(viewport.width, 0f) - placement.x(0f, 0f)
        val printedHeight = placement.y(0f, viewport.height) - placement.y(0f, 0f)
        assertEquals(viewport.width / viewport.height, printedWidth / printedHeight, .001f)
        val live = DocumentLivePageGeometry.bitmapTransform(
            viewport.pixelWidth, viewport.pixelHeight, viewport.width, viewport.height, PdfPageLayout(),
        )
        assertEquals(printedWidth, live.scaleX * viewport.pixelWidth, .001f)
        assertEquals(printedHeight, live.scaleY * viewport.pixelHeight, .001f)
    }

    @Test fun identityPreservesSourceDimensionsAndShiftedNativeBox() {
        val viewport = PdfCropGeometry.viewport(600f, 800f, null, 2400)
        assertEquals(1800, viewport.pixelWidth)
        assertEquals(2400, viewport.pixelHeight)
        assertEquals(600f, viewport.width, 0f)
        assertEquals(800f, viewport.height, 0f)
        assertEquals(PdfCropGeometry.SourceBox(10f, 20f, 600f, 800f),
            PdfCropGeometry.sourceBox(10f, 20f, 600f, 800f, 90, PdfCropBounds()))
    }

    @Test fun boundsRejectInvalidGeometryButAllowMinimumCropAtImageEdge() {
        assertTrue(PdfCropBounds().isIdentity())
        assertTrue(PdfCropBounds(.9999f, .9999f, 1f, 1f).isValid())
        assertTrue(PdfCropBounds(left = .5f, right = .501f).isValid())
        assertFalse(PdfCropBounds(Float.NaN).isValid())
        assertFalse(PdfCropBounds(left = -.1f).isValid())
        assertFalse(PdfCropBounds(left = .8f, right = .2f).isValid())
        assertFalse(PdfCropBounds(left = .5f, right = .50001f).isValid())
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidRestoredBoundsAreRejectedBeforeAllocation() {
        PdfCropGeometry.viewport(600f, 800f, PdfCropBounds(right = Float.POSITIVE_INFINITY), 4000)
    }

    @Test(expected = IllegalArgumentException::class)
    fun renderLimitStillProtectsMemoryForVeryTightCrop() {
        PdfCropGeometry.viewport(600f, 800f, PdfCropBounds(.45f, .45f, .55f, .55f), 4001)
    }
}
