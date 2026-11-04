package com.joseleandro.scaniqo.ui.pdfTools.di

import com.joseleandro.scaniqo.ui.documentCertificates.DocumentCertificatesViewModel
import com.joseleandro.scaniqo.ui.pdfTools.PdfUtilityViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val pdfToolsFeatureModule = module {
    viewModel { PdfUtilityViewModel(get(), get(), get(), get()) }
    viewModelOf(::DocumentCertificatesViewModel)
}
