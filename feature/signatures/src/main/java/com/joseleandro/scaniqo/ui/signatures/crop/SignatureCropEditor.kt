package com.joseleandro.scaniqo.ui.signatures.crop

import com.joseleandro.scaniqo.feature.signatures.R

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joseleandro.scaniqo.domain.model.PdfSignatureCrop
import com.joseleandro.scaniqo.ui.signatures.SignatureImage
import com.joseleandro.scaniqo.ui.theme.ScaniqoTheme
import kotlin.math.roundToInt
import android.content.res.Configuration

@Composable
internal fun SignatureCropEditor(
    uri: String,
    aspectRatio: Float,
    crop: PdfSignatureCrop,
    onCropChange: (PdfSignatureCrop) -> Unit,
    onImageReady: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onGestureActiveChange: (Boolean) -> Unit = {},
    enableImageGestures: Boolean = false,
    resetKey: Int = 0,
    enabled: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val inspection = LocalInspectionMode.current
    var imageAspect by remember(uri, inspection) {
        mutableStateOf<Float?>(if (inspection) 1200f / 360f else null)
    }
    var activeHandle by remember { mutableStateOf<SignatureCropHandle?>(null) }
    var viewport by remember(uri, enableImageGestures, resetKey) { mutableStateOf(SignatureCropViewport()) }
    val currentCrop by rememberUpdatedState(crop)
    val currentOnChange by rememberUpdatedState(onCropChange)
    val currentOnReady by rememberUpdatedState(onImageReady)
    val currentOnGesture by rememberUpdatedState(onGestureActiveChange)
    val aspect = imageAspect ?: aspectRatio.takeIf { it.isFinite() && it > 0f } ?: 3f
    val imageReady = imageAspect != null
    val ready = imageReady && enabled
    val selectionLabel = stringResource(R.string.signature_crop_selection)
    val selectionSize = stringResource(
        R.string.signature_crop_selection_size,
        ((crop.right - crop.left) * 100f).roundToInt(),
        ((crop.bottom - crop.top) * 100f).roundToInt(),
    )
    val inward = stringResource(R.string.signature_crop_inward)
    val outward = stringResource(R.string.signature_crop_outward)
    val edgeLabels = listOf(
        stringResource(R.string.signature_crop_left),
        stringResource(R.string.signature_crop_top),
        stringResource(R.string.signature_crop_right),
        stringResource(R.string.signature_crop_bottom),
    )
    val movementLabels = listOf(
        stringResource(R.string.signature_crop_move_left),
        stringResource(R.string.signature_crop_move_right),
        stringResource(R.string.signature_crop_move_up),
        stringResource(R.string.signature_crop_move_down),
    )
    val resetLabel = stringResource(R.string.signature_crop_reset)
    fun adjust(handle: SignatureCropHandle, x: Float, y: Float): Boolean {
        if (!ready) return false
        currentOnChange(SignatureCropGeometry.drag(currentCrop, handle, x, y))
        return true
    }
    BoxWithConstraints(
        modifier.clip(RoundedCornerShape(20.dp)).background(colors.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        val availableWidth = (maxWidth - 48.dp).coerceAtLeast(1.dp)
        val availableHeight = (maxHeight - 48.dp).coerceAtLeast(1.dp)
        val imageWidth = minOf(availableWidth, availableHeight * aspect)
        val imageHeight = imageWidth / aspect
        val baseBounds = with(density) {
            Rect(
                Offset((maxWidth - imageWidth).toPx() / 2f, (maxHeight - imageHeight).toPx() / 2f),
                Size(imageWidth.toPx(), imageHeight.toPx()),
            )
        }
        fun imageBounds(view: SignatureCropViewport) = Rect(
            baseBounds.left + view.offsetX * baseBounds.width,
            baseBounds.top + view.offsetY * baseBounds.height,
            baseBounds.left + (view.offsetX + view.scale) * baseBounds.width,
            baseBounds.top + (view.offsetY + view.scale) * baseBounds.height,
        )
        val bounds = imageBounds(viewport)
        val touchRadius = with(density) { 24.dp.toPx() }
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Color.White, bounds.topLeft, bounds.size)
            val square = 12.dp.toPx()
            clipRect(bounds.left, bounds.top, bounds.right, bounds.bottom) {
                val columns = (bounds.width / square).toInt() + 1
                val rows = (bounds.height / square).toInt() + 1
                for (row in 0..rows) {
                    for (column in 0..columns) {
                        if ((row + column) % 2 == 0) {
                            drawRect(
                                colors.outlineVariant.copy(alpha = .18f),
                                bounds.topLeft + Offset(column * square, row * square),
                                Size(square, square),
                            )
                        }
                    }
                }
            }
        }
        SignatureImage(
            uri = uri,
            modifier = Modifier.size(imageWidth, imageHeight).graphicsLayer {
                transformOrigin = TransformOrigin(0f, 0f)
                scaleX = viewport.scale
                scaleY = viewport.scale
                translationX = viewport.offsetX * baseBounds.width
                translationY = viewport.offsetY * baseBounds.height
            }.then(if (enableImageGestures) Modifier else Modifier.clearAndSetSemantics {}),
            onImageBitmapChanged = { image ->
                imageAspect = image?.let { it.width.toFloat() / it.height }
                currentOnReady(image != null)
            },
        )
        Canvas(
            Modifier.fillMaxSize().testTag("signature_crop_canvas")
                .semantics {
                    contentDescription = selectionLabel
                    stateDescription = selectionSize
                    if (!ready) {
                        disabled()
                    } else {
                        customActions = buildList {
                            val edges = listOf(
                                Triple(SignatureCropHandle.LEFT, .025f, 0f),
                                Triple(SignatureCropHandle.TOP, 0f, .025f),
                                Triple(SignatureCropHandle.RIGHT, -.025f, 0f),
                                Triple(SignatureCropHandle.BOTTOM, 0f, -.025f),
                            )
                            edges.forEachIndexed { index, (handle, x, y) ->
                                add(CustomAccessibilityAction("${edgeLabels[index]}: $inward") { adjust(handle, x, y) })
                                add(CustomAccessibilityAction("${edgeLabels[index]}: $outward") { adjust(handle, -x, -y) })
                            }
                            val steps = listOf(Offset(-.025f, 0f), Offset(.025f, 0f), Offset(0f, -.025f), Offset(0f, .025f))
                            steps.forEachIndexed { index, step ->
                                add(CustomAccessibilityAction(movementLabels[index]) { adjust(SignatureCropHandle.MOVE, step.x, step.y) })
                            }
                            add(CustomAccessibilityAction(resetLabel) {
                                viewport = SignatureCropViewport()
                                currentOnChange(SignatureCropGeometry.fullImage)
                                true
                            })
                        }
                    }
                }
                .onKeyEvent { event ->
                    if (!ready || event.type != KeyEventType.KeyDown) return@onKeyEvent false
                    val step = .01f
                    val handle = if (event.isShiftPressed) SignatureCropHandle.BOTTOM_RIGHT else SignatureCropHandle.MOVE
                    when (event.key) {
                        Key.DirectionLeft -> adjust(handle, -step, 0f)
                        Key.DirectionRight -> adjust(handle, step, 0f)
                        Key.DirectionUp -> adjust(handle, 0f, -step)
                        Key.DirectionDown -> adjust(handle, 0f, step)
                        Key.MoveHome -> {
                            viewport = SignatureCropViewport()
                            currentOnChange(SignatureCropGeometry.fullImage)
                            true
                        }
                        else -> false
                    }
                }
                .focusable(ready)
                .pointerInput(uri, ready, baseBounds, enableImageGestures, resetKey) {
                    if (!ready) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val start = currentCrop
                        val startViewport = viewport
                        val startBounds = imageBounds(startViewport)
                        val handle = SignatureCropGeometry.handleAt(
                            start,
                            down.position.x - startBounds.left,
                            down.position.y - startBounds.top,
                            startBounds.width,
                            startBounds.height,
                            touchRadius,
                        ) ?: if (enableImageGestures) SignatureCropHandle.MOVE else return@awaitEachGesture
                        var movingImage = enableImageGestures && handle == SignatureCropHandle.MOVE
                        var gestureCrop = start
                        var completed = false
                        down.consume()
                        activeHandle = handle
                        currentOnGesture(true)
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.changes.any { it.isConsumed }) break
                                if (enableImageGestures && event.changes.count { it.pressed } >= 2) movingImage = true
                                if (movingImage) {
                                    val pan = event.calculatePan()
                                    val focus = event.calculateCentroid(useCurrent = false)
                                    if (focus != Offset.Unspecified) {
                                        val transformed = viewport.transform(
                                            gestureCrop, event.calculateZoom(),
                                            pan.x / baseBounds.width, pan.y / baseBounds.height,
                                            (focus.x - baseBounds.left) / baseBounds.width,
                                            (focus.y - baseBounds.top) / baseBounds.height,
                                        )
                                        viewport = transformed.viewport
                                        gestureCrop = transformed.crop
                                        currentOnChange(gestureCrop)
                                    }
                                    event.changes.forEach { it.consume() }
                                } else {
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    val position = if (enableImageGestures) Offset(
                                        change.position.x.coerceIn(0f, size.width.toFloat()),
                                        change.position.y.coerceIn(0f, size.height.toFloat()),
                                    ) else change.position
                                    val delta = position - down.position
                                    gestureCrop = SignatureCropGeometry.drag(
                                        start, handle, delta.x / startBounds.width, delta.y / startBounds.height,
                                    )
                                    currentOnChange(gestureCrop)
                                    change.consume()
                                }
                                if (event.changes.none { it.pressed } ||
                                    (!enableImageGestures && event.changes.any { it.id == down.id && !it.pressed })
                                ) {
                                    completed = true
                                    break
                                }
                            }
                        } finally {
                            if (!completed) {
                                viewport = startViewport
                                currentOnChange(start)
                            }
                            activeHandle = null
                            currentOnGesture(false)
                        }
                    }
                },
        ) {
            if (!imageReady) return@Canvas
            val selection = Rect(
                bounds.left + crop.left * bounds.width,
                bounds.top + crop.top * bounds.height,
                bounds.left + crop.right * bounds.width,
                bounds.top + crop.bottom * bounds.height,
            )
            val shade = colors.scrim.copy(alpha = .52f)
            val shadeBounds = if (enableImageGestures) Rect(Offset.Zero, size) else bounds
            drawRect(shade, shadeBounds.topLeft, Size(shadeBounds.width, (selection.top - shadeBounds.top).coerceAtLeast(0f)))
            drawRect(shade, Offset(shadeBounds.left, selection.bottom), Size(shadeBounds.width, (shadeBounds.bottom - selection.bottom).coerceAtLeast(0f)))
            drawRect(shade, Offset(shadeBounds.left, selection.top), Size((selection.left - shadeBounds.left).coerceAtLeast(0f), selection.height))
            drawRect(shade, Offset(selection.right, selection.top), Size((shadeBounds.right - selection.right).coerceAtLeast(0f), selection.height))
            val line = 1.5.dp.toPx()
            drawRect(colors.primary, selection.topLeft, selection.size, style = Stroke(line))
            if (activeHandle != null) {
                for (division in 1..2) {
                    val x = selection.left + selection.width * division / 3f
                    val y = selection.top + selection.height * division / 3f
                    drawLine(Color.White.copy(alpha = .65f), Offset(x, selection.top), Offset(x, selection.bottom), 1.dp.toPx())
                    drawLine(Color.White.copy(alpha = .65f), Offset(selection.left, y), Offset(selection.right, y), 1.dp.toPx())
                }
            }
            val length = 12.dp.toPx().coerceAtMost(minOf(selection.width, selection.height) / 3f)
            val stroke = 4.dp.toPx()
            val corners = listOf(
                Triple(selection.topLeft, 1f, 1f),
                Triple(selection.topRight, -1f, 1f),
                Triple(selection.bottomRight, -1f, -1f),
                Triple(selection.bottomLeft, 1f, -1f),
            )
            corners.forEach { (point, x, y) ->
                drawLine(colors.primary, point, point + Offset(length * x, 0f), stroke, StrokeCap.Round)
                drawLine(colors.primary, point, point + Offset(0f, length * y), stroke, StrokeCap.Round)
            }
            val edgeLength = 20.dp.toPx()
            val edgeWidth = 4.dp.toPx()
            listOf(
                Rect(Offset(selection.center.x - edgeLength / 2f, selection.top - edgeWidth / 2f), Size(edgeLength, edgeWidth)),
                Rect(Offset(selection.center.x - edgeLength / 2f, selection.bottom - edgeWidth / 2f), Size(edgeLength, edgeWidth)),
                Rect(Offset(selection.left - edgeWidth / 2f, selection.center.y - edgeLength / 2f), Size(edgeWidth, edgeLength)),
                Rect(Offset(selection.right - edgeWidth / 2f, selection.center.y - edgeLength / 2f), Size(edgeWidth, edgeLength)),
            ).forEach { edge ->
                drawRoundRect(colors.primary, edge.topLeft, edge.size, CornerRadius(edgeWidth / 2f))
            }
        }
    }
}

@Preview(name = "Seleção de recorte", widthDp = 390, heightDp = 320, locale = "pt-rBR")
@Preview(name = "Seleção de recorte escura", widthDp = 390, heightDp = 320, locale = "pt-rBR", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SignatureCropSelectionPreview() {
    ScaniqoTheme {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            var crop by remember { mutableStateOf(PdfSignatureCrop(.1f, .15f, .9f, .85f)) }
            SignatureCropEditor(
                uri = "preview:signature",
                aspectRatio = 3f,
                crop = crop,
                onCropChange = { crop = it },
                onImageReady = {},
                modifier = Modifier.padding(24.dp).height(272.dp),
            )
        }
    }
}
