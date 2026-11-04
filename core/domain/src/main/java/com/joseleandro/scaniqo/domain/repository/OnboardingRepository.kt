package com.joseleandro.scaniqo.domain.repository

import kotlinx.coroutines.flow.StateFlow

interface OnboardingRepository {
    val completed: StateFlow<Boolean>
    suspend fun complete()
}
