package com.joseleandro.scaniqo.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Scaniqo color palette.
 *
 * Raw color definitions.
 *
 * UI components should use MaterialTheme.colorScheme
 * instead of accessing palette colors directly.
 */
internal object ScaniqoPalette {

    // ============================================================
    // BASE COLORS
    // ============================================================

    val White = Color(0xFFFFFFFF)
    val Black = Color(0xFF000000)


    // ============================================================
    // PRIMARY — INK BLUE
    // ============================================================

    val Ink10 = Color(0xFF061E2B)
    val Ink20 = Color(0xFF06364A)
    val Ink30 = Color(0xFF194A61)
    val Ink40 = Color(0xFF245A73)

    val Ink80 = Color(0xFF99CDE6)
    val Ink90 = Color(0xFFD0E8F5)


    // ============================================================
    // SECONDARY — TEAL
    // ============================================================

    val Teal10 = Color(0xFF062019)
    val Teal20 = Color(0xFF12382F)
    val Teal30 = Color(0xFF244D45)
    val Teal40 = Color(0xFF3C665F)

    val Teal80 = Color(0xFFA3CFC5)
    val Teal90 = Color(0xFFC0ECE1)


    // ============================================================
    // TERTIARY — WARM SAND
    // ============================================================

    val Sand10 = Color(0xFF241A0A)
    val Sand20 = Color(0xFF392D1A)
    val Sand30 = Color(0xFF51432E)
    val Sand40 = Color(0xFF6B5E46)

    val Sand80 = Color(0xFFD5C6AB)
    val Sand90 = Color(0xFFF1E2C7)


    // ============================================================
    // ERROR
    // ============================================================

    val Red10 = Color(0xFF410002)
    val Red20 = Color(0xFF690005)
    val Red30 = Color(0xFF93000A)
    val Red40 = Color(0xFFBA1A1A)

    val Red80 = Color(0xFFFFB4AB)
    val Red90 = Color(0xFFFFDAD6)


    // ============================================================
    // LIGHT THEME — NEUTRAL SURFACES
    // ============================================================

    /**
     * Screen background.
     *
     * Slightly gray to distinguish white cards
     * from the background.
     */
    val BackgroundLight = Color(0xFFF9FAFB)

    /**
     * Main surfaces.
     *
     * Pure white for a clean appearance.
     */
    val Paper = Color(0xFFFFFFFF)

    /**
     * Surface container elevation levels.
     *
     * All colors are neutral and contain
     * no warm beige or brown undertones.
     */
    val PaperLow = Color(0xFFE9EDF2)

    val PaperContainer = Color(0xFFE5E9EF)

    val PaperHigh = Color(0xFFE0E6ED)

    val PaperHighest = Color(0xFFDCE3EB)

    val PaperDim = Color(0xFFDCE3EB)


    // ============================================================
    // DARK THEME — COOL SLATE SURFACES
    // ============================================================

    /**
     * Dark theme background.
     */
    val BackgroundDark = Color(0xFF0F1318)

    /**
     * Dark surfaces with subtle blue-gray undertones.
     */
    val SlateLowest = Color(0xFF0B0F14)

    val Slate = Color(0xFF14191F)

    val SlateLow = Color(0xFF191F26)

    val SlateContainer = Color(0xFF1F252E)

    val SlateHigh = Color(0xFF29313A)

    val SlateHighest = Color(0xFF343D47)

    val SlateBright = Color(0xFF343B45)

    val SlateVariant = Color(0xFF343C46)

    val SlateOutline = Color(0xFF454F5A)


    // ============================================================
    // TEXT COLORS
    // ============================================================

    /**
     * Primary text in light theme.
     */
    val TextDark = Color(0xFF1B1F24)

    /**
     * Primary text in dark theme.
     */
    val TextLight = Color(0xFFF1F5F9)

    /**
     * Secondary text in dark theme.
     */
    val TextMutedDark = Color(0xFFADB7C2)


    // ============================================================
    // NEUTRAL VARIANTS
    // ============================================================

    /**
     * Neutral colors used for:
     *
     * - Secondary text
     * - Outlines
     * - Dividers
     * - Surface variants
     * - Subtle component borders
     */

    val NeutralVariant30 = Color(0xFF465360)

    val NeutralVariant50 = Color(0xFF6E7B89)

    val NeutralVariant60 = Color(0xFF8D97A3)

    val NeutralVariant80 = Color(0xFFB2BFCD)

    val NeutralVariant90 = Color(0xFFDCE3EB)
}