package com.joseleandro.scaniqo.ui.documentEditor

import android.graphics.pdf.PdfRenderer
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.joseleandro.scaniqo.R
import com.joseleandro.scaniqo.domain.model.PdfPageLayout
import com.joseleandro.scaniqo.domain.model.PdfPageSpec
import com.joseleandro.scaniqo.domain.model.PdfPerspective
import com.joseleandro.scaniqo.domain.model.PdfPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** O palco usa a captura do rascunho em memória, com a prévia PDF como alternativa. */
@Composable
internal fun DocumentCorrectionsPreview(
    state: DocumentToolsUiState,
    page: PdfPageSpec,
    onRetry: () -> Unit,
    pageNumber: Int,
    modifier: Modifier = Modifier,
    reserveHandleSpace: Boolean = false,
    liveLayout: PdfPageLayout = page.layout,
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    val current = state.correctionsPreview.takeIf { state.correctionsPreviewPageId == page.id }
    val livePreview = state.correctionsLivePreview.takeIf { it.pageId == page.id }
    val previewError = state.correctionsPreviewError || livePreview?.error == true
    DocumentInteractivePagePreview(
        page = page,
        preview = current,
        fallback = state.pagePreviews[page.id]?.preview,
        isLoading = state.isPreviewingCorrections || livePreview?.isRendering == true ||
            (current == null && livePreview?.pixels == null && !previewError),
        hasError = previewError,
        enabled = state.canEdit,
        onRetry = onRetry,
        modifier = modifier,
        pageNumber = pageNumber,
        reserveHandleSpace = reserveHandleSpace,
        livePixels = livePreview?.pixels,
        liveLayout = liveLayout,
        workspaceId = state.workspace?.workspaceId,
        pageCount = state.workspace?.pages?.size ?: 1,
        showPageNumbers = state.workspace?.showPageNumbers == true,
        overlay = overlay,
    )
}

private data class SourceDimensions(val width: Int, val height: Int)

/** Os quatro cantos correspondem à fonte nativa, sem a rotação ou o enquadramento da folha A4. */
@Composable
internal fun DocumentPerspectiveEditor(
    page: PdfPageSpec,
    perspective: PdfPerspective,
    enabled: Boolean,
    onChange: (PdfPerspective) -> Unit,
    pageNumber: Int,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current.applicationContext
    var retry by remember { mutableIntStateOf(0) }
    val dimensions = if (LocalInspectionMode.current) SourceDimensions(595, 842) else {
        produceState<SourceDimensions?>(null, page.sourceUri, page.sourcePageIndex, retry) {
            value = null
            value = try {
                withContext(Dispatchers.IO) {
                    requireNotNull(context.contentResolver.openFileDescriptor(page.sourceUri.toUri(), "r")).use { descriptor ->
                        PdfRenderer(descriptor).use { renderer ->
                            renderer.openPage(page.sourcePageIndex).use { source -> SourceDimensions(source.width, source.height) }
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                SourceDimensions(0, 0)
            }
        }.value
    }
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val source = dimensions
        when {
            source == null -> CircularProgressIndicator(Modifier.size(28.dp))
            source.width <= 0 || source.height <= 0 -> Column(
                Modifier.padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    stringResource(R.string.quality_preview_unavailable),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                TextButton(onClick = { retry++ }, enabled = enabled) {
                    Text(stringResource(R.string.corrections_preview_retry))
                }
            }
            else -> {
                val ratio = source.width.toFloat() / source.height
                // A margem de 24 dp mantém os alvos de 48 dp inteiramente dentro do palco.
                val width = minOf(
                    (maxWidth - 48.dp).coerceAtLeast(1.dp),
                    (maxHeight - 48.dp).coerceAtLeast(1.dp) * ratio,
                )
                val imageSize = with(LocalDensity.current) {
                    IntSize(width.roundToPx(), (width / ratio).roundToPx())
                }
                val lineColor = MaterialTheme.colorScheme.primary
                val points = perspective.corners()
                Box(Modifier.width(width + 48.dp).height(width / ratio + 48.dp)) {
                    Surface(
                        modifier = Modifier.offset(24.dp, 24.dp).width(width).height(width / ratio),
                        color = Color.White,
                        shape = RectangleShape,
                        shadowElevation = 2.dp,
                    ) {
                        Box {
                            DocumentPdfPage(
                                page.sourceUri, page.sourcePageIndex, Modifier.fillMaxSize(),
                                contentKey = "correction-source-${page.id}",
                                pageNumber = pageNumber,
                            )
                            Canvas(Modifier.matchParentSize()) {
                                val selected = Path().apply {
                                    points.forEachIndexed { index, point ->
                                        if (index == 0) moveTo(point.x * size.width, point.y * size.height)
                                        else lineTo(point.x * size.width, point.y * size.height)
                                    }
                                    close()
                                }
                                val mask = Path().apply {
                                    fillType = PathFillType.EvenOdd
                                    addRect(Rect(0f, 0f, size.width, size.height))
                                    addPath(selected)
                                }
                                drawPath(mask, Color.Black.copy(alpha = .3f))
                                drawPath(selected, lineColor, style = Stroke(2.dp.toPx()))
                            }
                        }
                    }
                    points.forEachIndexed { index, point ->
                        PerspectiveCorner(index, point, perspective, imageSize, enabled, onChange)
                    }
                }
            }
        }
    }
}

@Composable
private fun PerspectiveCorner(
    index: Int,
    point: PdfPoint,
    perspective: PdfPerspective,
    size: IntSize,
    enabled: Boolean,
    onChange: (PdfPerspective) -> Unit,
) {
    val latestPerspective by rememberUpdatedState(perspective)
    val latestOnChange by rememberUpdatedState(onChange)
    fun move(dx: Float, dy: Float): Boolean {
        if (!enabled || size.width == 0 || size.height == 0) return false
        val current = latestPerspective.corners()[index]
        val changed = latestPerspective.withCorner(
            index, PdfPoint((current.x + dx).coerceIn(0f, 1f), (current.y + dy).coerceIn(0f, 1f)),
        )
        if (!changed.isValidSelection()) return false
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
            .offset { IntOffset((point.x * size.width).roundToInt(), (point.y * size.height).roundToInt()) }
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
            .pointerInput(index, size, enabled) {
                if (enabled) detectDragGestures { change, amount ->
                    change.consume()
                    if (size.width > 0 && size.height > 0) move(amount.x / size.width, amount.y / size.height)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = CircleShape,
            color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            shadowElevation = 2.dp,
            modifier = Modifier.size(22.dp),
        ) {
            Box(Modifier.padding(6.dp).background(Color.White, CircleShape))
        }
    }
}

private fun PdfPerspective.corners(): List<PdfPoint> = listOf(topLeft, topRight, bottomRight, bottomLeft)

private fun PdfPerspective.withCorner(index: Int, point: PdfPoint): PdfPerspective = when (index) {
    0 -> copy(topLeft = point)
    1 -> copy(topRight = point)
    2 -> copy(bottomRight = point)
    else -> copy(bottomLeft = point)
}
