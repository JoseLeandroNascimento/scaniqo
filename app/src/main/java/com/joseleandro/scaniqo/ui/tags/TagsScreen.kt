package com.joseleandro.scaniqo.ui.tags

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joseleandro.scaniqo.R
import com.joseleandro.scaniqo.domain.model.TagIcon
import com.joseleandro.scaniqo.domain.model.TagModel
import com.joseleandro.scaniqo.ui.components.toast.AppToastHost
import com.joseleandro.scaniqo.ui.components.toast.AppToastType
import com.joseleandro.scaniqo.ui.components.toast.rememberAppToastState
import com.joseleandro.scaniqo.ui.theme.ScaniqoTheme
import com.joseleandro.scaniqo.ui.theme.dimensions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagsScreen(
    onNavigationBack: () -> Unit,
    modifier: Modifier = Modifier,
    uiState: TagsUiState,
    onEvent: (TagsEvent) -> Unit,
) {
    val dimensions = MaterialTheme.dimensions
    val toastState = rememberAppToastState()
    val message = when (uiState.message) {
        TagsMessage.SAVED -> stringResource(R.string.tag_saved)
        TagsMessage.DELETED -> stringResource(R.string.tag_deleted)
        else -> null
    }

    // A mensagem permanece no estado até a exibição terminar.
    LaunchedEffect(uiState.message, toastState) {
        if (message != null) {
            toastState.show(message = message, type = AppToastType.SUCCESS)
            onEvent(TagsEvent.DismissMessage)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.gerenciar_tags)) },
                navigationIcon = {
                    IconButton(onClick = onNavigationBack) {
                        Icon(
                            painterResource(R.drawable.arrow_back_24px),
                            contentDescription = stringResource(R.string.icone_de_voltar),
                        )
                    }
                },
            )
        },
        snackbarHost = { AppToastHost(state = toastState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onEvent(TagsEvent.CreateTag) },
                icon = {
                    Icon(painterResource(R.drawable.add_24px), contentDescription = null)
                },
                text = { Text(stringResource(R.string.nova_tag)) },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
            contentPadding = PaddingValues(
                start = dimensions.screenPadding,
                end = dimensions.screenPadding,
                top = dimensions.spacingMd,
                bottom = 104.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "header") {
                TagsHeader(
                    count = uiState.tags.size,
                    showCount = !uiState.isLoading && !uiState.loadError,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }

            when {
                uiState.isLoading -> item(key = "loading") {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 64.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                        Text(
                            stringResource(R.string.tags_loading),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                uiState.loadError -> item(key = "error") {
                    TagsLoadError(onRetry = { onEvent(TagsEvent.RetryLoad) })
                }
                uiState.tags.isEmpty() -> item(key = "empty") {
                    TagsEmptyState()
                }
                else -> items(uiState.tags, key = { it.id }) { tag ->
                    TagItemCard(
                        tag = tag,
                        onEdit = { onEvent(TagsEvent.EditTag(tag)) },
                        onDelete = { onEvent(TagsEvent.RequestDelete(tag)) },
                    )
                }
            }
        }
    }

    uiState.editor?.let { editor ->
        TagEditorSheet(
            editor = editor,
            onEvent = onEvent,
        )
    }

    uiState.pendingDeletion?.let { tag ->
        DeleteTagDialog(
            tag = tag,
            isDeleting = uiState.isDeleting,
            hasError = uiState.message == TagsMessage.DELETE_ERROR,
            onDismiss = { onEvent(TagsEvent.DismissDelete) },
            onConfirm = { onEvent(TagsEvent.ConfirmDelete) },
        )
    }
}

@Composable
private fun TagsHeader(
    count: Int,
    showCount: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.suas_tags),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            if (showCount) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    shape = MaterialTheme.shapes.extraLarge,
                ) {
                    Text(
                        text = pluralStringResource(R.plurals.tags_count, count, count),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
        Text(
            text = stringResource(R.string.tags_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TagItemCard(
    tag: TagModel,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var showMenu by remember(tag.id) { mutableStateOf(false) }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            TagIconBadge(icon = tag.icon, color = tag.color)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = tag.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(tag.icon.labelRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        painter = painterResource(R.drawable.more_vert_24px),
                        contentDescription = stringResource(R.string.tag_actions, tag.name),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.editar)) },
                        leadingIcon = {
                            Icon(painterResource(R.drawable.edit_24px), contentDescription = null)
                        },
                        onClick = {
                            showMenu = false
                            onEdit()
                        },
                    )
                    DropdownMenuItem(
                        text = {
                            Text(stringResource(R.string.excluir), color = MaterialTheme.colorScheme.error)
                        },
                        leadingIcon = {
                            Icon(
                                painterResource(R.drawable.delete_24px),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                            )
                        },
                        onClick = {
                            showMenu = false
                            onDelete()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun TagsEmptyState() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TagIconBadge(
            icon = TagIcon.LABEL,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp),
            size = 72.dp,
        )
        Text(
            text = stringResource(R.string.tags_empty_title),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.tags_empty_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun TagsLoadError(onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(R.string.tags_load_error_title),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            stringResource(R.string.tags_load_error_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onRetry) {
            Text(stringResource(R.string.try_again))
        }
    }
}

@Composable
private fun DeleteTagDialog(
    tag: TagModel,
    isDeleting: Boolean,
    hasError: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!isDeleting) onDismiss() },
        icon = {
            Icon(
                painterResource(R.drawable.delete_24px),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
        },
        title = { Text(stringResource(R.string.tag_delete_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.tag_delete_description, tag.name))
                if (hasError) {
                    Text(
                        stringResource(R.string.tag_delete_error),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = !isDeleting,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                if (isDeleting) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(stringResource(if (isDeleting) R.string.tag_deleting else R.string.excluir))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isDeleting) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Preview(name = "Claro", showBackground = true)
@Preview(name = "Escuro", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun TagsScreenPreview() {
    ScaniqoTheme {
        TagsScreen(
            onNavigationBack = {},
            uiState = TagsUiState(
                isLoading = false,
                tags = listOf(
                    TagModel(id = 1, name = "Comprovantes", color = Color(0xFF2B6F91), iconKey = TagIcon.DOCUMENT.key),
                    TagModel(id = 2, name = "Trabalho", color = Color(0xFF7D5BA6), iconKey = TagIcon.FOLDER.key),
                    TagModel(id = 3, name = "Pessoal", color = Color(0xFF39765A), iconKey = TagIcon.FOLDER.key),
                ),
            ),
            onEvent = {},
        )
    }
}

@Preview(name = "Sem tags", showBackground = true)
@Composable
private fun TagsEmptyPreview() {
    ScaniqoTheme {
        TagsScreen(onNavigationBack = {}, uiState = TagsUiState(isLoading = false), onEvent = {})
    }
}
