package com.joseleandro.scaniqo.ui.documentSignature

import com.joseleandro.scaniqo.feature.editor.R

import android.content.res.Configuration
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.joseleandro.scaniqo.domain.model.PdfContentPosition
import com.joseleandro.scaniqo.domain.model.PdfSignaturePlacement
import com.joseleandro.scaniqo.ui.documentCommon.components.DocumentTransformTabs
import com.joseleandro.scaniqo.ui.documentEditor.components.DocumentPositionPad
import com.joseleandro.scaniqo.ui.documentEditor.components.DocumentScaleSlider
import com.joseleandro.scaniqo.ui.documentEditor.components.RotationDial
import com.joseleandro.scaniqo.ui.documentEditor.event.DocumentToolsEvent
import com.joseleandro.scaniqo.ui.documentEditor.util.resizeSignatureAroundCenter
import com.joseleandro.scaniqo.ui.documentEditor.util.signatureWidthRange
import com.joseleandro.scaniqo.ui.signatures.SignatureImage
import com.joseleandro.scaniqo.ui.theme.ScaniqoTheme
import kotlin.math.roundToInt

@Composable
internal fun DocumentSignatureAdjustmentPanel(
    draft: PdfSignaturePlacement,
    mode: SignatureAdjustmentMode,
    enabled: Boolean,
    onModeChange: (SignatureAdjustmentMode) -> Unit,
    onEvent: (DocumentToolsEvent) -> Unit,
    modifier: Modifier = Modifier,
    onAdjustmentActiveChange: (Boolean) -> Unit = {},
    compact: Boolean = false,
) {
    Column(modifier.fillMaxWidth()) {
        SignatureAdjustmentTabs(mode, enabled, onModeChange, compact = compact)
        SignatureAdjustmentContent(
            draft, mode, enabled, onEvent,
            onAdjustmentActiveChange = onAdjustmentActiveChange,
            compact = compact,
        )
    }
}

@Composable
internal fun SignatureAdjustmentTabs(
    mode: SignatureAdjustmentMode,
    enabled: Boolean,
    onModeChange: (SignatureAdjustmentMode) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    DocumentTransformTabs(
        selectedTabIndex = mode.ordinal,
        enabled = enabled,
        onTabSelected = { onModeChange(SignatureAdjustmentMode.entries[it]) },
        modifier = modifier,
        compact = compact,
        testTagPrefix = "signature_adjustment",
    )
}

