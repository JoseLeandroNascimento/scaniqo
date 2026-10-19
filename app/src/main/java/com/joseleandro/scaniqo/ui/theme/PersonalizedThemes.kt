package com.joseleandro.scaniqo.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.joseleandro.scaniqo.domain.model.AppColorTheme

private data class ThemeColors(
    val primary: Color, val pastel: Color, val container: Color, val darkContainer: Color,
)

private fun AppColorTheme.colors(): ThemeColors = when (this) {
    AppColorTheme.ORIGINAL -> ThemeColors(Color(0xFF245A73), Color(0xFF99CDE6), Color(0xFFD0E8F5), Color(0xFF194A61))
    AppColorTheme.OCEAN -> ThemeColors(Color(0xFF245CBD), Color(0xFFAFC6FF), Color(0xFFDCE6FF), Color(0xFF153F85))
    AppColorTheme.FOREST -> ThemeColors(Color(0xFF28634A), Color(0xFFA1D5B3), Color(0xFFC5EED4), Color(0xFF164D35))
    AppColorTheme.LAVENDER -> ThemeColors(Color(0xFF6C47A4), Color(0xFFD3BBFF), Color(0xFFEDDDFF), Color(0xFF4F307E))
    AppColorTheme.ROSE -> ThemeColors(Color(0xFF99466C), Color(0xFFF7B3CF), Color(0xFFFFD9E8), Color(0xFF74304F))
    AppColorTheme.SUNSET -> ThemeColors(Color(0xFF8C5218), Color(0xFFFFBE82), Color(0xFFFFDFC0), Color(0xFF673B12))
}

/** As cinco paletas mantêm os mesmos componentes e níveis de superfície do app. */
internal fun personalizedColorScheme(theme: AppColorTheme, dark: Boolean): ColorScheme {
    val base = if (dark) DarkColorScheme else LightColorScheme
    if (theme == AppColorTheme.ORIGINAL) return base
    val palette = theme.colors()
    val primary = if (dark) palette.pastel else palette.primary
    val container = if (dark) palette.darkContainer else palette.container
    val onAccent = if (dark) Color(0xFF15191D) else Color.White
    val onContainer = if (dark) palette.container else Color(0xFF202027)
    fun surface(value: Color) = lerp(value, palette.primary, if (dark) .055f else .025f)
    return base.copy(
        primary = primary, onPrimary = onAccent,
        primaryContainer = container, onPrimaryContainer = onContainer,
        secondary = primary, onSecondary = onAccent,
        secondaryContainer = container, onSecondaryContainer = onContainer,
        tertiary = primary, onTertiary = onAccent,
        tertiaryContainer = container, onTertiaryContainer = onContainer,
        inversePrimary = if (dark) palette.primary else palette.pastel,
        surfaceTint = Color.Transparent,
        background = surface(base.background), surface = surface(base.surface),
        surfaceDim = surface(base.surfaceDim), surfaceBright = surface(base.surfaceBright),
        surfaceContainerLowest = surface(base.surfaceContainerLowest),
        surfaceContainerLow = surface(base.surfaceContainerLow),
        surfaceContainer = surface(base.surfaceContainer),
        surfaceContainerHigh = surface(base.surfaceContainerHigh),
        surfaceContainerHighest = surface(base.surfaceContainerHighest),
    )
}
