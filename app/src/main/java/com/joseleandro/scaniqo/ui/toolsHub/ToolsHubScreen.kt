package com.joseleandro.scaniqo.ui.toolsHub

import android.content.res.Configuration
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joseleandro.scaniqo.R
import com.joseleandro.scaniqo.domain.model.PdfUtility
import com.joseleandro.scaniqo.ui.documentCommon.components.DocumentThumbnail
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
    onSign: () -> Unit,
    onUtility: (PdfUtility) -> Unit,
    onEdit: (Long, DocumentTool?) -> Unit,
    onImportForEditor: (DocumentTool?) -> Unit,
    onRetryDocuments: () -> Unit,
    snackbarHost: @Composable () -> Unit = {},
) {
    var selectedEditor by rememberSaveable { mutableStateOf<CatalogTool?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var group by rememberSaveable { mutableStateOf(ToolGroup.ALL) }
    val focus = LocalFocusManager.current
    val labels = CatalogTool.entries.associateWith { stringResource(it.title) to stringResource(it.hint) }
    val visible = CatalogTool.entries.filter {
        (group == ToolGroup.ALL || group == it.group) &&
            (labels.getValue(it).first.contains(query.trim(), ignoreCase = true) ||
                labels.getValue(it).second.contains(query.trim(), ignoreCase = true))
    }
    fun open(tool: CatalogTool) {
        if (!enabled) return
        focus.clearFocus()
        when (tool) {
            CatalogTool.IMPORT -> onImport()
            CatalogTool.MERGE -> onMerge()
            CatalogTool.EXTRACT -> onUtility(PdfUtility.EXTRACT_PAGES)
            CatalogTool.PDF_IMAGES -> onUtility(PdfUtility.PDF_TO_IMAGES)
            CatalogTool.COMPRESS -> onUtility(PdfUtility.COMPRESS_PDF)
            CatalogTool.SIGN -> onSign()
            else -> selectedEditor = tool
        }
    }
    Scaffold(
        snackbarHost = snackbarHost,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tool_catalog_title)) },
                navigationIcon = { IconButton(onClick = onBack, enabled = enabled) {
                    Icon(painterResource(R.drawable.arrow_back_24px), stringResource(R.string.tools_hub_back))
                } },
            )
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(if (LocalDensity.current.fontScale > 1.3f) 230.dp else 154.dp),
            modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(stringResource(R.string.tool_catalog_heading), Modifier.padding(top = 12.dp).semantics { heading() },
                        style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(stringResource(R.string.tool_catalog_search), maxLines = 1, overflow = TextOverflow.Ellipsis) }, singleLine = true,
                        shape = MaterialTheme.shapes.extraLarge,
                        leadingIcon = { Icon(painterResource(R.drawable.ic_document_search), null) },
                        trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) {
                            Icon(painterResource(R.drawable.ic_toast_close), stringResource(R.string.tool_catalog_clear))
                        } },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(ToolGroup.entries, key = { it.name }) { option ->
                            FilterChip(selected = group == option, onClick = { group = option; focus.clearFocus() },
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
                    actionLabel = stringResource(R.string.tool_catalog_reset),
                    onAction = { query = ""; group = ToolGroup.ALL },
                )
            }
            items(visible, key = { it.name }) { tool ->
                ToolTile(tool, labels.getValue(tool).first, labels.getValue(tool).second, enabled) { open(tool) }
            }
        }
    }
    selectedEditor?.let { selected ->
        EditorDocumentPicker(
            documents = documents,
            actionTitle = stringResource(R.string.tool_catalog_choose_for, stringResource(selected.title)),
            enabled = enabled,
            onDismiss = { selectedEditor = null },
            onRetry = onRetryDocuments,
            onImport = {
                selectedEditor = null
                onImportForEditor(if (selected == CatalogTool.FILL_PAGE) DocumentTool.FORMS else null)
            },
            onSelect = { id ->
                selectedEditor = null
                onEdit(id, when (selected) {
                    CatalogTool.FILL_PAGE -> DocumentTool.FORMS
                    else -> null
                })
            },
        )
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
        color = lerp(MaterialTheme.colorScheme.surface, accent, if (dark) .07f else .045f),
        border = BorderStroke(1.dp, accent.copy(alpha = .16f)),
    ) {
        Column(Modifier.heightIn(min = 184.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(shape = MaterialTheme.shapes.medium, color = accent.copy(alpha = .13f)) {
                Icon(painterResource(tool.icon), null, Modifier.padding(12.dp).size(26.dp), tint = accent)
            }
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorDocumentPicker(
    documents: DocumentsUiState,
    actionTitle: String,
    enabled: Boolean,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onImport: () -> Unit,
    onSelect: (Long) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val visible = documents.documents.filter { it.name.contains(query.trim(), ignoreCase = true) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(actionTitle, style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.tools_hub_choose_hint), style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = onImport, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                Icon(painterResource(R.drawable.ic_document_import), null, Modifier.padding(end = 8.dp).size(20.dp))
                Text(stringResource(R.string.utility_workspace_import_device))
            }
            OutlinedTextField(
                query, { query = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                label = { Text(stringResource(R.string.tools_hub_search)) },
            )
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 440.dp)) {
                when {
                    documents.isLoading -> item { CircularProgressIndicator(Modifier.padding(20.dp)) }
                    documents.loadError -> item {
                        Text(stringResource(R.string.documents_error_title))
                        TextButton(onClick = onRetry) { Text(stringResource(R.string.documents_retry)) }
                    }
                    visible.isEmpty() -> item {
                        Text(stringResource(if (documents.documents.isEmpty()) R.string.tools_hub_empty else R.string.quick_sign_no_results),
                            modifier = Modifier.padding(vertical = 16.dp))
                    }
                    else -> items(visible, key = { it.id }) { document ->
                        Surface(onClick = { onSelect(document.id) }, enabled = enabled) {
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                DocumentThumbnail(document, Modifier.width(40.dp).aspectRatio(1f / 1.4142f).clip(MaterialTheme.shapes.extraSmall))
                                Column(Modifier.weight(1f)) {
                                    Text(document.name, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                                    Text(pluralStringResource(R.plurals.document_page_count, document.pageCount, document.pageCount),
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}


@Preview(name = "Ferramentas claro", widthDp = 390, heightDp = 844, locale = "pt-rBR")
@Preview(name = "Ferramentas escuro", widthDp = 390, heightDp = 844, locale = "pt-rBR", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Ferramentas fonte ampliada", widthDp = 320, heightDp = 640, locale = "pt-rBR", fontScale = 1.5f)
@Composable
private fun ToolsHubPreview() {
    ScaniqoTheme {
        ToolsHubScreen(DocumentsUiState(isLoading = false), true, {}, {}, {}, {}, {}, { _, _ -> }, {}, {})
    }
}
