package com.joseleandro.scaniqo.ui.signatures

import android.content.res.Configuration
import android.graphics.Typeface
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joseleandro.scaniqo.R
import com.joseleandro.scaniqo.core.localization.localizedRuntimeMessage
import com.joseleandro.scaniqo.domain.model.SignatureFont
import com.joseleandro.scaniqo.domain.model.SignaturePoint
import com.joseleandro.scaniqo.domain.model.SignatureStroke
import com.joseleandro.scaniqo.ui.theme.ScaniqoTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignatureEditorScreen(
    editor: SignatureEditorState,
    onEvent: (SignaturesEvent) -> Unit,
    onPickImage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(if (editor.editingId == null) R.string.runtime_signature_create else R.string.runtime_signature_edit))
                },
                navigationIcon = {
                    IconButton(
                        enabled = !editor.isSaving,
                        onClick = { onEvent(SignaturesEvent.DismissEditor) },
                    ) {
                        Icon(painterResource(R.drawable.arrow_back_24px), stringResource(R.string.runtime_back))
                    }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp, shadowElevation = 8.dp) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .imePadding()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    editor.error?.let {
                        Text(
                            text = localizedRuntimeMessage(it),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Button(
                        enabled = !editor.isSaving,
                        onClick = { onEvent(SignaturesEvent.Save) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    ) {
                        if (editor.isSaving) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(if (editor.isSaving) stringResource(R.string.runtime_saving) else stringResource(R.string.runtime_signature_save))
                    }
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.runtime_signatures_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = editor.name,
                onValueChange = { onEvent(SignaturesEvent.NameChanged(it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.runtime_library_name)) },
                singleLine = true,
                enabled = !editor.isSaving,
            )
            LazyRow(
                contentPadding = PaddingValues(end = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(SignatureInput.entries) { input ->
                    FilterChip(
                        selected = input == editor.input,
                        onClick = { onEvent(SignaturesEvent.InputChanged(input)) },
                        enabled = !editor.isSaving,
                        label = {
                            Text(when (input) {
                                SignatureInput.DRAW -> stringResource(R.string.runtime_draw)
                                SignatureInput.TYPE -> stringResource(R.string.runtime_type)
                                SignatureInput.IMAGE -> stringResource(R.string.runtime_image)
                            })
                        },
                    )
                }
            }
            when (editor.input) {
                SignatureInput.DRAW -> {
                    Text(
                        stringResource(R.string.runtime_signature_draw_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SignatureDrawingPad(editor, onEvent)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(
                            enabled = !editor.isSaving && editor.strokes.isNotEmpty(),
                            onClick = { onEvent(SignaturesEvent.Undo) },
                        ) { Text(stringResource(R.string.runtime_undo)) }
                        TextButton(
                            enabled = !editor.isSaving && editor.strokes.isNotEmpty(),
                            onClick = { onEvent(SignaturesEvent.Clear) },
                        ) { Text(stringResource(R.string.runtime_clear)) }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.runtime_thickness), style = MaterialTheme.typography.labelLarge)
                        Slider(
                            value = editor.strokeWidth,
                            onValueChange = { onEvent(SignaturesEvent.WidthChanged(it)) },
                            valueRange = 0.002f..0.014f,
                            enabled = !editor.isSaving,
                        )
                    }
                }
                SignatureInput.TYPE -> {
                    OutlinedTextField(
                        value = editor.text,
                        onValueChange = { onEvent(SignaturesEvent.TextChanged(it)) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.runtime_signature_text)) },
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
                                text = editor.text.ifBlank { stringResource(R.string.runtime_signature_placeholder) },
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
                                        text = editor.text.ifBlank { stringResource(R.string.runtime_signature_placeholder) },
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
                SignatureInput.IMAGE -> {
                    if (editor.editingId != null && editor.originalImageUri != null) {
                        Text(
                            stringResource(R.string.runtime_signature_legacy_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color.White,
                        shape = MaterialTheme.shapes.large,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Box(Modifier.fillMaxWidth().height(180.dp).padding(16.dp), contentAlignment = Alignment.Center) {
                            editor.imageUri?.let { SignatureImage(it, Modifier.fillMaxSize()) }
                                ?: Text(stringResource(R.string.runtime_signature_preview), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    OutlinedButton(
                        enabled = !editor.isSaving,
                        onClick = onPickImage,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) {
                        Text(if (editor.imageUri == null) stringResource(R.string.runtime_image_choose) else stringResource(R.string.runtime_image_change))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = editor.removeLightBackground,
                            onCheckedChange = { onEvent(SignaturesEvent.RemoveBackgroundChanged(it)) },
                            enabled = !editor.isSaving,
                        )
                        Text(stringResource(R.string.runtime_remove_white), style = MaterialTheme.typography.bodyMedium)
                    }
                    Text(
                        stringResource(R.string.runtime_signature_image_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (editor.input != SignatureInput.IMAGE) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        stringResource(R.string.runtime_black) to 0xFF18212B.toInt(),
                        stringResource(R.string.runtime_blue) to 0xFF183C9A.toInt(),
                        stringResource(R.string.runtime_violet) to 0xFF613D82.toInt(),
                    ).forEach { (label, argb) ->
                        FilterChip(
                            selected = editor.colorArgb == argb,
                            onClick = { onEvent(SignaturesEvent.ColorChanged(argb)) },
                            enabled = !editor.isSaving,
                            label = { Text(label) },
                            leadingIcon = { Box(Modifier.size(14.dp).clip(MaterialTheme.shapes.small).background(Color(argb))) },
                        )
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun SignatureFont.label(): String = when (this) {
    SignatureFont.CURSIVE -> stringResource(R.string.runtime_font_cursive)
    SignatureFont.HANDWRITTEN -> stringResource(R.string.runtime_font_handwritten)
    SignatureFont.PEN -> stringResource(R.string.runtime_font_pen)
    SignatureFont.SCRIPT -> stringResource(R.string.runtime_font_script)
    SignatureFont.BRUSH -> stringResource(R.string.runtime_font_brush)
    SignatureFont.CLASSIC -> stringResource(R.string.runtime_font_classic)
    SignatureFont.MONOSPACED -> stringResource(R.string.runtime_font_monospace)
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
                Text(stringResource(R.string.runtime_draw_here), Modifier.align(Alignment.Center), color = Color(0xFF72808A))
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
