package com.joseleandro.scaniqo.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp

object ScaniqoCardDefaults {
    val shape: Shape
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.shapes.large

    val containerColor: Color
        @Composable
        @ReadOnlyComposable
        get() = with(MaterialTheme.colorScheme) {
            if (surface.luminance() < .5f) surfaceContainerLow else surface
        }

    val border: BorderStroke
        @Composable
        @ReadOnlyComposable
        get() = with(MaterialTheme.colorScheme) {
            BorderStroke(1.dp, outlineVariant.copy(alpha = if (surface.luminance() < .5f) .6f else 1f))
        }
}

object ScaniqoFieldDefaults {
    val shape: Shape
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.shapes.medium
}
