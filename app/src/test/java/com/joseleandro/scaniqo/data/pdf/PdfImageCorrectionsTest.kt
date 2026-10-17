package com.joseleandro.scaniqo.data.pdf

import com.joseleandro.scaniqo.domain.model.PdfPageLayout
import com.joseleandro.scaniqo.domain.model.PdfPerspective
import com.joseleandro.scaniqo.domain.model.PdfPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.tan

class PdfImageCorrectionsTest {
    @Test
    fun `sem correcao reutiliza a imagem sem nova interpolacao`() = runBlocking {
        val source = IntArray(81) { it or -0x1000000 }
        assertSame(source, PdfImageCorrections.apply(source, 9, 9, PdfPageLayout()))
    }

    @Test
    fun `perspectiva identidade nao muda a captura`() = runBlocking {
        val source = IntArray(81) { it or -0x1000000 }
        val corrected = PdfImageCorrections.apply(source, 9, 9,
            PdfPageLayout(perspective = PdfPerspective()))
        assertArrayEquals(source, corrected)
    }

    @Test
    fun `quatro cantos retificam recorte e preservam seu centro`() = runBlocking {
        val source = IntArray(81) { it or -0x1000000 }
        val original = source.copyOf()
        val perspective = PdfPerspective(
            PdfPoint(.25f, .25f), PdfPoint(.75f, .25f),
            PdfPoint(.75f, .75f), PdfPoint(.25f, .75f)
        )
        val result = PdfImageCorrections.apply(source, 9, 9, PdfPageLayout(perspective = perspective))
        assertEquals(source[2 * 9 + 2], result[0])
        assertEquals(source[2 * 9 + 6], result[8])
        assertEquals(source[6 * 9 + 2], result[8 * 9])
        assertEquals(source[6 * 9 + 6], result[80])
        assertEquals(source[4 * 9 + 4], result[40])
        assertArrayEquals(original, source)
    }

    @Test
    fun `trapezio usa homografia para mapear todos os quatro cantos`() = runBlocking {
        val source = IntArray(81) { it or -0x1000000 }
        val perspective = PdfPerspective(
            PdfPoint(.25f, 0f), PdfPoint(.75f, 0f),
            PdfPoint(1f, 1f), PdfPoint(0f, 1f)
        )
        val result = PdfImageCorrections.apply(source, 9, 9, PdfPageLayout(perspective = perspective))
        assertEquals(source[2], result[0])
        assertEquals(source[6], result[8])
        assertEquals(source[72], result[72])
        assertEquals(source[80], result[80])
    }

    @Test
    fun `cantos cruzados degenerados e fora da imagem sao recusados`() {
        assertFalse(PdfImageCorrections.isValidPerspective(PdfPerspective(
            PdfPoint(0f, 0f), PdfPoint(1f, 1f), PdfPoint(1f, 0f), PdfPoint(0f, 1f)
        )))
        assertFalse(PdfImageCorrections.isValidPerspective(PdfPerspective(
            PdfPoint(0f, 0f), PdfPoint(0f, 0f), PdfPoint(0f, 0f), PdfPoint(0f, 0f)
        )))
        assertFalse(PdfImageCorrections.isValidPerspective(PdfPerspective(
            topLeft = PdfPoint(-.1f, 0f)
        )))
        assertTrue(PdfImageCorrections.isValidPerspective(PdfPerspective()))
    }

    @Test
    fun `perspectiva invalida preserva a imagem original`() = runBlocking {
        val source = IntArray(81) { -1 }
        val invalid = PdfPerspective(topLeft = PdfPoint(Float.NaN, 0f))
        assertSame(source, PdfImageCorrections.apply(source, 9, 9, PdfPageLayout(perspective = invalid)))
    }

    @Test
    fun `rotacao preenche cantos descobertos com papel branco`() = runBlocking {
        val source = IntArray(101 * 101) { -0x1000000 }
        val result = PdfImageCorrections.apply(source, 101, 101, PdfPageLayout(straightenDegrees = 12f))
        assertEquals(-1, result[0])
        assertEquals(-0x1000000, result[50 * 101 + 50])
        assertEquals(-0x1000000, source[0])
    }

    @Test
    fun `rotacao depois do recorte nao revela margem fora dos cantos`() = runBlocking {
        val source = IntArray(101 * 101) { -0x1000000 }
        val perspective = PdfPerspective(
            PdfPoint(.2f, .2f), PdfPoint(.8f, .2f),
            PdfPoint(.8f, .8f), PdfPoint(.2f, .8f)
        )
        val result = PdfImageCorrections.apply(source, 101, 101,
            PdfPageLayout(perspective = perspective, straightenDegrees = 12f))
        assertEquals(-1, result[0])
        assertEquals(-0x1000000, result[50 * 101 + 50])
    }

    @Test
    fun `curvatura manual retifica uma linha parabolica`() = runBlocking {
        val width = 101
        val height = 101
        val source = IntArray(width * height) { -1 }
        for (x in 0 until width) {
            val u = x / 100f
            val y = (40 + .12f * (1 - (2 * u - 1) * (2 * u - 1)) * 100).roundToInt()
            for (dy in -1..1) source[(y + dy) * width + x] = -0x1000000
        }
        val result = PdfImageCorrections.apply(source, width, height, PdfPageLayout(curvature = .12f / .35f))
        assertTrue((0 until width).all { (result[40 * width + it] and 255) < 20 })
    }

    @Test
    fun `estimativa encontra angulo corretivo de linhas inclinadas`() = runBlocking {
        val width = 300
        val height = 200
        val source = IntArray(width * height) { -1 }
        val slope = tan(Math.toRadians(6.0))
        for (line in 25..155 step 26) for (x in 25..275) {
            val y = (line + slope * x).roundToInt()
            source[y * width + x] = -0x1000000
            source[(y + 1) * width + x] = -0x1000000
        }
        val correction = PdfImageCorrections.estimateStraightenDegrees(source, width, height)
        assertTrue("Ângulo encontrado: $correction", abs(correction + 6f) <= 1f)
    }

    @Test
    fun `estimativa evita inventar um angulo em pagina sem texto`() = runBlocking {
        assertEquals(0f, PdfImageCorrections.estimateStraightenDegrees(IntArray(6400) { -1 }, 80, 80))
    }

    @Test
    fun `geometria respeita cancelamento sem alterar fonte`() = runBlocking {
        val source = IntArray(81) { it }
        val original = source.copyOf()
        var cancelled = false
        val task = launch {
            currentCoroutineContext().cancel()
            try {
                PdfImageCorrections.apply(source, 9, 9, PdfPageLayout(straightenDegrees = 3f))
            } catch (_: CancellationException) {
                cancelled = true
            }
        }
        task.join()
        assertTrue(cancelled)
        assertArrayEquals(original, source)
    }
}
