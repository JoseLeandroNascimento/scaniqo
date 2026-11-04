package com.joseleandro.scaniqo.ui.signatures.di

import com.joseleandro.scaniqo.core.localization.LocalizedNames
import com.joseleandro.scaniqo.ui.signatures.SignaturesViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val signaturesFeatureModule = module {
    viewModel { SignaturesViewModel(get(), get<LocalizedNames>()::signature) }
}
