package com.joseleandro.scaniqo.ui.signatures

import com.joseleandro.scaniqo.core.resources.R as CoreResourcesR
import com.joseleandro.scaniqo.feature.signatures.R

import android.content.res.Configuration
import android.graphics.Typeface
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joseleandro.scaniqo.core.localization.localizedRuntimeMessage
import com.joseleandro.scaniqo.domain.model.SignatureFont
import com.joseleandro.scaniqo.domain.model.SignaturePoint
import com.joseleandro.scaniqo.domain.model.SignatureStroke
import com.joseleandro.scaniqo.ui.components.DialogActions
import com.joseleandro.scaniqo.ui.theme.ScaniqoTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignatureEditorScreen(
    editor: SignatureEditorState,
    onEvent: (SignaturesEvent) -> Unit,
    onPickImage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler { onEvent(SignaturesEvent.EditorBack) }
    var previewReady by remember(editor.processedImageUri, editor.input, editor.imageStep) { mutableStateOf(false) }
    val busy = editor.isSaving || editor.isProcessing
    val saveDescription = stringResource(CoreResourcesR.string.runtime_signature_save)
    Scaffold(
        modifier = modifier,
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            stringResource(if (editor.editingId == null) CoreResourcesR.string.runtime_signature_create else R.string.runtime_signature_edit),
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            enabled = !editor.isSaving,
                            onClick = { onEvent(SignaturesEvent.EditorBack) },
                        ) {
                            Icon(painterResource(CoreResourcesR.drawable.arrow_back_24px), stringResource(CoreResourcesR.string.runtime_back))
                        }
                    },
                    actions = {
                        Button(
                            onClick = { onEvent(SignaturesEvent.RequestSave) },
                            enabled = editor.canSave && !editor.showSaveDialog && (!editor.input.isImage || previewReady),
                            modifier = Modifier.padding(end = 12.dp).heightIn(min = 48.dp)
                                .testTag("signature_save")
                                .semantics { contentDescription = saveDescription },
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        ) {
                            if (editor.isSaving) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(painterResource(CoreResourcesR.drawable.ic_check_24), null, Modifier.size(18.dp))
                            }
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(CoreResourcesR.string.runtime_save), maxLines = 1)
                        }
                    },
                )
                SignatureInputTabs(
                    selectedInput = editor.input,
                    onSelect = { onEvent(SignaturesEvent.InputChanged(it)) },
                    enabled = !busy && !editor.showSaveDialog,
                )
            }
        },
    ) { padding ->
        if (editor.input.isImage) {
            SignatureImageFlowScreen(
                editor, onEvent,
                modifier = Modifier.fillMaxSize().padding(padding),
                onPickImage = onPickImage,
                onPreviewReady = { previewReady = it },
            )
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)
                    .wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = 840.dp).fillMaxWidth()
                    .imePadding().verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                when (editor.input) {
                    SignatureInput.DRAW -> {
                        Text(stringResource(R.string.runtime_signature_draw_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        SignatureDrawingPad(editor, onEvent)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(enabled = !busy && editor.strokes.isNotEmpty(),
                                onClick = { onEvent(SignaturesEvent.Undo) }) {
                                Text(stringResource(CoreResourcesR.string.runtime_undo))
                            }
                            TextButton(enabled = !busy && editor.strokes.isNotEmpty(),
                                onClick = { onEvent(SignaturesEvent.Clear) }) {
                                Text(stringResource(CoreResourcesR.string.runtime_clear))
                            }
                        }
                        SignatureTipSizeSelector(
                            selectedWidth = editor.strokeWidth,
                            onWidthSelected = { onEvent(SignaturesEvent.WidthChanged(it)) },
                            enabled = !busy,
                        )
                    }
                    SignatureInput.TYPE -> {
                    OutlinedTextField(
                        value = editor.text,
                        onValueChange = { onEvent(SignaturesEvent.TextChanged(it)) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(CoreResourcesR.string.runtime_signature_text)) },
                        singleLine = true,
                        enabled = !editor.isSaving,
                    )
                    Text(stringResource(R.string.runtime_signature_preview), style = MaterialTheme.typography.titleSmall)
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color.White,
                        shape = MaterialTheme.shapes.large,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Box(Modifier.fillMaxWidth().heightIn(min = 160.dp).padding(20.dp), contentAlignment = Alignment.Center) {
                            Text(
                                text = editor.text.ifBlank { stringResource(CoreResourcesR.string.runtime_signature_placeholder) },
                                fontFamily = editor.font.toFontFamily(),
                                fontSize = 34.sp,
                                lineHeight = 42.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                color = Color(editor.colorArgb),
                            )
                        }
                    }
                    Text(stringResource(R.string.runtime_signature_font), style = MaterialTheme.typography.titleSmall)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(listOf(
                            SignatureFont.CURSIVE,
                            SignatureFont.HANDWRITTEN,
                            SignatureFont.PEN,
                            SignatureFont.SCRIPT,
                            SignatureFont.BRUSH,
                            SignatureFont.CLASSIC,
                            SignatureFont.MONOSPACED,
                        )) { font ->
                            val selected = editor.font == font
                            Card(
                                onClick = { onEvent(SignaturesEvent.FontChanged(font)) },
                                enabled = !editor.isSaving,
                                modifier = Modifier.width(152.dp).height(98.dp),
                                border = BorderStroke(
                                    if (selected) 2.dp else 1.dp,
                                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                ),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                ),
                            ) {
                                Column(
                                    Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        text = editor.text.ifBlank { stringResource(CoreResourcesR.string.runtime_signature_placeholder) },
                                        fontFamily = font.toFontFamily(),
                                        fontSize = 25.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Text(
                                        text = font.label(),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }

                    }
                    else -> Unit
                }
                SignaturePenColorSelector(
                    selectedColor = editor.colorArgb,
                    onColorSelected = { onEvent(SignaturesEvent.ColorChanged(it)) },
                    enabled = !busy,
                )
                if (!editor.showSaveDialog) editor.error?.let {
                    Text(localizedRuntimeMessage(it), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
    if (editor.showSaveDialog) SignatureSaveDialog(editor, onEvent)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SignatureInputTabs(
    selectedInput: SignatureInput,
    onSelect: (SignatureInput) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val inputs = SignatureInput.entries
    val labels = inputs.map { stringResource(it.label) }
    val textStyle = MaterialTheme.typography.labelMedium
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    // Share the full width when labels fit, preserving readable tabs at larger font scales.
    val minimumTabWidth = with(density) {
        labels.maxOf { textMeasurer.measure(AnnotatedString(it), textStyle).size.width }.toDp() + 32.dp
    }.coerceAtLeast(72.dp)
    val tabs: @Composable () -> Unit = {
        inputs.forEachIndexed { index, input ->
            Tab(
                selected = selectedInput == input,
                selectedContentColor = MaterialTheme.colorScheme.primary,
                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                onClick = { onSelect(input) },
                enabled = enabled,
                modifier = Modifier.testTag("signature_tab_${input.name.lowercase()}"),
                text = { Text(labels[index], style = textStyle, maxLines = 1) },
                icon = { Icon(painterResource(input.icon), null, Modifier.size(24.dp)) },
            )
        }
    }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        if (maxWidth / inputs.size >= minimumTabWidth) {
            PrimaryTabRow(selectedTabIndex = inputs.indexOf(selectedInput), tabs = tabs)
        } else {
            PrimaryScrollableTabRow(
                selectedTabIndex = inputs.indexOf(selectedInput),
                edgePadding = 0.dp,
                minTabWidth = minimumTabWidth,
                tabs = tabs,
            )
        }
    }
}

private val SignatureInput.label: Int get() = when (this) {
    SignatureInput.DRAW -> CoreResourcesR.string.runtime_draw
    SignatureInput.TYPE -> CoreResourcesR.string.runtime_type
    SignatureInput.IMAGE -> R.string.signature_tab_import
    SignatureInput.SCAN -> R.string.signature_tab_scan
}

private val SignatureInput.icon: Int get() = when (this) {
    SignatureInput.DRAW -> CoreResourcesR.drawable.ic_symbol_draw
    SignatureInput.TYPE -> CoreResourcesR.drawable.ic_fill_text
    SignatureInput.IMAGE -> CoreResourcesR.drawable.ic_tag_photo
    SignatureInput.SCAN -> CoreResourcesR.drawable.ic_document_scan
}

@Composable
private fun SignatureSaveDialog(
    editor: SignatureEditorState,
    onEvent: (SignaturesEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        modifier = modifier.testTag("signature_save_dialog"),
        onDismissRequest = { if (!editor.isSaving) onEvent(SignaturesEvent.CancelSave) },
        title = { Text(stringResource(CoreResourcesR.string.runtime_signature_save)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = editor.name,
                    onValueChange = { onEvent(SignaturesEvent.NameChanged(it)) },
                    label = { Text(stringResource(CoreResourcesR.string.runtime_library_name)) },
                    singleLine = true,
                    enabled = !editor.isSaving,
                    modifier = Modifier.fillMaxWidth().testTag("signature_name"),
                )
                editor.error?.let {
                    Text(localizedRuntimeMessage(it), color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            DialogActions(
                confirmText = stringResource(CoreResourcesR.string.runtime_save),
                onConfirm = { onEvent(SignaturesEvent.Save) },
                dismissText = stringResource(CoreResourcesR.string.runtime_cancel),
                onDismiss = { onEvent(SignaturesEvent.CancelSave) },
                confirmEnabled = editor.canSave && editor.name.isNotBlank(),
                dismissEnabled = !editor.isSaving,
                confirming = editor.isSaving,
            )
        },
    )
}

@Composable
private fun SignatureFont.label(): String = when (this) {
    SignatureFont.CURSIVE -> stringResource(CoreResourcesR.string.runtime_font_cursive)
    SignatureFont.HANDWRITTEN -> stringResource(CoreResourcesR.string.runtime_font_handwritten)
    SignatureFont.PEN -> stringResource(R.string.runtime_font_pen)
    SignatureFont.SCRIPT -> stringResource(R.string.runtime_font_script)
    SignatureFont.BRUSH -> stringResource(R.string.runtime_font_brush)
    SignatureFont.CLASSIC -> stringResource(CoreResourcesR.string.runtime_font_classic)
    SignatureFont.MONOSPACED -> stringResource(CoreResourcesR.string.runtime_font_monospace)
}

private fun SignatureFont.toFontFamily(): FontFamily = FontFamily(
    Typeface.create(
        family,
        when {
            bold && italic -> Typeface.BOLD_ITALIC
            bold -> Typeface.BOLD
            italic -> Typeface.ITALIC
            else -> Typeface.NORMAL
        },
    ),
)

@Composable
private fun SignatureDrawingPad(editor: SignatureEditorState, onEvent: (SignaturesEvent) -> Unit) {
    var currentPoints by remember { mutableStateOf<List<SignaturePoint>>(emptyList()) }
    val currentEditor by rememberUpdatedState(editor)
    val currentOnEvent by rememberUpdatedState(onEvent)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(2f)) {
            if (editor.strokes.isEmpty() && currentPoints.isEmpty()) {
                Text(stringResource(CoreResourcesR.string.runtime_draw_here), Modifier.align(Alignment.Center), color = Color(0xFF72808A))
            }
            Canvas(
                Modifier.fillMaxSize().pointerInput(editor.isSaving) {
                    if (!editor.isSaving) awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        currentPoints = listOf(SignaturePoint(
                            (down.position.x / size.width).coerceIn(0f, 1f),
                            (down.position.y / size.height).coerceIn(0f, 1f),
                        ))
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            if (change.position != change.previousPosition && currentPoints.size < 1000) {
                                currentPoints = currentPoints + SignaturePoint(
                                    (change.position.x / size.width).coerceIn(0f, 1f),
                                    (change.position.y / size.height).coerceIn(0f, 1f),
                                )
                            }
                            change.consume()
                        }
                        if (currentPoints.size > 1) currentOnEvent(SignaturesEvent.StrokeAdded(
                            SignatureStroke(currentPoints, currentEditor.colorArgb, currentEditor.strokeWidth),
                        ))
                        currentPoints = emptyList()
                    }
                },
            ) {
                drawLine(
                    Color(0xFFE5E9ED),
                    Offset(16.dp.toPx(), size.height * .8f),
                    Offset(size.width - 16.dp.toPx(), size.height * .8f),
                )
                val strokes = editor.strokes + SignatureStroke(currentPoints, editor.colorArgb, editor.strokeWidth)
                strokes.forEach { stroke ->
                    if (stroke.points.isNotEmpty()) {
                        val path = Path()
                        stroke.points.forEachIndexed { i, point ->
                            val x = point.x * size.width
                            val y = point.y * size.height
                            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        }
                        drawPath(
                            path,
                            Color(stroke.colorArgb),
                            style = Stroke(
                                width = stroke.width * size.width,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round,
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Preview(name = "Criar assinatura", showBackground = true, locale = "pt-rBR")
@Preview(name = "Criar assinatura tablet", widthDp = 1280, heightDp = 800, locale = "pt-rBR")
@Preview(name = "Criar assinatura dobrável", widthDp = 673, heightDp = 841, locale = "pt-rBR")
@Preview(name = "Criar assinatura estreita", widthDp = 320, heightDp = 640, fontScale = 1.5f, locale = "pt-rBR")
@Preview(name = "Criar assinatura escuro", showBackground = true, locale = "pt-rBR", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SignatureEditorPreview() {
    ScaniqoTheme {
        SignatureEditorScreen(
            editor = SignatureEditorState(input = SignatureInput.TYPE, text = "Maria Silva"),
            onEvent = {},
            onPickImage = {},
        )
    }
}
