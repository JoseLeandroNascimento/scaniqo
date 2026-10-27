package com.joseleandro.scaniqo.ui.documentCommon.components

import android.content.res.Configuration
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joseleandro.scaniqo.R
import com.joseleandro.scaniqo.domain.model.DocumentModel
import com.joseleandro.scaniqo.ui.theme.ScaniqoTheme

@Composable
fun DocumentSourceStep(
    toolTitle: String,
    documents: List<DocumentModel>,
    isLoading: Boolean,
    loadError: Boolean,
    enabled: Boolean,
    onSelectDocument: (DocumentModel) -> Unit,
    onPickDevice: () -> Unit,
    onRetryDocuments: () -> Unit,
    modifier: Modifier = Modifier,
    currentDocument: DocumentModel? = null,
    onResume: () -> Unit = {},
) {
    var showLibrary by rememberSaveable { mutableStateOf(false) }
    val compactHero = LocalConfiguration.current.screenHeightDp < 700
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (compactHero) 12.dp else 20.dp),
    ) {
        DocumentSourceHeader(
            toolTitle = toolTitle,
            heading = stringResource(R.string.tool_source_title),
            subtitle = stringResource(R.string.tool_source_subtitle),
            compact = compactHero,
            hero = { SourcePagesIllustration(it) },
        )
        if (currentDocument != null) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        DocumentThumbnail(
                            currentDocument,
                            Modifier.width(36.dp).aspectRatio(1f / 1.4142f)
                                .clip(MaterialTheme.shapes.extraSmall),
                        )
                        Column(Modifier.weight(1f)) {
                            Text(currentDocument.name, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.titleSmall)
                            Text(
                                pluralStringResource(R.plurals.document_page_count,
                                    currentDocument.pageCount, currentDocument.pageCount),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    TextButton(onClick = onResume, enabled = enabled,
                        modifier = Modifier.align(Alignment.End).heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.tool_source_resume))
                    }
                }
            }
        }
        DocumentSourceOrigins(
            enabled = enabled,
            onPickApp = { showLibrary = true },
            onPickDevice = onPickDevice,
        )
    }
    if (showLibrary) {
        DocumentSourceLibrarySheet(
            documents = documents,
            isLoading = isLoading,
            loadError = loadError,
            enabled = enabled,
            onSelectDocument = {
                showLibrary = false
                onSelectDocument(it)
            },
            onPickDevice = {
                showLibrary = false
                onPickDevice()
            },
            onRetryDocuments = onRetryDocuments,
            onDismiss = { showLibrary = false },
        )
    }
}

