package com.joseleandro.scaniqo.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class SignatureModel(
    val id: String,
    val name: String,
    val imageUri: String,
    val aspectRatio: Float,
    val imageVersion: Int = 0,
    val source: SignatureSource? = null,
)

/** Dados editáveis. Assinaturas antigas não têm esta informação e usam o PNG salvo. */
@Serializable
data class SignatureSource(
    val type: SignatureSourceType,
    val strokes: List<SignatureStroke> = emptyList(),
    val text: String = "",
    val font: SignatureFont = SignatureFont.CURSIVE,
    val colorArgb: Int = 0xFF18212B.toInt(),
)

@Serializable
enum class SignatureSourceType { DRAW, TYPE, IMAGE }

/** Pontos normalizados tornam o desenho independente da densidade e do tamanho da tela. */
@Serializable
data class SignaturePoint(val x: Float, val y: Float)

@Serializable
data class SignatureStroke(
    val points: List<SignaturePoint>,
    val colorArgb: Int,
    val width: Float,
)

@Serializable
enum class SignatureFont(val family: String, val italic: Boolean, val bold: Boolean = false) {
    CURSIVE("cursive", false),
    HANDWRITTEN("casual", true),
    CLASSIC("serif", true),
    MONOSPACED("monospace", true),
    PEN("casual", false),
    SCRIPT("cursive", true),
    BRUSH("cursive", true, true),
}
