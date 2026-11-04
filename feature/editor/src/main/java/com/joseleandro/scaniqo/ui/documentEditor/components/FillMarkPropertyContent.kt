package com.joseleandro.scaniqo.ui.documentEditor.components

import com.joseleandro.scaniqo.core.resources.R as CoreResourcesR
import com.joseleandro.scaniqo.feature.editor.R

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.joseleandro.scaniqo.domain.model.PdfOverlay
import com.joseleandro.scaniqo.domain.model.PdfOverlayKind
import com.joseleandro.scaniqo.domain.model.PdfTextVerticalAlignment

@Composable
internal fun FillMarkTypeSelector(
    kind: PdfOverlayKind,
    editable: Boolean,
    onMode: (PdfOverlayKind) -> Unit,
    modifier: Modifier = Modifier,
) {
    SingleChoiceSegmentedButtonRow(modifier) {
        listOf(PdfOverlayKind.CHECKMARK, PdfOverlayKind.CROSSMARK).forEachIndexed { index, choice ->
            SegmentedButton(
                selected = kind == choice,
                onClick = { onMode(choice) },
                enabled = editable,
                shape = SegmentedButtonDefaults.itemShape(index, 2),
                modifier = Modifier.heightIn(min = 48.dp),
                label = {
                    Text(
                        stringResource(if (choice == PdfOverlayKind.CHECKMARK)
                            R.string.fill_v4_mark_check else R.string.fill_v4_mark_cross),
                        maxLines = 1,
                    )
                },
                icon = {
                    Icon(
                        painterResource(if (choice == PdfOverlayKind.CHECKMARK)
                            CoreResourcesR.drawable.ic_check_24 else R.drawable.ic_fill_crossmark),
                        null,
                        Modifier.size(18.dp),
                    )
                },
            )
        }
    }
}

@Composable
internal fun FillMarkSizePanel(
    draft: PdfOverlay,
    editable: Boolean,
    numericSizeBuffer: String?,
    onStartNumericSize: () -> Unit,
    onNumericSizeChange: (String) -> Unit,
    onConfirmNumericSize: () -> Unit,
    numericSizeError: Boolean,
    onUpdate: (PdfOverlay) -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    val sizeFieldFocus = remember(draft.id) { FocusRequester() }
    LaunchedEffect(numericSizeBuffer != null) {
        if (numericSizeBuffer != null) sizeFieldFocus.requestFocus()
    }
    if (draft.kind in textKinds) {
        val smaller = stringResource(R.string.fill_mark_font_smaller)
        val larger = stringResource(R.string.fill_mark_font_larger)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(
                onClick = { onUpdate(draft.withFillMarkFontSize(draft.fontSize - 1f)) },
                enabled = editable && draft.fontSize > 6f,
                modifier = Modifier.size(48.dp),
                contentPadding = PaddingValues(0.dp),
            ) {
                Icon(painterResource(R.drawable.ic_fill_decrease), smaller, Modifier.size(20.dp))
            }
            if (numericSizeBuffer == null) OutlinedButton(
                onClick = onStartNumericSize,
                enabled = editable,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.fill_v3_size_value, formatFillMarkSize(draft.fontSize, locale)))
            } else OutlinedTextField(
                value = numericSizeBuffer,
                onValueChange = onNumericSizeChange,
                enabled = editable,
                singleLine = true,
                isError = numericSizeError,
                modifier = Modifier.weight(1f).focusRequester(sizeFieldFocus),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onConfirmNumericSize() }),
                label = { Text(stringResource(R.string.fill_v3_points)) },
            )
            FilledTonalButton(
                onClick = { onUpdate(draft.withFillMarkFontSize(draft.fontSize + 1f)) },
                enabled = editable && draft.fontSize < 72f,
                modifier = Modifier.size(48.dp),
                contentPadding = PaddingValues(0.dp),
            ) {
                Icon(painterResource(CoreResourcesR.drawable.add_24px), larger, Modifier.size(20.dp))
            }
        }
        if (numericSizeBuffer != null) {
            if (numericSizeError) Text(
                stringResource(R.string.fill_v3_size_error),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    } else {
        val smaller = stringResource(R.string.fill_mark_mark_smaller)
        val larger = stringResource(R.string.fill_mark_mark_larger)
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.size(48.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painterResource(if (draft.kind == PdfOverlayKind.CHECKMARK)
                            CoreResourcesR.drawable.ic_check_24 else R.drawable.ic_fill_crossmark),
                        null, Modifier.size(24.dp), tint = Color(draft.color),
                    )
                }
            }
            FilledTonalButton(
                onClick = { onUpdate(draft.scaledFillMarkAroundCenter(.9f)) },
                enabled = editable && draft.scaledFillMarkAroundCenter(.9f) != draft,
                modifier = Modifier.size(48.dp),
                contentPadding = PaddingValues(0.dp),
            ) {
                Icon(painterResource(R.drawable.ic_fill_decrease), smaller, Modifier.size(20.dp))
            }
            FilledTonalButton(
                onClick = { onUpdate(draft.scaledFillMarkAroundCenter(1.1f)) },
                enabled = editable && draft.scaledFillMarkAroundCenter(1.1f) != draft,
                modifier = Modifier.size(48.dp),
                contentPadding = PaddingValues(0.dp),
            ) {
                Icon(painterResource(CoreResourcesR.drawable.add_24px), larger, Modifier.size(20.dp))
            }
        }
    }
}


