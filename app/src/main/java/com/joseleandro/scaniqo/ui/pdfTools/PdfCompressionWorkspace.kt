package com.joseleandro.scaniqo.ui.pdfTools

import android.content.res.Configuration
import android.text.format.Formatter
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joseleandro.scaniqo.R
import com.joseleandro.scaniqo.ui.ads.ToolAdActionContent
import com.joseleandro.scaniqo.core.localization.localizedRuntimeMessage
import com.joseleandro.scaniqo.domain.model.DocumentModel
import com.joseleandro.scaniqo.domain.model.PdfCompressionLevel
import com.joseleandro.scaniqo.ui.documentCommon.components.DocumentThumbnail
import com.joseleandro.scaniqo.ui.theme.ScaniqoTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PdfCompressionWorkspace(
    state: PdfUtilityUiState,
    document: DocumentModel?,
    password: String,
    compression: PdfCompressionLevel,
    externalPicker: Boolean,
    onBack: () -> Unit,
    onChooseDocument: () -> Unit,
    onPassword: (String) -> Unit,
    onCompression: (PdfCompressionLevel) -> Unit,
    onAction: () -> Unit,
    onRetry: () -> Unit,
    onOpenDocument: (Long) -> Unit,
    modifier: Modifier = Modifier,
    showsVideo: Boolean = false,
    actionInProgress: Boolean = false,
) {
    val awaitingSave = state.awaitingSaveOperationId != null
    val enabled = !state.busy && !externalPicker && !awaitingSave && !actionInProgress
    val result = state.compressionResult
    val completedId = result?.documentId
    val ready = enabled && document != null &&
        (!document.isProtected || password.isNotEmpty())
    val scroll = rememberScrollState()
    LaunchedEffect(state.busy, result, awaitingSave) {
        if (state.busy || result != null || awaitingSave) scroll.animateScrollTo(0)
    }
    Scaffold(
        modifier = modifier.imePadding(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.utility_workspace_compress_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !state.busy && !externalPicker && !awaitingSave) {
                        Icon(painterResource(R.drawable.arrow_back_24px), stringResource(R.string.utility_back))
                    }
                },
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface) {
                Column(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = { if (completedId != null) onOpenDocument(completedId) else onAction() },
                        enabled = if (completedId != null) enabled else ready,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    ) {
                        ToolAdActionContent(
                            isVideo = showsVideo && completedId == null,
                            label = stringResource(when {
                                completedId != null -> R.string.utility_workspace_open_result
                                state.busy || actionInProgress -> R.string.utility_workspace_busy
                                awaitingSave -> R.string.utility_output_finish_in_sheet
                                showsVideo -> R.string.tool_ad_watch_apply
                                else -> R.string.compression_action
                            }),
                        )
                    }
                    Text(stringResource(if (showsVideo && completedId == null)
                        R.string.tool_ad_reward_hint else R.string.compression_original_kept),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center)
                }
            }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)
                .verticalScroll(scroll).padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            CompressionHero(state.busy, awaitingSave, result, Modifier.fillMaxWidth())
            if (state.busy || state.loading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            if (state.loadError) {
                CompressionError(stringResource(R.string.utility_library_error))
                TextButton(onClick = onRetry, enabled = enabled) {
                    Text(stringResource(R.string.documents_retry))
                }
            }
            state.error?.let { CompressionError(localizedRuntimeMessage(it)) }
            if (result != null) CompressionSizes(result)
            CompressionSource(document, enabled, onChooseDocument)
            if (!state.busy && !awaitingSave) {
                if (completedId != null) {
                    TextButton(onClick = onChooseDocument, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.compression_choose_another))
                    }
                } else {
                    if (document != null) {
                        if (document.isProtected) {
                            OutlinedTextField(
                                value = password, onValueChange = onPassword,
                                modifier = Modifier.fillMaxWidth(), enabled = enabled, singleLine = true,
                                label = { Text(stringResource(R.string.utility_password)) },
                                leadingIcon = { Icon(painterResource(R.drawable.ic_document_lock), null) },
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(stringResource(R.string.compression_level), style = MaterialTheme.typography.titleSmall)
                            Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                PdfCompressionLevel.entries.forEach { level ->
                                    CompressionLevelChoice(level, compression == level, enabled,
                                        { onCompression(level) }, Modifier.weight(1f))
                                }
                            }
                            if (compression == PdfCompressionLevel.STRONG) {
                                Text(
                                    stringResource(R.string.strong_compression_reward_quality_note),
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

@Composable
private fun CompressionHero(
    busy: Boolean,
    awaitingSave: Boolean,
    result: PdfCompressionSummary?,
    modifier: Modifier = Modifier,
) {
    val complete = result?.documentId != null
    val saved = if (result != null && result.originalBytes > 0) {
        ((1.0 - result.resultBytes.toDouble() / result.originalBytes) * 100).toInt().coerceIn(0, 100)
    } else 0
    Column(
        modifier.semantics { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
            CompressionIllustration(busy, Modifier.size(216.dp, 152.dp))
            if (complete) Surface(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp),
                shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.ic_check_24), null, Modifier.size(16.dp))
                    Text(stringResource(R.string.compression_hero_complete), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        Text(
            text = if (complete) {
                if (saved == 0) stringResource(R.string.compression_saved_small)
                else stringResource(R.string.compression_saved, saved)
            } else stringResource(when {
                busy -> R.string.compression_hero_busy
                awaitingSave -> R.string.utility_output_ready_to_save
                result != null -> R.string.compression_no_gain
                else -> R.string.compression_hero_ready
            }),
            style = if (complete) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        if (awaitingSave) {
            Text(stringResource(R.string.utility_output_finish_in_sheet), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        } else if (result != null && !complete) {
            Text(stringResource(R.string.compression_no_gain_detail), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun CompressionIllustration(busy: Boolean, modifier: Modifier = Modifier) {
    val compression = if (busy) {
        val transition = rememberInfiniteTransition(label = "PDF compression")
        val amount by transition.animateFloat(
            initialValue = 0f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "Pages contracting",
        )
        amount
    } else 0.35f
    val colors = MaterialTheme.colorScheme
    Canvas(modifier) {
        val unit = size.width / 216f
        val cx = size.width / 2
        val cy = size.height / 2
        drawCircle(colors.primaryContainer.copy(alpha = 0.45f), radius = 70 * unit, center = Offset(cx, cy))
        val gap = (13 - compression * 9) * unit
        val width = (78 - compression * 7) * unit
        val height = (94 - compression * 8) * unit
        for (index in 2 downTo 0) {
            val left = cx - width / 2 + index * 5 * unit
            val top = cy - height / 2 + (index - 1) * gap
            drawRoundRect(
                color = if (index == 0) colors.surfaceContainerLowest else colors.primaryContainer,
                topLeft = Offset(left, top), size = Size(width, height),
                cornerRadius = CornerRadius(10 * unit),
            )
            drawRoundRect(
                color = colors.primary.copy(alpha = if (index == 0) 0.6f else 0.25f),
                topLeft = Offset(left, top), size = Size(width, height),
                cornerRadius = CornerRadius(10 * unit), style = Stroke(1.5f * unit),
            )
            if (index == 0) {
                drawRoundRect(colors.primary, Offset(left + 14 * unit, top + 19 * unit),
                    Size(25 * unit, 5 * unit), CornerRadius(2.5f * unit))
                repeat(4) { line ->
                    drawLine(colors.outlineVariant, Offset(left + 14 * unit, top + (36 + line * 10) * unit),
                        Offset(left + (if (line == 3) 42 else 62) * unit, top + (36 + line * 10) * unit),
                        strokeWidth = 2.5f * unit, cap = StrokeCap.Round)
                }
            }
        }
        val arrowGap = (70 - compression * 12) * unit
        for (side in listOf(-1, 1)) {
            val tip = cx + side * arrowGap
            val end = tip + side * 16 * unit
            drawLine(colors.primary, Offset(end, cy), Offset(tip, cy), 3 * unit, StrokeCap.Round)
            drawLine(colors.primary, Offset(tip + side * 6 * unit, cy - 6 * unit),
                Offset(tip, cy), 3 * unit, StrokeCap.Round)
            drawLine(colors.primary, Offset(tip + side * 6 * unit, cy + 6 * unit),
                Offset(tip, cy), 3 * unit, StrokeCap.Round)
        }
    }
}

@Composable
private fun CompressionSource(
    document: DocumentModel?,
    enabled: Boolean,
    onChoose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onChoose, enabled = enabled, modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (document != null) {
                DocumentThumbnail(document, Modifier.width(40.dp).aspectRatio(595f / 842f).clip(MaterialTheme.shapes.small))
            } else {
                Icon(painterResource(R.drawable.ic_tag_document), null,
                    Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(document?.name ?: stringResource(R.string.utility_select_pdf),
                    style = MaterialTheme.typography.titleSmall.copy(textDirection = TextDirection.ContentOrLtr),
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(if (document == null) stringResource(R.string.compression_from_library) else
                    stringResource(R.string.utility_source_pages, document.pageCount) + " · " +
                        Formatter.formatShortFileSize(LocalContext.current, document.sizeBytes),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (enabled) Icon(
                painterResource(if (document == null) R.drawable.add_24px else R.drawable.edit_24px),
                stringResource(R.string.utility_change_pdf), Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun CompressionLevelChoice(
    level: PdfCompressionLevel,
    selected: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val balanced = level == PdfCompressionLevel.BALANCED
    Surface(
        modifier = modifier.selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = onSelect),
        shape = MaterialTheme.shapes.large,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            RadioButton(selected, onClick = null, enabled = enabled, modifier = Modifier.size(24.dp))
            Text(stringResource(if (balanced) R.string.compression_balanced else R.string.compression_strong),
                style = MaterialTheme.typography.titleSmall)
            Text(stringResource(if (balanced) R.string.compression_balanced_detail else R.string.compression_strong_detail),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CompressionSizes(result: PdfCompressionSummary, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.padding(20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.compression_size_before), style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(Formatter.formatShortFileSize(context, result.originalBytes), style = MaterialTheme.typography.titleLarge)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.compression_size_after), style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(Formatter.formatShortFileSize(context, result.resultBytes), style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun CompressionError(message: String, modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.errorContainer) {
        Text(message, Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer)
    }
}

@Preview(name = "Compressão em andamento", widthDp = 390, heightDp = 844, locale = "pt-rBR")
@Composable
private fun CompressionBusyPreview() = CompressionStatePreview(PdfUtilityUiState(loading = false, busy = true))

@Preview(name = "Compressão aguardando salvamento", widthDp = 390, heightDp = 844, locale = "pt-rBR")
@Composable
private fun CompressionAwaitingSavePreview() = CompressionStatePreview(
    PdfUtilityUiState(
        loading = false,
        awaitingSaveOperationId = "preview:1",
        compressionResult = PdfCompressionSummary(1_250_000, 680_000, null),
    ),
)

@Preview(name = "PDF comprimido", widthDp = 390, heightDp = 844, locale = "pt-rBR")
@Preview(name = "PDF comprimido escuro", widthDp = 390, heightDp = 844, locale = "pt-rBR",
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CompressionCompletePreview() = CompressionStatePreview(
    PdfUtilityUiState(loading = false, compressionResult = PdfCompressionSummary(1_250_000, 680_000, 2)),
)

@Preview(name = "Compressão sem redução", widthDp = 320, heightDp = 740, locale = "pt-rBR", fontScale = 1.5f)
@Composable
private fun CompressionNoGainPreview() = CompressionStatePreview(
    PdfUtilityUiState(loading = false, compressionResult = PdfCompressionSummary(1_250_000, 1_250_000, null)),
)

@Composable
private fun CompressionStatePreview(state: PdfUtilityUiState, modifier: Modifier = Modifier) {
    ScaniqoTheme {
        PdfCompressionWorkspace(
            state = state, document = DocumentModel(1, "Contrato de prestação de serviços", "preview", 6, 0, 1_250_000),
            password = "", compression = PdfCompressionLevel.BALANCED,
            externalPicker = false, onBack = {}, onChooseDocument = {},
            onPassword = {}, onCompression = {}, onAction = {}, onRetry = {}, onOpenDocument = {}, modifier = modifier,
        )
    }
}
