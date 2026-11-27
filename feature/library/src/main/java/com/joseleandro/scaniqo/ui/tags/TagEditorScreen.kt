package com.joseleandro.scaniqo.ui.tags

import com.joseleandro.scaniqo.core.resources.R as CoreResourcesR
import com.joseleandro.scaniqo.feature.library.R

import com.joseleandro.scaniqo.ui.components.ScaniqoCardDefaults
import com.joseleandro.scaniqo.ui.components.ScaniqoFieldDefaults
import com.joseleandro.scaniqo.ui.components.ScaniqoColorOption
import com.joseleandro.scaniqo.ui.components.ScaniqoIconOption
import com.joseleandro.scaniqo.ui.components.ScaniqoOptionGrid
import com.joseleandro.scaniqo.ui.components.drawableRes

import com.joseleandro.scaniqo.ui.components.labelRes

import com.joseleandro.scaniqo.ui.components.color

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joseleandro.scaniqo.domain.model.TAG_NAME_MAX_LENGTH
import com.joseleandro.scaniqo.domain.model.TagColor
import com.joseleandro.scaniqo.domain.model.TagIcon
import com.joseleandro.scaniqo.ui.theme.ScaniqoTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TagEditorScreen(
    editor: TagEditorUiState,
    onBack: () -> Unit,
    onEvent: (TagsEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val originalColorArgb = rememberSaveable(editor.id) { editor.color.toArgb() }
    val originalCustomColor = originalColorArgb
        .takeIf { argb -> TagColor.entries.none { it.argb == argb } }
        ?.let(::Color)
    val formState = rememberLazyListState()
    LaunchedEffect(editor.nameError, editor.saveError) {
        when {
            editor.saveError -> formState.animateScrollToItem(0)
            editor.nameError != null -> formState.animateScrollToItem(1)
        }
    }
    val nameErrorText = when (editor.nameError) {
        TagNameError.REQUIRED -> stringResource(R.string.tag_name_required)
        TagNameError.TOO_LONG -> stringResource(R.string.tag_name_too_long, TAG_NAME_MAX_LENGTH)
        null -> null
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(if (editor.id == null) R.string.nova_tag else R.string.tag_edit_title))
                },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !editor.isSaving) {
                        Icon(
                            painterResource(CoreResourcesR.drawable.arrow_back_24px),
                            contentDescription = stringResource(R.string.icone_de_voltar),
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            keyboardController?.hide()
                            onEvent(TagsEvent.SaveTag)
                        },
                        enabled = !editor.isSaving,
                        modifier = Modifier.testTag("tag_editor_save"),
                    ) {
                        if (editor.isSaving) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(stringResource(if (editor.isSaving) R.string.tag_saving else R.string.tag_editor_save))
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding(),
            contentAlignment = Alignment.TopCenter,
        ) {
            // O formulário inteiro usa uma lista; as grades não têm rolagem própria.
            LazyColumn(
                state = formState,
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxSize()
                    .testTag("tag_editor_form"),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                if (editor.saveError) {
                    item(key = "save_error") {
                        Text(
                            text = stringResource(R.string.tag_save_error),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    }
                }
                item(key = "preview") {
                    TagEditorPreviewCard(editor)
                }
                item(key = "name") {
                    OutlinedTextField(
                        value = editor.name,
                        onValueChange = { onEvent(TagsEvent.NameChanged(it)) },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.ContentOrLtr),
                        modifier = Modifier.fillMaxWidth().testTag("tag_editor_name"),
                        label = { Text(stringResource(R.string.tag_name_label)) },
                        placeholder = { Text(stringResource(R.string.tag_name_placeholder)) },
                        enabled = !editor.isSaving,
                        singleLine = true,
                        isError = editor.nameError != null,
                        shape = ScaniqoFieldDefaults.shape,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { keyboardController?.hide() },
                        ),
                        supportingText = {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                if (nameErrorText != null) {
                                    Text(nameErrorText)
                                }
                                Text(
                                    text = stringResource(
                                        R.string.tag_name_counter,
                                        editor.name.length,
                                        TAG_NAME_MAX_LENGTH
                                    ),
                                    modifier = Modifier.align(Alignment.End),
                                )
                            }
                        },
                    )
                }
                item(key = "icons") {
                    TagOptionSection(
                        title = stringResource(R.string.tag_icon_title),
                        selectionLabel = stringResource(TagIcon.fromKey(editor.iconKey).labelRes),
                    ) {
                        ScaniqoOptionGrid {
                            TagIcon.entries.forEach { icon ->
                                ScaniqoIconOption(
                                    painter = painterResource(icon.drawableRes),
                                    label = stringResource(icon.labelRes),
                                    selected = icon.key == editor.iconKey,
                                    enabled = !editor.isSaving,
                                    onClick = { onEvent(TagsEvent.IconChanged(icon)) },
                                )
                            }
                        }
                    }
                }
                item(key = "colors") {
                    TagOptionSection(
                        title = stringResource(R.string.tag_color_title),
                        selectionLabel = TagColor.entries.firstOrNull { it.color == editor.color }
                            ?.let { stringResource(it.labelRes) }
                            ?: originalCustomColor?.takeIf { it == editor.color }
                                ?.let { stringResource(R.string.tag_original_color) },
                    ) {
                        ScaniqoOptionGrid {
                            TagColor.entries.forEach { option ->
                                ScaniqoColorOption(
                                    color = option.color,
                                    label = stringResource(option.labelRes),
                                    selected = option.color == editor.color,
                                    enabled = !editor.isSaving,
                                    onClick = { onEvent(TagsEvent.ColorChanged(option.color)) },
                                )
                            }
                            originalCustomColor?.let { color ->
                                ScaniqoColorOption(
                                    color = color,
                                    label = stringResource(R.string.tag_original_color),
                                    selected = color == editor.color,
                                    enabled = !editor.isSaving,
                                    onClick = { onEvent(TagsEvent.ColorChanged(color)) },
                                )
                            }
                        }
                    }
                }
            }

        }
    }
}

