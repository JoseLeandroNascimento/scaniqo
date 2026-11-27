package com.joseleandro.scaniqo.ui.documentEditor

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joseleandro.scaniqo.core.localization.bidiArgument
import com.joseleandro.scaniqo.core.security.SecureWindow
import com.joseleandro.scaniqo.domain.model.DocumentModel
import com.joseleandro.scaniqo.domain.model.ReadableDocument
import com.joseleandro.scaniqo.ui.components.toast.AppToastHost
import com.joseleandro.scaniqo.ui.documentCommon.components.DocumentStatus
import com.joseleandro.scaniqo.ui.documentCommon.event.DocumentActionEvent
import com.joseleandro.scaniqo.ui.documentCommon.feedback.rememberDocumentToastState
import com.joseleandro.scaniqo.ui.documentEditor.components.DocumentSavedPage
import com.joseleandro.scaniqo.ui.documentEditor.components.DocumentViewerHeader
import com.joseleandro.scaniqo.ui.documentEditor.components.ToolProgress
import com.joseleandro.scaniqo.ui.documentEditor.state.DocumentReaderUiState
import com.joseleandro.scaniqo.ui.documentEditor.state.DocumentViewerUiState
import com.joseleandro.scaniqo.ui.theme.ScaniqoTheme
import kotlinx.coroutines.flow.first
import com.joseleandro.scaniqo.core.resources.R as CoreResourcesR
import com.joseleandro.scaniqo.feature.editor.R

