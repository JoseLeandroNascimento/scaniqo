package com.joseleandro.scaniqo.data.pdf

import com.joseleandro.scaniqo.domain.model.PdfCropBounds
import com.joseleandro.scaniqo.domain.model.PdfPerspective
import com.joseleandro.scaniqo.domain.model.PdfPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PdfCaptureGeometryTest {
    @Test fun selectedCropInsideCaptureNeedsNoAdditionalCropInResult() {
        val capture = PdfCropBounds(.2f, .25f, .8f, .75f)
        val selected = PdfCropBounds(.3f, .3f, .7f, .7f)
        val visible = requireNotNull(PdfCaptureGeometry.intersect(capture, selected))
        assertEquals(selected, visible)
        assertEquals(PdfCropBounds(), PdfCaptureGeometry.relativeTo(visible, selected))
        assertEquals(1100, PdfCaptureGeometry.resultMaxSide(600f, 800f, selected, visible, 1100))
    }

    @Test fun captureViewportDoesNotTurnItsSurroundingPdfMarginsIntoSelection() {
        val capture = PdfCropBounds(.2f, .25f, .8f, .75f)
        val renderArea = PdfCropBounds()
        assertEquals(capture, PdfCaptureGeometry.relativeTo(capture, renderArea))
        // A foto fica com 400 px de altura física dentro de uma página de 800 px.
        assertEquals(2200, PdfCaptureGeometry.resultMaxSide(600f, 800f, renderArea, capture, 1100))
        assertEquals(PdfCropBounds(), renderArea)
        assertNull(PdfCaptureGeometry.intersect(capture, PdfCropBounds(0f, 0f, .1f, .1f)))
    }

    @Test fun resultLimitsMemoryEvenWhenCaptureOccupiesSmallPartOfPage() {
        assertEquals(4000, PdfCaptureGeometry.resultMaxSide(
            600f, 800f, PdfCropBounds(), PdfCropBounds(.45f, .45f, .55f, .55f), 1100,
        ))
    }

    @Test fun automaticDocumentBoundaryKeepsPageCoordinatesForRecipeAndMask() {
        val capture = PdfCropBounds(.1f, .3f, .9f, .7f)
        val local = PdfPerspective(
            PdfPoint(.1f, .2f), PdfPoint(.8f, .1f),
            PdfPoint(.9f, .85f), PdfPoint(.05f, .9f),
        )
        val source = PdfCaptureGeometry.toSource(local, capture)
        assertEquals(.18f, source.topLeft.x, .0001f)
        assertEquals(.38f, source.topLeft.y, .0001f)
        assertEquals(.74f, source.topRight.x, .0001f)
        assertEquals(.34f, source.topRight.y, .0001f)
        assertEquals(.82f, source.bottomRight.x, .0001f)
        assertEquals(.64f, source.bottomRight.y, .0001f)
        assertEquals(.14f, source.bottomLeft.x, .0001f)
        assertEquals(.66f, source.bottomLeft.y, .0001f)
    }
}
