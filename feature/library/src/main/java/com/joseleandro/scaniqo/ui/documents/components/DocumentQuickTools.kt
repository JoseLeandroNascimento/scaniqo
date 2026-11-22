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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
@Composable
internal fun DocumentQuickTools(
    enabled: Boolean,
    onImport: () -> Unit,
    onMerge: () -> Unit,
    onSign: () -> Unit,
    onAllTools: () -> Unit,
) {
    val listState = rememberLazyListState()
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val minCardWidth = 208.dp * LocalDensity.current.fontScale.coerceIn(1f, 1.5f)
        val availableWidth = (maxWidth - 32.dp).coerceAtLeast(0.dp)
        val cardWidth = if (availableWidth >= ((minCardWidth * 4) + 36.dp)) {
            (availableWidth - 36.dp) / 4
        } else {
            minOf(minCardWidth, availableWidth)
        }
        LazyRow(
            state = listState,
            flingBehavior = rememberSnapFlingBehavior(listState),
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "import") {
                QuickToolCard(
                    CoreResourcesR.drawable.ic_document_import, CoreResourcesR.string.quick_tools_import,
                    CoreResourcesR.string.quick_tools_import_hint, enabled, onImport,
                    modifier = Modifier.width(cardWidth),
                    container = if (dark) Color(0xFF173D58) else Color(0xFFDCEEFF),
                    content = if (dark) Color(0xFFDCEEFF) else Color(0xFF173D58),
                )
            }
            item(key = "merge") {
                QuickToolCard(
                    CoreResourcesR.drawable.ic_tool_merge, CoreResourcesR.string.quick_tools_merge,
                    CoreResourcesR.string.quick_tools_merge_hint, enabled, onMerge,
                    modifier = Modifier.width(cardWidth),
                    container = if (dark) Color(0xFF42345E) else Color(0xFFEDE4FF),
                    content = if (dark) Color(0xFFEDE4FF) else Color(0xFF42345E),
                )
            }
            item(key = "sign") {
                QuickToolCard(
                    CoreResourcesR.drawable.ic_tool_signature, CoreResourcesR.string.quick_tools_sign,
                    CoreResourcesR.string.quick_tools_sign_hint, enabled, onSign,
                    modifier = Modifier.width(cardWidth),
                    container = if (dark) Color(0xFF21483E) else Color(0xFFDDF3E9),
                    content = if (dark) Color(0xFFDDF3E9) else Color(0xFF21483E),
                )
            }
            item(key = "all_tools") {
                QuickToolCard(
                    CoreResourcesR.drawable.ic_tag_tools, CoreResourcesR.string.tools_hub_title,
                    R.string.tools_hub_hint, enabled, onAllTools,
                    modifier = Modifier.width(cardWidth),
                    container = if (dark) Color(0xFF4B4031) else Color(0xFFFFEED6),
                    content = if (dark) Color(0xFFFFEED6) else Color(0xFF4B4031),
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
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 168.dp),
        shape = RoundedCornerShape(24.dp),
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
            Column(
                Modifier.fillMaxWidth().padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    Modifier.size(44.dp)
                        .background(content.copy(alpha = 0.09f), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(24.dp))
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
                    Text(
                        stringResource(description),
                        style = MaterialTheme.typography.bodySmall,
                        color = content.copy(alpha = if (enabled) 0.78f else 0.5f),
                    )
                }
            }
        }
    }
}
