package com.joseleandro.scaniqo.ui.documentCommon.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joseleandro.scaniqo.R
import com.joseleandro.scaniqo.domain.model.DOCUMENT_NAME_MAX_LENGTH
import com.joseleandro.scaniqo.domain.model.TagIcon
import com.joseleandro.scaniqo.domain.model.TagModel
import com.joseleandro.scaniqo.ui.ads.ToolAdActionContent
import com.joseleandro.scaniqo.ui.documentCommon.state.DocumentMetadataUiState
import com.joseleandro.scaniqo.ui.documentCommon.state.DocumentNameError
import com.joseleandro.scaniqo.ui.theme.ScaniqoTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DocumentMetadataSheet(
    editor: DocumentMetadataUiState,
    tags: List<TagModel>,
    onNameChange: (String) -> Unit,
    onTagChange: (Long?) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    isLoadingTags: Boolean = false,
    tagsLoadError: Boolean = false,
    onRetryTags: () -> Unit = {},
    saveShowsVideo: Boolean = false,
) {
    val isSaving by rememberUpdatedState(editor.isSaving)
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { !isSaving || it != SheetValue.Hidden },
    )
    ModalBottomSheet(
        onDismissRequest = { if (!editor.isSaving) onDismiss() },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        DocumentMetadataContent(
            editor = editor,
            tags = tags,
            onNameChange = onNameChange,
            onTagChange = onTagChange,
            onSave = onSave,
            onDismiss = onDismiss,
            isLoadingTags = isLoadingTags,
            tagsLoadError = tagsLoadError,
            onRetryTags = onRetryTags,
            saveShowsVideo = saveShowsVideo,
        )
    }
}

@Composable
private fun DocumentMetadataContent(
    editor: DocumentMetadataUiState,
    tags: List<TagModel>,
    onNameChange: (String) -> Unit,
    onTagChange: (Long?) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    isLoadingTags: Boolean = false,
    tagsLoadError: Boolean = false,
    onRetryTags: () -> Unit = {},
    saveShowsVideo: Boolean = false,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val canSave = !editor.isSaving && (editor.tagId == null || (!isLoadingTags && !tagsLoadError))
    val nameError = when (editor.nameError) {
        DocumentNameError.REQUIRED -> stringResource(R.string.document_name_required)
        DocumentNameError.TOO_LONG -> stringResource(R.string.document_name_too_long, DOCUMENT_NAME_MAX_LENGTH)
        null -> null
    }
    val tagListState = rememberLazyListState(
        initialFirstVisibleItemIndex = tags.indexOfFirst { it.id == editor.tagId } + 1,
    )

    Column(modifier = Modifier.fillMaxWidth().imePadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(
                    if (editor.documentId == null) R.string.document_create_title
                    else R.string.document_editor_title,
                ),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = { onDismiss() },
                enabled = !editor.isSaving,
            ) {
                Text(stringResource(R.string.document_cancel))
            }
        }

        // A altura acompanha o conteúdo; a rolagem só é necessária com pouco espaço ou teclado.
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = editor.name,
                onValueChange = onNameChange,
                textStyle = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.ContentOrLtr),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                label = { Text(stringResource(R.string.document_name_label)) },
                placeholder = { Text(stringResource(R.string.document_name_placeholder)) },
                enabled = !editor.isSaving,
                singleLine = true,
                isError = nameError != null,
                shape = MaterialTheme.shapes.medium,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = {
                    keyboard?.hide()
                    // O vídeo só começa pelo botão que informa claramente essa ação.
                    if (canSave && !saveShowsVideo) onSave()
                }),
                supportingText = if (nameError != null) {
                    {
                        Text(
                            text = nameError,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    }
                } else null,
            )

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.document_tag_label),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Text(
                        stringResource(R.string.document_optional),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DocumentTagSelector(
                    tags = tags,
                    selectedTagId = editor.tagId,
                    nullOptionLabel = stringResource(R.string.documents_no_tag),
                    onSelect = onTagChange,
                    enabled = !editor.isSaving && !isLoadingTags && !tagsLoadError,
                    background = MaterialTheme.colorScheme.surface,
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    listState = tagListState,
                )
                if (isLoadingTags) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text(stringResource(R.string.tags_loading), style = MaterialTheme.typography.bodySmall)
                    }
                } else if (tagsLoadError) {
                    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                        Text(
                            stringResource(R.string.tags_load_error_title),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        )
                        TextButton(onClick = onRetryTags, enabled = !editor.isSaving) {
                            Text(stringResource(R.string.documents_retry))
                        }
                    }
                } else if (tags.isEmpty()) {
                    Text(
                        stringResource(R.string.document_no_tags_available),
                        modifier = Modifier.padding(horizontal = 20.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (editor.saveError) {
                Text(
                    stringResource(R.string.document_save_error),
                    modifier = Modifier
                        .padding(horizontal = 20.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        if (saveShowsVideo) Text(
            text = stringResource(R.string.tool_ad_reward_hint),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 12.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = {
                keyboard?.hide()
                onSave()
            },
            enabled = canSave,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            shape = MaterialTheme.shapes.large,
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        ) {
            if (editor.isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.width(8.dp))
            }
            ToolAdActionContent(
                isVideo = saveShowsVideo && !editor.isSaving,
                label = stringResource(
                    when {
                        editor.isSaving -> R.string.document_saving
                        saveShowsVideo -> R.string.tool_ad_watch_save
                        editor.documentId == null -> R.string.document_save
                        else -> R.string.document_save_changes
                    },
                ),
            )
        }
    }
}

@Preview(name = "Editor documento claro", showBackground = true, locale = "pt-rBR")
@Preview(name = "Editor documento escuro", showBackground = true, locale = "pt-rBR", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DocumentMetadataPreview() {
    ScaniqoTheme {
        Surface {
            DocumentMetadataContent(
                editor = DocumentMetadataUiState(name = "Contrato de aluguel", tagId = 1),
                tags = listOf(
                    TagModel(id = 1, name = "Casa", color = Color(0xFF4F7CAC), iconKey = TagIcon.HOME.key),
                    TagModel(id = 2, name = "Pessoal", color = Color(0xFF0F766E), iconKey = TagIcon.PERSON.key),
                    TagModel(id = 3, name = "Trabalho", color = Color(0xFF7C5AC2), iconKey = TagIcon.WORK.key),
                    TagModel(id = 4, name = "Comprovantes", color = Color(0xFFB45F34), iconKey = TagIcon.RECEIPT.key),
                ),
                onNameChange = {},
                onTagChange = {},
                onSave = {},
                onDismiss = {},
            )
        }
    }
}
