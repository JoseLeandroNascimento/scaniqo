package com.joseleandro.scaniqo.core.billing.di

import com.joseleandro.scaniqo.core.billing.PlayBillingManager
import com.joseleandro.scaniqo.core.billing.R
import com.joseleandro.scaniqo.domain.repository.PremiumEntitlementRepository
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val billingModule = module {
    single {
        val context = androidContext()
        PlayBillingManager(context, context.getString(R.string.scaniqo_play_license_key))
    }
    single<PremiumEntitlementRepository> { get<PlayBillingManager>() }
}
