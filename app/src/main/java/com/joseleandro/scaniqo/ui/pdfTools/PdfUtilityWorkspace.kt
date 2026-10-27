package com.joseleandro.scaniqo.ui.pdfTools

import android.content.res.Configuration
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.animateColorAsState
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.pluralStringResource
import com.joseleandro.scaniqo.ui.documentCommon.components.DocumentPageJumpDialog
import kotlinx.coroutines.launch
import android.text.format.Formatter
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import com.joseleandro.scaniqo.ui.ads.ToolAdActionContent
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
    pageSelection: PdfUtilityPageSelectionState,
    password: String,
    format: PdfImageFormat,
    compression: PdfCompressionLevel,
    externalPicker: Boolean,
    onBack: () -> Unit,
    onChooseDocument: () -> Unit,
    onPageSelection: (PdfUtilityPageSelectionState) -> Unit,
    onPassword: (String) -> Unit,
    onFormat: (PdfImageFormat) -> Unit,
    onCompression: (PdfCompressionLevel) -> Unit,
    onAction: () -> Unit,
    onRetry: () -> Unit,
    onOpenDocument: (Long) -> Unit,
    showsVideo: Boolean = false,
    actionInProgress: Boolean = false,
) {
    if (tool == PdfUtility.COMPRESS_PDF) {
        PdfCompressionWorkspace(
            state = state, document = document, password = password,
            compression = compression, externalPicker = externalPicker,
            onBack = onBack, onChooseDocument = onChooseDocument,
            onPassword = onPassword, onCompression = onCompression,
            onAction = onAction, onRetry = onRetry, onOpenDocument = onOpenDocument,
            showsVideo = showsVideo, actionInProgress = actionInProgress,
        )
        return
    }
    val extract = tool == PdfUtility.EXTRACT_PAGES
    val awaitingSave = state.awaitingSaveOperationId != null
    val enabled = !state.busy && !externalPicker && !awaitingSave && !actionInProgress
    val pageCount = document?.pageCount ?: 0
    val validPageCount = pageCount in 1..100
    val selection = remember(pageSelection.selection, pageCount) {
        if (validPageCount) pageSelection.selection.resolve(pageCount) else emptyList()
    }
    val hasSelectionDraft = pageSelection.draftActive || pageSelection.from.isNotBlank() || pageSelection.until.isNotBlank()
    val ready = enabled && document != null && validPageCount &&
        (!document.isProtected || password.isNotEmpty()) && selection.isNotEmpty() && !hasSelectionDraft
    val canConfirmSelection = enabled && validPageCount && hasSelectionDraft &&
        pageSelection.from.isBlank() && pageSelection.until.isBlank() &&
        runCatching { UtilityPageSelection.fromText(pageSelection.draft, pageCount) }.isSuccess
    val title = if (extract) R.string.utility_extract_title else R.string.utility_images_export_title
    val pageGridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    var jumpToPage by rememberSaveable(document?.id) { mutableStateOf(false) }
    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(title)) },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = enabled) {
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
                    Button(onClick = {
                        if (hasSelectionDraft) onPageSelection(pageSelection.confirm(pageCount)) else onAction()
                    }, enabled = if (hasSelectionDraft) canConfirmSelection else ready,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                        ToolAdActionContent(
                            isVideo = showsVideo && !hasSelectionDraft,
                            label = when {
                                awaitingSave -> stringResource(R.string.utility_output_finish_in_sheet)
                                state.busy || actionInProgress -> stringResource(R.string.utility_workspace_busy)
                                hasSelectionDraft -> stringResource(R.string.pages_v2_confirm_selection)
                                showsVideo -> stringResource(if (extract) R.string.tool_ad_watch_apply else R.string.tool_ad_watch_export)
                                selection.isEmpty() -> stringResource(if (extract) R.string.utility_extract_action else R.string.utility_export_action)
                                else -> pluralStringResource(if (extract) R.plurals.pages_v2_create_pdf
                                    else R.plurals.pages_v2_export_images, selection.size, selection.size)
                            },
                        )
                    }
                    if (showsVideo && !hasSelectionDraft) Text(
                        stringResource(R.string.tool_ad_reward_hint),
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (hasSelectionDraft) TextButton(
                        onClick = { onPageSelection(pageSelection.cancel(pageCount)) },
                        enabled = enabled, modifier = Modifier.align(Alignment.CenterHorizontally),
                    ) { Text(stringResource(R.string.pages_v2_cancel_edit)) }
                }
            }
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(88.dp),
            state = pageGridState,
            modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding),
            contentPadding = PaddingValues(20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "controls", span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (state.busy || state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                    if (state.loadError) {
                        WorkspaceNotice(stringResource(R.string.utility_library_error), error = true)
                        TextButton(onClick = onRetry, enabled = enabled) { Text(stringResource(R.string.documents_retry)) }
                    }
                    state.error?.let { WorkspaceNotice(localizedRuntimeMessage(it), error = true) }
                    state.message?.let { WorkspaceNotice(localizedRuntimeMessage(it)) }
                    if (awaitingSave) WorkspaceNotice(stringResource(R.string.utility_output_ready_to_save))
                    WorkspaceSource(document, enabled, onChooseDocument)
                    if (document != null) {
                        if (!extract) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(stringResource(R.string.utility_workspace_format), style = MaterialTheme.typography.labelLarge)
                                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                                    PdfImageFormat.entries.forEachIndexed { index, option ->
                                        SegmentedButton(
                                            selected = format == option,
                                            onClick = { onFormat(option) },
                                            enabled = enabled,
                                            shape = SegmentedButtonDefaults.itemShape(index, PdfImageFormat.entries.size),
                                        ) { Text(if (option == PdfImageFormat.JPEG) "JPG" else "PNG") }
                                    }
                                }
                                Text(stringResource(R.string.utility_workspace_zip_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (document.isProtected) {
                            OutlinedTextField(
                                value = password, onValueChange = onPassword, modifier = Modifier.fillMaxWidth(),
                                singleLine = true, enabled = enabled, label = { Text(stringResource(R.string.utility_password)) },
                                leadingIcon = { Icon(painterResource(R.drawable.ic_document_lock), null) },
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            )
                            Text(stringResource(R.string.utility_workspace_locked_pages),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (validPageCount) {
                            PdfUtilityPageSelectionControls(
                                state = pageSelection, pageCount = pageCount, enabled = enabled,
                                onChange = onPageSelection, onGoToPage = { jumpToPage = true },
                            )
                        } else {
                            WorkspaceNotice(localizedRuntimeMessage(
                                runCatching { PdfPageSelection.parse("", pageCount) }.exceptionOrNull()?.message.orEmpty()),
                                error = true)
                        }
                    }
                }
            }
            if (document != null && validPageCount && pageSelection.mode == UtilityPageSelectionMode.THUMBNAILS) {
                items(count = pageCount, key = { "${document.id}:$it" }) { index ->
                    WorkspacePageTile(document, index, index in selection, enabled && !hasSelectionDraft) {
                        onPageSelection(pageSelection.select(pageSelection.selection.toggle(index, pageCount), pageCount))
                    }
                }
            }
        }
    }
    if (jumpToPage && validPageCount) DocumentPageJumpDialog(
        pageCount = pageCount, onDismiss = { jumpToPage = false },
        onPage = { index -> scope.launch { pageGridState.animateScrollToItem(index + 1) } },
    )
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
                if (document != null && enabled) Text(stringResource(R.string.utility_change_pdf),
                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun WorkspacePageTile(document: DocumentModel, index: Int, selected: Boolean, enabled: Boolean, onToggle: () -> Unit) {
    val description = stringResource(R.string.utility_workspace_page, index + 1)
    var inspecting by rememberSaveable(document.id, index) { mutableStateOf(false) }
    var pageAspectRatio by rememberSaveable(document.pdfUri, index) { mutableStateOf(595f / 842f) }
    val selectionColor by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        label = "pageSelection",
    )
    Surface(
        modifier = Modifier.semantics { contentDescription = description }
            .selectable(selected, enabled = enabled, role = Role.Checkbox, onClick = onToggle),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(if (selected) 2.dp else 1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
        color = selectionColor,
    ) {
        Column(Modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.fillMaxWidth().aspectRatio(pageAspectRatio).clip(MaterialTheme.shapes.small)
                .background(Color.White), contentAlignment = Alignment.Center) {
                if (!document.isProtected) DocumentPageThumbnail(document.pdfUri, index, Modifier.fillMaxSize(),
                    onImageBitmapChanged = { bitmap ->
                        if (bitmap != null && bitmap.height > 0) pageAspectRatio = bitmap.width.toFloat() / bitmap.height
                    })
                else Icon(painterResource(R.drawable.ic_document_lock), null, tint = MaterialTheme.colorScheme.primary)
                androidx.compose.animation.AnimatedVisibility(
                    visible = selected, modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
                    enter = fadeIn() + scaleIn(initialScale = .8f),
                    exit = fadeOut() + scaleOut(targetScale = .8f),
                ) {
                    Surface(shape = MaterialTheme.shapes.extraLarge,
                        color = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary) {
                        Icon(painterResource(R.drawable.ic_check_24), null, Modifier.size(20.dp).padding(2.dp))
                    }
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
                    contentAspectRatio = pageAspectRatio,
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

@Preview(name = "Extração aguardando salvamento", widthDp = 390, heightDp = 844, locale = "pt-rBR")
@Composable
private fun PdfExtractAwaitingSavePreview() = PdfUtilityPreviewContent(
    PdfUtility.EXTRACT_PAGES,
    PdfUtilityUiState(loading = false, awaitingSaveOperationId = "preview:1"),
)

@Preview(name = "Seleção por intervalos", widthDp = 390, heightDp = 844, locale = "pt-rBR")
@Preview(name = "Intervalos compacto", widthDp = 320, heightDp = 740, locale = "pt-rBR", fontScale = 1.5f)
@Composable
private fun PdfUtilityRangesPreview() = PdfUtilityPreviewContent(
    tool = PdfUtility.EXTRACT_PAGES,
    selection = PdfUtilityPageSelectionState(
        selection = UtilityPageSelection.none(), mode = UtilityPageSelectionMode.INTERVALS,
        draft = "2-4, 6", draftActive = true,
    ),
)

@Composable
private fun PdfUtilityPreviewContent(
    tool: PdfUtility,
    state: PdfUtilityUiState = PdfUtilityUiState(loading = false),
    selection: PdfUtilityPageSelectionState = PdfUtilityPageSelectionState(
        selection = UtilityPageSelection.fromText("1,3-5", 6),
    ),
) {
    val document = DocumentModel(1, "Contrato de prestação de serviços", "preview", 6, 0, 1_250_000)
    ScaniqoTheme {
        PdfUtilityWorkspace(
            tool = tool, state = state, document = document, pageSelection = selection, password = "",
            format = PdfImageFormat.JPEG, compression = PdfCompressionLevel.BALANCED,
            externalPicker = false, onBack = {}, onChooseDocument = {}, onPageSelection = {}, onPassword = {},
            onFormat = {}, onCompression = {}, onAction = {}, onRetry = {}, onOpenDocument = {},
        )
    }
}
