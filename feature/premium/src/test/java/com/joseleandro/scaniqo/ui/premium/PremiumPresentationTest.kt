package com.joseleandro.scaniqo.ui.premium

import com.joseleandro.scaniqo.core.billing.PremiumBillingMessage
import com.joseleandro.scaniqo.core.billing.PremiumBillingState
import com.joseleandro.scaniqo.core.billing.PremiumOffer
import com.joseleandro.scaniqo.domain.repository.PremiumEntitlement
import com.joseleandro.scaniqo.feature.premium.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PremiumPresentationTest {
    private val ready = PremiumBillingState(
        entitlement = PremiumEntitlement.FREE,
        offer = PremiumOffer("scaniqo_premium", "R$ 29,90", "BRL"),
        isConfigured = true,
    )

    @Test fun priceAndConfigurationAreRequiredBeforeShowingAPurchaseAction() {
        assertEquals(PremiumAction.PURCHASE, ready.presentation().action)
        assertEquals(PremiumAction.RETRY, ready.copy(offer = null).presentation().action)
        val missingConfiguration = ready.copy(isConfigured = false).presentation()
        assertEquals(PremiumAction.RETRY, missingConfiguration.action)
        assertEquals(R.string.premium_unavailable_body, missingConfiguration.statusBody)
    }

    @Test fun pendingOrUnacknowledgedPurchasesNeverOfferAnotherCheckout() {
        listOf(
            ready.copy(isPending = true),
            ready.copy(message = PremiumBillingMessage.PURCHASE_PENDING),
            ready.copy(message = PremiumBillingMessage.ACKNOWLEDGEMENT_PENDING),
        ).forEach { state ->
            assertEquals(PremiumAction.CHECK_PURCHASE, state.presentation().action)
            assertEquals(R.string.premium_pending_title, state.presentation().statusTitle)
        }
    }

    @Test fun verificationFailureAsksToCheckTheExistingPurchase() {
        val presentation = ready.copy(message = PremiumBillingMessage.VERIFICATION_FAILED).presentation()
        assertEquals(PremiumAction.CHECK_PURCHASE, presentation.action)
        assertEquals(R.string.premium_verification_failed, presentation.statusBody)
        assertTrue(presentation.statusIsError)
    }

    @Test fun loadingAndCheckoutDisableRepeatedPurchasesAndRestores() {
        listOf(ready.copy(isLoading = true), ready.copy(isPurchasing = true)).forEach { state ->
            assertEquals(PremiumAction.WAIT, state.presentation().action)
            assertFalse(state.presentation().canRestore)
        }
    }

    @Test fun onlyConfirmedEntitlementShowsPremiumActive() {
        val unverified = ready.copy(message = PremiumBillingMessage.PURCHASE_COMPLETED)
        assertNotEquals(PremiumAction.CONTINUE, unverified.presentation().action)
        val premium = ready.copy(entitlement = PremiumEntitlement.PREMIUM, isLoading = true).presentation()
        assertEquals(PremiumAction.CONTINUE, premium.action)
        assertEquals(R.string.premium_active_title, premium.statusTitle)
    }

    @Test fun cancellationAndNoPurchaseFoundKeepTheFreeUserAbleToBuyLater() {
        val cancelled = ready.copy(message = PremiumBillingMessage.PURCHASE_CANCELLED).presentation()
        assertEquals(PremiumAction.PURCHASE, cancelled.action)
        assertEquals(R.string.premium_cancelled, cancelled.statusBody)
        assertFalse(cancelled.statusIsError)
        val missing = ready.copy(message = PremiumBillingMessage.NO_PURCHASE_FOUND).presentation()
        assertEquals(PremiumAction.PURCHASE, missing.action)
        assertEquals(R.string.premium_no_purchase, missing.statusBody)
    }

    @Test fun aStaleOfferDoesNotStartCheckoutAfterStoreOrNetworkFailure() {
        listOf(
            PremiumBillingMessage.STORE_UNAVAILABLE,
            PremiumBillingMessage.NETWORK_ERROR,
            PremiumBillingMessage.PRODUCT_UNAVAILABLE,
        ).forEach { message ->
            assertEquals(PremiumAction.RETRY, ready.copy(message = message).presentation().action)
        }
    }

    @Test fun noOfferAndNetworkFailureShowRetryWithAnActionableMessage() {
        val presentation = ready.copy(
            offer = null, message = PremiumBillingMessage.NETWORK_ERROR,
        ).presentation()
        assertEquals(PremiumAction.RETRY, presentation.action)
        assertEquals(R.string.premium_network_error, presentation.statusBody)
        assertTrue(presentation.statusIsError)
        assertTrue(presentation.canRestore)
    }
}
