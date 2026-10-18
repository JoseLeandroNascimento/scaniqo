package com.joseleandro.scaniqo.ui.documents.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.joseleandro.scaniqo.R
import com.joseleandro.scaniqo.core.utils.documentDate
import com.joseleandro.scaniqo.core.utils.documentFileSize
import com.joseleandro.scaniqo.domain.model.DocumentModel
import com.joseleandro.scaniqo.domain.model.TagModel
import com.joseleandro.scaniqo.ui.documentCommon.components.DocumentTagLabel
import com.joseleandro.scaniqo.ui.documentCommon.components.DocumentThumbnail
import com.joseleandro.scaniqo.ui.documentCommon.event.DocumentActionEvent

@Composable
internal fun DocumentCard(
    document: DocumentModel,
    tag: TagModel?,
    isBusy: Boolean,
    actionsEnabled: Boolean,
    onOpen: () -> Unit,
    onEvent: (DocumentActionEvent) -> Unit,
) {
    var showMenu by remember(document.id) { mutableStateOf(false) }
    Surface(
        onClick = onOpen,
        enabled = actionsEnabled,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Surface(
                modifier = Modifier
                    .width(80.dp)
                    .aspectRatio(595f / 842f),
                shape = MaterialTheme.shapes.small,
                shadowElevation = 2.dp,
            ) {
                DocumentThumbnail(
                    document = document,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(595f / 842f),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        text = document.name,
                        modifier = Modifier
                            .weight(1f)
                            .padding(top = 5.dp),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Box {
                        IconButton(
                            onClick = { showMenu = true },
                            enabled = actionsEnabled && !isBusy,
                            modifier = Modifier.size(48.dp),
                        ) {
                            if (isBusy) {
                                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(
                                    painterResource(R.drawable.more_vert_24px),
                                    contentDescription = stringResource(
                                        R.string.document_actions,
                                        document.name
                                    ),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.document_edit)) },
                                leadingIcon = { Icon(painterResource(R.drawable.edit_24px), null) },
                                onClick = { showMenu = false; onOpen() },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.document_download)) },
                                leadingIcon = {
                                    Icon(
                                        painterResource(R.drawable.ic_document_download),
                                        null
                                    )
                                },
                                onClick = {
                                    showMenu = false; onEvent(
                                    DocumentActionEvent.Download(
                                        document
                                    )
                                )
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
                                onClick = {
                                    showMenu = false; onEvent(DocumentActionEvent.Share(document))
                                },
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        stringResource(R.string.document_delete),
                                        color = MaterialTheme.colorScheme.error
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        painterResource(R.drawable.delete_24px),
                                        null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                },
                                onClick = {
                                    showMenu = false; onEvent(
                                    DocumentActionEvent.RequestDelete(
                                        document
                                    )
                                )
                                },
                            )
                        }
                    }
                }
                Text(
                    text = documentDate(document),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.document_pdf_size, documentFileSize(document)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (tag != null) DocumentTagLabel(tag)
            }
        }
    }
}
