package com.joseleandro.scaniqo.ui.signatures

import com.joseleandro.scaniqo.core.resources.R as CoreResourcesR
import com.joseleandro.scaniqo.feature.signatures.R

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.abs

private val signaturePenColors = listOf(
    0xFF18212B.toInt() to CoreResourcesR.string.runtime_black,
    0xFF183C9A.toInt() to CoreResourcesR.string.runtime_blue,
    0xFF613D82.toInt() to CoreResourcesR.string.runtime_violet,
    0xFFC62828.toInt() to CoreResourcesR.string.overlay_red,
    0xFF2E7D32.toInt() to CoreResourcesR.string.fill_mark_green,
)

private val signatureTipWidths = listOf(.002f, .004f, .007f, .010f, .014f)
private val signatureTipLabels = listOf(
    R.string.signature_tip_extra_fine,
    R.string.signature_tip_fine,
    R.string.signature_tip_medium,
    R.string.signature_tip_bold,
    R.string.signature_tip_extra_bold,
)

@Composable
internal fun SignaturePenColorSelector(
    selectedColor: Int,
    onColorSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    val selectedLabel = signaturePenColors.firstOrNull { it.first == selectedColor }?.second
        ?.let { stringResource(it) }.orEmpty()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SignatureControlLabel(stringResource(R.string.signature_pen_color_title), selectedLabel)
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = colors.surfaceContainerLow,
            border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = .45f)),
        ) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val slotWidth = maxOf(52.dp, (maxWidth - 16.dp) / signaturePenColors.size)
                LazyRow(
                    modifier = Modifier.fillMaxWidth().selectableGroup().testTag("signature_pen_colors"),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    itemsIndexed(signaturePenColors, key = { _, pen -> pen.first }) { _, (argb, labelId) ->
                        val selected = selectedColor == argb
                        val lift by animateDpAsState(
                            targetValue = if (selected) 8.dp else 0.dp,
                            animationSpec = tween(240, easing = FastOutSlowInEasing),
                            label = "signaturePenLift",
                        )
                        val emphasis by animateFloatAsState(
                            targetValue = if (selected) 1f else 0f,
                            animationSpec = tween(240),
                            label = "signaturePenEmphasis",
                        )
                        val label = stringResource(R.string.signature_pen_color_description, stringResource(labelId))
                        val ink = Color(argb)
                        Box(
                            modifier = Modifier.size(slotWidth, 96.dp)
                                .testTag("signature_pen_${argb.toUInt().toString(16)}")
                                .clip(RoundedCornerShape(20.dp))
                                .selectable(selected, enabled = enabled, role = Role.RadioButton) { onColorSelected(argb) }
                                .semantics { contentDescription = label }
                                .alpha(if (enabled) 1f else .38f),
                            contentAlignment = Alignment.BottomCenter,
                        ) {
                            Canvas(Modifier.fillMaxSize()) {
                                drawRoundRect(
                                    colors.primary.copy(alpha = .08f * emphasis),
                                    topLeft = Offset((size.width - 44.dp.toPx()) / 2f, 4.dp.toPx()),
                                    size = Size(44.dp.toPx(), size.height - 8.dp.toPx()),
                                    cornerRadius = CornerRadius(22.dp.toPx()),
                                )
                                drawCircle(
                                    colors.primary.copy(alpha = emphasis),
                                    radius = 2.dp.toPx(),
                                    center = Offset(size.width / 2f, size.height - 5.dp.toPx()),
                                )
                            }
                            SignaturePenIllustration(
                                ink,
                                modifier = Modifier.size(34.dp, 78.dp)
                                    .offset { IntOffset(0, -(lift + 6.dp).roundToPx()) }
                                    .testTag("signature_pen_visual_${argb.toUInt().toString(16)}"),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SignaturePenIllustration(ink: Color, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val darkInk = lerp(ink, Color.Black, .3f)
    val highlight = lerp(ink, Color.White, .35f)
    val metal = colors.outline
    val metalLight = colors.outlineVariant
    Canvas(modifier) {
        scale(size.width / 34f, size.height / 78f, pivot = Offset.Zero) {
            drawOval(
                color = colors.scrim.copy(alpha = .09f),
                topLeft = Offset(9f, 73f),
                size = Size(18f, 4f),
            )
            val nib = Path().apply {
                moveTo(17f, 2f)
                lineTo(23f, 18f)
                quadraticTo(17f, 22f, 11f, 18f)
                close()
            }
            drawPath(nib, Brush.horizontalGradient(
                listOf(metal, metalLight, metal), startX = 11f, endX = 23f,
            ))
            drawLine(darkInk, Offset(17f, 2f), Offset(17f, 6f), 1.5f, StrokeCap.Round)
            drawLine(metal, Offset(17f, 8f), Offset(17f, 17f), .8f, StrokeCap.Round)
            drawCircle(metal, .9f, Offset(17f, 15f))
            drawRoundRect(
                darkInk, Offset(10f, 18f), Size(14f, 8f), CornerRadius(3f),
            )
            drawRoundRect(
                Brush.horizontalGradient(
                    listOf(darkInk, ink, highlight, ink, darkInk),
                    startX = 9f, endX = 25f,
                ),
                Offset(9f, 23f), Size(16f, 50f), CornerRadius(7f),
            )
            repeat(4) { index ->
                val y = 27f + index * 3f
                drawLine(darkInk.copy(alpha = .4f), Offset(10f, y), Offset(24f, y), .7f)
            }
            drawLine(highlight.copy(alpha = .75f), Offset(13f, 40f), Offset(13f, 65f), 1.1f, StrokeCap.Round)
            drawRoundRect(
                Brush.horizontalGradient(listOf(metalLight, metal), startX = 22f, endX = 25f),
                Offset(22f, 39f), Size(2.4f, 20f), CornerRadius(1.2f),
            )
            drawLine(darkInk.copy(alpha = .6f), Offset(11f, 67f), Offset(23f, 67f), .8f)
        }
    }
}

@Composable
internal fun SignatureTipSizeSelector(
    selectedWidth: Float,
    onWidthSelected: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val width = selectedWidth.takeIf(Float::isFinite) ?: .007f
    val selectedIndex = signatureTipWidths.indices.minBy { abs(signatureTipWidths[it] - width) }
    val colors = MaterialTheme.colorScheme
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SignatureControlLabel(
            stringResource(CoreResourcesR.string.runtime_thickness),
            stringResource(signatureTipLabels[selectedIndex]),
        )
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = colors.surfaceContainerLow,
            border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = .45f)),
        ) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val slotWidth = maxOf(48.dp, (maxWidth - 8.dp) / signatureTipWidths.size)
                LazyRow(
                    modifier = Modifier.fillMaxWidth().selectableGroup().testTag("signature_tip_sizes"),
                    contentPadding = PaddingValues(4.dp),
                ) {
                    itemsIndexed(signatureTipWidths) { index, preset ->
                        val selected = index == selectedIndex
                        val label = stringResource(
                            R.string.signature_tip_size_description, stringResource(signatureTipLabels[index]),
                        )
                        val container by animateColorAsState(
                            if (selected) colors.primary else colors.surfaceContainerLow,
                            tween(180), label = "signatureTipContainer",
                        )
                        val tint by animateColorAsState(
                            if (selected) colors.onPrimary else colors.onSurfaceVariant,
                            tween(180), label = "signatureTipTint",
                        )
                        Box(
                            modifier = Modifier.size(slotWidth, 48.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(container)
                                .selectable(selected, enabled = enabled, role = Role.RadioButton) { onWidthSelected(preset) }
                                .semantics { contentDescription = label }
                                .testTag("signature_tip_$index"),
                            contentAlignment = Alignment.Center,
                        ) {
                            Canvas(Modifier.size(40.dp).alpha(if (enabled) 1f else .38f)) {
                                val diameter = listOf(3f, 5f, 8f, 12f, 17f)[index].dp.toPx()
                                drawCircle(color = tint, radius = diameter / 2f)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SignatureControlLabel(title: String, selectedLabel: String, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
        Text(
            selectedLabel,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
