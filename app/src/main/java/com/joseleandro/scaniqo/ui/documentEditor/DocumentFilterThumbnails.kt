package com.joseleandro.scaniqo.ui.documentEditor

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.joseleandro.scaniqo.R
import com.joseleandro.scaniqo.domain.model.PdfImageFilter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

internal val documentFilterChoices = listOf(
    PdfImageFilter.ORIGINAL, PdfImageFilter.ENHANCE,
    PdfImageFilter.SHADOW_REMOVAL, PdfImageFilter.ILLUMINATION,
    PdfImageFilter.WHITEN, PdfImageFilter.ADAPTIVE_BLACK_WHITE,
    PdfImageFilter.DENOISE, PdfImageFilter.SHARPEN,
    PdfImageFilter.BLEED_THROUGH, PdfImageFilter.AUTO,
    PdfImageFilter.GRAYSCALE, PdfImageFilter.BLACK_WHITE,
)

/** A faixa virtualizada só decodifica as miniaturas visíveis, sem abrir novamente o PDF. */
@Composable
internal fun DocumentFilterThumbnails(
    thumbnails: Map<PdfImageFilter, String>,
    selectedFilter: PdfImageFilter?,
    loading: Boolean,
    enabled: Boolean,
    onSelect: (PdfImageFilter) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    fadeColor: Color? = null,
    listState: LazyListState = rememberLazyListState(),
) {
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    LazyRow(
        state = listState,
        modifier = modifier.fillMaxWidth().selectableGroup().drawWithContent {
            drawContent()
            // O desvanecimento só aparece na direção em que ainda há opções fora da tela.
            if (fadeColor != null) {
                val fadeWidth = 20.dp.toPx().coerceAtMost(size.width / 2f)
                val fadeLeft = if (isRtl) listState.canScrollForward else listState.canScrollBackward
                val fadeRight = if (isRtl) listState.canScrollBackward else listState.canScrollForward
                if (fadeLeft) drawRect(
                    brush = Brush.horizontalGradient(
                        listOf(fadeColor, fadeColor.copy(alpha = 0f)), endX = fadeWidth,
                    ),
                    size = Size(fadeWidth, size.height),
                )
                if (fadeRight) drawRect(
                    brush = Brush.horizontalGradient(
                        listOf(fadeColor.copy(alpha = 0f), fadeColor),
                        startX = size.width - fadeWidth, endX = size.width,
                    ),
                    topLeft = Offset(size.width - fadeWidth, 0f),
                    size = Size(fadeWidth, size.height),
                )
            }
        },
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 12.dp),
    ) {
        items(documentFilterChoices, key = { it }) { filter ->
            val selected = filter == selectedFilter
            Surface(
                modifier = Modifier
                    .width(if (compact) 108.dp else 124.dp)
                    .clip(shape = MaterialTheme.shapes.medium)
                    .selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = { onSelect(filter) }),
                shape = MaterialTheme.shapes.medium,
                color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                border = BorderStroke(
                    if (selected) 2.dp else .5.dp,
                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                ),
            ) {
                Column(Modifier.padding(if (compact) 6.dp else 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.fillMaxWidth().height(if (compact) 52.dp else 94.dp)) {
                        FilterThumbnailImage(thumbnails[filter], loading, Modifier.fillMaxSize())
                        if (selected) {
                            Surface(
                                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(22.dp),
                                color = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                shape = CircleShape,
                            ) {
                                Icon(painterResource(R.drawable.ic_check_24), null, Modifier.padding(3.dp))
                            }
                        }
                    }
                    Box(
                        // O card inteiro recebe o toque; o rótulo não precisa reservar outro alvo de 48 dp.
                        modifier = Modifier.fillMaxWidth()
                            .heightIn(min = if (compact) 36.dp else 48.dp)
                            .padding(top = if (compact) 4.dp else 8.dp, bottom = if (compact) 0.dp else 4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            stringResource(filter.titleResource()),
                            style = MaterialTheme.typography.labelSmall,
                            textAlign = TextAlign.Center,
                            minLines = if (compact) 2 else 1,
                        )
                    }
                }
            }
        }
    }
}

private sealed interface FilterImageState {
    data object Loading : FilterImageState
    data class Ready(val bitmap: Bitmap) : FilterImageState
    data object Error : FilterImageState
}

@Composable
private fun FilterThumbnailImage(uri: String?, loading: Boolean, modifier: Modifier) {
    if (LocalInspectionMode.current) {
        // A imagem esquemática existe apenas no editor; em execução mostramos o PNG do filtro real.
        DocumentPageThumbnail("preview", 0, modifier)
        return
    }
    val context = LocalContext.current.applicationContext
    val imageState by produceState<FilterImageState>(FilterImageState.Loading, uri) {
        value = FilterImageState.Loading
        if (uri == null) return@produceState
        var decoded: Bitmap? = null
        try {
            withContext(Dispatchers.IO) {
                decoded = context.contentResolver.openInputStream(uri.toUri())?.use { BitmapFactory.decodeStream(it) }
                ensureActive()
            }
            value = decoded?.let(FilterImageState::Ready) ?: FilterImageState.Error
        } catch (cancelled: CancellationException) {
            decoded?.recycle()
            throw cancelled
        } catch (_: Exception) {
            decoded?.recycle()
            value = FilterImageState.Error
        }
        // O bitmap entregue pertence ao Compose e será liberado quando sair da composição.
    }
    Surface(modifier, color = Color.White, shape = MaterialTheme.shapes.small) {
        Box(contentAlignment = Alignment.Center) {
            when (val image = imageState) {
                is FilterImageState.Ready -> Image(
                    bitmap = image.bitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
                FilterImageState.Loading -> if (uri != null || loading) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    ThumbnailUnavailable()
                }
                FilterImageState.Error -> ThumbnailUnavailable()
            }
        }
    }
}

@Composable
private fun ThumbnailUnavailable() {
    Text(
        stringResource(R.string.filter_preview_thumbnail_error),
        Modifier.padding(8.dp),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}
