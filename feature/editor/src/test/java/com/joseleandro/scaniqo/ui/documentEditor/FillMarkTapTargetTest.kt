package com.joseleandro.scaniqo.ui.documentEditor

import com.joseleandro.scaniqo.domain.model.PdfOverlay
import com.joseleandro.scaniqo.domain.model.PdfOverlayKind
import com.joseleandro.scaniqo.domain.model.PdfPoint
import com.joseleandro.scaniqo.ui.documentEditor.components.findFillMarkTapTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FillMarkTapTargetTest {
    private val pageWidth = 300f
    private val pageHeight = 600f

    private fun overlay(
        id: String,
        kind: PdfOverlayKind,
        left: Float,
        top: Float,
        width: Float = 10f,
        height: Float = 10f,
    ) = PdfOverlay(
        id = id,
        kind = kind,
        x = left / pageWidth,
        y = top / pageHeight,
        width = width / pageWidth,
        height = height / pageHeight,
    )

    private fun hit(
        x: Float,
        y: Float,
        overlays: List<PdfOverlay>,
        selected: PdfOverlay? = null,
        targetSize: Float = 48f,
    ) = findFillMarkTapTarget(
        selected, overlays, PdfPoint(x / pageWidth, y / pageHeight),
        pageWidth, pageHeight, targetSize,
    )

    @Test fun tinyCheckHasAtLeast48PixelTargetEvenAtPageEdge() {
        val check = overlay("check", PdfOverlayKind.CHECKMARK, left = 0f, top = 0f, width = 8f, height = 8f)

        assertEquals(check, hit(47f, 4f, listOf(check)).overlay)
        assertNull(hit(49f, 4f, listOf(check)).overlay)
        assertEquals(check, hit(4f, 47f, listOf(check)).overlay)
        assertNull(hit(4f, 49f, listOf(check)).overlay)
    }

    @Test fun tinyTextExpandsOnlyItsShortDimension() {
        val text = overlay("text", PdfOverlayKind.TEXT, left = 80f, top = 200f, width = 100f, height = 8f)

        assertEquals(text, hit(120f, 223f, listOf(text)).overlay)
        assertNull(hit(190f, 223f, listOf(text)).overlay)
    }

    @Test fun exactRenderedHitWinsOverExpandedTargetOfTopmostMark() {
        val text = overlay("text", PdfOverlayKind.TEXT, left = 50f, top = 100f)
        val cross = overlay("cross", PdfOverlayKind.CROSSMARK, left = 75f, top = 100f)

        assertEquals(text, hit(57f, 105f, listOf(text, cross)).overlay)
    }

    @Test fun nearestVisibleMarkWinsWhenExpandedTargetsOverlap() {
        val check = overlay("check", PdfOverlayKind.CHECKMARK, left = 90f, top = 100f)
        val cross = overlay("cross", PdfOverlayKind.CROSSMARK, left = 118f, top = 100f)

        assertEquals(check, hit(105f, 105f, listOf(check, cross)).overlay)
        assertEquals(cross, hit(113f, 105f, listOf(check, cross)).overlay)
    }

    @Test fun selectedElementWinsExactTieAndTopmostWinsExpandedTie() {
        val selected = overlay("selected", PdfOverlayKind.TEXT, left = 90f, top = 100f)
        val other = overlay("other", PdfOverlayKind.CHECKMARK, left = 90f, top = 100f)

        assertEquals(selected, hit(95f, 105f, listOf(other), selected).overlay)
        assertEquals(other, hit(108f, 105f, listOf(selected, other)).overlay)
    }

    @Test fun nonFillOverlayBlocksOnlyAnExactTap() {
        val highlight = overlay("highlight", PdfOverlayKind.HIGHLIGHT, left = 90f, top = 100f)
        val near = hit(75f, 105f, listOf(highlight))
        val exact = hit(95f, 105f, listOf(highlight))

        assertNull(near.overlay)
        assertFalse(near.blocksInsertion)
        assertNull(exact.overlay)
        assertTrue(exact.blocksInsertion)
    }

    @Test fun exactNonFillOverlayBlocksNearbyExpandedMark() {
        val check = overlay("check", PdfOverlayKind.CHECKMARK, left = 118f, top = 100f)
        val highlight = overlay("highlight", PdfOverlayKind.HIGHLIGHT, left = 90f, top = 100f, width = 20f)

        val target = hit(105f, 105f, listOf(check, highlight))

        assertNull(target.overlay)
        assertTrue(target.blocksInsertion)
    }

    @Test fun hitTargetKeepsSamePhysicalSizeAtDifferentZoom() {
        val check = overlay("check", PdfOverlayKind.CHECKMARK, left = 90f, top = 100f)
        val point = PdfPoint(113f / pageWidth, 105f / pageHeight)
        val normal = findFillMarkTapTarget(null, listOf(check), point, pageWidth, pageHeight, 48f)
        val zoomed = findFillMarkTapTarget(null, listOf(check), point, pageWidth * 2f, pageHeight * 2f, 48f)

        assertEquals(check, normal.overlay)
        assertNull(zoomed.overlay)
    }
}
