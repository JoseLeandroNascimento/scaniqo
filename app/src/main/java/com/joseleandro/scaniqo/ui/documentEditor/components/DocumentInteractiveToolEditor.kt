package com.joseleandro.scaniqo.ui.documentEditor.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.joseleandro.scaniqo.R
import com.joseleandro.scaniqo.domain.model.PdfInteractivePixels
import com.joseleandro.scaniqo.domain.model.PdfPageLayout
import com.joseleandro.scaniqo.domain.model.PdfPagePreview
import com.joseleandro.scaniqo.domain.model.PdfPageSpec
import com.joseleandro.scaniqo.ui.documentCommon.components.DocumentPdfPage
import com.joseleandro.scaniqo.ui.documentEditor.model.DocumentPageScope
import com.joseleandro.scaniqo.ui.documentEditor.model.DocumentPageScopeMode

/** O resumo sempre indica os alvos reais; abrir o menu não altera a seleção. */
@Composable
internal fun DocumentToolScopeMenu(
    pages: List<PdfPageSpec>,
    currentPageId: String,
    scope: DocumentPageScope,
    onScopeChange: (DocumentPageScope) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    var choosing by remember { mutableStateOf(false) }
    val targets = scope.targets(pages, currentPageId)
    val summary = when (scope.mode) {
        DocumentPageScopeMode.CURRENT -> stringResource(
            R.string.interactive_scope_current_summary,
            pages.indexOfFirst { it.id == currentPageId } + 1,
        )

        DocumentPageScopeMode.SELECTED -> pluralStringResource(
            R.plurals.interactive_scope_selected_summary, targets.size, targets.size,
        )

        DocumentPageScopeMode.ALL -> stringResource(
            R.string.interactive_scope_all_summary,
            targets.size
        )
    }
    val description = stringResource(R.string.interactive_scope_description, summary)
    Box(modifier) {
        TextButton(
            onClick = { expanded = true },
            enabled = enabled,
            modifier = Modifier
                .heightIn(min = 48.dp)
                .semantics { contentDescription = description },
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
        ) {
            Icon(painterResource(R.drawable.ic_tool_pages), null, Modifier.size(18.dp))
            Text(
                summary,
                Modifier.padding(horizontal = 6.dp),
                style = MaterialTheme.typography.labelMedium
            )
            Icon(
                painterResource(R.drawable.arrow_back_24px),
                null,
                Modifier
                    .size(16.dp)
                    .rotate(270f)
            )
        }
        DropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
            DocumentPageScopeMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(
                                when (mode) {
                                    DocumentPageScopeMode.CURRENT -> R.string.interactive_scope_current
                                    DocumentPageScopeMode.SELECTED -> R.string.interactive_scope_choose
                                    DocumentPageScopeMode.ALL -> R.string.interactive_scope_all
                                }
                            )
                        )
                    },
                    trailingIcon = {
                        if (mode == scope.mode) Icon(painterResource(R.drawable.ic_check_24), null)
                    },
                    onClick = {
                        expanded = false
                        if (mode == DocumentPageScopeMode.SELECTED) choosing = true
                        else onScopeChange(scope.copy(mode = mode))
                    },
                )
            }
        }
    }
    if (choosing) {
        DocumentPagePickerDialog(
            pages = pages,
            selectedIds = scope.pageIds.ifEmpty { setOf(currentPageId) },
            allowMultiple = true,
            onDismiss = { choosing = false },
            onConfirm = {
                onScopeChange(DocumentPageScope(DocumentPageScopeMode.SELECTED, it))
                choosing = false
            },
        )
    }
}

/** Percorre apenas os alvos, mantendo a numeração original mesmo em seleções descontínuas. */
@Composable
internal fun DocumentToolTargetNavigation(
    pages: List<PdfPageSpec>,
    targetIds: Set<String>,
    previewPageId: String,
    onPageChange: (String) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val targets = remember(pages, targetIds) { pages.filter { it.id in targetIds } }
    val targetIndex = targets.indexOfFirst { it.id == previewPageId }.coerceAtLeast(0)
    val pageNumber = pages.indexOfFirst { it.id == previewPageId } + 1
    var choosing by remember { mutableStateOf(false) }
    val description = stringResource(
        R.string.interactive_preview_page_description, pageNumber, targetIndex + 1, targets.size,
    )
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (targets.size > 1) {
            IconButton(
                onClick = { targets.getOrNull(targetIndex - 1)?.let { onPageChange(it.id) } },
                enabled = enabled && targetIndex > 0,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    painterResource(R.drawable.arrow_back_24px),
                    stringResource(R.string.corrections_preview_previous)
                )
            }
        }
        TextButton(
            onClick = { choosing = true },
            enabled = enabled && targets.size > 1,
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
            modifier = Modifier
                .heightIn(min = 48.dp)
                .semantics { contentDescription = description },
        ) {
            Text(
                stringResource(R.string.interactive_preview_page, pageNumber),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (targets.size > 1) {
            IconButton(
                onClick = { targets.getOrNull(targetIndex + 1)?.let { onPageChange(it.id) } },
                enabled = enabled && targetIndex < targets.lastIndex,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    painterResource(R.drawable.arrow_back_24px),
                    stringResource(R.string.corrections_preview_next),
                    Modifier.rotate(180f),
                )
            }
        }
    }
    if (choosing) {
        DocumentPagePickerDialog(
            pages = targets,
            selectedIds = setOf(previewPageId),
            allowMultiple = false,
            onDismiss = { choosing = false },
            onConfirm = { ids -> ids.firstOrNull()?.let(onPageChange); choosing = false },
            pageNumbers = pages.mapIndexed { index, page -> page.id to index + 1 }.toMap(),
        )
    }
}

