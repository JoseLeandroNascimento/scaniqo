package com.joseleandro.scaniqo.ui.premium

import androidx.annotation.StringRes
import com.joseleandro.scaniqo.core.billing.PremiumBillingMessage
import com.joseleandro.scaniqo.core.billing.PremiumBillingState
import com.joseleandro.scaniqo.feature.premium.R

internal enum class PremiumAction { PURCHASE, RETRY, CHECK_PURCHASE, CONTINUE, WAIT }

internal data class PremiumPresentation(
    val action: PremiumAction,
    @param:StringRes val statusTitle: Int? = null,
    @param:StringRes val statusBody: Int? = null,
    val statusIsError: Boolean = false,
    val canRestore: Boolean = true,
)

/** Somente um direito confirmado pode apresentar a tela como Premium ativo. */
internal fun PremiumBillingState.presentation(): PremiumPresentation {
    if (entitlement.isPremium) return PremiumPresentation(
        action = PremiumAction.CONTINUE,
        statusTitle = R.string.premium_active_title,
        statusBody = R.string.premium_active_body,
        canRestore = !isLoading && !isPurchasing,
    )
    val canRestore = !isLoading && !isPurchasing
    if (isPending || message == PremiumBillingMessage.PURCHASE_PENDING ||
        message == PremiumBillingMessage.ACKNOWLEDGEMENT_PENDING) {
        return PremiumPresentation(
            action = if (isLoading) PremiumAction.WAIT else PremiumAction.CHECK_PURCHASE,
            statusTitle = R.string.premium_pending_title,
            statusBody = if (message == PremiumBillingMessage.ACKNOWLEDGEMENT_PENDING)
                R.string.premium_activation_pending else R.string.premium_pending_body,
            canRestore = canRestore,
        )
    }
    if (isPurchasing) return PremiumPresentation(
        action = PremiumAction.WAIT,
        statusTitle = R.string.premium_purchasing_title,
        statusBody = R.string.premium_purchasing_body,
        canRestore = false,
    )
    if (isLoading) return PremiumPresentation(action = PremiumAction.WAIT, canRestore = false)
    if (!isConfigured || message == PremiumBillingMessage.CONFIGURATION_REQUIRED) {
        return PremiumPresentation(
            action = PremiumAction.RETRY,
            statusTitle = R.string.premium_unavailable_title,
            statusBody = R.string.premium_unavailable_body,
            canRestore = canRestore,
        )
    }
    if (message == PremiumBillingMessage.VERIFICATION_FAILED) return PremiumPresentation(
        action = PremiumAction.CHECK_PURCHASE,
        statusTitle = R.string.premium_confirmation_title,
        statusBody = R.string.premium_verification_failed,
        statusIsError = true,
        canRestore = canRestore,
    )
    val body = when (message) {
        PremiumBillingMessage.STORE_UNAVAILABLE -> R.string.premium_store_unavailable
        PremiumBillingMessage.NETWORK_ERROR -> R.string.premium_network_error
        PremiumBillingMessage.PRODUCT_UNAVAILABLE -> R.string.premium_unavailable_body
        PremiumBillingMessage.PURCHASE_CANCELLED -> R.string.premium_cancelled
        PremiumBillingMessage.NO_PURCHASE_FOUND -> R.string.premium_no_purchase
        PremiumBillingMessage.PURCHASE_FAILED -> R.string.premium_purchase_failed
        else -> null
    }
    val error = message in setOf(
        PremiumBillingMessage.STORE_UNAVAILABLE,
        PremiumBillingMessage.NETWORK_ERROR,
        PremiumBillingMessage.PURCHASE_FAILED,
    )
    val refreshRequired = message in setOf(
        PremiumBillingMessage.STORE_UNAVAILABLE,
        PremiumBillingMessage.NETWORK_ERROR,
        PremiumBillingMessage.PRODUCT_UNAVAILABLE,
    )
    return PremiumPresentation(
        action = if (canPurchase && !refreshRequired) PremiumAction.PURCHASE else PremiumAction.RETRY,
        statusTitle = if (body != null && error) R.string.premium_error_title else null,
        statusBody = body,
        statusIsError = error,
        canRestore = canRestore,
    )
}
