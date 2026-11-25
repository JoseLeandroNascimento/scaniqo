package com.joseleandro.scaniqo.ui.documentEditor.di

import com.joseleandro.scaniqo.ui.documentEditor.DocumentReaderViewModel
import com.joseleandro.scaniqo.ui.documentEditor.DocumentToolsViewModel
import com.joseleandro.scaniqo.ui.documentEditor.DocumentViewerViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val editorFeatureModule = module {
    viewModelOf(::DocumentViewerViewModel)
    viewModelOf(::DocumentReaderViewModel)
    viewModel { DocumentToolsViewModel(get(), get(), get(), get(), adAccess = get()) }
}
