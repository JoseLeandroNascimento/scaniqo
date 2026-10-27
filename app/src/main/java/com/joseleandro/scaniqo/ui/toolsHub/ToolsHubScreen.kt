package com.joseleandro.scaniqo.ui.toolsHub

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joseleandro.scaniqo.R
import com.joseleandro.scaniqo.domain.model.PdfUtility
import com.joseleandro.scaniqo.ui.ads.ToolsBannerAd
import com.joseleandro.scaniqo.ui.documentCommon.components.DocumentSourceStep
import com.joseleandro.scaniqo.ui.documentCommon.components.ScaniqoEmptyState
import com.joseleandro.scaniqo.ui.documentEditor.model.DocumentTool
import com.joseleandro.scaniqo.ui.documents.state.DocumentsUiState
import com.joseleandro.scaniqo.ui.theme.ScaniqoTheme

internal enum class ToolGroup(@StringRes val label: Int) {
    ALL(R.string.tool_catalog_all), ORGANIZE(R.string.tool_catalog_organize),
    CONVERT(R.string.tool_catalog_convert), EDIT(R.string.tool_catalog_edit), FILL(R.string.tool_catalog_fill)
}

/** Catálogo explícito: recursos descontinuados não reaparecem ao acrescentar valores aos enums do domínio. */
internal enum class CatalogTool(
    @StringRes val title: Int, @StringRes val hint: Int, @DrawableRes val icon: Int,
    val group: ToolGroup, val accent: Color,
) {
    IMPORT(R.string.quick_tools_import, R.string.quick_tools_import_hint, R.drawable.ic_document_import, ToolGroup.ORGANIZE, Color(0xFF396E9A)),
    MERGE(R.string.quick_tools_merge, R.string.quick_tools_merge_hint, R.drawable.ic_tool_merge, ToolGroup.ORGANIZE, Color(0xFF74549B)),
    EXTRACT(R.string.tools_hub_split, R.string.tools_hub_split_hint, R.drawable.ic_tool_extract, ToolGroup.ORGANIZE, Color(0xFFAA632B)),
    PDF_IMAGES(R.string.tools_hub_pdf_images, R.string.tools_hub_pdf_images_hint, R.drawable.ic_tool_export_images, ToolGroup.CONVERT, Color(0xFF9F5358)),
    COMPRESS(R.string.tool_catalog_compress, R.string.tool_catalog_compress_hint, R.drawable.ic_tool_compress, ToolGroup.ORGANIZE, Color(0xFF277F73)),
    EDITOR(R.string.tools_hub_editor, R.string.tools_hub_editor_hint, R.drawable.ic_tool_pdf_edit, ToolGroup.EDIT, Color(0xFF376B8E)),
    SIGN(R.string.quick_tools_sign, R.string.quick_tools_sign_hint, R.drawable.ic_tool_signature, ToolGroup.FILL, Color(0xFF735A91)),
    FILL_PAGE(R.string.tool_catalog_fill_page, R.string.tool_catalog_fill_hint, R.drawable.ic_tool_fill_page, ToolGroup.FILL, Color(0xFF387E88)),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsHubScreen(
    documents: DocumentsUiState,
    enabled: Boolean,
    onBack: () -> Unit,
    onImport: () -> Unit,
    onMerge: () -> Unit,
    onSignDocument: (Long) -> Unit,
    onImportForSign: () -> Unit,
    onUtility: (PdfUtility) -> Unit,
    onEdit: (Long, DocumentTool?) -> Unit,
    onImportForEditor: (DocumentTool?) -> Unit,
    onRetryDocuments: () -> Unit,
    snackbarHost: @Composable () -> Unit = {},
    banner: @Composable () -> Unit = {},
) {
    var selectedSource by rememberSaveable { mutableStateOf<CatalogTool?>(null) }
    var isSearching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var group by rememberSaveable { mutableStateOf(ToolGroup.ALL) }
    val gridState = rememberLazyGridState()
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val searchFocus = remember { FocusRequester() }
    var requestSearchFocus by remember { mutableStateOf(false) }
    var previousFilter by remember { mutableStateOf(query to group) }
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    LaunchedEffect(isSearching, requestSearchFocus, selectedSource) {
        if (isSearching && requestSearchFocus && selectedSource == null) {
            searchFocus.requestFocus()
            keyboard?.show()
            requestSearchFocus = false
        }
    }
    LaunchedEffect(query, group) {
        val filter = query to group
        if (previousFilter != filter) {
            gridState.scrollToItem(0)
            previousFilter = filter
        }
    }
    fun closeSearch() {
        focus.clearFocus()
        keyboard?.hide()
        query = ""
        isSearching = false
        requestSearchFocus = false
    }
    val searchLabel = stringResource(R.string.tool_catalog_search)
    val labels = CatalogTool.entries.associateWith { stringResource(it.title) to stringResource(it.hint) }
    val visible = CatalogTool.entries.filter {
        (group == ToolGroup.ALL || group == it.group) &&
            (labels.getValue(it).first.contains(query.trim(), ignoreCase = true) ||
                labels.getValue(it).second.contains(query.trim(), ignoreCase = true))
    }
    fun open(tool: CatalogTool) {
        if (!enabled) return
        focus.clearFocus()
        keyboard?.hide()
        when (tool) {
            CatalogTool.IMPORT -> onImport()
            CatalogTool.MERGE -> onMerge()
            CatalogTool.EXTRACT -> onUtility(PdfUtility.EXTRACT_PAGES)
            CatalogTool.PDF_IMAGES -> onUtility(PdfUtility.PDF_TO_IMAGES)
            CatalogTool.COMPRESS -> onUtility(PdfUtility.COMPRESS_PDF)
            CatalogTool.EDITOR, CatalogTool.SIGN, CatalogTool.FILL_PAGE -> selectedSource = tool
        }
    }
    BackHandler(enabled = (selectedSource != null && enabled) ||
        (selectedSource == null && isSearching && !imeVisible)) {
        if (selectedSource != null) selectedSource = null else closeSearch()
    }
    Scaffold(
        snackbarHost = snackbarHost,
        bottomBar = {
            if (selectedSource == null && !isSearching && enabled) banner()
        },
        topBar = {
            TopAppBar(
                title = {
                    AnimatedContent(
                        targetState = selectedSource == null && isSearching,
                        transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(90)) },
                        label = "Tool catalog search",
                    ) { searching ->
                        if (searching) {
                            TextField(
                                value = query,
                                onValueChange = { query = it },
                                modifier = Modifier.fillMaxWidth().focusRequester(searchFocus)
                                    .semantics { contentDescription = searchLabel },
                                placeholder = {
                                    Text(stringResource(R.string.tool_catalog_search),
                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                                },
                                textStyle = MaterialTheme.typography.bodyLarge.copy(
                                    textDirection = TextDirection.ContentOrLtr,
                                ),
                                singleLine = true,
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                ),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = {
                                    focus.clearFocus()
                                    keyboard?.hide()
                                }),
                            )
                        } else {
                            Text(stringResource(selectedSource?.title ?: R.string.tool_catalog_title))
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            when {
                                selectedSource != null -> selectedSource = null
                                isSearching -> closeSearch()
                                else -> onBack()
                            }
                        },
                        enabled = enabled,
                    ) {
                        Icon(painterResource(R.drawable.arrow_back_24px),
                            stringResource(R.string.tools_hub_back))
                    }
                },
                actions = {
                    if (selectedSource == null) {
                        if (isSearching) {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = {
                                    query = ""
                                    searchFocus.requestFocus()
                                    keyboard?.show()
                                }) {
                                    Icon(painterResource(R.drawable.ic_toast_close),
                                        stringResource(R.string.tool_catalog_clear))
                                }
                            }
                        } else {
                            IconButton(onClick = {
                                isSearching = true
                                requestSearchFocus = true
                            }, enabled = enabled) {
                                Icon(painterResource(R.drawable.ic_document_search),
                                    stringResource(R.string.tool_catalog_search))
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        val source = selectedSource
        if (source != null) {
            DocumentSourceStep(
                toolTitle = stringResource(source.title),
                documents = documents.documents,
                isLoading = documents.isLoading,
                loadError = documents.loadError,
                enabled = enabled,
                onSelectDocument = { document ->
                    if (enabled && selectedSource == source) {
                        when (source) {
                            CatalogTool.EDITOR -> onEdit(document.id, null)
                            CatalogTool.FILL_PAGE -> onEdit(document.id, DocumentTool.FORMS)
                            CatalogTool.SIGN -> onSignDocument(document.id)
                            else -> Unit
                        }
                    }
                },
                onPickDevice = {
                    if (enabled && selectedSource == source) {
                        when (source) {
                            CatalogTool.EDITOR -> onImportForEditor(null)
                            CatalogTool.FILL_PAGE -> onImportForEditor(DocumentTool.FORMS)
                            CatalogTool.SIGN -> onImportForSign()
                            else -> Unit
                        }
                    }
                },
                onRetryDocuments = onRetryDocuments,
                modifier = Modifier.padding(padding).consumeWindowInsets(padding),
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(if (LocalDensity.current.fontScale > 1.3f) 230.dp else 154.dp),
                state = gridState,
                modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(stringResource(R.string.tool_catalog_heading), Modifier.padding(top = 12.dp).semantics { heading() },
                            style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(ToolGroup.entries, key = { it.name }) { option ->
                                FilterChip(selected = group == option, onClick = { group = option; focus.clearFocus(); keyboard?.hide() },
                                    label = { Text(stringResource(option.label)) })
                            }
                        }
                    }
                }
                if (visible.isEmpty()) item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                    ScaniqoEmptyState(
                        title = stringResource(R.string.tool_catalog_empty),
                        description = stringResource(R.string.tool_catalog_empty_hint),
                        iconRes = R.drawable.ic_document_search,
                        actionLabel = stringResource(if (group == ToolGroup.ALL)
                            R.string.tool_catalog_clear else R.string.tool_catalog_reset),
                        onAction = { query = ""; if (group != ToolGroup.ALL) group = ToolGroup.ALL },
                    )
                }
                items(visible, key = { it.name }) { tool ->
                    ToolTile(tool, labels.getValue(tool).first, labels.getValue(tool).second, enabled) { open(tool) }
                }
            }
        }
    }
}

