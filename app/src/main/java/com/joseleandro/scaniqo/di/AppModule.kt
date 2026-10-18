package com.joseleandro.scaniqo.di

import androidx.room.Room
import com.google.mlkit.vision.documentscanner.GmsDocumentScanner
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.joseleandro.scaniqo.core.navigation.NavigationViewModel
import com.joseleandro.scaniqo.core.security.PasswordAccess
import com.joseleandro.scaniqo.core.security.PasswordVault
import com.joseleandro.scaniqo.data.backup.AndroidDocumentBackupRepository
import com.joseleandro.scaniqo.data.backup.GoogleDriveBackupRemote
import com.joseleandro.scaniqo.domain.repository.DocumentBackupRepository
import com.joseleandro.scaniqo.domain.repository.DriveBackupRemote
import com.joseleandro.scaniqo.ui.backup.DriveBackupViewModel
import com.joseleandro.scaniqo.data.datasource.DocumentDatasource
import com.joseleandro.scaniqo.data.datasource.DocumentDatasourceImpl
import com.joseleandro.scaniqo.data.datasource.TagDatasource
import com.joseleandro.scaniqo.data.datasource.TagDatasourceImpl
import com.joseleandro.scaniqo.data.local.ScaniqoDatabase
import com.joseleandro.scaniqo.data.local.document.AndroidDocumentFileStore
import com.joseleandro.scaniqo.data.local.document.DocumentDao
import com.joseleandro.scaniqo.data.local.document.DocumentFileStore
import com.joseleandro.scaniqo.data.local.tag.TagDao
import com.joseleandro.scaniqo.data.pdf.AndroidPdfEditorRepository
import com.joseleandro.scaniqo.data.repository.AndroidSignatureRepository
import com.joseleandro.scaniqo.data.repository.DocumentRepositoryImpl
import com.joseleandro.scaniqo.data.repository.TagRepositoryImpl
import com.joseleandro.scaniqo.domain.repository.DocumentRepository
import com.joseleandro.scaniqo.domain.repository.PdfEditorRepository
import com.joseleandro.scaniqo.domain.repository.SignatureRepository
import com.joseleandro.scaniqo.domain.repository.TagRepository
import com.joseleandro.scaniqo.ui.documentEditor.DocumentToolsViewModel
import com.joseleandro.scaniqo.ui.documentEditor.DocumentViewerViewModel
import com.joseleandro.scaniqo.ui.documentCommon.DocumentActionsViewModel
import com.joseleandro.scaniqo.ui.documentMerge.DocumentQuickMergeViewModel
import com.joseleandro.scaniqo.ui.documents.DocumentsViewModel
import com.joseleandro.scaniqo.ui.documents.PdfImportViewModel
import com.joseleandro.scaniqo.ui.signatures.SignaturesViewModel
import com.joseleandro.scaniqo.ui.tags.TagsViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val appModule = module {

    viewModelOf(::NavigationViewModel)

    viewModelOf(::TagsViewModel)
    viewModelOf(::DocumentsViewModel)
    viewModelOf(::DocumentActionsViewModel)
    viewModelOf(::DocumentViewerViewModel)
    viewModelOf(::DocumentToolsViewModel)
    viewModelOf(::PdfImportViewModel)
    viewModelOf(::DocumentQuickMergeViewModel)
    viewModelOf(::SignaturesViewModel)
    viewModelOf(::DriveBackupViewModel)
    single<DocumentBackupRepository> { AndroidDocumentBackupRepository(androidContext(), get(), get()) }
    single<DriveBackupRemote> { GoogleDriveBackupRemote() }
    single { PasswordVault(androidContext()) }
    single<PasswordAccess> { get<PasswordVault>() }
    single<SignatureRepository> { AndroidSignatureRepository(androidContext()) }
    single<PdfEditorRepository> { AndroidPdfEditorRepository(androidContext(), get(), get()) }

    single<ScaniqoDatabase>{
        Room.databaseBuilder(
            androidContext(),
            ScaniqoDatabase::class.java,
            "scaniqo.db"
        ).build()
    }
    single<DocumentDao> { get<ScaniqoDatabase>().documentDao() }
    single<DocumentDatasource> { DocumentDatasourceImpl(get()) }
    single<DocumentFileStore> { AndroidDocumentFileStore(androidContext()) }
    single<DocumentRepository> { DocumentRepositoryImpl(get(), get()) }

    single<TagDao>{ get<ScaniqoDatabase>().tagDao() }

    single<TagDatasource>{
        TagDatasourceImpl(
            tagDao = get()
        )
    }

    single<TagRepository> { TagRepositoryImpl(tagDatasource = get()) }

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
