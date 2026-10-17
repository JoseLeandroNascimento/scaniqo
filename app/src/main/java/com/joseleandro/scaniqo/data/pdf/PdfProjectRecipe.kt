package com.joseleandro.scaniqo.data.pdf

import com.joseleandro.scaniqo.domain.model.PdfExportQuality
import com.joseleandro.scaniqo.domain.model.PdfPageLayout
import kotlinx.serialization.Serializable

/** A receita referencia somente arquivos relativos à pasta privada do documento. */
@Serializable
internal data class PdfProjectRecipe(
    val version: Int = 1,
    val pages: List<RecipePage>,
    val showPageNumbers: Boolean = false,
    val exportQuality: PdfExportQuality = PdfExportQuality.HIGH_QUALITY,
    val ocrEnabled: Boolean = false,
) {
    fun validate() {
        require(version == 1 && pages.size in 1..PdfWorkspaceFiles.MAX_PAGES) {
            "A receita do documento não é compatível."
        }
        pages.forEach { it.layout.validateImageProcessing() }
    }
}

@Serializable
internal data class RecipePage(
    val id: String,
    val sourcePageIndex: Int,
    val layout: PdfPageLayout,
    val signatures: List<RecipeSignature> = emptyList()
)

@Serializable
internal data class RecipeSignature(
    val id: String,
    val filename: String,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float
)
