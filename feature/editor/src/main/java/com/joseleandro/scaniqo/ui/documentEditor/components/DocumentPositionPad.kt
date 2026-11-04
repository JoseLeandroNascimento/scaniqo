package com.joseleandro.scaniqo.ui.documentEditor.components

import com.joseleandro.scaniqo.feature.editor.R

import android.content.res.Configuration
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.absoluteOffset
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
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.joseleandro.scaniqo.domain.model.PdfContentPosition
import com.joseleandro.scaniqo.ui.theme.ScaniqoTheme
import kotlin.math.abs
import kotlin.math.roundToInt

/** A small page that can be moved within the same normalized travel as the real document. */
@Composable
internal fun DocumentPositionPad(
    position: PdfContentPosition,
    enabled: Boolean,
    horizontalEnabled: Boolean,
    verticalEnabled: Boolean,
    onPositionChange: (PdfContentPosition) -> Unit,
    onGestureStart: () -> Unit,
    onGestureEnd: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    itemSize: DpSize? = null,
    description: String? = null,
    testTagPrefix: String = "layout",
    itemContent: (@Composable (Modifier) -> Unit)? = null,
) {
    val currentPosition by rememberUpdatedState(position)
    val currentOnChange by rememberUpdatedState(onPositionChange)
    val currentOnStart by rememberUpdatedState(onGestureStart)
    val currentOnEnd by rememberUpdatedState(onGestureEnd)
    val interactive = enabled && (horizontalEnabled || verticalEnabled)
    val density = LocalDensity.current
    val inset = with(density) { (if (compact) 12.dp else 16.dp).toPx() }
    val paperWidth = itemSize?.width ?: if (compact) 40.dp else 48.dp
    val paperHeight = itemSize?.height ?: if (compact) 48.dp else 64.dp
    val paperSize = with(density) { Size(paperWidth.toPx(), paperHeight.toPx()) }
    var dragging by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val accent = if (interactive) colors.primary else colors.onSurface.copy(alpha = .38f)
    val shape = RoundedCornerShape(20.dp)
    val horizontalLabel = stringResource(R.string.tools_signature_x)
    val verticalLabel = stringResource(R.string.tools_signature_y)
    val positionLabel = stringResource(R.string.tools_position)
    val dragLabel = stringResource(R.string.layout_interactive_drag)
    val directions = listOf(
        stringResource(R.string.tools_align_left),
        stringResource(R.string.tools_align_center),
        stringResource(R.string.tools_align_right),
        stringResource(R.string.tools_align_top),
        stringResource(R.string.tools_align_center),
        stringResource(R.string.tools_align_bottom),
    )

    fun moveTo(target: PdfContentPosition): Boolean {
        if (!interactive) return false
        val next = PdfContentPosition(
            if (horizontalEnabled) target.horizontal.coerceIn(0f, 1f) else currentPosition.horizontal,
            if (verticalEnabled) target.vertical.coerceIn(0f, 1f) else currentPosition.vertical,
        )
        if (next == currentPosition) return false
        currentOnStart()
        try {
            currentOnChange(next)
        } finally {
            currentOnEnd()
        }
        return true
    }

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(if (compact) 112.dp else 160.dp)
            .testTag("${testTagPrefix}_position_pad")
            .clip(shape)
            .background(colors.surfaceContainer)
            .border(1.dp, colors.outlineVariant.copy(alpha = .5f), shape)
            .semantics(mergeDescendants = true) {
                contentDescription = description ?: "$positionLabel. $dragLabel"
                stateDescription = "$horizontalLabel: ${(position.horizontal * 100).roundToInt()}% · " +
                    "$verticalLabel: ${(position.vertical * 100).roundToInt()}%"
                if (interactive) {
                    customActions = directions.mapIndexed { index, label ->
                        val horizontal = index < 3
                        val axis = if (horizontal) horizontalLabel else verticalLabel
                        CustomAccessibilityAction("$axis: $label") {
                            val target = (index % 3) / 2f
                            moveTo(
                                if (horizontal) currentPosition.copy(horizontal = target)
                                else currentPosition.copy(vertical = target),
                            )
                        }
                    }
                } else {
                    disabled()
                }
            }
            .onKeyEvent { event ->
                if (!interactive || event.type != KeyEventType.KeyDown) return@onKeyEvent false
                val current = currentPosition
                when (event.key) {
                    Key.DirectionLeft -> moveTo(current.copy(horizontal = current.horizontal - .05f))
                    Key.DirectionRight -> moveTo(current.copy(horizontal = current.horizontal + .05f))
                    Key.DirectionUp -> moveTo(current.copy(vertical = current.vertical - .05f))
                    Key.DirectionDown -> moveTo(current.copy(vertical = current.vertical + .05f))
                    Key.MoveHome -> moveTo(PdfContentPosition())
                    Key.MoveEnd -> moveTo(PdfContentPosition(1f, 1f))
                    else -> false
                }
            }
            .focusable(interactive)
            .pointerInput(interactive, horizontalEnabled, verticalEnabled, inset, paperSize) {
                if (!interactive) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val available = Offset(
                        (size.width - 2 * inset - paperSize.width).coerceAtLeast(0f),
                        (size.height - 2 * inset - paperSize.height).coerceAtLeast(0f),
                    )
                    var next = currentPosition
                    var moved = false
                    val origin = Offset(
                        inset + next.horizontal * available.x,
                        inset + next.vertical * available.y,
                    )
                    val grab = if (Rect(origin, paperSize).contains(down.position)) {
                        down.position - origin
                    } else {
                        Offset(paperSize.width / 2f, paperSize.height / 2f)
                    }
                    fun update(pointer: Offset) {
                        val target = PdfContentPosition(
                            if (horizontalEnabled && available.x > 0f) {
                                ((pointer.x - grab.x - inset) / available.x).coerceIn(0f, 1f)
                            } else next.horizontal,
                            if (verticalEnabled && available.y > 0f) {
                                ((pointer.y - grab.y - inset) / available.y).coerceIn(0f, 1f)
                            } else next.vertical,
                        )
                        if (abs(target.horizontal - next.horizontal) > .0001f ||
                            abs(target.vertical - next.vertical) > .0001f
                        ) {
                            moved = true
                            next = target
                            currentOnChange(target)
                        }
                    }
                    down.consume()
                    dragging = true
                    currentOnStart()
                    try {
                        update(down.position)
                        do {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
                                ?: break
                            if (change.isConsumed) break
                            update(change.position)
                            change.consume()
                            if (!change.pressed) {
                                fun snap(value: Float): Float {
                                    val anchor = (value * 2f).roundToInt() / 2f
                                    return if (abs(anchor - value) <= .08f) anchor else value
                                }
                                val snapped = PdfContentPosition(
                                    if (horizontalEnabled) snap(next.horizontal) else next.horizontal,
                                    if (verticalEnabled) snap(next.vertical) else next.vertical,
                                )
                                if (moved && snapped != next) currentOnChange(snapped)
                                break
                            }
                        } while (true)
                    } finally {
                        dragging = false
                        currentOnEnd()
                    }
                }
            },
        contentAlignment = AbsoluteAlignment.TopLeft,
    ) {
        val travel = Offset(
            (constraints.maxWidth - (2 * inset) - paperSize.width).coerceAtLeast(0f),
            (constraints.maxHeight - (2 * inset) - paperSize.height).coerceAtLeast(0f),
        )
        Canvas(Modifier.fillMaxSize()) {
            val guides = colors.outlineVariant.copy(alpha = .65f)
            val dash = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 5.dp.toPx()))
            drawLine(
                guides, Offset(center.x, inset), Offset(center.x, size.height - inset),
                1.dp.toPx(), pathEffect = dash,
            )
            drawLine(
                guides, Offset(inset, center.y), Offset(size.width - inset, center.y),
                1.dp.toPx(), pathEffect = dash,
            )
            for (x in 0..2) {
                for (y in 0..2) {
                    drawCircle(
                        guides, 2.dp.toPx(),
                        Offset(
                            inset + paperSize.width / 2f + travel.x * x / 2f,
                            inset + paperSize.height / 2f + travel.y * y / 2f,
                        ),
                    )
                }
            }
        }
        val itemModifier = Modifier
                .absoluteOffset {
                    IntOffset(
                        (inset + position.horizontal * travel.x).roundToInt(),
                        (inset + position.vertical * travel.y).roundToInt(),
                    )
                }
                .size(paperWidth, paperHeight)
                .testTag("${testTagPrefix}_position_document")
                .shadow(if (dragging) 6.dp else 2.dp, RoundedCornerShape(7.dp))
        if (itemContent != null) {
            itemContent(itemModifier)
        } else Canvas(itemModifier) {
            val corner = CornerRadius(7.dp.toPx())
            drawRoundRect(colors.surfaceContainerLowest, cornerRadius = corner)
            drawRoundRect(accent.copy(alpha = .08f), cornerRadius = corner)
            val edge = 1.dp.toPx()
            drawRoundRect(
                accent.copy(alpha = if (dragging) 1f else .65f),
                topLeft = Offset(edge / 2f, edge / 2f),
                size = Size(size.width - edge, size.height - edge),
                cornerRadius = corner,
                style = Stroke(edge),
            )
            drawRoundRect(
                accent.copy(alpha = .16f),
                Offset(size.width * .2f, size.height * .2f),
                Size(size.width * .6f, size.height * .18f),
                CornerRadius(2.dp.toPx()),
            )
            repeat(3) { line ->
                val y = size.height * (.52f + line * .13f)
                drawLine(
                    accent.copy(alpha = .55f),
                    Offset(size.width * .2f, y),
                    Offset(size.width * (if (line == 2) .56f else .8f), y),
                    2.dp.toPx(), StrokeCap.Round,
                )
            }
        }
    }
}

@Preview(name = "Posição interativa", widthDp = 344, heightDp = 160, locale = "pt-rBR")
@Preview(
    name = "Posição interativa escura", widthDp = 344, heightDp = 160,
    uiMode = Configuration.UI_MODE_NIGHT_YES, locale = "pt-rBR",
)
@Composable
private fun DocumentPositionPadPreview() {
    var position by remember { mutableStateOf(PdfContentPosition()) }
    ScaniqoTheme {
        Surface {
            DocumentPositionPad(
                position = position,
                enabled = true,
                horizontalEnabled = true,
                verticalEnabled = true,
                onPositionChange = { position = it },
                onGestureStart = {},
                onGestureEnd = {},
            )
        }
    }
}
