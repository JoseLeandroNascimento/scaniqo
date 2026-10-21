package com.joseleandro.scaniqo.ui.trash

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joseleandro.scaniqo.R
import com.joseleandro.scaniqo.core.localization.bidiArgument
import com.joseleandro.scaniqo.domain.model.DocumentModel
import com.joseleandro.scaniqo.ui.components.DialogActions
import com.joseleandro.scaniqo.ui.theme.ScaniqoTheme
import android.text.format.DateFormat
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
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
    snackbarHost: @Composable () -> Unit = {},
) {
    Scaffold(
        snackbarHost = snackbarHost,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.trash_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !state.isBusy) {
                        Icon(painterResource(R.drawable.arrow_back_24px), stringResource(R.string.document_back))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(stringResource(R.string.trash_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (state.isBusy) item {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(stringResource(R.string.trash_working), style = MaterialTheme.typography.labelMedium)
            }
            when {
                state.isLoading -> item {
                    Row(Modifier.fillMaxWidth().padding(32.dp), horizontalArrangement = Arrangement.Center) {
                        CircularProgressIndicator()
                    }
                }
                state.loadError -> item {
                    Column(Modifier.fillMaxWidth().padding(vertical = 24.dp)) {
                        Text(stringResource(R.string.trash_load_error), color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = onRetry, enabled = !state.isBusy) {
                            Text(stringResource(R.string.documents_retry))
                        }
                    }
                }
                state.documents.isEmpty() -> item {
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(painterResource(R.drawable.delete_24px), null,
                            Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(stringResource(R.string.trash_empty_title), style = MaterialTheme.typography.titleLarge)
                        Text(stringResource(R.string.trash_empty_body), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                else -> items(state.documents, key = { it.id }) { document ->
                    TrashDocumentCard(document, enabled = !state.isBusy,
                        onRestore = { onRestore(document.id) },
                        onDelete = { onRequestDeletion(document.id) })
                }
            }
        }
    }
    state.pendingDeletion?.let { document ->
        AlertDialog(
            onDismissRequest = onDismissDeletion,
            icon = { Icon(painterResource(R.drawable.delete_24px), null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(stringResource(R.string.trash_delete_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.trash_delete_body, bidiArgument(document.name)))
                    if (state.deleteError) Text(stringResource(R.string.trash_delete_error),
                        color = MaterialTheme.colorScheme.error)
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
private fun TrashDocumentCard(document: DocumentModel, enabled: Boolean, onRestore: () -> Unit, onDelete: () -> Unit) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val date = remember(document.deletedAt, configuration) {
        document.deletedAt?.let { DateFormat.getMediumDateFormat(context).format(Date(it)) }
    }
    val restoreLabel = stringResource(R.string.trash_restore_document, bidiArgument(document.name))
    val deleteLabel = stringResource(R.string.trash_delete_document, bidiArgument(document.name))
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(document.name, style = MaterialTheme.typography.titleMedium.copy(textDirection = TextDirection.ContentOrLtr))
            Text(pluralStringResource(R.plurals.document_page_count, document.pageCount, document.pageCount),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (date != null) Text(stringResource(R.string.trash_deleted_on, bidiArgument(date)),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = onRestore, enabled = enabled,
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = restoreLabel }) {
                Text(stringResource(R.string.trash_restore))
            }
            TextButton(onClick = onDelete, enabled = enabled,
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = deleteLabel }) {
                Text(stringResource(R.string.trash_permanent_delete),
                    color = if (enabled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = .38f))
            }
        }
    }
}

@Preview(name = "Lixeira", widthDp = 390, heightDp = 780, locale = "pt-rBR")
@Preview(name = "Lixeira escura", widthDp = 390, heightDp = 780, locale = "pt-rBR", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Lixeira fonte ampliada", widthDp = 320, heightDp = 640, locale = "pt-rBR", fontScale = 1.5f)
@Composable
private fun TrashPreview() {
    ScaniqoTheme {
        TrashScreen(
            state = TrashUiState(isLoading = false, documents = listOf(
                DocumentModel(id = 1, name = "Contrato de aluguel", pdfUri = "", pageCount = 3,
                    createdAt = 1_790_000_000_000L, sizeBytes = 2048, deletedAt = 1_790_000_000_000L),
            )),
            onBack = {}, onRetry = {}, onRestore = {}, onRequestDeletion = {},
            onConfirmDeletion = {}, onDismissDeletion = {},
        )
    }
}
