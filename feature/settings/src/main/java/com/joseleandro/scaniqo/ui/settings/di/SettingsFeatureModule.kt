package com.joseleandro.scaniqo.ui.settings.di

import com.joseleandro.scaniqo.ui.settings.SettingsViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val settingsFeatureModule = module {
    viewModelOf(::SettingsViewModel)
}
