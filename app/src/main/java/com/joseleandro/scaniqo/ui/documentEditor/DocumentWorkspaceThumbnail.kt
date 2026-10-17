package com.joseleandro.scaniqo.ui.documentEditor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier

/** Miniaturas usam a identidade da folha, mesmo depois de reordenar o documento. */
@Composable
internal fun DocumentWorkspaceThumbnail(
    state: DocumentToolsUiState,
    pageId: String,
    onEvent: (DocumentToolsEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val workspace = state.workspace ?: return
    val page = workspace.pages.firstOrNull { it.id == pageId } ?: return
    DisposableEffect(workspace.workspaceId, pageId) {
        onEvent(DocumentToolsEvent.PageVisibility(pageId, true))
        onDispose { onEvent(DocumentToolsEvent.PageVisibility(pageId, false)) }
    }
    val preview = state.pagePreviews[pageId]?.preview
    DocumentPageThumbnail(
        pdfUri = preview?.uri ?: page.sourceUri,
        pageIndex = preview?.pageIndex ?: page.sourcePageIndex,
        modifier = modifier,
    )
}
