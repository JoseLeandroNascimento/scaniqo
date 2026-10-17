package com.joseleandro.scaniqo.ui.documentEditor

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joseleandro.scaniqo.R
import com.joseleandro.scaniqo.domain.model.DocumentModel
import com.joseleandro.scaniqo.ui.theme.ScaniqoTheme

private enum class MergePosition { START, AFTER_PAGE, END }

/** A origem é escolhida depois da posição; abrir um seletor não altera o documento. */
@Composable
internal fun DocumentMergeSheetContent(
    state: DocumentToolsUiState,
    onEvent: (DocumentToolsEvent) -> Unit,
    documents: List<DocumentModel> = emptyList(),
) {
    val workspace = state.workspace ?: return
    var position by rememberSaveable(workspace.workspaceId) { mutableStateOf(MergePosition.END) }
    var pageNumber by rememberSaveable(workspace.workspaceId, state.selectedPageId) {
        mutableStateOf((state.selectedPageIndex + 1).toString())
    }
    var appInsertionIndex by rememberSaveable(workspace.workspaceId) { mutableStateOf<Int?>(null) }
    val enabled = state.canEdit && state.signatureDraft == null && workspace.pages.isNotEmpty()
    val insertionIndex = appInsertionIndex
    if (insertionIndex != null) {
        DocumentMergeAppPicker(
            documents = documents,
            currentDocumentId = state.document?.id,
            resultProtected = workspace.isProtected,
            enabled = enabled,
            onCancel = { appInsertionIndex = null },
            onConfirm = { selectedDocuments ->
                appInsertionIndex = null
                onEvent(DocumentToolsEvent.MergeAppDocuments(selectedDocuments, insertionIndex))
            },
        )
        return
    }
    DocumentMergeOptions(
        pageCount = workspace.pages.size,
        position = position,
        pageNumber = pageNumber,
        enabled = enabled,
        onPositionChange = { selected ->
            if (selected == MergePosition.AFTER_PAGE && position != selected) {
                pageNumber = (state.selectedPageIndex + 1).toString()
            }
            position = selected
        },
        onPageNumberChange = { pageNumber = it },
        onSelectPdfs = { onEvent(DocumentToolsEvent.MergePdfs(insertionIndex = it)) },
        onSelectAppDocuments = { appInsertionIndex = it },
    )
}

@Composable
private fun DocumentMergeOptions(
    pageCount: Int,
    position: MergePosition,
    pageNumber: String,
    enabled: Boolean,
    onPositionChange: (MergePosition) -> Unit,
    onPageNumberChange: (String) -> Unit,
    onSelectPdfs: (Int) -> Unit,
    onSelectAppDocuments: (Int) -> Unit,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val afterPage = pageNumber.toIntOrNull()?.takeIf { it in 1..pageCount }
    // O número exibido já corresponde ao índice de inserção depois daquela página.
    val insertionIndex = when (position) {
        MergePosition.START -> 0
        MergePosition.AFTER_PAGE -> afterPage
        MergePosition.END -> pageCount
    }
    val invalidPage = position == MergePosition.AFTER_PAGE && afterPage == null

    Column(
        Modifier.fillMaxWidth().heightIn(max = 520.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Column(
            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(top = 4.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.merge_position_label), style = MaterialTheme.typography.titleMedium)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                    MergePosition.entries.forEachIndexed { index, option ->
                        SegmentedButton(
                            selected = position == option,
                            onClick = {
                                focus.clearFocus()
                                keyboard?.hide()
                                onPositionChange(option)
                            },
                            shape = SegmentedButtonDefaults.itemShape(index, MergePosition.entries.size),
                            enabled = enabled,
                            icon = {},
                            modifier = Modifier.heightIn(min = 52.dp).fillMaxHeight(),
                            label = {
                                Text(
                                    stringResource(when (option) {
                                        MergePosition.START -> R.string.merge_position_start
                                        MergePosition.AFTER_PAGE -> R.string.merge_position_after
                                        MergePosition.END -> R.string.merge_position_end
                                    }),
                                    textAlign = TextAlign.Center,
                                )
                            },
                        )
                    }
                }
            }
            if (position == MergePosition.AFTER_PAGE) {
                OutlinedTextField(
                    value = pageNumber,
                    onValueChange = { value ->
                        onPageNumberChange(value.filter { it in '0'..'9' }.take(3))
                    },
                    label = { Text(stringResource(R.string.merge_position_page_label)) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    singleLine = true,
                    enabled = enabled,
                    isError = invalidPage,
                    supportingText = if (invalidPage) {
                        { Text(stringResource(R.string.merge_position_page_error, pageCount)) }
                    } else null,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = {
                        focus.clearFocus()
                        keyboard?.hide()
                    }),
                )
            }
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val description = when (position) {
                        MergePosition.START -> stringResource(R.string.merge_position_start_description)
                        MergePosition.AFTER_PAGE -> if (afterPage != null) {
                            stringResource(R.string.merge_position_after_description, afterPage)
                        } else {
                            stringResource(R.string.merge_position_invalid_description)
                        }
                        MergePosition.END -> stringResource(R.string.merge_position_end_description)
                    }
                    Text(description, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        pluralStringResource(R.plurals.merge_position_page_count, pageCount, pageCount),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        // A ação permanece acessível enquanto o conteúdo rola em janelas menores.
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text(stringResource(R.string.merge_source_label), style = MaterialTheme.typography.titleSmall)
            Button(
                onClick = {
                    insertionIndex?.let { index ->
                        focus.clearFocus()
                        keyboard?.hide()
                        onSelectPdfs(index)
                    }
                },
                enabled = enabled && insertionIndex != null,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) {
                Text(stringResource(R.string.merge_source_device), textAlign = TextAlign.Center)
            }
            OutlinedButton(
                onClick = {
                    insertionIndex?.let { index ->
                        focus.clearFocus()
                        keyboard?.hide()
                        onSelectAppDocuments(index)
                    }
                },
                enabled = enabled && insertionIndex != null,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) {
                Text(stringResource(R.string.merge_source_app), textAlign = TextAlign.Center)
            }
        }
    }
}

@Preview(name = "Mesclar PDFs", widthDp = 390, heightDp = 700, showBackground = true)
@Preview(name = "Mesclar PDFs escuro", widthDp = 390, heightDp = 700, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DocumentMergeSheetPreview() {
    MergeSheetPreviewContent(MergePosition.END)
}

@Preview(name = "Mesclar após página — fonte ampliada", widthDp = 360, heightDp = 700, fontScale = 1.3f)
@Composable
private fun DocumentMergeAfterPagePreview() {
    MergeSheetPreviewContent(MergePosition.AFTER_PAGE)
}

@Composable
private fun MergeSheetPreviewContent(initialPosition: MergePosition) {
    var position by rememberSaveable { mutableStateOf(initialPosition) }
    var pageNumber by rememberSaveable { mutableStateOf("3") }
    ScaniqoTheme {
        Surface {
            Column(
                Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(stringResource(R.string.merge_position_title), style = MaterialTheme.typography.titleLarge)
                DocumentMergeOptions(
                    pageCount = 8,
                    position = position,
                    pageNumber = pageNumber,
                    enabled = true,
                    onPositionChange = { position = it },
                    onPageNumberChange = { pageNumber = it },
                    onSelectPdfs = {},
                    onSelectAppDocuments = {},
                )
            }
        }
    }
}
