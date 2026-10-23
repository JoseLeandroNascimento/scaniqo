package com.joseleandro.scaniqo.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class PdfOverlayKind { TEXT, CHECKMARK, HIGHLIGHT, UNDERLINE, ARROW, DRAWING, STAMP, WATERMARK, HEADER, FOOTER, PAGE_NUMBER }

@Serializable
enum class PdfTextHorizontalAlignment { START, CENTER, END }

@Serializable
enum class PdfTextVerticalAlignment { TOP, CENTER, BOTTOM }

/** Elementos editáveis do Scaniqo. Coordenadas normalizadas, origem no canto superior esquerdo. */
@Serializable
data class PdfOverlay(
    val id: String,
    val kind: PdfOverlayKind,
    val text: String = "",
    val x: Float = .1f,
    val y: Float = .1f,
    val width: Float = .5f,
    val height: Float = .08f,
    val fontSize: Float = 16f,
    val color: Int = 0xFF202124.toInt(),
    val opacity: Float = 1f,
    val points: List<PdfPoint> = emptyList(),
    val textHorizontalAlignment: PdfTextHorizontalAlignment = PdfTextHorizontalAlignment.START,
    val textVerticalAlignment: PdfTextVerticalAlignment = PdfTextVerticalAlignment.TOP,
    val autoSizeText: Boolean = false,
    val wrapText: Boolean = false,
) {
    fun movedTo(newX: Float, newY: Float): PdfOverlay {
        val left = newX.coerceIn(0f, 1f - width)
        val top = newY.coerceIn(0f, 1f - height)
        return copy(x = left, y = top, points = points.map {
            PdfPoint((it.x + left - x).coerceIn(0f, 1f), (it.y + top - y).coerceIn(0f, 1f))
        })
    }

    fun isValid(): Boolean =
        id.isNotBlank() && text.length <= 2000 &&
            listOf(x, y, width, height).all { it.isFinite() && it in 0f..1f } &&
            width > 0f && height > 0f && x + width <= 1.001f && y + height <= 1.001f &&
            fontSize.isFinite() && fontSize in 6f..72f &&
            opacity.isFinite() && opacity in .05f..1f && points.size <= 2000 &&
            points.all { it.x.isFinite() && it.y.isFinite() && it.x in 0f..1f && it.y in 0f..1f }
}

/** Áreas transitórias: nunca entram na receita editável do documento de origem. */
@Serializable
data class PdfRedactionArea(
    val pageId: String,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
) {
    fun isValid(): Boolean = pageId.isNotBlank() &&
        listOf(x, y, width, height).all { it.isFinite() && it in 0f..1f } &&
        width > 0f && height > 0f && x + width <= 1.001f && y + height <= 1.001f
}
