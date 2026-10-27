package com.joseleandro.scaniqo.ui.documentEditor.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.joseleandro.scaniqo.R
import com.joseleandro.scaniqo.core.localization.bidiArgument
import com.joseleandro.scaniqo.domain.model.DocumentModel
import com.joseleandro.scaniqo.ui.documentCommon.event.DocumentActionEvent
import com.joseleandro.scaniqo.ui.documentEditor.event.DocumentToolsEvent
import com.joseleandro.scaniqo.ui.documentEditor.state.DocumentToolsUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DocumentViewerTopBar(
    document: DocumentModel?,
    showDocumentActions: Boolean,
    toolsState: DocumentToolsUiState,
    onDocumentAction: (DocumentActionEvent) -> Unit,
    onToolsEvent: (DocumentToolsEvent) -> Unit,
    documentBusy: Boolean,
    isBusy: Boolean,
) {
    var showActions by remember(document?.id, isBusy, showDocumentActions) { mutableStateOf(false) }
    val processingDescription = stringResource(R.string.document_processing)
    TopAppBar(
        title = {
            Text(
                stringResource(R.string.viewer_title),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        navigationIcon = {
            IconButton(
                onClick = { onToolsEvent(DocumentToolsEvent.RequestBack) },
                enabled = !isBusy,
            ) {
                Icon(
                    painterResource(R.drawable.arrow_back_24px),
                    stringResource(R.string.document_back)
                )
            }
        },
        actions = {
            if (document != null && showDocumentActions) {
                if (toolsState.signatureDraft == null && (toolsState.canUndo || toolsState.canRedo)) {
                    IconButton(
                        onClick = { onToolsEvent(DocumentToolsEvent.Undo) },
                        enabled = toolsState.canEdit && toolsState.canUndo && !documentBusy,
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_document_undo),
                            contentDescription = stringResource(R.string.viewer_undo_description),
                        )
                    }
                    IconButton(
                        onClick = { onToolsEvent(DocumentToolsEvent.Redo) },
                        enabled = toolsState.canEdit && toolsState.canRedo && !documentBusy,
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_document_redo),
                            contentDescription = stringResource(R.string.viewer_redo_description),
                        )
                    }
                }
                Box {
                    FilledTonalIconButton(
                        onClick = { showActions = true },
                        enabled = !isBusy,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                    ) {
                        if (isBusy) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .size(24.dp)
                                    .semantics {
                                        contentDescription = processingDescription
                                    },
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(
                                painterResource(R.drawable.more_vert_24px),
                                contentDescription = stringResource(
                                    R.string.document_actions,
                                    bidiArgument(document.name)
                                ),
                            )
                        }
                    }
                    DropdownMenu(
                        expanded = showActions && !isBusy,
                        onDismissRequest = { showActions = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.document_metadata_edit)) },
                            leadingIcon = {
                                Icon(
                                    painterResource(R.drawable.edit_24px),
                                    null
                                )
                            },
                            enabled = !isBusy && toolsState.signatureDraft == null,
                            onClick = {
                                showActions = false
                                onDocumentAction(DocumentActionEvent.EditMetadata(document))
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.document_download)) },
                            leadingIcon = {
                                Icon(
                                    painterResource(R.drawable.ic_document_download),
                                    null
                                )
                            },
                            enabled = !isBusy && toolsState.canExport,
                            onClick = {
                                showActions = false
                                onDocumentAction(DocumentActionEvent.Download(document))
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.document_share)) },
                            leadingIcon = {
                                Icon(
                                    painterResource(R.drawable.ic_document_share),
                                    null
                                )
                            },
                            enabled = !isBusy && toolsState.canExport,
                            onClick = {
                                showActions = false
                                onDocumentAction(DocumentActionEvent.Share(document))
                            },
                        )
                        if (toolsState.hasChanges || toolsState.signatureDraft != null) {
                            Text(
                                stringResource(R.string.tools_export_save_first),
                                modifier = Modifier
                                    .widthIn(max = 260.dp)
                                    .padding(16.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
    )
}
