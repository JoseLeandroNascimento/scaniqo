package com.joseleandro.scaniqo.core.billing

import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.UnfetchedProduct
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayBillingDiagnosticsTest {
    @Test fun missingProductsRetainTheDistinctReasonReturnedByPlay() {
        val reasons = listOf(
            UnfetchedProduct.StatusCode.PRODUCT_NOT_FOUND to "PRODUCT_NOT_FOUND",
            UnfetchedProduct.StatusCode.NO_ELIGIBLE_OFFER to "NO_ELIGIBLE_OFFER",
            UnfetchedProduct.StatusCode.INVALID_PRODUCT_ID_FORMAT to "INVALID_PRODUCT_ID_FORMAT",
        )
        for ((status, description) in reasons) {
            val result = response(BillingResponseCode.OK, "Product query completed")
                .toOfferResult(offer = null, unfetchedProductStatusCode = status)

            // Existing UI still receives ITEM_UNAVAILABLE, while diagnosis retains Play's reason.
            assertEquals(BillingResponseCode.ITEM_UNAVAILABLE, result.code)
            assertNull(result.value)
            assertEquals(status, result.unfetchedProductStatusCode)
            assertEquals("Product query completed", result.debugMessage)
            val message = result.diagnosticMessage("queryOffer", responseCode = BillingResponseCode.OK)
            assertTrue(message.contains("responseCode=0"))
            assertTrue(message.contains("resultCode=4"))
            assertTrue(message.contains("unfetchedProductStatus=$status($description)"))
        }
    }

    @Test fun failedQueryKeepsTheGlobalFailureInsteadOfClaimingTheProductIsUnavailable() {
        val result = response(BillingResponseCode.DEVELOPER_ERROR, "Invalid query parameters")
            .toOfferResult(offer = null)

        assertEquals(BillingResponseCode.DEVELOPER_ERROR, result.code)
        assertEquals("Invalid query parameters", result.debugMessage)
        assertNull(result.unfetchedProductStatusCode)
        assertTrue(result.diagnosticMessage("queryOffer").contains("responseCode=5"))
    }

    @Test fun successfulOfferKeepsTheExactPriceAndOfferReturnedByPlay() {
        val offer = StoreOffer(PremiumOffer("scaniqo_premium", "R$ 29,90", "BRL"), "offer-token")
        val result = response(BillingResponseCode.OK, "Success").toOfferResult(offer)

        assertEquals(BillingResponseCode.OK, result.code)
        assertSame(offer, result.value)
        assertEquals("R$ 29,90", result.value?.display?.formattedPrice)
        assertNull(result.unfetchedProductStatusCode)
    }

    @Test fun anEmptyQueryDoesNotInventAnUnfetchedProductReason() {
        val result = response(BillingResponseCode.OK, "").toOfferResult(offer = null)

        assertEquals(BillingResponseCode.ITEM_UNAVAILABLE, result.code)
        assertNull(result.unfetchedProductStatusCode)
        assertFalse(result.diagnosticMessage("queryOffer").contains("unfetchedProductStatus"))
    }

    @Test fun loggingAResultNeverSerializesPurchaseData() {
        val purchase = StorePurchase(
            receipt = SignedPlayReceipt("private-receipt-json", "private-signature"),
            token = "private-purchase-token",
            products = listOf("private-product"),
            state = StorePurchaseState.COMPLETED,
            acknowledged = false,
        )
        val result = response(BillingResponseCode.OK, "First line\nSecond line\r")
            .toStoreResult(listOf(purchase))

        assertSame(purchase, result.value?.single())
        assertEquals("First line\nSecond line\r", result.debugMessage)
        val message = result.diagnosticMessage("queryPurchases")
        assertTrue(message.contains("operation=queryPurchases"))
        assertTrue(message.contains("reason=First line Second line "))
        assertFalse(message.contains("private-"))
        assertFalse(message.contains("StorePurchase"))
        assertFalse(message.contains('\n'))
        assertFalse(message.contains('\r'))
    }

    private fun response(code: Int, message: String): BillingResult =
        BillingResult.newBuilder().setResponseCode(code).setDebugMessage(message).build()
}
