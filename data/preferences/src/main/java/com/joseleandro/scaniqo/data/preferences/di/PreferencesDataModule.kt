package com.joseleandro.scaniqo.data.preferences.di

import com.joseleandro.scaniqo.data.repository.AndroidAppSettingsRepository
import com.joseleandro.scaniqo.data.repository.AndroidFormValueRepository
import com.joseleandro.scaniqo.data.repository.AndroidOnboardingRepository
import com.joseleandro.scaniqo.domain.repository.AppSettingsRepository
import com.joseleandro.scaniqo.domain.repository.FormValueRepository
import com.joseleandro.scaniqo.domain.repository.OnboardingRepository
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val preferencesDataModule = module {
    single<AppSettingsRepository> { AndroidAppSettingsRepository(androidContext()) }
    single<OnboardingRepository> { AndroidOnboardingRepository(androidContext()) }
    single<FormValueRepository> { AndroidFormValueRepository(androidContext()) }
}
