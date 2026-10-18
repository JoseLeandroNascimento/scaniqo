package com.joseleandro.scaniqo.ui.documentEditor.util

import com.joseleandro.scaniqo.domain.model.PdfSignaturePlacement
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

private const val PAGE_ASPECT_RATIO = 595f / 842f

/** Mantém a proporção quando height é omitido e limita todo o retângulo à folha. */
internal fun transformSignaturePlacement(
    placement: PdfSignaturePlacement,
    x: Float = placement.x,
    y: Float = placement.y,
    width: Float = placement.width,
    rotationDegrees: Float = placement.rotationDegrees,
    height: Float? = null,
    stretchToBounds: Boolean = placement.stretchToBounds,
): PdfSignaturePlacement {
    val shape = if (height == null) placement else placement.copy(
        width = width.takeIf { it.isFinite() && it > 0f }
            ?: placement.width.takeIf { it.isFinite() && it > 0f } ?: .3f,
        height = height.takeIf { it.isFinite() && it > 0f }
            ?: placement.height.takeIf { it.isFinite() && it > 0f } ?: .12f,
    )
    val aspect = (shape.height / shape.width).takeIf { it.isFinite() && it > 0f } ?: .4f
    val rotation = rotationDegrees.takeIf(Float::isFinite) ?: placement.rotationDegrees.takeIf(Float::isFinite) ?: 0f
    val normalizedRotation = ((rotation + 180f) % 360f + 360f) % 360f - 180f
    val radians = Math.toRadians(normalizedRotation.toDouble())
    val cosine = abs(cos(radians)).toFloat()
    val sine = abs(sin(radians)).toFloat()
    val rotatedWidthFactor = cosine + sine * aspect / PAGE_ASPECT_RATIO
    val rotatedHeightFactor = sine * PAGE_ASPECT_RATIO + cosine * aspect
    val widthRange = signatureWidthRange(shape, normalizedRotation)
    val safeWidth = (width.takeIf(Float::isFinite) ?: placement.width.takeIf(Float::isFinite) ?: .3f)
        .coerceIn(widthRange)
    val safeHeight = safeWidth * aspect
    val halfBoundsWidth = (maxOf(safeWidth, safeWidth * rotatedWidthFactor) / 2f).coerceAtMost(.5f)
    val halfBoundsHeight = (maxOf(safeHeight, safeWidth * rotatedHeightFactor) / 2f).coerceAtMost(.5f)
    val centerX = ((x.takeIf(Float::isFinite) ?: placement.x.takeIf(Float::isFinite) ?: 0f) + safeWidth / 2f)
        .coerceIn(halfBoundsWidth, 1f - halfBoundsWidth)
    val centerY = ((y.takeIf(Float::isFinite) ?: placement.y.takeIf(Float::isFinite) ?: 0f) + safeHeight / 2f)
        .coerceIn(halfBoundsHeight, 1f - halfBoundsHeight)
    return placement.copy(
        x = centerX - safeWidth / 2f,
        y = centerY - safeHeight / 2f,
        width = safeWidth,
        height = safeHeight,
        rotationDegrees = normalizedRotation,
        stretchToBounds = stretchToBounds,
    )
}

/** Limites iguais para a barra de tamanho, os gestos e a validação da geometria. */
internal fun signatureWidthRange(
    placement: PdfSignaturePlacement,
    rotationDegrees: Float = placement.rotationDegrees,
): ClosedFloatingPointRange<Float> {
    val aspect = (placement.height / placement.width).takeIf { it.isFinite() && it > 0f } ?: .4f
    val radians = Math.toRadians((rotationDegrees.takeIf(Float::isFinite) ?: 0f).toDouble())
    val cosine = abs(cos(radians)).toFloat()
    val sine = abs(sin(radians)).toFloat()
    val maximum = minOf(
        .85f, .85f / aspect,
        1f / (cosine + sine * aspect / PAGE_ASPECT_RATIO),
        1f / (sine * PAGE_ASPECT_RATIO + cosine * aspect),
    )
    return minOf(.04f, maximum / 2f)..maximum
}

/** Escalar ou girar mantém o centro; o centro só se desloca para respeitar as bordas da folha. */
internal fun resizeSignatureAroundCenter(
    placement: PdfSignaturePlacement,
    width: Float = placement.width,
    rotationDegrees: Float = placement.rotationDegrees,
    height: Float? = null,
    stretchToBounds: Boolean = placement.stretchToBounds,
): PdfSignaturePlacement {
    val sized = transformSignaturePlacement(
        placement, width = width, rotationDegrees = rotationDegrees,
        height = height, stretchToBounds = stretchToBounds,
    )
    return transformSignaturePlacement(
        sized,
        x = placement.x + (placement.width - sized.width) / 2f,
        y = placement.y + (placement.height - sized.height) / 2f,
    )
}

/** A alça responde à distância arrastada na diagonal, sem crescer ao cruzar o centro. */
internal fun resizeSignatureFromHandle(
    placement: PdfSignaturePlacement,
    deltaX: Float,
    deltaY: Float,
    pageWidth: Float,
    pageHeight: Float,
): PdfSignaturePlacement {
    if (!listOf(deltaX, deltaY, pageWidth, pageHeight).all(Float::isFinite) ||
        pageWidth <= 0f || pageHeight <= 0f
    ) return placement
    val angle = Math.toRadians(placement.rotationDegrees.toDouble())
    val x = placement.width * pageWidth / 2f
    val y = placement.height * pageHeight / 2f
    val diagonalX = (x * cos(angle) - y * sin(angle)).toFloat()
    val diagonalY = (x * sin(angle) + y * cos(angle)).toFloat()
    val distanceSquared = diagonalX * diagonalX + diagonalY * diagonalY
    if (distanceSquared <= 0f) return placement
    val scale = (1f + (deltaX * diagonalX + deltaY * diagonalY) / distanceSquared).coerceAtLeast(0f)
    return resizeSignatureAroundCenter(placement, width = placement.width * scale)
}

