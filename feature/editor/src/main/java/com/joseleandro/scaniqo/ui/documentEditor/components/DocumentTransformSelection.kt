package com.joseleandro.scaniqo.ui.documentEditor.components

import com.joseleandro.scaniqo.core.resources.R as CoreResourcesR

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.joseleandro.scaniqo.domain.model.PdfPoint
import com.joseleandro.scaniqo.ui.documentEditor.util.DocumentSelectionGeometry
import kotlin.math.roundToInt

@Composable
internal fun DocumentTransformSelection(
    selection: DocumentSelectionGeometry,
    enabled: Boolean,
    visibleHandles: List<Int>,
    handleLabels: List<String>,
    handleActions: (Int) -> List<CustomAccessibilityAction>,
    modifier: Modifier = Modifier,
    showHandles: Boolean = true,
    testTagPrefix: String = "signature_selection",
    handleModifier: @Composable (Int, PdfPoint) -> Modifier = { _, _ -> Modifier },
) {
    val accent = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    val targetSize = with(LocalDensity.current) { 48.dp.toPx() }
    val handles = selection.cornerHandles + selection.rotationHandle
    Box(modifier.fillMaxSize(), contentAlignment = AbsoluteAlignment.TopLeft) {
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
                    if (index in visibleHandles) drawLine(
                        accent, Offset(corner.x, corner.y), Offset(handle.x, handle.y),
                        1.dp.toPx(), pathEffect = dash,
                    )
                }
                if (4 in visibleHandles) drawLine(
                    accent, Offset(selection.topMiddle.x, selection.topMiddle.y),
                    Offset(selection.rotationHandle.x, selection.rotationHandle.y),
                    1.5.dp.toPx(), pathEffect = dash,
                )
            }
        }
        if (showHandles) handles.forEachIndexed { index, handle ->
            if (index !in visibleHandles) return@forEachIndexed
            val isRotation = index == 4
            Box(
                Modifier.absoluteOffset {
                    IntOffset((handle.x - targetSize / 2f).roundToInt(), (handle.y - targetSize / 2f).roundToInt())
                }.size(48.dp).testTag("${testTagPrefix}_handle_$index")
                    .semantics {
                        contentDescription = handleLabels[index]
                        if (enabled) customActions = handleActions(index)
                    }.then(handleModifier(index, handle)),
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
                        if (isRotation) Icon(
                            painterResource(CoreResourcesR.drawable.ic_layout_rotate_right),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        ) else Box(Modifier.size(10.dp).background(Color.White, CircleShape))
                    }
                }
            }
        }
    }
}
