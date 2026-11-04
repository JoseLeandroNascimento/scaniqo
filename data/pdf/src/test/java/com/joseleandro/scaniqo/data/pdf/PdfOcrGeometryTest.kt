package com.joseleandro.scaniqo.data.pdf

import com.joseleandro.scaniqo.domain.model.PdfPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PdfOcrGeometryTest {
    @Test
    fun `texto horizontal acompanha pixels na folha e inverte eixo vertical`() {
        val result = requireNotNull(PdfOcrGeometry.place(
            PdfPoint(100f, 220f), PdfPoint(300f, 220f), PdfPoint(100f, 200f),
            1000, 1400, 500f, 700f, 5f,
        ))
        assertEquals(10f, result.fontSize, .001f)
        assertEquals(2f, result.a, .001f)
        assertEquals(0f, result.b, .001f)
        assertEquals(50f, result.x, .001f)
        assertEquals(591.6f, result.y, .001f)
        assertEquals(100f, result.fontSize * 5 * result.a, .001f)
    }

    @Test
    fun `palavra girada mantem direcao vertical na selecao`() {
        val result = requireNotNull(PdfOcrGeometry.place(
            PdfPoint(100f, 100f), PdfPoint(100f, 300f), PdfPoint(120f, 100f),
            1000, 1400, 500f, 700f, 5f,
        ))
        assertEquals(0f, result.a, .001f)
        assertEquals(-2f, result.b, .001f)
        assertEquals(1f, result.c, .001f)
        assertEquals(0f, result.d, .001f)
        assertEquals(51.6f, result.x, .001f)
        assertEquals(650f, result.y, .001f)
    }

    @Test
    fun `caixas degeneradas nao produzem matriz invalida`() {
        assertNull(PdfOcrGeometry.place(
            PdfPoint(0f, 0f), PdfPoint(0f, 0f), PdfPoint(0f, 0f),
            100, 100, 50f, 50f, 5f,
        ))
    }
}
