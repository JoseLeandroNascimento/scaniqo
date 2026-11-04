package com.joseleandro.scaniqo.ui.documentSignature

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.joseleandro.scaniqo.domain.model.PdfPoint
import com.joseleandro.scaniqo.domain.model.PdfSignaturePlacement
import com.joseleandro.scaniqo.ui.documentEditor.util.placeCaptureMagnifier
import kotlin.math.roundToInt

/** Recompõe documento e tinta usando os bitmaps já exibidos, sem renderizar ou alocar por gesto. */
@Composable
internal fun DocumentSignatureMagnifier(
    document: ImageBitmap,
    signature: ImageBitmap,
    placement: PdfSignaturePlacement,
    pointer: PdfPoint,
    pageBounds: Rect,
    viewportSize: IntSize,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val lens = with(density) {
        placeCaptureMagnifier(
            viewportWidth = viewportSize.width.toFloat(),
            viewportHeight = viewportSize.height.toFloat(),
            fingerX = pageBounds.left + pointer.x * pageBounds.width,
            fingerY = pageBounds.top + pointer.y * pageBounds.height,
            desiredWidth = 224.dp.toPx(),
            desiredHeight = 160.dp.toPx(),
            margin = 8.dp.toPx(),
            clearance = 40.dp.toPx(),
            minimumWidth = 128.dp.toPx(),
            minimumHeight = 96.dp.toPx(),
        )
    } ?: return
    val shape = RoundedCornerShape(12.dp)
    val outline = MaterialTheme.colorScheme.primary
    val surround = MaterialTheme.colorScheme.surfaceContainerHighest
    Canvas(
        modifier
            .absoluteOffset { IntOffset(lens.left.roundToInt(), lens.top.roundToInt()) }
            .size(with(density) { lens.width.toDp() }, with(density) { lens.height.toDp() })
            .shadow(6.dp, shape)
            .clip(shape)
            .background(surround)
            .border(2.dp, outline, shape)
            .clearAndSetSemantics {},
    ) {
        val viewport = signatureMagnifierViewport(
            placement, pageBounds.width, pageBounds.height, size.width, size.height,
        ) ?: return@Canvas
        val ink = signatureMagnifierImageBounds(
            placement, pageBounds.width, pageBounds.height, signature.width, signature.height,
        ) ?: return@Canvas
        withTransform({
            translate(viewport.translateX, viewport.translateY)
            scale(viewport.zoom, viewport.zoom, pivot = Offset.Zero)
        }) {
            clipRect(0f, 0f, pageBounds.width, pageBounds.height) {
                drawRect(Color.White, size = Size(pageBounds.width, pageBounds.height))
                withTransform({
                    scale(pageBounds.width / document.width, pageBounds.height / document.height,
                        pivot = Offset.Zero)
                }) {
                    drawImage(document, srcOffset = IntOffset.Zero,
                        srcSize = IntSize(document.width, document.height),
                        dstOffset = IntOffset.Zero, dstSize = IntSize(document.width, document.height),
                        filterQuality = FilterQuality.Medium)
                }
                withTransform({
                    rotate(placement.rotationDegrees, pivot = Offset(viewport.centerX, viewport.centerY))
                    translate(ink.left, ink.top)
                    scale(ink.width / signature.width, ink.height / signature.height, pivot = Offset.Zero)
                }) {
                    drawImage(signature, srcOffset = IntOffset.Zero,
                        srcSize = IntSize(signature.width, signature.height),
                        dstOffset = IntOffset.Zero, dstSize = IntSize(signature.width, signature.height),
                        filterQuality = FilterQuality.Medium)
                }
            }
        }
    }
}
