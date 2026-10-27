package com.joseleandro.scaniqo.data.pdf

import com.joseleandro.scaniqo.domain.model.PageAlignment
import com.joseleandro.scaniqo.domain.model.PageScaleMode
import com.joseleandro.scaniqo.domain.model.PdfPageLayout
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfPageGeometryTest {
    @Test
    fun `alinhamentos verticais respeitam as margens A4`() {
        val top = square(PageAlignment.TOP)
        val bottom = square(PageAlignment.BOTTOM)
        val center = square(PageAlignment.CENTER)

        assertEquals(810f, top.y(500f, 500f), .001f)
        assertEquals(32f, bottom.y(0f, 0f), .001f)
        assertEquals(421f, center.y(250f, 250f), .001f)
        assertEquals(1f, top.a / top.d, .001f)
    }

    @Test
    fun `alinhamentos laterais preservam a proporcao de paginas estreitas`() {
        val left = PdfPageGeometry.placement(
            0f, 0f, 500f, 2000f, 0, PdfPageLayout(PageAlignment.LEFT)
        )
        val right = PdfPageGeometry.placement(
            0f, 0f, 500f, 2000f, 0, PdfPageLayout(PageAlignment.RIGHT)
        )

        assertEquals(32f, left.x(0f, 0f), .001f)
        assertEquals(563f, right.x(500f, 2000f), .001f)
        assertEquals(left.a, left.d, .001f)
        assertEquals(left.a, right.a, .001f)
    }

    @Test
    fun `preencher cobre toda a area util e centraliza o excesso`() {
        val fill = square(PageAlignment.CENTER, PageScaleMode.FILL)
        val width = fill.x(500f, 0f) - fill.x(0f, 0f)
        val height = fill.y(0f, 500f) - fill.y(0f, 0f)

        assertTrue(width >= 595f)
        assertEquals(842f, height, .001f)
        assertEquals(297.5f, fill.x(250f, 250f), .001f)
        assertEquals(421f, fill.y(250f, 250f), .001f)
    }

    @Test
    fun `rotacoes e CropBox deslocado mapeiam corretamente os cantos`() {
        val ninety = PdfPageGeometry.placement(
            10f, 20f, 100f, 200f, 90, PdfPageLayout(), 200f, 100f, 0f
        )
        assertEquals(0f, ninety.x(10f, 20f), .001f)
        assertEquals(100f, ninety.y(10f, 20f), .001f)
        assertEquals(200f, ninety.x(110f, 220f), .001f)
        assertEquals(0f, ninety.y(110f, 220f), .001f)

        val twoSeventy = PdfPageGeometry.placement(
            10f, 20f, 100f, 200f, 270, PdfPageLayout(), 200f, 100f, 0f
        )
        assertEquals(200f, twoSeventy.x(10f, 20f), .001f)
        assertEquals(0f, twoSeventy.y(10f, 20f), .001f)
        assertEquals(0f, twoSeventy.x(110f, 220f), .001f)
        assertEquals(100f, twoSeventy.y(110f, 220f), .001f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `geometria invalida e rejeitada antes de gravar o pdf`() {
        PdfPageGeometry.placement(Float.NaN, 0f, 100f, 100f, 0, PdfPageLayout())
    }

    @Test
    fun `reduzir captura A4 libera espaco real para alinhar a esquerda e a direita`() {
        fun placement(alignment: PageAlignment, scale: Float) = PdfPageGeometry.placement(
            0f, 0f, 595f, 842f, 0,
            PdfPageLayout(alignment = alignment, contentScale = scale)
        )
        val fullLeft = placement(PageAlignment.LEFT, 1f)
        val fullRight = placement(PageAlignment.RIGHT, 1f)
        // Em 100%, FIT preenche este eixo; não existe espaço para deslocamento lateral.
        assertEquals(fullLeft.e, fullRight.e, .001f)

        val left = placement(PageAlignment.LEFT, .75f)
        val right = placement(PageAlignment.RIGHT, .75f)
        assertEquals(32f, left.x(0f, 0f), .001f)
        assertEquals(563f, right.x(595f, 0f), .001f)
        assertEquals(132.75f, right.e - left.e, .001f)
        assertEquals(fullLeft.a * .75f, left.a, .001f)
    }

    @Test
    fun `rotacao manual de noventa graus e horaria e respeita a orientacao original`() {
        val manual = PdfPageGeometry.placement(
            10f, 20f, 100f, 200f, 0,
            PdfPageLayout(rotationQuarterTurns = 1), 200f, 100f, 0f
        )
        val original = PdfPageGeometry.placement(
            10f, 20f, 100f, 200f, 90, PdfPageLayout(), 200f, 100f, 0f
        )
        assertEquals(original, manual)
        assertEquals(0f, manual.x(10f, 20f), .001f)
        assertEquals(100f, manual.y(10f, 20f), .001f)

        val completeTurn = PdfPageGeometry.placement(
            0f, 0f, 100f, 200f, 90, PdfPageLayout(rotationQuarterTurns = 3)
        )
        val unrotated = PdfPageGeometry.placement(0f, 0f, 100f, 200f, 0, PdfPageLayout())
        assertEquals(unrotated, completeTurn)
    }

    @Test
    fun `aumento mantem centro e tamanho proporcional para preview e exportacao`() {
        val normal = square(PageAlignment.CENTER)
        val enlarged = PdfPageGeometry.placement(
            0f, 0f, 500f, 500f, 0,
            PdfPageLayout(contentScale = 2f)
        )

        assertEquals(normal.a * 2, enlarged.a, .001f)
        assertEquals(297.5f, enlarged.x(250f, 250f), .001f)
        assertEquals(421f, enlarged.y(250f, 250f), .001f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `escala nao finita e rejeitada antes de modificar o documento`() {
        PdfPageGeometry.placement(
            0f, 0f, 100f, 100f, 0, PdfPageLayout(contentScale = Float.NaN)
        )
    }

    @Test
    fun `receita antiga sem posicao conserva alinhamento e margem`() {
        val layout = Json.decodeFromString<PdfPageLayout>("{\"alignment\":\"TOP\",\"contentScale\":0.75}")
        assertEquals(null, layout.contentPosition)
        assertEquals(false, layout.edgeToEdge)
        val transform = PdfPageGeometry.placement(0f, 0f, 595f, 842f, 0, layout)
        assertEquals(810f, transform.y(0f, 842f), .01f)
    }

    @Test
    fun `posicao livre alcanca topo e esquerda simultaneamente sem margem`() {
        val layout = PdfPageLayout(
            contentScale = .75f,
            contentPosition = com.joseleandro.scaniqo.domain.model.PdfContentPosition(0f, 0f),
            edgeToEdge = true,
        )
        val transform = PdfPageGeometry.placement(0f, 0f, 595f, 842f, 0, layout)
        assertEquals(0f, transform.x(0f, 842f), .001f)
        assertEquals(842f, transform.y(0f, 842f), .001f)
        assertEquals(446.25f, transform.x(595f, 0f), .001f)
        assertEquals(210.5f, transform.y(595f, 0f), .001f)
    }

    @Test
    fun `nove ancoras incluem quatro cantos com margem exata`() {
        for (horizontal in listOf(0f, .5f, 1f)) {
            for (vertical in listOf(0f, .5f, 1f)) {
                val layout = PdfPageLayout(
                    contentScale = .5f,
                    contentPosition = com.joseleandro.scaniqo.domain.model.PdfContentPosition(horizontal, vertical),
                )
                val transform = PdfPageGeometry.placement(0f, 0f, 595f, 842f, 0, layout)
                val width = transform.a * 595f
                val height = transform.d * 842f
                val left = 32f + (531f - width) * horizontal
                val bottom = 32f + (778f - height) * (1f - vertical)
                assertEquals(left, transform.x(0f, 0f), .01f)
                assertEquals(bottom, transform.y(0f, 0f), .01f)
            }
        }
    }

    @Test
    fun `posicao livre escolhe parte visivel ao ampliar sem mudar escala`() {
        val right = PdfPageGeometry.placement(
            0f, 0f, 595f, 842f, 0,
            PdfPageLayout(
                contentScale = 1.5f,
                edgeToEdge = true,
                contentPosition = com.joseleandro.scaniqo.domain.model.PdfContentPosition(1f, 1f),
            )
        )
        assertEquals(595f, right.x(595f, 0f), .01f)
        assertEquals(0f, right.y(0f, 0f), .01f)
        assertEquals(1.5f, right.a, .001f)
    }

    @Test
    fun `rotacao e CropBox deslocado preservam canto escolhido`() {
        val transform = PdfPageGeometry.placement(
            10f, 20f, 100f, 200f, 90,
            PdfPageLayout(
                contentScale = .5f,
                edgeToEdge = true,
                contentPosition = com.joseleandro.scaniqo.domain.model.PdfContentPosition(0f, 0f),
            ),
            200f, 100f,
        )
        val xs = listOf(transform.x(10f, 20f), transform.x(110f, 20f),
            transform.x(10f, 220f), transform.x(110f, 220f))
        val ys = listOf(transform.y(10f, 20f), transform.y(110f, 20f),
            transform.y(10f, 220f), transform.y(110f, 220f))
        assertEquals(0f, xs.min(), .01f)
        assertEquals(100f, ys.max(), .01f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `posicao fora da folha e rejeitada`() {
        PdfPageGeometry.placement(
            0f, 0f, 595f, 842f, 0,
            PdfPageLayout(
                contentPosition = com.joseleandro.scaniqo.domain.model.PdfContentPosition(Float.NaN, 0f)
            ),
        )
    }

    private fun square(alignment: PageAlignment, scale: PageScaleMode = PageScaleMode.FIT) =
        PdfPageGeometry.placement(0f, 0f, 500f, 500f, 0, PdfPageLayout(alignment, scale))
}
