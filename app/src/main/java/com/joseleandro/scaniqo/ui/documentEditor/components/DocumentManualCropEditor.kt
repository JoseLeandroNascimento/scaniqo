package com.joseleandro.scaniqo.ui.documentEditor.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
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
        corners.forEachIndexed { index, point ->
            CropCorner(
                index, point, crop, size, enabled, onChange, sourceState.source?.pixels,
                onMagnifierChange = { magnifier = it },
            )
        }
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
