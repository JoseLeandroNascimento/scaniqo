package com.joseleandro.scaniqo.ui.signatures

import android.graphics.Typeface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.joseleandro.scaniqo.core.localization.localizedRuntimeMessage
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.joseleandro.scaniqo.R
import com.joseleandro.scaniqo.ui.components.DialogActions
import com.joseleandro.scaniqo.domain.model.SignatureFont
import com.joseleandro.scaniqo.domain.model.SignaturePoint
import com.joseleandro.scaniqo.domain.model.SignatureStroke
import com.joseleandro.scaniqo.ui.components.toast.AppToastHost
import com.joseleandro.scaniqo.ui.components.toast.rememberAppToastState
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignaturesScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SignaturesViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val toast = rememberAppToastState()
    val messageText = state.message?.let { localizedRuntimeMessage(it) }
    LaunchedEffect(messageText) {
        messageText?.let {
            toast.show(it)
            viewModel.onEvent(SignaturesEvent.DismissMessage)
        }
    }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        viewModel.onEvent(SignaturesEvent.ImageSelected(uri?.toString()))
    }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.runtime_signatures_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(painterResource(R.drawable.arrow_back_24px), stringResource(R.string.runtime_back)) }
                },
            )
        },
        snackbarHost = { AppToastHost(toast) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { viewModel.onEvent(SignaturesEvent.Create) },
                icon = { Icon(painterResource(R.drawable.edit_24px), null) },
                text = { Text(stringResource(R.string.runtime_new_signature)) },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    stringResource(R.string.runtime_signatures_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            when {
                state.isLoading -> item { CircularProgressIndicator(Modifier.padding(24.dp)) }
                state.loadError -> item {
                    Text(stringResource(R.string.runtime_signatures_load_error))
                    TextButton(onClick = { viewModel.onEvent(SignaturesEvent.Retry) }) { Text(stringResource(R.string.runtime_retry)) }
                }
                state.signatures.isEmpty() -> item {
                    Text(stringResource(R.string.runtime_signatures_empty), Modifier.padding(vertical = 32.dp))
                }
                else -> items(state.signatures, key = { it.id }) { signature ->
                    Card {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(signature.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                                IconButton(onClick = { viewModel.onEvent(SignaturesEvent.RequestRename(signature)) }) {
                                    Icon(painterResource(R.drawable.edit_24px), stringResource(R.string.runtime_signature_rename))
                                }
                                IconButton(onClick = { viewModel.onEvent(SignaturesEvent.RequestDelete(signature)) }) {
                                    Icon(painterResource(R.drawable.delete_24px), stringResource(R.string.runtime_signature_delete))
                                }
                            }
                            SignatureImage(
                                signature.imageUri,
                                Modifier.fillMaxWidth().height(100.dp).clip(MaterialTheme.shapes.small).background(Color.White).padding(12.dp),
                            )
                        }
                    }
                }
            }
        }
    }
    state.editor?.let { editor ->
        SignatureEditorSheet(editor, viewModel::onEvent, onPickImage = { imagePicker.launch(arrayOf("image/*")) })
    }
    state.deleteCandidate?.let { signature ->
        AlertDialog(
            onDismissRequest = { viewModel.onEvent(SignaturesEvent.DismissDialog) },
            title = { Text(stringResource(R.string.runtime_signature_delete_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.runtime_signature_delete_body, androidx.core.text.BidiFormatter.getInstance().unicodeWrap(signature.name)))
                    state.error?.let { Text(localizedRuntimeMessage(it), color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                DialogActions(
                    confirmText = stringResource(R.string.runtime_delete),
                    onConfirm = { viewModel.onEvent(SignaturesEvent.ConfirmDelete) },
                    dismissText = stringResource(R.string.runtime_cancel),
                    onDismiss = { viewModel.onEvent(SignaturesEvent.DismissDialog) },
                    confirmEnabled = !state.isBusy,
                    dismissEnabled = !state.isBusy,
                    confirming = state.isBusy,
                    destructive = true,
                )
            },
        )
    }
    state.renameCandidate?.let {
        AlertDialog(
            onDismissRequest = { viewModel.onEvent(SignaturesEvent.DismissDialog) },
            title = { Text(stringResource(R.string.runtime_signature_rename)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        state.renameText, { viewModel.onEvent(SignaturesEvent.RenameChanged(it)) },
                        label = { Text(stringResource(R.string.runtime_name)) }, singleLine = true, enabled = !state.isBusy,
                    )
                    state.error?.let { Text(localizedRuntimeMessage(it), color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                DialogActions(
                    confirmText = stringResource(R.string.runtime_save),
                    onConfirm = { viewModel.onEvent(SignaturesEvent.ConfirmRename) },
                    dismissText = stringResource(R.string.runtime_cancel),
                    onDismiss = { viewModel.onEvent(SignaturesEvent.DismissDialog) },
                    confirmEnabled = !state.isBusy,
                    dismissEnabled = !state.isBusy,
                    confirming = state.isBusy,
                )
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun SignatureEditorSheet(
    editor: SignatureEditorState,
    onEvent: (SignaturesEvent) -> Unit,
    onPickImage: () -> Unit,
) {
    val saving by rememberUpdatedState(editor.isSaving)
    val sheet = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { !saving || it != SheetValue.Hidden },
    )
    ModalBottomSheet(
        onDismissRequest = { if (!editor.isSaving) onEvent(SignaturesEvent.DismissEditor) },
        sheetState = sheet,
    ) {
        Column(
            Modifier.fillMaxWidth().imePadding().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.runtime_signature_create), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                TextButton(enabled = !editor.isSaving, onClick = { onEvent(SignaturesEvent.DismissEditor) }) { Text(stringResource(R.string.runtime_cancel)) }
            }
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = editor.name,
                    onValueChange = { onEvent(SignaturesEvent.NameChanged(it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.runtime_library_name)) },
                    singleLine = true,
                    enabled = !editor.isSaving,
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(SignatureInput.entries) { input ->
                        FilterChip(
                            selected = input == editor.input,
                            onClick = { onEvent(SignaturesEvent.InputChanged(input)) },
                            enabled = !editor.isSaving,
                            label = { Text(when (input) {
                                SignatureInput.DRAW -> stringResource(R.string.runtime_draw)
                                SignatureInput.TYPE -> stringResource(R.string.runtime_type)
                                SignatureInput.IMAGE -> stringResource(R.string.runtime_image)
                            }) },
                        )
                    }
                }
                when (editor.input) {
                    SignatureInput.DRAW -> {
                        SignatureDrawingPad(editor, onEvent)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(enabled = !editor.isSaving && editor.strokes.isNotEmpty(), onClick = { onEvent(SignaturesEvent.Undo) }) { Text(stringResource(R.string.runtime_undo)) }
                            TextButton(enabled = !editor.isSaving && editor.strokes.isNotEmpty(), onClick = { onEvent(SignaturesEvent.Clear) }) { Text(stringResource(R.string.runtime_clear)) }
                        }
                        Text(stringResource(R.string.runtime_thickness), style = MaterialTheme.typography.labelMedium)
                        Slider(
                            value = editor.strokeWidth,
                            onValueChange = { onEvent(SignaturesEvent.WidthChanged(it)) },
                            valueRange = 0.002f..0.014f,
                            enabled = !editor.isSaving,
                        )
                    }
                    SignatureInput.TYPE -> {
                        OutlinedTextField(
                            editor.text, { onEvent(SignaturesEvent.TextChanged(it)) },
                            Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.runtime_signature_text)) },
                            singleLine = true, enabled = !editor.isSaving,
                        )
                        Surface(color = Color.White, shape = MaterialTheme.shapes.medium) {
                            Text(
                                text = editor.text.ifBlank { stringResource(R.string.runtime_signature_placeholder) },
                                modifier = Modifier.fillMaxWidth().padding(24.dp),
                                fontFamily = FontFamily(Typeface.create(
                                    editor.font.family,
                                    if (editor.font.italic) Typeface.ITALIC else Typeface.NORMAL,
                                )),
                                fontSize = 32.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                color = Color(editor.colorArgb),
                            )
                        }
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(SignatureFont.entries) { font ->
                                FilterChip(
                                    selected = editor.font == font,
                                    onClick = { onEvent(SignaturesEvent.FontChanged(font)) },
                                    enabled = !editor.isSaving,
                                    label = { Text(when (font) {
                                        SignatureFont.CURSIVE -> stringResource(R.string.runtime_font_cursive)
                                        SignatureFont.HANDWRITTEN -> stringResource(R.string.runtime_font_handwritten)
                                        SignatureFont.CLASSIC -> stringResource(R.string.runtime_font_classic)
                                        SignatureFont.MONOSPACED -> stringResource(R.string.runtime_font_monospace)
                                    }) },
                                )
                            }
                        }
                    }
                    SignatureInput.IMAGE -> {
                        editor.imageUri?.let {
                            SignatureImage(it, Modifier.fillMaxWidth().height(140.dp).background(Color.White).padding(12.dp))
                        }
                        OutlinedButton(enabled = !editor.isSaving, onClick = onPickImage, modifier = Modifier.fillMaxWidth()) {
                            Text(if (editor.imageUri == null) stringResource(R.string.runtime_image_choose) else stringResource(R.string.runtime_image_change))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(editor.removeLightBackground, { onEvent(SignaturesEvent.RemoveBackgroundChanged(it)) }, enabled = !editor.isSaving)
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
                        listOf(stringResource(R.string.runtime_black) to 0xFF18212B.toInt(), stringResource(R.string.runtime_blue) to 0xFF183C9A.toInt(), stringResource(R.string.runtime_violet) to 0xFF613D82.toInt()).forEach { (label, argb) ->
                            FilterChip(
                                selected = editor.colorArgb == argb,
                                onClick = { onEvent(SignaturesEvent.ColorChanged(argb)) },
                                enabled = !editor.isSaving,
                                label = { Text(label) },
                                leadingIcon = { Box(Modifier.size(14.dp).background(Color(argb), MaterialTheme.shapes.small)) },
                            )
                        }
                    }
                }
                editor.error?.let { Text(localizedRuntimeMessage(it), color = MaterialTheme.colorScheme.error) }
            }
            Button(
                enabled = !editor.isSaving,
                onClick = { onEvent(SignaturesEvent.Save) },
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            ) {
                if (editor.isSaving) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (editor.isSaving) stringResource(R.string.runtime_saving) else stringResource(R.string.runtime_signature_save))
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "Criar assinatura", showBackground = true)
@Composable
private fun SignatureEditorPreview() {
    com.joseleandro.scaniqo.ui.theme.ScaniqoTheme {
        SignatureEditorSheet(
            editor = SignatureEditorState(input = SignatureInput.TYPE, text = "Maria Silva"),
            onEvent = {},
            onPickImage = {},
        )
    }
}

