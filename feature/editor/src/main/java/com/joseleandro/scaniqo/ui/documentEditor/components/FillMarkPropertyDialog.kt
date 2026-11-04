package com.joseleandro.scaniqo.ui.documentEditor.components

import com.joseleandro.scaniqo.feature.editor.R

import android.content.res.Configuration
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import com.joseleandro.scaniqo.domain.model.PdfOverlay
import com.joseleandro.scaniqo.domain.model.PdfOverlayKind
import com.joseleandro.scaniqo.ui.components.DialogActions
import com.joseleandro.scaniqo.ui.theme.ScaniqoTheme
import kotlin.math.roundToInt

@Composable
internal fun FillMarkPropertyDialog(
    property: FillMarkProperty,
    draft: PdfOverlay,
    editable: Boolean,
    onUpdate: (PdfOverlay) -> Unit,
    onCancel: () -> Unit,
    onApply: () -> Unit,
    modifier: Modifier = Modifier,
    onSelectColor: (Int) -> Unit = { onUpdate(draft.copy(color = it)) },
    allPages: Boolean = false,
    pageCount: Int = 1,
    onAllPages: (Boolean) -> Unit = {},
    maxHeight: Dp = 420.dp,
    onHeightChange: (Dp) -> Unit = {},
) {
    if (property == FillMarkProperty.TRANSFORM) {
        FillMarkTransformSheet(
            draft, editable, onUpdate, onCancel, onApply,
            modifier = modifier, maxHeight = maxHeight, onHeightChange = onHeightChange,
        )
        return
    }
    var numericSizeBuffer by rememberSaveable(draft.id, property) { mutableStateOf<String?>(null) }
    val numericSizeError = numericSizeBuffer?.let { parseFillMarkSize(it) == null } == true
    val locale = LocalConfiguration.current.locales[0]
    AlertDialog(
        onDismissRequest = onApply,
        modifier = modifier.testTag("fill_property_dialog_${property.name}"),
        title = { Text(stringResource(propertyTitle(property))) },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                when (property) {
                    FillMarkProperty.SIZE -> {
                        FillMarkSizePanel(
                            draft, editable, numericSizeBuffer,
                            onStartNumericSize = { numericSizeBuffer = formatFillMarkSize(draft.fontSize, locale) },
                            onNumericSizeChange = { input ->
                                if (acceptsFillMarkSizeInput(input)) {
                                    numericSizeBuffer = input
                                    parseFillMarkSize(input)?.let { onUpdate(draft.withFillMarkFontSize(it)) }
                                }
                            },
                            onConfirmNumericSize = { if (!numericSizeError) numericSizeBuffer = null },
                            numericSizeError = numericSizeError,
                            onUpdate = { numericSizeBuffer = null; onUpdate(it) },
                        )
                        if (numericSizeBuffer == null) FillMarkSizeSlider(draft, editable, onUpdate)
                    }
                    FillMarkProperty.COLOR -> FillMarkColorPanel(draft, editable, onSelectColor)
                    FillMarkProperty.OPACITY -> {
                        Text(stringResource(R.string.fill_mark_opacity_value, (draft.opacity * 100).roundToInt()),
                            style = MaterialTheme.typography.titleMedium)
                        DocumentScaleSlider(
                            value = draft.opacity, enabled = editable,
                            onValueChange = { onUpdate(draft.copy(opacity = it)) },
                            onValueChangeFinished = {},
                            valueRange = .05f..1f,
                            label = stringResource(R.string.fill_property_opacity),
                            testTag = "fill_opacity_slider", referenceValue = 1f,
                        )
                    }
                    FillMarkProperty.STYLE -> FillMarkStylePanel(draft, editable, onUpdate)
                    FillMarkProperty.ALIGNMENT -> FillMarkTextAlignmentControls(draft, editable, onUpdate, showAutoSize = false)
                    FillMarkProperty.BOX -> FillMarkBoxPanel(draft, editable, onUpdate)
                    FillMarkProperty.TYPE -> FillMarkTypeSelector(
                        draft.kind, editable, { onUpdate(draft.copy(kind = it)) }, Modifier.fillMaxWidth(),
                    )
                    FillMarkProperty.SCOPE -> FillMarkScopePanel(allPages, pageCount, editable, onAllPages)
                    FillMarkProperty.TRANSFORM -> Unit
                }
            }
        },
        confirmButton = {
            DialogActions(
                confirmText = stringResource(R.string.fill_mark_apply),
                onConfirm = onApply,
                dismissText = stringResource(R.string.tools_cancel),
                onDismiss = onCancel,
                confirmEnabled = editable && !numericSizeError,
            )
        },
        properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
    )
}

