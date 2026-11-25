package com.joseleandro.scaniqo.core.billing

import android.app.Activity
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.joseleandro.scaniqo.domain.repository.PremiumEntitlement
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PlayBillingCheckoutTest {
    @Test fun explicitTapLaunchesExactlyOnceAndSuccessfulLaunchDoesNotGrantPremium() = runTest {
        val gateway = CheckoutGateway()
        val manager = PlayBillingManager(gateway,
            PlayPurchaseVerifier(PlayBillingFixtures.publicKey, PlayBillingFixtures.PACKAGE),
            EmptyStore, CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler)),
            { PlayBillingFixtures.NOW })
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        try {
            runCurrent()
            assertTrue(manager.state.value.canPurchase)
            assertEquals(0, gateway.launches)
            manager.purchase(controller.get())
            manager.purchase(controller.get())
            assertEquals(1, gateway.launches)
            assertEquals("current-play-offer", gateway.launchedOffer?.offerToken)
            assertEquals(PremiumEntitlement.FREE, manager.entitlement.value)
            assertTrue(manager.state.value.isPurchasing)
            assertFalse(manager.state.value.canPurchase)
            gateway.onPurchasesUpdated?.invoke(StoreResult(BillingResponseCode.USER_CANCELED))
            runCurrent()
            assertFalse(manager.state.value.isPurchasing)
            assertEquals(PremiumBillingMessage.PURCHASE_CANCELLED, manager.state.value.message)
        } finally {
            manager.close()
            controller.pause().stop().destroy()
        }
    }

    @Test fun unavailableLaunchDiscardsStaleOfferAndDisablesRepeatedCheckout() = runTest {
        val gateway = CheckoutGateway().apply { launchCode = BillingResponseCode.ITEM_UNAVAILABLE }
        val manager = PlayBillingManager(gateway,
            PlayPurchaseVerifier(PlayBillingFixtures.publicKey, PlayBillingFixtures.PACKAGE),
            EmptyStore, CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler)),
            { PlayBillingFixtures.NOW })
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        try {
            runCurrent()
            manager.purchase(controller.get())
            assertEquals(PremiumBillingMessage.PRODUCT_UNAVAILABLE, manager.state.value.message)
            assertNull(manager.state.value.offer)
            assertFalse(manager.state.value.canPurchase)
            manager.purchase(controller.get())
            assertEquals(1, gateway.launches)
        } finally {
            manager.close()
            controller.pause().stop().destroy()
        }
    }

    @Test fun alreadyOwnedCallbackRecoversCheckoutEvenWhenRestoreIsOffline() = runTest {
        val gateway = CheckoutGateway()
        val manager = PlayBillingManager(gateway,
            PlayPurchaseVerifier(PlayBillingFixtures.publicKey, PlayBillingFixtures.PACKAGE),
            EmptyStore, CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler)),
            { PlayBillingFixtures.NOW })
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        try {
            runCurrent()
            manager.purchase(controller.get())
            assertTrue(manager.state.value.isPurchasing)
            gateway.connectionCode = BillingResponseCode.NETWORK_ERROR
            gateway.onPurchasesUpdated?.invoke(StoreResult(BillingResponseCode.ITEM_ALREADY_OWNED))
            runCurrent()
            assertFalse(manager.state.value.isPurchasing)
            assertFalse(manager.state.value.isLoading)
            assertEquals(PremiumBillingMessage.NETWORK_ERROR, manager.state.value.message)
            assertEquals(PremiumEntitlement.FREE, manager.entitlement.value)
            assertEquals(1, gateway.launches)
        } finally {
            manager.close()
            controller.pause().stop().destroy()
        }
    }

    @Test fun foregroundReturnRecoversLostCheckoutCallbackEvenWhenRefreshFails() = runTest {
        val gateway = CheckoutGateway()
        val manager = PlayBillingManager(gateway,
            PlayPurchaseVerifier(PlayBillingFixtures.publicKey, PlayBillingFixtures.PACKAGE),
            EmptyStore, CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler)),
            { PlayBillingFixtures.NOW })
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        try {
            runCurrent()
            manager.purchase(controller.get())
            assertTrue(manager.state.value.isPurchasing)
            gateway.connectionCode = BillingResponseCode.NETWORK_ERROR
            // No listener callback arrives after the Play UI closes.
            manager.refreshAfterHostResume()
            assertFalse(manager.state.value.isPurchasing)
            assertFalse(manager.state.value.isLoading)
            assertEquals(PremiumBillingMessage.NETWORK_ERROR, manager.state.value.message)
            assertEquals(PremiumEntitlement.FREE, manager.entitlement.value)
            gateway.connectionCode = BillingResponseCode.OK
            manager.retry()
            runCurrent()
            assertTrue(manager.state.value.canPurchase)
            manager.purchase(controller.get())
            assertEquals(2, gateway.launches)
        } finally {
            manager.close()
            controller.pause().stop().destroy()
        }
    }

    @Test fun ordinaryRefreshCannotCloseCheckoutStillOpenInPlay() = runTest {
        val gateway = CheckoutGateway()
        val manager = PlayBillingManager(gateway,
            PlayPurchaseVerifier(PlayBillingFixtures.publicKey, PlayBillingFixtures.PACKAGE),
            EmptyStore, CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler)),
            { PlayBillingFixtures.NOW })
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        try {
            runCurrent()
            manager.purchase(controller.get())
            gateway.connectionCode = BillingResponseCode.NETWORK_ERROR
            manager.refresh()
            assertTrue(manager.state.value.isPurchasing)
            assertFalse(manager.state.value.canPurchase)
            gateway.connectionCode = BillingResponseCode.OK
            manager.refresh()
            assertTrue(manager.state.value.isPurchasing)
            assertFalse(manager.state.value.canPurchase)
            manager.purchase(controller.get())
            assertEquals(1, gateway.launches)
            manager.refreshAfterHostResume()
            assertFalse(manager.state.value.isPurchasing)
            assertTrue(manager.state.value.canPurchase)
        } finally {
            manager.close()
            controller.pause().stop().destroy()
        }
    }

    private object EmptyStore : PlayReceiptStore {
        override suspend fun read(): CachedPlayReceipt? = null
        override suspend fun write(receipt: CachedPlayReceipt) = Unit
        override suspend fun clear() = Unit
    }

    private class CheckoutGateway : PlayBillingGateway {
        override var onPurchasesUpdated: ((StoreResult<List<StorePurchase>>) -> Unit)? = null
        var launches = 0
        var launchCode = BillingResponseCode.OK
        var connectionCode = BillingResponseCode.OK
        var launchedOffer: StoreOffer? = null
        override suspend fun connect() = StoreResult(connectionCode, Unit)
        override suspend fun queryPurchases() = StoreResult(BillingResponseCode.OK, emptyList<StorePurchase>())
        override suspend fun queryOffer() = StoreResult(BillingResponseCode.OK,
            StoreOffer(PremiumOffer("scaniqo_premium", "£12.49", "GBP"), "current-play-offer"))
        override suspend fun acknowledge(token: String) = StoreResult(BillingResponseCode.OK, Unit)
        override fun launch(activity: Activity, offer: StoreOffer): StoreResult<Unit> {
            launches++
            launchedOffer = offer
            return StoreResult(launchCode, Unit)
        }
        override fun close() = Unit
    }
}
