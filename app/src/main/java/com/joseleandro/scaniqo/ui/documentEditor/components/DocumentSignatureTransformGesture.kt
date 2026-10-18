package com.joseleandro.scaniqo.ui.documentEditor.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import com.joseleandro.scaniqo.domain.model.PdfSignaturePlacement
import com.joseleandro.scaniqo.ui.documentEditor.util.signatureContainsPointer
import com.joseleandro.scaniqo.ui.documentEditor.util.resizeSignatureAroundCenter
import com.joseleandro.scaniqo.ui.documentEditor.util.transformSignaturePlacement
import kotlin.math.PI
import kotlin.math.abs

/** Arraste e pinça compartilham o estado do gesto e respeitam os alvos exclusivos das alças. */
internal fun Modifier.signatureTransformGestures(
    placement: PdfSignaturePlacement,
    pageBounds: Rect,
    enabled: Boolean,
    excludedTargets: List<Rect>,
    onChange: (PdfSignaturePlacement, Offset, DocumentSignatureGestureKind) -> Unit,
    onFinish: () -> Unit,
    onViewportPan: ((Offset) -> Unit)? = null,
    minimumTargetSize: Float = 0f,
    viewportGestureKey: Any = pageBounds,
): Modifier = composed {
    val current by rememberUpdatedState(placement)
    val targets by rememberUpdatedState(excludedTargets)
    val change by rememberUpdatedState(onChange)
    val finish by rememberUpdatedState(onFinish)
    val currentBounds by rememberUpdatedState(pageBounds)
    val panViewport by rememberUpdatedState(onViewportPan)
    // Rect.width/height podem variar um ULP durante o pan; a chave vem da escala e janela originais.
    pointerInput(placement.id, placement.imageUri, viewportGestureKey, enabled) {
        if (!enabled || pageBounds.width <= 0f || pageBounds.height <= 0f) return@pointerInput
        awaitEachGesture {
            var active = false
            var panningDocument = false
            try {
                val first = awaitFirstDown(requireUnconsumed = false)
                if (targets.any { it.contains(first.position) }) return@awaitEachGesture
                val bounds = currentBounds
                var value = current
                panningDocument = panViewport != null &&
                    !signatureContainsPointer(value, bounds, first.position, minimumTargetSize)
                var totalPan = Offset.Zero
                var totalZoom = 1f
                var totalRotation = 0f
                do {
                    val event = awaitPointerEvent()
                    if (event.changes.any { it.isConsumed }) break
                    val pan = event.calculatePan()
                    val zoom = event.calculateZoom()
                    val rotation = event.calculateRotation()
                    if (!active) {
                        totalPan += pan
                        totalZoom *= zoom
                        totalRotation += rotation
                        val radius = event.calculateCentroidSize(useCurrent = false)
                        active = totalPan.getDistance() > viewConfiguration.touchSlop ||
                            (!panningDocument && (
                                abs(1f - totalZoom) * radius > viewConfiguration.touchSlop ||
                                    abs(totalRotation * PI.toFloat() * radius / 180f) > viewConfiguration.touchSlop
                                ))
                    }
                    if (active && panningDocument && pan != Offset.Zero) {
                        panViewport?.invoke(pan)
                        event.changes.forEach { it.consume() }
                    } else if (active && !panningDocument && (pan != Offset.Zero || zoom != 1f || rotation != 0f)) {
                        val sized = resizeSignatureAroundCenter(
                            value, width = value.width * zoom,
                            rotationDegrees = value.rotationDegrees + rotation,
                        )
                        value = transformSignaturePlacement(
                            sized,
                            x = sized.x + pan.x / bounds.width,
                            y = sized.y + pan.y / bounds.height,
                        )
                        change(
                            value, event.calculateCentroid(useCurrent = false),
                            when {
                                zoom != 1f -> DocumentSignatureGestureKind.RESIZE
                                rotation != 0f -> DocumentSignatureGestureKind.ROTATE
                                else -> DocumentSignatureGestureKind.MOVE
                            },
                        )
                        event.changes.forEach { it.consume() }
                    }
                } while (event.changes.any { it.pressed })
            } finally {
                if (active && !panningDocument) finish()
            }
        }
    }
}
