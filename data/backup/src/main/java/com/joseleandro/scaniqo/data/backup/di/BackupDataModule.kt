package com.joseleandro.scaniqo.data.backup.di

import com.joseleandro.scaniqo.data.backup.AndroidBackupHistoryRepository
import com.joseleandro.scaniqo.data.backup.AndroidBackupSettingsRepository
import com.joseleandro.scaniqo.data.backup.AndroidDocumentBackupRepository
import com.joseleandro.scaniqo.data.backup.AutomaticBackupRunner
import com.joseleandro.scaniqo.data.backup.BackupAuthorizationProvider
import com.joseleandro.scaniqo.data.backup.BackupSnapshotFingerprint
import com.joseleandro.scaniqo.data.backup.GoogleBackupAuthorizationProvider
import com.joseleandro.scaniqo.data.backup.GoogleDriveBackupRemote
import com.joseleandro.scaniqo.data.backup.WorkManagerBackupScheduler
import com.joseleandro.scaniqo.domain.repository.BackupFingerprint
import com.joseleandro.scaniqo.domain.repository.BackupHistoryRepository
import com.joseleandro.scaniqo.domain.repository.BackupScheduler
import com.joseleandro.scaniqo.domain.repository.BackupSettingsRepository
import com.joseleandro.scaniqo.domain.repository.DocumentBackupRepository
import com.joseleandro.scaniqo.domain.repository.DriveBackupRemote
import com.joseleandro.scaniqo.domain.service.BackupArchiveStore
import com.joseleandro.scaniqo.domain.service.BackupOperationGate
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val backupDataModule = module {
    single<BackupSettingsRepository> { AndroidBackupSettingsRepository(androidContext()) }
    single<BackupHistoryRepository> { AndroidBackupHistoryRepository(androidContext()) }
    single { BackupArchiveStore(get(), get()) }
    single<BackupScheduler> { WorkManagerBackupScheduler(androidContext()) }
    single<BackupAuthorizationProvider> { GoogleBackupAuthorizationProvider(androidContext()) }
    single { BackupOperationGate() }
    single<BackupFingerprint> { BackupSnapshotFingerprint(get()) }
    single { AutomaticBackupRunner(get(), get(), get(), get(), get(), get(), get()) }
    single<DocumentBackupRepository> { AndroidDocumentBackupRepository(androidContext(), get(), get()) }
    single<DriveBackupRemote> { GoogleDriveBackupRemote() }
}
