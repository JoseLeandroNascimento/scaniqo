package com.joseleandro.scaniqo.ui.documentMerge

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joseleandro.scaniqo.R
import com.joseleandro.scaniqo.domain.model.DocumentModel
import com.joseleandro.scaniqo.ui.documentCommon.components.DocumentMergeIllustration
import com.joseleandro.scaniqo.ui.documentCommon.components.DocumentMergeSelectionContent
import com.joseleandro.scaniqo.ui.documentCommon.model.DocumentMergeItem
import com.joseleandro.scaniqo.ui.documentCommon.model.toMergeItem
import com.joseleandro.scaniqo.ui.documentCommon.state.DocumentMergeSelectionState
import com.joseleandro.scaniqo.ui.documentMerge.model.DocumentMergeTarget
import com.joseleandro.scaniqo.ui.theme.ScaniqoTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DocumentMergeScreen(
    documents: List<DocumentModel>,
    selection: DocumentMergeSelectionState,
    target: DocumentMergeTarget?,
    savedDocumentId: Long? = null,
    enabled: Boolean,
    canNavigateBack: Boolean,
    canConfirm: Boolean,
    isLoading: Boolean,
    loadError: Boolean,
    error: String?,
    onRetry: () -> Unit,
    snackbarHost: @Composable () -> Unit = {},
    onUnlock: (() -> Unit)? = null,
    onBack: () -> Unit,
    onOpenDocument: (Long) -> Unit = {},
    onPickFiles: () -> Unit,
    onConfirm: (List<DocumentMergeItem>) -> Unit,
    actionShowsVideo: Boolean = false,
) {
    var reviewing by rememberSaveable(target?.documentId) { mutableStateOf(target != null) }
    val back = {
        if (savedDocumentId == null && target == null && reviewing) reviewing = false else onBack()
    }
    BackHandler(enabled = savedDocumentId == null && target == null && reviewing && canNavigateBack) {
        reviewing = false
    }
    Scaffold(
        snackbarHost = snackbarHost,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.merge_selection_title), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = back, enabled = canNavigateBack) {
                        Icon(painterResource(R.drawable.arrow_back_24px), stringResource(R.string.merge_screen_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()
                .padding(horizontal = 20.dp).padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (savedDocumentId == null && isLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (savedDocumentId == null && loadError) {
                Row(Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(R.string.merge_screen_library_error),
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    TextButton(onClick = onRetry) { Text(stringResource(R.string.documents_retry)) }
                }
            }
            if (savedDocumentId == null && error != null) Text(error, color = MaterialTheme.colorScheme.error)
            if (savedDocumentId == null && onUnlock != null) {
                OutlinedButton(onClick = onUnlock, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.tools_unlock))
                }
            }
            AnimatedContent(
                targetState = if (savedDocumentId != null) 2 else if (reviewing) 1 else 0,
                modifier = Modifier.weight(1f),
                transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                label = "Merge journey",
            ) { stage ->
                when (stage) {
                    2 -> DocumentMergeCompletedContent(
                        selectedCount = selection.items.size,
                        onOpen = { savedDocumentId?.let(onOpenDocument) },
                        onBack = onBack,
                    )
                    0 -> DocumentMergeSourceStep(
                        documents = documents,
                        selection = selection,
                        enabled = enabled,
                        canContinue = canConfirm,
                        isLoading = isLoading,
                        loadError = loadError,
                        onRetry = onRetry,
                        onPickFiles = onPickFiles,
                        onContinue = { reviewing = true },
                    )
                    else -> DocumentMergeSelectionContent(
                        documents = documents,
                        selection = selection,
                        currentDocumentId = target?.documentId,
                        enabled = enabled,
                        minimumSelection = 2,
                        canConfirm = canConfirm,
                        actionShowsVideo = actionShowsVideo,
                        footerLabel = when {
                            actionShowsVideo -> stringResource(R.string.tool_ad_reward_hint)
                            target == null -> stringResource(R.string.merge_result_new_document)
                            else -> null
                        },
                        modifier = Modifier.fillMaxSize(),
                        onPickFiles = onPickFiles,
                        onConfirm = onConfirm,
                    )
                }
            }
        }
    }
}

