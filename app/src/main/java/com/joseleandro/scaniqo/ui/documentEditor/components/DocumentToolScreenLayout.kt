package com.joseleandro.scaniqo.ui.documentEditor.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.joseleandro.scaniqo.ui.documentCommon.components.DocumentPreviewZoom

/**
 * A ação final de cada editor é registrada no host da topbar. A função executada permanece atual
 * após recomposições, enquanto o registro só muda quando a disponibilidade da ação muda.
 */
@Composable
internal fun RegisterDocumentToolPrimaryAction(
    enabled: Boolean,
    onClick: () -> Unit,
    register: (((() -> Unit)?) -> Unit),
) {
    val currentOnClick by rememberUpdatedState(onClick)
    DisposableEffect(register, enabled) {
        register(if (enabled) ({ currentOnClick() }) else null)
        onDispose { register(null) }
    }
}

/** Mantém a página como área principal e os ajustes da categoria sempre ao alcance. */
@Composable
internal fun DocumentToolScreenLayout(
    modifier: Modifier = Modifier,
    header: @Composable () -> Unit,
    preview: @Composable BoxScope.() -> Unit,
    tools: @Composable RowScope.() -> Unit,
    controls: @Composable () -> Unit,
    navigation: @Composable () -> Unit = {},
    previewKey: Any = Unit,
    previewTransformGestures: Boolean = true,
    gestureHint: String = "",
    onGestureStart: () -> Unit = {},
    onTransformStart: () -> Unit = {},
    actions: @Composable () -> Unit,
    toolsGrid: @Composable (() -> Unit)? = null,
) {
    val canvas: @Composable (Modifier) -> Unit = { canvasModifier ->
        DocumentPreviewZoom(
            canvasModifier,
            contentKey = previewKey,
            transformGesturesEnabled = previewTransformGestures,
            onGestureStart = onGestureStart,
            onTransformStart = onTransformStart,
            content = preview,
        )
    }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val crampedTools = (maxWidth < 380.dp) || (LocalDensity.current.fontScale >= 1.5f)
        val modes: @Composable () -> Unit = {
            if (crampedTools && (toolsGrid != null)) {
                toolsGrid()
            } else {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                    content = tools,
                )
            }
        }
        val controlsMaxHeight = if (maxHeight < 560.dp) {
            (maxHeight * .30f).coerceIn(120.dp, 176.dp)
        } else {
            (maxHeight * .32f).coerceIn(144.dp, 260.dp)
        }
        if (maxWidth >= 600.dp && maxHeight < 500.dp) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                canvas(Modifier.weight(1f).fillMaxHeight())
                Column(Modifier.width(288.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        header()
                        modes()
                        if (gestureHint.isNotEmpty()) GestureHint(gestureHint)
                        navigation()
                        controls()
                    }
                    actions()
                }
            }
        } else {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                header()
                modes()
                if (gestureHint.isNotEmpty()) GestureHint(gestureHint)
                canvas(Modifier.weight(1f).fillMaxWidth())
                navigation()
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Column(
                        Modifier.fillMaxWidth().heightIn(max = controlsMaxHeight)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) { controls() }
                }
                actions()
            }
        }
    }
}

@Composable
private fun GestureHint(text: String) {
    Text(
        text,
        modifier = Modifier.fillMaxWidth(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

@Composable
internal fun DocumentToolModeButton(
    label: String,
    iconRes: Int,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val targetColor = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerLow
    val color by animateColorAsState(targetColor, tween(160), label = "editor tool category")
    Surface(
        modifier = modifier.widthIn(min = 88.dp)
            .selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = color,
        contentColor = when {
            !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = .38f)
            selected -> MaterialTheme.colorScheme.onPrimaryContainer
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = .4f)) else null,
    ) {
        Column(
            Modifier.heightIn(min = 64.dp).padding(horizontal = 8.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(painterResource(iconRes), null, Modifier.size(22.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 2)
        }
    }
}
