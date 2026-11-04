package com.joseleandro.scaniqo.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Scaniqo Material Design 3 color schemes.
 *
 * Light theme:
 * - White surfaces
 * - Neutral gray containers
 * - Subtle contrasts
 * - Clean background
 *
 * Dark theme:
 * - Cool dark surfaces
 * - Subtle elevation differences
 * - Comfortable text contrast
 *
 * Semantic colors remain separate from the raw palette.
 */


// ================================================================
// LIGHT COLOR SCHEME
// ================================================================

internal val LightColorScheme = lightColorScheme(

    // ------------------------------------------------------------
    // PRIMARY
    // ------------------------------------------------------------

    primary = ScaniqoPalette.Ink40,

    onPrimary = ScaniqoPalette.White,

    primaryContainer = ScaniqoPalette.Ink90,

    onPrimaryContainer = ScaniqoPalette.Ink10,

    inversePrimary = ScaniqoPalette.Ink80,


    // ------------------------------------------------------------
    // SECONDARY
    // ------------------------------------------------------------

    secondary = ScaniqoPalette.Teal40,

    onSecondary = ScaniqoPalette.White,

    secondaryContainer = ScaniqoPalette.Teal90,

    onSecondaryContainer = ScaniqoPalette.Teal10,


    // ------------------------------------------------------------
    // TERTIARY
    // ------------------------------------------------------------

    tertiary = ScaniqoPalette.Sand40,

    onTertiary = ScaniqoPalette.White,

    tertiaryContainer = ScaniqoPalette.Sand90,

    onTertiaryContainer = ScaniqoPalette.Sand10,


    // ------------------------------------------------------------
    // BACKGROUND
    // ------------------------------------------------------------

    background = ScaniqoPalette.BackgroundLight,

    onBackground = ScaniqoPalette.TextDark,


    // ------------------------------------------------------------
    // SURFACE
    // ------------------------------------------------------------

    surface = ScaniqoPalette.Paper,

    onSurface = ScaniqoPalette.TextDark,

    surfaceVariant = ScaniqoPalette.NeutralVariant90,

    onSurfaceVariant = ScaniqoPalette.NeutralVariant30,

    /**
     * Prevent primary color tint from affecting
     * elevated surfaces.
     */
    surfaceTint = Color.Transparent,


    // ------------------------------------------------------------
    // INVERSE SURFACES
    // ------------------------------------------------------------

    inverseSurface = ScaniqoPalette.SlateHighest,

    inverseOnSurface = ScaniqoPalette.PaperLow,


    // ------------------------------------------------------------
    // ERROR
    // ------------------------------------------------------------

    error = ScaniqoPalette.Red40,

    onError = ScaniqoPalette.White,

    errorContainer = ScaniqoPalette.Red90,

    onErrorContainer = ScaniqoPalette.Red10,


    // ------------------------------------------------------------
    // OUTLINES
    // ------------------------------------------------------------

    outline = ScaniqoPalette.NeutralVariant50,

    outlineVariant = ScaniqoPalette.NeutralVariant80,


    // ------------------------------------------------------------
    // SCRIM
    // ------------------------------------------------------------

    scrim = ScaniqoPalette.Black,


    // ------------------------------------------------------------
    // SURFACE BRIGHTNESS
    // ------------------------------------------------------------

    surfaceBright = ScaniqoPalette.Paper,

    surfaceDim = ScaniqoPalette.PaperDim,


    // ------------------------------------------------------------
    // SURFACE CONTAINERS
    // ------------------------------------------------------------

    /**
     * Lowest surface level.
     *
     * Pure white.
     */
    surfaceContainerLowest = ScaniqoPalette.White,

    /**
     * Low surface level.
     *
     * Almost white.
     */
    surfaceContainerLow = ScaniqoPalette.PaperLow,

    /**
     * Default container level.
     *
     * Very light neutral gray.
     */
    surfaceContainer = ScaniqoPalette.PaperContainer,

    /**
     * Higher container level.
     *
     * Light neutral gray.
     */
    surfaceContainerHigh = ScaniqoPalette.PaperHigh,

    /**
     * Highest container level.
     *
     * Slightly stronger gray contrast.
     */
    surfaceContainerHighest = ScaniqoPalette.PaperHighest,


    // ------------------------------------------------------------
    // PRIMARY FIXED
    // ------------------------------------------------------------

    primaryFixed = ScaniqoPalette.Ink90,

    primaryFixedDim = ScaniqoPalette.Ink80,

    onPrimaryFixed = ScaniqoPalette.Ink10,

    onPrimaryFixedVariant = ScaniqoPalette.Ink30,


    // ------------------------------------------------------------
    // SECONDARY FIXED
    // ------------------------------------------------------------

    secondaryFixed = ScaniqoPalette.Teal90,

    secondaryFixedDim = ScaniqoPalette.Teal80,

    onSecondaryFixed = ScaniqoPalette.Teal10,

    onSecondaryFixedVariant = ScaniqoPalette.Teal30,


    // ------------------------------------------------------------
    // TERTIARY FIXED
    // ------------------------------------------------------------

    tertiaryFixed = ScaniqoPalette.Sand90,

    tertiaryFixedDim = ScaniqoPalette.Sand80,

    onTertiaryFixed = ScaniqoPalette.Sand10,

    onTertiaryFixedVariant = ScaniqoPalette.Sand30,
)


