package com.joseleandro.scaniqo.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.joseleandro.scaniqo.domain.model.AppColorTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonalizedThemesTest {
    private val themes = listOf(
        AppColorTheme.OCEAN,
        AppColorTheme.FOREST,
        AppColorTheme.LAVENDER,
        AppColorTheme.ROSE,
        AppColorTheme.SUNSET,
    )

    @Test fun originalThemeKeepsExistingLightAndDarkSchemesUnchanged() {
        assertSame(LightColorScheme, personalizedColorScheme(AppColorTheme.ORIGINAL, dark = false))
        assertSame(DarkColorScheme, personalizedColorScheme(AppColorTheme.ORIGINAL, dark = true))
    }

    @Test fun fivePersonalizedPalettesAreDistinctInBothModes() {
        assertEquals(themes.toSet(), AppColorTheme.entries.filter { it != AppColorTheme.ORIGINAL }.toSet())
        for (dark in listOf(false, true)) {
            val schemes = themes.map { personalizedColorScheme(it, dark) }
            assertEquals(5, schemes.map { it.primary }.distinct().size)
            assertEquals(5, schemes.map { it.primaryContainer }.distinct().size)
            val original = personalizedColorScheme(AppColorTheme.ORIGINAL, dark)
            schemes.forEach { assertNotEquals(original.primary, it.primary) }
        }
        themes.forEach { theme ->
            val light = personalizedColorScheme(theme, dark = false)
            val dark = personalizedColorScheme(theme, dark = true)
            assertNotEquals(light.primary, dark.primary)
            assertNotEquals(light.background, dark.background)
            assertTrue(dark.background.luminance() < light.background.luminance())
        }
    }

    @Test fun accentAndContainerTextMeetsNormalTextContrastInEveryPalette() {
        forEachScheme { name, scheme ->
            mapOf(
                "primary" to (scheme.onPrimary to scheme.primary),
                "secondary" to (scheme.onSecondary to scheme.secondary),
                "tertiary" to (scheme.onTertiary to scheme.tertiary),
                "primaryContainer" to (scheme.onPrimaryContainer to scheme.primaryContainer),
                "secondaryContainer" to (scheme.onSecondaryContainer to scheme.secondaryContainer),
                "tertiaryContainer" to (scheme.onTertiaryContainer to scheme.tertiaryContainer),
                "inverseSurface" to (scheme.inverseOnSurface to scheme.inverseSurface),
                "inversePrimary" to (scheme.inversePrimary to scheme.inverseSurface),
            ).forEach { (role, colors) -> assertReadable("$name/$role", colors.first, colors.second) }
        }
    }

    @Test fun MainAndSecondaryTextStayReadableAcrossAllSurfaceElevations() {
        forEachScheme { name, scheme ->
            assertReadable("$name/background", scheme.onBackground, scheme.background)
            assertReadable("$name/surfaceVariant", scheme.onSurfaceVariant, scheme.surfaceVariant)
            mapOf(
                "surface" to scheme.surface,
                "surfaceDim" to scheme.surfaceDim,
                "surfaceBright" to scheme.surfaceBright,
                "containerLowest" to scheme.surfaceContainerLowest,
                "containerLow" to scheme.surfaceContainerLow,
                "container" to scheme.surfaceContainer,
                "containerHigh" to scheme.surfaceContainerHigh,
                "containerHighest" to scheme.surfaceContainerHighest,
            ).forEach { (role, surface) ->
                assertReadable("$name/onSurface/$role", scheme.onSurface, surface)
                assertReadable("$name/onSurfaceVariant/$role", scheme.onSurfaceVariant, surface)
                assertReadable("$name/primary/$role", scheme.primary, surface)
            }
        }
    }

    @Test fun personalizationPreservesReadableErrorColorsAndDoesNotAddSurfaceTint() {
        forEachScheme { name, scheme ->
            val dark = name.endsWith("/dark")
            val base = if (dark) DarkColorScheme else LightColorScheme
            assertEquals(base.error, scheme.error)
            assertEquals(base.onError, scheme.onError)
            assertEquals(base.errorContainer, scheme.errorContainer)
            assertEquals(base.onErrorContainer, scheme.onErrorContainer)
            assertReadable("$name/error", scheme.onError, scheme.error)
            assertReadable("$name/errorContainer", scheme.onErrorContainer, scheme.errorContainer)
            assertEquals(0f, scheme.surfaceTint.alpha, 0f)
        }
    }

    private fun forEachScheme(assertion: (String, ColorScheme) -> Unit) {
        for (theme in AppColorTheme.entries) for (dark in listOf(false, true)) {
            assertion("${theme.name}/${if (dark) "dark" else "light"}", personalizedColorScheme(theme, dark))
        }
    }

    private fun assertReadable(name: String, foreground: Color, background: Color) {
        assertEquals("$name foreground opacity", 1f, foreground.alpha, 0f)
        assertEquals("$name background opacity", 1f, background.alpha, 0f)
        val first = foreground.luminance()
        val second = background.luminance()
        val contrast = (maxOf(first, second) + .05f) / (minOf(first, second) + .05f)
        assertTrue("$name contrast $contrast must be at least 4.5:1", contrast >= 4.5f)
    }
}
