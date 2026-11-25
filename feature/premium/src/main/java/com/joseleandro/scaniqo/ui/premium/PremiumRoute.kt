package com.joseleandro.scaniqo.ui.premium

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.joseleandro.scaniqo.core.billing.PlayBillingManager
import org.koin.compose.koinInject

@Composable
fun PremiumRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    billing: PlayBillingManager = koinInject(),
) {
    val state by billing.state.collectAsStateWithLifecycle()
    val activity = LocalActivity.current
    LaunchedEffect(billing) { billing.refresh() }
    PremiumScreen(
        state = state,
        onPurchase = { activity?.let(billing::purchase) },
        onRestore = billing::restore,
        onRetry = billing::retry,
        onBack = onBack,
        modifier = modifier,
        hasPurchaseHost = activity != null,
    )
}
