package com.joseleandro.scaniqo.ui.documentSignature

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.joseleandro.scaniqo.R
import com.joseleandro.scaniqo.domain.model.PdfSignaturePlacement
import com.joseleandro.scaniqo.ui.documentEditor.event.DocumentToolsEvent
import com.joseleandro.scaniqo.ui.documentEditor.util.resizeSignatureAroundCenter
import com.joseleandro.scaniqo.ui.documentEditor.util.signatureWidthRange
import kotlin.math.roundToInt

@Composable
internal fun DocumentSignatureAdjustmentPanel(
    draft: PdfSignaturePlacement,
    mode: SignatureAdjustmentMode,
    enabled: Boolean,
    onModeChange: (SignatureAdjustmentMode) -> Unit,
    onEvent: (DocumentToolsEvent) -> Unit,
    onCrop: () -> Unit,
    onAdjustmentActiveChange: (Boolean) -> Unit = {},
) {
    val interactionSource = remember(draft.id, mode) { MutableInteractionSource() }
    val dragging by interactionSource.collectIsDraggedAsState()
    val currentAdjustmentActiveChange by rememberUpdatedState(onAdjustmentActiveChange)
    LaunchedEffect(dragging, enabled) { currentAdjustmentActiveChange(dragging && enabled) }
    DisposableEffect(draft.id, mode) {
        onDispose { currentAdjustmentActiveChange(false) }
    }
    fun apply(width: Float = draft.width, rotation: Float = draft.rotationDegrees) {
        val value = resizeSignatureAroundCenter(draft, width, rotation)
        onEvent(DocumentToolsEvent.TransformSignature(
            value.x, value.y, value.width, value.rotationDegrees,
            height = value.height, stretchToBounds = value.stretchToBounds,
        ))
    }
    val sizeLabel = stringResource(R.string.signature_resize_handle)
    val rotationLabel = stringResource(R.string.signature_rotate_handle)
    val smaller = stringResource(R.string.signature_shrink)
    val larger = stringResource(R.string.signature_grow)
    val rotateLeft = stringResource(R.string.signature_rotate_left)
    val rotateRight = stringResource(R.string.signature_rotate_right)
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SignatureAdjustmentMode.entries.forEach { item ->
                val selected = item == mode
                Surface(
                    selected = selected,
                    onClick = { onModeChange(item) },
                    enabled = enabled,
                    shape = MaterialTheme.shapes.small,
                    color = if (selected) MaterialTheme.colorScheme.secondaryContainer
                        else MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).semantics { role = Role.Tab },
                ) {
                    Box(Modifier.padding(horizontal = 6.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(when (item) {
                                SignatureAdjustmentMode.MOVE -> R.string.signature_adjust_move
                                SignatureAdjustmentMode.SIZE -> R.string.signature_adjust_size
                                SignatureAdjustmentMode.ROTATE -> R.string.signature_adjust_rotate
                            }),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }
        when (mode) {
            SignatureAdjustmentMode.MOVE -> Text(
                stringResource(R.string.signature_gesture_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SignatureAdjustmentMode.SIZE -> {
                Text(
                    stringResource(R.string.signature_size_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val range = signatureWidthRange(draft)
                Text(
                    stringResource(R.string.signature_size_value, (draft.width * 100).roundToInt()),
                    style = MaterialTheme.typography.labelLarge,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { apply(width = draft.width - .02f) },
                        enabled = enabled && draft.width > range.start + .0001f,
                        modifier = Modifier.semantics { contentDescription = smaller },
                    ) { Text("−", style = MaterialTheme.typography.headlineSmall) }
                    Slider(
                        value = draft.width.coerceIn(range),
                        onValueChange = { apply(width = it) },
                        interactionSource = interactionSource,
                        valueRange = range,
                        enabled = enabled,
                        modifier = Modifier.weight(1f).semantics { contentDescription = sizeLabel },
                    )
                    IconButton(
                        onClick = { apply(width = draft.width + .02f) },
                        enabled = enabled && draft.width < range.endInclusive - .0001f,
                        modifier = Modifier.semantics { contentDescription = larger },
                    ) { Text("+", style = MaterialTheme.typography.headlineSmall) }
                }
            }
            SignatureAdjustmentMode.ROTATE -> {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.signature_rotation, draft.rotationDegrees.roundToInt()),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    TextButton(onClick = { apply(rotation = 0f) }, enabled = enabled) {
                        Text(stringResource(R.string.signature_rotate_reset))
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { apply(rotation = draft.rotationDegrees - 15f) },
                        enabled = enabled,
                        modifier = Modifier.semantics { contentDescription = rotateLeft },
                    ) { Text("−15°", style = MaterialTheme.typography.labelMedium) }
                    Slider(
                        value = draft.rotationDegrees.coerceIn(-180f, 180f),
                        onValueChange = { apply(rotation = it.coerceIn(-179.99f, 179.99f)) },
                        interactionSource = interactionSource,
                        valueRange = -180f..180f,
                        enabled = enabled,
                        modifier = Modifier.weight(1f).semantics { contentDescription = rotationLabel },
                    )
                    IconButton(
                        onClick = { apply(rotation = draft.rotationDegrees + 15f) },
                        enabled = enabled,
                        modifier = Modifier.semantics { contentDescription = rotateRight },
                    ) { Text("+15°", style = MaterialTheme.typography.labelMedium) }
                }
            }
        }
        OutlinedButton(onClick = onCrop, enabled = enabled) {
            Text(stringResource(R.string.signature_crop))
        }
    }
}
