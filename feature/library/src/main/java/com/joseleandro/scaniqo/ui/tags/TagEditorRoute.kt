package com.joseleandro.scaniqo.ui.tags

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import com.joseleandro.scaniqo.ui.components.ScaniqoLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.joseleandro.scaniqo.feature.library.R
import org.koin.compose.viewmodel.koinViewModel
import com.joseleandro.scaniqo.core.resources.R as CoreResourcesR

/** Uses the editor destination's ViewModel, so Back never replaces the tag list's state. */
@Composable
fun TagEditorRoute(
    tagId: Long?,
    onBack: () -> Unit,
    viewModel: TagsViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(tagId, state.isLoading, state.loadError) {
        viewModel.initializeEditor(tagId)
    }
    LaunchedEffect(state.message) {
        if (state.message == TagsMessage.SAVED) onBack()
    }
    BackHandler(enabled = state.editor?.isSaving == true) {}
    TagEditorContent(tagId = tagId, state = state, onBack = onBack, onEvent = viewModel::onEvent)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TagEditorContent(
    tagId: Long?,
    state: TagsUiState,
    onBack: () -> Unit,
    onEvent: (TagsEvent) -> Unit,
) {
    val editor = state.editor
    if (editor != null) {
        TagEditorScreen(editor = editor, onBack = onBack, onEvent = onEvent)
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(if (tagId == null) R.string.nova_tag else R.string.tag_edit_title)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                painterResource(CoreResourcesR.drawable.arrow_back_24px),
                                contentDescription = stringResource(R.string.icone_de_voltar),
                            )
                        }
                    },
                )
            },
        ) { padding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)
                    .verticalScroll(rememberScrollState()).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            ) {
                if (
                    tagId == null || state.isLoading || state.message == TagsMessage.SAVED ||
                    (!state.loadError && !state.editorTagNotFound)
                ) {
                    ScaniqoLoadingIndicator(Modifier.testTag("tag_editor_loading"))
                } else {
                    Text(
                        stringResource(if (state.loadError) R.string.tags_load_error_description else R.string.tag_unavailable),
                        modifier = Modifier.widthIn(max = 480.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                    )
                    if (state.loadError) {
                        Button(onClick = { onEvent(TagsEvent.RetryLoad) }) {
                            Text(stringResource(R.string.try_again))
                        }
                    }
                }
            }
        }
    }
}
