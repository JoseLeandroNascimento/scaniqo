package com.joseleandro.scaniqo.ui.documentSignature

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import com.joseleandro.scaniqo.ui.components.DialogActions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import com.joseleandro.scaniqo.R
import com.joseleandro.scaniqo.ui.signatures.SignatureImage

/** O retângulo usa coordenadas da imagem; o recorte é aplicado somente à cópia no documento. */
@Composable
internal fun SignatureCropDialog(
    uri: String,
    aspectRatio: Float,
    onDismiss: () -> Unit,
    onApply: (left: Float, top: Float, right: Float, bottom: Float) -> Unit,
) {
    var left by rememberSaveable(uri) { mutableFloatStateOf(0f) }
    var top by rememberSaveable(uri) { mutableFloatStateOf(0f) }
    var right by rememberSaveable(uri) { mutableFloatStateOf(1f) }
    var bottom by rememberSaveable(uri) { mutableFloatStateOf(1f) }
    val accent = MaterialTheme.colorScheme.primary
    var imageAspect by remember(uri) { mutableStateOf<Float?>(null) }
    // O posicionamento pode ter largura e altura independentes; o recorte usa a imagem original.
    val aspect = imageAspect ?: aspectRatio.takeIf { it.isFinite() && it > 0f } ?: 3f
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.signature_crop_title)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(R.string.signature_crop_hint), style = MaterialTheme.typography.bodySmall)
                BoxWithConstraints(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                    val width = minOf(maxWidth, maxHeight * aspect)
                    Box(Modifier.size(width, width / aspect).background(Color.White)) {
                        SignatureImage(
                            uri, Modifier.fillMaxSize(),
                            onImageBitmapChanged = { bitmap ->
                                imageAspect = bitmap?.let { it.width.toFloat() / it.height }
                            },
                        )
                        Canvas(Modifier.fillMaxSize()) {
                            val x = left * size.width
                            val y = top * size.height
                            val endX = right * size.width
                            val endY = bottom * size.height
                            val shade = Color.Black.copy(alpha = .45f)
                            drawRect(shade, size = Size(size.width, y))
                            drawRect(shade, topLeft = Offset(0f, endY), size = Size(size.width, size.height - endY))
                            drawRect(shade, topLeft = Offset(0f, y), size = Size(x, endY - y))
                            drawRect(shade, topLeft = Offset(endX, y), size = Size(size.width - endX, endY - y))
                            drawRect(accent, topLeft = Offset(x, y), size = Size(endX - x, endY - y), style = Stroke(2.dp.toPx()))
                        }
                    }
                }
                CropEdgeSlider(stringResource(R.string.signature_crop_left), left, 0f..(right - .05f)) { left = it }
                CropEdgeSlider(stringResource(R.string.signature_crop_right), right, (left + .05f)..1f) { right = it }
                CropEdgeSlider(stringResource(R.string.signature_crop_top), top, 0f..(bottom - .05f)) { top = it }
                CropEdgeSlider(stringResource(R.string.signature_crop_bottom), bottom, (top + .05f)..1f) { bottom = it }
                TextButton(onClick = { left = 0f; top = 0f; right = 1f; bottom = 1f }) {
                    Text(stringResource(R.string.signature_crop_reset))
                }
            }
        },
        confirmButton = {
            DialogActions(
                confirmText = stringResource(R.string.signature_crop_apply),
                onConfirm = { onApply(left, top, right, bottom) },
                dismissText = stringResource(R.string.signature_cancel),
                onDismiss = onDismiss,
                confirmEnabled = imageAspect != null,
            )
        },
        properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
    )
}

@Composable
private fun CropEdgeSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Slider(
            value = value.coerceIn(range),
            valueRange = range,
            onValueChange = onChange,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
        )
    }
}
