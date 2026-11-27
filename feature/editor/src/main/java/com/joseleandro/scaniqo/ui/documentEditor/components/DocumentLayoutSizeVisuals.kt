package com.joseleandro.scaniqo.ui.documentEditor.components

import com.joseleandro.scaniqo.feature.editor.R

import android.content.res.Configuration
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import com.joseleandro.scaniqo.ui.theme.ScaniqoTheme
/** Keeps native slider gestures and accessibility, with a lighter scale track. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DocumentScaleSlider(
    value: Float,
    enabled: Boolean,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = .25f..2f,
    label: String = stringResource(R.string.layout_v3_scale),
    testTag: String = "layout_size_slider",
    referenceValue: Float? = 1f,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    steps: Int = 0,
) {
    val colors = MaterialTheme.colorScheme
    val accent = colors.primary.copy(alpha = if (enabled) 1f else .38f)
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val sliderState = remember(steps, valueRange) {
        SliderState(value = value, steps = steps, trackRange = valueRange)
    }
    sliderState.value = value
    Slider(
        state = sliderState,
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
        enabled = enabled,
        interactionSource = interactionSource,
        modifier = modifier.heightIn(min = 48.dp).testTag(testTag)
            .semantics { contentDescription = label },
        thumb = {
            Surface(
                Modifier.size(20.dp),
                shape = CircleShape,
                color = colors.surfaceContainerLowest,
                border = BorderStroke(2.dp, accent),
                shadowElevation = if (enabled) 2.dp else 0.dp,
            ) {}
        },
        track = { state ->
            Canvas(Modifier.fillMaxWidth().height(24.dp)) {
                fun x(fraction: Float) = size.width * if (isRtl) 1f - fraction else fraction
                val y = center.y
                val span = (valueRange.endInclusive - valueRange.start).coerceAtLeast(.0001f)
                val fraction = ((state.value - valueRange.start) / span).coerceIn(0f, 1f)
                drawLine(
                    colors.outlineVariant, Offset(x(0f), y), Offset(x(1f), y),
                    2.dp.toPx(), StrokeCap.Round,
                )
                drawLine(
                    accent, Offset(x(0f), y), Offset(x(fraction), y),
                    3.dp.toPx(), StrokeCap.Round,
                )
                // Quiet reference mark for the original 100% size.
                referenceValue?.takeIf { it in valueRange }?.let { reference ->
                    drawCircle(
                        colors.onSurfaceVariant.copy(alpha = .55f), 1.5.dp.toPx(),
                        Offset(x((reference - valueRange.start) / span), y + 7.dp.toPx()),
                    )
                }
            }
        },
    )
}

@Composable
internal fun LayoutSizePresets(
    selectedPreset: LayoutSizePreset?,
    enabled: Boolean,
    onSelect: (LayoutSizePreset) -> Unit,
    modifier: Modifier = Modifier,
) {
    val labels = LayoutSizePreset.entries.map { layoutPresetLabel(it) }
    val textStyle = MaterialTheme.typography.labelMedium
    val textMeasurer = rememberTextMeasurer()
    val minimumTileWidth = with(LocalDensity.current) {
        labels.flatMap { it.split(' ') }
            .maxOf { textMeasurer.measure(AnnotatedString(it), textStyle).size.width }.toDp() + 16.dp
    }.coerceAtLeast(72.dp)
    BoxWithConstraints(modifier.fillMaxWidth()) {
        if ((maxWidth - 24.dp) / 3 >= minimumTileWidth) {
            Row(
                Modifier.fillMaxWidth().height(IntrinsicSize.Min).selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                LayoutSizePreset.entries.forEach { preset ->
                    LayoutSizePresetTile(
                        preset, selected = selectedPreset == preset, enabled = enabled,
                        onClick = { onSelect(preset) }, modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
            }
        } else {
            Column(Modifier.fillMaxWidth().selectableGroup(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LayoutSizePreset.entries.forEach { preset ->
                    LayoutSizePresetTile(
                        preset, selected = selectedPreset == preset, enabled = enabled,
                        onClick = { onSelect(preset) }, modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun layoutPresetLabel(preset: LayoutSizePreset) = stringResource(when (preset) {
    LayoutSizePreset.WITH_MARGIN -> R.string.layout_with_margin
    LayoutSizePreset.WITHOUT_MARGIN -> R.string.layout_v3_without_margin
    LayoutSizePreset.FILL -> R.string.tools_fill_compact
})

@Composable
internal fun LayoutSizePresetTile(
    preset: LayoutSizePreset,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = layoutPresetLabel(preset)
    val description = stringResource(when (preset) {
        LayoutSizePreset.WITH_MARGIN -> R.string.layout_v3_with_margin_description
        LayoutSizePreset.WITHOUT_MARGIN -> R.string.layout_v3_without_margin_description
        LayoutSizePreset.FILL -> R.string.tools_fill_description
    })
    val colors = MaterialTheme.colorScheme
    val surfaceColor by animateColorAsState(
        if (selected) colors.secondaryContainer else colors.surfaceContainerHigh,
        animationSpec = tween(160), label = "layout_preset",
    )
    val ink = (if (selected) colors.onSecondaryContainer else colors.onSurfaceVariant)
        .copy(alpha = if (enabled) 1f else .38f)
    Surface(
        modifier = modifier
            .testTag("layout_size_preset_${preset.name}")
            .selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = "$label. $description" },
        color = surfaceColor,
        contentColor = ink,
        shape = MaterialTheme.shapes.medium,
        border = if (selected) BorderStroke(2.dp, colors.primary) else null,
    ) {
        Column(
            Modifier.heightIn(min = 76.dp).padding(horizontal = 4.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
        ) {
            Canvas(Modifier.size(width = 28.dp, height = 32.dp)) {
                val page = Size(size.width * .78f, size.height - 2.dp.toPx())
                val origin = Offset((size.width - page.width) / 2f, 1.dp.toPx())
                drawRoundRect(colors.surfaceContainerLowest, origin, page, CornerRadius(2.dp.toPx()))
                clipRect(origin.x, origin.y, origin.x + page.width, origin.y + page.height) {
                    val inset = when (preset) {
                        LayoutSizePreset.WITH_MARGIN -> 4.dp.toPx()
                        LayoutSizePreset.WITHOUT_MARGIN -> 1.dp.toPx()
                        LayoutSizePreset.FILL -> -3.dp.toPx()
                    }
                    val contentOrigin = origin + Offset(inset, inset)
                    val contentSize = Size(page.width - inset * 2, page.height - inset * 2)
                    drawRect(ink.copy(alpha = .18f), contentOrigin, contentSize)
                    repeat(4) { line ->
                        val y = contentOrigin.y + contentSize.height * (.24f + line * .17f)
                        drawLine(
                            ink.copy(alpha = .65f),
                            Offset(contentOrigin.x + contentSize.width * .16f, y),
                            Offset(contentOrigin.x + contentSize.width * (if (line == 3) .62f else .84f), y),
                            1.dp.toPx(), StrokeCap.Round,
                        )
                    }
                }
                drawRoundRect(ink.copy(alpha = .7f), origin, page, CornerRadius(2.dp.toPx()), style = Stroke(1.dp.toPx()))
            }
            Text(
                label,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Preview(name = "Tamanhos de página fonte 2", widthDp = 320, fontScale = 2f, locale = "pt-rBR")
@Preview(name = "Tamanhos de página fonte 2 escuro", widthDp = 320, fontScale = 2f, locale = "pt-rBR", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun LayoutSizePresetsPreview() {
    ScaniqoTheme {
        Surface {
            LayoutSizePresets(
                selectedPreset = LayoutSizePreset.WITH_MARGIN, enabled = true,
                onSelect = {}, modifier = Modifier.padding(24.dp),
            )
        }
    }
}
