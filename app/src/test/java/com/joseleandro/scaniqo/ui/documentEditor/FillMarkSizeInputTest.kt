package com.joseleandro.scaniqo.ui.documentEditor

import com.joseleandro.scaniqo.ui.documentEditor.components.acceptsFillMarkSizeInput
import com.joseleandro.scaniqo.ui.documentEditor.components.formatFillMarkSize
import com.joseleandro.scaniqo.ui.documentEditor.components.parseFillMarkSize
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FillMarkSizeInputTest {
    @Test fun acceptsLocalizedDecimalWithoutRoundingLegacyValue() {
        assertEquals("16,5", formatFillMarkSize(16.5f, Locale.forLanguageTag("pt-BR")))
        assertEquals("16.25", formatFillMarkSize(16.25f, Locale.ENGLISH))
        assertEquals(16.5f, parseFillMarkSize("16,5")!!, 0f)
        assertEquals(16.25f, parseFillMarkSize("16.25")!!, 0f)
    }

    @Test fun rejectsOutOfRangeOrMalformedValueInsteadOfClamping() {
        assertNull(parseFillMarkSize(""))
        assertNull(parseFillMarkSize("5,9"))
        assertNull(parseFillMarkSize("72,1"))
        assertNull(parseFillMarkSize("10,2,3"))
        assertNull(parseFillMarkSize("NaN"))
        assertTrue(acceptsFillMarkSizeInput("12,5"))
        assertFalse(acceptsFillMarkSizeInput("12,5,7"))
        assertFalse(acceptsFillMarkSizeInput("abc"))
    }
}
