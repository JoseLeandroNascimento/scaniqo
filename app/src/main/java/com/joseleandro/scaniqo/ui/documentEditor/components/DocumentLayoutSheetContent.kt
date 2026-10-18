package com.joseleandro.scaniqo.ui.documentEditor.components

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joseleandro.scaniqo.R
import com.joseleandro.scaniqo.domain.model.PageAlignment
import com.joseleandro.scaniqo.domain.model.PageScaleMode
import com.joseleandro.scaniqo.domain.model.PdfPageLayout
import com.joseleandro.scaniqo.domain.model.PdfPagePreview
import com.joseleandro.scaniqo.domain.model.PdfPageSpec
import com.joseleandro.scaniqo.domain.model.PdfWorkspace
import com.joseleandro.scaniqo.ui.documentEditor.event.DocumentToolsEvent
import com.joseleandro.scaniqo.ui.documentEditor.model.DocumentLayoutControl
import com.joseleandro.scaniqo.ui.documentEditor.model.DocumentPageScope
import com.joseleandro.scaniqo.ui.documentEditor.model.DocumentPageScopeMode
import com.joseleandro.scaniqo.ui.documentEditor.state.DocumentToolPreviewState
import com.joseleandro.scaniqo.ui.documentEditor.state.DocumentToolsUiState
import com.joseleandro.scaniqo.ui.documentEditor.util.DocumentPageScopeSaver
import com.joseleandro.scaniqo.ui.documentEditor.util.PdfPageLayoutSaver
import com.joseleandro.scaniqo.ui.documentEditor.util.documentLayoutRotation
import com.joseleandro.scaniqo.ui.documentEditor.util.withLayoutAdjustmentsFrom
import com.joseleandro.scaniqo.ui.theme.ScaniqoTheme
import kotlin.math.roundToInt

/** A folha mostra o rascunho real; somente Aplicar transfere a geometria aos alvos escolhidos. */
@Composable
internal fun DocumentLayoutSheetContent(
    state: DocumentToolsUiState,
    onEvent: (DocumentToolsEvent) -> Unit,
    modifier: Modifier = Modifier,
    onDraftChanged: (Boolean) -> Unit = {},
    initialControl: DocumentLayoutControl = DocumentLayoutControl.POSITION,
) {
    val workspace = state.workspace ?: return
    val initialPage = workspace.pages.getOrNull(state.selectedPageIndex) ?: return
    val anchorPageId = rememberSaveable(workspace.workspaceId) { initialPage.id }
    var previewPageId by rememberSaveable(workspace.workspaceId) { mutableStateOf(initialPage.id) }
    var draft by rememberSaveable(workspace.workspaceId, stateSaver = PdfPageLayoutSaver) {
        mutableStateOf(initialPage.layout)
    }
    var control by rememberSaveable { mutableStateOf(initialControl) }
    var scope by rememberSaveable(workspace.workspaceId, stateSaver = DocumentPageScopeSaver) {
        mutableStateOf(DocumentPageScope())
    }
    val targets = scope.targets(workspace.pages, anchorPageId)
    val page = workspace.pages.firstOrNull { it.id == previewPageId && it.id in targets }
        ?: workspace.pages.firstOrNull { it.id in targets } ?: initialPage
    val previewLayout = page.layout.withLayoutAdjustmentsFrom(draft)
    val currentPreview = state.layoutPreview.preview.takeIf { state.layoutPreview.pageId == page.id }
    val livePreview = state.layoutPreview.live.takeIf { it.pageId == page.id }
    val previewError = state.layoutPreview.error || livePreview?.error == true
    val changed = workspace.pages.any { it.id in targets && it.layout.withLayoutAdjustmentsFrom(draft) != it.layout }
    SideEffect { onDraftChanged(changed) }

    LaunchedEffect(page.id, previewLayout, state.canEdit) {
        if (state.canEdit) onEvent(DocumentToolsEvent.PreviewLayout(page.id, previewLayout))
    }
    DisposableEffect(Unit) {
        onDispose { onEvent(DocumentToolsEvent.ClearLayoutPreview) }
    }

    DocumentToolScreenLayout(
        modifier = modifier,
        header = {
            DocumentToolScopeMenu(
                pages = workspace.pages,
                currentPageId = anchorPageId,
                scope = scope,
                onScopeChange = { scope = it },
                enabled = state.canEdit,
            )
        },
        tools = {
            DocumentLayoutControl.entries.forEach { option ->
                DocumentPreviewToolButton(
                    label = stringResource(option.labelResource()),
                    iconRes = option.iconResource(),
                    selected = control == option,
                    enabled = state.canEdit,
                    onClick = { control = option },
                )
            }
        },
        controls = {
            when (control) {
                DocumentLayoutControl.POSITION -> LayoutPositionControls(draft, state.canEdit) { draft = it }
                DocumentLayoutControl.ROTATION -> LayoutRotationControls(draft, state.canEdit) { draft = it }
                DocumentLayoutControl.SIZE -> LayoutSizeControls(draft, state.canEdit) { draft = it }
            }
        },
        preview = {
            DocumentInteractivePagePreview(
                page = page,
                preview = currentPreview,
                fallback = state.pagePreviews[page.id]?.preview,
                isLoading = state.layoutPreview.isRendering || livePreview?.isRendering == true ||
                    (currentPreview == null && livePreview?.pixels == null && !previewError),
                hasError = previewError,
                onRetry = { onEvent(DocumentToolsEvent.PreviewLayout(page.id, previewLayout)) },
                enabled = state.canEdit,
                pageNumber = workspace.pages.indexOf(page) + 1,
                reserveHandleSpace = true,
                livePixels = livePreview?.pixels,
                liveLayout = previewLayout,
                workspaceId = workspace.workspaceId,
                pageCount = workspace.pages.size,
                showPageNumbers = workspace.showPageNumbers,
                overlay = {
                    DocumentLayoutHandles(
                        page.id, control, draft, state.canEdit,
                        onLayoutChange = { draft = it },
                    )
                },
            )
        },
        navigation = {
            if (targets.size > 1) DocumentToolTargetNavigation(
                pages = workspace.pages,
                targetIds = targets,
                previewPageId = page.id,
                onPageChange = { previewPageId = it },
                enabled = state.canEdit,
            )
        },
        actions = {
            DocumentToolActions(
                compact = true,
                enabled = state.canEdit && targets.isNotEmpty(),
                applyLabel = pluralStringResource(R.plurals.document_scope_apply_count, targets.size, targets.size),
                onReset = { draft = draft.withLayoutAdjustmentsFrom(PdfPageLayout()) },
                onApply = {
                    onEvent(DocumentToolsEvent.SelectPage(page.id))
                    onEvent(DocumentToolsEvent.ApplyLayout(
                        alignment = draft.alignment,
                        scaleMode = draft.scaleMode,
                        allPages = scope.mode == DocumentPageScopeMode.ALL,
                        pageIds = targets,
                        rotationQuarterTurns = draft.rotationQuarterTurns,
                        contentScale = draft.contentScale,
                    ))
                },
            )
        },
    )
}

