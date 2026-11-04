package com.joseleandro.scaniqo.di

import com.google.mlkit.vision.documentscanner.GmsDocumentScanner
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.joseleandro.scaniqo.core.ads.di.adsModule
import com.joseleandro.scaniqo.core.localization.LocalizedNames
import com.joseleandro.scaniqo.core.navigation.NavigationViewModel
import com.joseleandro.scaniqo.core.security.di.securityModule
import com.joseleandro.scaniqo.data.backup.di.backupDataModule
import com.joseleandro.scaniqo.data.local.di.documentsDataModule
import com.joseleandro.scaniqo.data.pdf.di.pdfDataModule
import com.joseleandro.scaniqo.data.preferences.di.preferencesDataModule
import com.joseleandro.scaniqo.data.signatures.di.signaturesDataModule
import com.joseleandro.scaniqo.ui.backup.di.backupFeatureModule
import com.joseleandro.scaniqo.ui.documentCommon.di.documentActionsModule
import com.joseleandro.scaniqo.ui.documentEditor.di.editorFeatureModule
import com.joseleandro.scaniqo.ui.documentMerge.di.mergeFeatureModule
import com.joseleandro.scaniqo.ui.documents.di.libraryFeatureModule
import com.joseleandro.scaniqo.ui.onboarding.di.onboardingFeatureModule
import com.joseleandro.scaniqo.ui.pdfTools.di.pdfToolsFeatureModule
import com.joseleandro.scaniqo.ui.settings.di.settingsFeatureModule
import com.joseleandro.scaniqo.ui.signatures.di.signaturesFeatureModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val appModule = module {
    includes(
        documentsDataModule,
        pdfDataModule,
        preferencesDataModule,
        signaturesDataModule,
        backupDataModule,
        adsModule,
        securityModule,
        documentActionsModule,
        libraryFeatureModule,
        editorFeatureModule,
        signaturesFeatureModule,
        mergeFeatureModule,
        pdfToolsFeatureModule,
        backupFeatureModule,
        settingsFeatureModule,
        onboardingFeatureModule,
    )
    viewModelOf(::NavigationViewModel)
    single { LocalizedNames(androidContext()) }
    single {
        GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(20)
            // O PDF final é gerado pelo app para manter o mesmo enquadramento A4.
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()
    }
    single<GmsDocumentScanner> {
        GmsDocumentScanning.getClient(get<GmsDocumentScannerOptions>())
    }
}
