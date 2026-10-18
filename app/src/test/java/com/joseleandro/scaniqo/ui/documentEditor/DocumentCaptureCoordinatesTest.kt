package com.joseleandro.scaniqo.ui.documentEditor

import com.joseleandro.scaniqo.domain.model.PdfCropBounds
import com.joseleandro.scaniqo.domain.model.PdfPerspective
import com.joseleandro.scaniqo.domain.model.PdfPoint
import com.joseleandro.scaniqo.ui.documentEditor.util.DocumentCaptureCoordinates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class DocumentCaptureCoordinatesTest {
    private val image = PdfCropBounds(.2f, .1f, .8f, .9f)

    @Test
    fun noCropShowsOnlyTheImageWithoutPageMargins() {
        assertEquals(PdfCropBounds(), DocumentCaptureCoordinates.localCrop(null, image))
        assertEquals(PdfCropBounds(), DocumentCaptureCoordinates.localCrop(PdfCropBounds(), image))
    }

    @Test
    fun rectangleCoordinatesRoundTripThroughTheImageBounds() {
        val local = PdfCropBounds(.15f, .2f, .75f, .8f)
        val page = DocumentCaptureCoordinates.pageCrop(local, image)!!
        assertEquals(.29f, page.left, .00001f)
        assertEquals(.26f, page.top, .00001f)
        assertEquals(.65f, page.right, .00001f)
        assertEquals(.74f, page.bottom, .00001f)
        val restored = DocumentCaptureCoordinates.localCrop(page, image)
        assertEquals(local.left, restored.left, .00001f)
        assertEquals(local.top, restored.top, .00001f)
        assertEquals(local.right, restored.right, .00001f)
        assertEquals(local.bottom, restored.bottom, .00001f)
    }

    @Test
    fun aLegacyPageCropIsClippedToTheVisibleImage() {
        val local = DocumentCaptureCoordinates.localCrop(PdfCropBounds(0f, 0f, .5f, 1f), image)
        assertEquals(0f, local.left, 0f)
        assertEquals(0f, local.top, 0f)
        assertEquals(.5f, local.right, .00001f)
        assertEquals(1f, local.bottom, 0f)
    }

    @Test
    fun restoringTheWholeImageClearsCropWithoutAddingAnExportCrop() {
        assertNull(DocumentCaptureCoordinates.pageCrop(PdfCropBounds(), image))
    }

    @Test
    fun maskCoordinatesRoundTripWithoutMovingTheCrop() {
        val local = PdfPerspective(
            PdfPoint(.1f, .2f), PdfPoint(.85f, .15f),
            PdfPoint(.9f, .9f), PdfPoint(.15f, .8f),
        )
        val page = DocumentCaptureCoordinates.pageMask(local, image)!!
        val restored = DocumentCaptureCoordinates.localMask(page, image)
        assertPoint(local.topLeft, restored.topLeft)
        assertPoint(local.topRight, restored.topRight)
        assertPoint(local.bottomRight, restored.bottomRight)
        assertPoint(local.bottomLeft, restored.bottomLeft)
        assertEquals(PdfCropBounds(.2f, .1f, .8f, .9f), image)
    }

    @Test
    fun identityMaskPreservesTheWholeCapture() {
        assertEquals(PdfPerspective(), DocumentCaptureCoordinates.localMask(null, image))
        assertNull(DocumentCaptureCoordinates.pageMask(PdfPerspective(), image))
    }

    @Test
    fun fullPageSourcesKeepTheExistingCoordinateSystem() {
        val crop = PdfCropBounds(.1f, .1f, .9f, .9f)
        assertEquals(crop, DocumentCaptureCoordinates.pageCrop(crop, PdfCropBounds()))
        assertEquals(crop, DocumentCaptureCoordinates.localCrop(crop, PdfCropBounds()))
    }

    @Test
    fun invalidGeometryCannotBePersisted() {
        assertNull(DocumentCaptureCoordinates.pageCrop(PdfCropBounds(right = Float.NaN), image))
        assertNull(DocumentCaptureCoordinates.pageMask(PdfPerspective(topLeft = PdfPoint(Float.NaN, 0f)), image))
        assertNotNull(DocumentCaptureCoordinates.localCrop(PdfCropBounds(0f, 0f, .1f, .05f), image))
    }

    private fun assertPoint(expected: PdfPoint, actual: PdfPoint) {
        assertEquals(expected.x, actual.x, .00001f)
        assertEquals(expected.y, actual.y, .00001f)
    }
}
