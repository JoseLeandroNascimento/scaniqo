package com.joseleandro.scaniqo.ui.documentEditor.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.joseleandro.scaniqo.R
import com.joseleandro.scaniqo.ui.documentCommon.components.DocumentPreviewZoom

/** O documento usa toda a área disponível; os ajustes aparecem somente quando solicitados. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DocumentToolScreenLayout(
    modifier: Modifier = Modifier,
    header: @Composable () -> Unit,
    preview: @Composable BoxScope.() -> Unit,
    tools: @Composable ColumnScope.() -> Unit,
    controls: @Composable () -> Unit,
    navigation: @Composable () -> Unit = {},
    previewKey: Any = Unit,
    onGestureStart: () -> Unit = {},
    onTransformStart: () -> Unit = {},
    actions: @Composable () -> Unit,
) {
    var showControls by rememberSaveable { mutableStateOf(false) }
    Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            DocumentPreviewZoom(Modifier.fillMaxSize(), contentKey = previewKey,
                onGestureStart = onGestureStart, onTransformStart = onTransformStart, content = preview)
            FilledTonalButton(
                onClick = { showControls = true },
                modifier = Modifier.align(Alignment.BottomStart).padding(8.dp),
            ) {
                Icon(painterResource(R.drawable.ic_tool_adjust), null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.preview_controls_show))
            }
        }
        actions()
    }
    if (showControls) ModalBottomSheet(
        onDismissRequest = { showControls = false },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().heightIn(max = maxHeight * .8f).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { header() }
                    IconButton(onClick = { showControls = false }) {
                        Icon(painterResource(R.drawable.ic_toast_close), stringResource(R.string.preview_controls_hide))
                    }
                }
                navigation()
                Row(Modifier.weight(1f, fill = false), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.width(48.dp).verticalScroll(rememberScrollState()).selectableGroup(),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, content = tools)
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { controls() }
                }
                actions()
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}
