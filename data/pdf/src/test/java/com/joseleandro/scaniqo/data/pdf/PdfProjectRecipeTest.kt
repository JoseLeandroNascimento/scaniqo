package com.joseleandro.scaniqo.data.pdf

import com.joseleandro.scaniqo.domain.model.PageAlignment
import com.joseleandro.scaniqo.domain.model.PageScaleMode
import com.joseleandro.scaniqo.domain.model.PdfCropBounds
import com.joseleandro.scaniqo.domain.model.PdfImageFilter
import com.joseleandro.scaniqo.domain.model.PdfExportQuality
import com.joseleandro.scaniqo.domain.model.PdfPerspective
import com.joseleandro.scaniqo.domain.model.PdfPoint
import com.joseleandro.scaniqo.domain.model.PdfPageLayout
import com.joseleandro.scaniqo.domain.model.PdfOverlay
import com.joseleandro.scaniqo.domain.model.PdfOverlayKind
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class PdfProjectRecipeTest {
    @Test
    fun editableOverlaysRoundTripAndOldRecipesDefaultToEmpty() {
        val overlay = PdfOverlay("note", PdfOverlayKind.TEXT, text = "Nome: José", x = .2f, opacity = .7f)
        val recipe = PdfProjectRecipe(pages = listOf(RecipePage("page", 0, PdfPageLayout(), overlays = listOf(overlay))))
        val restored = Json.decodeFromString<PdfProjectRecipe>(Json.encodeToString(recipe))
        restored.validate()
        assertEquals(recipe, restored)
        val legacy = Json.decodeFromString<PdfProjectRecipe>("""{"pages":[{"id":"p","sourcePageIndex":0,"layout":{}}]}""")
        assertEquals(emptyList<PdfOverlay>(), legacy.pages.single().overlays)
    }

    @Test
    fun storedRecipeUsesUtf8AndCanBeReopened() {
        val recipe = PdfProjectRecipe(pages = listOf(RecipePage("p", 0, PdfPageLayout(),
            overlays = listOf(PdfOverlay("text", PdfOverlayKind.TEXT, text = "João — revisão")))))
        val encoded = recipe.encodeForStorage(Json)
        val restored = Json.decodeFromString<PdfProjectRecipe>(encoded.toString(Charsets.UTF_8))
        restored.validate()
        assertEquals(recipe, restored)
    }

    @Test(expected = IllegalArgumentException::class)
    fun oversizedOverlayRecipeCannotBeSaved() {
        // O JSON cabe em 4 milhões de caracteres, mas ultrapassa o limite em bytes UTF-8.
        val overlays = List(12) { PdfOverlay("text-$it", PdfOverlayKind.TEXT, text = "ã".repeat(2000)) }
        PdfProjectRecipe(pages = List(100) {
            RecipePage("p-$it", it, PdfPageLayout(), overlays = overlays)
        }).encodeForStorage(Json)
    }

    @Test(expected = IllegalArgumentException::class)
    fun corruptOverlayCannotEnterEditableRecipe() {
        PdfProjectRecipe(pages = listOf(RecipePage("p", 0, PdfPageLayout(),
            overlays = listOf(PdfOverlay("bad", PdfOverlayKind.TEXT, width = -1f))))).validate()
    }

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
    fun `receita persiste recorte sem distorcao e mascara sem modificar perspectiva legada`() {
        val legacyPerspective = PdfPerspective(topLeft = PdfPoint(.05f, .08f))
        val recipe = PdfProjectRecipe(pages = listOf(
            RecipePage("legada", 0, PdfPageLayout(perspective = legacyPerspective)),
            RecipePage("recortada", 1, PdfPageLayout(
                crop = PdfCropBounds(.2f, .1f, .8f, .7f),
                backgroundMask = PdfPerspective(
                    PdfPoint(.1f, .05f), PdfPoint(.9f, .1f),
                    PdfPoint(.95f, .9f), PdfPoint(.1f, .95f),
                ),
            )),
        ))
        val restored = Json.decodeFromString<PdfProjectRecipe>(Json.encodeToString(recipe))
        restored.validate()
        assertEquals(recipe, restored)
        assertEquals(legacyPerspective, restored.pages[0].layout.perspective)
        assertEquals(null, restored.pages[0].layout.crop)
        assertEquals(null, restored.pages[1].layout.perspective)
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
    fun `assinatura anterior sem rotacao continua com angulo zero`() {
        val legacy = """{"pages":[{"id":"a","sourcePageIndex":0,"layout":{},"signatures":[{"id":"s","filename":"s.png","x":0.2,"y":0.3,"width":0.4,"height":0.1}]}]}"""
        val signature = Json.decodeFromString<PdfProjectRecipe>(legacy).pages.single().signatures.single()

        assertEquals(0f, signature.rotationDegrees, 0f)
        assertEquals(false, signature.stretchToBounds)
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
                        RecipeSignature("assinatura", "signature_123.png", .2f, .3f, .4f, .1f,
                            rotationDegrees = -37f, stretchToBounds = true)
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
