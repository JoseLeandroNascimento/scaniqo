package com.joseleandro.scaniqo.ui.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun OnboardingRoute(
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = koinViewModel(),
) {
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
    val saveError by viewModel.saveError.collectAsStateWithLifecycle()
    OnboardingScreen(
        onFinish = viewModel::finish,
        modifier = modifier,
        isSaving = isSaving,
        saveError = saveError,
    )
}
