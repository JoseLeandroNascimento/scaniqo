package com.joseleandro.scaniqo.ui.documentSearch

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joseleandro.scaniqo.R
import com.joseleandro.scaniqo.core.localization.bidiArgument
import com.joseleandro.scaniqo.core.utils.documentFileSize
import com.joseleandro.scaniqo.domain.model.DocumentModel
import com.joseleandro.scaniqo.domain.model.DocumentSearchResult
import com.joseleandro.scaniqo.domain.repository.DocumentSearchRepository
import com.joseleandro.scaniqo.ui.documentCommon.components.DocumentThumbnail
import com.joseleandro.scaniqo.ui.documentSearch.state.DocumentSearchUiState
import com.joseleandro.scaniqo.ui.theme.ScaniqoTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentSearchScreen(
    uiState: DocumentSearchUiState,
    onQueryChanged: (String) -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onOpenDocument: (documentId: Long, pageNumber: Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val inspectionMode = LocalInspectionMode.current
    val searchLabel = stringResource(R.string.document_search_query_label)

    LaunchedEffect(Unit) {
        if (!inspectionMode && uiState.query.isBlank()) focusRequester.requestFocus()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        TextField(
                            value = uiState.query,
                            onValueChange = onQueryChanged,
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester)
                                .semantics { contentDescription = searchLabel },
                            placeholder = {
                                Text(
                                    text = stringResource(R.string.document_search_title),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            textStyle = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.ContentOrLtr),
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                            ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = {
                                focusManager.clearFocus()
                                keyboard?.hide()
                            }),
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            focusManager.clearFocus()
                            keyboard?.hide()
                            onBack()
                        }) {
                            Icon(
                                painterResource(R.drawable.arrow_back_24px),
                                contentDescription = stringResource(R.string.document_back),
                            )
                        }
                    },
                    actions = {
                        if (uiState.query.isNotEmpty()) {
                            IconButton(onClick = {
                                onQueryChanged("")
                                focusRequester.requestFocus()
                                keyboard?.show()
                            }) {
                                Icon(
                                    painterResource(R.drawable.ic_toast_close),
                                    contentDescription = stringResource(R.string.document_search_clear),
                                )
                            }
                        }
                    },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .imePadding(),
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
            ) {
                when {
                    uiState.loadError -> item(key = "error") {
                        SearchStatus(
                            title = stringResource(R.string.document_search_error_title),
                            description = stringResource(R.string.document_search_error_body),
                            actionLabel = stringResource(R.string.documents_retry),
                            onAction = onRetry,
                        )
                    }

                    uiState.query.isBlank() -> item(key = "initial") {
                        SearchStatus(
                            title = stringResource(R.string.document_search_initial_title),
                            description = stringResource(R.string.document_search_initial_body),
                        )
                    }

                    uiState.isSearching && uiState.results.isEmpty() -> item(key = "loading") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(32.dp), strokeWidth = 3.dp)
                            Text(
                                text = stringResource(R.string.document_search_loading),
                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    uiState.results.isEmpty() -> item(key = "empty") {
                        SearchStatus(
                            title = stringResource(R.string.document_search_empty_title),
                            description = stringResource(R.string.document_search_empty_body),
                        )
                    }

                    else -> {
                        if (uiState.isSearching) item(key = "search_progress") {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                Text(
                                    text = stringResource(R.string.document_search_loading),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (uiState.resultLimitReached) item(key = "result_limit") {
                            Text(
                                text = stringResource(
                                    R.string.document_search_result_limit,
                                    DocumentSearchRepository.MAX_RESULTS,
                                ),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        item(key = "count") {
                            Text(
                                text = pluralStringResource(
                                    R.plurals.document_search_result_count,
                                    uiState.results.size,
                                    uiState.results.size,
                                ),
                                modifier = Modifier
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                                    .semantics { liveRegion = LiveRegionMode.Polite },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        items(uiState.results, key = { it.document.id }) { result ->
                            SearchResultRow(
                                result = result,
                                query = uiState.query,
                                onOpen = {
                                    focusManager.clearFocus()
                                    keyboard?.hide()
                                    onOpenDocument(result.document.id, result.pageNumber)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultRow(result: DocumentSearchResult, query: String, onOpen: () -> Unit) {
    val document = result.document
    val highlightStyle = SpanStyle(
        background = MaterialTheme.colorScheme.primaryContainer,
        color = MaterialTheme.colorScheme.onPrimaryContainer,
        fontWeight = FontWeight.SemiBold,
    )
    val metadata = stringResource(
        R.string.document_search_metadata,
        pluralStringResource(R.plurals.document_page_count, document.pageCount, document.pageCount),
        bidiArgument(documentFileSize(document)),
    )
    val contentMatch = when {
        result.matchesName -> null
        result.pageNumber != null -> stringResource(
            R.string.document_search_match_page,
            result.pageNumber,
        )
        else -> stringResource(R.string.document_search_match_content)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 80.dp)
            .clickable(onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        DocumentThumbnail(
            document = document,
            modifier = Modifier
                .width(40.dp)
                .aspectRatio(595f / 842f)
                .clip(MaterialTheme.shapes.extraSmall),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = remember(document.name, query, highlightStyle) {
                    highlightSearchMatches(document.name, query, highlightStyle)
                },
                style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.ContentOrLtr),
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (contentMatch == null) metadata else {
                    stringResource(R.string.document_search_metadata, metadata, contentMatch)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!result.contentSnippet.isNullOrBlank()) {
                Text(
                    text = remember(result.contentSnippet, query, highlightStyle) {
                        highlightSearchMatches(result.contentSnippet, query, highlightStyle)
                    },
                    style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.ContentOrLtr),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SearchStatus(
    title: String,
    description: String,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 32.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null) {
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Preview(name = "Pesquisa inicial", showBackground = true)
@Composable
private fun DocumentSearchInitialPreview() {
    ScaniqoTheme {
        DocumentSearchScreen(
            uiState = DocumentSearchUiState(),
            onQueryChanged = {},
            onRetry = {},
            onBack = {},
            onOpenDocument = { _, _ -> },
        )
    }
}

@Preview(name = "Resultados claro", showBackground = true)
@Preview(
    name = "Resultados escuro",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun DocumentSearchResultsPreview() {
    val document = DocumentModel(
        id = 1,
        name = "Contrato de prestação de serviços",
        pdfUri = "preview.pdf",
        pageCount = 3,
        createdAt = 1781431200000L,
        sizeBytes = 248_000L,
    )
    ScaniqoTheme {
        DocumentSearchScreen(
            uiState = DocumentSearchUiState(
                query = "serviços",
                results = listOf(
                    DocumentSearchResult(document = document, matchesName = true),
                    DocumentSearchResult(
                        document = document.copy(id = 2, name = "Proposta comercial"),
                        matchesName = false,
                        contentSnippet = "A prestação dos serviços terá início após a aprovação desta proposta.",
                        pageNumber = 2,
                    ),
                ),
            ),
            onQueryChanged = {},
            onRetry = {},
            onBack = {},
            onOpenDocument = { _, _ -> },
        )
    }
}
