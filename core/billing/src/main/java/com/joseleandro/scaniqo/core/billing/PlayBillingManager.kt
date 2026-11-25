package com.joseleandro.scaniqo.core.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.joseleandro.scaniqo.domain.repository.PremiumEntitlement
import com.joseleandro.scaniqo.domain.repository.PremiumEntitlementRepository
import java.io.Closeable
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Single application-scoped owner of Play Billing. Activities are used only for launchBillingFlow.
 * refresh() is safe to call on foreground and on entry to the Premium screen.
 */
class PlayBillingManager internal constructor(
    private val gateway: PlayBillingGateway,
    private val verifier: PlayPurchaseVerifier,
    private val receipts: PlayReceiptStore,
    private val scope: CoroutineScope,
    private val nowMillis: () -> Long,
) : PremiumEntitlementRepository, Closeable {
    constructor(context: Context, publicKey: String) : this(
        AndroidPlayBillingGateway(context.applicationContext),
        PlayPurchaseVerifier(publicKey, context.packageName),
        FilePlayReceiptStore(context.applicationContext),
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
        System::currentTimeMillis,
    )

    private val mutableState = MutableStateFlow(PremiumBillingState(isConfigured = verifier.isConfigured))
    val state: StateFlow<PremiumBillingState> = mutableState.asStateFlow()
    private val mutableEntitlement = MutableStateFlow(PremiumEntitlement.UNKNOWN)
    override val entitlement: StateFlow<PremiumEntitlement> = mutableEntitlement.asStateFlow()
    private val operationMutex = Mutex()
    private var initialized = false
    private var currentOffer: StoreOffer? = null
    private var verifiedReceipt: CachedPlayReceipt? = null
    private var acknowledgementRetry: Job? = null
    private var expiry: Job? = null
    private var awaitingAcknowledgement = false

    init {
        gateway.onPurchasesUpdated = { result ->
            scope.launch {
                if (result.code == BillingResponseCode.ITEM_ALREADY_OWNED) {
                    refreshInternal(restoring = true, checkoutClosed = true)
                } else {
                    operationMutex.withLock {
                        when (result.code) {
                            BillingResponseCode.OK -> {
                                reconcile(result.value.orEmpty(), authoritative = false, restoring = false)
                            }
                            BillingResponseCode.USER_CANCELED -> mutableState.update {
                                it.copy(isPurchasing = false, message = PremiumBillingMessage.PURCHASE_CANCELLED)
                            }
                            else -> mutableState.update {
                                it.copy(isPurchasing = false, message = messageFor(result.code))
                            }
                        }
                    }
                }
            }
        }
        scope.launch { refresh() }
    }

    override suspend fun refresh() = refreshInternal(restoring = false)

    /** Returning to the host closes a checkout even if Play lost its listener callback. */
    suspend fun refreshAfterHostResume() =
        refreshInternal(restoring = false, checkoutClosed = state.value.isPurchasing)

    fun restore() {
        scope.launch { refreshInternal(restoring = true) }
    }

    fun retry() {
        scope.launch { refresh() }
    }

    /** Called from the UI thread following an explicit tap; never called during refresh/restore. */
    fun purchase(activity: Activity) {
        val offer = currentOffer
        if (!state.value.canPurchase || offer == null) return
        mutableState.update { it.copy(isPurchasing = true, message = null) }
        val result = try {
            gateway.launch(activity, offer)
        } catch (_: RuntimeException) {
            StoreResult<Unit>(BillingResponseCode.ERROR)
        }
        when (result.code) {
            BillingResponseCode.OK -> Unit // Ownership comes exclusively from a verified receipt.
            BillingResponseCode.ITEM_ALREADY_OWNED -> {
                mutableState.update { it.copy(isPurchasing = false) }
                restore()
            }
            else -> {
                mutableState.update { it.copy(isPurchasing = false, message = messageFor(result.code)) }
                if (result.code == BillingResponseCode.ITEM_UNAVAILABLE) {
                    currentOffer = null
                    mutableState.update { it.copy(offer = null) }
                }
            }
        }
    }

    private suspend fun refreshInternal(
        restoring: Boolean,
        checkoutClosed: Boolean = false,
    ) = operationMutex.withLock {
        mutableState.update {
            it.copy(isLoading = true, isPurchasing = it.isPurchasing && !checkoutClosed, message = null)
        }
        try {
            initializeEntitlement()
            expireIfNeeded()
            if (!verifier.isConfigured) {
                setEntitlement(PremiumEntitlement.FREE)
                currentOffer = null
                mutableState.update { it.copy(offer = null, message = PremiumBillingMessage.CONFIGURATION_REQUIRED) }
                return@withLock
            }
            val connection = storeCall { gateway.connect() }
            if (connection.code != BillingResponseCode.OK) {
                mutableState.update { it.copy(message = messageFor(connection.code)) }
                return@withLock
            }
            val purchases = storeCall { gateway.queryPurchases() }
            if (purchases.code == BillingResponseCode.OK && purchases.value != null) {
                reconcile(purchases.value, authoritative = true, restoring = restoring, checkoutClosed = checkoutClosed)
            } else {
                mutableState.update { it.copy(message = messageFor(purchases.code)) }
            }
            if (!entitlement.value.isPremium) {
                val offer = storeCall { gateway.queryOffer() }
                currentOffer = offer.value.takeIf { offer.code == BillingResponseCode.OK }
                mutableState.update {
                    it.copy(
                        offer = currentOffer?.display,
                        message = it.message ?: if (currentOffer == null) messageFor(offer.code) else null,
                    )
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            if (entitlement.value == PremiumEntitlement.UNKNOWN) setEntitlement(PremiumEntitlement.FREE)
            mutableState.update { it.copy(message = PremiumBillingMessage.STORE_UNAVAILABLE) }
        } finally {
            // Hide ads only during the bounded initial ownership lookup, never indefinitely offline.
            if (entitlement.value == PremiumEntitlement.UNKNOWN) setEntitlement(PremiumEntitlement.FREE)
            mutableState.update { it.copy(isLoading = false) }
        }
    }

    private suspend fun initializeEntitlement() {
        if (initialized) return
        initialized = true
        val cached = try { receipts.read() } catch (_: IOException) { null }
        val valid = cached?.takeIf {
            it.isWithinGrace(nowMillis()) && verifier.verify(it.receipt)?.let { purchase ->
                purchase.purchaseTimeMillis <= it.verifiedAtMillis + CLOCK_TOLERANCE_MILLIS
            } == true
        }
        if (valid != null) {
            rememberVerified(valid)
        } else {
            // A reinstall can belong to a buyer: keep UNKNOWN until the first Play result.
            try { receipts.clear() } catch (_: IOException) { }
        }
    }

    private suspend fun reconcile(
        purchases: List<StorePurchase>, authoritative: Boolean, restoring: Boolean,
        checkoutClosed: Boolean = true,
    ) {
        val relevant = purchases.filter { PlayPurchaseVerifier.PREMIUM_PRODUCT_ID in it.products }
        val completed = relevant.filter { it.state == StorePurchaseState.COMPLETED }
        val verified = completed.mapNotNull { purchase ->
            verifier.verify(purchase.receipt)?.takeIf {
                it.token == purchase.token && it.purchaseTimeMillis <= nowMillis() + CLOCK_TOLERANCE_MILLIS
            }?.let { purchase to it }
        }
        if (verified.isNotEmpty()) {
            mutableState.update { it.copy(isPending = false, isPurchasing = false) }
            awaitingAcknowledgement = false
            for ((purchase, validation) in verified) {
                val cached = CachedPlayReceipt(validation.receipt, nowMillis())
                rememberVerified(cached)
                val acknowledgement = if (purchase.acknowledged) StoreResult(BillingResponseCode.OK, Unit)
                    else storeCall { gateway.acknowledge(validation.token) }
                if (acknowledgement.code == BillingResponseCode.OK) {
                    try { receipts.write(cached) } catch (_: IOException) {
                        // Live ownership is valid; a later foreground query retries durable storage.
                    }
                } else {
                    awaitingAcknowledgement = true
                }
            }
            mutableState.update {
                it.copy(message = when {
                    awaitingAcknowledgement -> PremiumBillingMessage.ACKNOWLEDGEMENT_PENDING
                    restoring -> PremiumBillingMessage.PURCHASE_RESTORED
                    authoritative -> null
                    else -> PremiumBillingMessage.PURCHASE_COMPLETED
                })
            }
            if (awaitingAcknowledgement) scheduleAcknowledgementRetry()
            return
        }
        val pending = relevant.any { it.state == StorePurchaseState.PENDING }
        if (authoritative) {
            clearVerified() // Successful query is authoritative for cancellation/refund/account changes.
            awaitingAcknowledgement = false
        }
        mutableState.update {
            // A background ownership query can finish while the Play checkout is still open.
            // Only its callback or the host returning may close an unconfirmed checkout.
            it.copy(isPending = pending, isPurchasing = it.isPurchasing && !checkoutClosed, message = when {
                completed.isNotEmpty() -> PremiumBillingMessage.VERIFICATION_FAILED
                pending -> PremiumBillingMessage.PURCHASE_PENDING
                restoring -> PremiumBillingMessage.NO_PURCHASE_FOUND
                else -> it.message
            })
        }
    }

    private fun rememberVerified(receipt: CachedPlayReceipt) {
        verifiedReceipt = receipt
        setEntitlement(PremiumEntitlement.PREMIUM)
        expiry?.cancel()
        expiry = scope.launch {
            val remaining = receipt.verifiedAtMillis + CachedPlayReceipt.OFFLINE_GRACE_MILLIS - nowMillis()
            delay(remaining.coerceAtLeast(0) + 1)
            operationMutex.withLock {
                expiry = null // Do not cancel this coroutine before deleting an expired cache.
                expireIfNeeded()
            }
        }
    }

    private suspend fun expireIfNeeded() {
        if (verifiedReceipt?.isWithinGrace(nowMillis()) == false) clearVerified()
    }

    private suspend fun clearVerified() {
        verifiedReceipt = null
        expiry?.cancel()
        expiry = null
        setEntitlement(PremiumEntitlement.FREE)
        try { receipts.clear() } catch (_: IOException) {
            // Current-process entitlement remains revoked even if local storage is unavailable.
        }
    }

    private fun setEntitlement(value: PremiumEntitlement) {
        mutableEntitlement.value = value
        mutableState.update { it.copy(entitlement = value) }
    }

    private fun scheduleAcknowledgementRetry() {
        if (acknowledgementRetry?.isActive == true) return
        acknowledgementRetry = scope.launch {
            for (backoffMillis in listOf(2_000L, 10_000L, 30_000L, 120_000L)) {
                delay(backoffMillis)
                if (!awaitingAcknowledgement) break
                refresh()
            }
            // If the process stops, every next foreground query retries unacknowledged purchases.
        }
    }

    private suspend fun <T> storeCall(block: suspend () -> StoreResult<T>): StoreResult<T> =
        try {
            withTimeoutOrNull(20_000) { block() } ?: StoreResult(BillingResponseCode.SERVICE_UNAVAILABLE)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            StoreResult(BillingResponseCode.ERROR)
        }

    override fun close() {
        gateway.close()
        scope.cancel()
    }

    private fun messageFor(code: Int): PremiumBillingMessage = when (code) {
        BillingResponseCode.USER_CANCELED -> PremiumBillingMessage.PURCHASE_CANCELLED
        BillingResponseCode.NETWORK_ERROR -> PremiumBillingMessage.NETWORK_ERROR
        BillingResponseCode.ITEM_UNAVAILABLE -> PremiumBillingMessage.PRODUCT_UNAVAILABLE
        BillingResponseCode.SERVICE_DISCONNECTED, BillingResponseCode.SERVICE_UNAVAILABLE,
        BillingResponseCode.BILLING_UNAVAILABLE, BillingResponseCode.FEATURE_NOT_SUPPORTED ->
            PremiumBillingMessage.STORE_UNAVAILABLE
        else -> PremiumBillingMessage.PURCHASE_FAILED
    }

    private companion object {
        const val CLOCK_TOLERANCE_MILLIS = 5L * 60 * 1000
    }
}
