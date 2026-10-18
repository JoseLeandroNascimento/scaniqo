package com.joseleandro.scaniqo.data.pdf

import com.joseleandro.scaniqo.domain.model.PdfSignaturePlacement
import org.junit.Assert.assertEquals
import org.junit.Test

class PdfSignatureGeometryTest {
    private val signature = PdfSignaturePlacement(
        id = "signature", imageUri = "file:///signature.png",
        x = .2f, y = .3f, width = .4f, height = .2f,
    )

    @Test fun unrotatedExportFitsTheImageWithoutChangingItsAspect() {
        val bounds = PdfSignatureGeometry.bounds(signature, 400, 100)
        val transform = PdfSignatureGeometry.pdfTransform(signature, 400, 100)

        assertEquals(4f, bounds.width / bounds.height, .0001f)
        assertEquals(.4f * PdfPageGeometry.WIDTH, bounds.width, .0001f)
        assertEquals(.4f * PdfPageGeometry.WIDTH, bounds.centerX, .0001f)
        assertEquals(.4f * PdfPageGeometry.HEIGHT, bounds.centerY, .0001f)
        assertEquals(bounds.left, transform.x(0f, 0f), .0001f)
        assertEquals(PdfPageGeometry.HEIGHT - bounds.top - bounds.height, transform.y(0f, 0f), .0001f)
        assertEquals(bounds.left + bounds.width, transform.x(1f, 1f), .0001f)
        assertEquals(PdfPageGeometry.HEIGHT - bounds.top, transform.y(1f, 1f), .0001f)
    }

    @Test fun clockwiseQuarterTurnPreservesCenterAndRotatesImageCorners() {
        val rotated = signature.copy(rotationDegrees = 90f)
        val bounds = PdfSignatureGeometry.bounds(rotated, 400, 100)
        val transform = PdfSignatureGeometry.pdfTransform(rotated, 400, 100)
        val centerY = PdfPageGeometry.HEIGHT - bounds.centerY

        assertEquals(bounds.centerX, transform.x(.5f, .5f), .0001f)
        assertEquals(centerY, transform.y(.5f, .5f), .0001f)
        // O canto superior esquerdo da imagem passa ao superior direito da folha.
        assertEquals(bounds.centerX + bounds.height / 2f, transform.x(0f, 1f), .0001f)
        assertEquals(centerY + bounds.width / 2f, transform.y(0f, 1f), .0001f)
        assertEquals(bounds.centerX - bounds.height / 2f, transform.x(1f, 0f), .0001f)
        assertEquals(centerY - bounds.width / 2f, transform.y(1f, 0f), .0001f)
    }


    @Test fun stretchedSignatureUsesBothBoxDimensionsRegardlessOfBitmapAspect() {
        val stretched = signature.copy(stretchToBounds = true)
        for ((imageWidth, imageHeight) in listOf(400 to 100, 100 to 400, 32 to 32)) {
            val bounds = PdfSignatureGeometry.bounds(stretched, imageWidth, imageHeight)
            assertEquals(stretched.x * PdfPageGeometry.WIDTH, bounds.left, .0001f)
            assertEquals(stretched.y * PdfPageGeometry.HEIGHT, bounds.top, .0001f)
            assertEquals(stretched.width * PdfPageGeometry.WIDTH, bounds.width, .0001f)
            assertEquals(stretched.height * PdfPageGeometry.HEIGHT, bounds.height, .0001f)
        }
    }

    @Test fun stretchedExportRotatesTheFullIndependentRectangleAroundItsCenter() {
        for (rotation in listOf(-135f, -90f, 0f, 45f, 90f, 180f)) {
            val stretched = signature.copy(rotationDegrees = rotation, stretchToBounds = true)
            val transform = PdfSignatureGeometry.pdfTransform(stretched, 400, 100)
            val width = stretched.width * PdfPageGeometry.WIDTH
            val height = stretched.height * PdfPageGeometry.HEIGHT
            val radians = -Math.toRadians(rotation.toDouble())
            assertEquals(width * kotlin.math.cos(radians).toFloat(), transform.x(1f, 0f) - transform.x(0f, 0f), .0001f)
            assertEquals(width * kotlin.math.sin(radians).toFloat(), transform.y(1f, 0f) - transform.y(0f, 0f), .0001f)
            assertEquals(-height * kotlin.math.sin(radians).toFloat(), transform.x(0f, 1f) - transform.x(0f, 0f), .0001f)
            assertEquals(height * kotlin.math.cos(radians).toFloat(), transform.y(0f, 1f) - transform.y(0f, 0f), .0001f)
            assertEquals((stretched.x + stretched.width / 2f) * PdfPageGeometry.WIDTH, transform.x(.5f, .5f), .0001f)
            assertEquals((1f - stretched.y - stretched.height / 2f) * PdfPageGeometry.HEIGHT, transform.y(.5f, .5f), .0001f)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidAngleCannotReachPdfRendering() {
        PdfSignatureGeometry.pdfTransform(signature.copy(rotationDegrees = Float.NaN), 400, 100)
    }
}
