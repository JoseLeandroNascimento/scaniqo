package com.joseleandro.scaniqo.ui.documentEditor.components

import com.joseleandro.scaniqo.feature.editor.R

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.joseleandro.scaniqo.domain.model.PdfPoint
import com.joseleandro.scaniqo.domain.model.PdfSignaturePlacement
import com.joseleandro.scaniqo.ui.documentEditor.event.DocumentToolsEvent
import com.joseleandro.scaniqo.ui.documentEditor.util.resizeSignatureAroundCenter
import com.joseleandro.scaniqo.ui.documentEditor.util.resizeSignatureFromCorner
import com.joseleandro.scaniqo.ui.documentEditor.util.signatureSelectionGeometry
import com.joseleandro.scaniqo.ui.documentEditor.util.selectionRotationDelta
import com.joseleandro.scaniqo.ui.documentEditor.util.transformSignaturePlacement
import com.joseleandro.scaniqo.ui.signatures.SignatureImage
import com.joseleandro.scaniqo.ui.theme.ScaniqoTheme
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
            contentAlignment = androidx.compose.ui.AbsoluteAlignment.TopLeft,
        ) {
            Box(
                Modifier
                    .absoluteOffset { IntOffset((page.left + placement.x * page.width).roundToInt(), (page.top + placement.y * page.height).roundToInt()) }
                    .wrapContentSize(androidx.compose.ui.AbsoluteAlignment.TopLeft, unbounded = true)
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
            DocumentTransformSelection(
                selection = selection,
                enabled = enabled,
                visibleHandles = visibleHandles,
                handleLabels = cornerLabels + rotate,
                showHandles = showHandles,
                handleActions = { index ->
                    if (index == 4) listOf(
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
                },
                handleModifier = { index, handle ->
                    val isRotation = index == 4
                    val currentHandle by rememberUpdatedState(handle)
                    Modifier.pointerInput(placement.id, placement.imageUri, page, index) {
                        var start = current
                        var pointer = Offset.Zero
                        var startPointer = Offset.Zero
                        var movement = Offset.Zero
                        var firstDrag = true
                        var active = false
                        try {
                            detectDragGestures(
                                onDragStart = { position ->
                                    start = current
                                    movement = Offset.Zero
                                    firstDrag = true
                                    active = true
                                    pointer = Offset(currentHandle.x, currentHandle.y) + position - Offset(targetSize / 2f, targetSize / 2f)
                                    startPointer = pointer
                                    showGesture(start, pointer, if (isRotation) DocumentSignatureGestureKind.ROTATE else DocumentSignatureGestureKind.RESIZE, index.takeUnless { isRotation })
                                },
                                onDragEnd = { active = false; currentGesture(null) },
                                onDragCancel = { active = false; currentGesture(null) },
                                onDrag = { change, delta ->
                                    change.consume()
                                    movement += delta
                                    if (firstDrag) firstDrag = false else pointer += delta
                                    val value = if (isRotation) {
                                        val center = PdfPoint(
                                            page.left + (start.x + start.width / 2f) * page.width,
                                            page.top + (start.y + start.height / 2f) * page.height,
                                        )
                                        resizeSignatureAroundCenter(start, rotationDegrees = start.rotationDegrees +
                                            selectionRotationDelta(center, PdfPoint(startPointer.x, startPointer.y), PdfPoint(pointer.x, pointer.y)))
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
                    }
                },
            )
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
