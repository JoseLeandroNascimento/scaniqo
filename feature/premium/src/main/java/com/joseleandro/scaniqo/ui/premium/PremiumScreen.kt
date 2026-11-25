package com.joseleandro.scaniqo.ui.premium

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joseleandro.scaniqo.core.billing.PremiumBillingMessage
import com.joseleandro.scaniqo.core.billing.PremiumBillingState
import com.joseleandro.scaniqo.core.billing.PremiumOffer
import com.joseleandro.scaniqo.domain.repository.PremiumEntitlement
import com.joseleandro.scaniqo.feature.premium.R
import com.joseleandro.scaniqo.ui.theme.ScaniqoTheme
import com.joseleandro.scaniqo.core.resources.R as CoreResourcesR

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PremiumScreen(
    state: PremiumBillingState,
    onPurchase: () -> Unit,
    onRestore: () -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    hasPurchaseHost: Boolean = true,
) {
    val presentation = state.presentation()
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = {},
                actions = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painterResource(CoreResourcesR.drawable.ic_toast_close),
                            stringResource(R.string.premium_close),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding),
            contentAlignment = Alignment.TopCenter,
        ) {
            LazyColumn(
                modifier = Modifier.widthIn(max = 520.dp).fillMaxSize().testTag("premium_content"),
                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                item(key = "hero") { PremiumHero(state.entitlement.isPremium, Modifier.fillMaxWidth()) }
                if (presentation.statusBody != null) {
                    item(key = "status") { PremiumStatus(presentation, Modifier.fillMaxWidth()) }
                }
                item(key = "benefits") { PremiumBenefits(Modifier.fillMaxWidth()) }
                item(key = "purchase") {
                    PremiumPurchaseActions(
                        state = state,
                        presentation = presentation,
                        onPurchase = onPurchase,
                        onRestore = onRestore,
                        onRetry = onRetry,
                        onBack = onBack,
                        hasPurchaseHost = hasPurchaseHost,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (!state.entitlement.isPremium) item(key = "free") {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            stringResource(R.string.premium_free_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.premium_continue_free), textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PremiumPurchaseActions(
    state: PremiumBillingState,
    presentation: PremiumPresentation,
    onPurchase: () -> Unit,
    onRestore: () -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    hasPurchaseHost: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (!state.entitlement.isPremium) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        stringResource(R.string.premium_one_time),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        stringResource(R.string.premium_no_subscription),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val price = state.offer?.formattedPrice
                if (price != null && state.isConfigured) {
                    Text(
                        price,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.End,
                    )
                } else {
                    Text(
                        stringResource(
                            if (state.isLoading) R.string.premium_price_loading
                            else R.string.premium_price_unavailable,
                        ),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End,
                    )
                }
            }
        }
        val waiting = presentation.action == PremiumAction.WAIT
        Button(
            onClick = {
                when (presentation.action) {
                    PremiumAction.PURCHASE -> onPurchase()
                    PremiumAction.RETRY -> onRetry()
                    PremiumAction.CHECK_PURCHASE -> onRestore()
                    PremiumAction.CONTINUE -> onBack()
                    PremiumAction.WAIT -> Unit
                }
            },
            enabled = !waiting && (presentation.action != PremiumAction.PURCHASE || hasPurchaseHost),
            modifier = Modifier.fillMaxWidth().testTag("premium_primary_action"),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (waiting) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                }
                Text(
                    stringResource(
                        when (presentation.action) {
                            PremiumAction.PURCHASE -> R.string.premium_buy
                            PremiumAction.RETRY -> R.string.premium_retry
                            PremiumAction.CHECK_PURCHASE -> R.string.premium_check_purchase
                            PremiumAction.CONTINUE -> R.string.premium_start
                            PremiumAction.WAIT -> R.string.premium_processing
                        },
                    ),
                    textAlign = TextAlign.Center,
                )
            }
        }
        if (!state.entitlement.isPremium) {
            TextButton(
                onClick = onRestore,
                enabled = presentation.canRestore,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.premium_restore), textAlign = TextAlign.Center)
            }
            Text(
                stringResource(R.string.premium_payment_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun PremiumStatus(
    presentation: PremiumPresentation,
    modifier: Modifier = Modifier,
) {
    val body = presentation.statusBody ?: return
    Surface(
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
        shape = RoundedCornerShape(16.dp),
        color = if (presentation.statusIsError) MaterialTheme.colorScheme.errorContainer
            else MaterialTheme.colorScheme.secondaryContainer,
        contentColor = if (presentation.statusIsError) MaterialTheme.colorScheme.onErrorContainer
            else MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            presentation.statusTitle?.let { title ->
                Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
            }
            Text(stringResource(body), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Preview(name = "Premium", widthDp = 390, heightDp = 850, locale = "pt-rBR")
@Preview(
    name = "Premium escuro", widthDp = 390, heightDp = 850,
    locale = "pt-rBR", uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Preview(
    name = "Premium fonte ampliada", widthDp = 320, heightDp = 640,
    locale = "pt-rBR", fontScale = 1.5f,
)
@Preview(name = "Premium tablet", widthDp = 1000, heightDp = 800, locale = "pt-rBR")
@Composable
private fun PremiumScreenPreview() {
    ScaniqoTheme {
        PremiumScreen(
            state = PremiumBillingState(
                entitlement = PremiumEntitlement.FREE,
                offer = PremiumOffer("scaniqo_premium", "R$ 29,90", "BRL"),
                isConfigured = true,
                isLoading = false,
            ),
            onPurchase = {}, onRestore = {}, onRetry = {}, onBack = {},
        )
    }
}

@Preview(name = "Premium indisponível", widthDp = 390, heightDp = 850, locale = "pt-rBR")
@Composable
private fun PremiumUnavailablePreview() {
    ScaniqoTheme {
        PremiumScreen(
            state = PremiumBillingState(
                entitlement = PremiumEntitlement.FREE,
                isConfigured = false,
                isLoading = false,
                message = PremiumBillingMessage.CONFIGURATION_REQUIRED,
            ),
            onPurchase = {}, onRestore = {}, onRetry = {}, onBack = {},
        )
    }
}

@Preview(name = "Premium ativado", widthDp = 390, heightDp = 850, locale = "pt-rBR")
@Composable
private fun PremiumActivePreview() {
    ScaniqoTheme {
        PremiumScreen(
            state = PremiumBillingState(
                entitlement = PremiumEntitlement.PREMIUM, isLoading = false, isConfigured = true,
            ),
            onPurchase = {}, onRestore = {}, onRetry = {}, onBack = {},
        )
    }
}
