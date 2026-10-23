package com.joseleandro.scaniqo.ui.documentEditor

import com.joseleandro.scaniqo.domain.model.PdfCropBounds
import com.joseleandro.scaniqo.domain.model.PdfImageFilter
import com.joseleandro.scaniqo.domain.model.PdfPageLayout
import com.joseleandro.scaniqo.domain.model.PdfPerspective
import com.joseleandro.scaniqo.domain.model.PdfPoint
import com.joseleandro.scaniqo.ui.documentEditor.util.withCorrectionAdjustmentsFrom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentCorrectionDraftTest {
    private val perspective = PdfPerspective(
        PdfPoint(.1f, .05f), PdfPoint(.9f, .15f),
        PdfPoint(.95f, .9f), PdfPoint(.05f, .95f),
    )

    @Test fun openingCropPreservesLegacyPerspectiveUntilAnExplicitEdit() {
        val original = PdfPageLayout(perspective = perspective, straightenDegrees = 3f)
        assertEquals(original, original.withCorrectionAdjustmentsFrom(original))
        assertEquals(perspective, original.withCorrectionAdjustmentsFrom(original.copy(curvature = .2f)).perspective)
    }

    @Test fun explicitCropReplacesPerspectiveAndPreservesOtherCorrections() {
        val original = PdfPageLayout(perspective = perspective, straightenDegrees = 3f, curvature = .2f)
        val draft = original.copy(crop = PdfCropBounds(.1f, .1f, .8f, .9f), perspective = null)
        val result = original.withCorrectionAdjustmentsFrom(draft)
        assertEquals(draft.crop, result.crop)
        assertNull(result.perspective)
        assertEquals(original.straightenDegrees, result.straightenDegrees)
        assertEquals(original.curvature, result.curvature)
    }

    @Test fun correctionCannotDisableRasterizationRequiredByAppliedFilter() {
        val filter = PdfImageFilter.entries.first { it != PdfImageFilter.ORIGINAL }
        val original = PdfPageLayout(imageFilter = filter, filterIntensity = 1f, rasterizeText = true)
        val result = original.withCorrectionAdjustmentsFrom(original.copy(rasterizeText = false))
        assertTrue(result.rasterizeText)
        assertEquals(original.imageFilter, result.imageFilter)
    }
}
