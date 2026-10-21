package com.joseleandro.scaniqo.ui.documentCommon.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.joseleandro.scaniqo.R
import com.joseleandro.scaniqo.domain.model.TagIcon
import com.joseleandro.scaniqo.domain.model.TagModel
import com.joseleandro.scaniqo.ui.theme.ScaniqoTheme

/** Compartilha as opções de tags e o efeito das bordas entre a lista e o formulário. */
@Composable
internal fun DocumentTagSelector(
    tags: List<TagModel>,
    selectedTagId: Long?,
    nullOptionLabel: String,
    onSelect: (Long?) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    background: Color = MaterialTheme.colorScheme.background,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp),
    listState: LazyListState = rememberLazyListState(),
) {
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val filterChipShade = MaterialTheme.shapes.large;

    LazyRow(
        state = listState,
        userScrollEnabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .drawWithContent {
                drawContent()
                // O espaçamento fica no conteúdo para que a rolagem alcance a borda da tela.
                val fadeWidth = 24.dp.toPx()
                val fadeLeft =
                    if (isLtr) listState.canScrollBackward else listState.canScrollForward
                val fadeRight =
                    if (isLtr) listState.canScrollForward else listState.canScrollBackward
                if (fadeLeft) {
                    drawRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(background, Color.Transparent),
                            endX = fadeWidth,
                        ),
                        size = Size(fadeWidth, size.height),
                    )
                }
                if (fadeRight) {
                    drawRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(Color.Transparent, background),
                            startX = size.width - fadeWidth,
                            endX = size.width,
                        ),
                        topLeft = Offset(size.width - fadeWidth, 0f),
                        size = Size(fadeWidth, size.height),
                    )
                }
            },
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "no_selection") {
            FilterChip(
                selected = selectedTagId == null,
                enabled = enabled,
                shape = filterChipShade,
                onClick = { onSelect(null) },
                modifier = Modifier.heightIn(min = 48.dp),
                label = { Text(nullOptionLabel) },
                leadingIcon = if (selectedTagId == null) {
                    { Icon(painterResource(R.drawable.ic_check_24), null, Modifier.size(18.dp)) }
                } else null,
            )
        }
        items(tags, key = { it.id }) { tag ->
            FilterChip(
                selected = selectedTagId == tag.id,
                enabled = enabled,
                shape = filterChipShade,
                onClick = { onSelect(tag.id) },
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .widthIn(max = 240.dp),
                label = {
                    Text(tag.name, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelLarge.copy(textDirection = TextDirection.ContentOrLtr))
                },
                leadingIcon = { DocumentTagIcon(tag) },
            )
        }
    }
}

private val TAGS_PREVIEW = listOf(
    TagModel(id = 1L, name = "Documento", color = Color(0xFF4F7CAC), iconKey = TagIcon.FOLDER.key),
    TagModel(id = 2L, name = "Arquivo", color = Color(0xFF4F7CAC), iconKey = TagIcon.DOCUMENT.key),
    TagModel(id = 3L, name = "Extrato", color = Color(0xFF4F7CAC), iconKey = TagIcon.TRAVEL.key),
    TagModel(id = 4L, name = "Comprovante", color = Color(0xFF4F7CAC), iconKey = TagIcon.KEY.key),
)

@Preview(name = "light", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DocumentTagSelectorPreview() {
    ScaniqoTheme(
        dynamicColor = false
    ) {
        DocumentTagSelector(
            tags = TAGS_PREVIEW,
            nullOptionLabel = "Todas",
            selectedTagId = 1L,
            onSelect = {},
            enabled = true,
            listState = rememberLazyListState()
        )
    }
}
