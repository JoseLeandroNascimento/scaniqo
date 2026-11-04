package com.joseleandro.scaniqo.data.pdf

import kotlinx.serialization.json.Json

private val projectValidationJson = Json { ignoreUnknownKeys = true }

/** Validates a restored project's references without exposing its persisted recipe format. */
fun validatePdfProjectRecipe(
    recipeJson: String,
    pageCount: Int,
    sourcePageCount: Int,
    filenames: Collection<String>,
) {
    require(recipeJson.toByteArray(Charsets.UTF_8).size.toLong() <= PdfProjectRecipe.MAX_BYTES)
    val recipe = projectValidationJson.decodeFromString<PdfProjectRecipe>(recipeJson)
    recipe.validate()
    require(recipe.pages.size == pageCount && recipe.pages.map { it.id }.distinct().size == recipe.pages.size)
    recipe.pages.forEach { page ->
        require(page.sourcePageIndex in 0 until sourcePageCount && page.signatures.size <= 20)
        page.signatures.forEach { signature ->
            require(signature.filename in filenames &&
                signature.filename.matches(Regex("signature_[a-fA-F0-9-]+\\.png")))
            require(listOf(signature.x, signature.y, signature.width, signature.height, signature.rotationDegrees)
                .all { it.isFinite() })
            require(signature.x >= 0f && signature.y >= 0f && signature.width > 0f && signature.height > 0f &&
                signature.x + signature.width <= 1.001f && signature.y + signature.height <= 1.001f)
        }
    }
}