private fun propertyTitle(property: FillMarkProperty): Int = when (property) {
    FillMarkProperty.SIZE -> R.string.fill_v4_size
    FillMarkProperty.COLOR -> R.string.fill_v4_color
    FillMarkProperty.OPACITY -> R.string.fill_property_opacity
    FillMarkProperty.STYLE -> R.string.fill_property_style
    FillMarkProperty.ALIGNMENT -> R.string.fill_text_align_action
    FillMarkProperty.BOX -> R.string.fill_v3_text_box
    FillMarkProperty.TYPE -> R.string.fill_property_type
    FillMarkProperty.SCOPE -> R.string.fill_v3_repeat_pages
    FillMarkProperty.TRANSFORM -> R.string.fill_property_transform
}

@Composable
internal fun FillMarkSizeSlider(
    draft: PdfOverlay,
    enabled: Boolean,
    onUpdate: (PdfOverlay) -> Unit,
    modifier: Modifier = Modifier,
    proportionalText: Boolean = false,
    onGestureActiveChange: (Boolean) -> Unit = {},
) {
    val interactionSource = remember(draft.id) { MutableInteractionSource() }
    val dragging by interactionSource.collectIsDraggedAsState()
    val currentGestureCallback by rememberUpdatedState(onGestureActiveChange)
    LaunchedEffect(dragging, enabled) { currentGestureCallback(dragging && enabled) }
    DisposableEffect(draft.id) { onDispose { currentGestureCallback(false) } }
    val mapping = remember(draft.id, draft.kind, proportionalText) { FillMarkSizeMapping(draft, proportionalText) }
    val range = mapping.valueRange
    val value = mapping.value(draft)
    DocumentScaleSlider(
        value = value.coerceIn(range),
        enabled = enabled && range.endInclusive > range.start,
        onValueChange = { onUpdate(mapping.resize(draft, it)) },
        onValueChangeFinished = { onGestureActiveChange(false) },
        modifier = modifier.fillMaxWidth(),
        valueRange = if (range.endInclusive > range.start) range else range.start..(range.start + .001f),
        label = stringResource(if (mapping.isText) R.string.fill_v4_size else R.string.fill_mark_mark_size),
        testTag = "fill_size_slider", referenceValue = null,
        interactionSource = interactionSource,
        steps = mapping.steps,
    )
}

@Preview(name = "Cores do elemento", widthDp = 320, locale = "pt-rBR")
@Preview(name = "Cores no tema escuro", widthDp = 320, locale = "pt-rBR", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun FillMarkColorDialogPreview() {
    ScaniqoTheme {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.extraLarge) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                Text(stringResource(R.string.fill_v4_color), style = MaterialTheme.typography.headlineSmall)
                FillMarkColorPanel(PdfOverlay("preview", PdfOverlayKind.TEXT), true, {})
                DialogActions(stringResource(R.string.fill_mark_apply), {}, stringResource(R.string.tools_cancel), {})
            }
        }
    }
}

@Preview(name = "Estilo com fonte ampliada", widthDp = 320, fontScale = 1.5f, locale = "pt-rBR")
@Composable
private fun FillMarkStyleDialogPreview() {
    ScaniqoTheme {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.extraLarge) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                Text(stringResource(R.string.fill_property_style), style = MaterialTheme.typography.headlineSmall)
                FillMarkStylePanel(PdfOverlay("preview", PdfOverlayKind.TEXT), true, {})
                DialogActions(stringResource(R.string.fill_mark_apply), {}, stringResource(R.string.tools_cancel), {})
            }
        }
    }
}
