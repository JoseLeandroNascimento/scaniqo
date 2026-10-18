package com.joseleandro.scaniqo.ui.documentEditor.components

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.joseleandro.scaniqo.domain.model.PdfPoint

/** A posição da mira segue o limite aplicado; a posição do dedo evita que a lupa o cubra. */
internal data class CaptureMagnifierFocus(val point: PdfPoint, val pointer: PdfPoint)

/** Compartilha o ciclo do gesto, inclusive cancelamento e deltas entre recomposições. */
internal fun <T> Modifier.captureSelectionDrag(
    index: Int,
    imageSize: IntSize,
    enabled: Boolean,
    sourceKey: Any?,
    current: T,
    pointOf: (T) -> PdfPoint,
    move: (T, Float, Float) -> T,
    onChange: (T) -> Unit,
    onMagnifierChange: (CaptureMagnifierFocus?) -> Unit,
): Modifier = composed {
    val latestCurrent by rememberUpdatedState(current)
    val latestPointOf by rememberUpdatedState(pointOf)
    val latestMove by rememberUpdatedState(move)
    val latestOnChange by rememberUpdatedState(onChange)
    val latestOnMagnifierChange by rememberUpdatedState(onMagnifierChange)
    val handleRadius = with(LocalDensity.current) { 24.dp.toPx() }
    pointerInput(index, imageSize, enabled, sourceKey, handleRadius) {
        if (enabled && imageSize.width > 0 && imageSize.height > 0) {
            var draft = latestCurrent
            var pointer = Offset.Zero
            var firstDrag = true
            fun showMagnifier() {
                latestOnMagnifierChange(
                    CaptureMagnifierFocus(
                        latestPointOf(draft),
                        PdfPoint(pointer.x / imageSize.width, pointer.y / imageSize.height),
                    ),
                )
            }
            try {
                detectDragGestures(
                    onDragStart = { position ->
                        draft = latestCurrent
                        firstDrag = true
                        val point = latestPointOf(draft)
                        pointer = Offset(
                            point.x * imageSize.width + position.x - handleRadius,
                            point.y * imageSize.height + position.y - handleRadius,
                        )
                        showMagnifier()
                    },
                    onDragEnd = { latestOnMagnifierChange(null) },
                    onDragCancel = { latestOnMagnifierChange(null) },
                    onDrag = { change, amount ->
                        change.consume()
                        // onDragStart já recebe a posição após cruzar o touch slop.
                        if (firstDrag) firstDrag = false else pointer += amount
                        val changed = latestMove(
                            draft, amount.x / imageSize.width, amount.y / imageSize.height,
                        )
                        if (changed != draft) latestOnChange(changed)
                        draft = changed
                        showMagnifier()
                    },
                )
            } finally {
                // Também cobre troca de fonte, bloqueio e remoção do editor da composição.
                latestOnMagnifierChange(null)
            }
        }
    }
}
