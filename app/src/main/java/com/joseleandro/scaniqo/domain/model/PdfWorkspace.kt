package com.joseleandro.scaniqo.domain.model

import java.io.IOException
import kotlinx.serialization.Serializable

class PdfPasswordRequiredException : IOException("Informe a senha correta do PDF.")

class PdfImportException(message: String, cause: Throwable? = null) : IOException(message, cause)

@Serializable
enum class PageAlignment { CENTER, TOP, BOTTOM, LEFT, RIGHT }

@Serializable
enum class PageScaleMode { FIT, FILL }

@Serializable
enum class PdfImageFilter {
    ORIGINAL, AUTO, GRAYSCALE, BLACK_WHITE, ENHANCE,
    SHADOW_REMOVAL, ILLUMINATION, WHITEN, ADAPTIVE_BLACK_WHITE, DENOISE, SHARPEN, BLEED_THROUGH
}

@Serializable
enum class PdfExportQuality { HIGH_QUALITY, BALANCED, SMALL }

/** Pontos normalizados da captura; a origem fica no canto superior esquerdo. */
@Serializable
data class PdfPoint(val x: Float, val y: Float)

@Serializable
data class PdfPerspective(
    val topLeft: PdfPoint = PdfPoint(0f, 0f),
    val topRight: PdfPoint = PdfPoint(1f, 0f),
    val bottomRight: PdfPoint = PdfPoint(1f, 1f),
    val bottomLeft: PdfPoint = PdfPoint(0f, 1f)
)

/** Fração do deslocamento disponível na folha: 0 = esquerda/topo, 1 = direita/base. */
@Serializable
data class PdfContentPosition(
    val horizontal: Float = .5f,
    val vertical: Float = .5f,
) {
    fun isValid(): Boolean = horizontal.isFinite() && vertical.isFinite() &&
        horizontal in 0f..1f && vertical in 0f..1f
}

/** Ausência de posição livre preserva exatamente o alinhamento das receitas antigas. */
fun PdfPageLayout.effectiveContentPosition(): PdfContentPosition = contentPosition ?: when (alignment) {
    PageAlignment.TOP -> PdfContentPosition(.5f, 0f)
    PageAlignment.BOTTOM -> PdfContentPosition(.5f, 1f)
    PageAlignment.LEFT -> PdfContentPosition(0f, .5f)
    PageAlignment.RIGHT -> PdfContentPosition(1f, .5f)
    PageAlignment.CENTER -> PdfContentPosition()
}

@Serializable
data class PdfPageLayout(
    val alignment: PageAlignment = PageAlignment.CENTER,
    val scaleMode: PageScaleMode = PageScaleMode.FIT,
    val rotationQuarterTurns: Int = 0,
    val contentScale: Float = 1f,
    val imageFilter: PdfImageFilter = PdfImageFilter.ORIGINAL,
    val filterIntensity: Float = 1f,
    val straightenDegrees: Float = 0f,
    val perspective: PdfPerspective? = null,
    val curvature: Float = 0f,
    val rasterizeText: Boolean = false,
    /** Recorta a área real da fonte sem alterar as proporções do conteúdo. */
    val crop: PdfCropBounds? = null,
    /** Remove apenas o entorno da captura; não retifica nem desloca o documento. */
    val backgroundMask: PdfPerspective? = null,
    /** Nulo mantém a geometria de receitas criadas antes do posicionamento livre. */
    val contentPosition: PdfContentPosition? = null,
    /** Permite alcançar a borda mantendo FIT e o tamanho escolhido. */
    val edgeToEdge: Boolean = false,
)

/** Coordenadas proporcionais à folha, com origem no canto superior esquerdo. */
@Serializable
data class PdfSignaturePlacement(
    val id: String,
    val imageUri: String,
    val x: Float = .3f,
    val y: Float = .7f,
    val width: Float = .3f,
    val height: Float = .12f,
    /** Rotação horária ao redor do centro, independente da orientação da página. */
    val rotationDegrees: Float = 0f,
    /** Preenche o retângulo editado; receitas anteriores preservam a proporção original. */
    val stretchToBounds: Boolean = false,
)

@Serializable
data class PdfPageSpec(
    val id: String,
    val sourceUri: String,
    val sourcePageIndex: Int,
    val layout: PdfPageLayout = PdfPageLayout(),
    val signatures: List<PdfSignaturePlacement> = emptyList(),
    val overlays: List<PdfOverlay> = emptyList()
)

@Serializable
data class PdfWorkspace(
    val documentId: Long,
    val workspaceId: String,
    val directoryUri: String,
    val pages: List<PdfPageSpec>,
    val showPageNumbers: Boolean = false,
    val isProtected: Boolean = false,
    val protectionKeyId: String? = null,
    val exportQuality: PdfExportQuality = PdfExportQuality.HIGH_QUALITY,
    val ocrEnabled: Boolean = false
)
