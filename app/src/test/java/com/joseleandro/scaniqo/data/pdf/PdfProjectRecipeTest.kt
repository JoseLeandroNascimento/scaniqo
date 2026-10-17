package com.joseleandro.scaniqo.data.pdf

import com.joseleandro.scaniqo.domain.model.PageAlignment
import com.joseleandro.scaniqo.domain.model.PageScaleMode
import com.joseleandro.scaniqo.domain.model.PdfImageFilter
import com.joseleandro.scaniqo.domain.model.PdfExportQuality
import com.joseleandro.scaniqo.domain.model.PdfPerspective
import com.joseleandro.scaniqo.domain.model.PdfPoint
import com.joseleandro.scaniqo.domain.model.PdfPageLayout
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class PdfProjectRecipeTest {
    @Test
    fun `receita persiste filtros geometricos OCR e perfil sem alterar fontes`() {
        val recipe = PdfProjectRecipe(
            pages = listOf(RecipePage("pagina", 0, PdfPageLayout(
                imageFilter = PdfImageFilter.SHADOW_REMOVAL,
                filterIntensity = .6f,
                straightenDegrees = -3.5f,
                curvature = .2f,
                rasterizeText = true,
                perspective = PdfPerspective(
                    PdfPoint(.1f, .1f), PdfPoint(.9f, .05f),
                    PdfPoint(.95f, .95f), PdfPoint(.05f, .9f),
                ),
            ))),
            exportQuality = PdfExportQuality.BALANCED,
            ocrEnabled = true,
        )

        assertEquals(recipe, Json.decodeFromString<PdfProjectRecipe>(Json.encodeToString(recipe)))
    }

    @Test
    fun `receita anterior mantém originais e OCR desabilitado`() {
        val legacy = """{"pages":[{"id":"a","sourcePageIndex":0,"layout":{}}]}"""
        val recipe = Json.decodeFromString<PdfProjectRecipe>(legacy)

        assertEquals(PdfExportQuality.HIGH_QUALITY, recipe.exportQuality)
        assertEquals(false, recipe.ocrEnabled)
        assertEquals(PdfPageLayout(), recipe.pages.single().layout)
    }

    @Test
    fun `receita persiste melhoria e continua reconhecendo os filtros anteriores`() {
        PdfImageFilter.entries.forEach { filter ->
            val recipe = PdfProjectRecipe(
                pages = listOf(RecipePage("pagina", 0, PdfPageLayout(imageFilter = filter)))
            )

            val restored = Json.decodeFromString<PdfProjectRecipe>(Json.encodeToString(recipe))

            assertEquals(filter, restored.pages.single().layout.imageFilter)
            assertEquals(0, restored.pages.single().sourcePageIndex)
        }
    }

    @Test
    fun `receita antiga conserva aparencia sem rotacao escala ou filtros adicionais`() {
        val legacy = """{"pages":[{"id":"a","sourcePageIndex":0,"layout":{"alignment":"TOP","scaleMode":"FIT"}}]}"""

        val layout = Json.decodeFromString<PdfProjectRecipe>(legacy).pages.single().layout

        assertEquals(PageAlignment.TOP, layout.alignment)
        assertEquals(0, layout.rotationQuarterTurns)
        assertEquals(1f, layout.contentScale, 0f)
        assertEquals(PdfImageFilter.ORIGINAL, layout.imageFilter)
    }

    @Test
    fun `receita preserva ordem fontes alinhamento numeracao e assinatura`() {
        val recipe = PdfProjectRecipe(
            pages = listOf(
                RecipePage(
                    id = "pagina-b",
                    sourcePageIndex = 2,
                    layout = PdfPageLayout(
                        PageAlignment.RIGHT, PageScaleMode.FILL,
                        rotationQuarterTurns = 3, contentScale = .75f,
                        imageFilter = PdfImageFilter.AUTO
                    ),
                    signatures = listOf(
                        RecipeSignature("assinatura", "signature_123.png", .2f, .3f, .4f, .1f)
                    )
                ),
                RecipePage("pagina-a", 0, PdfPageLayout())
            ),
            showPageNumbers = true
        )

        val encoded = Json.encodeToString(recipe)
        val restored = Json.decodeFromString<PdfProjectRecipe>(encoded)

        assertEquals(recipe, restored)
        assertEquals(listOf(2, 0), restored.pages.map { it.sourcePageIndex })
    }
}