@Composable
fun DocumentReaderScreen(
    documentId: Long,
    uiState: DocumentViewerUiState,
    readerState: DocumentReaderUiState,
    onDocumentAction: (DocumentActionEvent) -> Unit,
    onRetryLoad: () -> Unit,
    onNavigationBack: () -> Unit,
    onEdit: (Int?) -> Unit,
    onUnlock: () -> Unit,
    modifier: Modifier = Modifier,
    initialPageNumber: Int? = null,
) {
    val document = uiState.document?.takeIf { it.id == documentId }
    val sameSession = readerState.document?.let {
        it.id == documentId && it.pdfUri == document?.pdfUri &&
            it.protectionKeyId == document.protectionKeyId && it.sizeBytes == document.sizeBytes &&
            it.pageCount == document.pageCount
    } == true
    val readable = readerState.readable?.takeIf { sameSession && !readerState.isLocked }
    val locked = document?.isProtected == true && (!sameSession || readerState.isLocked)
    val ready = document != null && readable != null && !uiState.isLoading && !uiState.loadError
    val busy = uiState.actions.isBusy
    SecureWindow(enabled = document?.isProtected == true)
    val toastState = rememberDocumentToastState(uiState.actions.message) {
        onDocumentAction(DocumentActionEvent.DismissMessage)
    }
    val listState = rememberLazyListState()
    var initialPageApplied by rememberSaveable(documentId, initialPageNumber) {
        mutableStateOf(initialPageNumber == null)
    }
    LaunchedEffect(ready, readable?.pageCount, initialPageNumber) {
        if (!ready || initialPageApplied) return@LaunchedEffect
        val pageIndex = initialViewerPageIndex(initialPageNumber, readable.pageCount)
            ?: return@LaunchedEffect
        snapshotFlow { listState.layoutInfo.totalItemsCount }.first { it > pageIndex + 1 }
        listState.scrollToItem(pageIndex + 1)
        initialPageApplied = true
    }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surfaceDim,
        topBar = {
            DocumentReaderTopBar(
                document = document,
                showActions = ready,
                busy = busy,
                onDocumentAction = onDocumentAction,
                onNavigationBack = onNavigationBack,
            )
        },
        snackbarHost = { AppToastHost(toastState) },
        floatingActionButton = {
            if (ready) {
                FloatingActionButton(
                    onClick = {
                        if (!busy) onEdit(listState.firstVisibleItemIndex.coerceIn(1, readable.pageCount))
                    },
                    modifier = Modifier.semantics { if (busy) disabled() },
                    containerColor = if (busy) MaterialTheme.colorScheme.surfaceContainerHigh
                        else MaterialTheme.colorScheme.primaryContainer,
                    contentColor = if (busy) MaterialTheme.colorScheme.onSurface.copy(alpha = .38f)
                        else MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                    Icon(
                        painterResource(CoreResourcesR.drawable.edit_24px),
                        contentDescription = stringResource(R.string.reader_edit_document),
                    )
                }
            }
        },
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding),
        ) {
            val pageMaxWidth = if (maxWidth >= 600.dp) {
                ((maxHeight - 88.dp).coerceAtLeast(120.dp) * (595f / 842f)).coerceAtMost(840.dp)
            } else 840.dp
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp, top = 16.dp,
                    bottom = if (ready) 104.dp else 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                when {
                    uiState.isLoading -> item(key = "loading") {
                        ToolProgress(stringResource(R.string.reader_loading))
                    }
                    uiState.loadError -> item(key = "error") {
                        DocumentStatus(
                            title = stringResource(CoreResourcesR.string.documents_error_title),
                            description = stringResource(CoreResourcesR.string.documents_error_body),
                            actionLabel = stringResource(CoreResourcesR.string.documents_retry),
                            onAction = onRetryLoad,
                        )
                    }
                    document == null -> item(key = "missing") {
                        DocumentStatus(
                            title = stringResource(R.string.document_not_found_title),
                            description = stringResource(R.string.document_not_found_body),
                            actionLabel = stringResource(R.string.document_return),
                            onAction = onNavigationBack,
                        )
                    }
                    locked -> item(key = "locked") {
                        DocumentStatus(
                            title = stringResource(R.string.tools_locked_title),
                            description = stringResource(
                                if (readerState.recoveryRequired) R.string.tools_recovery_description
                                else R.string.tools_locked_description,
                            ),
                            actionLabel = stringResource(
                                if (readerState.recoveryRequired) R.string.tools_recovery_action
                                else CoreResourcesR.string.tools_unlock,
                            ),
                            onAction = onUnlock,
                        )
                    }
                    sameSession && readerState.loadError -> item(key = "read_error") {
                        DocumentStatus(
                            title = stringResource(R.string.reader_load_error),
                            description = stringResource(CoreResourcesR.string.documents_error_body),
                            actionLabel = stringResource(CoreResourcesR.string.documents_retry),
                            onAction = onRetryLoad,
                        )
                    }
                    ready -> {
                        item(key = "details") {
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                DocumentViewerHeader(
                                    document = document,
                                    tag = uiState.actions.tags.firstOrNull { it.id == document.tagId },
                                    pageCount = readable.pageCount,
                                    hasChanges = false,
                                    modifier = Modifier.widthIn(max = 840.dp),
                                )
                            }
                        }
                        items(readable.pageCount, key = { "read_page_$it" }) { pageIndex ->
                            DocumentSavedPage(
                                document = document.copy(pdfUri = readable.pdfUri, pageCount = readable.pageCount),
                                pageIndex = pageIndex,
                                maxPageWidth = pageMaxWidth,
                            )
                        }
                    }
                    else -> item(key = "preparing") {
                        ToolProgress(stringResource(R.string.reader_loading))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DocumentReaderTopBar(
    document: DocumentModel?,
    showActions: Boolean,
    busy: Boolean,
    onDocumentAction: (DocumentActionEvent) -> Unit,
    onNavigationBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember(document?.id, busy, showActions) { mutableStateOf(false) }
    TopAppBar(
        modifier = modifier,
        title = {
            Text(
                stringResource(R.string.viewer_title),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            scrolledContainerColor = MaterialTheme.colorScheme.surface,
        ),
        navigationIcon = {
            IconButton(onClick = onNavigationBack, enabled = !busy) {
                Icon(
                    painterResource(CoreResourcesR.drawable.arrow_back_24px),
                    stringResource(CoreResourcesR.string.document_back),
                )
            }
        },
        actions = {
            if (document != null && showActions) {
                Box {
                    FilledTonalIconButton(
                        onClick = { expanded = true },
                        enabled = !busy,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                    ) {
                        if (busy) {
                            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(
                                painterResource(CoreResourcesR.drawable.more_vert_24px),
                                stringResource(CoreResourcesR.string.document_actions, bidiArgument(document.name)),
                            )
                        }
                    }
                    DropdownMenu(expanded = expanded && !busy, onDismissRequest = { expanded = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(CoreResourcesR.string.document_download)) },
                            leadingIcon = { Icon(painterResource(CoreResourcesR.drawable.ic_document_download), null) },
                            onClick = {
                                expanded = false
                                onDocumentAction(DocumentActionEvent.Download(document))
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(CoreResourcesR.string.document_share)) },
                            leadingIcon = { Icon(painterResource(CoreResourcesR.drawable.ic_document_share), null) },
                            onClick = {
                                expanded = false
                                onDocumentAction(DocumentActionEvent.Share(document))
                            },
                        )
                    }
                }
            }
        },
    )
}

@Preview(name = "Leitura tablet paisagem", widthDp = 1280, heightDp = 800, locale = "pt-rBR")
@Preview(name = "Leitura janela dividida", widthDp = 600, heightDp = 800, locale = "pt-rBR")
@Preview(name = "Leitura do documento", widthDp = 390, heightDp = 850, locale = "pt-rBR")
@Preview(
    name = "Leitura escura", widthDp = 390, heightDp = 850,
    locale = "pt-rBR", uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Preview(
    name = "Leitura com fonte ampliada", widthDp = 360, heightDp = 800,
    locale = "pt-rBR", fontScale = 1.3f,
)
@Composable
private fun DocumentReaderPreview() {
    val document = DocumentModel(
        id = 1, name = "Contrato de aluguel", pdfUri = "preview.pdf",
        pageCount = 2, createdAt = 1781431200000L, sizeBytes = 248_000L,
    )
    ScaniqoTheme {
        DocumentReaderScreen(
            documentId = document.id,
            uiState = DocumentViewerUiState(document = document, isLoading = false),
            readerState = DocumentReaderUiState(
                document = document, readable = ReadableDocument(document.pdfUri, document.pageCount),
            ),
            onDocumentAction = {}, onRetryLoad = {}, onNavigationBack = {},
            onEdit = {}, onUnlock = {},
        )
    }
}
