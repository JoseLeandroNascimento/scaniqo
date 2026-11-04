package com.joseleandro.scaniqo.ui.documents.di

import com.joseleandro.scaniqo.core.localization.LocalizedNames
import com.joseleandro.scaniqo.ui.documentSearch.DocumentSearchViewModel
import com.joseleandro.scaniqo.ui.documents.DocumentsViewModel
import com.joseleandro.scaniqo.ui.documents.PdfImportViewModel
import com.joseleandro.scaniqo.ui.tags.TagsViewModel
import com.joseleandro.scaniqo.ui.trash.TrashViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val libraryFeatureModule = module {
    viewModelOf(::TagsViewModel)
    // Usa o dispatcher padrão do ViewModel sem exigir um binding global no Koin.
    viewModel { DocumentsViewModel(get(), get(), get()) }
    viewModelOf(::TrashViewModel)
    viewModelOf(::DocumentSearchViewModel)
    viewModel {
        PdfImportViewModel(get(), get(), get(), get(), get<LocalizedNames>()::importedDocument)
    }
}