@Composable
private fun SignatureDrawingPad(editor: SignatureEditorState, onEvent: (SignaturesEvent) -> Unit) {
    var currentPoints by remember { mutableStateOf<List<SignaturePoint>>(emptyList()) }
    val currentEditor by rememberUpdatedState(editor)
    val currentOnEvent by rememberUpdatedState(onEvent)
    Box(
        Modifier.fillMaxWidth().aspectRatio(8f / 3f).clip(MaterialTheme.shapes.medium).background(Color.White),
    ) {
        if (editor.strokes.isEmpty() && currentPoints.isEmpty()) {
            Text(stringResource(R.string.runtime_draw_here), Modifier.align(Alignment.Center), color = Color(0xFF72808A))
        }
        Canvas(
            Modifier.fillMaxSize().pointerInput(editor.isSaving) {
                if (!editor.isSaving) detectDragGestures(
                    onDragStart = { point ->
                        currentPoints = listOf(SignaturePoint(
                            (point.x / size.width).coerceIn(0f, 1f),
                            (point.y / size.height).coerceIn(0f, 1f),
                        ))
                    },
                    onDragEnd = {
                        if (currentPoints.size > 1) currentOnEvent(SignaturesEvent.StrokeAdded(
                            SignatureStroke(currentPoints, currentEditor.colorArgb, currentEditor.strokeWidth),
                        ))
                        currentPoints = emptyList()
                    },
                    onDragCancel = { currentPoints = emptyList() },
                    onDrag = { change, _ ->
                        change.consume()
                        if (currentPoints.size < 1000) currentPoints = currentPoints + SignaturePoint(
                            (change.position.x / size.width).coerceIn(0f, 1f),
                            (change.position.y / size.height).coerceIn(0f, 1f),
                        )
                    },
                )
            },
        ) {
            // O canvas e o PNG usam a mesma proporção e largura de traço normalizada.
            val strokes = editor.strokes + SignatureStroke(currentPoints, editor.colorArgb, editor.strokeWidth)
            strokes.forEach { stroke ->
                if (stroke.points.isNotEmpty()) {
                    val path = Path()
                    stroke.points.forEachIndexed { i, point ->
                        val x = point.x * size.width
                        val y = point.y * size.height
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(path, Color(stroke.colorArgb), style = Stroke(
                        width = stroke.width * size.width,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    ))
                }
            }
            drawLine(Color(0xFFE5E9ED), Offset(16.dp.toPx(), size.height * .8f), Offset(size.width - 16.dp.toPx(), size.height * .8f))
        }
    }
}
