package com.joseleandro.scaniqo.navigation

import androidx.compose.ui.unit.IntRect
import androidx.lifecycle.SavedStateHandle
import com.joseleandro.scaniqo.core.navigation.AppRoute
import com.joseleandro.scaniqo.core.navigation.NavigationViewModel
import com.joseleandro.scaniqo.core.navigation.SeparatingFold
import com.joseleandro.scaniqo.core.navigation.foldSafeBounds
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.window.core.layout.WindowSizeClass
import androidx.window.core.layout.computeWindowSizeClass
import com.joseleandro.scaniqo.core.navigation.navigationSuiteType
import org.junit.Assert.assertEquals
import org.junit.Test

class AdaptiveLayoutTest {
    private fun navigationType(width: Int, height: Int, route: AppRoute = AppRoute.Documents) =
        navigationSuiteType(
            WindowSizeClass.BREAKPOINTS_V2.computeWindowSizeClass(width.toFloat(), height.toFloat()),
            route,
        )

    @Test
    fun navigationAdaptsToWindowBoundariesAndShortWindows() {
        assertEquals(NavigationSuiteType.None, navigationType(599, 800))
        assertEquals(NavigationSuiteType.NavigationRail, navigationType(600, 800))
        assertEquals(NavigationSuiteType.NavigationRail, navigationType(1199, 800))
        assertEquals(NavigationSuiteType.NavigationDrawer, navigationType(1200, 800))
        assertEquals(NavigationSuiteType.None, navigationType(1200, 479))
        assertEquals(NavigationSuiteType.NavigationDrawer, navigationType(1200, 480))
        assertEquals(NavigationSuiteType.NavigationRail, navigationType(840, 800, AppRoute.LanguageSettings))
    }

    @Test
    fun documentWorkspacesKeepTheEntireWindowForTheirContent() {
        listOf(
            AppRoute.DocumentReader(1), AppRoute.DocumentViewer(1), AppRoute.DocumentSignature(1),
        ).forEach { route ->
            assertEquals(NavigationSuiteType.None, navigationType(1600, 900, route))
        }
    }

    @Test
    fun resizingBetweenTabletAndSplitScreenRestoresTheAppropriateNavigation() {
        assertEquals(NavigationSuiteType.NavigationDrawer, navigationType(1280, 800))
        assertEquals(NavigationSuiteType.None, navigationType(500, 800))
        assertEquals(NavigationSuiteType.NavigationRail, navigationType(800, 1280))
        assertEquals(NavigationSuiteType.NavigationDrawer, navigationType(1280, 800))
    }

    @Test
    fun switchingRailDestinationsDoesNotAccumulateDuplicateScreens() {
        val navigation = NavigationViewModel(SavedStateHandle())
        navigation.navigateToTopLevel(AppRoute.Tags)
        navigation.navigateToTopLevel(AppRoute.Tools)
        navigation.navigateToTopLevel(AppRoute.Tools)
        assertEquals(listOf(AppRoute.Documents, AppRoute.Tools), navigation.backStack)
        navigation.navigateToTopLevel(AppRoute.Documents)
        assertEquals(listOf(AppRoute.Documents), navigation.backStack)
    }

    @Test
    fun flatOrFoldedCoverWindowUsesAllAvailableSpace() {
        val window = IntRect(0, 0, 320, 720)
        assertEquals(window, foldSafeBounds(window, emptyList()))
        assertEquals(window, foldSafeBounds(window, listOf(SeparatingFold(IntRect(500, 0, 520, 720), true))))
    }

    @Test
    fun verticalHingeNeverOverlapsContentAndHonorsReadingDirectionOnEqualPanes() {
        val window = IntRect(0, 0, 1100, 800)
        val fold = SeparatingFold(IntRect(540, 0, 560, 800), true)
        assertEquals(IntRect(0, 0, 540, 800), foldSafeBounds(window, listOf(fold)))
        assertEquals(IntRect(560, 0, 1100, 800), foldSafeBounds(window, listOf(fold), rightToLeft = true))
    }

    @Test
    fun tabletopSeparatingCreaseUsesUpperPaneWithoutRequiringPhysicalOcclusion() {
        val window = IntRect(0, 0, 900, 1200)
        val fold = SeparatingFold(IntRect(0, 600, 900, 600), false)
        assertEquals(IntRect(0, 0, 900, 600), foldSafeBounds(window, listOf(fold)))
    }

    @Test
    fun offCenterFoldUsesLargerPaneAndWindowCoordinates() {
        val window = IntRect(100, 50, 1200, 850)
        val fold = SeparatingFold(IntRect(500, 50, 520, 850), true)
        assertEquals(IntRect(520, 50, 1200, 850), foldSafeBounds(window, listOf(fold)))
    }

    @Test
    fun foldingAndUnfoldingRecalculatesTheViewport() {
        val window = IntRect(0, 0, 1000, 800)
        val fold = SeparatingFold(IntRect(490, 0, 510, 800), true)
        assertEquals(490, foldSafeBounds(window, listOf(fold)).width)
        assertEquals(1000, foldSafeBounds(window, emptyList()).width)
    }
}
