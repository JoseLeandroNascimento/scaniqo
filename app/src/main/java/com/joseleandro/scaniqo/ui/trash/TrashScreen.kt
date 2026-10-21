package com.joseleandro.scaniqo.ui.trash

import android.content.res.Configuration
import android.text.format.DateFormat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.joseleandro.scaniqo.R
import com.joseleandro.scaniqo.core.localization.bidiArgument
import com.joseleandro.scaniqo.core.utils.documentFileSize
import com.joseleandro.scaniqo.domain.model.DocumentModel
import com.joseleandro.scaniqo.ui.components.DialogActions
import com.joseleandro.scaniqo.ui.documentCommon.components.DocumentThumbnail
import com.joseleandro.scaniqo.ui.theme.ScaniqoTheme
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TrashScreen(
    state: TrashUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onRestore: (Long) -> Unit,
    onRequestDeletion: (Long) -> Unit,
    onConfirmDeletion: () -> Unit,
    onDismissDeletion: () -> Unit,
    onRequestClearAll: () -> Unit,
    onConfirmClearAll: () -> Unit,
    onDismissClearAll: () -> Unit,
    snackbarHost: @Composable () -> Unit = {},
) {
    var showHelp by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        snackbarHost = snackbarHost,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.trash_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !state.isBusy) {
                        Icon(
                            painterResource(R.drawable.arrow_back_24px),
                            contentDescription = stringResource(R.string.document_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showHelp = true }) {
                        Icon(
                            painterResource(R.drawable.ic_toast_info),
                            contentDescription = stringResource(R.string.trash_info),
                        )
                    }
                },
            )
        },
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding),
            contentAlignment = Alignment.TopCenter,
        ) {
            val contentWidth = minOf(maxWidth, 720.dp)
            val availableHeight = maxHeight
            when {
                state.isLoading && state.documents.isEmpty() -> TrashCenteredStatus(
                    modifier = Modifier.width(contentWidth).fillMaxHeight(),
                    minHeight = availableHeight,
                    title = stringResource(R.string.trash_loading),
                    visual = { CircularProgressIndicator() },
                )
                state.loadError && state.documents.isEmpty() -> TrashCenteredStatus(
                    modifier = Modifier.width(contentWidth).fillMaxHeight(),
                    minHeight = availableHeight,
                    title = stringResource(R.string.trash_load_error),
                    visual = {
                        Surface(
                            shape = MaterialTheme.shapes.extraLarge,
                            color = MaterialTheme.colorScheme.errorContainer,
                        ) {
                            Icon(
                                painterResource(R.drawable.ic_toast_error),
                                contentDescription = null,
                                modifier = Modifier.padding(24.dp).size(40.dp),
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                    },
                    action = {
                        Button(onClick = onRetry, enabled = !state.isBusy) {
                            Text(stringResource(R.string.documents_retry))
                        }
                    },
                )
                state.documents.isEmpty() -> TrashCenteredStatus(
                    modifier = Modifier.width(contentWidth).fillMaxHeight(),
                    minHeight = availableHeight,
                    title = stringResource(R.string.trash_empty_title),
                    body = stringResource(R.string.trash_empty_body),
                    visual = {
                        Surface(
                            shape = MaterialTheme.shapes.extraLarge,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                        ) {
                            Image(
                                painterResource(R.drawable.trash_empty_illustration),
                                contentDescription = null,
                                modifier = Modifier.padding(20.dp)
                                    .width(192.dp).aspectRatio(240f / 184f),
                            )
                        }
                    },
                )
                else -> LazyColumn(
                    modifier = Modifier.width(contentWidth).fillMaxHeight(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                pluralStringResource(
                                    R.plurals.trash_document_count,
                                    state.documents.size,
                                    state.documents.size,
                                ),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            TextButton(
                                onClick = onRequestClearAll,
                                enabled = !state.isBusy && !state.loadError &&
                                    !state.pendingClearAll && state.pendingDeletion == null,
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error,
                                ),
                            ) {
                                Icon(
                                    painterResource(R.drawable.delete_24px),
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.trash_clear_all))
                            }
                        }
                    }
                    if (state.isLoading) item {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                    if (state.loadError) item {
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.errorContainer,
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Text(
                                    stringResource(R.string.trash_load_error),
                                    modifier = Modifier.weight(1f),
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                TextButton(onClick = onRetry, enabled = !state.isBusy) {
                                    Text(stringResource(R.string.documents_retry))
                                }
                            }
                        }
                    }
                    items(state.documents, key = { it.id }) { document ->
                        TrashDocumentCard(
                            document = document,
                            enabled = !state.isBusy,
                            restoring = state.busyDocumentId == document.id &&
                                state.pendingDeletion == null,
                            onRestore = { onRestore(document.id) },
                            onDelete = { onRequestDeletion(document.id) },
                            modifier = Modifier.animateItem(
                                fadeInSpec = tween(180),
                                placementSpec = tween(220),
                                fadeOutSpec = tween(180),
                            ),
                        )
                    }
                }
            }
        }
    }

    if (showHelp) {
        AlertDialog(
            onDismissRequest = { showHelp = false },
            icon = {
                Icon(
                    painterResource(R.drawable.ic_toast_info),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            },
            title = { Text(stringResource(R.string.trash_help_title)) },
            text = { Text(stringResource(R.string.trash_description)) },
            confirmButton = {
                TextButton(onClick = { showHelp = false }) {
                    Text(stringResource(R.string.trash_help_close))
                }
            },
        )
    }

    if (state.pendingClearAll) {
        AlertDialog(
            onDismissRequest = onDismissClearAll,
            icon = {
                Icon(
                    painterResource(R.drawable.delete_24px),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
            },
            title = { Text(stringResource(R.string.trash_clear_all_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        pluralStringResource(
                            R.plurals.trash_clear_all_body,
                            state.pendingClearAllCount,
                            state.pendingClearAllCount,
                        )
                    )
                    if (state.clearAllError) {
                        Text(
                            stringResource(R.string.trash_clear_all_error),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    if (state.isClearingAll) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                }
            },
            confirmButton = {
                DialogActions(
                    confirmText = stringResource(
                        if (state.isClearingAll) R.string.trash_clearing else R.string.trash_clear_all
                    ),
                    onConfirm = onConfirmClearAll,
                    dismissText = stringResource(R.string.document_cancel),
                    onDismiss = onDismissClearAll,
                    confirmEnabled = !state.isBusy,
                    dismissEnabled = !state.isBusy,
                    confirming = state.isClearingAll,
                    destructive = true,
                )
            },
        )
    }

    state.pendingDeletion?.let { document ->
        AlertDialog(
            onDismissRequest = onDismissDeletion,
            icon = {
                Icon(
                    painterResource(R.drawable.delete_24px),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
            },
            title = { Text(stringResource(R.string.trash_delete_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.trash_delete_body, bidiArgument(document.name)))
                    if (state.deleteError) {
                        Text(
                            stringResource(R.string.trash_delete_error),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
            confirmButton = {
                DialogActions(
                    confirmText = stringResource(R.string.trash_permanent_delete),
                    onConfirm = onConfirmDeletion,
                    dismissText = stringResource(R.string.document_cancel),
                    onDismiss = onDismissDeletion,
                    confirmEnabled = !state.isBusy,
                    dismissEnabled = !state.isBusy,
                    confirming = state.isBusy,
                    destructive = true,
                )
            },
        )
    }
}

@Composable
private fun TrashCenteredStatus(
    modifier: Modifier,
    minHeight: Dp,
    title: String,
    visual: @Composable () -> Unit,
    body: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .heightIn(min = minHeight)
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        visual()
        Spacer(Modifier.size(24.dp))
        Text(
            title,
            modifier = Modifier.widthIn(max = 520.dp),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        if (body != null) {
            Spacer(Modifier.size(10.dp))
            Text(
                body,
                modifier = Modifier.widthIn(max = 520.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (action != null) {
            Spacer(Modifier.size(20.dp))
            action()
        }
    }
}

@Composable
private fun TrashDocumentCard(
    document: DocumentModel,
    enabled: Boolean,
    restoring: Boolean,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val date = remember(document.deletedAt, configuration) {
        document.deletedAt?.let { DateFormat.getMediumDateFormat(context).format(Date(it)) }
    }
    val pages = pluralStringResource(
        R.plurals.document_page_count,
        document.pageCount,
        document.pageCount,
    )
    val size = documentFileSize(document)
    val restoreLabel = stringResource(R.string.trash_restore_document, bidiArgument(document.name))
    val deleteLabel = stringResource(R.string.trash_delete_document, bidiArgument(document.name))
    val stackedActions = LocalDensity.current.fontScale >= 1.3f

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Surface(
                    modifier = Modifier.width(72.dp).aspectRatio(595f / 842f),
                    shape = MaterialTheme.shapes.small,
                    shadowElevation = 2.dp,
                ) {
                    DocumentThumbnail(document = document, modifier = Modifier.fillMaxSize())
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        document.name,
                        style = MaterialTheme.typography.titleMedium.copy(
                            textDirection = TextDirection.ContentOrLtr
                        ),
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        stringResource(
                            R.string.trash_document_metadata,
                            bidiArgument(pages),
                            bidiArgument(size),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (date != null) {
                        Text(
                            stringResource(R.string.trash_deleted_on, bidiArgument(date)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            BoxWithConstraints {
                val stackButtons = stackedActions || maxWidth < 300.dp
                if (stackButtons) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        TrashRestoreButton(
                            enabled = enabled,
                            restoring = restoring,
                            label = restoreLabel,
                            onClick = onRestore,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        TrashDeleteButton(
                            enabled = enabled,
                            label = deleteLabel,
                            onClick = onDelete,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TrashRestoreButton(
                            enabled = enabled,
                            restoring = restoring,
                            label = restoreLabel,
                            onClick = onRestore,
                            modifier = Modifier.weight(1f),
                        )
                        TrashDeleteButton(
                            enabled = enabled,
                            label = deleteLabel,
                            onClick = onDelete,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TrashRestoreButton(
    enabled: Boolean,
    restoring: Boolean,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.semantics {
            contentDescription = label
        },
    ) {
        if (restoring) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        } else {
            Icon(painterResource(R.drawable.ic_document_undo), contentDescription = null)
        }
        Spacer(Modifier.width(8.dp))
        Text(if (restoring) stringResource(R.string.trash_restoring) else stringResource(R.string.trash_restore))
    }
}

@Composable
private fun TrashDeleteButton(
    enabled: Boolean,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.semantics { contentDescription = label },
    ) {
        val color = if (enabled) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        Icon(
            painterResource(R.drawable.delete_24px),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = color,
        )
        Spacer(Modifier.width(6.dp))
        Text(stringResource(R.string.trash_delete_short), color = color)
    }
}

@Preview(name = "Lixeira", widthDp = 390, heightDp = 780, locale = "pt-rBR")
@Preview(name = "Lixeira escura", widthDp = 390, heightDp = 780, locale = "pt-rBR", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Lixeira fonte ampliada", widthDp = 320, heightDp = 640, locale = "pt-rBR", fontScale = 1.5f)
@Preview(name = "Lixeira árabe", widthDp = 390, heightDp = 780, locale = "ar")
@Composable
private fun TrashPreview() {
    ScaniqoTheme {
        TrashScreen(
            state = TrashUiState(
                isLoading = false,
                documents = listOf(
                    DocumentModel(
                        id = 1,
                        name = "Contrato de aluguel com um nome extenso",
                        pdfUri = "",
                        pageCount = 3,
                        createdAt = 1_790_000_000_000L,
                        sizeBytes = 2_400_000,
                        deletedAt = 1_790_000_000_000L,
                    ),
                ),
            ),
            onBack = {},
            onRetry = {},
            onRestore = {},
            onRequestDeletion = {},
            onConfirmDeletion = {},
            onDismissDeletion = {},
            onRequestClearAll = {},
            onConfirmClearAll = {},
            onDismissClearAll = {},
        )
    }
}

@Preview(name = "Lixeira vazia", widthDp = 390, heightDp = 780, locale = "pt-rBR")
@Preview(name = "Lixeira vazia escura", widthDp = 390, heightDp = 780, locale = "pt-rBR", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Lixeira vazia com fonte ampliada", widthDp = 320, heightDp = 640, locale = "pt-rBR", fontScale = 1.5f)
@Preview(name = "Lixeira vazia em árabe", widthDp = 390, heightDp = 780, locale = "ar")
@Composable
private fun TrashEmptyPreview() {
    ScaniqoTheme {
        TrashScreen(
            state = TrashUiState(isLoading = false),
            onBack = {},
            onRetry = {},
            onRestore = {},
            onRequestDeletion = {},
            onConfirmDeletion = {},
            onDismissDeletion = {},
            onRequestClearAll = {},
            onConfirmClearAll = {},
            onDismissClearAll = {},
        )
    }
}

@Preview(name = "Esvaziar lixeira", widthDp = 390, heightDp = 780, locale = "pt-rBR")
@Preview(name = "Esvaziar lixeira em árabe", widthDp = 390, heightDp = 780, locale = "ar")
@Composable
private fun TrashClearAllPreview() {
    ScaniqoTheme {
        TrashScreen(
            state = TrashUiState(
                isLoading = false,
                documents = listOf(
                    DocumentModel(
                        id = 1,
                        name = "Contrato de aluguel",
                        pdfUri = "",
                        pageCount = 3,
                        createdAt = 1_790_000_000_000L,
                        sizeBytes = 2_400_000L,
                        deletedAt = 1_790_000_000_000L,
                    ),
                ),
                pendingClearAll = true,
                pendingClearAllCount = 1,
            ),
            onBack = {},
            onRetry = {},
            onRestore = {},
            onRequestDeletion = {},
            onConfirmDeletion = {},
            onDismissDeletion = {},
            onRequestClearAll = {},
            onConfirmClearAll = {},
            onDismissClearAll = {},
        )
    }
}
