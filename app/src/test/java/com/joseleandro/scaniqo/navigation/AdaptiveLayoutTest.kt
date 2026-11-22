package com.joseleandro.scaniqo.navigation

import androidx.compose.ui.unit.IntRect
import androidx.lifecycle.SavedStateHandle
import com.joseleandro.scaniqo.core.navigation.AppRoute
import com.joseleandro.scaniqo.core.navigation.NavigationViewModel
import com.joseleandro.scaniqo.core.navigation.SeparatingFold
import com.joseleandro.scaniqo.core.navigation.foldSafeBounds
import com.joseleandro.scaniqo.core.navigation.showNavigationRail
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveLayoutTest {
    @Test
    fun railFollowsAvailableWidthAndLeavesDocumentWorkspacesFullScreen() {
        assertFalse(showNavigationRail(599, AppRoute.Documents))
        assertTrue(showNavigationRail(600, AppRoute.Documents))
        assertTrue(showNavigationRail(840, AppRoute.Settings))
        assertFalse(showNavigationRail(599, AppRoute.LanguageSettings))
        assertTrue(showNavigationRail(600, AppRoute.LanguageSettings))
        assertFalse(showNavigationRail(1200, AppRoute.DocumentViewer(1)))
        assertFalse(showNavigationRail(1200, AppRoute.DocumentSignature(1)))
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
