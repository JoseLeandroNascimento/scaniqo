package com.joseleandro.scaniqo.ui.documents.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.joseleandro.scaniqo.R

@Composable
internal fun DocumentQuickTools(
    enabled: Boolean,
    onImport: () -> Unit,
    onMerge: () -> Unit,
    onSign: () -> Unit,
) {
    val listState = rememberLazyListState()
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    LazyRow(
        state = listState,
        flingBehavior = rememberSnapFlingBehavior(listState),
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "import") {
            QuickToolCard(
                R.drawable.ic_document_import, R.string.quick_tools_import,
                R.string.quick_tools_import_hint, enabled, onImport,
                container = if (dark) Color(0xFF173D58) else Color(0xFFDCEEFF),
                content = if (dark) Color(0xFFDCEEFF) else Color(0xFF173D58),
            )
        }
        item(key = "merge") {
            QuickToolCard(
                R.drawable.ic_tool_merge, R.string.quick_tools_merge,
                R.string.quick_tools_merge_hint, enabled, onMerge,
                container = if (dark) Color(0xFF42345E) else Color(0xFFEDE4FF),
                content = if (dark) Color(0xFFEDE4FF) else Color(0xFF42345E),
            )
        }
        item(key = "sign") {
            QuickToolCard(
                R.drawable.ic_tool_signature, R.string.quick_tools_sign,
                R.string.quick_tools_sign_hint, enabled, onSign,
                container = if (dark) Color(0xFF21483E) else Color(0xFFDDF3E9),
                content = if (dark) Color(0xFFDDF3E9) else Color(0xFF21483E),
            )
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
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.width(208.dp).heightIn(min = 168.dp),
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
