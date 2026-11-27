package com.joseleandro.scaniqo.core.navigation

import android.app.Application
import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.joseleandro.scaniqo.ui.theme.ScaniqoTheme
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AdaptiveNavigationFrameLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun railKeepsIconsAndLongLabelsAlignedAfterRepeatedTabletRotation() {
        verifyResizing(fontScale = 1f, locale = Locale.forLanguageTag("pt-BR"))
    }

    @Test
    fun enlargedPortugueseLabelsStayAlignedAndReachableAtTheRailBreakpoint() {
        verifyResizing(fontScale = 2f, locale = Locale.forLanguageTag("pt-BR"), includeNarrowRail = true)
    }

    @Test
    fun enlargedRtlLabelsStayAlignedAndReachableAfterRotation() {
        verifyResizing(fontScale = 2f, locale = Locale.forLanguageTag("ar"), includeNarrowRail = true)
    }

    private fun verifyResizing(
        fontScale: Float,
        locale: Locale,
        includeNarrowRail: Boolean = false,
    ) {
        val windowSize = mutableStateOf(DpSize(1280.dp, 800.dp))
        val navigations = mutableListOf<AppRoute>()
        var imports = 0
        lateinit var drawerState: DrawerState
        val application = RuntimeEnvironment.getApplication()
        val configuration = Configuration(application.resources.configuration).apply {
            setLocale(locale)
            setLayoutDirection(locale)
            this.fontScale = fontScale
        }
        val localizedContext = application.createConfigurationContext(configuration)
        val layoutDirection = if (configuration.layoutDirection == android.view.View.LAYOUT_DIRECTION_RTL) {
            LayoutDirection.Rtl
        } else {
            LayoutDirection.Ltr
        }

        // A plain Application prevents document initialization, backup and widget jobs.
        // One composition survives every size change, as it does in a resizable window.
        compose.setContent {
            CompositionLocalProvider(
                LocalContext provides localizedContext,
                LocalConfiguration provides configuration,
            ) {
                DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(windowSize.value)) {
                    CompositionLocalProvider(
                        LocalDensity provides Density(LocalDensity.current.density, fontScale),
                        LocalLayoutDirection provides layoutDirection,
                    ) {
                        ScaniqoTheme(darkTheme = false) {
                            drawerState = rememberDrawerState(DrawerValue.Closed)
                            AdaptiveNavigationFrame(
                                currentRoute = AppRoute.Settings,
                                drawerState = drawerState,
                                navigationEnabled = true,
                                onNavigate = navigations::add,
                                onImportPdf = { imports++ },
                                drawerContent = { permanent ->
                                    AppNavigationDrawer(
                                        currentRoute = AppRoute.Settings,
                                        onClose = {},
                                        onImportPdf = { imports++ },
                                        onNavigate = navigations::add,
                                        permanent = permanent,
                                        modifier = Modifier.testTag(
                                            if (permanent) "persistent_drawer" else "modal_drawer",
                                        ),
                                    )
                                },
                            ) {
                                Box(Modifier.fillMaxSize().testTag("screen_content"))
                            }
                        }
                    }
                }
            }
        }

        val sizes = listOf(
            DpSize(1280.dp, 800.dp),
            DpSize(800.dp, 1280.dp),
            DpSize(1280.dp, 800.dp),
            DpSize(800.dp, 1280.dp),
        ) + if (includeNarrowRail) listOf(DpSize(600.dp, 960.dp)) else emptyList()
        var railVisits = 0
        sizes.forEach { size ->
            compose.runOnIdle { windowSize.value = size }
            compose.onNodeWithTag("screen_content").assertIsDisplayed()
            compose.runOnIdle {
                assertEquals(DrawerValue.Closed, drawerState.currentValue)
                assertEquals(DrawerValue.Closed, drawerState.targetValue)
            }
            if (size.width >= 1200.dp) {
                compose.onNodeWithTag("navigation_rail").assertDoesNotExist()
                compose.onNodeWithTag("persistent_drawer").assertIsDisplayed()
            } else {
                compose.onNodeWithTag("persistent_drawer").assertDoesNotExist()
                assertRailAlignment()
                val items = compose.onAllNodesWithTag("navigation_rail_item", useUnmergedTree = true)
                items[5].performScrollTo().assertIsDisplayed().assertIsSelected()
                items[6].performScrollTo().assertIsDisplayed().assertIsEnabled()
                    .performTouchInput { click() }
                compose.onNodeWithTag("navigation_rail_import").performScrollTo()
                    .assertIsDisplayed().assertIsEnabled().performTouchInput { click() }
                railVisits++
                compose.runOnIdle {
                    assertEquals(railVisits, imports)
                    assertEquals(List(railVisits) { AppRoute.Premium }, navigations)
                }
            }
        }
    }

    private fun assertRailAlignment() {
        val rail = compose.onNodeWithTag("navigation_rail").assertIsDisplayed()
            .getUnclippedBoundsInRoot()
        val icons = compose.onAllNodesWithTag("navigation_rail_icon", useUnmergedTree = true)
        val labels = compose.onAllNodesWithTag("navigation_rail_label", useUnmergedTree = true)
        val items = compose.onAllNodesWithTag("navigation_rail_item", useUnmergedTree = true)
        icons.assertCountEquals(7)
        labels.assertCountEquals(7)
        items.assertCountEquals(7)
        val premiumText = labels[6].fetchSemanticsNode().config[SemanticsProperties.Text].joinToString()
        assertTrue("The long Premium label must be exercised", premiumText.length > 10)

        compose.onNodeWithTag("navigation_rail_import").performScrollTo().assertIsDisplayed()
            .assertCenteredInside(rail, "Import")
        repeat(7) { index ->
            items[index].performScrollTo().assertIsDisplayed().assertIsEnabled()
                .assertCenteredInside(rail, "Item $index")
            icons[index].assertIsDisplayed().assertCenteredInside(rail, "Icon $index")
            labels[index].assertIsDisplayed().assertCenteredInside(rail, "Label $index")
        }
    }

    private fun SemanticsNodeInteraction.assertCenteredInside(rail: DpRect, description: String) {
        // Unclipped bounds catch an overflowing label even when an ancestor hides its edge.
        val bounds = getUnclippedBoundsInRoot()
        // ForcedSize can reduce density; allow one physical pixel of layout rounding.
        val tolerance = 1f / fetchSemanticsNode().layoutInfo.density.density
        assertTrue("$description must have a measured width", bounds.right > bounds.left)
        assertEquals(
            "$description must share the rail center",
            (rail.left.value + rail.right.value) / 2f,
            (bounds.left.value + bounds.right.value) / 2f,
            tolerance,
        )
        assertTrue("$description exceeds the rail start: $bounds in $rail", bounds.left.value >= rail.left.value - tolerance)
        assertTrue("$description exceeds the rail end: $bounds in $rail", bounds.right.value <= rail.right.value + tolerance)
    }
}
