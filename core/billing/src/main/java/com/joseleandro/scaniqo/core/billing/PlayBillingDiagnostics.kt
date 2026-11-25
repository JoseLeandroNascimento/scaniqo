package com.joseleandro.scaniqo.core.billing

import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.UnfetchedProduct

internal fun <T> BillingResult.toStoreResult(value: T? = null): StoreResult<T> =
    StoreResult(responseCode, value, debugMessage)

/** Keep the original Play reason when an OK query contains no purchasable offer. */
internal fun BillingResult.toOfferResult(
    offer: StoreOffer?,
    unfetchedProductStatusCode: Int? = null,
): StoreResult<StoreOffer> = StoreResult(
    code = if (responseCode == BillingResponseCode.OK && offer == null)
        BillingResponseCode.ITEM_UNAVAILABLE else responseCode,
    value = offer,
    debugMessage = debugMessage,
    unfetchedProductStatusCode = unfetchedProductStatusCode,
)

/** Deliberately excludes value: it can contain purchase tokens and signed receipts. */
internal fun StoreResult<*>.diagnosticMessage(operation: String, responseCode: Int = code): String =
    buildString {
        append("operation=").append(operation)
        append(" responseCode=").append(responseCode)
        if (code != responseCode) append(" resultCode=").append(code)
        unfetchedProductStatusCode?.let { status ->
            append(" unfetchedProductStatus=").append(status).append('(')
            append(when (status) {
                UnfetchedProduct.StatusCode.INVALID_PRODUCT_ID_FORMAT -> "INVALID_PRODUCT_ID_FORMAT"
                UnfetchedProduct.StatusCode.PRODUCT_NOT_FOUND -> "PRODUCT_NOT_FOUND"
                UnfetchedProduct.StatusCode.NO_ELIGIBLE_OFFER -> "NO_ELIGIBLE_OFFER"
                else -> "UNKNOWN"
            })
            append(')')
        }
        if (debugMessage.isNotBlank()) {
            append(" reason=").append(debugMessage.replace('\n', ' ').replace('\r', ' ').take(500))
        }
    }
