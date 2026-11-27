package com.joseleandro.scaniqo.data.pdf

import com.joseleandro.scaniqo.domain.model.PdfExportQuality
import com.joseleandro.scaniqo.domain.model.PdfPageLayout
import com.joseleandro.scaniqo.domain.model.PdfOverlay
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement

/** A receita referencia somente arquivos relativos à pasta privada do documento. */
@Serializable
internal data class PdfProjectRecipe(
    val version: Int = 1,
    val pages: List<RecipePage>,
    val showPageNumbers: Boolean = false,
    val exportQuality: PdfExportQuality = PdfExportQuality.HIGH_QUALITY,
    val ocrEnabled: Boolean = false,
    /** Receitas do antigo preenchimento usam o resultado salvo como base visual imutável. */
    @Transient val useSavedDocumentAsSource: Boolean = false,
) {
    companion object {
        const val MAX_BYTES = 4L * 1024 * 1024

        fun decodeForEditing(json: Json, text: String): PdfProjectRecipe {
            val element = json.parseToJsonElement(text)
            // Não interpretamos nem reativamos campos antigos. Seus valores visíveis já estão no PDF final.
            val legacyFields = ((element as? JsonObject)?.get("pages") as? JsonArray).orEmpty().any { page ->
                ((page as? JsonObject)?.get("formFields") as? JsonArray)?.isNotEmpty() == true
            }
            return json.decodeFromJsonElement<PdfProjectRecipe>(element)
                .copy(useSavedDocumentAsSource = legacyFields)
                .also(PdfProjectRecipe::validate)
        }
    }

    /** Recusa uma receita que o leitor não conseguiria restaurar, antes de adotar o novo arquivo. */
    fun encodeForStorage(json: Json): ByteArray {
        validate()
        val encoded = json.encodeToString(this).toByteArray(Charsets.UTF_8)
        require(encoded.size.toLong() <= MAX_BYTES) {
            "O documento tem muitas anotações. Remova algumas antes de salvar."
        }
        return encoded
    }

    fun validate() {
        require(version == 1 && pages.size in 1..PdfWorkspaceFiles.MAX_PAGES) {
            "A receita do documento não é compatível."
        }
        pages.forEach {
            it.layout.validateImageProcessing()
            require(it.overlays.size <= 100 && it.overlays.all(PdfOverlay::isValid))
        }
    }
}

@Serializable
internal data class RecipePage(
    val id: String,
    val sourcePageIndex: Int,
    val layout: PdfPageLayout,
    val signatures: List<RecipeSignature> = emptyList(),
    val overlays: List<PdfOverlay> = emptyList()
)

@Serializable
internal data class RecipeSignature(
    val id: String,
    val filename: String,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val rotationDegrees: Float = 0f,
    val stretchToBounds: Boolean = false,
)
