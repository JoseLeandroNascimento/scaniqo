package com.joseleandro.scaniqo.core.ads.di

import com.joseleandro.scaniqo.core.ads.AdMobManager
import com.joseleandro.scaniqo.core.ads.RewardedAdManager
import com.joseleandro.scaniqo.core.ads.ToolAdAccessPolicy
import com.joseleandro.scaniqo.core.ads.ToolAdAccessStore
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val adsModule = module {
    single { AdMobManager(androidContext(), get()) }
    single<ToolAdAccessPolicy> { ToolAdAccessStore(androidContext(), get()) }
    single { RewardedAdManager(get(), get()) }
}
