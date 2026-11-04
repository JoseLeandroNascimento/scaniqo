package com.joseleandro.scaniqo.domain.repository

import com.joseleandro.scaniqo.domain.model.BackupSettings

interface BackupScheduler {
    fun schedule(settings: BackupSettings)
    fun cancel()
}
