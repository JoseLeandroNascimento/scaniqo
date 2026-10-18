package com.joseleandro.scaniqo.ui.documentEditor

import com.joseleandro.scaniqo.data.pdf.PdfPageGeometry
import com.joseleandro.scaniqo.domain.model.PageAlignment
import com.joseleandro.scaniqo.domain.model.PageScaleMode
import com.joseleandro.scaniqo.domain.model.PdfPageLayout
import com.joseleandro.scaniqo.domain.model.PdfSignaturePlacement
import com.joseleandro.scaniqo.ui.documentEditor.model.DocumentLiveRect
import com.joseleandro.scaniqo.ui.documentEditor.model.DocumentLiveTransform
import com.joseleandro.scaniqo.ui.documentEditor.preview.DocumentLivePageGeometry
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class DocumentLivePageGeometryTest {
    @Test fun canvasCornersMatchExportForEveryAlignmentRotationAndFitMode() {
        val sources = listOf(595f to 842f, 1200f to 400f, 200f to 1000f)
        val bitmapWidth = 713
        val bitmapHeight = 997
        sources.forEach { (sourceWidth, sourceHeight) ->
            PageAlignment.entries.forEach { alignment ->
                PageScaleMode.entries.forEach { scaleMode ->
                    repeat(4) { turns ->
                        listOf(.25f, 1f, 2f).forEach { scale ->
                            val layout = PdfPageLayout(alignment, scaleMode, turns, scale)
                            val canvas = DocumentLivePageGeometry.bitmapTransform(
                                bitmapWidth, bitmapHeight, sourceWidth, sourceHeight, layout,
                            )
                            val pdf = PdfPageGeometry.placement(0f, 0f, sourceWidth, sourceHeight, 0, layout)
                            listOf(0f to 0f, 1f to 0f, 0f to 1f, 1f to 1f, .5f to .5f).forEach { (u, v) ->
                                val sourceX = u * sourceWidth
                                val sourceY = (1f - v) * sourceHeight
                                assertEquals(
                                    "X: $layout, fonte $sourceWidth x $sourceHeight",
                                    pdf.x(sourceX, sourceY),
                                    canvas.x(u * bitmapWidth, v * bitmapHeight),
                                    .002f,
                                )
                                assertEquals(
                                    "Y: $layout, fonte $sourceWidth x $sourceHeight",
                                    PdfPageGeometry.HEIGHT - pdf.y(sourceX, sourceY),
                                    canvas.y(u * bitmapWidth, v * bitmapHeight),
                                    .002f,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    @Test fun clockwiseRotationMovesBitmapTopLeftToPaperTopRightWithoutMirroring() {
        val transform = DocumentLivePageGeometry.bitmapTransform(
            400, 200, 200f, 100f, PdfPageLayout(rotationQuarterTurns = 1),
        )
        assertEquals(492f, transform.x(0f, 0f), .001f)
        assertEquals(32f, transform.y(0f, 0f), .001f)
        assertEquals(492f, transform.x(400f, 0f), .001f)
        assertEquals(810f, transform.y(400f, 0f), .001f)
        assertEquals(103f, transform.x(0f, 200f), .001f)
        assertEquals(32f, transform.y(0f, 200f), .001f)
    }

    @Test fun matrixValuesUseAndroidRowOrderAndRemainAffine() {
        val transform = DocumentLiveTransform(1f, 2f, 3f, 4f, 5f, 6f)
        assertArrayEquals(floatArrayOf(1f, 2f, 3f, 4f, 5f, 6f, 0f, 0f, 1f), transform.matrixValues(), 0f)
        assertEquals(8f, transform.x(1f, 2f), .001f)
        assertEquals(20f, transform.y(1f, 2f), .001f)
    }

    @Test fun fitKeepsWhiteMarginsAndFillClipsOnlyAtPaperEdges() {
        assertEquals(
            DocumentLiveRect(32f, 32f, 563f, 810f),
            DocumentLivePageGeometry.contentClip(PdfPageLayout(contentScale = 2f)),
        )
        assertEquals(
            DocumentLiveRect(0f, 0f, 595f, 842f),
            DocumentLivePageGeometry.contentClip(PdfPageLayout(scaleMode = PageScaleMode.FILL, contentScale = .25f)),
        )
    }

    @Test fun signatureUsesFixedA4CoordinatesAndFitsItsBoxWithoutStretching() {
        val signature = PdfSignaturePlacement("signature", "preview", .2f, .6f, .4f, .1f)
        val rect = DocumentLivePageGeometry.signatureRect(signature, imageWidth = 400, imageHeight = 100)
        assertEquals(119f, rect.left, .001f)
        assertEquals(517.55f, rect.top, .001f)
        assertEquals(357f, rect.right, .001f)
        assertEquals(577.05f, rect.bottom, .001f)
        assertEquals(4f, (rect.right - rect.left) / (rect.bottom - rect.top), .001f)

        // O recorte da captura não limita a assinatura, que pode ocupar as margens.
        val marginSignature = signature.copy(x = 0f, y = 0f, width = .1f, height = .2f)
        val marginRect = DocumentLivePageGeometry.signatureRect(marginSignature, imageWidth = 50, imageHeight = 200)
        assertEquals(8.7f, marginRect.left, .001f)
        assertEquals(0f, marginRect.top, .001f)
    }

    @Test fun pageNumberKeepsExportBaselineAndWhiteBackground() {
        val number = DocumentLivePageGeometry.pageNumber(27.8f)
        assertEquals(283.6f, number.left, .001f)
        assertEquals(827f, number.baseline, .001f)
        assertEquals(279.6f, number.background.left, .001f)
        assertEquals(814f, number.background.top, .001f)
        assertEquals(315.4f, number.background.right, .001f)
        assertEquals(830f, number.background.bottom, .001f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun zeroSizedBitmapIsRejectedBeforeDrawing() {
        DocumentLivePageGeometry.bitmapTransform(0, 100, 595f, 842f, PdfPageLayout())
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidSignatureCannotEscapeThePage() {
        DocumentLivePageGeometry.signatureRect(
            PdfSignaturePlacement("signature", "preview", x = .9f, width = .3f), 100, 100,
        )
    }
}
