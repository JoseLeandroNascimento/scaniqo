package com.joseleandro.scaniqo.ui.documentEditor.components

import android.content.res.Configuration
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
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
import com.joseleandro.scaniqo.domain.model.PdfPageLayout
import com.joseleandro.scaniqo.domain.model.PdfCaptureSource
import com.joseleandro.scaniqo.domain.model.PdfInteractivePixels
import com.joseleandro.scaniqo.domain.model.PdfCropBounds
import com.joseleandro.scaniqo.domain.model.PdfPagePreview
import com.joseleandro.scaniqo.domain.model.PdfPageSpec
import com.joseleandro.scaniqo.domain.model.PdfPerspective
import com.joseleandro.scaniqo.domain.model.PdfPoint
import com.joseleandro.scaniqo.domain.model.PdfWorkspace
import com.joseleandro.scaniqo.ui.documentEditor.state.DocumentCaptureSourceState
import com.joseleandro.scaniqo.ui.documentEditor.state.DocumentToolPreviewState
import com.joseleandro.scaniqo.ui.documentEditor.state.DocumentToolsUiState
import com.joseleandro.scaniqo.ui.documentEditor.util.isValidSelection
import com.joseleandro.scaniqo.ui.theme.ScaniqoTheme
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
    val current = state.correctionsPreview.preview.takeIf { state.correctionsPreview.pageId == page.id }
    val livePreview = state.correctionsPreview.live.takeIf { it.pageId == page.id }
    val previewError = state.correctionsPreview.error || livePreview?.error == true
    DocumentInteractivePagePreview(
        page = page,
        preview = current,
        fallback = state.pagePreviews[page.id]?.preview,
        isLoading = state.correctionsPreview.isRendering || livePreview?.isRendering == true ||
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

/** A seleção de fundo mascara o exterior; os cantos não deformam a captura. */
@Composable
internal fun DocumentBackgroundMaskEditor(
    page: PdfPageSpec,
    perspective: PdfPerspective,
    enabled: Boolean,
    onChange: (PdfPerspective) -> Unit,
    pageNumber: Int,
    modifier: Modifier = Modifier,
    sourceState: DocumentCaptureSourceState = DocumentCaptureSourceState(),
    onRetry: () -> Unit = {},
) {
    var magnifier by remember(page.id, sourceState.source?.pixels) {
        mutableStateOf<CaptureMagnifierFocus?>(null)
    }
    val points = perspective.corners()
    DocumentSourceSelectionEditor(
        page, points, enabled, pageNumber, modifier, sourceState, onRetry, magnifier,
    ) { size ->
        points.forEachIndexed { index, point ->
            PerspectiveCorner(
                index, point, perspective, size, enabled, onChange, sourceState.source?.pixels,
                onMagnifierChange = { magnifier = it },
            )
        }
    }
}

/** Compartilha somente a imagem capturada entre recorte retangular e máscara de fundo. */
@Composable
internal fun DocumentSourceSelectionEditor(
    page: PdfPageSpec,
    points: List<PdfPoint>,
    enabled: Boolean,
    pageNumber: Int,
    modifier: Modifier = Modifier,
    sourceState: DocumentCaptureSourceState = DocumentCaptureSourceState(),
    onRetry: () -> Unit = {},
    magnifierFocus: CaptureMagnifierFocus? = null,
    corners: @Composable BoxScope.(IntSize) -> Unit,
) {
    val previewSource = if (LocalInspectionMode.current) remember {
        val width = 240
        val height = 320
        PdfCaptureSource(
            PdfInteractivePixels(width, height, IntArray(width * height) { index ->
                val x = index % width
                val y = index / width
                if (x in 20..220 && y in 16..304) 0xFFFFFFFF.toInt() else 0xFFCBC7C0.toInt()
            }, width.toFloat(), height.toFloat()),
            PdfCropBounds(),
        )
    } else null
    val matchesPage = sourceState.pageId == page.id
    val source = sourceState.source.takeIf { matchesPage && !sourceState.isLoading && !sourceState.error }
        ?: previewSource
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        if (source == null) {
            DocumentCaptureStatus(
                unsupported = matchesPage && sourceState.unsupported,
                error = matchesPage && sourceState.error,
                enabled = enabled,
                onRetry = onRetry,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            val pixels = source.pixels
            val raster = rememberCaptureRaster(pixels)
            val ratio = pixels.sourceWidth / pixels.sourceHeight
            // A margem de 24 dp mantém os alvos de 48 dp inteiramente dentro do palco.
            val width = minOf(
                (maxWidth - 48.dp).coerceAtLeast(1.dp),
                (maxHeight - 48.dp).coerceAtLeast(1.dp) * ratio,
            )
            val imageSize = with(LocalDensity.current) {
                IntSize(width.roundToPx(), (width / ratio).roundToPx())
            }
            val lineColor = MaterialTheme.colorScheme.primary
            Box(Modifier.width(width + 48.dp).height(width / ratio + 48.dp), contentAlignment = AbsoluteAlignment.TopLeft) {
                Surface(
                    modifier = Modifier.absoluteOffset(24.dp, 24.dp).width(width).height(width / ratio),
                    color = Color.White,
                    shape = RectangleShape,
                    shadowElevation = 2.dp,
                ) {
                    Box {
                        DocumentCaptureImage(
                            pixels = pixels,
                            description = stringResource(R.string.capture_editor_source_page, pageNumber),
                            modifier = Modifier.fillMaxSize(),
                            raster = raster,
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
                corners(imageSize)
            }
            if (enabled && magnifierFocus != null && raster?.image != null) {
                DocumentCaptureMagnifier(
                    image = raster.image,
                    focus = magnifierFocus,
                    imageSize = imageSize,
                    viewportSize = with(LocalDensity.current) {
                        IntSize(maxWidth.roundToPx(), maxHeight.roundToPx())
                    },
                    modifier = Modifier.align(AbsoluteAlignment.TopLeft),
                )
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
    sourceKey: Any? = null,
    onMagnifierChange: (CaptureMagnifierFocus?) -> Unit = {},
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

    val label = stringResource(
        when (index) {
            0 -> R.string.quality_corner_top_left
            1 -> R.string.quality_corner_top_right
            2 -> R.string.quality_corner_bottom_right
            else -> R.string.quality_corner_bottom_left
        }
    )
    val left = stringResource(R.string.quality_corner_left)
    val right = stringResource(R.string.quality_corner_right)
    val up = stringResource(R.string.quality_corner_up)
    val down = stringResource(R.string.quality_corner_down)
    Box(
        Modifier
            .absoluteOffset {
                IntOffset(
                    (point.x * size.width).roundToInt(),
                    (point.y * size.height).roundToInt()
                )
            }
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
                current = perspective,
                pointOf = { it.corners()[index] },
                move = { current, dx, dy ->
                    val corner = current.corners()[index]
                    current.withCorner(
                        index, PdfPoint((corner.x + dx).coerceIn(0f, 1f), (corner.y + dy).coerceIn(0f, 1f)),
                    ).takeIf { it.isValidSelection() } ?: current
                },
                onChange = onChange,
                onMagnifierChange = onMagnifierChange,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = CircleShape,
            color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            shadowElevation = 2.dp,
            modifier = Modifier.size(22.dp),
        ) {
            Box(Modifier
                .padding(6.dp)
                .background(Color.White, CircleShape))
        }
    }
}

private fun PdfPerspective.corners(): List<PdfPoint> =
    listOf(topLeft, topRight, bottomRight, bottomLeft)

private fun PdfPerspective.withCorner(index: Int, point: PdfPoint): PdfPerspective = when (index) {
    0 -> copy(topLeft = point)
    1 -> copy(topRight = point)
    2 -> copy(bottomRight = point)
    else -> copy(bottomLeft = point)
}

private val samplePerspective = PdfPerspective(
    topLeft = PdfPoint(0.12f, 0.08f),
    topRight = PdfPoint(0.88f, 0.15f),
    bottomRight = PdfPoint(0.95f, 0.91f),
    bottomLeft = PdfPoint(0.06f, 0.95f),
)

private val samplePage = PdfPageSpec(
    id = "page-1",
    sourceUri = "preview",
    sourcePageIndex = 0,
    layout = PdfPageLayout(
        backgroundMask = samplePerspective,
    ),
)

private val sampleWorkspace = PdfWorkspace(
    documentId = 1L,
    workspaceId = "preview",
    directoryUri = "preview",
    pages = listOf(samplePage),
)

private val sampleState = DocumentToolsUiState(
    workspace = sampleWorkspace,
    selectedPageId = "page-1",
    correctionsPreview = DocumentToolPreviewState(
        preview = PdfPagePreview("preview", 0),
        pageId = "page-1",
    ),
)

@Preview(name = "Prévia de correções - Claro", widthDp = 390, heightDp = 600, showBackground = true)
@Preview(name = "Prévia de correções - Escuro", widthDp = 390, heightDp = 600, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Prévia de correções - Fonte ampliada", widthDp = 360, heightDp = 600, fontScale = 1.3f)
@Composable
private fun DocumentCorrectionsPreviewDefaultPreview() {
    ScaniqoTheme {
        Surface {
            DocumentCorrectionsPreview(
                state = sampleState,
                page = samplePage,
                onRetry = {},
                pageNumber = 1,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Preview(name = "Prévia de correções - Carregando", widthDp = 390, heightDp = 600, showBackground = true)
@Composable
private fun DocumentCorrectionsPreviewLoadingPreview() {
    ScaniqoTheme {
        Surface {
            DocumentCorrectionsPreview(
                state = sampleState.copy(correctionsPreview = sampleState.correctionsPreview.copy(isRendering = true)),
                page = samplePage,
                onRetry = {},
                pageNumber = 1,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Preview(name = "Prévia de correções - Erro", widthDp = 390, heightDp = 600, showBackground = true)
@Composable
private fun DocumentCorrectionsPreviewErrorPreview() {
    ScaniqoTheme {
        Surface {
            DocumentCorrectionsPreview(
                state = sampleState.copy(correctionsPreview = sampleState.correctionsPreview.copy(error = true)),
                page = samplePage,
                onRetry = {},
                pageNumber = 1,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Preview(name = "Máscara de fundo - Árabe", locale = "ar", widthDp = 390, heightDp = 600, showBackground = true)
@Preview(name = "Máscara de fundo - Claro", widthDp = 390, heightDp = 600, showBackground = true)
@Preview(name = "Máscara de fundo - Escuro", widthDp = 390, heightDp = 600, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Máscara de fundo - Fonte ampliada", widthDp = 360, heightDp = 600, fontScale = 1.3f)
@Composable
private fun DocumentBackgroundMaskEditorPreview() {
    ScaniqoTheme {
        Surface {
            DocumentBackgroundMaskEditor(
                page = samplePage,
                perspective = samplePerspective,
                enabled = true,
                onChange = {},
                pageNumber = 1,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Preview(name = "Máscara de fundo - Desabilitado", widthDp = 390, heightDp = 600, showBackground = true)
@Composable
private fun DocumentBackgroundMaskEditorDisabledPreview() {
    ScaniqoTheme {
        Surface {
            DocumentBackgroundMaskEditor(
                page = samplePage,
                perspective = samplePerspective,
                enabled = false,
                onChange = {},
                pageNumber = 1,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Preview(name = "Alça da máscara - Habilitada", showBackground = true)
@Preview(name = "Alça da máscara - Escuro", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PerspectiveCornerEnabledPreview() {
    ScaniqoTheme {
        Surface {
            Box(Modifier.padding(16.dp)) {
                PerspectiveCorner(
                    index = 0,
                    point = PdfPoint(0f, 0f),
                    perspective = samplePerspective,
                    size = IntSize(100, 100),
                    enabled = true,
                    onChange = {},
                )
            }
        }
    }
}

@Preview(name = "Alça da máscara - Desabilitada", showBackground = true)
@Composable
private fun PerspectiveCornerDisabledPreview() {
    ScaniqoTheme {
        Surface {
            Box(Modifier.padding(16.dp)) {
                PerspectiveCorner(
                    index = 0,
                    point = PdfPoint(0f, 0f),
                    perspective = samplePerspective,
                    size = IntSize(100, 100),
                    enabled = false,
                    onChange = {},
                )
            }
        }
    }
}
