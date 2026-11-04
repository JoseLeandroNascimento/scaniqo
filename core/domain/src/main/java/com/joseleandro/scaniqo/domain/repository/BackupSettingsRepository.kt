package com.joseleandro.scaniqo.domain.repository

import com.joseleandro.scaniqo.domain.model.BackupSettings
import kotlinx.coroutines.flow.StateFlow

interface BackupSettingsRepository {
    val settings: StateFlow<BackupSettings>
    suspend fun update(transform: (BackupSettings) -> BackupSettings)
}
