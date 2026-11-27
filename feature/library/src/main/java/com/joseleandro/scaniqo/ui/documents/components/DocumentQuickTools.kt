package com.joseleandro.scaniqo.ui.documents.components

import com.joseleandro.scaniqo.core.resources.R as CoreResourcesR
import com.joseleandro.scaniqo.feature.library.R

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.joseleandro.scaniqo.ui.theme.documentQuickToolColors
@Composable
internal fun DocumentQuickTools(
    enabled: Boolean,
    onImport: () -> Unit,
    onMerge: () -> Unit,
    onSign: () -> Unit,
    onAllTools: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp),
) {
    val listState = rememberLazyListState()
    val colors = documentQuickToolColors(dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f)
    val layoutDirection = LocalLayoutDirection.current
    val edgeColor = MaterialTheme.colorScheme.background
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val compact = maxWidth >= 552.dp
        val minCardWidth = 208.dp * LocalDensity.current.fontScale.coerceIn(1f, 1.5f)
        val availableWidth = (maxWidth - contentPadding.calculateLeftPadding(layoutDirection) -
            contentPadding.calculateRightPadding(layoutDirection)).coerceAtLeast(0.dp)
        val cardWidth = if (availableWidth >= ((minCardWidth * 4) + 36.dp)) {
            (availableWidth - 36.dp) / 4
        } else {
            minOf(minCardWidth, availableWidth)
        }
        LazyRow(
            state = listState,
            flingBehavior = rememberSnapFlingBehavior(listState),
            modifier = Modifier.fillMaxWidth().drawWithContent {
                drawContent()
                val fadeWidth = 24.dp.toPx().coerceAtMost(size.width / 2f)
                val isRtl = layoutDirection == LayoutDirection.Rtl
                val fadeLeft = if (isRtl) listState.canScrollForward else listState.canScrollBackward
                val fadeRight = if (isRtl) listState.canScrollBackward else listState.canScrollForward
                if (fadeLeft) drawRect(
                    Brush.horizontalGradient(listOf(edgeColor, edgeColor.copy(alpha = 0f)), endX = fadeWidth),
                    size = Size(fadeWidth, size.height),
                )
                if (fadeRight) drawRect(
                    Brush.horizontalGradient(
                        listOf(edgeColor.copy(alpha = 0f), edgeColor),
                        startX = size.width - fadeWidth, endX = size.width,
                    ),
                    topLeft = Offset(size.width - fadeWidth, 0f),
                    size = Size(fadeWidth, size.height),
                )
            },
            contentPadding = contentPadding,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "import") {
                QuickToolCard(
                    CoreResourcesR.drawable.ic_symbol_file_open, CoreResourcesR.string.quick_tools_import,
                    CoreResourcesR.string.quick_tools_import_hint, enabled, onImport,
                    modifier = Modifier.width(cardWidth),
                    compact = compact,
                    container = colors.importPdf.container,
                    content = colors.importPdf.onContainer,
                )
            }
            item(key = "merge") {
                QuickToolCard(
                    CoreResourcesR.drawable.ic_symbol_merge, CoreResourcesR.string.quick_tools_merge,
                    CoreResourcesR.string.quick_tools_merge_hint, enabled, onMerge,
                    modifier = Modifier.width(cardWidth),
                    compact = compact,
                    container = colors.mergePdf.container,
                    content = colors.mergePdf.onContainer,
                )
            }
            item(key = "sign") {
                QuickToolCard(
                    CoreResourcesR.drawable.ic_symbol_draw, CoreResourcesR.string.quick_tools_sign,
                    CoreResourcesR.string.quick_tools_sign_hint, enabled, onSign,
                    modifier = Modifier.width(cardWidth),
                    compact = compact,
                    container = colors.signDocument.container,
                    content = colors.signDocument.onContainer,
                )
            }
            item(key = "all_tools") {
                QuickToolCard(
                    CoreResourcesR.drawable.ic_tag_tools, CoreResourcesR.string.tools_hub_title,
                    R.string.tools_hub_hint, enabled, onAllTools,
                    modifier = Modifier.width(cardWidth),
                    compact = compact,
                    container = colors.allTools.container,
                    content = colors.allTools.onContainer,
                )
            }
        }
    }
}

@Composable
private fun QuickToolCard(
    icon: Int,
    title: Int,
    description: Int,
    enabled: Boolean,
    onClick: () -> Unit,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = if (compact) 104.dp else 168.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = container,
        contentColor = content.copy(alpha = if (enabled) 1f else 0.5f),
    ) {
        Box {
            Canvas(Modifier.matchParentSize()) {
                drawCircle(
                    color = content.copy(alpha = 0.05f),
                    radius = size.width * 0.46f,
                    center = Offset(size.width, 0f),
                )
                drawCircle(
                    color = content.copy(alpha = 0.04f),
                    radius = size.width * 0.3f,
                    center = Offset(size.width, 0f),
                )
            }
            if (compact) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    QuickToolIcon(icon = icon, content = content)
                    QuickToolLabel(
                        title = title,
                        description = description,
                        enabled = enabled,
                        content = content,
                        modifier = Modifier.weight(1f),
                    )
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    QuickToolIcon(icon = icon, content = content)
                    QuickToolLabel(
                        title = title,
                        description = description,
                        enabled = enabled,
                        content = content,
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickToolIcon(
    icon: Int,
    content: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.size(44.dp)
            .background(content.copy(alpha = 0.09f), RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun QuickToolLabel(
    title: Int,
    description: Int,
    enabled: Boolean,
    content: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
        Text(
            stringResource(description),
            style = MaterialTheme.typography.bodySmall,
            color = if (enabled) content else content.copy(alpha = 0.5f),
        )
    }
}
