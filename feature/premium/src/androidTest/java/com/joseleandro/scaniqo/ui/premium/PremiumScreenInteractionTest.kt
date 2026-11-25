package com.joseleandro.scaniqo.ui.premium

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.joseleandro.scaniqo.core.billing.PremiumBillingMessage
import com.joseleandro.scaniqo.core.billing.PremiumBillingState
import com.joseleandro.scaniqo.core.billing.PremiumOffer
import com.joseleandro.scaniqo.domain.repository.PremiumEntitlement
import com.joseleandro.scaniqo.feature.premium.R
import com.joseleandro.scaniqo.ui.theme.ScaniqoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PremiumScreenInteractionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val ready = PremiumBillingState(
        entitlement = PremiumEntitlement.FREE,
        offer = PremiumOffer("scaniqo_premium", "R$ 37,45", "BRL"),
        isConfigured = true,
    )
    private fun label(id: Int) = compose.activity.getString(id)

    @Test fun displaysThePlayPriceAndKeepsPurchaseRestoreAndFreeExitSeparate() {
        var purchases = 0
        var restores = 0
        var exits = 0
        compose.setContent {
            ScaniqoTheme {
                PremiumScreen(
                    state = ready,
                    onPurchase = { purchases++ }, onRestore = { restores++ },
                    onRetry = {}, onBack = { exits++ },
                )
            }
        }
        content().performScrollToNode(hasText("R$ 37,45"))
        compose.onNodeWithText("R$ 37,45").assertIsDisplayed()
        content().performScrollToNode(hasTestTag("premium_primary_action"))
        primary().performClick()
        content().performScrollToNode(hasText(label(R.string.premium_restore)))
        compose.onNodeWithText(label(R.string.premium_restore)).performClick()
        content().performScrollToNode(hasText(label(R.string.premium_continue_free)))
        compose.onNodeWithText(label(R.string.premium_continue_free)).performClick()
        compose.runOnIdle {
            assertEquals(1, purchases)
            assertEquals(1, restores)
            assertEquals(1, exits)
        }
    }

    @Test fun pendingPaymentOffersCheckingTheExistingPurchaseAndSuccessClosesTheScreen() {
        var state by mutableStateOf(ready.copy(isPending = true))
        var purchases = 0
        var checks = 0
        var exits = 0
        compose.setContent {
            ScaniqoTheme {
                PremiumScreen(
                    state, { purchases++ }, { checks++ }, {}, { exits++ },
                )
            }
        }
        content().performScrollToNode(hasText(label(R.string.premium_pending_title)))
        compose.onNodeWithText(label(R.string.premium_pending_title)).assertIsDisplayed()
        content().performScrollToNode(hasTestTag("premium_primary_action"))
        primary().performClick()
        compose.runOnIdle {
            assertEquals(0, purchases)
            assertEquals(1, checks)
            state = ready.copy(
                entitlement = PremiumEntitlement.PREMIUM,
                message = PremiumBillingMessage.PURCHASE_COMPLETED,
            )
        }
        content().performScrollToNode(hasText(label(R.string.premium_active_title)))
        compose.onNodeWithText(label(R.string.premium_active_title)).assertIsDisplayed()
        content().performScrollToNode(hasTestTag("premium_primary_action"))
        primary().performClick()
        compose.runOnIdle { assertEquals(1, exits) }
    }

    @Test fun unavailableConfigurationShowsNoPriceAndRetriesInsteadOfPurchasing() {
        var purchases = 0
        var retries = 0
        compose.setContent {
            ScaniqoTheme {
                PremiumScreen(
                    state = ready.copy(isConfigured = false, message = PremiumBillingMessage.CONFIGURATION_REQUIRED),
                    onPurchase = { purchases++ }, onRestore = {}, onRetry = { retries++ }, onBack = {},
                )
            }
        }
        content().performScrollToNode(hasText(label(R.string.premium_price_unavailable)))
        compose.onNodeWithText(label(R.string.premium_price_unavailable)).assertIsDisplayed()
        compose.onNodeWithText("R$ 37,45").assertDoesNotExist()
        content().performScrollToNode(hasTestTag("premium_primary_action"))
        primary().performClick()
        compose.runOnIdle {
            assertEquals(0, purchases)
            assertEquals(1, retries)
        }
    }

    @Test fun smallWidthAndLargeTextKeepTheActionsReachableWhileLoadingDisablesCheckout() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                ScaniqoTheme {
                    Box(Modifier.width(320.dp)) {
                        PremiumScreen(
                            state = ready.copy(isLoading = true),
                            onPurchase = {}, onRestore = {}, onRetry = {}, onBack = {},
                        )
                    }
                }
            }
        }
        content().performScrollToNode(hasTestTag("premium_primary_action"))
        primary().assertIsDisplayed().assertIsNotEnabled()
        content().performScrollToNode(hasText(label(R.string.premium_continue_free)))
        compose.onNodeWithText(label(R.string.premium_continue_free)).assertIsDisplayed()
        compose.onNodeWithContentDescription(label(R.string.premium_close)).assertIsDisplayed()
    }

    private fun content() = compose.onNodeWithTag("premium_content")
    private fun primary() = compose.onNodeWithTag("premium_primary_action")
}
