package com.joseleandro.scaniqo.ui.onboarding.di

import com.joseleandro.scaniqo.ui.onboarding.OnboardingViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val onboardingFeatureModule = module {
    viewModelOf(::OnboardingViewModel)
}
