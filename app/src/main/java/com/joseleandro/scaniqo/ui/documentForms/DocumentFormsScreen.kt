package com.joseleandro.scaniqo.ui.documentForms

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import com.joseleandro.scaniqo.ui.components.DialogActions
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joseleandro.scaniqo.R
import com.joseleandro.scaniqo.domain.model.PdfFormDocument
import com.joseleandro.scaniqo.domain.model.PdfFormField
import com.joseleandro.scaniqo.domain.model.PdfFormFieldKind
import com.joseleandro.scaniqo.domain.model.PdfFormOption
import com.joseleandro.scaniqo.ui.theme.ScaniqoTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DocumentFormsScreen(
    state: DocumentFormsUiState,
    onBack: () -> Unit,
    onPickPdf: () -> Unit,
    onChange: (String, List<String>) -> Unit,
    onSave: () -> Unit,
) {
    var choiceId by rememberSaveable(state.document?.sessionId) { mutableStateOf<String?>(null) }
    var query by rememberSaveable(state.document?.sessionId) { mutableStateOf("") }
    var onlyPending by rememberSaveable(state.document?.sessionId) { mutableStateOf(false) }
    var help by rememberSaveable { mutableStateOf(false) }
    val fields = state.document?.fields.orEmpty()
    fun hasValue(field: PdfFormField) = state.values[field.id].orEmpty().any { it.isNotBlank() }
    val filled = fields.count(::hasValue)
    val missing = fields.count { it.required && !it.readOnly && !hasValue(it) }
    val visible = fields.filter {
        it.label.contains(query.trim(), ignoreCase = true) &&
            (!onlyPending || it.required && !it.readOnly && !hasValue(it))
    }
    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.form_screen_title), style = MaterialTheme.typography.titleMedium,
                    maxLines = 2, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !state.busy) {
                        Icon(painterResource(R.drawable.arrow_back_24px), stringResource(R.string.certificate_back))
                    }
                },
                actions = { IconButton(onClick = { help = true }) {
                    Icon(painterResource(R.drawable.ic_toast_info), stringResource(R.string.form_workspace_help))
                } },
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 4.dp) {
                Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.document != null) {
                        if (missing > 0) TextButton(onClick = { query = ""; onlyPending = true }, enabled = !state.busy) {
                            Text(stringResource(R.string.form_workspace_review))
                        }
                        Button(onClick = onSave, enabled = !state.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Icon(painterResource(R.drawable.ic_document_download), null, Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.form_save_copy))
                        }
                    } else Button(onClick = onPickPdf, enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.form_select))
                    }
                }
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    state.message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                    val document = state.document
                    if (document == null) {
                        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.primaryContainer) {
                            Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                Icon(painterResource(R.drawable.ic_tool_interactive_form), null,
                                    Modifier.size(52.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                Text(stringResource(R.string.form_workspace_intro), style = MaterialTheme.typography.headlineSmall)
                                Text(stringResource(R.string.form_workspace_hint), style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                        Text(stringResource(R.string.form_flat_hint), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Surface(onClick = onPickPdf, enabled = !state.busy, shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.surfaceContainerLow) {
                            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Icon(painterResource(R.drawable.ic_tool_interactive_form), null,
                                    Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
                                Column(Modifier.weight(1f)) {
                                    Text(document.name, style = MaterialTheme.typography.titleSmall, maxLines = 2,
                                        overflow = TextOverflow.Ellipsis)
                                    Text(stringResource(R.string.form_workspace_change), style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                        Text(stringResource(R.string.form_workspace_progress, filled, fields.size),
                            style = MaterialTheme.typography.labelLarge)
                        LinearProgressIndicator(progress = { filled.toFloat() / fields.size.coerceAtLeast(1) },
                            modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.form_workspace_search)) }, singleLine = true,
                            leadingIcon = { Icon(painterResource(R.drawable.ic_document_search), null) })
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item { FilterChip(selected = !onlyPending, onClick = { onlyPending = false },
                                label = { Text(stringResource(R.string.form_workspace_all)) }) }
                            item { FilterChip(selected = onlyPending, onClick = { onlyPending = true },
                                label = { Text(stringResource(R.string.form_workspace_pending, missing)) }) }
                        }
                        if (document.unsupportedFields > 0) Text(
                            stringResource(R.string.form_unsupported, document.unsupportedFields),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            items(visible, key = { it.id }) { field ->
                FormField(
                    field = field,
                    values = state.values[field.id].orEmpty(),
                    enabled = !state.busy && !field.readOnly,
                    onChange = { onChange(field.id, it) },
                    onChoose = { choiceId = field.id },
                )
            }
            if (state.document != null && visible.isEmpty()) item {
                Text(stringResource(R.string.form_workspace_no_match), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if (help) AlertDialog(
        onDismissRequest = { help = false },
        title = { Text(stringResource(R.string.form_workspace_help)) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.form_intro))
            Text(stringResource(R.string.form_workspace_original))
        } },
        confirmButton = { TextButton(onClick = { help = false }) { Text(stringResource(R.string.form_choice_done)) } },
    )
    val choice = state.document?.fields?.firstOrNull { it.id == choiceId }
    if (choice != null) {
        val selected = state.values[choice.id].orEmpty()
        AlertDialog(
            onDismissRequest = { choiceId = null },
            title = { Text(choice.label) },
            text = {
                LazyColumn {
                    itemsIndexed(choice.options) { _, option ->
                        Row(
                            Modifier.fillMaxWidth().clickable(enabled = !state.busy && !choice.readOnly) {
                                if (choice.kind == PdfFormFieldKind.MULTIPLE_CHOICE) {
                                    onChange(choice.id, if (option.value in selected) selected - option.value else selected + option.value)
                                } else {
                                    onChange(choice.id, listOf(option.value))
                                    choiceId = null
                                }
                            }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (choice.kind == PdfFormFieldKind.MULTIPLE_CHOICE) {
                                Checkbox(checked = option.value in selected, onCheckedChange = null)
                            } else RadioButton(selected = option.value in selected, onClick = null)
                            Text(option.label, Modifier.padding(start = 8.dp))
                        }
                    }
                }
            },
            confirmButton = {
                if (choice.required) {
                    TextButton(onClick = { choiceId = null }) { Text(stringResource(R.string.form_choice_done)) }
                } else {
                    DialogActions(
                        confirmText = stringResource(R.string.form_choice_done),
                        onConfirm = { choiceId = null },
                        dismissText = stringResource(R.string.form_choice_clear),
                        onDismiss = { onChange(choice.id, emptyList()); choiceId = null },
                    )
                }
            },
        )
    }
}

@Composable
private fun FormField(
    field: PdfFormField,
    values: List<String>,
    enabled: Boolean,
    onChange: (List<String>) -> Unit,
    onChoose: () -> Unit,
) {
    val label = field.label + if (field.required) " *" else ""
    when (field.kind) {
        PdfFormFieldKind.TEXT -> OutlinedTextField(
            value = values.firstOrNull().orEmpty(),
            onValueChange = { onChange(listOf(it.take(if (field.maxLength > 0) minOf(field.maxLength, 20_000) else 20_000))) },
            label = { Text(label) },
            enabled = enabled,
            singleLine = !field.multiline,
            minLines = if (field.multiline) 3 else 1,
            maxLines = if (field.multiline) 8 else 1,
            visualTransformation = if (field.password) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = if (field.password) KeyboardType.Password else KeyboardType.Text),
            supportingText = if (field.readOnly) ({ Text(stringResource(R.string.form_read_only)) }) else null,
            modifier = Modifier.fillMaxWidth(),
        )
        PdfFormFieldKind.CHECKBOX -> Row(
            Modifier.fillMaxWidth().toggleable(value = values.isNotEmpty(), enabled = enabled, role = Role.Checkbox) {
                onChange(if (it) listOf(field.options.first().value) else emptyList())
            }.padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = values.isNotEmpty(), onCheckedChange = null, enabled = enabled)
            Column(Modifier.padding(start = 8.dp)) {
                Text(label)
                if (field.readOnly) Text(stringResource(R.string.form_read_only), style = MaterialTheme.typography.bodySmall)
            }
        }
        PdfFormFieldKind.SINGLE_CHOICE, PdfFormFieldKind.MULTIPLE_CHOICE -> Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(
                field.options.filter { it.value in values }.joinToString { it.label }.ifBlank { stringResource(R.string.form_choice_empty) },
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = onChoose, enabled = enabled) { Text(stringResource(R.string.form_choice)) }
            if (field.readOnly) Text(stringResource(R.string.form_read_only), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Preview(name = "Preencher formulário", widthDp = 390, heightDp = 780, showBackground = true)
@Preview(name = "Formulário fonte ampliada", widthDp = 320, heightDp = 640, fontScale = 1.5f, locale = "pt-rBR")
@Composable
private fun DocumentFormsPreview() {
    val fields = listOf(
        PdfFormField("name", "Nome completo", PdfFormFieldKind.TEXT, listOf("Maria Silva"), required = true),
        PdfFormField("city", "Cidade", PdfFormFieldKind.TEXT, listOf("Rio Branco")),
        PdfFormField("agree", "Confirmo os dados informados", PdfFormFieldKind.CHECKBOX, emptyList(),
            options = listOf(PdfFormOption("Yes", "Sim"))),
    )
    ScaniqoTheme {
        DocumentFormsScreen(
            DocumentFormsUiState(
                document = PdfFormDocument("preview", "Ficha de cadastro.pdf", fields),
                values = fields.associate { it.id to it.values },
            ), onBack = {}, onPickPdf = {}, onChange = { _, _ -> }, onSave = {},
        )
    }
}
