package com.joseleandro.scaniqo.core.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

internal class AndroidPlayBillingGateway(context: Context) : PlayBillingGateway {
    private val applicationContext = context.applicationContext
    override var onPurchasesUpdated: ((StoreResult<List<StorePurchase>>) -> Unit)? = null
    private var client = createClient()
    private var currentProduct: ProductDetails? = null
    private var currentOffer: StoreOffer? = null

    private fun createClient(): BillingClient = BillingClient.newBuilder(applicationContext)
        .setListener { result, purchases ->
            onPurchasesUpdated?.invoke(
                result.toStoreResult(purchases?.map(::toStorePurchase)).logged("purchasesUpdated"),
            )
        }
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    override suspend fun connect(): StoreResult<Unit> {
        if (client.isReady) return StoreResult(BillingClient.BillingResponseCode.OK, Unit)
            .logged("connect")
        val connectingClient = client
        return withTimeoutOrNull(15_000) {
            suspendCancellableCoroutine { continuation ->
                connectingClient.startConnection(object : BillingClientStateListener {
                    override fun onBillingSetupFinished(result: BillingResult) {
                        val connection = result.toStoreResult(Unit).logged("connect")
                        if (continuation.isActive) continuation.resume(connection)
                    }
                    override fun onBillingServiceDisconnected() {
                        if (continuation.isActive) continuation.resume(
                            StoreResult<Unit>(
                                BillingClient.BillingResponseCode.SERVICE_DISCONNECTED,
                                debugMessage = "Play Billing service disconnected during setup",
                            ).logged("connect"),
                        )
                    }
                })
            }
        } ?: run {
            connectingClient.endConnection()
            client = createClient()
            currentProduct = null
            currentOffer = null
            StoreResult<Unit>(
                BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE,
                debugMessage = "Play Billing setup timed out after 15000 ms",
            ).logged("connect")
        }
    }

    override suspend fun queryPurchases(): StoreResult<List<StorePurchase>> =
        suspendCancellableCoroutine { continuation ->
            client.queryPurchasesAsync(
                QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build(),
            ) { result, purchases ->
                if (continuation.isActive) continuation.resume(
                    result.toStoreResult(purchases.map(::toStorePurchase)).logged("queryPurchases"),
                )
            }
        }

    override suspend fun queryOffer(): StoreResult<StoreOffer> =
        suspendCancellableCoroutine { continuation ->
            currentProduct = null
            currentOffer = null
            client.queryProductDetailsAsync(
                QueryProductDetailsParams.newBuilder().setProductList(listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PlayPurchaseVerifier.PREMIUM_PRODUCT_ID)
                        .setProductType(BillingClient.ProductType.INAPP).build(),
                )).build(),
            ) { result, details ->
                val product = details.productDetailsList.firstOrNull {
                    it.productId == PlayPurchaseVerifier.PREMIUM_PRODUCT_ID &&
                        it.productType == BillingClient.ProductType.INAPP
                }
                val eligibleOffers = product?.oneTimePurchaseOfferDetailsList.orEmpty().ifEmpty {
                    listOfNotNull(product?.oneTimePurchaseOfferDetails)
                }
                // Premium is permanent: never launch a rental or preorder purchase option.
                val offer = eligibleOffers.filter {
                    it.rentalDetails == null && it.preorderDetails == null
                }.minByOrNull { it.priceAmountMicros }
                val storeOffer = offer?.let {
                    StoreOffer(
                        PremiumOffer(PlayPurchaseVerifier.PREMIUM_PRODUCT_ID,
                            it.formattedPrice, it.priceCurrencyCode),
                        it.offerToken?.takeIf(String::isNotBlank),
                    )
                }
                val unfetchedStatus = details.unfetchedProductList.firstOrNull {
                    it.productId == PlayPurchaseVerifier.PREMIUM_PRODUCT_ID &&
                        it.productType == BillingClient.ProductType.INAPP
                }?.statusCode
                val offerResult = result.toOfferResult(storeOffer, unfetchedStatus)
                    .logged("queryOffer", responseCode = result.responseCode)
                if (continuation.isActive) {
                    if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                        currentProduct = product
                        currentOffer = storeOffer
                    }
                    continuation.resume(offerResult)
                }
            }
        }

    override suspend fun acknowledge(token: String): StoreResult<Unit> =
        suspendCancellableCoroutine { continuation ->
            client.acknowledgePurchase(
                AcknowledgePurchaseParams.newBuilder().setPurchaseToken(token).build(),
            ) { result ->
                val acknowledgement = result.toStoreResult(Unit).logged("acknowledge")
                if (continuation.isActive) continuation.resume(acknowledgement)
            }
        }

    override fun launch(activity: Activity, offer: StoreOffer): StoreResult<Unit> {
        val product = currentProduct
        if (product == null || currentOffer != offer || activity.isFinishing || activity.isDestroyed) {
            return StoreResult<Unit>(
                BillingClient.BillingResponseCode.ITEM_UNAVAILABLE,
                debugMessage = "Purchase host or current product offer is unavailable",
            ).logged("launch")
        }
        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(product)
        // Legacy one-time products can have no offer token.
        offer.offerToken?.let(productParams::setOfferToken)
        val result = client.launchBillingFlow(activity,
            BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(
                productParams.build(),
            )).build(),
        )
        return result.toStoreResult(Unit).logged("launch")
    }

    override fun close() {
        onPurchasesUpdated = null
        currentProduct = null
        currentOffer = null
        client.endConnection()
    }

    private fun <T> StoreResult<T>.logged(operation: String, responseCode: Int = code): StoreResult<T> {
        val message = diagnosticMessage(operation, responseCode)
        if (code == BillingClient.BillingResponseCode.OK) Log.d("ScaniqoBilling", message)
        else Log.w("ScaniqoBilling", message)
        return this
    }

    private fun toStorePurchase(purchase: Purchase): StorePurchase = StorePurchase(
        receipt = SignedPlayReceipt(purchase.originalJson, purchase.signature),
        token = purchase.purchaseToken,
        products = purchase.products,
        state = when (purchase.purchaseState) {
            Purchase.PurchaseState.PURCHASED -> StorePurchaseState.COMPLETED
            Purchase.PurchaseState.PENDING -> StorePurchaseState.PENDING
            else -> StorePurchaseState.OTHER
        },
        acknowledged = purchase.isAcknowledged,
    )
}
