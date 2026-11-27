package com.joseleandro.scaniqo.ui.documents.components

import com.joseleandro.scaniqo.core.resources.R as CoreResourcesR
import com.joseleandro.scaniqo.feature.library.R

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joseleandro.scaniqo.ui.theme.ScaniqoTheme
import com.joseleandro.scaniqo.core.localization.bidiArgument
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
    modifier: Modifier = Modifier,
    showLargePreview: Boolean = false,
) {
    val dark = MaterialTheme.colorScheme.surface.luminance() < .5f
    Surface(
        onClick = onOpen,
        enabled = actionsEnabled,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = if (dark) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (dark) 0.55f else 1f)),
    ) {
        if (showLargePreview) {
            Column {
                Surface(
                    modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f),
                    color = if (dark) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.background,
                ) {
                    Box(
                        modifier = Modifier.padding(16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxHeight().aspectRatio(595f / 842f),
                            shape = MaterialTheme.shapes.extraSmall,
                            shadowElevation = 2.dp,
                        ) {
                            DocumentThumbnail(
                                document = document,
                                modifier = Modifier.fillMaxWidth().aspectRatio(595f / 842f),
                            )
                        }
                    }
                }
                DocumentCardDetails(
                    document = document,
                    tag = tag,
                    isBusy = isBusy,
                    actionsEnabled = actionsEnabled,
                    onOpen = onOpen,
                    onEvent = onEvent,
                    modifier = Modifier.padding(12.dp),
                )
            }
        } else {
            BoxWithConstraints {
                val thumbnailWidth = if (maxWidth < 320.dp || LocalDensity.current.fontScale > 1.3f) {
                    56.dp
                } else {
                    80.dp
                }
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Surface(
                        modifier = Modifier.width(thumbnailWidth).aspectRatio(595f / 842f),
                        shape = MaterialTheme.shapes.small,
                        shadowElevation = 2.dp,
                    ) {
                        DocumentThumbnail(
                            document = document,
                            modifier = Modifier.fillMaxWidth().aspectRatio(595f / 842f),
                        )
                    }
                    DocumentCardDetails(
                        document = document,
                        tag = tag,
                        isBusy = isBusy,
                        actionsEnabled = actionsEnabled,
                        onOpen = onOpen,
                        onEvent = onEvent,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun DocumentCardDetails(
    document: DocumentModel,
    tag: TagModel?,
    isBusy: Boolean,
    actionsEnabled: Boolean,
    onOpen: () -> Unit,
    onEvent: (DocumentActionEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showMenu by remember(document.id) { mutableStateOf(false) }
    Column(
        modifier = modifier,
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
                style = MaterialTheme.typography.titleMedium.copy(textDirection = TextDirection.ContentOrLtr),
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
                            painterResource(CoreResourcesR.drawable.more_vert_24px),
                            contentDescription = stringResource(
                                CoreResourcesR.string.document_actions,
                                bidiArgument(document.name)
                            ),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.document_edit)) },
                        leadingIcon = { Icon(painterResource(CoreResourcesR.drawable.edit_24px), null) },
                        onClick = { showMenu = false; onOpen() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(CoreResourcesR.string.document_download)) },
                        leadingIcon = {
                            Icon(
                                painterResource(CoreResourcesR.drawable.ic_document_download),
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
                        text = { Text(stringResource(CoreResourcesR.string.document_share)) },
                        leadingIcon = {
                            Icon(
                                painterResource(CoreResourcesR.drawable.ic_document_share),
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
                                stringResource(CoreResourcesR.string.document_delete),
                                color = MaterialTheme.colorScheme.error
                            )
                        },
                        leadingIcon = {
                            Icon(
                                painterResource(CoreResourcesR.drawable.delete_24px),
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
            text = stringResource(CoreResourcesR.string.document_pdf_size, bidiArgument(documentFileSize(document))),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (tag != null) DocumentTagLabel(tag)
    }

}

@Preview(name = "Card claro", widthDp = 390)
@Preview(name = "Card escuro", widthDp = 390, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DocumentCardPreview() {
    ScaniqoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            DocumentCard(
                document = DocumentModel(
                    id = 1,
                    name = "Contrato de prestação de serviços",
                    pdfUri = "preview.pdf",
                    pageCount = 3,
                    createdAt = 1781431200000L,
                    sizeBytes = 248_000L,
                ),
                tag = null,
                isBusy = false,
                actionsEnabled = true,
                onOpen = {},
                onEvent = {},
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}
