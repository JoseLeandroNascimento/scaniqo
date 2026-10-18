package com.joseleandro.scaniqo.ui.documentEditor.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Mantém Aplicar acessível e usa a altura disponível para a página, sem limites de bottom sheet. */
@Composable
internal fun DocumentToolScreenLayout(
    modifier: Modifier = Modifier,
    header: @Composable () -> Unit,
    preview: @Composable BoxScope.() -> Unit,
    tools: @Composable ColumnScope.() -> Unit,
    controls: @Composable () -> Unit,
    navigation: @Composable () -> Unit = {},
    actions: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        if (maxWidth > maxHeight || maxWidth >= 600.dp) {
            val controlsWidth = minOf(320.dp, maxWidth * .46f)
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(
                    Modifier.weight(1f).fillMaxHeight(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Row(Modifier.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center, content = preview)
                        Column(
                            Modifier.width(48.dp).verticalScroll(rememberScrollState()).selectableGroup(),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            content = tools,
                        )
                    }
                }
                Column(
                    Modifier.width(controlsWidth).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Column(
                        Modifier.weight(1f).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        header()
                        controls()
                        navigation()
                    }
                    actions()
                }
            }
        } else {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                header()
                DocumentInteractivePreviewStage(
                    modifier = Modifier.weight(1f),
                    tools = tools,
                    controls = controls,
                    content = preview,
                )
                navigation()
                actions()
            }
        }
    }
}
