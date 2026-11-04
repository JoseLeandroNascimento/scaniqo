package com.joseleandro.scaniqo.ui.documentEditor

import com.joseleandro.scaniqo.domain.geometry.PdfPageGeometry
import com.joseleandro.scaniqo.domain.model.PdfPageLayout
import com.joseleandro.scaniqo.ui.documentEditor.preview.DocumentLivePageGeometry
import org.junit.Assert.assertEquals
import org.junit.Test

class DocumentCroppedPreviewGeometryTest {
    @Test fun croppedPreviewUsesTheSameUniformA4PlacementAsExport() {
        // Visible source after cropping an 800 x 1200 page to (0.1, 0.35, 0.9, 0.65).
        val sourceWidth = 640f
        val sourceHeight = 360f
        val pixelWidth = 2400
        val pixelHeight = 1350
        val placement = PdfPageGeometry.placement(
            0f, 0f, sourceWidth, sourceHeight, 0, PdfPageLayout(),
        )
        val live = DocumentLivePageGeometry.bitmapTransform(
            pixelWidth, pixelHeight, sourceWidth, sourceHeight, PdfPageLayout(),
        )
        assertEquals(placement.a, placement.d, 0f)
        assertEquals(placement.a * sourceWidth, live.scaleX * pixelWidth, .001f)
        assertEquals(placement.d * sourceHeight, live.scaleY * pixelHeight, .001f)
    }
}
