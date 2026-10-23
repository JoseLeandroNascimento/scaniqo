package com.joseleandro.scaniqo.ui.documentEditor.util

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import com.joseleandro.scaniqo.domain.model.PageAlignment
import com.joseleandro.scaniqo.domain.model.PdfPageLayout
import com.joseleandro.scaniqo.ui.documentEditor.model.DocumentLayoutControl
import com.joseleandro.scaniqo.ui.documentEditor.model.DocumentLayoutHandle
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.roundToInt


/** Copia somente a geometria; filtros e correções continuam próprios de cada página. */
internal fun PdfPageLayout.withLayoutAdjustmentsFrom(draft: PdfPageLayout): PdfPageLayout = copy(
    alignment = draft.alignment,
    scaleMode = draft.scaleMode,
    rotationQuarterTurns = draft.rotationQuarterTurns,
    contentScale = draft.contentScale,
)

/** As cinco zonas têm um centro generoso, evitando trocar o alinhamento por pequenos movimentos. */
internal fun documentLayoutAlignmentAt(x: Float, y: Float): PageAlignment {
    val dx = x - .5f
    val dy = y - .5f
    return when {
        !x.isFinite() || !y.isFinite() -> PageAlignment.CENTER
        abs(dx) <= .16f && abs(dy) <= .16f -> PageAlignment.CENTER
        abs(dx) > abs(dy) -> if (dx < 0f) PageAlignment.LEFT else PageAlignment.RIGHT
        dy < 0f -> PageAlignment.TOP
        else -> PageAlignment.BOTTOM
    }
}

internal fun documentLayoutScale(current: Float, zoom: Float): Float {
    if (!current.isFinite()) return 1f
    if (!zoom.isFinite() || zoom <= 0f) return current.coerceIn(.25f, 2f)
    return (current * zoom).coerceIn(.25f, 2f)
}

/** Acumula os pequenos deltas do gesto e encaixa a rotação em quartos de volta. */
internal class DocumentLayoutRotationGesture {
    private var remainder = 0f

    fun reset() {
        remainder = 0f
    }

    fun turn(deltaDegrees: Float): Int {
        if (!deltaDegrees.isFinite()) return 0
        remainder += deltaDegrees
        val turns = (remainder / 90f).roundToInt()
        remainder -= turns * 90f
        return turns
    }
}

internal fun documentLayoutRotation(current: Int, turns: Int): Int = ((current + turns) % 4 + 4) % 4

/** Projeta o arraste sobre a diagonal, preservando proporções e impedindo inversão ao cruzar o centro. */
internal fun documentLayoutHandleScale(
    initialScale: Float,
    drag: Offset,
    paperSize: IntSize,
    fromTopLeft: Boolean,
): Float {
    if (!initialScale.isFinite() || paperSize.width <= 0 || paperSize.height <= 0 || !drag.x.isFinite() || !drag.y.isFinite()) {
        return documentLayoutScale(initialScale, 1f)
    }
    val width = paperSize.width.toFloat()
    val height = paperSize.height.toFloat()
    val direction = if (fromTopLeft) -1f else 1f
    val factor =
        1f + direction * 2f * (drag.x * width + drag.y * height) / (width * width + height * height)
    return (initialScale * factor).coerceIn(.25f, 2f)
}

/** Cruza a fronteira entre -180° e 180° sem produzir um salto de uma volta inteira. */
internal fun documentLayoutHandleRotation(
    previous: Offset,
    current: Offset,
    center: Offset
): Float {
    val before = previous - center
    val after = current - center
    if (!before.x.isFinite() || !before.y.isFinite() || !after.x.isFinite() || !after.y.isFinite()) return 0f
    if (before.getDistanceSquared() < 1f || after.getDistanceSquared() < 1f) return 0f
    val delta = (atan2(after.y, after.x) - atan2(before.y, before.x)) * (180f / PI.toFloat())
    return (delta + 540f) % 360f - 180f
}

