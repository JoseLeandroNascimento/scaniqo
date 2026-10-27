package com.joseleandro.scaniqo.ui.documentEditor.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import com.joseleandro.scaniqo.R
import java.text.DecimalFormatSymbols
import kotlin.math.abs
import kotlin.math.roundToInt

/** Uma escala e um valor editável para inclinação ou curvatura; nenhum campo altera a prévia até ser confirmado. */
@Composable
internal fun CorrectionNumericControl(
    label: String,
    value: Float,
    curvature: Boolean,
    enabled: Boolean,
    onPreviewChange: (Float) -> Unit,
    onPreviewFinished: () -> Unit,
    onCommit: (Float) -> Unit,
) {
    val locale = LocalConfiguration.current.locales.get(0)
    val range = if (curvature) -1f..1f else -15f..15f
    val step = if (curvature) .01f else .1f
    fun display(number: Float): String = if (curvature) {
        String.format(locale, "%+d%%", (number * 100).roundToInt())
    } else {
        String.format(locale, "%+.1f°", number)
    }
    var editing by rememberSaveable(label) { mutableStateOf(false) }
    var input by rememberSaveable(label) { mutableStateOf("") }
    var error by rememberSaveable(label) { mutableStateOf(false) }
    fun dismiss() {
        editing = false
        error = false
    }
    fun accept() {
        val separator = DecimalFormatSymbols.getInstance(locale).decimalSeparator
        val normalized = buildString {
            input.trim().forEach { char ->
                val digit = Character.digit(char, 10)
                append(when {
                    digit >= 0 -> ('0'.code + digit).toChar()
                    char == separator || char == ',' -> '.'
                    char == '−' -> '-'
                    else -> char
                })
            }
        }
        val raw = normalized.toFloatOrNull()
        val parsed = if (curvature) raw?.div(100f) else raw
        if (parsed == null || !parsed.isFinite() || parsed !in range ||
            abs(parsed / step - (parsed / step).roundToInt()) > .002f
        ) {
            error = true
            return
        }
        onCommit((parsed / step).roundToInt() * step)
        dismiss()
    }
    BackHandler(editing) { dismiss() }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall)
        if (curvature) {
            Text(
                stringResource(R.string.correction_v3_curve_title),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (editing) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it; error = false },
                    modifier = Modifier.widthIn(min = 104.dp, max = 144.dp),
                    label = { Text(label) },
                    isError = error,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { accept() }),
                )
                TextButton(onClick = { accept() }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.correction_v3_value_done))
                }
            }
            if (error) Text(
                stringResource(R.string.correction_v3_value_error, display(range.start), display(range.endInclusive), display(step)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        } else {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val decrease = stringResource(R.string.correction_v3_value_decrease, label)
                val increase = stringResource(R.string.correction_v3_value_increase, label)
                FilledTonalIconButton(
                    onClick = { onCommit(((value / step).roundToInt() - 1).times(step).coerceIn(range)) },
                    enabled = enabled && value > range.start,
                    modifier = Modifier.size(48.dp).semantics { contentDescription = decrease },
                ) { Text("−", style = MaterialTheme.typography.titleLarge) }
                val editValue = stringResource(R.string.correction_v3_value_edit, label)
                TextButton(
                    onClick = {
                        input = if (curvature) String.format(locale, "%d", (value * 100).roundToInt())
                            else String.format(locale, "%.1f", value)
                        editing = true
                    },
                    enabled = enabled,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).semantics {
                        contentDescription = label + ": " + display(value) + ". " + editValue
                    },
                ) { Text(display(value), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center) }
                FilledTonalIconButton(
                    onClick = { onCommit(((value / step).roundToInt() + 1).times(step).coerceIn(range)) },
                    enabled = enabled && value < range.endInclusive,
                    modifier = Modifier.size(48.dp).semantics { contentDescription = increase },
                ) { Text("+", style = MaterialTheme.typography.titleLarge) }
            }
        }
        Slider(
            value = value.coerceIn(range),
            onValueChange = { onPreviewChange((it / step).roundToInt() * step) },
            onValueChangeFinished = onPreviewFinished,
            valueRange = range,
            enabled = enabled && !editing,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics {
                contentDescription = label + ": " + display(value)
            },
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(display(range.start), style = MaterialTheme.typography.labelSmall)
            Text(display(0f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            Text(display(range.endInclusive), style = MaterialTheme.typography.labelSmall)
        }
        if (curvature) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                CurveDirectionSample(up = true)
                CurveDirectionSample(up = false)
            }
        }
    }
}

@Composable
private fun CurveDirectionSample(up: Boolean) {
    val color = MaterialTheme.colorScheme.primary
    val label = stringResource(if (up) R.string.correction_v3_curve_up else R.string.correction_v3_curve_down)
    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Canvas(Modifier.size(width = 52.dp, height = 28.dp).semantics { contentDescription = label }) {
            val path = Path().apply {
                moveTo(size.width * .12f, size.height * .5f)
                quadraticTo(size.width * .5f, if (up) -size.height * .25f else size.height * 1.25f,
                    size.width * .88f, size.height * .5f)
            }
            drawPath(path, color, style = Stroke(2.dp.toPx()))
            drawLine(color.copy(alpha = .35f), Offset(size.width * .12f, size.height * .5f),
                Offset(size.width * .88f, size.height * .5f), 1.dp.toPx())
        }
    }
}
