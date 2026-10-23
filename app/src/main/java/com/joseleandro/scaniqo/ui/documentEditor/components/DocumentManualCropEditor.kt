package com.joseleandro.scaniqo.ui.documentEditor.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import com.joseleandro.scaniqo.ui.documentEditor.util.resizeManualCropEdge
import com.joseleandro.scaniqo.ui.documentEditor.util.transformManualCrop
import kotlin.math.abs
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.joseleandro.scaniqo.R
import com.joseleandro.scaniqo.domain.model.PdfCropBounds
import com.joseleandro.scaniqo.domain.model.PdfPageSpec
import com.joseleandro.scaniqo.domain.model.PdfPoint
import com.joseleandro.scaniqo.ui.documentEditor.state.DocumentCaptureSourceState
import com.joseleandro.scaniqo.ui.documentEditor.util.corners
import com.joseleandro.scaniqo.ui.documentEditor.util.resizeManualCrop
import com.joseleandro.scaniqo.ui.theme.ScaniqoTheme
import kotlin.math.roundToInt

@Composable
internal fun DocumentManualCropEditor(
    page: PdfPageSpec,
    crop: PdfCropBounds,
    enabled: Boolean,
    onChange: (PdfCropBounds) -> Unit,
    pageNumber: Int,
    modifier: Modifier = Modifier,
    sourceState: DocumentCaptureSourceState = DocumentCaptureSourceState(),
    onRetry: () -> Unit = {},
) {
    var magnifier by remember(page.id, sourceState.source?.pixels) {
        mutableStateOf<CaptureMagnifierFocus?>(null)
    }
    val corners = crop.corners()
    DocumentSourceSelectionEditor(
        page, corners, enabled, pageNumber, modifier, sourceState, onRetry, magnifier,
    ) { size ->
        CropSelectionBody(page.id, crop, size, enabled, onChange)
        repeat(4) { edge ->
            CropEdge(edge, crop, size, enabled, onChange, sourceState.source?.pixels) { magnifier = it }
        }
        corners.forEachIndexed { index, point ->
            CropCorner(
                index, point, crop, size, enabled, onChange, sourceState.source?.pixels,
                onMagnifierChange = { magnifier = it },
            )
        }
    }
}

