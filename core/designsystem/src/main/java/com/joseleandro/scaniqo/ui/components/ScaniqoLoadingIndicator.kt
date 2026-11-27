package com.joseleandro.scaniqo.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Mantém as ondas proporcionais mesmo em contêineres que impõem largura ou altura. */
@Composable
fun ScaniqoLoadingIndicator(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    CircularWavyProgressIndicator(
        modifier = modifier.wrapContentSize(Alignment.Center).size(48.dp),
        color = color,
    )
}
