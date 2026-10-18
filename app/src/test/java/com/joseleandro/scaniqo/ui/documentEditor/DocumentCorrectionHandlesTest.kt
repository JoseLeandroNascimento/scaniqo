package com.joseleandro.scaniqo.ui.documentEditor

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import com.joseleandro.scaniqo.ui.documentEditor.util.documentCorrectionHandleValue
import org.junit.Assert.assertEquals
import org.junit.Test

class DocumentCorrectionHandlesTest {
    private val page = IntSize(300, 600)

    @Test fun straightenUsesHorizontalMovementAndIgnoresVerticalMovement() {
        assertEquals(3f, documentCorrectionHandleValue(0f, Offset(30f, 400f), page, false), .001f)
        assertEquals(-3f, documentCorrectionHandleValue(0f, Offset(-30f, 0f), page, false), .001f)
        assertEquals(4f, documentCorrectionHandleValue(4f, Offset(0f, 400f), page, false), .001f)
    }

    @Test fun curvatureUsesVerticalMovementMatchingImageCorrection() {
        assertEquals(-.2f, documentCorrectionHandleValue(0f, Offset(400f, 60f), page, true), .001f)
        assertEquals(.2f, documentCorrectionHandleValue(0f, Offset(0f, -60f), page, true), .001f)
        assertEquals(.4f, documentCorrectionHandleValue(.4f, Offset(400f, 0f), page, true), .001f)
    }

    @Test fun handlesClampToSupportedCorrectionRanges() {
        assertEquals(15f, documentCorrectionHandleValue(14f, Offset(300f, 0f), page, false), .001f)
        assertEquals(-15f, documentCorrectionHandleValue(-14f, Offset(-300f, 0f), page, false), .001f)
        assertEquals(1f, documentCorrectionHandleValue(.9f, Offset(0f, -600f), page, true), .001f)
        assertEquals(-1f, documentCorrectionHandleValue(-.9f, Offset(0f, 600f), page, true), .001f)
    }

    @Test fun missingMeasurementsAndNonFiniteInputDoNotCorruptDraft() {
        assertEquals(3f, documentCorrectionHandleValue(3f, Offset(30f, 0f), IntSize.Zero, false), .001f)
        assertEquals(.2f, documentCorrectionHandleValue(.2f, Offset(0f, Float.NaN), page, true), .001f)
        assertEquals(0f, documentCorrectionHandleValue(Float.NaN, Offset.Zero, page, false), .001f)
    }
}
