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
import com.joseleandro.scaniqo.data.pdf.PdfPageGeometry
import com.joseleandro.scaniqo.domain.model.PageAlignment
import com.joseleandro.scaniqo.domain.model.PdfContentPosition
import com.joseleandro.scaniqo.domain.model.PdfPageLayout
import com.joseleandro.scaniqo.domain.model.effectiveContentPosition
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
    contentPosition = draft.contentPosition,
    edgeToEdge = draft.edgeToEdge,
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

/** Converte deslocamento na tela em deslocamento real na folha, incluindo recorte e rotação. */
internal fun documentLayoutDragged(
    layout: PdfPageLayout,
    drag: Offset,
    paperSize: IntSize,
    sourceWidth: Float,
    sourceHeight: Float,
): PdfPageLayout {
    if (paperSize.width <= 0 || paperSize.height <= 0 || !drag.x.isFinite() || !drag.y.isFinite() ||
        sourceWidth <= 0f || sourceHeight <= 0f || !sourceWidth.isFinite() || !sourceHeight.isFinite()
    ) return layout
    val travel = PdfPageGeometry.contentTravel(sourceWidth, sourceHeight, layout)
    val current = layout.effectiveContentPosition()
    val dx = drag.x / paperSize.width * PdfPageGeometry.WIDTH
    val dy = drag.y / paperSize.height * PdfPageGeometry.HEIGHT
    val next = PdfContentPosition(
        horizontal = if (abs(travel.horizontal) < .01f) current.horizontal
            else (current.horizontal + dx / travel.horizontal).coerceIn(0f, 1f),
        vertical = if (abs(travel.vertical) < .01f) current.vertical
            else (current.vertical + dy / travel.vertical).coerceIn(0f, 1f),
    )
    return if (next == current) layout else layout.copy(contentPosition = next)
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
    sourceWidth: Float,
    sourceHeight: Float,
    enabled: Boolean,
    onLayoutChange: (PdfPageLayout) -> Unit,
    onGestureStart: () -> Unit = {},
    onGestureEnd: () -> Unit = {},
): Modifier {
    val latestLayout by rememberUpdatedState(layout)
    val latestOnChange by rememberUpdatedState(onLayoutChange)
    val latestOnGestureStart by rememberUpdatedState(onGestureStart)
    val latestOnGestureEnd by rememberUpdatedState(onGestureEnd)
    val latestSourceWidth by rememberUpdatedState(sourceWidth)
    val latestSourceHeight by rememberUpdatedState(sourceHeight)
    val sourceReady = sourceWidth.isFinite() && sourceHeight.isFinite() &&
        sourceWidth > 0f && sourceHeight > 0f
    val dragReady = handle != DocumentLayoutHandle.MOVE || sourceReady
    return pointerInput(pageId, handle, enabled) {
        if (enabled) awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            latestOnGestureStart()
            do {
                val event = awaitPointerEvent(PointerEventPass.Final)
                if (event.changes.none { it.pressed }) break
            } while (true)
            latestOnGestureEnd()
        }
    }.pointerInput(pageId, handle, paperSize, enabled, dragReady) {
        if (!enabled || !dragReady) return@pointerInput
        var workingLayout = latestLayout
        var initialScale = latestLayout.contentScale
        var gestureSourceWidth = 0f
        var gestureSourceHeight = 0f
        var drag = Offset.Zero
        val rotationOrigin = Offset(paperSize.width / 2f, 0f)
        var previous = rotationOrigin
        val center = Offset(paperSize.width / 2f, paperSize.height / 2f)
        val rotation = DocumentLayoutRotationGesture()
        detectDragGestures(
            onDragStart = {
                workingLayout = latestLayout
                initialScale = latestLayout.contentScale
                // Congela as dimensões reais até o dedo sair, mesmo se chegar uma captura refinada.
                gestureSourceWidth = latestSourceWidth
                gestureSourceHeight = latestSourceHeight
                drag = Offset.Zero
                previous = rotationOrigin
                rotation.reset()
            },
        ) { change, amount ->
            if (paperSize.width <= 0 || paperSize.height <= 0) return@detectDragGestures
            change.consume()
            when (handle) {
                DocumentLayoutHandle.MOVE -> {
                    if (gestureSourceWidth <= 0f || gestureSourceHeight <= 0f) return@detectDragGestures
                    workingLayout = documentLayoutDragged(
                        workingLayout, amount, paperSize, gestureSourceWidth, gestureSourceHeight,
                    )
                    latestOnChange(workingLayout)
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

/** O detector não reinicia a cada prévia; a folha mantém o gesto até o usuário soltar os dedos. */
@Composable
internal fun Modifier.documentLayoutGestures(
    pageId: String,
    control: DocumentLayoutControl,
    layout: PdfPageLayout,
    sourceWidth: Float,
    sourceHeight: Float,
    enabled: Boolean,
    onLayoutChange: (PdfPageLayout) -> Unit,
    onGestureStart: () -> Unit = {},
    onGestureEnd: () -> Unit = {},
): Modifier {
    val latestLayout by rememberUpdatedState(layout)
    val latestOnChange by rememberUpdatedState(onLayoutChange)
    val latestOnGestureStart by rememberUpdatedState(onGestureStart)
    val latestOnGestureEnd by rememberUpdatedState(onGestureEnd)
    val latestSourceWidth by rememberUpdatedState(sourceWidth)
    val latestSourceHeight by rememberUpdatedState(sourceHeight)
    val sourceReady = sourceWidth.isFinite() && sourceHeight.isFinite() &&
        sourceWidth > 0f && sourceHeight > 0f
    val dragReady = control != DocumentLayoutControl.POSITION || sourceReady
    val rotation = remember(pageId, control, enabled) { DocumentLayoutRotationGesture() }
    // Observa o primeiro toque sem consumi-lo, descartando resíduos entre gestos separados.
    return pointerInput(pageId, control, enabled) {
        if (enabled) awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            latestOnGestureStart()
            do {
                val event = awaitPointerEvent(PointerEventPass.Final)
                if (event.changes.none { it.pressed }) break
            } while (true)
            latestOnGestureEnd()
        }
    }.pointerInput(pageId, control, enabled) {
        if (enabled && control == DocumentLayoutControl.ROTATION) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                rotation.reset()
            }
        }
    }.pointerInput(pageId, control, enabled, dragReady) {
        if (!enabled || !dragReady) return@pointerInput
        when (control) {
            DocumentLayoutControl.MARGINS -> Unit
            DocumentLayoutControl.POSITION -> {
                var workingLayout = latestLayout
                var gestureSourceWidth = 0f
                var gestureSourceHeight = 0f
                detectDragGestures(
                    onDragStart = {
                        workingLayout = latestLayout
                        gestureSourceWidth = latestSourceWidth
                        gestureSourceHeight = latestSourceHeight
                    },
                ) { change, amount ->
                    if (size.width > 0 && size.height > 0 &&
                        gestureSourceWidth > 0f && gestureSourceHeight > 0f
                    ) {
                        change.consume()
                        workingLayout = documentLayoutDragged(
                            workingLayout, amount, size, gestureSourceWidth, gestureSourceHeight,
                        )
                        latestOnChange(workingLayout)
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