@Composable
private fun DocumentMergeCompletedContent(
    selectedCount: Int,
    onOpen: () -> Unit,
    onBack: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (maxWidth > 600.dp && maxHeight < 420.dp) {
            Row(
                Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                DocumentMergeCompletedCard(selectedCount, compact = true, modifier = Modifier.weight(1f))
                DocumentMergeCompletedActions(
                    onOpen = onOpen,
                    onBack = onBack,
                    modifier = Modifier.width(200.dp),
                )
            }
        } else {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = maxHeight),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                DocumentMergeCompletedCard(selectedCount, compact = false, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(20.dp))
                DocumentMergeCompletedActions(onOpen, onBack, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun DocumentMergeCompletedCard(
    selectedCount: Int,
    compact: Boolean,
    modifier: Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f),
    ) {
        if (compact) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                DocumentMergeIllustration(
                    selectedCount = 2,
                    isWorking = false,
                    modifier = Modifier.width(140.dp).height(120.dp),
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    DocumentMergeCompletedCheck(compact = true)
                    DocumentMergeCompletedLabel(selectedCount)
                }
            }
        } else {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                DocumentMergeIllustration(
                    selectedCount = 2,
                    isWorking = false,
                    modifier = Modifier.fillMaxWidth().height(142.dp),
                )
                DocumentMergeCompletedCheck(compact = false)
                DocumentMergeCompletedLabel(selectedCount)
            }
        }
    }
}

@Composable
private fun DocumentMergeCompletedCheck(compact: Boolean) {
    Box(
        Modifier.size(if (compact) 36.dp else 48.dp)
            .background(MaterialTheme.colorScheme.primary, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_check_24),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

@Composable
private fun DocumentMergeCompletedLabel(selectedCount: Int) {
    Text(
        text = stringResource(R.string.document_saved),
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.SemiBold,
    )
    if (selectedCount >= 2) {
        Text(
            text = stringResource(R.string.merge_motion_count, selectedCount),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DocumentMergeCompletedActions(
    onOpen: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onOpen, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            Text(stringResource(R.string.runtime_open_document))
        }
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            Text(stringResource(R.string.merge_screen_back))
        }
    }
}

@Preview(name = "Mesclar PDFs", widthDp = 390, heightDp = 780, showBackground = true, locale = "pt-rBR")
@Preview(name = "Mesclar PDFs — escuro", widthDp = 390, heightDp = 780, locale = "pt-rBR",
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DocumentMergeScreenPreview() {
    DocumentMergePreviewContent()
}

@Preview(name = "Inserir PDFs — fonte ampliada", widthDp = 360, heightDp = 780, fontScale = 1.3f, showBackground = true, locale = "pt-rBR")
@Preview(name = "Inserir PDFs — paisagem", widthDp = 720, heightDp = 360, showBackground = true, locale = "pt-rBR")
@Composable
private fun DocumentMergeEditorScreenPreview() {
    DocumentMergePreviewContent(DocumentMergeTarget(documentId = 4, pageCount = 8, selectedPageIndex = 2))
}

@Composable
private fun DocumentMergePreviewContent(
    target: DocumentMergeTarget? = null,
    savedDocumentId: Long? = null,
) {
    val documents = listOf(
        DocumentModel(1, "Contrato", "file:///contract.pdf", 3, 0, 0),
        DocumentModel(2, "Comprovantes", "file:///receipts.pdf", 5, 0, 0),
        DocumentModel(3, "Notas", "file:///notes.pdf", 2, 0, 0),
    )
    val current = target?.let {
        DocumentMergeItem("file:///current.pdf", "Documento em edição", it.documentId, it.pageCount, isCurrentDocument = true)
    }
    val selection = remember {
        DocumentMergeSelectionState(listOfNotNull(current) + documents.take(2).map(DocumentModel::toMergeItem))
    }
    ScaniqoTheme {
        DocumentMergeScreen(
            documents, selection, target,
            savedDocumentId = savedDocumentId,
            enabled = true, canNavigateBack = true, canConfirm = true,
            isLoading = false, loadError = false, error = null,
            onRetry = {},
            onBack = {}, onPickFiles = {}, onConfirm = {},
        )
    }
}

@Preview(name = "Mesclar — PDF salvo", widthDp = 390, heightDp = 780, showBackground = true, locale = "pt-rBR")
@Preview(name = "Mesclar — PDF salvo em paisagem", widthDp = 720, heightDp = 360, showBackground = true, locale = "pt-rBR")
@Composable
private fun DocumentMergeSavedScreenPreview() {
    DocumentMergePreviewContent(savedDocumentId = 9L)
}

@Preview(name = "Mesclar — seleção vazia", widthDp = 390, heightDp = 780, showBackground = true, locale = "pt-rBR")
@Composable
private fun DocumentMergeEmptyScreenPreview() {
    val selection = remember { DocumentMergeSelectionState() }
    ScaniqoTheme {
        DocumentMergeScreen(
            documents = emptyList(), selection = selection, target = null,
            enabled = true, canNavigateBack = true, canConfirm = true,
            isLoading = false, loadError = false, error = null,
            onRetry = {}, onBack = {}, onPickFiles = {}, onConfirm = {},
        )
    }
}
