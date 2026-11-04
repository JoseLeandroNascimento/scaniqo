package com.joseleandro.scaniqo.core.security.di

import com.joseleandro.scaniqo.core.security.PasswordAccess
import com.joseleandro.scaniqo.core.security.PasswordVault
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val securityModule = module {
    single { PasswordVault(androidContext()) }
    single<PasswordAccess> { get<PasswordVault>() }
}
