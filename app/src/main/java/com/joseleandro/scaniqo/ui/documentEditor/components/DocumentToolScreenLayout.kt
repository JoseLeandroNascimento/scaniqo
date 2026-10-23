package com.joseleandro.scaniqo.ui.documentEditor.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.joseleandro.scaniqo.R
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.joseleandro.scaniqo.ui.documentCommon.components.DocumentPreviewZoom

/** Modos sempre visíveis; o usuário ajusta a folha e confere o resultado antes de aplicar. */
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
    keepControlsVisible: Boolean = false,
    onGestureStart: () -> Unit = {},
    onTransformStart: () -> Unit = {},
    actions: @Composable () -> Unit,
) {
    var expandedControls by rememberSaveable(previewKey) { mutableStateOf(value = false) }
    val modes: @Composable () -> Unit = {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
            content = tools,
        )
    }
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
        val controlsMaxHeight = maxHeight * .28f
        val compactControls = (maxHeight < 560.dp) && !keepControlsVisible
        if ((maxWidth >= 600.dp) && (maxHeight < 500.dp)) {
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
                if (compactControls) {
                    val controlsDescription = stringResource(
                        if (expandedControls) R.string.preview_controls_hide else R.string.preview_controls_show,
                    )
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) { header() }
                        TextButton(
                            onClick = { expandedControls = !expandedControls },
                            contentPadding = PaddingValues(horizontal = 8.dp),
                            modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = controlsDescription },
                        ) {
                            Icon(
                                painterResource(if (expandedControls) R.drawable.ic_toast_close else R.drawable.ic_tool_adjust),
                                null, Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.preview_controls_show), style = MaterialTheme.typography.labelMedium, maxLines = 1)
                        }
                    }
                } else header()
                modes()
                if (gestureHint.isNotEmpty()) GestureHint(gestureHint)
                canvas(Modifier.weight(1f).fillMaxWidth())
                navigation()
                if (!compactControls || expandedControls) Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Column(
                        Modifier.fillMaxWidth().heightIn(max = controlsMaxHeight)
                            .verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
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
) {
    Surface(
        modifier = Modifier.widthIn(min = 88.dp)
            .selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
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
            Text(label, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 1)
        }
    }
}
