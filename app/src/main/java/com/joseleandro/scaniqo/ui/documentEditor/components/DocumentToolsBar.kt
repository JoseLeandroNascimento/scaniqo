package com.joseleandro.scaniqo.ui.documentEditor.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.joseleandro.scaniqo.R
import com.joseleandro.scaniqo.domain.model.PdfPageSpec
import com.joseleandro.scaniqo.domain.model.PdfSignaturePlacement
import com.joseleandro.scaniqo.domain.model.PdfWorkspace
import com.joseleandro.scaniqo.ui.documentEditor.event.DocumentToolsEvent
import com.joseleandro.scaniqo.ui.documentEditor.model.DocumentTool
import com.joseleandro.scaniqo.ui.documentEditor.state.DocumentToolsUiState
import com.joseleandro.scaniqo.ui.documentEditor.util.titleRes
import com.joseleandro.scaniqo.ui.theme.ScaniqoTheme


@Composable
internal fun DocumentToolsBar(
    state: DocumentToolsUiState,
    onEvent: (DocumentToolsEvent) -> Unit,
    enabled: Boolean,
) {
    // A superfície cobre também os insets; somente os controles respeitam a área segura.
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier.windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
            ),
        ) {
            if (state.signatureDraft != null) {
                DocumentSignatureControls(state, onEvent, enabled)
            } else {
                if (state.tool == DocumentTool.FILTERS) {
                    DocumentFiltersBar(state, onEvent, enabled = enabled)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                val toolsScroll = rememberLazyListState()
                val rightToLeft = LocalLayoutDirection.current == LayoutDirection.Rtl
                val edgeColor = MaterialTheme.colorScheme.surfaceContainer
                val arrowColor = MaterialTheme.colorScheme.onSurfaceVariant
                // A indicação é desenhada sobre a faixa, sem interceptar toques ou arrastos.
                LazyRow(
                    state = toolsScroll,
                    modifier = Modifier.fillMaxWidth().drawWithContent {
                        drawContent()
                        if (toolsScroll.canScrollBackward) {
                            drawToolsOverflowIndicator(atLeft = !rightToLeft, edgeColor, arrowColor)
                        }
                        if (toolsScroll.canScrollForward) {
                            drawToolsOverflowIndicator(atLeft = rightToLeft, edgeColor, arrowColor)
                        }
                    },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(DocumentTool.entries.filterNot { it == DocumentTool.ANNOTATIONS || it == DocumentTool.REDACTION || it == DocumentTool.WATERMARK }, key = { it.name }) { tool ->
                        DocumentToolButton(
                            tool = tool,
                            selected = state.tool == tool,
                            enabled = state.canEdit && enabled,
                            onClick = { onEvent(DocumentToolsEvent.OpenTool(tool)) },
                        )
                    }
                }
                if (state.hasChanges || state.isSaving) {
                    Button(
                        onClick = { onEvent(DocumentToolsEvent.Save) },
                        enabled = state.canEdit && enabled,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
                            .heightIn(min = 48.dp),
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        if (state.isSaving) {
                            CircularProgressIndicator(
                                Modifier.padding(end = 8.dp).size(18.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(
                                painterResource(R.drawable.ic_check_24),
                                contentDescription = null,
                                modifier = Modifier.padding(end = 8.dp).size(20.dp),
                            )
                        }
                        Text(stringResource(if (state.isSaving) R.string.tools_saving else R.string.tools_save))
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawToolsOverflowIndicator(
    atLeft: Boolean,
    background: Color,
    foreground: Color,
) {
    val fadeWidth = 28.dp.toPx()
    val startX = if (atLeft) 0f else size.width - fadeWidth
    drawRect(
        brush = Brush.horizontalGradient(
            colors = if (atLeft) listOf(background, background.copy(alpha = 0f))
            else listOf(background.copy(alpha = 0f), background),
            startX = startX,
            endX = startX + fadeWidth,
        ),
        topLeft = Offset(startX, 0f),
        size = Size(fadeWidth, size.height),
    )
    val tipX = if (atLeft) 8.dp.toPx() else size.width - 8.dp.toPx()
    val tailX = tipX + if (atLeft) 4.dp.toPx() else -4.dp.toPx()
    val centerY = size.height / 2f
    val halfHeight = 4.dp.toPx()
    drawLine(
        foreground, Offset(tailX, centerY - halfHeight), Offset(tipX, centerY),
        strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round,
    )
    drawLine(
        foreground, Offset(tipX, centerY), Offset(tailX, centerY + halfHeight),
        strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round,
    )
}

@Composable
private fun DocumentToolButton(
    tool: DocumentTool,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val contentColor = when {
        !enabled -> colors.onSurface.copy(alpha = .38f)
        selected -> colors.onPrimaryContainer
        else -> colors.onSurfaceVariant
    }
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .widthIn(min = 72.dp)
            .heightIn(min = 76.dp)
            .semantics { this.selected = selected },
        shape = RoundedCornerShape(16.dp),
        color = if (selected) colors.primaryContainer else Color.Transparent,
        contentColor = contentColor,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
        ) {
            Box(Modifier.size(width = 40.dp, height = 32.dp), contentAlignment = Alignment.Center) {
                Icon(
                    painterResource(
                        when (tool) {
                            DocumentTool.LAYOUT -> R.drawable.ic_tool_adjust
                            DocumentTool.FILTERS -> R.drawable.ic_tool_filters
                            DocumentTool.CORRECTIONS -> R.drawable.ic_tool_corrections
                            DocumentTool.QUALITY -> R.drawable.ic_tool_quality
                            DocumentTool.PAGES -> R.drawable.ic_tool_pages
                            DocumentTool.MERGE -> R.drawable.ic_tool_merge
                            DocumentTool.SIGNATURES -> R.drawable.ic_tool_signature
                            DocumentTool.PROTECTION -> R.drawable.ic_tool_lock
                            DocumentTool.FORMS -> R.drawable.ic_tool_fill_page
                            DocumentTool.ANNOTATIONS -> R.drawable.ic_tool_adjust
                            DocumentTool.WATERMARK -> R.drawable.ic_tool_watermark
                            DocumentTool.REDACTION -> R.drawable.ic_tool_lock
                        },
                    ),
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                )
            }
            Text(
                text = stringResource(tool.titleRes()),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun DocumentSignatureControls(
    state: DocumentToolsUiState,
    onEvent: (DocumentToolsEvent) -> Unit,
    enabled: Boolean,
) {
    val draft = state.signatureDraft ?: return
    val editable = state.canEdit && enabled && !state.isRendering
    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            stringResource(R.string.tools_signature_place),
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            stringResource(R.string.tools_signature_hint),
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SignatureSlider(
            label = stringResource(R.string.tools_signature_x),
            value = draft.x,
            range = 0f..(1f - draft.width),
            enabled = editable,
            onChange = { onEvent(DocumentToolsEvent.MoveSignature(it, draft.y)) },
        )
        SignatureSlider(
            label = stringResource(R.string.tools_signature_y),
            value = draft.y,
            range = 0f..(1f - draft.height),
            enabled = editable,
            onChange = { onEvent(DocumentToolsEvent.MoveSignature(draft.x, it)) },
        )
        SignatureSlider(
            label = stringResource(R.string.tools_signature_size),
            value = draft.width,
            range = minOf(0.04f, 0.425f * draft.width / draft.height)..minOf(0.85f, 0.85f * draft.width / draft.height),
            enabled = editable,
            onChange = { onEvent(DocumentToolsEvent.ResizeSignature(it)) },
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(
                onClick = { onEvent(DocumentToolsEvent.CancelSignature) },
                enabled = editable,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.tools_cancel))
            }
            Button(
                onClick = { onEvent(DocumentToolsEvent.ConfirmSignature) },
                enabled = editable,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text(stringResource(R.string.tools_done))
            }
        }
    }
}

@Composable
private fun SignatureSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    onChange: (Float) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, modifier = Modifier.weight(0.4f), style = MaterialTheme.typography.labelMedium)
        Slider(
            value = value.coerceIn(range),
            onValueChange = onChange,
            valueRange = range,
            enabled = enabled,
            modifier = Modifier.weight(0.6f).semantics { contentDescription = label },
        )
    }
}


@Preview(name = "Ferramentas", widthDp = 390, showBackground = true)
@Preview(name = "Ferramentas escuro", widthDp = 390, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Ferramentas fonte ampliada", widthDp = 360, fontScale = 1.5f)
@Composable
private fun DocumentToolsBarPreview() {
    DocumentToolsBarPreviewContent(previewToolState().copy(tool = DocumentTool.PAGES))
}

@Preview(name = "Alterações pendentes", widthDp = 390)
@Preview(name = "Salvamento fonte ampliada", widthDp = 360, fontScale = 1.5f)
@Composable
private fun DocumentToolsUnsavedPreview() {
    DocumentToolsBarPreviewContent(previewToolState().copy(hasChanges = true))
}

@Preview(name = "Salvando", widthDp = 390)
@Composable
private fun DocumentToolsSavingPreview() {
    DocumentToolsBarPreviewContent(previewToolState().copy(hasChanges = true, isSaving = true))
}

@Preview(name = "Assinatura", widthDp = 390)
@Preview(name = "Assinatura fonte ampliada", widthDp = 360, fontScale = 1.5f)
@Composable
private fun DocumentSignatureControlsPreview() {
    DocumentToolsBarPreviewContent(
        previewToolState().copy(
            signatureDraft = PdfSignaturePlacement(id = "signature", imageUri = "signature.png"),
            signaturePageId = "page",
        ),
    )
}

@Composable
private fun DocumentToolsBarPreviewContent(state: DocumentToolsUiState) {
    ScaniqoTheme {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            DocumentToolsBar(state, onEvent = {}, enabled = true)
        }
    }
}

private fun previewToolState() = DocumentToolsUiState(
    workspace = PdfWorkspace(
        documentId = 1L,
        workspaceId = "preview",
        directoryUri = "preview",
        pages = listOf(PdfPageSpec(id = "page", sourceUri = "preview.pdf", sourcePageIndex = 0)),
    ),
    selectedPageId = "page",
)