@Composable
private fun LayoutPositionControls(
    layout: PdfPageLayout,
    enabled: Boolean,
    onChange: (PdfPageLayout) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        LayoutControlCaption(
            hint = stringResource(R.string.gesture_drag_handle),
            value = stringResource(layout.alignment.labelResource()),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .selectableGroup(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            listOf(
                PageAlignment.LEFT,
                PageAlignment.CENTER,
                PageAlignment.RIGHT,
                PageAlignment.TOP,
                PageAlignment.BOTTOM
            )
                .forEach { alignment ->
                    val selected = layout.alignment == alignment
                    val description = stringResource(alignment.labelResource())
                    Surface(
                        modifier = Modifier
                            .size(48.dp)
                            .selectable(
                                selected = selected,
                                enabled = enabled,
                                role = Role.RadioButton,
                                onClick = { onChange(layout.copy(alignment = alignment)) },
                            )
                            .semantics { contentDescription = description }
                            .padding(2.dp),
                        shape = MaterialTheme.shapes.small,
                        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                        contentColor = when {
                            !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = .38f)
                            selected -> MaterialTheme.colorScheme.onPrimaryContainer
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        border = if (selected) BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.primary.copy(alpha = .35f)
                        ) else null,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painterResource(alignment.iconResource()),
                                null,
                                Modifier.size(24.dp)
                            )
                        }
                    }
                }
        }
    }
}

@Composable
private fun LayoutRotationControls(
    layout: PdfPageLayout,
    enabled: Boolean,
    onChange: (PdfPageLayout) -> Unit
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = {
                onChange(
                    layout.copy(
                        rotationQuarterTurns = documentLayoutRotation(
                            layout.rotationQuarterTurns,
                            -1
                        )
                    )
                )
            },
            enabled = enabled,
        ) {
            Icon(
                painterResource(R.drawable.ic_layout_rotate_left),
                stringResource(R.string.layout_interactive_rotate_left)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                stringResource(R.string.layout_interactive_rotate),
                style = MaterialTheme.typography.labelSmall
            )
            Text(
                stringResource(R.string.tools_rotation_angle, layout.rotationQuarterTurns * 90),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        IconButton(
            onClick = {
                onChange(
                    layout.copy(
                        rotationQuarterTurns = documentLayoutRotation(
                            layout.rotationQuarterTurns,
                            1
                        )
                    )
                )
            },
            enabled = enabled,
        ) {
            Icon(
                painterResource(R.drawable.ic_layout_rotate_right),
                stringResource(R.string.layout_interactive_rotate_right)
            )
        }
    }
}

