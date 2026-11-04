package com.joseleandro.scaniqo.data.pdf

import com.joseleandro.scaniqo.domain.model.PdfExportQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfExportSettingsTest {
    @Test
    fun `perfis limitam imagem e preservam sua proporcao`() {
        val small = PdfExportSettings.forQuality(PdfExportQuality.SMALL)
        assertEquals(750 to 1500, small.dimensions(3000, 6000))
        assertEquals(1500 to 750, small.dimensions(6000, 3000))
    }

    @Test
    fun `compressao nunca amplia imagem pequena`() {
        PdfExportQuality.entries.forEach { quality ->
            assertEquals(200 to 100, PdfExportSettings.forQuality(quality).dimensions(200, 100))
        }
    }

    @Test
    fun `perfis reduzem qualidade progressivamente`() {
        val high = PdfExportSettings.forQuality(PdfExportQuality.HIGH_QUALITY)
        val balanced = PdfExportSettings.forQuality(PdfExportQuality.BALANCED)
        val small = PdfExportSettings.forQuality(PdfExportQuality.SMALL)
        assertTrue(high.maxImageSide > balanced.maxImageSide)
        assertTrue(balanced.maxImageSide > small.maxImageSide)
        assertTrue(high.jpegQuality > balanced.jpegQuality)
        assertTrue(balanced.jpegQuality > small.jpegQuality)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `dimensoes invalidas sao rejeitadas`() {
        PdfExportSettings.forQuality(PdfExportQuality.SMALL).dimensions(0, 10)
    }
}
