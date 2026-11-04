package com.joseleandro.scaniqo.ui.backup.di

import com.joseleandro.scaniqo.ui.backup.DriveBackupViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val backupFeatureModule = module {
    viewModelOf(::DriveBackupViewModel)
}
