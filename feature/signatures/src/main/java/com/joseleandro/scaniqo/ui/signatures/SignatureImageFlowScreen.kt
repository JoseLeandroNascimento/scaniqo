package com.joseleandro.scaniqo.ui.signatures

import com.joseleandro.scaniqo.core.resources.R as CoreResourcesR
import com.joseleandro.scaniqo.feature.signatures.R

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joseleandro.scaniqo.core.localization.localizedRuntimeMessage
import com.joseleandro.scaniqo.domain.model.PdfSignatureCrop
import com.joseleandro.scaniqo.ui.signatures.crop.SignatureCropEditor
import com.joseleandro.scaniqo.ui.components.ScaniqoLoadingIndicator
import com.joseleandro.scaniqo.ui.theme.ScaniqoTheme

@Composable
internal fun SignatureImageFlowScreen(
    editor: SignatureEditorState,
    onEvent: (SignaturesEvent) -> Unit,
    modifier: Modifier = Modifier,
    onPickImage: () -> Unit = {},
    onPreviewReady: (Boolean) -> Unit = {},
) {
    if (editor.imageStep == SignatureImageStep.CAMERA) {
        SignatureCameraScreen(
            onCaptured = { uri, crop -> onEvent(SignaturesEvent.PhotoCaptured(uri, crop)) },
            onCancel = { onEvent(SignaturesEvent.EditorBack) },
            modifier = modifier,
        )
        return
    }
    val contentModifier = modifier.wrapContentWidth(Alignment.CenterHorizontally)
        .widthIn(max = 840.dp).fillMaxWidth()
    val preview = editor.imageStep == SignatureImageStep.PREVIEW
    val busy = editor.isProcessing || editor.isSaving
    var imageReady by remember(editor.imageUri, editor.processedImageUri, preview) { mutableStateOf(false) }
    var resetKey by remember(editor.imageUri) { mutableIntStateOf(0) }
    var background by remember { mutableStateOf(SignaturePreviewBackground.CHECKER) }
    if (editor.imageStep == SignatureImageStep.SELECT) {
        val scan = editor.input == SignatureInput.SCAN
        Column(
            contentModifier.padding(24.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Icon(
                    painterResource(if (scan) CoreResourcesR.drawable.ic_document_scan else CoreResourcesR.drawable.ic_tag_photo),
                    null, Modifier.padding(24.dp).size(48.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Text(stringResource(if (scan) R.string.signature_import_scan_body else R.string.signature_import_image_body),
                style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Button(
                onClick = if (scan) { { onEvent(SignaturesEvent.OpenCamera) } } else onPickImage,
                enabled = !busy,
                modifier = Modifier.heightIn(min = 48.dp).testTag("signature_pick_image"),
            ) {
                Text(stringResource(if (scan) R.string.signature_import_scan else R.string.signature_import_image))
            }
            Text(stringResource(R.string.signature_import_automatic),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center)
            editor.error?.let { Text(localizedRuntimeMessage(it), color = MaterialTheme.colorScheme.error) }
        }
        return
    }
    BoxWithConstraints(contentModifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
        val controlsMaxHeight = (maxHeight * .4f).coerceAtMost(220.dp)
        Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(if (preview) R.string.signature_import_preview_hint else R.string.signature_import_crop_hint),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (preview) {
                    editor.processedImageUri?.let { uri ->
                        SignatureTransparencyPreview(
                            uri, background, Modifier.fillMaxSize(),
                            onImageReady = { imageReady = it; onPreviewReady(it) },
                        )
                    }
                } else {
                    editor.imageUri?.let { uri ->
                        SignatureCropEditor(
                            uri = uri, aspectRatio = editor.imageAspectRatio, crop = editor.crop,
                            onCropChange = { if (!busy) onEvent(SignaturesEvent.CropChanged(it)) },
                            onImageReady = { imageReady = it },
                            modifier = Modifier.fillMaxSize(),
                            enableImageGestures = true, resetKey = resetKey, enabled = !busy,
                        )
                    }
                }
                androidx.compose.animation.AnimatedVisibility(
                    visible = editor.isProcessing,
                    enter = fadeIn(tween(180)) + scaleIn(initialScale = 0.96f, animationSpec = tween(180)),
                    exit = fadeOut(tween(120)),
                ) {
                    Surface(
                        modifier = Modifier.padding(24.dp).semantics { liveRegion = LiveRegionMode.Polite },
                        shape = MaterialTheme.shapes.extraLarge,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        tonalElevation = 6.dp,
                        shadowElevation = 2.dp,
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 28.dp, vertical = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            ScaniqoLoadingIndicator()
                            Text(
                                stringResource(R.string.signature_import_processing),
                                style = MaterialTheme.typography.titleSmall,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
            Column(
                Modifier.fillMaxWidth().animateContentSize().heightIn(max = controlsMaxHeight).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (!editor.showSaveDialog) editor.error?.let {
                    Text(localizedRuntimeMessage(it), color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium)
                }
                if (preview) {
                    if (editor.processedImageUri != null) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SignaturePreviewBackground.entries.forEach { option ->
                            FilterChip(
                                selected = background == option,
                                onClick = { background = option },
                                label = { Text(stringResource(option.label)) },
                            )
                        }
                    }
                    TextButton(
                        onClick = { onEvent(if (editor.fromCamera) SignaturesEvent.OpenCamera else SignaturesEvent.AdjustCrop) },
                        enabled = !busy,
                        modifier = Modifier.align(Alignment.CenterHorizontally).testTag("signature_image_revise"),
                    ) {
                        Icon(painterResource(if (editor.fromCamera) CoreResourcesR.drawable.ic_symbol_photo_camera else CoreResourcesR.drawable.ic_signature_crop), null,
                            Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(if (editor.fromCamera) R.string.signature_import_retake else R.string.signature_import_adjust_crop))
                    }
                } else {
                    FlowRow(Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            enabled = !busy && editor.imageUri != null,
                            onClick = {
                                resetKey++
                                onEvent(SignaturesEvent.CropChanged(PdfSignatureCrop(0f, 0f, 1f, 1f)))
                            },
                        ) { Text(stringResource(R.string.signature_crop_reset)) }
                        Button(
                            enabled = !busy && imageReady && editor.imageUri != null,
                            onClick = { onEvent(SignaturesEvent.ProcessImage) },
                            modifier = Modifier.testTag("signature_import_confirm"),
                        ) { Text(stringResource(R.string.signature_import_confirm_crop)) }
                    }
                }
            }
        }
    }
}

internal enum class SignaturePreviewBackground(val label: Int) {
    CHECKER(R.string.signature_import_transparent),
    WHITE(R.string.signature_import_white),
    DARK(R.string.signature_import_dark),
}

@Composable
internal fun SignatureTransparencyPreview(
    uri: String,
    background: SignaturePreviewBackground,
    modifier: Modifier = Modifier,
    onImageReady: (Boolean) -> Unit = {},
) {
    val previewBackground by animateColorAsState(
        targetValue = if (background == SignaturePreviewBackground.DARK) Color(0xFF20252B) else Color.White,
        animationSpec = tween(220),
        label = "signaturePreviewBackground",
    )
    Surface(modifier, shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Box(Modifier.fillMaxSize().background(previewBackground)) {
            if (background == SignaturePreviewBackground.CHECKER) Canvas(Modifier.fillMaxSize()) {
                val square = 12.dp.toPx()
                for (y in 0..(size.height / square).toInt()) {
                    for (x in 0..(size.width / square).toInt()) {
                        if ((x + y) % 2 == 0) drawRect(
                            Color(0xFFE2E5E8), Offset(x * square, y * square), Size(square, square),
                        )
                    }
                }
            }
            SignatureImage(uri, Modifier.fillMaxSize().padding(24.dp),
                onImageBitmapChanged = { onImageReady(it != null) })
        }
    }
}

@Preview(name = "Importar ou escanear", locale = "pt-rBR")
@Composable
private fun SignatureImportPreview() {
    ScaniqoTheme {
        SignatureEditorScreen(SignatureEditorState(name = "Pessoal", input = SignatureInput.IMAGE), {}, {})
    }
}

@Preview(name = "Conferir transparência", locale = "pt-rBR")
@Preview(name = "Conferir transparência paisagem", widthDp = 760, heightDp = 240, locale = "pt-rBR")
@Preview(name = "Conferir transparência tablet", widthDp = 1280, heightDp = 800, locale = "pt-rBR")
@Composable
private fun SignatureProcessedPreview() {
    ScaniqoTheme {
        SignatureImageFlowScreen(SignatureEditorState(
            name = "Pessoal", imageStep = SignatureImageStep.PREVIEW,
            processedImageUri = "preview:signature",
        ), {})
    }
}