// ================================================================
// DARK COLOR SCHEME
// ================================================================

internal val DarkColorScheme = darkColorScheme(

    // ------------------------------------------------------------
    // PRIMARY
    // ------------------------------------------------------------

    primary = ScaniqoPalette.Ink80,

    onPrimary = ScaniqoPalette.Ink20,

    primaryContainer = ScaniqoPalette.Ink30,

    onPrimaryContainer = ScaniqoPalette.Ink90,

    inversePrimary = ScaniqoPalette.Ink40,


    // ------------------------------------------------------------
    // SECONDARY
    // ------------------------------------------------------------

    secondary = ScaniqoPalette.Teal80,

    onSecondary = ScaniqoPalette.Teal20,

    secondaryContainer = ScaniqoPalette.Teal30,

    onSecondaryContainer = ScaniqoPalette.Teal90,


    // ------------------------------------------------------------
    // TERTIARY
    // ------------------------------------------------------------

    tertiary = ScaniqoPalette.Sand80,

    onTertiary = ScaniqoPalette.Sand20,

    tertiaryContainer = ScaniqoPalette.Sand30,

    onTertiaryContainer = ScaniqoPalette.Sand90,


    // ------------------------------------------------------------
    // BACKGROUND
    // ------------------------------------------------------------

    background = ScaniqoPalette.BackgroundDark,

    onBackground = ScaniqoPalette.TextLight,


    // ------------------------------------------------------------
    // SURFACE
    // ------------------------------------------------------------

    surface = ScaniqoPalette.Slate,

    onSurface = ScaniqoPalette.TextLight,

    surfaceVariant = ScaniqoPalette.SlateVariant,

    onSurfaceVariant = ScaniqoPalette.TextMutedDark,

    /**
     * Avoid additional color tint on surfaces.
     */
    surfaceTint = Color.Transparent,


    // ------------------------------------------------------------
    // INVERSE SURFACES
    // ------------------------------------------------------------

    inverseSurface = ScaniqoPalette.TextLight,

    inverseOnSurface = ScaniqoPalette.SlateHigh,


    // ------------------------------------------------------------
    // ERROR
    // ------------------------------------------------------------

    error = ScaniqoPalette.Red80,

    onError = ScaniqoPalette.Red20,

    errorContainer = ScaniqoPalette.Red30,

    onErrorContainer = ScaniqoPalette.Red90,


    // ------------------------------------------------------------
    // OUTLINES
    // ------------------------------------------------------------

    outline = ScaniqoPalette.NeutralVariant60,

    outlineVariant = ScaniqoPalette.SlateOutline,


    // ------------------------------------------------------------
    // SCRIM
    // ------------------------------------------------------------

    scrim = ScaniqoPalette.Black,


    // ------------------------------------------------------------
    // SURFACE BRIGHTNESS
    // ------------------------------------------------------------

    surfaceBright = ScaniqoPalette.SlateBright,

    surfaceDim = ScaniqoPalette.BackgroundDark,


    // ------------------------------------------------------------
    // SURFACE CONTAINERS
    // ------------------------------------------------------------

    surfaceContainerLowest = ScaniqoPalette.SlateLowest,

    surfaceContainerLow = ScaniqoPalette.SlateLow,

    surfaceContainer = ScaniqoPalette.SlateContainer,

    surfaceContainerHigh = ScaniqoPalette.SlateHigh,

    surfaceContainerHighest = ScaniqoPalette.SlateHighest,


    // ------------------------------------------------------------
    // PRIMARY FIXED
    // ------------------------------------------------------------

    primaryFixed = ScaniqoPalette.Ink90,

    primaryFixedDim = ScaniqoPalette.Ink80,

    onPrimaryFixed = ScaniqoPalette.Ink10,

    onPrimaryFixedVariant = ScaniqoPalette.Ink30,


    // ------------------------------------------------------------
    // SECONDARY FIXED
    // ------------------------------------------------------------

    secondaryFixed = ScaniqoPalette.Teal90,

    secondaryFixedDim = ScaniqoPalette.Teal80,

    onSecondaryFixed = ScaniqoPalette.Teal10,

    onSecondaryFixedVariant = ScaniqoPalette.Teal30,


    // ------------------------------------------------------------
    // TERTIARY FIXED
    // ------------------------------------------------------------

    tertiaryFixed = ScaniqoPalette.Sand90,

    tertiaryFixedDim = ScaniqoPalette.Sand80,

    onTertiaryFixed = ScaniqoPalette.Sand10,

    onTertiaryFixedVariant = ScaniqoPalette.Sand30,
)