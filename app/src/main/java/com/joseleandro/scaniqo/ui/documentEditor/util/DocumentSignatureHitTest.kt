package com.joseleandro.scaniqo.ui.documentEditor.util

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.joseleandro.scaniqo.domain.model.PdfSignaturePlacement
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Testa a caixa rotacionada no referencial ampliado da folha, com alvo mínimo para assinaturas finas. */
internal fun signatureContainsPointer(
    placement: PdfSignaturePlacement,
    pageBounds: Rect,
    pointer: Offset,
    minimumTargetSize: Float,
): Boolean {
    val centerX = pageBounds.left + (placement.x + placement.width / 2f) * pageBounds.width
    val centerY = pageBounds.top + (placement.y + placement.height / 2f) * pageBounds.height
    val angle = Math.toRadians(placement.rotationDegrees.toDouble())
    val dx = pointer.x - centerX
    val dy = pointer.y - centerY
    val localX = (dx * cos(angle) + dy * sin(angle)).toFloat()
    val localY = (-dx * sin(angle) + dy * cos(angle)).toFloat()
    return abs(localX) <= maxOf(placement.width * pageBounds.width, minimumTargetSize) / 2f &&
        abs(localY) <= maxOf(placement.height * pageBounds.height, minimumTargetSize) / 2f
}
