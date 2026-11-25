package com.joseleandro.scaniqo.domain.repository

import kotlinx.coroutines.flow.StateFlow

enum class PremiumEntitlement {
    UNKNOWN,
    FREE,
    PREMIUM;

    val isPremium: Boolean get() = this == PREMIUM
}

/** Access is granted by a verified, completed Google Play purchase. */
interface PremiumEntitlementRepository {
    val entitlement: StateFlow<PremiumEntitlement>
    suspend fun refresh()
}