@Composable
private fun CropSelectionBody(
    pageId: String,
    crop: PdfCropBounds,
    imageSize: IntSize,
    enabled: Boolean,
    onChange: (PdfCropBounds) -> Unit,
) {
    val latestCrop by rememberUpdatedState(crop)
    val latestOnChange by rememberUpdatedState(onChange)
    val density = LocalDensity.current
    val minimumPixels = with(density) { 48.dp.toPx() }
    val guide = MaterialTheme.colorScheme.primary.copy(alpha = .35f)
    Canvas(
        Modifier.absoluteOffset(24.dp, 24.dp)
            .width(with(density) { imageSize.width.toDp() })
            .height(with(density) { imageSize.height.toDp() })
            .pointerInput(pageId, imageSize, enabled) {
                if (!enabled || imageSize.width <= 0 || imageSize.height <= 0) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown()
                    var current = latestCrop
                    val startsInside = down.position.x / imageSize.width in current.left..current.right &&
                        down.position.y / imageSize.height in current.top..current.bottom
                    var transforming = false
                    var totalPan = Offset.Zero
                    var totalZoom = 1f
                    do {
                        val event = awaitPointerEvent()
                        if (event.changes.any { it.isConsumed }) break
                        val twoFingers = event.changes.count { it.pressed && it.previousPressed } >= 2
                        if (!transforming && !startsInside && !twoFingers) {
                            // Um arraste fora da seleção não deve se acumular antes da pinça.
                            totalPan = Offset.Zero
                            totalZoom = 1f
                            continue
                        }
                        val pan = event.calculatePan()
                        val zoom = event.calculateZoom()
                        totalPan += pan
                        totalZoom *= zoom
                        if (!transforming) {
                            val zoomMotion = abs(1f - totalZoom) * minOf(imageSize.width, imageSize.height) / 2f
                            transforming = totalPan.getDistance() > viewConfiguration.touchSlop ||
                                zoomMotion > viewConfiguration.touchSlop
                        }
                        if (transforming) {
                            current = transformManualCrop(
                                current, totalPan.x / imageSize.width, totalPan.y / imageSize.height, totalZoom,
                                minimumPixels / imageSize.width, minimumPixels / imageSize.height,
                            )
                            latestOnChange(current)
                            totalPan = Offset.Zero
                            totalZoom = 1f
                            event.changes.forEach { it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
    ) {
        val left = crop.left * size.width
        val right = crop.right * size.width
        val top = crop.top * size.height
        val bottom = crop.bottom * size.height
        for (third in 1..2) {
            val x = left + (right - left) * third / 3f
            val y = top + (bottom - top) * third / 3f
            drawLine(guide, Offset(x, top), Offset(x, bottom), 1.dp.toPx())
            drawLine(guide, Offset(left, y), Offset(right, y), 1.dp.toPx())
        }
    }
}

private fun PdfCropBounds.edgePoint(edge: Int): PdfPoint = when (edge) {
    0 -> PdfPoint((left + right) / 2f, top)
    1 -> PdfPoint(right, (top + bottom) / 2f)
    2 -> PdfPoint((left + right) / 2f, bottom)
    else -> PdfPoint(left, (top + bottom) / 2f)
}

@Composable
private fun CropEdge(
    edge: Int,
    crop: PdfCropBounds,
    imageSize: IntSize,
    enabled: Boolean,
    onChange: (PdfCropBounds) -> Unit,
    sourceKey: Any?,
    onMagnifierChange: (CaptureMagnifierFocus?) -> Unit,
) {
    val point = crop.edgePoint(edge)
    val minimumPixels = with(LocalDensity.current) { 48.dp.toPx() }
    val horizontal = edge % 2 == 0
    Box(
        Modifier.absoluteOffset {
            IntOffset((point.x * imageSize.width).roundToInt(), (point.y * imageSize.height).roundToInt())
        }.size(48.dp).captureSelectionDrag(
            index = edge + 4,
            imageSize = imageSize,
            enabled = enabled,
            sourceKey = sourceKey,
            current = crop,
            pointOf = { it.edgePoint(edge) },
            move = { current, dx, dy ->
                resizeManualCropEdge(current, edge, dx, dy, minimumPixels / imageSize.width, minimumPixels / imageSize.height)
            },
            onChange = onChange,
            onMagnifierChange = onMagnifierChange,
        ),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            Modifier.width(if (horizontal) 28.dp else 6.dp).height(if (horizontal) 6.dp else 28.dp),
            shape = CircleShape,
            color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            shadowElevation = 2.dp,
        ) {}
    }
}

@Composable
private fun CropCorner(
    index: Int,
    point: PdfPoint,
    crop: PdfCropBounds,
    size: IntSize,
    enabled: Boolean,
    onChange: (PdfCropBounds) -> Unit,
    sourceKey: Any?,
    onMagnifierChange: (CaptureMagnifierFocus?) -> Unit,
) {
    val latestCrop by rememberUpdatedState(crop)
    val latestOnChange by rememberUpdatedState(onChange)
    val minimumPixels = with(LocalDensity.current) { 48.dp.toPx() }
    fun move(dx: Float, dy: Float): Boolean {
        if (!enabled || size.width <= 0 || size.height <= 0) return false
        val changed = resizeManualCrop(
            latestCrop, index, dx, dy,
            minimumWidth = minimumPixels / size.width,
            minimumHeight = minimumPixels / size.height,
        )
        if (changed == latestCrop) return false
        latestOnChange(changed)
        return true
    }
    val label = stringResource(when (index) {
        0 -> R.string.quality_corner_top_left
        1 -> R.string.quality_corner_top_right
        2 -> R.string.quality_corner_bottom_right
        else -> R.string.quality_corner_bottom_left
    })
    val left = stringResource(R.string.quality_corner_left)
    val right = stringResource(R.string.quality_corner_right)
    val up = stringResource(R.string.quality_corner_up)
    val down = stringResource(R.string.quality_corner_down)
    Box(
        Modifier
            .absoluteOffset { IntOffset((point.x * size.width).roundToInt(), (point.y * size.height).roundToInt()) }
            .size(48.dp)
            .semantics {
                contentDescription = label
                customActions = if (!enabled) emptyList() else listOf(
                    CustomAccessibilityAction(left) { move(-.02f, 0f) },
                    CustomAccessibilityAction(right) { move(.02f, 0f) },
                    CustomAccessibilityAction(up) { move(0f, -.02f) },
                    CustomAccessibilityAction(down) { move(0f, .02f) },
                )
            }
            .captureSelectionDrag(
                index = index,
                imageSize = size,
                enabled = enabled,
                sourceKey = sourceKey,
                current = crop,
                pointOf = { it.corners()[index] },
                move = { current, dx, dy ->
                    resizeManualCrop(
                        current, index, dx, dy,
                        minimumWidth = minimumPixels / size.width,
                        minimumHeight = minimumPixels / size.height,
                    )
                },
                onChange = onChange,
                onMagnifierChange = onMagnifierChange,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.size(22.dp),
            shape = CircleShape,
            color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            shadowElevation = 2.dp,
        ) {
            Box(Modifier.padding(6.dp).background(Color.White, CircleShape))
        }
    }
}

@Preview(name = "Recorte retangular - Árabe", locale = "ar", widthDp = 390, heightDp = 600, showBackground = true)
@Preview(name = "Recorte retangular", widthDp = 390, heightDp = 600, showBackground = true)
@Preview(name = "Recorte retangular fonte ampliada", widthDp = 360, heightDp = 600, fontScale = 1.3f)
@Composable
private fun DocumentManualCropPreview() {
    ScaniqoTheme {
        Surface {
            DocumentManualCropEditor(
                PdfPageSpec("page", "preview", 0),
                PdfCropBounds(.15f, .1f, .85f, .9f),
                enabled = true,
                onChange = {},
                pageNumber = 1,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
