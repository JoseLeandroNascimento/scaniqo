package com.joseleandro.scaniqo.core.ads.di

import com.joseleandro.scaniqo.core.ads.AdMobManager
import com.joseleandro.scaniqo.core.ads.RewardedAdManager
import com.joseleandro.scaniqo.core.ads.ToolAdAccessPolicy
import com.joseleandro.scaniqo.core.ads.ToolAdAccessStore
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val adsModule = module {
    single { AdMobManager(androidContext()) }
    single<ToolAdAccessPolicy> { ToolAdAccessStore(androidContext()) }
    single { RewardedAdManager(get(), get()) }
}