/**
 * Os índices seguem os cantos superior esquerdo, superior direito, inferior direito e inferior
 * esquerdo. Deltas são pixels da folha; largura e altura mudam nos eixos locais da assinatura.
 * O canto oposto fica ancorado. Ao alcançar uma borda, limita o gesto sem deslocar essa âncora.
 */
internal fun resizeSignatureFromCorner(
    placement: PdfSignaturePlacement,
    cornerIndex: Int,
    deltaX: Float,
    deltaY: Float,
    pageWidth: Float,
    pageHeight: Float,
): PdfSignaturePlacement {
    if (cornerIndex !in 0..3 ||
        !listOf(deltaX, deltaY, pageWidth, pageHeight).all(Float::isFinite) ||
        pageWidth <= 0f || pageHeight <= 0f || (deltaX == 0f && deltaY == 0f)
    ) return placement
    val base = transformSignaturePlacement(placement)
    val angle = Math.toRadians(base.rotationDegrees.toDouble())
    val cosine = cos(angle).toFloat()
    val sine = sin(angle).toFloat()
    val signX = if (cornerIndex == 0 || cornerIndex == 3) -1f else 1f
    val signY = if (cornerIndex < 2) -1f else 1f
    // A altura física de referência é 1; a largura é a proporção do papel A4.
    val dx = deltaX / pageWidth * PAGE_ASPECT_RATIO
    val dy = deltaY / pageHeight
    val localX = dx * cosine + dy * sine
    val localY = -dx * sine + dy * cosine
    val desiredWidth = (base.width + signX * localX / PAGE_ASPECT_RATIO)
        .coerceIn(minOf(.04f, base.width), .85f)
    val desiredHeight = (base.height + signY * localY)
        .coerceIn(minOf(.04f * PAGE_ASPECT_RATIO, base.height), .85f)
    val anchorX = (base.x + base.width / 2f) * PAGE_ASPECT_RATIO -
        (signX * base.width * PAGE_ASPECT_RATIO * cosine - signY * base.height * sine) / 2f
    val anchorY = base.y + base.height / 2f -
        (signX * base.width * PAGE_ASPECT_RATIO * sine + signY * base.height * cosine) / 2f

    fun candidate(fraction: Float): PdfSignaturePlacement {
        val width = base.width + (desiredWidth - base.width) * fraction
        val height = base.height + (desiredHeight - base.height) * fraction
        val centerX = anchorX +
            (signX * width * PAGE_ASPECT_RATIO * cosine - signY * height * sine) / 2f
        val centerY = anchorY +
            (signX * width * PAGE_ASPECT_RATIO * sine + signY * height * cosine) / 2f
        return base.copy(
            x = centerX / PAGE_ASPECT_RATIO - width / 2f,
            y = centerY - height / 2f,
            width = width, height = height, stretchToBounds = true,
        )
    }

    val requested = candidate(1f)
    val fitted = if (requested.isInsidePage()) requested else {
        // Todas as bordas são lineares em relação ao tamanho; o intervalo válido é contínuo.
        var lower = 0f
        var upper = 1f
        repeat(28) {
            val middle = (lower + upper) / 2f
            if (candidate(middle).isInsidePage()) lower = middle else upper = middle
        }
        candidate(lower)
    }
    // Corrige somente diferenças de arredondamento dos cálculos de borda.
    return transformSignaturePlacement(
        fitted, width = fitted.width, height = fitted.height, stretchToBounds = true,
    )
}

/** A alça de rotação fica acima do centro; a posição recebida é absoluta na folha. */
internal fun rotateSignatureFromHandle(
    placement: PdfSignaturePlacement,
    pointerX: Float,
    pointerY: Float,
    pageWidth: Float,
    pageHeight: Float,
): PdfSignaturePlacement {
    if (!listOf(pointerX, pointerY, pageWidth, pageHeight).all(Float::isFinite) ||
        pageWidth <= 0f || pageHeight <= 0f
    ) return placement
    val centerX = (placement.x + placement.width / 2f) * pageWidth
    val centerY = (placement.y + placement.height / 2f) * pageHeight
    val dx = pointerX - centerX
    val dy = pointerY - centerY
    if (!dx.isFinite() || !dy.isFinite() || dx * dx + dy * dy < .0001f) return placement
    val degrees = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat() + 90f
    return resizeSignatureAroundCenter(placement, rotationDegrees = degrees)
}

private fun PdfSignaturePlacement.isInsidePage(): Boolean {
    val epsilon = .000001f
    if (x < -epsilon || y < -epsilon || x + width > 1f + epsilon || y + height > 1f + epsilon) {
        return false
    }
    val radians = Math.toRadians(rotationDegrees.toDouble())
    val cosine = abs(cos(radians)).toFloat()
    val sine = abs(sin(radians)).toFloat()
    val halfWidth = (width * cosine + height * sine / PAGE_ASPECT_RATIO) / 2f
    val halfHeight = (width * sine * PAGE_ASPECT_RATIO + height * cosine) / 2f
    val centerX = x + width / 2f
    val centerY = y + height / 2f
    return centerX - halfWidth >= -epsilon && centerX + halfWidth <= 1f + epsilon &&
        centerY - halfHeight >= -epsilon && centerY + halfHeight <= 1f + epsilon
}
