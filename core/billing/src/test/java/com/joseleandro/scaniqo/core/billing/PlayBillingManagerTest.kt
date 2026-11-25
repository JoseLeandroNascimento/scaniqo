package com.joseleandro.scaniqo.core.billing

import android.app.Activity
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.joseleandro.scaniqo.domain.repository.PremiumEntitlement
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayBillingManagerTest {
    @Test fun noKeyResolvesFreeAndNeverConnectsOrEnablesCheckout() = runTest {
        val gateway = FakeGateway()
        val manager = manager(gateway, publicKey = "")
        runCurrent()
        assertEquals(PremiumEntitlement.FREE, manager.entitlement.value)
        assertEquals(PremiumBillingMessage.CONFIGURATION_REQUIRED, manager.state.value.message)
        assertFalse(manager.state.value.canPurchase)
        assertEquals(0, gateway.connectionCalls)
        manager.close()
    }

    @Test fun emptyAuthoritativeQueryRevokesPreviouslyCachedPurchase() = runTest {
        val store = MemoryReceiptStore(cached())
        val manager = manager(FakeGateway(), store)
        runCurrent()
        assertEquals(PremiumEntitlement.FREE, manager.entitlement.value)
        assertNull(store.value)
        assertTrue(manager.state.value.canPurchase)
        manager.close()
    }

    @Test fun validCompletedPurchaseGrantsAcknowledgesAndCachesSignedReceipt() = runTest {
        val gateway = FakeGateway().apply { purchases = listOf(PlayBillingFixtures.purchase()) }
        val store = MemoryReceiptStore()
        val manager = manager(gateway, store)
        runCurrent()
        assertEquals(PremiumEntitlement.PREMIUM, manager.entitlement.value)
        assertEquals(listOf("fixture-purchase-token"), gateway.acknowledgedTokens)
        assertNotNull(store.value)
        assertEquals(PlayBillingFixtures.receipt().json, store.value?.receipt?.json)
        assertFalse(manager.state.value.canPurchase)
        assertEquals(0, gateway.launchCalls)
        manager.close()
    }

    @Test fun pendingPurchaseNeverGrantsAcknowledgesOrCaches() = runTest {
        val gateway = FakeGateway().apply {
            purchases = listOf(PlayBillingFixtures.purchase(
                receipt = PlayBillingFixtures.receipt(rawState = 4), state = StorePurchaseState.PENDING,
            ))
        }
        val store = MemoryReceiptStore()
        val manager = manager(gateway, store)
        runCurrent()
        assertEquals(PremiumEntitlement.FREE, manager.entitlement.value)
        assertEquals(PremiumBillingMessage.PURCHASE_PENDING, manager.state.value.message)
        assertTrue(manager.state.value.isPending)
        assertFalse(manager.state.value.canPurchase)
        assertTrue(gateway.acknowledgedTokens.isEmpty())
        assertNull(store.value)
        manager.close()
    }

    @Test fun forgedReceiptAndMismatchedLiveTokenNeverGrantOrAcknowledge() = runTest {
        for (purchase in listOf(
            PlayBillingFixtures.purchase(receipt = PlayBillingFixtures.receipt().copy(signature = "invalid")),
            PlayBillingFixtures.purchase(token = "different-token"),
            PlayBillingFixtures.purchase(receipt = PlayBillingFixtures.receipt(purchaseTime = PlayBillingFixtures.NOW + 600_000)),
        )) {
            val gateway = FakeGateway().apply { purchases = listOf(purchase) }
            val manager = manager(gateway)
            runCurrent()
            assertEquals(PremiumEntitlement.FREE, manager.entitlement.value)
            assertEquals(PremiumBillingMessage.VERIFICATION_FAILED, manager.state.value.message)
            assertTrue(gateway.acknowledgedTokens.isEmpty())
            manager.close()
        }
    }

    @Test fun unrelatedProductAndSuccessfulLaunchAloneNeverGrantAccess() = runTest {
        val gateway = FakeGateway().apply {
            purchases = listOf(PlayBillingFixtures.purchase(
                receipt = PlayBillingFixtures.receipt(product = "other"), products = listOf("other"),
            ))
        }
        val manager = manager(gateway)
        runCurrent()
        gateway.onPurchasesUpdated?.invoke(StoreResult(BillingResponseCode.OK, emptyList()))
        runCurrent()
        assertEquals(PremiumEntitlement.FREE, manager.entitlement.value)
        assertTrue(gateway.acknowledgedTokens.isEmpty())
        manager.close()
    }

    @Test fun acknowledgementFailureRetriesAndDoesNotCacheBeforeSuccess() = runTest {
        val gateway = FakeGateway().apply {
            purchases = listOf(PlayBillingFixtures.purchase())
            acknowledgeCode = BillingResponseCode.NETWORK_ERROR
        }
        val store = MemoryReceiptStore()
        val manager = manager(gateway, store)
        runCurrent()
        assertEquals(PremiumEntitlement.PREMIUM, manager.entitlement.value)
        assertEquals(PremiumBillingMessage.ACKNOWLEDGEMENT_PENDING, manager.state.value.message)
        assertNull(store.value)
        gateway.acknowledgeCode = BillingResponseCode.OK
        advanceTimeBy(2_001)
        runCurrent()
        assertEquals(2, gateway.acknowledgedTokens.size)
        assertNotNull(store.value)
        assertNull(manager.state.value.message)
        manager.close()
    }

    @Test fun alreadyOwnedRestoresInsteadOfLaunchingAnotherCharge() = runTest {
        val gateway = FakeGateway()
        val manager = manager(gateway)
        runCurrent()
        gateway.purchases = listOf(PlayBillingFixtures.purchase(acknowledged = true))
        gateway.onPurchasesUpdated?.invoke(StoreResult(BillingResponseCode.ITEM_ALREADY_OWNED))
        runCurrent()
        assertEquals(PremiumEntitlement.PREMIUM, manager.entitlement.value)
        assertEquals(PremiumBillingMessage.PURCHASE_RESTORED, manager.state.value.message)
        assertEquals(0, gateway.launchCalls)
        manager.close()
    }

    @Test fun restoreWithNoPurchaseShowsFeedbackWithoutLaunchingBillingFlow() = runTest {
        val gateway = FakeGateway()
        val manager = manager(gateway)
        runCurrent()
        manager.restore()
        runCurrent()
        assertEquals(PremiumBillingMessage.NO_PURCHASE_FOUND, manager.state.value.message)
        assertEquals(0, gateway.launchCalls)
        manager.close()
    }

    @Test fun offlineValidCacheRemainsPremiumOnlyWithinSevenDays() = runTest {
        val gateway = FakeGateway().apply { connectionCode = BillingResponseCode.NETWORK_ERROR }
        val store = MemoryReceiptStore(cached())
        val manager = manager(gateway, store)
        runCurrent()
        assertEquals(PremiumEntitlement.PREMIUM, manager.entitlement.value)
        advanceTimeBy(CachedPlayReceipt.OFFLINE_GRACE_MILLIS + 1)
        runCurrent()
        assertEquals(PremiumEntitlement.FREE, manager.entitlement.value)
        assertNull(store.value)
        manager.close()
    }

    @Test fun expiredOrTamperedOfflineCacheNeverGrantsPremium() = runTest {
        for (receipt in listOf(
            cached().copy(verifiedAtMillis = PlayBillingFixtures.NOW - CachedPlayReceipt.OFFLINE_GRACE_MILLIS - 1),
            cached().copy(receipt = PlayBillingFixtures.receipt().copy(signature = "invalid")),
            cached().copy(verifiedAtMillis = PlayBillingFixtures.NOW + 1),
        )) {
            val gateway = FakeGateway().apply { connectionCode = BillingResponseCode.SERVICE_UNAVAILABLE }
            val store = MemoryReceiptStore(receipt)
            val manager = manager(gateway, store)
            runCurrent()
            assertEquals(PremiumEntitlement.FREE, manager.entitlement.value)
            assertNull(store.value)
            manager.close()
        }
    }

    @Test fun cancellationAndStoreFailureProvideFeedbackWithoutChangingEntitlement() = runTest {
        val gateway = FakeGateway()
        val manager = manager(gateway)
        runCurrent()
        gateway.onPurchasesUpdated?.invoke(StoreResult(BillingResponseCode.USER_CANCELED))
        runCurrent()
        assertEquals(PremiumBillingMessage.PURCHASE_CANCELLED, manager.state.value.message)
        assertEquals(PremiumEntitlement.FREE, manager.entitlement.value)
        gateway.connectionCode = BillingResponseCode.NETWORK_ERROR
        manager.retry()
        runCurrent()
        assertEquals(PremiumBillingMessage.NETWORK_ERROR, manager.state.value.message)
        assertFalse(manager.state.value.isLoading)
        manager.close()
    }

    @Test fun initialLookupKeepsUnknownUntilTimeoutWithoutHidingAdsForever() = runTest {
        val gateway = FakeGateway().apply { suspendPurchaseQuery = true }
        val manager = manager(gateway)
        runCurrent()
        assertEquals(PremiumEntitlement.UNKNOWN, manager.entitlement.value)
        assertTrue(manager.state.value.isLoading)
        advanceTimeBy(20_001)
        runCurrent()
        assertEquals(PremiumEntitlement.FREE, manager.entitlement.value)
        assertFalse(manager.state.value.isLoading)
        assertEquals(PremiumBillingMessage.STORE_UNAVAILABLE, manager.state.value.message)
        manager.close()
    }

    @Test fun validatedCacheRemainsPremiumWhileRefreshIsPendingOrTimesOut() = runTest {
        val gateway = FakeGateway().apply { suspendPurchaseQuery = true }
        val manager = manager(gateway, MemoryReceiptStore(cached()))
        runCurrent()
        assertEquals(PremiumEntitlement.PREMIUM, manager.entitlement.value)
        advanceTimeBy(20_001)
        runCurrent()
        assertEquals(PremiumEntitlement.PREMIUM, manager.entitlement.value)
        manager.close()
    }

    private fun TestScope.manager(
        gateway: FakeGateway,
        store: MemoryReceiptStore = MemoryReceiptStore(),
        publicKey: String = PlayBillingFixtures.publicKey,
    ) = PlayBillingManager(
        gateway, PlayPurchaseVerifier(publicKey, PlayBillingFixtures.PACKAGE), store,
        CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler)),
        { PlayBillingFixtures.NOW + testScheduler.currentTime },
    )

    private fun cached() = CachedPlayReceipt(PlayBillingFixtures.receipt(), PlayBillingFixtures.NOW)

    private class MemoryReceiptStore(var value: CachedPlayReceipt? = null) : PlayReceiptStore {
        override suspend fun read(): CachedPlayReceipt? = value
        override suspend fun write(receipt: CachedPlayReceipt) { value = receipt }
        override suspend fun clear() { value = null }
    }

    private class FakeGateway : PlayBillingGateway {
        override var onPurchasesUpdated: ((StoreResult<List<StorePurchase>>) -> Unit)? = null
        var connectionCode = BillingResponseCode.OK
        var acknowledgeCode = BillingResponseCode.OK
        var purchases = emptyList<StorePurchase>()
        var connectionCalls = 0
        var launchCalls = 0
        var suspendPurchaseQuery = false
        val acknowledgedTokens = mutableListOf<String>()
        override suspend fun connect(): StoreResult<Unit> {
            connectionCalls++
            return StoreResult(connectionCode, Unit)
        }
        override suspend fun queryPurchases(): StoreResult<List<StorePurchase>> {
            if (suspendPurchaseQuery) awaitCancellation()
            return StoreResult(BillingResponseCode.OK, purchases)
        }
        override suspend fun queryOffer() = StoreResult(BillingResponseCode.OK,
            StoreOffer(PremiumOffer("scaniqo_premium", "R$ 9,99", "BRL"), "fixture-offer-token"))
        override suspend fun acknowledge(token: String): StoreResult<Unit> {
            acknowledgedTokens += token
            return StoreResult(acknowledgeCode, Unit)
        }
        override fun launch(activity: Activity, offer: StoreOffer): StoreResult<Unit> {
            launchCalls++
            return StoreResult(BillingResponseCode.OK, Unit)
        }
        override fun close() = Unit
    }
}
