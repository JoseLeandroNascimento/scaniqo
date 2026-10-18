package com.joseleandro.scaniqo.ui.documentEditor

import com.joseleandro.scaniqo.domain.model.PageAlignment
import com.joseleandro.scaniqo.domain.model.PageScaleMode
import com.joseleandro.scaniqo.domain.model.PdfCropBounds
import com.joseleandro.scaniqo.domain.model.PdfImageFilter
import com.joseleandro.scaniqo.domain.model.PdfPageLayout
import com.joseleandro.scaniqo.domain.model.PdfPageSpec
import com.joseleandro.scaniqo.domain.model.PdfPerspective
import com.joseleandro.scaniqo.domain.model.PdfPoint
import com.joseleandro.scaniqo.domain.model.PdfSignaturePlacement
import com.joseleandro.scaniqo.domain.model.PdfWorkspace
import com.joseleandro.scaniqo.ui.documentEditor.util.withoutImageEnhancements
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class DocumentQualityEditsTest {
    @Test
    fun comparisonRestoresTheCaptureWithoutChangingPageLayoutSignaturesOrOtherPages() {
        val correctedLayout = PdfPageLayout(
            alignment = PageAlignment.LEFT,
            scaleMode = PageScaleMode.FILL,
            rotationQuarterTurns = 1,
            contentScale = .8f,
            imageFilter = PdfImageFilter.ENHANCE,
            filterIntensity = .6f,
            straightenDegrees = 3f,
            perspective = PdfPerspective(),
            curvature = .2f,
            rasterizeText = true,
            crop = PdfCropBounds(.2f, .3f, .7f, .8f),
            backgroundMask = PdfPerspective(
                PdfPoint(.25f, .35f), PdfPoint(.65f, .32f),
                PdfPoint(.68f, .75f), PdfPoint(.22f, .78f),
            ),
        )
        val selected = PdfPageSpec(
            "selected", "file:///source.pdf", 2, correctedLayout,
            signatures = listOf(PdfSignaturePlacement("signature", "file:///signature.png", rotationDegrees = 20f)),
        )
        val other = PdfPageSpec("other", "file:///source.pdf", 4, correctedLayout)
        val workspace = PdfWorkspace(
            7, "session", "file:///session", listOf(selected, other),
            showPageNumbers = true, isProtected = true, protectionKeyId = "key", ocrEnabled = true,
        )

        val comparison = workspace.withoutImageEnhancements(selected.id)

        val expectedLayout = correctedLayout.copy(
            imageFilter = PdfImageFilter.ORIGINAL,
            filterIntensity = 1f,
            straightenDegrees = 0f,
            perspective = null,
            curvature = 0f,
            crop = null,
            backgroundMask = null,
        )
        assertEquals(workspace.copy(pages = listOf(selected.copy(layout = expectedLayout), other)), comparison)
        assertNull(comparison.pages.first().layout.crop)
        assertNull(comparison.pages.first().layout.backgroundMask)
        assertSame(other, comparison.pages[1])
        assertEquals(selected.signatures, comparison.pages.first().signatures)
        assertNotNull(workspace.pages.first().layout.crop)
        assertNotNull(workspace.pages.first().layout.backgroundMask)
    }
}
