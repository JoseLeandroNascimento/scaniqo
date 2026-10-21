package com.joseleandro.scaniqo.ui.documentCommon.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.joseleandro.scaniqo.R

/** A transformação afeta a prévia e suas alças juntas, sem modificar o PDF. */
internal data class PreviewZoom(val scale: Float = 1f, val x: Float = 0f, val y: Float = 0f) {
    fun transformed(centroid: Offset, pan: Offset, zoom: Float, width: Float, height: Float,
        contentWidth: Float = width, contentHeight: Float = height): PreviewZoom {
        if (width <= 0f || height <= 0f || !width.isFinite() || !height.isFinite()) return this
        if (!centroid.x.isFinite() || !centroid.y.isFinite() || !pan.x.isFinite() ||
            !pan.y.isFinite() || !zoom.isFinite() || zoom <= 0f) return this
        val next = (scale * zoom).coerceIn(1f, 6f)
        if (next == 1f) return PreviewZoom()
        val ratio = next / scale
        val limitX = ((contentWidth * next - width) / (2f * width)).coerceAtLeast(0f)
        val limitY = ((contentHeight * next - height) / (2f * height)).coerceAtLeast(0f)
        return PreviewZoom(
            next,
            (x * ratio + (centroid.x / width - .5f) * (1f - ratio) + pan.x / width).coerceIn(-limitX, limitX),
            (y * ratio + (centroid.y / height - .5f) * (1f - ratio) + pan.y / height).coerceIn(-limitY, limitY),
        )
    }
}

/** O segundo dedo cancela o gesto dos filhos; nenhum toque residual vira seleção ou edição. */
internal suspend fun PointerInputScope.detectDocumentTransformGestures(
    allowSinglePan: () -> Boolean = { false },
    onGestureStart: () -> Unit = {},
    onTransformStart: () -> Unit = {},
    onTransform: (Offset, Offset, Float) -> Unit,
) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        onGestureStart()
        var transforming = false
        var drag = Offset.Zero
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val pressed = event.changes.count { it.pressed }
            val continuous = event.changes.count { it.pressed && it.previousPressed }
            val pan = event.calculatePan()
            if (!transforming && pressed >= 2) {
                transforming = true
                onTransformStart()
            } else if (!transforming && allowSinglePan() && pressed == 1) {
                drag += pan
                if (drag.getDistance() > viewConfiguration.touchSlop) {
                    transforming = true
                    onTransformStart()
                }
            }
            if (transforming) {
                if (continuous > 0) {
                    val centroid = event.calculateCentroid(useCurrent = false)
                    onTransform(centroid, pan, if (continuous >= 2) event.calculateZoom() else 1f)
                }
                event.changes.forEach { it.consume() }
            }
        } while (event.changes.any { it.pressed })
    }
}

private val PreviewZoomSaver = listSaver<PreviewZoom, Float>(
    save = { listOf(it.scale, it.x, it.y) },
    restore = { PreviewZoom(it[0].coerceIn(1f, 6f), it[1], it[2]) },
)

@Composable
internal fun DocumentPreviewZoom(
    modifier: Modifier = Modifier,
    contentKey: Any = Unit,
    singleFingerPan: Boolean = false,
    allowFullscreen: Boolean = true,
    contentAspectRatio: Float? = null,
    onGestureStart: () -> Unit = {},
    onTransformStart: () -> Unit = {},
    content: @Composable BoxScope.() -> Unit,
) {
    var zoom by rememberSaveable(contentKey, stateSaver = PreviewZoomSaver) { mutableStateOf(PreviewZoom()) }
    var expanded by rememberSaveable(contentKey) { mutableStateOf(false) }
    val viewport: @Composable (Modifier, Boolean) -> Unit = { viewportModifier, fullscreen ->
        BoxWithConstraints(viewportModifier.clipToBounds()) {
            val width = constraints.maxWidth.toFloat()
            val height = constraints.maxHeight.toFloat()
            val paperWidth = contentAspectRatio?.let { minOf(maxWidth, maxHeight * it) }
            val contentWidth = contentAspectRatio?.let { minOf(width, height * it) } ?: width
            val contentHeight = contentAspectRatio?.let { contentWidth / it } ?: height
            LaunchedEffect(width, height, contentWidth, contentHeight) {
                zoom = zoom.transformed(Offset(width / 2f, height / 2f), Offset.Zero, 1f,
                    width, height, contentWidth, contentHeight)
            }
            val currentZoom by rememberUpdatedState(zoom)
            val currentGestureStart by rememberUpdatedState(onGestureStart)
            val currentTransformStart by rememberUpdatedState(onTransformStart)
            Box(Modifier.fillMaxSize().pointerInput(contentKey, width, height, singleFingerPan) {
                detectDocumentTransformGestures(
                    allowSinglePan = { singleFingerPan && currentZoom.scale > 1f },
                    onGestureStart = { currentGestureStart() },
                    onTransformStart = { currentTransformStart() },
                    onTransform = { centroid, pan, factor ->
                        zoom = zoom.transformed(centroid, pan, factor, width, height, contentWidth, contentHeight)
                    },
                )
            }) {
                val paperModifier = if (contentAspectRatio != null) {
                    Modifier.width(requireNotNull(paperWidth)).aspectRatio(contentAspectRatio)
                } else Modifier.fillMaxSize()
                Box(
                    paperModifier.align(Alignment.Center).graphicsLayer {
                        scaleX = zoom.scale
                        scaleY = zoom.scale
                        translationX = zoom.x * width
                        translationY = zoom.y * height
                    },
                    contentAlignment = Alignment.Center,
                    content = content,
                )
            }
            Row(Modifier.align(Alignment.TopEnd).padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (zoom.scale > 1f) Surface(shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = .94f)) {
                    val resetLabel = stringResource(R.string.preview_zoom_reset)
                    TextButton(onClick = { zoom = PreviewZoom() },
                        modifier = Modifier.semantics { contentDescription = resetLabel }) {
                        Text(com.joseleandro.scaniqo.core.localization.localizedPercent(zoom.scale,
                            androidx.compose.ui.platform.LocalConfiguration.current.locales[0]))
                    }
                }
                if (allowFullscreen || fullscreen) FilledTonalIconButton(onClick = { expanded = !fullscreen }) {
                    Icon(painterResource(if (fullscreen) R.drawable.ic_toast_close else R.drawable.ic_preview_expand),
                        stringResource(if (fullscreen) R.string.preview_zoom_close else R.string.preview_zoom_expand))
                }
            }
        }
    }
    Box(modifier) {
        if (!expanded) viewport(Modifier.fillMaxSize(), false)
    }
    if (expanded) Dialog(onDismissRequest = { expanded = false },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainer) {
            viewport(Modifier.fillMaxSize().safeDrawingPadding(), true)
        }
    }
}
