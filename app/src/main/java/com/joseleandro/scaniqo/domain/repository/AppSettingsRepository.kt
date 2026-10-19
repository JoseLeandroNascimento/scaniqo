package com.joseleandro.scaniqo.domain.repository

import com.joseleandro.scaniqo.domain.model.AppSettings
import kotlinx.coroutines.flow.StateFlow

interface AppSettingsRepository {
    val settings: StateFlow<AppSettings>
    suspend fun update(transform: (AppSettings) -> AppSettings)
}
