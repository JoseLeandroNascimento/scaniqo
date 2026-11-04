package com.joseleandro.scaniqo.ui.documentCommon.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
fun DocumentSelectionToolbar(
    description: String,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.widthIn(max = 360.dp)
            .semantics { contentDescription = description; isTraversalGroup = true },
        shape = RoundedCornerShape(20.dp),
        color = colors.surfaceContainerHigh,
        contentColor = colors.onSurfaceVariant,
        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = .5f)),
        shadowElevation = 4.dp,
    ) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentSelectionToolbarAction(
    label: String,
    @DrawableRes icon: Int,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    destructive: Boolean = false,
    swatch: Color? = null,
) {
    val colors = MaterialTheme.colorScheme
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
    ) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.size(48.dp),
        ) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(12.dp))
                    .background(if (primary) colors.secondaryContainer else Color.Transparent),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(icon),
                    contentDescription = label,
                    tint = when {
                        !enabled -> colors.onSurface.copy(alpha = .38f)
                        destructive -> colors.error
                        primary -> colors.onSecondaryContainer
                        else -> colors.onSurfaceVariant
                    },
                    modifier = Modifier.size(22.dp),
                )
                if (swatch != null) {
                    Box(
                        Modifier.align(Alignment.BottomEnd).padding(4.dp).size(9.dp)
                            .background(swatch, CircleShape)
                            .border(1.dp, colors.surfaceContainerHigh, CircleShape),
                    )
                }
            }
        }
    }
}
