package com.joseleandro.scaniqo.data.pdf.di

import com.joseleandro.scaniqo.data.documentCertificates.AndroidPdfCertificateRepository
import com.joseleandro.scaniqo.data.pdf.AndroidPdfEditorRepository
import com.joseleandro.scaniqo.data.pdf.AndroidDocumentReaderRepository
import com.joseleandro.scaniqo.data.pdf.AndroidPdfUtilityRepository
import com.joseleandro.scaniqo.data.repository.AndroidDocumentSearchRepository
import com.joseleandro.scaniqo.domain.repository.DocumentSearchRepository
import com.joseleandro.scaniqo.domain.repository.DocumentReaderRepository
import com.joseleandro.scaniqo.domain.repository.PdfCertificateRepository
import com.joseleandro.scaniqo.domain.repository.PdfEditorRepository
import com.joseleandro.scaniqo.domain.repository.PdfUtilityRepository
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val pdfDataModule = module {
    single<DocumentReaderRepository> { AndroidDocumentReaderRepository(androidContext()) }
    single<DocumentSearchRepository> { AndroidDocumentSearchRepository(androidContext()) }
    single<PdfUtilityRepository> { AndroidPdfUtilityRepository(androidContext()) }
    single<PdfCertificateRepository> { AndroidPdfCertificateRepository(androidContext()) }
    single<PdfEditorRepository> {
        AndroidPdfEditorRepository(androidContext(), get(), get(), settingsRepository = get())
    }
}
