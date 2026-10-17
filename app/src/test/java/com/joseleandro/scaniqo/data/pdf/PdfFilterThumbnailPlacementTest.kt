package com.joseleandro.scaniqo.data.pdf

import com.joseleandro.scaniqo.domain.model.PageAlignment
import com.joseleandro.scaniqo.domain.model.PageScaleMode
import com.joseleandro.scaniqo.domain.model.PdfPageLayout
import org.junit.Assert.assertEquals
import org.junit.Test

class PdfFilterThumbnailPlacementTest {
    @Test
    fun `rotacao horaria coloca topo esquerdo da captura no topo direito do conteudo`() {
        val matrix = PdfFilterThumbnailPlacement.matrix(
            200f, 100f, 400, 200, PdfPageLayout(rotationQuarterTurns = 1),
        )
        assertPoint(492f, 32f, matrix, 0f, 0f)
        assertPoint(492f, 810f, matrix, 400f, 0f)
        assertPoint(103f, 32f, matrix, 0f, 200f)
    }

    @Test
    fun `alinhamento superior e inferior respeita as margens da folha`() {
        val top = PdfFilterThumbnailPlacement.matrix(
            100f, 50f, 200, 100, PdfPageLayout(alignment = PageAlignment.TOP),
        )
        assertPoint(32f, 32f, top, 0f, 0f)
        assertPoint(563f, 297.5f, top, 200f, 100f)
        val bottom = PdfFilterThumbnailPlacement.matrix(
            100f, 50f, 200, 100, PdfPageLayout(alignment = PageAlignment.BOTTOM),
        )
        assertPoint(32f, 544.5f, bottom, 0f, 0f)
        assertPoint(563f, 810f, bottom, 200f, 100f)
    }

    @Test
    fun `preenchimento sem margens mantem recorte central e escala configurada`() {
        val matrix = PdfFilterThumbnailPlacement.matrix(
            100f, 100f, 220, 220, PdfPageLayout(scaleMode = PageScaleMode.FILL),
        )
        assertPoint(-123.5f, 0f, matrix, 0f, 0f)
        assertPoint(718.5f, 842f, matrix, 220f, 220f)
    }

    @Test
    fun `quatro rotacoes preservam a orientacao dos quatro cantos da imagem`() {
        // Ordem dos cantos da captura: superior esquerdo, superior direito, inferior esquerdo e direito.
        val corners = listOf(0f to 0f, 400f to 0f, 0f to 200f, 400f to 200f)
        val expected = listOf(
            listOf(32f to 288.25f, 563f to 288.25f, 32f to 553.75f, 563f to 553.75f),
            listOf(492f to 32f, 492f to 810f, 103f to 32f, 103f to 810f),
            listOf(563f to 553.75f, 32f to 553.75f, 563f to 288.25f, 32f to 288.25f),
            listOf(103f to 810f, 103f to 32f, 492f to 810f, 492f to 32f),
        )
        for (turn in 0..3) {
            val matrix = PdfFilterThumbnailPlacement.matrix(
                200f, 100f, 400, 200, PdfPageLayout(rotationQuarterTurns = turn),
            )
            corners.forEachIndexed { index, (x, y) ->
                val (targetX, targetY) = expected[turn][index]
                assertPoint(targetX, targetY, matrix, x, y)
            }
        }
    }

    private fun assertPoint(x: Float, y: Float, matrix: FloatArray, sourceX: Float, sourceY: Float) {
        assertEquals(x, matrix[0] * sourceX + matrix[1] * sourceY + matrix[2], .001f)
        assertEquals(y, matrix[3] * sourceX + matrix[4] * sourceY + matrix[5], .001f)
    }
}
