package com.joseleandro.scaniqo.data.signatures.di

import com.joseleandro.scaniqo.data.repository.AndroidSignatureRepository
import com.joseleandro.scaniqo.domain.repository.SignatureRepository
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val signaturesDataModule = module {
    single<SignatureRepository> { AndroidSignatureRepository(androidContext()) }
}
