package com.joseleandro.scaniqo.ui.documentEditor

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.joseleandro.scaniqo.domain.model.PdfSignaturePlacement
import com.joseleandro.scaniqo.ui.documentEditor.util.signatureContainsPointer
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentSignatureHitTest {
    @Test
    fun pannedAndZoomedPageUsesItsActualScreenOrigin() {
        val page = Rect(-150f, -300f, 450f, 600f)
        assertTrue(signatureContainsPointer(signature(), page, Offset(150f, 150f), 48f))
        assertFalse(signatureContainsPointer(signature(), page, Offset(350f, 150f), 48f))
    }

    @Test
    fun aQuarterTurnRotatesTheInteractiveAreaWithTheSignature() {
        val value = signature().copy(rotationDegrees = 90f)
        val page = Rect(0f, 0f, 400f, 600f)
        assertTrue(signatureContainsPointer(value, page, Offset(200f, 335f), 0f))
        assertFalse(signatureContainsPointer(value, page, Offset(235f, 300f), 0f))
    }

    @Test
    fun thinSignaturesRetainAFortyEightPixelMoveTarget() {
        val value = signature().copy(height = .005f)
        val page = Rect(0f, 0f, 400f, 600f)
        val center = Offset((value.x + value.width / 2f) * 400f, (value.y + value.height / 2f) * 600f)
        assertTrue(signatureContainsPointer(value, page, center + Offset(0f, 23f), 48f))
        assertFalse(signatureContainsPointer(value, page, center + Offset(0f, 25f), 48f))
    }

    @Test
    fun aPointOutsideTheRotatedSelectionCanPanTheDocument() {
        val value = signature().copy(rotationDegrees = 45f)
        assertFalse(signatureContainsPointer(value, Rect(0f, 0f, 400f, 600f), Offset(10f, 20f), 48f))
    }

    private fun signature() = PdfSignaturePlacement(
        id = "signature", imageUri = "content://signature", x = .4f, y = .45f, width = .2f, height = .1f,
    )
}
