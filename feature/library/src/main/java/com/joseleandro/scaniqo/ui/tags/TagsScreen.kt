package com.joseleandro.scaniqo.ui.tags

import com.joseleandro.scaniqo.core.resources.R as CoreResourcesR
import com.joseleandro.scaniqo.feature.library.R

import com.joseleandro.scaniqo.ui.components.ScaniqoCardDefaults
import com.joseleandro.scaniqo.ui.components.color
import com.joseleandro.scaniqo.ui.components.labelRes
import com.joseleandro.scaniqo.ui.components.drawableRes

import androidx.compose.ui.graphics.toArgb

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.AlertDialog
import com.joseleandro.scaniqo.ui.components.DialogActions
import androidx.compose.material3.Button
import com.joseleandro.scaniqo.ui.components.ScaniqoLoadingIndicator
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joseleandro.scaniqo.core.localization.bidiArgument
import com.joseleandro.scaniqo.domain.model.TagIcon
import com.joseleandro.scaniqo.domain.model.TagModel
import com.joseleandro.scaniqo.ui.components.toast.AppToastHost
import com.joseleandro.scaniqo.ui.components.toast.AppToastType
import com.joseleandro.scaniqo.ui.components.toast.rememberAppToastState
import com.joseleandro.scaniqo.ui.components.EmptyStateIllustration
import com.joseleandro.scaniqo.ui.components.ScaniqoEmptyState
import com.joseleandro.scaniqo.ui.theme.ScaniqoTheme
import com.joseleandro.scaniqo.ui.theme.dimensions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagsScreen(
    onNavigationBack: () -> Unit,
    modifier: Modifier = Modifier,
    uiState: TagsUiState,
    onEvent: (TagsEvent) -> Unit,
    onCreateTag: () -> Unit = {},
    onEditTag: (Long) -> Unit = {},
) {
    val dimensions = MaterialTheme.dimensions
    val density = LocalDensity.current
    var createButtonHeightPx by remember { mutableIntStateOf(0) }
    val createButtonClearance = with(density) { createButtonHeightPx.toDp() } + 32.dp
    val toastState = rememberAppToastState()
    val createLabel = stringResource(R.string.nova_tag)
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
                            painterResource(CoreResourcesR.drawable.arrow_back_24px),
                            contentDescription = stringResource(R.string.icone_de_voltar),
                        )
                    }
                },
            )
        },
        snackbarHost = { AppToastHost(state = toastState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreateTag,
                modifier = Modifier
                    .onSizeChanged { createButtonHeightPx = it.height }
                    .semantics { contentDescription = createLabel },
                icon = {
                    Icon(painterResource(CoreResourcesR.drawable.add_24px), contentDescription = null)
                },
                text = { Text(createLabel) },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(innerPadding).consumeWindowInsets(innerPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(300.dp * LocalDensity.current.fontScale.coerceAtLeast(1f)),
                modifier = Modifier.widthIn(max = 1200.dp).fillMaxSize(),
                contentPadding = PaddingValues(
                    start = dimensions.screenPadding,
                    end = dimensions.screenPadding,
                    top = dimensions.spacingMd,
                    bottom = maxOf(104.dp, createButtonClearance),
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "header", span = { GridItemSpan(maxLineSpan) }) {
                    TagsHeader(
                        count = uiState.tags.size,
                        showCount = !uiState.isLoading && !uiState.loadError,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                }

                when {
                    uiState.isLoading -> item(key = "loading", span = { GridItemSpan(maxLineSpan) }) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 64.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            ScaniqoLoadingIndicator()
                            Text(
                                stringResource(CoreResourcesR.string.tags_loading),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    uiState.loadError -> item(key = "error", span = { GridItemSpan(maxLineSpan) }) {
                        TagsLoadError(onRetry = { onEvent(TagsEvent.RetryLoad) })
                    }
                    uiState.tags.isEmpty() -> item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                        TagsEmptyState()
                    }
                    else -> items(uiState.tags, key = { it.id }) { tag ->
                        TagItemCard(
                            tag = tag,
                            onEdit = { onEditTag(tag.id) },
                            onDelete = { onEvent(TagsEvent.RequestDelete(tag)) },
                        )
                    }
                }
            }
        }
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
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.align(Alignment.CenterVertically),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    painter = painterResource(TagIcon.LABEL.drawableRes),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    text = stringResource(R.string.suas_tags),
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            if (showCount) {
                Surface(
                    modifier = Modifier.align(Alignment.CenterVertically),
                    color = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    shape = MaterialTheme.shapes.extraLarge,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Text(
                        text = pluralStringResource(R.plurals.tags_count, count, count),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
        if (count > 0) {
            Text(
                text = stringResource(R.string.tags_edit_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TagItemCard(
    tag: TagModel,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val largeText = LocalDensity.current.fontScale >= 1.5f

    Surface(
        onClick = onEdit,
        color = ScaniqoCardDefaults.containerColor,
        shape = ScaniqoCardDefaults.shape,
        border = ScaniqoCardDefaults.border,
    ) {
        if (largeText) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    TagIconBadge(icon = tag.icon, color = tag.color)
                    TagActionsMenu(tag = tag, onEdit = onEdit, onDelete = onDelete)
                }
                TagCardName(tag = tag, maxLines = Int.MAX_VALUE)
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 16.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TagIconBadge(icon = tag.icon, color = tag.color)
                TagCardName(tag = tag, modifier = Modifier.weight(1f))
                TagActionsMenu(tag = tag, onEdit = onEdit, onDelete = onDelete)
            }
        }
    }
}

@Composable
private fun TagCardName(
    tag: TagModel,
    modifier: Modifier = Modifier,
    maxLines: Int = 2,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = tag.name,
            style = MaterialTheme.typography.titleMedium.copy(textDirection = TextDirection.ContentOrLtr),
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = stringResource(tag.icon.labelRes),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TagActionsMenu(
    tag: TagModel,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var showMenu by remember(tag.id) { mutableStateOf(false) }
    Box {
        IconButton(onClick = { showMenu = true }) {
            Icon(
                painter = painterResource(CoreResourcesR.drawable.more_vert_24px),
                contentDescription = stringResource(R.string.tag_actions, bidiArgument(tag.name)),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(CoreResourcesR.string.editar)) },
                leadingIcon = {
                    Icon(painterResource(CoreResourcesR.drawable.edit_24px), contentDescription = null)
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
                        painterResource(CoreResourcesR.drawable.delete_24px),
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

@Composable
private fun TagsEmptyState() {
    ScaniqoEmptyState(
        title = stringResource(R.string.tags_empty_title),
        description = stringResource(R.string.tags_empty_description),
        illustration = EmptyStateIllustration.TAGS,
    )
}

@Composable
private fun TagsLoadError(onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(CoreResourcesR.string.tags_load_error_title),
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
                painterResource(CoreResourcesR.drawable.delete_24px),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
        },
        title = { Text(stringResource(R.string.tag_delete_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.tag_delete_description, bidiArgument(tag.name)))
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
            DialogActions(
                confirmText = stringResource(if (isDeleting) R.string.tag_deleting else R.string.excluir),
                onConfirm = onConfirm,
                dismissText = stringResource(CoreResourcesR.string.cancel),
                onDismiss = onDismiss,
                confirmEnabled = !isDeleting,
                dismissEnabled = !isDeleting,
                confirming = isDeleting,
                destructive = true,
            )
        },
    )
}

@Preview(name = "Claro", showBackground = true)
@Preview(name = "Escuro", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Tags tela externa", widthDp = 320, heightDp = 720, locale = "pt-rBR")
@Preview(name = "Tags tablet", widthDp = 1280, heightDp = 800, locale = "pt-rBR")
@Preview(name = "Tags texto grande", widthDp = 320, heightDp = 640, fontScale = 2f, locale = "pt-rBR")
@Composable
private fun TagsScreenPreview() {
    ScaniqoTheme {
        TagsScreen(
            onNavigationBack = {},
            uiState = TagsUiState(
                isLoading = false,
                tags = listOf(
                    TagModel(id = 1, name = "Comprovantes", colorArgb = Color(0xFF2B6F91).toArgb(), iconKey = TagIcon.DOCUMENT.key),
                    TagModel(id = 2, name = "Trabalho", colorArgb = Color(0xFF7D5BA6).toArgb(), iconKey = TagIcon.FOLDER.key),
                    TagModel(id = 3, name = "Pessoal", colorArgb = Color(0xFF39765A).toArgb(), iconKey = TagIcon.FOLDER.key),
                ),
            ),
            onEvent = {},
        )
    }
}

@Preview(name = "Sem tags", widthDp = 390, heightDp = 780, locale = "pt-rBR")
@Preview(name = "Sem tags escuro", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Sem tags fonte ampliada", widthDp = 320, heightDp = 640, fontScale = 1.5f, locale = "pt-rBR")
@Preview(name = "Sem tags tablet", widthDp = 1280, heightDp = 800, locale = "pt-rBR")
@Composable
private fun TagsEmptyPreview() {
    ScaniqoTheme {
        TagsScreen(onNavigationBack = {}, uiState = TagsUiState(isLoading = false), onEvent = {})
    }
}
