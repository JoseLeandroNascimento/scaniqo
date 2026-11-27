package com.joseleandro.scaniqo.ui.theme

import androidx.compose.material3.RippleDefaults
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.joseleandro.scaniqo.domain.model.AppColorTheme
import com.joseleandro.scaniqo.domain.model.TagColor
import com.joseleandro.scaniqo.ui.components.checkColor
import java.io.File
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MaterialContrastTest {
    private data class Measurement(
        val component: String,
        val theme: String,
        val foreground: Color,
        val background: Color,
        val minimum: Double,
        val ratio: Double,
    )

    private val measurements = mutableListOf<Measurement>()
    private val states = with(RippleDefaults.RippleAlpha) {
        mapOf("normal" to 0f, "hover" to hoveredAlpha, "focus" to focusedAlpha, "pressed" to pressedAlpha)
    }

    @Test
    fun renderedRolePairsAndInteractionLayersMeetContrastTargets() {
        for (theme in AppColorTheme.entries) for (dark in listOf(false, true)) {
            val name = "${theme.name}/${if (dark) "dark" else "light"}"
            val c = personalizedColorScheme(theme, dark)
            val surfaces = mapOf(
                "background" to c.background, "surface" to c.surface,
                "containerLowest" to c.surfaceContainerLowest, "containerLow" to c.surfaceContainerLow,
                "container" to c.surfaceContainer, "containerHigh" to c.surfaceContainerHigh,
                "containerHighest" to c.surfaceContainerHighest, "surfaceDim" to c.surfaceDim,
                "surfaceBright" to c.surfaceBright, "surfaceVariant" to c.surfaceVariant,
                "surfaceElevated3dp" to c.surfaceColorAtElevation(3.dp),
                "surfaceElevated6dp" to c.surfaceColorAtElevation(6.dp),
            )
            surfaces.forEach { (surface, background) ->
                record("text/$surface", name, c.onSurface, background)
                record("supporting-placeholder/$surface", name, c.onSurfaceVariant, background)
                record("action-focus/$surface", name, c.primary, background)
                record("error-label/$surface", name, c.error, background)
                record("field-outline/$surface", name, c.outline, background, 3.0)
            }

            mapOf(
                "primary-button" to (c.onPrimary to c.primary),
                "secondary-button" to (c.onSecondary to c.secondary),
                "tertiary-button" to (c.onTertiary to c.tertiary),
                "primary-selected" to (c.onPrimaryContainer to c.primaryContainer),
                "secondary-selected-chip" to (c.onSecondaryContainer to c.secondaryContainer),
                "tertiary-selected" to (c.onTertiaryContainer to c.tertiaryContainer),
                "destructive-button" to (c.onError to c.error),
                "error-message" to (c.onErrorContainer to c.errorContainer),
                "snackbar" to (c.inverseOnSurface to c.inverseSurface),
            ).forEach { (role, pair) ->
                states.forEach { (state, alpha) ->
                    val background = pair.first.copy(alpha = alpha).compositeOver(pair.second)
                    record("$role/$state", name, pair.first, background)
                }
            }
            record("snackbar-action", name, c.inversePrimary, c.inverseSurface)
            record("selected-preset-border", name, c.primary, c.secondaryContainer, 3.0)

            val card = if (dark) c.surfaceContainerLow else c.surface
            states.forEach { (state, alpha) ->
                record("card-body/$state", name, c.onSurface, c.onSurface.copy(alpha = alpha).compositeOver(card))
                record("card-supporting/$state", name, c.onSurfaceVariant, c.onSurface.copy(alpha = alpha).compositeOver(card))
                val menu = c.onSurface.copy(alpha = alpha).compositeOver(c.surfaceContainer)
                record("menu-text/$state", name, c.onSurface, menu)
                record("menu-icon/$state", name, c.onSurfaceVariant, menu, 3.0)
                record("dialog-dismiss/$state", name, c.primary, c.primary.copy(alpha = alpha).compositeOver(c.surfaceContainerHigh))
                record("dialog-destructive/$state", name, c.error, c.error.copy(alpha = alpha).compositeOver(c.surfaceContainerHigh))
                record("chip-unselected/$state", name, c.onSurfaceVariant, c.onSurfaceVariant.copy(alpha = alpha).compositeOver(c.background))
            }

            mapOf(
                "primary-fixed" to Triple(c.primaryFixed, c.primaryFixedDim, c.onPrimaryFixed to c.onPrimaryFixedVariant),
                "secondary-fixed" to Triple(c.secondaryFixed, c.secondaryFixedDim, c.onSecondaryFixed to c.onSecondaryFixedVariant),
                "tertiary-fixed" to Triple(c.tertiaryFixed, c.tertiaryFixedDim, c.onTertiaryFixed to c.onTertiaryFixedVariant),
            ).forEach { (role, values) ->
                for (background in listOf(values.first, values.second)) {
                    record(role, name, values.third.first, background)
                    record("$role-variant", name, values.third.second, background)
                }
            }

            states.forEach { (state, alpha) ->
                val selectedDrawer = c.primary.copy(alpha = .1f).compositeOver(c.surface)
                record("drawer-selected/$state", name, c.primary, c.primary.copy(alpha = alpha).compositeOver(selectedDrawer))
                for (sample in 0..32) {
                    val gradient = lerp(c.primaryContainer, c.secondaryContainer, sample / 32f)
                    val background = c.onPrimaryContainer.copy(alpha = alpha).compositeOver(gradient)
                    record("premium-gradient-caption/$state/sample=$sample", name,
                        c.onPrimaryContainer, background)
                }
            }
            for (tag in TagColor.entries) {
                val background = Color(tag.argb).copy(alpha = .9f).compositeOver(c.surface)
                record("tag-content/${tag.name}", name, checkColor(background), background)
            }

            val disabledContainer = c.onSurface.copy(alpha = .12f).compositeOver(c.surface)
            record("busy-button-indicator", name, c.onSurfaceVariant, disabledContainer, 3.0)
            record("disabled-button/exempt", name, c.onSurface.copy(alpha = .38f), disabledContainer, 0.0)
            record("decorative-card-border/exempt", name, c.outlineVariant, c.background, 0.0)
            assertEquals("Static surfaces must not receive a hidden tonal tint", 0f, c.surfaceTint.alpha, 0f)
        }

        checkShortcutLayers()
        checkPaperFeedback()
        writeReport()

        val failures = measurements.filter { it.ratio < it.minimum }
        assertTrue(failures.joinToString("\n") { "${it.theme}/${it.component}: ${it.ratio} < ${it.minimum}" }, failures.isEmpty())
    }

    private fun checkShortcutLayers() {
        for (dark in listOf(false, true)) {
            val c = documentQuickToolColors(dark)
            val name = "shortcuts/${if (dark) "dark" else "light"}"
            mapOf("import" to c.importPdf, "merge" to c.mergePdf, "sign" to c.signDocument, "tools" to c.allTools)
                .forEach { (role, pair) ->
                    // Both circles can overlap behind a label; the icon also has a 9% backing.
                    for (decoration in listOf(0f, .05f, .04f, 1f - .95f * .96f)) {
                        val decorated = pair.onContainer.copy(alpha = decoration).compositeOver(pair.container)
                        states.forEach { (state, alpha) ->
                            val effective = pair.onContainer.copy(alpha = alpha).compositeOver(decorated)
                            record("shortcut-$role-caption/$state/decor=$decoration", name, pair.onContainer, effective)
                            val iconBackground = pair.onContainer.copy(alpha = .09f).compositeOver(decorated)
                            record("shortcut-$role-icon/$state/decor=$decoration", name, pair.onContainer,
                                pair.onContainer.copy(alpha = alpha).compositeOver(iconBackground), 3.0)
                        }
                    }
                }
        }
    }

    private fun checkPaperFeedback() {
        for (theme in listOf("paper/light", "paper/dark")) {
            record("document-loading", theme, DocumentPaperColors.indicator, DocumentPaperColors.background, 3.0)
            record("filter-placeholder-drawing-hint", theme, DocumentPaperColors.supportingText, DocumentPaperColors.background)
            record("paper-error", theme, DocumentPaperColors.onBackground, DocumentPaperColors.background)
            // 96% opaque white error overlay: black and white bound the luminance of any PDF underneath.
            for (underlay in listOf(Color.Black, Color.White)) {
                val background = DocumentPaperColors.background.copy(alpha = .96f).compositeOver(underlay)
                record("paper-error-overlay", theme, DocumentPaperColors.onBackground, background)
                record("paper-retry-overlay", theme, DocumentPaperColors.indicator, background)
                record("paper-retry-icon-overlay", theme, DocumentPaperColors.supportingText, background, 3.0)
            }
        }
    }

    private fun record(component: String, theme: String, foreground: Color, background: Color, minimum: Double = 4.5) {
        assertEquals("The final background must be opaque: $component", 1f, background.alpha, 0f)
        // Measure the same final sRGB colors exported in the report, after alpha compositing.
        val effectiveForeground = Color(foreground.compositeOver(background).toArgb())
        val effectiveBackground = Color(background.toArgb())
        val a = effectiveForeground.luminance().toDouble()
        val b = effectiveBackground.luminance().toDouble()
        val ratio = (maxOf(a, b) + .05) / (minOf(a, b) + .05)
        measurements += Measurement(component, theme, effectiveForeground, effectiveBackground, minimum, ratio)
    }

    private fun writeReport() {
        val file = File("build/reports/material-contrast.csv")
        file.parentFile?.mkdirs()
        file.writeText(buildString {
            appendLine("component,theme,content,background,contrast,minimum,result")
            measurements.forEach { row ->
                val result = when {
                    row.minimum == 0.0 -> "EXEMPT"
                    row.ratio >= row.minimum -> "PASS"
                    else -> "FAIL"
                }
                appendLine(listOf(row.component, row.theme, hex(row.foreground), hex(row.background),
                    String.format(Locale.ROOT, "%.8f", row.ratio), row.minimum, result).joinToString(","))
            }
        })
    }

    private fun hex(color: Color) = String.format(Locale.ROOT, "#%06X", color.toArgb() and 0xFFFFFF)
}
