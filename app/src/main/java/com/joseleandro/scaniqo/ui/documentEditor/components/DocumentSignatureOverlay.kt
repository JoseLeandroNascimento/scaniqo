package com.joseleandro.scaniqo.ui.documentEditor.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.joseleandro.scaniqo.R
import com.joseleandro.scaniqo.domain.model.PdfPoint
import com.joseleandro.scaniqo.domain.model.PdfSignaturePlacement
import com.joseleandro.scaniqo.ui.documentEditor.event.DocumentToolsEvent
import com.joseleandro.scaniqo.ui.documentEditor.util.resizeSignatureAroundCenter
import com.joseleandro.scaniqo.ui.documentEditor.util.resizeSignatureFromCorner
import com.joseleandro.scaniqo.ui.documentEditor.util.signatureSelectionGeometry
import com.joseleandro.scaniqo.ui.documentEditor.util.transformSignaturePlacement
import com.joseleandro.scaniqo.ui.signatures.SignatureImage
import com.joseleandro.scaniqo.ui.theme.ScaniqoTheme
import kotlin.math.atan2
import kotlin.math.roundToInt

/** A seleção ocupa o palco inteiro; a geometria persistida continua relativa à folha. */
@Composable
internal fun DocumentSignatureOverlay(
    placement: PdfSignaturePlacement,
    onEvent: (DocumentToolsEvent) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    showResizeHandle: Boolean = true,
    pageBounds: Rect? = null,
    onGestureChange: (DocumentSignatureGesture?) -> Unit = {},
    onImageBitmapChanged: (ImageBitmap?) -> Unit = {},
    onViewportPan: ((Offset) -> Unit)? = null,
    viewportGestureKey: Any? = null,
) {
    val current by rememberUpdatedState(placement)
    val currentEvent by rememberUpdatedState(onEvent)
    val currentGesture by rememberUpdatedState(onGestureChange)
    val description = stringResource(R.string.tools_signature_selected)
    val left = stringResource(R.string.tools_signature_left)
    val right = stringResource(R.string.tools_signature_right)
    val up = stringResource(R.string.tools_signature_up)
    val down = stringResource(R.string.tools_signature_down)
    val rotate = stringResource(R.string.signature_rotate_handle)
    val rotateLeft = stringResource(R.string.signature_rotate_left)
    val rotateRight = stringResource(R.string.signature_rotate_right)
    val grow = stringResource(R.string.signature_grow)
    val shrink = stringResource(R.string.signature_shrink)
    val cornerLabels = listOf(
        stringResource(R.string.signature_resize_top_left),
        stringResource(R.string.signature_resize_top_right),
        stringResource(R.string.signature_resize_bottom_right),
        stringResource(R.string.signature_resize_bottom_left),
    )
    val accent = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    val density = LocalDensity.current
    val showHandles = enabled && showResizeHandle
    BoxWithConstraints(modifier.fillMaxSize()) {
        val viewportWidth = constraints.maxWidth.toFloat()
        val viewportHeight = constraints.maxHeight.toFloat()
        val page = pageBounds ?: Rect(0f, 0f, viewportWidth, viewportHeight)
        if (page.width <= 0f || page.height <= 0f) return@BoxWithConstraints
        val targetSize = with(density) { 48.dp.toPx() }
        val selection = signatureSelectionGeometry(
            placement, page.left, page.top, page.width, page.height, viewportWidth, viewportHeight,
            targetSize, with(density) { 4.dp.toPx() }, with(density) { 48.dp.toPx() },
        )
        val handles = selection.cornerHandles + selection.rotationHandle
        val visibleHandles = handles.indices.filter { index ->
            val attached = if (index == 4) selection.topMiddle else selection.corners[index]
            onViewportPan == null || (attached.x in 0f..viewportWidth && attached.y in 0f..viewportHeight)
        }
        val targets = visibleHandles.map { index ->
            val point = handles[index]
            Rect(point.x - targetSize / 2f, point.y - targetSize / 2f, point.x + targetSize / 2f, point.y + targetSize / 2f)
        }
        fun apply(value: PdfSignaturePlacement) {
            currentEvent(DocumentToolsEvent.TransformSignature(
                value.x, value.y, value.width, value.rotationDegrees, value.height, value.stretchToBounds,
            ))
        }
        fun showGesture(value: PdfSignaturePlacement, pointer: Offset, kind: DocumentSignatureGestureKind, corner: Int? = null) {
            currentGesture(DocumentSignatureGesture(
                value, PdfPoint((pointer.x - page.left) / page.width, (pointer.y - page.top) / page.height),
                kind, corner,
            ))
        }
        fun resizeBy(scale: Float) {
            apply(resizeSignatureAroundCenter(current, width = current.width * scale))
        }
        Box(
            Modifier.fillMaxSize().signatureTransformGestures(
                placement, page, enabled, if (showHandles) targets else emptyList(),
                onChange = { value, pointer, kind ->
                    apply(value)
                    showGesture(value, pointer, kind)
                },
                onFinish = { currentGesture(null) },
                onViewportPan = onViewportPan,
                minimumTargetSize = targetSize,
                viewportGestureKey = viewportGestureKey ?: page,
            ),
        ) {
            Box(
                Modifier
                    .offset { IntOffset((page.left + placement.x * page.width).roundToInt(), (page.top + placement.y * page.height).roundToInt()) }
                    .wrapContentSize(Alignment.TopStart, unbounded = true)
                    .size(with(density) { (placement.width * page.width).toDp() }, with(density) { (placement.height * page.height).toDp() })
                    .graphicsLayer { rotationZ = placement.rotationDegrees }
                    .semantics(mergeDescendants = true) {
                        contentDescription = description
                        if (enabled) customActions = listOf(
                            CustomAccessibilityAction(left) { apply(transformSignaturePlacement(current, x = current.x - .02f)); true },
                            CustomAccessibilityAction(right) { apply(transformSignaturePlacement(current, x = current.x + .02f)); true },
                            CustomAccessibilityAction(up) { apply(transformSignaturePlacement(current, y = current.y - .02f)); true },
                            CustomAccessibilityAction(down) { apply(transformSignaturePlacement(current, y = current.y + .02f)); true },
                            CustomAccessibilityAction(grow) { resizeBy(1.1f); true },
                            CustomAccessibilityAction(shrink) { resizeBy(.9f); true },
                            CustomAccessibilityAction(rotate) {
                                apply(resizeSignatureAroundCenter(current, rotationDegrees = current.rotationDegrees + 15f)); true
                            },
                        )
                    },
            ) {
                SignatureImage(
                    placement.imageUri, Modifier.fillMaxSize(),
                    contentScale = if (placement.stretchToBounds) ContentScale.FillBounds else ContentScale.Fit,
                    onImageBitmapChanged = onImageBitmapChanged,
                )
            }
            Canvas(Modifier.fillMaxSize()) {
                val dash = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))
                val outline = Path().apply {
                    selection.corners.forEachIndexed { index, point ->
                        if (index == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y)
                    }
                    close()
                }
                drawPath(outline, accent, style = Stroke(1.5.dp.toPx(), pathEffect = dash))
                if (showHandles) {
                    selection.corners.zip(selection.cornerHandles).forEachIndexed { index, (corner, handle) ->
                        if (index in visibleHandles) {
                            drawLine(accent, Offset(corner.x, corner.y), Offset(handle.x, handle.y), 1.dp.toPx(), pathEffect = dash)
                        }
                    }
                    if (4 in visibleHandles) {
                        drawLine(
                            accent, Offset(selection.topMiddle.x, selection.topMiddle.y),
                            Offset(selection.rotationHandle.x, selection.rotationHandle.y),
                            1.5.dp.toPx(), pathEffect = dash,
                        )
                    }
                }
            }
            if (showHandles) {
                handles.forEachIndexed { index, handle ->
                    if (index !in visibleHandles) return@forEachIndexed
                    val isRotation = index == 4
                    val actions = if (isRotation) listOf(
                        CustomAccessibilityAction(rotateLeft) {
                            apply(resizeSignatureAroundCenter(current, rotationDegrees = current.rotationDegrees - 15f)); true
                        },
                        CustomAccessibilityAction(rotateRight) {
                            apply(resizeSignatureAroundCenter(current, rotationDegrees = current.rotationDegrees + 15f)); true
                        },
                    ) else listOf(
                        CustomAccessibilityAction(left) { apply(resizeSignatureFromCorner(current, index, -page.width * .02f, 0f, page.width, page.height)); true },
                        CustomAccessibilityAction(right) { apply(resizeSignatureFromCorner(current, index, page.width * .02f, 0f, page.width, page.height)); true },
                        CustomAccessibilityAction(up) { apply(resizeSignatureFromCorner(current, index, 0f, -page.height * .02f, page.width, page.height)); true },
                        CustomAccessibilityAction(down) { apply(resizeSignatureFromCorner(current, index, 0f, page.height * .02f, page.width, page.height)); true },
                    )
                    val currentHandle by rememberUpdatedState(handle)
                    Box(
                        Modifier
                            .offset { IntOffset((handle.x - targetSize / 2f).roundToInt(), (handle.y - targetSize / 2f).roundToInt()) }
                            .size(48.dp)
                            .semantics {
                                contentDescription = if (isRotation) rotate else cornerLabels[index]
                                customActions = actions
                            }
                            .pointerInput(placement.id, placement.imageUri, page, index) {
                                var start = current
                                var pointer = Offset.Zero
                                var movement = Offset.Zero
                                var firstDrag = true
                                var startAngle = 0f
                                var active = false
                                fun angle(point: Offset): Float {
                                    val centerX = page.left + (start.x + start.width / 2f) * page.width
                                    val centerY = page.top + (start.y + start.height / 2f) * page.height
                                    return Math.toDegrees(atan2((point.y - centerY).toDouble(), (point.x - centerX).toDouble())).toFloat()
                                }
                                try {
                                    detectDragGestures(
                                        onDragStart = { position ->
                                            start = current
                                            movement = Offset.Zero
                                            firstDrag = true
                                            active = true
                                            pointer = Offset(currentHandle.x, currentHandle.y) + position - Offset(targetSize / 2f, targetSize / 2f)
                                            startAngle = angle(pointer)
                                            showGesture(start, pointer, if (isRotation) DocumentSignatureGestureKind.ROTATE else DocumentSignatureGestureKind.RESIZE, index.takeUnless { isRotation })
                                        },
                                        onDragEnd = { active = false; currentGesture(null) },
                                        onDragCancel = { active = false; currentGesture(null) },
                                        onDrag = { change, delta ->
                                            change.consume()
                                            movement += delta
                                            if (firstDrag) firstDrag = false else pointer += delta
                                            val value = if (isRotation) {
                                                resizeSignatureAroundCenter(start, rotationDegrees = start.rotationDegrees + angle(pointer) - startAngle)
                                            } else {
                                                resizeSignatureFromCorner(start, index, movement.x, movement.y, page.width, page.height)
                                            }
                                            apply(value)
                                            showGesture(value, pointer, if (isRotation) DocumentSignatureGestureKind.ROTATE else DocumentSignatureGestureKind.RESIZE, index.takeUnless { isRotation })
                                        },
                                    )
                                } finally {
                                    if (active) currentGesture(null)
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Surface(
                            Modifier.size(if (isRotation) 32.dp else 22.dp),
                            shape = CircleShape,
                            color = accent,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            shadowElevation = 2.dp,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (isRotation) {
                                    Icon(painterResource(R.drawable.ic_layout_rotate_right), contentDescription = null, modifier = Modifier.size(20.dp))
                                } else {
                                    Box(Modifier.size(10.dp).background(Color.White, CircleShape))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview(name = "Assinatura · seleção pontilhada", widthDp = 390, heightDp = 550, showBackground = true)
@Preview(name = "Assinatura · seleção pequena", widthDp = 320, heightDp = 400, fontScale = 1.3f)
@Composable
private fun DocumentSignatureSelectionPreview() {
    ScaniqoTheme {
        Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
            Box(Modifier.fillMaxSize().padding(16.dp)) {
                DocumentSignatureOverlay(
                    placement = PdfSignaturePlacement(
                        id = "preview", imageUri = "preview", x = .18f, y = .5f,
                        width = .64f, height = .12f, rotationDegrees = -12f,
                    ),
                    onEvent = {},
                )
            }
        }
    }
}