/** A folha tem área própria. A paleta e o controle flutuante não encobrem a captura. */
@Composable
internal fun DocumentInteractivePreviewStage(
    modifier: Modifier = Modifier,
    tools: @Composable ColumnScope.() -> Unit,
    controls: @Composable () -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    Surface(
        modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(Modifier.padding(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxSize(),
                    contentAlignment = Alignment.Center,
                    content = content
                )
                Column(
                    Modifier
                        .width(48.dp)
                        .verticalScroll(rememberScrollState())
                        .selectableGroup(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    content = tools,
                )
            }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                shadowElevation = 2.dp,
            ) {
                Box(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) { controls() }
            }
        }
    }
}

/** Ícones compactos mantêm alvos de toque completos e nomes acessíveis. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DocumentPreviewToolButton(
    label: String,
    iconRes: Int,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
    ) {
        Surface(
            modifier = Modifier
                .size(48.dp)
                .selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
                .semantics { contentDescription = label },
            shape = MaterialTheme.shapes.medium,
            color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
            contentColor = when {
                !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = .38f)
                selected -> MaterialTheme.colorScheme.onPrimaryContainer
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            border = if (selected) BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.primary.copy(alpha = .4f)
            ) else null,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(painterResource(iconRes), null, Modifier.size(24.dp))
            }
        }
    }
}

/** Mantém a captura em memória durante os gestos e usa o PDF quando ela não está disponível. */
@Composable
internal fun DocumentInteractivePagePreview(
    page: PdfPageSpec,
    preview: PdfPagePreview?,
    fallback: PdfPagePreview?,
    isLoading: Boolean,
    hasError: Boolean,
    onRetry: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    pageNumber: Int = page.sourcePageIndex + 1,
    reserveHandleSpace: Boolean = false,
    livePixels: PdfInteractivePixels? = null,
    liveLayout: PdfPageLayout = page.layout,
    workspaceId: String? = null,
    pageCount: Int = 1,
    showPageNumbers: Boolean = false,
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    val handleMargin = if (reserveHandleSpace) 24.dp else 6.dp
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .padding(handleMargin),
        contentAlignment = Alignment.Center
    ) {
        val paperWidth = minOf(maxWidth, maxHeight * (595f / 842f)).coerceAtLeast(1.dp)
        Box(Modifier
            .width(paperWidth)
            .aspectRatio(595f / 842f)) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = RectangleShape,
                color = Color.White,
                shadowElevation = 3.dp,
                border = BorderStroke(.5.dp, Color.Black.copy(alpha = .16f)),
            ) {
                key(workspaceId, page.id, page.sourceUri, page.sourcePageIndex) {
                    if (livePixels != null) {
                        DocumentLivePagePreview(
                            pixels = livePixels,
                            layout = liveLayout,
                            signatures = page.signatures,
                            pageNumber = pageNumber,
                            pageCount = pageCount,
                            showPageNumbers = showPageNumbers,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        DocumentPdfPage(
                            pdfUri = preview?.uri ?: fallback?.uri ?: page.sourceUri,
                            pageIndex = preview?.pageIndex ?: fallback?.pageIndex
                            ?: page.sourcePageIndex,
                            contentKey = "interactive-$workspaceId-${page.id}",
                            modifier = Modifier.fillMaxSize(),
                            pageNumber = pageNumber,
                        )
                    }
                }
            }
            // As alças são irmãs do papel: não são recortadas pela Surface da folha.
            overlay()
        }
        if (isLoading) {
            val description = stringResource(R.string.interactive_preview_busy)
            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = MaterialTheme.shapes.small,
            ) {
                CircularProgressIndicator(
                    Modifier
                        .padding(6.dp)
                        .size(16.dp)
                        .semantics { contentDescription = description },
                    strokeWidth = 2.dp,
                )
            }
        }
        if (hasError) {
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter),
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ) {
                Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        stringResource(R.string.corrections_preview_error),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                    )
                    TextButton(onClick = onRetry, enabled = enabled) {
                        Text(stringResource(R.string.corrections_preview_retry))
                    }
                }
            }
        }
    }
}
