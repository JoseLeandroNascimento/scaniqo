package com.joseleandro.scaniqo.ui.pdfTools

import android.content.res.Configuration
import android.text.format.Formatter
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.joseleandro.scaniqo.core.localization.localizedRuntimeMessage
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.joseleandro.scaniqo.R
import com.joseleandro.scaniqo.domain.model.DOCUMENT_NAME_MAX_LENGTH
import com.joseleandro.scaniqo.domain.model.DocumentModel
import com.joseleandro.scaniqo.domain.model.PdfCompressionLevel
import com.joseleandro.scaniqo.domain.model.PdfImageFormat
import com.joseleandro.scaniqo.domain.model.PdfPageSelection
import com.joseleandro.scaniqo.domain.model.PdfUtility
import com.joseleandro.scaniqo.ui.documentCommon.components.DocumentPageThumbnail
import com.joseleandro.scaniqo.ui.documentCommon.components.DocumentPdfPage
import com.joseleandro.scaniqo.ui.documentCommon.components.DocumentPreviewZoom
import com.joseleandro.scaniqo.ui.documentCommon.components.DocumentThumbnail
import com.joseleandro.scaniqo.ui.theme.ScaniqoTheme

/** Os seletores e as operações continuam na Route; aqui ficam somente os controles do rascunho. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PdfUtilityWorkspace(
    tool: PdfUtility,
    state: PdfUtilityUiState,
    document: DocumentModel?,
    name: String,
    pages: String,
    password: String,
    format: PdfImageFormat,
    compression: PdfCompressionLevel,
    destinationReady: Boolean,
    externalPicker: Boolean,
    onBack: () -> Unit,
    onChooseDocument: () -> Unit,
    onImport: () -> Unit,
    onName: (String) -> Unit,
    onPages: (String) -> Unit,
    onPassword: (String) -> Unit,
    onFormat: (PdfImageFormat) -> Unit,
    onCompression: (PdfCompressionLevel) -> Unit,
    onDestination: () -> Unit,
    onAction: () -> Unit,
    onRetry: () -> Unit,
    onOpenDocument: (Long) -> Unit,
) {
    val compress = tool == PdfUtility.COMPRESS_PDF
    val extract = tool == PdfUtility.EXTRACT_PAGES
    val enabled = !state.busy && !externalPicker
    var emptySelection by rememberSaveable(document?.id) { mutableStateOf(false) }
    var showRange by rememberSaveable(document?.id) { mutableStateOf(false) }
    val parsed = remember(document?.id, document?.pageCount, pages, emptySelection) {
        if (emptySelection) Result.success(emptyList())
        else runCatching { PdfPageSelection.parse(pages, document?.pageCount ?: 1) }
    }
    val selection = parsed.getOrNull().orEmpty()
    val pageError = when {
        document == null || compress -> null
        emptySelection -> stringResource(R.string.utility_workspace_empty_selection)
        parsed.isFailure -> parsed.exceptionOrNull()?.message
        else -> null
    }
    val ready = enabled && document != null &&
        (!document.isProtected || password.isNotEmpty()) &&
        (compress || selection.isNotEmpty()) &&
        (!(compress || extract) || name.isNotBlank())
    val title = when (tool) {
        PdfUtility.EXTRACT_PAGES -> R.string.utility_extract_title
        PdfUtility.COMPRESS_PDF -> R.string.utility_workspace_compress_title
        else -> R.string.utility_images_export_title
    }
    val completedId = state.compressionResult?.documentId.takeIf { compress }
    val actionText = when {
        completedId != null -> R.string.utility_workspace_open_result
        state.busy -> R.string.utility_workspace_busy
        compress -> R.string.utility_workspace_compress_action
        extract -> R.string.utility_extract_action
        !destinationReady -> R.string.utility_choose_destination
        else -> R.string.utility_export_action
    }
    val actionEnabled = if (completedId != null) enabled else if (!compress && !extract && !destinationReady) {
        enabled && document != null && selection.isNotEmpty() && pageError == null
    } else ready && pageError == null
    val contentScroll = rememberScrollState()
    LaunchedEffect(state.compressionResult) {
        if (compress && state.compressionResult != null) contentScroll.animateScrollTo(0)
    }
    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(title)) },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !state.busy) {
                        Icon(painterResource(R.drawable.arrow_back_24px), stringResource(R.string.utility_back))
                    }
                },
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp) {
                Column(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (document != null && !compress) {
                        Text(stringResource(R.string.utility_workspace_selection, selection.size, document.pageCount),
                            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Button(onClick = { if (completedId != null) onOpenDocument(completedId) else onAction() }, enabled = actionEnabled,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                        if (state.busy) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        } else {
                            Icon(painterResource(when {
                                compress -> R.drawable.ic_tool_compress
                                extract -> R.drawable.ic_tool_extract
                                else -> R.drawable.ic_tool_export_images
                            }), null, Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(stringResource(actionText))
                    }
                }
            }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)
                .verticalScroll(contentScroll).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            if (state.busy || state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (state.loadError) {
                WorkspaceNotice(stringResource(R.string.utility_library_error), error = true)
                TextButton(onClick = onRetry, enabled = enabled) { Text(stringResource(R.string.documents_retry)) }
            }
            state.error?.let { WorkspaceNotice(localizedRuntimeMessage(it), error = true) }
            state.message?.let { WorkspaceNotice(localizedRuntimeMessage(it)) }
            if (compress) state.compressionResult?.let { result ->
                Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primaryContainer) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(if (result.documentId == null) R.string.utility_workspace_compress_no_gain
                            else R.string.utility_workspace_compress_complete), style = MaterialTheme.typography.titleMedium)
                        val context = LocalContext.current
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text(stringResource(R.string.utility_workspace_original_size), style = MaterialTheme.typography.labelMedium)
                                Text(Formatter.formatShortFileSize(context, result.originalBytes), style = MaterialTheme.typography.titleLarge)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(stringResource(R.string.utility_workspace_result_size), style = MaterialTheme.typography.labelMedium)
                                Text(Formatter.formatShortFileSize(context, result.resultBytes), style = MaterialTheme.typography.titleLarge)
                            }
                        }
                        val id = result.documentId
                        if (id != null) {
                            val percent = if (result.originalBytes > 0) ((1.0 - result.resultBytes.toDouble() / result.originalBytes) * 100).toInt() else 0
                            Text(stringResource(R.string.utility_workspace_saved, percent.coerceAtLeast(0)),
                                style = MaterialTheme.typography.labelLarge)
                            Button(onClick = { onOpenDocument(id) }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.utility_workspace_open_result))
                            }
                        } else Text(stringResource(R.string.utility_workspace_compress_no_gain_hint), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            Text(stringResource(if (compress) R.string.utility_workspace_compress_hint else if (extract)
                R.string.utility_extract_description else R.string.utility_workspace_zip_hint),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                WorkspaceSource(document, enabled, onChooseDocument)
                OutlinedButton(onClick = onImport, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                    Icon(painterResource(R.drawable.ic_document_import), null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.utility_workspace_import_device))
                }
            }
            if (document != null) {
                if (document.isProtected) {
                    OutlinedTextField(
                        value = password, onValueChange = onPassword, modifier = Modifier.fillMaxWidth(),
                        singleLine = true, enabled = enabled, label = { Text(stringResource(R.string.utility_password)) },
                        leadingIcon = { Icon(painterResource(R.drawable.ic_document_lock), null) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    )
                    if (extract) Text(stringResource(R.string.utility_password_note),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (!compress) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.utility_workspace_pages), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.utility_workspace_order), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            FilterChip(selected = selection.size == document.pageCount && pageError == null,
                                onClick = { emptySelection = false; onPages("") }, enabled = enabled,
                                label = { Text(stringResource(R.string.utility_workspace_select_all)) })
                            TextButton(onClick = { emptySelection = true; onPages("") }, enabled = enabled && selection.isNotEmpty()) {
                                Text(stringResource(R.string.utility_workspace_clear))
                            }
                            TextButton(onClick = { showRange = !showRange }, enabled = enabled) {
                                Text(stringResource(R.string.utility_workspace_range))
                            }
                        }
                        if (showRange) {
                            OutlinedTextField(
                                value = pages, onValueChange = { emptySelection = false; onPages(it.take(1000)) },
                                textStyle = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Ltr),
                                modifier = Modifier.fillMaxWidth(), enabled = enabled,
                                label = { Text(stringResource(R.string.utility_pages)) }, singleLine = true,
                                isError = pageError != null,
                                supportingText = { Text(pageError ?: stringResource(R.string.utility_pages_hint)) },
                            )
                        } else if (pageError != null) {
                            Text(pageError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                        if (document.isProtected) Text(stringResource(R.string.utility_workspace_locked_pages),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(88.dp),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp, max = 360.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items((0 until document.pageCount).toList(), key = { "${document.id}:$it" }) { index ->
                                WorkspacePageTile(document, index, index in selection, enabled) {
                                    val updated = if (index in selection) selection - index else selection + index
                                    emptySelection = updated.isEmpty()
                                    onPages(updated.joinToString(",") { (it + 1).toString() })
                                }
                            }
                        }
                    }
                } else {
                    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.utility_workspace_compress_level), style = MaterialTheme.typography.titleMedium)
                        PdfCompressionLevel.entries.forEach { level ->
                            WorkspaceChoice(
                                title = stringResource(if (level == PdfCompressionLevel.BALANCED)
                                    R.string.utility_workspace_balanced else R.string.utility_workspace_strong),
                                description = stringResource(if (level == PdfCompressionLevel.BALANCED)
                                    R.string.utility_workspace_balanced_hint else R.string.utility_workspace_strong_hint),
                                selected = compression == level, enabled = enabled, onSelect = { onCompression(level) },
                            )
                        }
                        Text(stringResource(R.string.utility_workspace_compress_note),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.utility_workspace_output), style = MaterialTheme.typography.titleMedium)
                    if (extract || compress) {
                        OutlinedTextField(value = name, onValueChange = { onName(it.take(DOCUMENT_NAME_MAX_LENGTH)) },
                            textStyle = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.ContentOrLtr),
                            modifier = Modifier.fillMaxWidth(), enabled = enabled,
                            label = { Text(stringResource(R.string.utility_name)) }, singleLine = true)
                        Text(stringResource(R.string.utility_workspace_copy_hint), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Text(stringResource(R.string.utility_workspace_format), style = MaterialTheme.typography.labelLarge)
                        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            PdfImageFormat.entries.forEach { option ->
                                WorkspaceChoice(title = if (option == PdfImageFormat.JPEG) "JPG" else "PNG",
                                    description = stringResource(if (option == PdfImageFormat.JPEG)
                                        R.string.utility_workspace_jpg_hint else R.string.utility_workspace_png_hint),
                                    selected = format == option, enabled = enabled, onSelect = { onFormat(option) })
                            }
                        }
                        if (destinationReady) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(painterResource(R.drawable.ic_check_24), null, tint = MaterialTheme.colorScheme.primary)
                                Text(stringResource(R.string.utility_destination_ready), Modifier.weight(1f).padding(start = 8.dp),
                                    style = MaterialTheme.typography.bodySmall)
                                TextButton(onClick = onDestination, enabled = enabled) {
                                    Text(stringResource(R.string.utility_workspace_change_destination))
                                }
                            }
                        }
                    }
                }

            }
        }
    }
}

@Composable
private fun WorkspaceSource(document: DocumentModel?, enabled: Boolean, onChoose: () -> Unit) {
    Surface(onClick = onChoose, enabled = enabled, shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            if (document != null) DocumentThumbnail(document, Modifier.width(48.dp).aspectRatio(595f / 842f)
                .clip(MaterialTheme.shapes.small))
            else Icon(painterResource(R.drawable.ic_document_import), null,
                Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(document?.name ?: stringResource(R.string.utility_select_pdf),
                    style = MaterialTheme.typography.titleSmall.copy(textDirection = TextDirection.ContentOrLtr),
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(if (document == null) stringResource(R.string.utility_workspace_choose_hint)
                    else stringResource(R.string.utility_source_pages, document.pageCount) + " · " +
                        Formatter.formatShortFileSize(LocalContext.current, document.sizeBytes),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (document != null) Text(stringResource(R.string.utility_change_pdf),
                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun WorkspacePageTile(document: DocumentModel, index: Int, selected: Boolean, enabled: Boolean, onToggle: () -> Unit) {
    val description = stringResource(R.string.utility_workspace_page, index + 1)
    var inspecting by rememberSaveable(document.id, index) { mutableStateOf(false) }
    Surface(
        modifier = Modifier.semantics { contentDescription = description }
            .selectable(selected, enabled = enabled, role = Role.Checkbox, onClick = onToggle),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(if (selected) 2.dp else 1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.fillMaxWidth().aspectRatio(595f / 842f).clip(MaterialTheme.shapes.small)
                .background(Color.White), contentAlignment = Alignment.Center) {
                if (!document.isProtected) DocumentPageThumbnail(document.pdfUri, index, Modifier.fillMaxSize())
                else Icon(painterResource(R.drawable.ic_document_lock), null, tint = MaterialTheme.colorScheme.primary)
                if (selected) Surface(Modifier.align(Alignment.TopEnd).padding(4.dp), shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary) {
                    Icon(painterResource(R.drawable.ic_check_24), null, Modifier.size(20.dp).padding(2.dp))
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text((index + 1).toString(), Modifier.weight(1f).padding(start = 6.dp),
                    style = MaterialTheme.typography.labelLarge.copy(textDirection = TextDirection.Ltr))
                if (!document.isProtected) IconButton(onClick = { inspecting = true }, enabled = enabled) {
                    Icon(painterResource(R.drawable.ic_preview_expand),
                        stringResource(R.string.utility_workspace_inspect_page, index + 1), Modifier.size(20.dp))
                }
            }
        }
    }
    if (inspecting && !document.isProtected) Dialog(
        onDismissRequest = { inspecting = false },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainer) {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(description, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = { inspecting = false }) {
                        Icon(painterResource(R.drawable.ic_toast_close), stringResource(R.string.preview_zoom_close))
                    }
                }
                DocumentPreviewZoom(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentKey = "${document.id}:$index",
                    singleFingerPan = true,
                    allowFullscreen = false,
                    contentAspectRatio = 595f / 842f,
                ) {
                    DocumentPdfPage(document.pdfUri, index, Modifier.fillMaxSize())
                }
                Button(onClick = { onToggle(); inspecting = false }, enabled = enabled,
                    modifier = Modifier.fillMaxWidth().padding(16.dp).heightIn(min = 48.dp)) {
                    Text(stringResource(if (selected) R.string.utility_workspace_deselect_page
                        else R.string.utility_workspace_select_page))
                }
            }
        }
    }
}

@Composable
private fun WorkspaceChoice(title: String, description: String, selected: Boolean, enabled: Boolean, onSelect: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onSelect),
        shape = MaterialTheme.shapes.medium,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            RadioButton(selected, onClick = null, enabled = enabled)
        }
    }
}

@Composable
private fun WorkspaceNotice(message: String, error: Boolean = false) {
    Surface(shape = MaterialTheme.shapes.medium,
        color = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer) {
        Text(message, Modifier.fillMaxWidth().padding(14.dp), style = MaterialTheme.typography.bodyMedium,
            color = if (error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer)
    }
}

@Preview(name = "Extrair páginas", widthDp = 390, heightDp = 844, locale = "pt-rBR")
@Preview(name = "Extrair páginas escuro", widthDp = 390, heightDp = 844, locale = "pt-rBR", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Extrair páginas fonte ampliada", widthDp = 320, heightDp = 740, locale = "pt-rBR", fontScale = 1.5f)
@Composable
private fun PdfUtilityWorkspacePreview() = PdfUtilityPreviewContent(PdfUtility.EXTRACT_PAGES)

@Preview(name = "Comprimir PDF", widthDp = 390, heightDp = 844, locale = "pt-rBR")
@Composable
private fun PdfCompressionWorkspacePreview() = PdfUtilityPreviewContent(PdfUtility.COMPRESS_PDF)

@Preview(name = "PDF para imagens", widthDp = 390, heightDp = 844, locale = "pt-rBR")
@Composable
private fun PdfImagesWorkspacePreview() = PdfUtilityPreviewContent(PdfUtility.PDF_TO_IMAGES)

@Composable
private fun PdfUtilityPreviewContent(tool: PdfUtility) {
    val document = DocumentModel(1, "Contrato de prestação de serviços", "preview", 6, 0, 1_250_000)
    ScaniqoTheme {
        PdfUtilityWorkspace(tool, PdfUtilityUiState(loading = false), document,
            "Contrato — páginas", "1,3-5", "", PdfImageFormat.JPEG, PdfCompressionLevel.BALANCED,
            false, false, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})
    }
}
