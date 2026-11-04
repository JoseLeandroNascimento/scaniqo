package com.joseleandro.scaniqo.ui.documentEditor

import com.joseleandro.scaniqo.domain.geometry.PdfPageGeometry
import com.joseleandro.scaniqo.domain.model.PdfContentPosition
import com.joseleandro.scaniqo.domain.model.PdfSignaturePlacement
import com.joseleandro.scaniqo.ui.documentEditor.util.signatureSelectionGeometry
import com.joseleandro.scaniqo.ui.documentEditor.util.transformSignaturePlacement
import com.joseleandro.scaniqo.ui.documentSignature.signaturePositionMapping
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SignaturePositionMappingTest {
    private val signature = PdfSignaturePlacement(
        id = "signature", imageUri = "file:///signature.png",
        x = .2f, y = .5f, width = .4f, height = .1f,
    )

    @Test fun draggingAcrossThePadKeepsEveryRotatedCornerInsideTheExportedPage() {
        for (rotation in listOf(-179f, -135f, -90f, -15f, 0f, 37f, 90f, 135f)) {
            for (height in listOf(.04f, .2f, .6f)) {
                val source = transformSignaturePlacement(signature.copy(height = height, rotationDegrees = rotation))
                val mapping = signaturePositionMapping(source)
                for (x in listOf(0f, .5f, 1f)) for (y in listOf(0f, .5f, 1f)) {
                    val result = mapping.place(source, PdfContentPosition(x, y))
                    val geometry = signatureSelectionGeometry(
                        result, 0f, 0f, PdfPageGeometry.WIDTH, PdfPageGeometry.HEIGHT,
                        PdfPageGeometry.WIDTH, PdfPageGeometry.HEIGHT, 48f, 4f, 48f,
                    )
                    geometry.corners.forEach { corner ->
                        assertTrue("x=${corner.x} at rotation $rotation", corner.x >= -.001f && corner.x <= PdfPageGeometry.WIDTH + .001f)
                        assertTrue("y=${corner.y} at rotation $rotation", corner.y >= -.001f && corner.y <= PdfPageGeometry.HEIGHT + .001f)
                    }
                    assertEquals(source.width, result.width, .00001f)
                    assertEquals(source.height, result.height, .00001f)
                    assertEquals(source.rotationDegrees, result.rotationDegrees, .00001f)
                    assertEquals(source.id, result.id)
                    assertEquals(source.imageUri, result.imageUri)
                }
            }
        }
    }

    @Test fun openingTheMoveTabDoesNotChangeTheSignaturePosition() {
        for (rotation in listOf(-135f, -90f, -27f, 0f, 51f, 90f)) {
            val source = transformSignaturePlacement(signature.copy(rotationDegrees = rotation))
            val mapping = signaturePositionMapping(source)
            val result = mapping.place(source, mapping.position(source))
            assertEquals(source.x, result.x, .00001f)
            assertEquals(source.y, result.y, .00001f)
        }
    }

    @Test fun centeringUsesThePageCenterWithoutChangingTheSignatureShape() {
        val source = transformSignaturePlacement(signature.copy(rotationDegrees = 53f, stretchToBounds = true))
        val result = signaturePositionMapping(source).place(source, PdfContentPosition())
        assertEquals(.5f, result.x + result.width / 2f, .00001f)
        assertEquals(.5f, result.y + result.height / 2f, .00001f)
        assertEquals(source.width, result.width, .00001f)
        assertEquals(source.height, result.height, .00001f)
        assertTrue(result.stretchToBounds)
    }

    @Test fun padProgressCanReachBothEdgesAtFineRotationAngles() {
        val source = transformSignaturePlacement(signature.copy(rotationDegrees = 37f))
        val mapping = signaturePositionMapping(source)
        for (x in listOf(0f, .25f, .5f, .75f, 1f)) {
            for (y in listOf(0f, .25f, .5f, .75f, 1f)) {
                val result = mapping.position(mapping.place(source, PdfContentPosition(x, y)))
                assertEquals(x, result.horizontal, .00001f)
                assertEquals(y, result.vertical, .00001f)
            }
        }
    }
}
