package com.joseleandro.scaniqo.core.billing

import android.app.Activity

internal data class StoreResult<T>(
    val code: Int,
    val value: T? = null,
    val debugMessage: String = "",
    val unfetchedProductStatusCode: Int? = null,
)
internal data class StoreOffer(val display: PremiumOffer, val offerToken: String?)
internal enum class StorePurchaseState { COMPLETED, PENDING, OTHER }
internal data class StorePurchase(
    val receipt: SignedPlayReceipt,
    val token: String,
    val products: List<String>,
    val state: StorePurchaseState,
    val acknowledged: Boolean,
)

internal interface PlayBillingGateway {
    var onPurchasesUpdated: ((StoreResult<List<StorePurchase>>) -> Unit)?
    suspend fun connect(): StoreResult<Unit>
    suspend fun queryPurchases(): StoreResult<List<StorePurchase>>
    suspend fun queryOffer(): StoreResult<StoreOffer>
    suspend fun acknowledge(token: String): StoreResult<Unit>
    fun launch(activity: Activity, offer: StoreOffer): StoreResult<Unit>
    fun close()
}
