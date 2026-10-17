package com.joseleandro.scaniqo.ui.documents.components

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
import com.joseleandro.scaniqo.ui.documents.DocumentEditorUiState
import com.joseleandro.scaniqo.ui.documents.DocumentNameError
import com.joseleandro.scaniqo.ui.documentEditor.DocumentsEvent
import com.joseleandro.scaniqo.ui.theme.ScaniqoTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DocumentEditorSheet(
    editor: DocumentEditorUiState,
    tags: List<TagModel>,
    onEvent: (DocumentsEvent) -> Unit,
) {
    val isSaving by rememberUpdatedState(editor.isSaving)
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { !isSaving || it != SheetValue.Hidden },
    )
    ModalBottomSheet(
        onDismissRequest = { if (!editor.isSaving) onEvent(DocumentsEvent.DismissEditor) },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        DocumentEditorContent(editor, tags, onEvent)
    }
}

@Composable
private fun DocumentEditorContent(
    editor: DocumentEditorUiState,
    tags: List<TagModel>,
    onEvent: (DocumentsEvent) -> Unit,
) {
    val keyboard = LocalSoftwareKeyboardController.current
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
                onClick = { onEvent(DocumentsEvent.DismissEditor) },
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
                onValueChange = { onEvent(DocumentsEvent.NameChanged(it)) },
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
                    if (!editor.isSaving) {
                        keyboard?.hide()
                        onEvent(DocumentsEvent.SaveDocument)
                    }
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
                    onSelect = { onEvent(DocumentsEvent.TagChanged(it)) },
                    enabled = !editor.isSaving,
                    background = MaterialTheme.colorScheme.surface,
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    listState = tagListState,
                )
                if (tags.isEmpty()) {
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

        Button(
            onClick = {
                keyboard?.hide()
                onEvent(DocumentsEvent.SaveDocument)
            },
            enabled = !editor.isSaving,
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
            Text(
                stringResource(
                    when {
                        editor.isSaving -> R.string.document_saving
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
private fun DocumentEditorPreview() {
    ScaniqoTheme {
        Surface {
            DocumentEditorContent(
                editor = DocumentEditorUiState(name = "Contrato de aluguel", tagId = 1),
                tags = listOf(
                    TagModel(id = 1, name = "Casa", color = Color(0xFF4F7CAC), iconKey = TagIcon.HOME.key),
                    TagModel(id = 2, name = "Pessoal", color = Color(0xFF0F766E), iconKey = TagIcon.PERSON.key),
                    TagModel(id = 3, name = "Trabalho", color = Color(0xFF7C5AC2), iconKey = TagIcon.WORK.key),
                    TagModel(id = 4, name = "Comprovantes", color = Color(0xFFB45F34), iconKey = TagIcon.RECEIPT.key),
                ),
                onEvent = {},
            )
        }
    }
}