@Composable
private fun TagEditorPreviewCard(editor: TagEditorUiState) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = ScaniqoCardDefaults.containerColor,
        shape = ScaniqoCardDefaults.shape,
        border = ScaniqoCardDefaults.border,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.tag_preview_label),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TagIconBadge(icon = TagIcon.fromKey(editor.iconKey), color = editor.color, size = 56.dp)
            Text(
                text = editor.name.trim().ifBlank { stringResource(R.string.tag_preview_name) },
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.titleMedium.copy(textDirection = TextDirection.ContentOrLtr),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun TagOptionSection(
    title: String,
    selectionLabel: String?,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                title,
                modifier = Modifier.align(Alignment.CenterVertically).semantics { heading() },
                style = MaterialTheme.typography.titleMedium,
            )
            if (selectionLabel != null) {
                Text(
                    selectionLabel,
                    modifier = Modifier.align(Alignment.CenterVertically),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        content()
    }
}

@Preview(name = "Editor claro", widthDp = 390, heightDp = 800, locale = "pt-rBR")
@Preview(name = "Editor escuro", widthDp = 390, heightDp = 800, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Editor tablet", widthDp = 1280, heightDp = 800, locale = "pt-rBR")
@Preview(name = "Editor fonte ampliada", widthDp = 320, heightDp = 640, fontScale = 2f, locale = "pt-rBR")
@Composable
private fun TagEditorPreview() {
    ScaniqoTheme {
        Surface {
            TagEditorScreen(
                editor = TagEditorUiState(
                    id = null,
                    name = "Comprovantes",
                    iconKey = TagIcon.DOCUMENT.key,
                    color = Color(0xFF2B6F91),
                ),
                onBack = {},
                onEvent = {},
            )
        }
    }
}