private val fillMarkPaletteColors = listOf(
    0xFF202124.toInt() to R.string.overlay_black,
    0xFF1565C0.toInt() to R.string.overlay_blue,
    0xFFC62828.toInt() to CoreResourcesR.string.overlay_red,
    0xFFFFCA28.toInt() to R.string.overlay_yellow,
    0xFF2E7D32.toInt() to CoreResourcesR.string.fill_mark_green,
    0xFF6A1B9A.toInt() to R.string.fill_mark_purple,
    0xFF616161.toInt() to R.string.fill_mark_gray,
    0xFFFFFFFF.toInt() to R.string.fill_mark_white,
)


@Composable
internal fun FillMarkColorGrid(
    colors: List<Pair<Int, String>>,
    selectedColor: Int,
    editable: Boolean,
    onSelectColor: (Int) -> Unit,
) {
    colors.chunked(4).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            row.forEach { (color, label) ->
                val borderWidth by animateDpAsState(
                    if (selectedColor == color) 3.dp else 1.dp,
                    animationSpec = tween(160), label = "fill color",
                )
                Surface(
                    onClick = { onSelectColor(color) }, enabled = editable, shape = CircleShape,
                    color = Color(color),
                    border = BorderStroke(borderWidth, if (selectedColor == color)
                        MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                    modifier = Modifier.size(48.dp).testTag("fill_color_option_${color.toUInt().toString(16)}").semantics {
                        contentDescription = label
                        selected = selectedColor == color
                    },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (selectedColor == color) Icon(
                            painterResource(CoreResourcesR.drawable.ic_check_24), null,
                            Modifier.size(18.dp).testTag("fill_color_selected_check"),
                            tint = if (Color(color).compositeOver(MaterialTheme.colorScheme.surfaceContainerHigh).luminance() > .179f)
                                Color.Black else Color.White,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun FillMarkFormatToggle(
    label: String,
    icon: Int,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    horizontal: Boolean = false,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceContainer,
        border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = modifier.heightIn(min = 56.dp).semantics { this.selected = selected },
    ) {
        if (horizontal) {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(painterResource(icon), null, Modifier.size(20.dp))
                Text(label, style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            Column(
                Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(painterResource(icon), null, Modifier.size(20.dp))
                Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 3,
                    overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
internal fun FillMarkBoxPanel(draft: PdfOverlay, editable: Boolean, onUpdate: (PdfOverlay) -> Unit) {
    Text(stringResource(R.string.fill_v3_text_box_hint), style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.fill_text_auto_box), modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium)
        Switch(
            checked = draft.autoSizeText, enabled = editable,
            onCheckedChange = { onUpdate(draft.copy(autoSizeText = it, wrapText = true)) },
        )
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.fill_v3_wrap), modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium)
        Switch(
            checked = draft.wrapText, enabled = editable && !draft.autoSizeText,
            onCheckedChange = { onUpdate(draft.copy(wrapText = it)) },
        )
    }
    Text(stringResource(R.string.fill_text_vertical), style = MaterialTheme.typography.labelLarge)
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        listOf(
            PdfTextVerticalAlignment.TOP to R.string.fill_text_top,
            PdfTextVerticalAlignment.CENTER to R.string.fill_text_middle,
            PdfTextVerticalAlignment.BOTTOM to R.string.fill_text_bottom,
        ).forEachIndexed { index, (alignment, label) ->
            SegmentedButton(
                selected = draft.textVerticalAlignment == alignment,
                onClick = { onUpdate(draft.copy(textVerticalAlignment = alignment)) },
                enabled = editable, shape = SegmentedButtonDefaults.itemShape(index, 3),
                label = { Text(stringResource(label), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            )
        }
    }
}

@Composable
internal fun FillMarkScopePanel(
    allPages: Boolean,
    pageCount: Int,
    editable: Boolean,
    onAllPages: (Boolean) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = !allPages, onClick = { onAllPages(false) }, enabled = editable,
            label = { Text(stringResource(R.string.fill_mark_this_page)) },
        )
        FilterChip(
            selected = allPages, onClick = { onAllPages(true) }, enabled = editable,
            label = { Text(stringResource(R.string.fill_mark_every_page)) },
        )
    }
    Text(
        stringResource(R.string.fill_v3_page_count, if (allPages) pageCount else 1),
        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}


@Composable
internal fun FillMarkColorPanel(
    draft: PdfOverlay,
    editable: Boolean,
    onSelectColor: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Keep an imported color available for this edit, even after another swatch is chosen.
    val originalCustomColor = remember(draft.id) {
        draft.color.takeUnless { color -> fillMarkPaletteColors.any { it.first == color } }
    }
    val palette = fillMarkPaletteColors.map { it.first to stringResource(it.second) } +
        listOfNotNull(originalCustomColor?.let { it to stringResource(R.string.overlay_color) })
    Column(
        modifier.fillMaxWidth().testTag("fill_color_palette"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FillMarkColorGrid(palette, draft.color, editable, onSelectColor)
    }
}

@Composable
internal fun FillMarkStylePanel(
    draft: PdfOverlay,
    editable: Boolean,
    onUpdate: (PdfOverlay) -> Unit,
    modifier: Modifier = Modifier,
) {
    val expandedText = LocalConfiguration.current.fontScale >= 1.3f
    val options = listOf(
        Triple(R.string.fill_v4_bold, R.drawable.ic_fill_bold, draft.bold),
        Triple(R.string.fill_v4_italic, R.drawable.ic_fill_italic, draft.italic),
        Triple(R.string.fill_v4_underline, R.drawable.ic_fill_underline, draft.underlined),
    )
    val option: @Composable (Int, Modifier) -> Unit = { index, optionModifier ->
        val (label, icon, selected) = options[index]
        FillMarkFormatToggle(
            stringResource(label), icon, selected, editable,
            onClick = {
                onUpdate(when (index) {
                    0 -> draft.copy(bold = !draft.bold)
                    1 -> draft.copy(italic = !draft.italic)
                    else -> draft.copy(underlined = !draft.underlined)
                })
            },
            modifier = optionModifier,
            horizontal = expandedText,
        )
    }
    if (expandedText) {
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            options.indices.forEach { option(it, Modifier.fillMaxWidth()) }
        }
    } else {
        Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.indices.forEach { option(it, Modifier.weight(1f)) }
        }
    }
}
