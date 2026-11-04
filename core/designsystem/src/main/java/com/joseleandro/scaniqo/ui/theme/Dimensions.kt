package com.joseleandro.scaniqo.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class ScaniqoDimensions(
    val spacingXs: Dp = 4.dp,
    val spacingSm: Dp = 8.dp,
    val spacingMd: Dp = 16.dp,
    val spacingLg: Dp = 24.dp,
    val spacingXl: Dp = 32.dp,
    val screenPadding: Dp = 16.dp,
    val iconSmall: Dp = 16.dp,
    val iconMedium: Dp = 24.dp,
    val iconLarge: Dp = 32.dp,
    val minimumTouchTarget: Dp = 48.dp,
    val cornerXs: Dp = 4.dp,
    val cornerSm: Dp = 8.dp,
    val cornerMd: Dp = 12.dp,
    val cornerLg: Dp = 16.dp,
    val cornerXl: Dp = 24.dp,
)

internal val LocalScaniqoDimensions = staticCompositionLocalOf { ScaniqoDimensions() }

val MaterialTheme.dimensions: ScaniqoDimensions
    @Composable
    @ReadOnlyComposable
    get() = LocalScaniqoDimensions.current
