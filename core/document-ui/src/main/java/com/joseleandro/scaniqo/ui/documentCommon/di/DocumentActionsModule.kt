package com.joseleandro.scaniqo.ui.documentCommon.di

import com.joseleandro.scaniqo.core.localization.LocalizedNames
import com.joseleandro.scaniqo.ui.documentCommon.DocumentActionsViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val documentActionsModule = module {
    viewModel {
        DocumentActionsViewModel(get(), get(), get(), get<LocalizedNames>()::document, adAccess = get())
    }
}
