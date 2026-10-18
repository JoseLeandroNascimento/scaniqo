package com.joseleandro.scaniqo.data.pdf

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.common.PDStream
import java.io.ByteArrayInputStream
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfNativeContentTest {
    @Test
    fun `fundo branco inteiro nao impede filtro de captura`() {
        assertFalse(hasVectors("1 1 1 rg 0 0 600 800 re f"))
    }

    @Test
    fun `retangulo colorido deve continuar vetorial`() {
        assertTrue(hasVectors("0.2 0.4 0.8 rg 0 0 600 800 re f"))
    }

    @Test
    fun `recorte nao e confundido com desenho vetorial`() {
        assertFalse(hasVectors("0 0 400 500 re W n"))
    }

    @Test
    fun `traco sobre fundo branco permanece vetorial`() {
        assertTrue(hasVectors("1 g 0 0 600 800 re f 0 G 10 20 m 100 200 l S"))
    }

    @Test
    fun `estado de cor restaurado nao esconde um vetor`() {
        assertTrue(hasVectors("0 g q 1 g 0 0 600 800 re f Q 0 0 600 800 re f"))
    }

    @Test
    fun skiaDeviceRgbAndPolygonPageBackgroundRemainProcessable() {
        assertFalse(hasVectors("1 0 0 -1 0 800 cm /DeviceRGB cs 1 1 1 scn 0 0 m 600 0 l 600 800 l 0 800 l h f"))
        assertFalse(hasVectors("/DeviceRGB cs 1 1 1 sc 0 0 600 800 re f"))
        assertFalse(hasVectors("/DeviceCMYK cs 0 0 0 0 scn 0 0 600 800 re f"))
    }

    @Test
    fun rectangularPolygonClipDoesNotBecomeNativeArtwork() {
        assertFalse(hasVectors("0 0 m 600 0 l 600 800 l 0 800 l h W* n"))
    }

    @Test
    fun smallWhitePolygonColoredFillAndLateWhiteOverlayRemainNative() {
        assertTrue(hasVectors("1 g 0 0 m 100 0 l 100 100 l 0 100 l h f"))
        assertTrue(hasVectors("/DeviceRGB cs .8 .8 .8 scn 0 0 600 800 re f"))
        assertTrue(hasVectors("/Im0 Do 1 g 0 0 600 800 re f"))
        assertTrue(hasVectors("1 g q /DeviceRGB cs 0 0 0 scn Q 0 0 600 800 re f 0 g 10 10 20 20 re f"))
    }

    private fun hasVectors(content: String): Boolean = PDDocument().use { document ->
        val page = PDPage(PDRectangle(600f, 800f))
        document.addPage(page)
        page.setContents(PDStream(document, ByteArrayInputStream(content.toByteArray())))
        PdfNativeContent.containsVectors(page)
    }
}
