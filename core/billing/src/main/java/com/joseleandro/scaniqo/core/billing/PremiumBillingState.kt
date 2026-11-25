package com.joseleandro.scaniqo.core.billing

import com.joseleandro.scaniqo.domain.repository.PremiumEntitlement

data class PremiumOffer(
    val productId: String,
    val formattedPrice: String,
    val currencyCode: String,
)

enum class PremiumBillingMessage {
    CONFIGURATION_REQUIRED,
    STORE_UNAVAILABLE,
    NETWORK_ERROR,
    PRODUCT_UNAVAILABLE,
    PURCHASE_PENDING,
    PURCHASE_CANCELLED,
    PURCHASE_RESTORED,
    NO_PURCHASE_FOUND,
    VERIFICATION_FAILED,
    PURCHASE_FAILED,
    ACKNOWLEDGEMENT_PENDING,
    PURCHASE_COMPLETED,
}

data class PremiumBillingState(
    val entitlement: PremiumEntitlement = PremiumEntitlement.UNKNOWN,
    val offer: PremiumOffer? = null,
    val isLoading: Boolean = false,
    val isPurchasing: Boolean = false,
    val isPending: Boolean = false,
    val isConfigured: Boolean = false,
    val message: PremiumBillingMessage? = null,
) {
    val canPurchase: Boolean get() = isConfigured && offer != null &&
        !isLoading && !isPurchasing && !isPending && !entitlement.isPremium
}