/** Cada alça usa um dedo e mantém seu ponto inicial enquanto a prévia muda. */
@Composable
internal fun Modifier.documentLayoutHandleGesture(
    pageId: String,
    handle: DocumentLayoutHandle,
    layout: PdfPageLayout,
    paperSize: IntSize,
    enabled: Boolean,
    onLayoutChange: (PdfPageLayout) -> Unit,
): Modifier {
    val latestLayout by rememberUpdatedState(layout)
    val latestOnChange by rememberUpdatedState(onLayoutChange)
    return pointerInput(pageId, handle, paperSize, enabled) {
        if (!enabled) return@pointerInput
        var position = latestLayout.alignment.dragOrigin()
        var initialScale = latestLayout.contentScale
        var drag = Offset.Zero
        val rotationOrigin = Offset(paperSize.width / 2f, 0f)
        var previous = rotationOrigin
        val center = Offset(paperSize.width / 2f, paperSize.height / 2f)
        val rotation = DocumentLayoutRotationGesture()
        detectDragGestures(
            onDragStart = {
                position = latestLayout.alignment.dragOrigin()
                initialScale = latestLayout.contentScale
                drag = Offset.Zero
                previous = rotationOrigin
                rotation.reset()
            },
        ) { change, amount ->
            if (paperSize.width <= 0 || paperSize.height <= 0) return@detectDragGestures
            change.consume()
            when (handle) {
                DocumentLayoutHandle.MOVE -> {
                    position = Offset(
                        (position.x + amount.x / paperSize.width).coerceIn(0f, 1f),
                        (position.y + amount.y / paperSize.height).coerceIn(0f, 1f),
                    )
                    latestOnChange(
                        latestLayout.copy(
                            alignment = documentLayoutAlignmentAt(
                                position.x,
                                position.y
                            )
                        )
                    )
                }

                DocumentLayoutHandle.ROTATE -> {
                    val current = previous + amount
                    val turns =
                        rotation.turn(documentLayoutHandleRotation(previous, current, center))
                    previous = current
                    if (turns != 0) {
                        latestOnChange(
                            latestLayout.copy(
                                rotationQuarterTurns = documentLayoutRotation(
                                    latestLayout.rotationQuarterTurns,
                                    turns
                                ),
                            )
                        )
                    }
                }

                DocumentLayoutHandle.SCALE_TOP_LEFT, DocumentLayoutHandle.SCALE_BOTTOM_RIGHT -> {
                    drag += amount
                    latestOnChange(
                        latestLayout.copy(
                            contentScale = documentLayoutHandleScale(
                                initialScale,
                                drag,
                                paperSize,
                                handle == DocumentLayoutHandle.SCALE_TOP_LEFT,
                            ),
                        )
                    )
                }
            }
        }
    }
}

private fun PageAlignment.dragOrigin(): Offset = when (this) {
    PageAlignment.CENTER -> Offset(.5f, .5f)
    PageAlignment.TOP -> Offset(.5f, .12f)
    PageAlignment.BOTTOM -> Offset(.5f, .88f)
    PageAlignment.LEFT -> Offset(.12f, .5f)
    PageAlignment.RIGHT -> Offset(.88f, .5f)
}

/** O detector não reinicia a cada prévia; a folha mantém o gesto até o usuário soltar os dedos. */
@Composable
internal fun Modifier.documentLayoutGestures(
    pageId: String,
    control: DocumentLayoutControl,
    layout: PdfPageLayout,
    enabled: Boolean,
    onLayoutChange: (PdfPageLayout) -> Unit,
): Modifier {
    val latestLayout by rememberUpdatedState(layout)
    val latestOnChange by rememberUpdatedState(onLayoutChange)
    val rotation = remember(pageId, control, enabled) { DocumentLayoutRotationGesture() }
    // Observa o primeiro toque sem consumi-lo, descartando resíduos entre gestos separados.
    return pointerInput(pageId, control, enabled) {
        if (enabled && control == DocumentLayoutControl.ROTATION) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                rotation.reset()
            }
        }
    }.pointerInput(pageId, control, enabled) {
        if (!enabled) return@pointerInput
        when (control) {
            DocumentLayoutControl.POSITION -> {
                var position = latestLayout.alignment.dragOrigin()
                detectDragGestures(
                    onDragStart = { position = latestLayout.alignment.dragOrigin() },
                ) { change, amount ->
                    if (size.width > 0 && size.height > 0) {
                        change.consume()
                        position = Offset(
                            (position.x + amount.x / size.width).coerceIn(0f, 1f),
                            (position.y + amount.y / size.height).coerceIn(0f, 1f),
                        )
                        latestOnChange(
                            latestLayout.copy(
                                alignment = documentLayoutAlignmentAt(position.x, position.y),
                            )
                        )
                    }
                }
            }

            DocumentLayoutControl.ROTATION -> {
                detectTransformGestures { _, _, _, angle ->
                    val turns = rotation.turn(angle)
                    if (turns != 0) latestOnChange(
                        latestLayout.copy(
                            rotationQuarterTurns = documentLayoutRotation(
                                latestLayout.rotationQuarterTurns,
                                turns
                            ),
                        )
                    )
                }
            }

            DocumentLayoutControl.SIZE -> {
                detectTransformGestures { _, _, zoom, _ ->
                    if (zoom != 1f) latestOnChange(
                        latestLayout.copy(
                            contentScale = documentLayoutScale(latestLayout.contentScale, zoom),
                        )
                    )
                }
            }
        }
    }
}