@Composable
private fun ToolTile(tool: CatalogTool, title: String, hint: String, enabled: Boolean, onClick: () -> Unit) {
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) .975f else 1f, label = "toolPress")
    val dark = MaterialTheme.colorScheme.surface.luminance() < .5f
    val accent = if (dark) lerp(tool.accent, Color.White, .5f) else tool.accent
    Surface(
        onClick = onClick, enabled = enabled, interactionSource = interactions,
        modifier = Modifier.fillMaxWidth().graphicsLayer { scaleX = scale; scaleY = scale },
        shape = MaterialTheme.shapes.extraLarge,
        color = if (dark) lerp(MaterialTheme.colorScheme.surface, accent, .07f)
            else lerp(MaterialTheme.colorScheme.surfaceContainerLow, accent, .045f),
        border = BorderStroke(1.dp, if (dark) accent.copy(alpha = .16f)
            else lerp(MaterialTheme.colorScheme.outlineVariant, accent, .2f)),
    ) {
        Column(Modifier.heightIn(min = 148.dp).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(shape = MaterialTheme.shapes.medium, color = accent.copy(alpha = .13f)) {
                Icon(painterResource(tool.icon), null, Modifier.padding(10.dp).size(24.dp), tint = accent)
            }
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}


@Preview(name = "Ferramentas claro", widthDp = 390, heightDp = 844, locale = "pt-rBR")
@Preview(name = "Ferramentas escuro", widthDp = 390, heightDp = 844, locale = "pt-rBR", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Ferramentas fonte ampliada", widthDp = 320, heightDp = 640, locale = "pt-rBR", fontScale = 1.5f)
@Preview(name = "Ferramentas fonte muito ampliada", widthDp = 320, heightDp = 640, locale = "pt-rBR", fontScale = 2f)
@Composable
private fun ToolsHubPreview() {
    ScaniqoTheme {
        ToolsHubScreen(
            documents = DocumentsUiState(isLoading = false),
            enabled = true,
            onBack = {},
            onImport = {},
            onMerge = {},
            onSignDocument = {},
            onImportForSign = {},
            onUtility = {},
            onEdit = { _, _ -> },
            onImportForEditor = {},
            onRetryDocuments = {},
            banner = { ToolsBannerAd() },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(name = "Assinar — escolher origem", widthDp = 390, heightDp = 844, locale = "pt-rBR")
@Preview(name = "Assinar — texto ampliado", widthDp = 320, heightDp = 640, locale = "pt-rBR", fontScale = 1.5f)
@Composable
private fun ToolsHubSourcePreview() {
    ScaniqoTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.quick_tools_sign)) },
                    navigationIcon = {
                        IconButton(onClick = {}) {
                            Icon(painterResource(R.drawable.arrow_back_24px), stringResource(R.string.tools_hub_back))
                        }
                    },
                )
            },
        ) { padding ->
            DocumentSourceStep(
                toolTitle = stringResource(R.string.quick_tools_sign),
                documents = emptyList(),
                isLoading = false,
                loadError = false,
                enabled = true,
                onSelectDocument = {},
                onPickDevice = {},
                onRetryDocuments = {},
                modifier = Modifier.padding(padding).consumeWindowInsets(padding),
            )
        }
    }
}
