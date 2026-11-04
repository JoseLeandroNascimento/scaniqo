package com.joseleandro.scaniqo.data.local.di

import androidx.room.Room
import com.joseleandro.scaniqo.data.datasource.DocumentDatasource
import com.joseleandro.scaniqo.data.datasource.DocumentDatasourceImpl
import com.joseleandro.scaniqo.data.datasource.TagDatasource
import com.joseleandro.scaniqo.data.datasource.TagDatasourceImpl
import com.joseleandro.scaniqo.data.local.ScaniqoDatabase
import com.joseleandro.scaniqo.data.local.document.AndroidDocumentFileStore
import com.joseleandro.scaniqo.data.local.document.DocumentDao
import com.joseleandro.scaniqo.data.local.document.DocumentFileStore
import com.joseleandro.scaniqo.data.local.tag.DefaultTagInitializer
import com.joseleandro.scaniqo.data.local.tag.LocalizedDefaultTags
import com.joseleandro.scaniqo.data.local.tag.TagDao
import com.joseleandro.scaniqo.data.repository.DocumentRepositoryImpl
import com.joseleandro.scaniqo.data.repository.TagRepositoryImpl
import com.joseleandro.scaniqo.domain.repository.DocumentRepository
import com.joseleandro.scaniqo.domain.repository.TagRepository
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val documentsDataModule = module {
    single<ScaniqoDatabase> {
        Room.databaseBuilder(
            androidContext(),
            ScaniqoDatabase::class.java,
            "scaniqo.db",
        ).build()
    }
    single<DocumentDao> { get<ScaniqoDatabase>().documentDao() }
    single<DocumentDatasource> { DocumentDatasourceImpl(get()) }
    single<DocumentFileStore> { AndroidDocumentFileStore(androidContext()) }
    single<DocumentRepository> { DocumentRepositoryImpl(get(), get()) }
    single<TagDao> { get<ScaniqoDatabase>().tagDao() }
    single<TagDatasource> { TagDatasourceImpl(tagDao = get()) }
    single { DefaultTagInitializer(get(), LocalizedDefaultTags(androidContext())::tags) }
    single<TagRepository> {
        TagRepositoryImpl(
            tagDatasource = get(),
            initializeDefaults = get<DefaultTagInitializer>()::initialize,
        )
    }
}
