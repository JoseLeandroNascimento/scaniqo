package com.joseleandro.scaniqo.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.joseleandro.scaniqo.domain.repository.OnboardingRepository
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class AndroidOnboardingRepository internal constructor(
    private val preferences: SharedPreferences,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : OnboardingRepository {
    constructor(context: Context) : this(
        context.applicationContext.getSharedPreferences("onboarding", Context.MODE_PRIVATE),
    )

    private val mutex = Mutex()
    private val state = MutableStateFlow(
        runCatching { preferences.getBoolean("completed", false) }.getOrDefault(false),
    )
    override val completed = state.asStateFlow()

    override suspend fun complete() = withContext(ioDispatcher) {
        mutex.withLock {
            if (state.value) return@withLock
            if (!preferences.edit().putBoolean("completed", true).commit()) {
                throw IOException("Unable to save tutorial completion.")
            }
            state.value = true
        }
    }
}
