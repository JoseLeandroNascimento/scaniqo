package com.joseleandro.scaniqo.ui.documentSignature

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.geometry.Rect
import com.joseleandro.scaniqo.domain.model.PdfPoint

/** Pan normalizado pela folha em 100%, para conservar a região visível após redimensionar a janela. */
internal data class SignatureDocumentViewport(
    val zoom: Float = 1f,
    val panX: Float = 0f,
    val panY: Float = 0f,
)

@Stable
internal class DocumentSignatureViewportState(initial: SignatureDocumentViewport = SignatureDocumentViewport()) {
    var value by mutableStateOf(initial)
        private set

    fun pan(deltaX: Float, deltaY: Float, width: Float, height: Float, margin: Float) {
        value = panSignatureDocumentViewport(value, deltaX, deltaY, width, height, margin)
    }

    fun zoomTo(zoom: Float, width: Float, height: Float, margin: Float, focus: PdfPoint?) {
        value = zoomSignatureDocumentViewport(value, zoom, width, height, margin, focus)
    }

    fun reset() { value = SignatureDocumentViewport() }

    companion object {
        val Saver = listSaver<DocumentSignatureViewportState, Float>(
            save = { listOf(it.value.zoom, it.value.panX, it.value.panY) },
            restore = { DocumentSignatureViewportState(SignatureDocumentViewport(it[0], it[1], it[2])) },
        )
    }
}

/** Apenas mudanças de escala ou de janela reiniciam os gestos; translação não faz parte da chave. */
internal data class SignatureDocumentViewportGestureKey(
    val canvasWidth: Float,
    val canvasHeight: Float,
    val margin: Float,
    val zoom: Float,
)

internal data class SignatureDocumentViewportBounds(
    val viewport: SignatureDocumentViewport,
    val basePage: Rect,
    val page: Rect,
    val gestureKey: SignatureDocumentViewportGestureKey,
)

/** Mantém a folha centralizada em eixos sem overflow e permite chegar às quatro bordas com margem. */
internal fun signatureDocumentViewportBounds(
    viewport: SignatureDocumentViewport,
    width: Float,
    height: Float,
    margin: Float,
): SignatureDocumentViewportBounds {
    val canvasWidth = width.takeIf { it.isFinite() && it > 0f } ?: 1f
    val canvasHeight = height.takeIf { it.isFinite() && it > 0f } ?: 1f
    val safeMargin = margin.takeIf { it.isFinite() && it >= 0f } ?: 0f
    val availableWidth = (canvasWidth - 2f * safeMargin).coerceAtLeast(1f)
    val availableHeight = (canvasHeight - 2f * safeMargin).coerceAtLeast(1f)
    val baseWidth = minOf(availableWidth, availableHeight * 595f / 842f)
    val baseHeight = baseWidth * 842f / 595f
    val zoom = (viewport.zoom.takeIf(Float::isFinite) ?: 1f).coerceIn(1f, 3f)
    val maxPanX = ((baseWidth * zoom - availableWidth) / 2f).coerceAtLeast(0f) / baseWidth
    val maxPanY = ((baseHeight * zoom - availableHeight) / 2f).coerceAtLeast(0f) / baseHeight
    val current = SignatureDocumentViewport(
        zoom,
        if (maxPanX == 0f) 0f else (viewport.panX.takeIf(Float::isFinite) ?: 0f).coerceIn(-maxPanX, maxPanX),
        if (maxPanY == 0f) 0f else (viewport.panY.takeIf(Float::isFinite) ?: 0f).coerceIn(-maxPanY, maxPanY),
    )
    val base = Rect(
        (canvasWidth - baseWidth) / 2f, (canvasHeight - baseHeight) / 2f,
        (canvasWidth + baseWidth) / 2f, (canvasHeight + baseHeight) / 2f,
    )
    val left = (canvasWidth - baseWidth * zoom) / 2f + current.panX * baseWidth
    val top = (canvasHeight - baseHeight * zoom) / 2f + current.panY * baseHeight
    return SignatureDocumentViewportBounds(
        current, base, Rect(left, top, left + baseWidth * zoom, top + baseHeight * zoom),
        SignatureDocumentViewportGestureKey(canvasWidth, canvasHeight, safeMargin, zoom),
    )
}

internal fun panSignatureDocumentViewport(
    viewport: SignatureDocumentViewport,
    deltaX: Float,
    deltaY: Float,
    width: Float,
    height: Float,
    margin: Float,
): SignatureDocumentViewport {
    val current = signatureDocumentViewportBounds(viewport, width, height, margin)
    return signatureDocumentViewportBounds(
        current.viewport.copy(
            panX = current.viewport.panX + (deltaX.takeIf(Float::isFinite) ?: 0f) / current.basePage.width,
            panY = current.viewport.panY + (deltaY.takeIf(Float::isFinite) ?: 0f) / current.basePage.height,
        ),
        width, height, margin,
    ).viewport
}

/** Amplia ao redor da assinatura visível; sem ela, mantém o centro da região que já estava na tela. */
internal fun zoomSignatureDocumentViewport(
    viewport: SignatureDocumentViewport,
    zoom: Float,
    width: Float,
    height: Float,
    margin: Float,
    focus: PdfPoint? = null,
): SignatureDocumentViewport {
    val current = signatureDocumentViewportBounds(viewport, width, height, margin)
    val target = (zoom.takeIf(Float::isFinite) ?: current.viewport.zoom).coerceIn(1f, 3f)
    val visibleFocus = focus?.takeIf {
        it.x.isFinite() && it.y.isFinite() &&
            current.page.left + it.x * current.page.width in 0f..width &&
            current.page.top + it.y * current.page.height in 0f..height
    }
    val anchor = visibleFocus ?: PdfPoint(
        (width / 2f - current.page.left) / current.page.width,
        (height / 2f - current.page.top) / current.page.height,
    )
    return signatureDocumentViewportBounds(
        current.viewport.copy(
            zoom = target,
            panX = current.viewport.panX + (anchor.x - .5f) * (current.viewport.zoom - target),
            panY = current.viewport.panY + (anchor.y - .5f) * (current.viewport.zoom - target),
        ),
        width, height, margin,
    ).viewport
}
