package com.joseleandro.scaniqo.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joseleandro.scaniqo.core.resources.R
import com.joseleandro.scaniqo.domain.model.TagColor
import com.joseleandro.scaniqo.domain.model.TagIcon
import com.joseleandro.scaniqo.ui.theme.ScaniqoTheme
import com.joseleandro.scaniqo.ui.theme.dimensions

@Composable
fun ScaniqoOptionGrid(
    modifier: Modifier = Modifier,
    content: @Composable FlowRowScope.() -> Unit,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(
            MaterialTheme.dimensions.spacingSm,
            Alignment.CenterHorizontally,
        ),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.dimensions.spacingSm),
        content = content,
    )
}

@Composable
fun ScaniqoIconOption(
    painter: Painter,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    val background = if (selected) colors.primaryContainer else colors.surface
    val contentColor = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant
    Box(
        modifier = modifier
            .size(MaterialTheme.dimensions.minimumTouchTarget)
            .clip(MaterialTheme.shapes.medium)
            .background(background)
            .border(
                BorderStroke(
                    if (selected) 2.dp else 1.dp,
                    if (selected) colors.primary else colors.outlineVariant,
                ),
                MaterialTheme.shapes.medium,
            )
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painter,
            contentDescription = null,
            tint = if (enabled) contentColor else colors.onSurface.copy(alpha = .38f),
            modifier = Modifier.size(MaterialTheme.dimensions.iconMedium),
        )
        if (selected) {
            Surface(
                modifier = Modifier.align(Alignment.TopEnd).padding(2.dp).size(16.dp),
                color = colors.primary,
                contentColor = colors.onPrimary,
                shape = CircleShape,
                border = BorderStroke(1.dp, background),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_check_24),
                    contentDescription = null,
                    modifier = Modifier.padding(2.dp),
                )
            }
        }
    }
}

@Composable
fun ScaniqoColorOption(
    color: Color,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selectionIndicatorModifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .size(MaterialTheme.dimensions.minimumTouchTarget)
            .clip(CircleShape)
            .border(
                width = if (selected) 2.dp else 0.dp,
                color = if (selected) colors.primary else Color.Transparent,
                shape = CircleShape,
            )
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .background(color, CircleShape)
                .border(1.dp, colors.outline, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(
                    painter = painterResource(R.drawable.ic_check_24),
                    contentDescription = null,
                    tint = checkColor(color.compositeOver(colors.surface)),
                    modifier = selectionIndicatorModifier.size(20.dp),
                )
            }
        }
    }
}

@Preview(name = "Opções centralizadas", widthDp = 320, heightDp = 210)
@Preview(name = "Opções centralizadas escuras", widthDp = 320, heightDp = 210,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ScaniqoOptionGridPreview() {
    ScaniqoTheme {
        Surface {
            Column(
                Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                ScaniqoOptionGrid {
                    TagIcon.entries.take(7).forEachIndexed { index, icon ->
                        ScaniqoIconOption(
                            painter = painterResource(icon.drawableRes),
                            label = icon.name,
                            selected = index == 1,
                            onClick = {},
                        )
                    }
                }
                ScaniqoOptionGrid {
                    TagColor.entries.take(4).forEachIndexed { index, color ->
                        ScaniqoColorOption(
                            color = color.color,
                            label = color.name,
                            selected = index == 2,
                            onClick = {},
                        )
                    }
                }
            }
        }
    }
}
