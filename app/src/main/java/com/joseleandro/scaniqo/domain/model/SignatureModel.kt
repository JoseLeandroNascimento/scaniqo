package com.joseleandro.scaniqo.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class SignatureModel(
    val id: String,
    val name: String,
    val imageUri: String,
    val aspectRatio: Float,
)

/** Pontos normalizados tornam o desenho independente da densidade e do tamanho da tela. */
data class SignaturePoint(val x: Float, val y: Float)

data class SignatureStroke(
    val points: List<SignaturePoint>,
    val colorArgb: Int,
    val width: Float,
)

enum class SignatureFont(val family: String, val italic: Boolean) {
    CURSIVE("cursive", false),
    HANDWRITTEN("casual", true),
    CLASSIC("serif", true),
    MONOSPACED("monospace", true),
}
