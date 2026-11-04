package com.joseleandro.scaniqo.ui.signatures.crop

import com.joseleandro.scaniqo.core.resources.R as CoreResourcesR
import com.joseleandro.scaniqo.feature.signatures.R

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.SecureFlagPolicy
import com.joseleandro.scaniqo.domain.model.PdfSignatureCrop
import com.joseleandro.scaniqo.ui.theme.ScaniqoTheme

/** O retângulo usa coordenadas da imagem; o recorte é aplicado somente à cópia no documento. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignatureCropDialog(
    uri: String,
    aspectRatio: Float,
    onDismiss: () -> Unit,
    onApply: (left: Float, top: Float, right: Float, bottom: Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var dragging by remember { mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        sheetMaxWidth = 640.dp,
        sheetGesturesEnabled = !dragging,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        properties = ModalBottomSheetProperties(securePolicy = SecureFlagPolicy.SecureOn),
    ) {
        SignatureCropContent(
            uri = uri,
            aspectRatio = aspectRatio,
            onDismiss = onDismiss,
            onApply = { onApply(it.left, it.top, it.right, it.bottom) },
            onGestureActiveChange = { dragging = it },
        )
    }
}

@Composable
internal fun SignatureCropContent(
    uri: String,
    aspectRatio: Float,
    onDismiss: () -> Unit,
    onApply: (PdfSignatureCrop) -> Unit,
    modifier: Modifier = Modifier,
    onGestureActiveChange: (Boolean) -> Unit = {},
) {
    var left by rememberSaveable(uri) { mutableFloatStateOf(0f) }
    var top by rememberSaveable(uri) { mutableFloatStateOf(0f) }
    var right by rememberSaveable(uri) { mutableFloatStateOf(1f) }
    var bottom by rememberSaveable(uri) { mutableFloatStateOf(1f) }
    val inspection = LocalInspectionMode.current
    val fontScale = LocalDensity.current.fontScale
    var imageReady by remember(uri, inspection) { mutableStateOf(inspection) }
    val crop = PdfSignatureCrop(left, top, right, bottom)
    fun update(next: PdfSignatureCrop) {
        left = next.left
        top = next.top
        right = next.right
        bottom = next.bottom
    }
    BoxWithConstraints(modifier.fillMaxWidth().testTag("signature_crop_sheet")) {
        val previewHeight = (maxHeight * .46f / fontScale.coerceIn(1f, 1.6f)).coerceIn(160.dp, 360.dp)
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(
                Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(stringResource(R.string.signature_crop_title), style = MaterialTheme.typography.titleLarge)
                Text(
                    stringResource(R.string.signature_crop_drag_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SignatureCropEditor(
                    uri = uri,
                    aspectRatio = aspectRatio,
                    crop = crop,
                    onCropChange = ::update,
                    onImageReady = { imageReady = it },
                    onGestureActiveChange = onGestureActiveChange,
                    modifier = Modifier.fillMaxWidth().height(previewHeight),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    TextButton(
                        onClick = { update(SignatureCropGeometry.fullImage) },
                        enabled = imageReady && crop != SignatureCropGeometry.fullImage,
                        modifier = Modifier.testTag("signature_crop_reset"),
                    ) {
                        Text(stringResource(R.string.signature_crop_reset))
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f).testTag("signature_crop_cancel"),
                ) {
                    Text(stringResource(CoreResourcesR.string.signature_cancel))
                }
                Button(
                    onClick = { onApply(crop) },
                    enabled = imageReady && crop.isValid,
                    modifier = Modifier.weight(1f).testTag("signature_crop_apply"),
                ) {
                    Text(stringResource(R.string.signature_crop_apply))
                }
            }
        }
    }
}

@Preview(name = "Recorte de assinatura", widthDp = 390, heightDp = 560, locale = "pt-rBR")
@Preview(name = "Recorte escuro", widthDp = 390, heightDp = 560, locale = "pt-rBR", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Recorte compacto", widthDp = 320, heightDp = 480, fontScale = 1.3f, locale = "pt-rBR")
@Composable
private fun SignatureCropPreview() {
    ScaniqoTheme {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            SignatureCropContent("preview:signature", 3f, {}, {})
        }
    }
}