@Composable
internal fun SignatureAdjustmentContent(
    draft: PdfSignaturePlacement,
    mode: SignatureAdjustmentMode,
    enabled: Boolean,
    onEvent: (DocumentToolsEvent) -> Unit,
    modifier: Modifier = Modifier,
    onAdjustmentActiveChange: (Boolean) -> Unit = {},
    compact: Boolean = false,
) {
    val interactionSource = remember(draft.id, mode) { MutableInteractionSource() }
    val dragging by interactionSource.collectIsDraggedAsState()
    val currentAdjustmentActiveChange by rememberUpdatedState(onAdjustmentActiveChange)
    LaunchedEffect(dragging, enabled) { currentAdjustmentActiveChange(dragging && enabled) }
    DisposableEffect(draft.id, mode) {
        onDispose { currentAdjustmentActiveChange(false) }
    }
    fun transform(value: PdfSignaturePlacement) {
        onEvent(DocumentToolsEvent.TransformSignature(
            value.x, value.y, value.width, value.rotationDegrees,
            height = value.height, stretchToBounds = value.stretchToBounds,
        ))
    }
    fun apply(width: Float = draft.width, rotation: Float = draft.rotationDegrees) {
        transform(resizeSignatureAroundCenter(draft, width, rotation))
    }
    val sizeLabel = stringResource(R.string.signature_resize_handle)
    val rotationLabel = stringResource(R.string.signature_rotate_handle)
    val smaller = stringResource(R.string.signature_shrink)
    val larger = stringResource(R.string.signature_grow)
    val rotateLeft = stringResource(R.string.signature_rotate_left)
    val rotateRight = stringResource(R.string.signature_rotate_right)
    Column(
        modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = if (compact) 4.dp else 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (mode) {
            SignatureAdjustmentMode.MOVE -> {
                val mapping = signaturePositionMapping(draft)
                val hint = stringResource(R.string.signature_position_hint)
                DocumentPositionPad(
                    position = mapping.position(draft),
                    enabled = enabled,
                    horizontalEnabled = mapping.horizontalTravel > .0001f,
                    verticalEnabled = mapping.verticalTravel > .0001f,
                    onPositionChange = { transform(mapping.place(draft, it)) },
                    onGestureStart = { onAdjustmentActiveChange(true) },
                    onGestureEnd = { onAdjustmentActiveChange(false) },
                    compact = compact,
                    itemSize = DpSize(72.dp, 44.dp),
                    description = hint,
                    testTagPrefix = "signature",
                    itemContent = { itemModifier ->
                        Surface(
                            modifier = itemModifier,
                            color = Color.White,
                            shape = RoundedCornerShape(7.dp),
                        ) {
                            SignatureImage(
                                draft.imageUri,
                                Modifier.padding(6.dp).rotate(draft.rotationDegrees),
                            )
                        }
                    },
                )
                Text(
                    hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(
                    onClick = { transform(mapping.place(draft, PdfContentPosition())) },
                    enabled = enabled,
                    modifier = Modifier.testTag("signature_position_center"),
                ) { Text(stringResource(R.string.tools_align_center)) }
            }
            SignatureAdjustmentMode.SIZE -> {
                val range = signatureWidthRange(draft)
                Text(
                    stringResource(R.string.signature_size_value, (draft.width * 100).roundToInt()),
                    style = MaterialTheme.typography.titleSmall,
                )
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { apply(width = draft.width - .02f) },
                        enabled = enabled && draft.width > range.start + .0001f,
                        modifier = Modifier.testTag("signature_size_decrease")
                            .semantics { contentDescription = smaller },
                    ) { Text("−", style = MaterialTheme.typography.headlineSmall) }
                    DocumentScaleSlider(
                        value = draft.width.coerceIn(range),
                        enabled = enabled,
                        onValueChange = { apply(width = it) },
                        onValueChangeFinished = { onAdjustmentActiveChange(false) },
                        modifier = Modifier.weight(1f),
                        valueRange = range,
                        label = sizeLabel,
                        testTag = "signature_size_slider",
                        referenceValue = null,
                        interactionSource = interactionSource,
                    )
                    IconButton(
                        onClick = { apply(width = draft.width + .02f) },
                        enabled = enabled && draft.width < range.endInclusive - .0001f,
                        modifier = Modifier.testTag("signature_size_increase")
                            .semantics { contentDescription = larger },
                    ) { Text("+", style = MaterialTheme.typography.headlineSmall) }
                }
                Text(
                    stringResource(R.string.signature_scale_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            SignatureAdjustmentMode.ROTATE -> {
                RotationDial(
                    rotationDegrees = draft.rotationDegrees,
                    enabled = enabled,
                    onRotationChange = { apply(rotation = it) },
                    onGestureStart = { onAdjustmentActiveChange(true) },
                    onGestureEnd = { onAdjustmentActiveChange(false) },
                    description = rotationLabel,
                    leftLabel = rotateLeft,
                    rightLabel = rotateRight,
                    compact = compact,
                )
                Text(
                    stringResource(R.string.signature_rotation_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(
                    onClick = { apply(rotation = 0f) },
                    enabled = enabled,
                    modifier = Modifier.testTag("signature_rotation_reset"),
                ) { Text(stringResource(R.string.signature_rotate_reset)) }
            }
        }
    }
}

@Preview(name = "Mover assinatura", widthDp = 360, locale = "pt-rBR")
@Preview(name = "Mover assinatura fonte grande", widthDp = 320, fontScale = 1.5f, locale = "pt-rBR")
@Preview(name = "Mover assinatura escuro", widthDp = 360, uiMode = Configuration.UI_MODE_NIGHT_YES, locale = "pt-rBR")
@Composable
private fun SignatureMovePanelPreview() {
    SignatureAdjustmentPreview(SignatureAdjustmentMode.MOVE)
}

@Preview(name = "Tamanho assinatura", widthDp = 360, locale = "pt-rBR")
@Composable
private fun SignatureSizePanelPreview() {
    SignatureAdjustmentPreview(SignatureAdjustmentMode.SIZE)
}

@Preview(name = "Girar assinatura", widthDp = 360, locale = "pt-rBR")
@Composable
private fun SignatureRotationPanelPreview() {
    SignatureAdjustmentPreview(SignatureAdjustmentMode.ROTATE)
}

@Composable
private fun SignatureAdjustmentPreview(initialMode: SignatureAdjustmentMode, modifier: Modifier = Modifier) {
    var mode by remember { mutableStateOf(initialMode) }
    var placement by remember { mutableStateOf(PdfSignaturePlacement("preview", "file:///signature.png")) }
    ScaniqoTheme {
        Surface(modifier) {
            DocumentSignatureAdjustmentPanel(
                draft = placement,
                mode = mode,
                enabled = true,
                onModeChange = { mode = it },
                onEvent = { event ->
                    if (event is DocumentToolsEvent.TransformSignature) {
                        placement = placement.copy(
                            x = event.x, y = event.y, width = event.width,
                            height = event.height ?: placement.height,
                            rotationDegrees = event.rotationDegrees,
                        )
                    }
                },
            )
        }
    }
}