@Composable
fun DocumentSourceHeader(
    modifier: Modifier = Modifier,
    toolTitle: String,
    heading: String,
    subtitle: String,
    compact: Boolean,
    hero: @Composable (Modifier) -> Unit,
) {
    Column(
        modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 20.dp),
    ) {
        hero(Modifier.size(width = if (compact) 168.dp else 256.dp,
            height = if (compact) 110.dp else 168.dp))
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                toolTitle,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
            )
            Text(
                heading,
                Modifier.semantics { heading() },
                style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
fun DocumentSourceOrigins(
    modifier: Modifier = Modifier,
    enabled: Boolean,
    onPickApp: () -> Unit,
    onPickDevice: () -> Unit,
) {
    val compact = LocalDensity.current.fontScale > 1.3f
    BoxWithConstraints(modifier.fillMaxWidth()) {
        if (maxWidth < 340.dp || compact) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SourceOriginCard(
                    title = stringResource(R.string.merge_source_app),
                    hint = stringResource(R.string.tool_source_app_hint),
                    icon = R.drawable.ic_tag_document,
                    enabled = enabled,
                    compact = true,
                    onClick = onPickApp,
                )
                SourceOriginCard(
                    title = stringResource(R.string.merge_source_device),
                    hint = stringResource(R.string.tool_source_device_hint),
                    icon = R.drawable.ic_document_import,
                    enabled = enabled,
                    compact = true,
                    onClick = onPickDevice,
                )
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SourceOriginCard(
                    title = stringResource(R.string.merge_source_app),
                    hint = stringResource(R.string.tool_source_app_hint),
                    icon = R.drawable.ic_tag_document,
                    enabled = enabled,
                    compact = false,
                    onClick = onPickApp,
                    modifier = Modifier.weight(1f),
                )
                SourceOriginCard(
                    title = stringResource(R.string.merge_source_device),
                    hint = stringResource(R.string.tool_source_device_hint),
                    icon = R.drawable.ic_document_import,
                    enabled = enabled,
                    compact = false,
                    onClick = onPickDevice,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun SourceOriginCard(
    modifier: Modifier = Modifier,
    title: String,
    hint: String,
    @DrawableRes icon: Int,
    enabled: Boolean,
    compact: Boolean,
    onClick: () -> Unit,
) {
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) .975f else 1f, label = "Source card press")
    Surface(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactions,
        modifier = modifier.fillMaxWidth().graphicsLayer { scaleX = scale; scaleY = scale },
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        if (compact) {
            Row(
                Modifier.heightIn(min = 88.dp).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SourceOriginIcon(icon)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(title, style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold)
                    Text(hint, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            Column(
                Modifier.heightIn(min = 136.dp).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SourceOriginIcon(icon)
                Text(title, style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold)
                Text(hint, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SourceOriginIcon(@DrawableRes icon: Int, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.primaryContainer) {
        Icon(
            painterResource(icon), null,
            Modifier.padding(10.dp).size(26.dp),
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
private fun SourcePagesIllustration(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "Source pages")
    val drift by transition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(2400), repeatMode = RepeatMode.Reverse),
        label = "Floating pages",
    )
    Box(modifier.clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(154.dp).background(
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = .55f), CircleShape,
            ),
        )
        SourcePage(
            Modifier.width(92.dp).height(122.dp).graphicsLayer {
                translationX = -48.dp.toPx()
                translationY = 10.dp.toPx() - drift * 5
                rotationZ = -13f + drift
            },
            MaterialTheme.colorScheme.secondaryContainer,
        )
        SourcePage(
            Modifier.width(92.dp).height(122.dp).graphicsLayer {
                translationX = 45.dp.toPx()
                translationY = 9.dp.toPx() + drift * 5
                rotationZ = 12f - drift
            },
            MaterialTheme.colorScheme.tertiaryContainer,
        )
        SourcePage(
            Modifier.width(100.dp).height(134.dp).graphicsLayer {
                translationY = -3.dp.toPx() - drift * 4
                rotationZ = drift * .6f
            },
            MaterialTheme.colorScheme.surface,
        )
    }
}

@Composable
private fun SourcePage(modifier: Modifier = Modifier, accent: Color) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 5.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            Modifier.fillMaxSize().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Box(Modifier.fillMaxWidth().height(20.dp).background(accent, MaterialTheme.shapes.extraSmall))
            Box(Modifier.fillMaxWidth().height(4.dp).background(MaterialTheme.colorScheme.outlineVariant, CircleShape))
            Box(Modifier.fillMaxWidth(.72f).height(4.dp).background(MaterialTheme.colorScheme.outlineVariant, CircleShape))
            Box(Modifier.fillMaxWidth(.86f).height(4.dp).background(MaterialTheme.colorScheme.outlineVariant, CircleShape))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DocumentSourceLibrarySheet(
    documents: List<DocumentModel>,
    isLoading: Boolean,
    loadError: Boolean,
    enabled: Boolean,
    onSelectDocument: (DocumentModel) -> Unit,
    onPickDevice: () -> Unit,
    onRetryDocuments: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val focus = LocalFocusManager.current
    val matches = documents.filter { it.name.contains(query.trim(), ignoreCase = true) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = modifier,
    ) {
        Column(
            Modifier.fillMaxWidth().imePadding().padding(horizontal = 20.dp).padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                stringResource(R.string.merge_app_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
            if (!isLoading && !loadError && documents.isNotEmpty()) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.merge_app_search)) },
                    leadingIcon = {
                        Icon(painterResource(R.drawable.ic_document_search), null, Modifier.size(20.dp))
                    },
                    trailingIcon = {
                        if (query.isNotEmpty()) IconButton(onClick = { query = "" }) {
                            Icon(painterResource(R.drawable.ic_toast_close),
                                stringResource(R.string.tool_catalog_clear))
                        }
                    },
                    singleLine = true,
                    shape = MaterialTheme.shapes.large,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                )
            }
            when {
                isLoading -> Box(Modifier.fillMaxWidth().heightIn(min = 112.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                loadError -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.documents_error_title),
                        style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = onRetryDocuments, enabled = enabled) {
                        Text(stringResource(R.string.documents_retry))
                    }
                }
                documents.isEmpty() -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.tool_source_library_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(onClick = onPickDevice, enabled = enabled) {
                        Text(stringResource(R.string.merge_source_device))
                    }
                }
                matches.isEmpty() -> Text(
                    stringResource(R.string.merge_app_no_results),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> LazyColumn(
                    Modifier.fillMaxWidth().heightIn(max = 440.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(matches, key = { it.id }) { document ->
                        Surface(
                            onClick = { onSelectDocument(document) },
                            enabled = enabled,
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                        ) {
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 68.dp)
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                DocumentThumbnail(
                                    document,
                                    Modifier.width(44.dp).aspectRatio(1f / 1.4142f)
                                        .clip(MaterialTheme.shapes.extraSmall),
                                )
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text(
                                        document.name,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            textDirection = TextDirection.ContentOrLtr,
                                        ),
                                    )
                                    Text(
                                        pluralStringResource(R.plurals.document_page_count,
                                            document.pageCount, document.pageCount),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview(name = "Origem PDF claro", widthDp = 390, heightDp = 844, locale = "pt-rBR")
@Preview(name = "Origem PDF escuro", widthDp = 390, heightDp = 844,
    locale = "pt-rBR", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Origem PDF texto ampliado", widthDp = 320, heightDp = 640,
    locale = "pt-rBR", fontScale = 1.5f)
@Composable
private fun DocumentSourceStepPreview() {
    ScaniqoTheme {
        DocumentSourceStep(
            toolTitle = stringResource(R.string.tool_catalog_compress),
            documents = emptyList(),
            isLoading = false,
            loadError = false,
            enabled = true,
            onSelectDocument = {},
            onPickDevice = {},
            onRetryDocuments = {},
        )
    }
}
