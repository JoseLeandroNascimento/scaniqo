package com.joseleandro.scaniqo.ui.documentMerge.di

import com.joseleandro.scaniqo.ui.documentMerge.DocumentQuickMergeViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val mergeFeatureModule = module {
    viewModel { DocumentQuickMergeViewModel(get(), get(), get(), get(), adAccess = get()) }
}
