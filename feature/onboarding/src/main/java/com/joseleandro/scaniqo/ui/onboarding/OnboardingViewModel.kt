package com.joseleandro.scaniqo.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joseleandro.scaniqo.domain.repository.OnboardingRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class OnboardingViewModel(
    private val repository: OnboardingRepository,
) : ViewModel() {
    val completed = repository.completed
    private val saving = MutableStateFlow(false)
    val isSaving = saving.asStateFlow()
    private val failure = MutableStateFlow(false)
    val saveError = failure.asStateFlow()

    fun finish() {
        if (completed.value || saving.value) return
        saving.value = true
        failure.value = false
        viewModelScope.launch {
            try {
                repository.complete()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                failure.value = true
            } finally {
                saving.value = false
            }
        }
    }
}