@Composable
private fun LayoutSizeControls(
    layout: PdfPageLayout,
    enabled: Boolean,
    onChange: (PdfPageLayout) -> Unit
) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PageScaleMode.entries.forEach { mode ->
                FilterChip(
                    selected = layout.scaleMode == mode,
                    onClick = { onChange(layout.copy(scaleMode = mode)) },
                    enabled = enabled,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp),
                    label = {
                        Text(
                            stringResource(if (mode == PageScaleMode.FIT) R.string.tools_fit_compact else R.string.tools_fill_compact),
                            textAlign = TextAlign.Center,
                        )
                    },
                )
            }
        }
        val description = stringResource(R.string.tools_content_size)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                stringResource(
                    R.string.tools_content_size_value,
                    (layout.contentScale * 100).roundToInt()
                ),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Slider(
                value = layout.contentScale,
                onValueChange = { onChange(layout.copy(contentScale = it)) },
                valueRange = .25f..2f,
                enabled = enabled,
                modifier = Modifier
                    .weight(1f)
                    .semantics { contentDescription = description },
            )
        }
    }
}

@Composable
private fun LayoutControlCaption(hint: String, value: String) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            hint,
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

private fun DocumentLayoutControl.labelResource() = when (this) {
    DocumentLayoutControl.POSITION -> R.string.layout_interactive_position
    DocumentLayoutControl.ROTATION -> R.string.layout_interactive_rotate
    DocumentLayoutControl.SIZE -> R.string.tools_size_compact
}

private fun DocumentLayoutControl.iconResource() = when (this) {
    DocumentLayoutControl.POSITION -> R.drawable.ic_layout_position
    DocumentLayoutControl.ROTATION -> R.drawable.ic_layout_rotate_right
    DocumentLayoutControl.SIZE -> R.drawable.ic_layout_size
}

private fun PageAlignment.labelResource() = when (this) {
    PageAlignment.CENTER -> R.string.tools_align_center
    PageAlignment.TOP -> R.string.tools_align_top
    PageAlignment.BOTTOM -> R.string.tools_align_bottom
    PageAlignment.LEFT -> R.string.tools_align_left
    PageAlignment.RIGHT -> R.string.tools_align_right
}

private fun PageAlignment.iconResource() = when (this) {
    PageAlignment.CENTER -> R.drawable.ic_layout_align_center
    PageAlignment.TOP -> R.drawable.ic_layout_align_top
    PageAlignment.BOTTOM -> R.drawable.ic_layout_align_bottom
    PageAlignment.LEFT -> R.drawable.ic_layout_align_left
    PageAlignment.RIGHT -> R.drawable.ic_layout_align_right
}

@Preview(name = "Ajustar página", widthDp = 390, heightDp = 760, showBackground = true)
@Preview(
    name = "Ajustar página escuro",
    widthDp = 390,
    heightDp = 760,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Preview(name = "Ajustar fonte ampliada", widthDp = 360, heightDp = 760, fontScale = 1.3f)
@Composable
private fun DocumentLayoutSheetPreview() {
    DocumentLayoutPreviewContent(DocumentLayoutControl.POSITION)
}

@Preview(name = "Ajustar rotação", widthDp = 390, heightDp = 760, showBackground = true)
@Composable
private fun DocumentLayoutRotationPreview() {
    DocumentLayoutPreviewContent(DocumentLayoutControl.ROTATION)
}

@Preview(name = "Ajustar tamanho fonte ampliada", widthDp = 360, heightDp = 760, fontScale = 1.3f)
@Composable
private fun DocumentLayoutSizePreview() {
    DocumentLayoutPreviewContent(DocumentLayoutControl.SIZE)
}

@Composable
private fun DocumentLayoutPreviewContent(control: DocumentLayoutControl) {
    ScaniqoTheme {
        Surface {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    stringResource(R.string.tools_layout),
                    style = MaterialTheme.typography.titleLarge
                )
                DocumentLayoutSheetContent(
                    DocumentToolsUiState(
                        workspace = PdfWorkspace(
                            1, "preview", "preview",
                            (1..8).map { PdfPageSpec("page-$it", "preview", it - 1) },
                        ),
                        selectedPageId = "page-1",
                        layoutPreview = DocumentToolPreviewState(
                            preview = PdfPagePreview("preview", 0),
                            pageId = "page-1",
                        ),
                    ),
                    {},
                    initialControl = control,
                )
            }
        }
    }
}
