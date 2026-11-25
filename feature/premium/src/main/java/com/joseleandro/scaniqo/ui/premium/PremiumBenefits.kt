package com.joseleandro.scaniqo.ui.premium

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joseleandro.scaniqo.feature.premium.R
import com.joseleandro.scaniqo.ui.theme.ScaniqoTheme
import com.joseleandro.scaniqo.core.resources.R as CoreResourcesR

@Composable
internal fun PremiumBenefits(modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        PremiumBenefit(CoreResourcesR.drawable.ic_symbol_auto_stories, R.string.premium_ads_title)
        PremiumBenefit(CoreResourcesR.drawable.ic_symbol_auto_fix_high, R.string.premium_tools_title)
        PremiumBenefit(CoreResourcesR.drawable.ic_symbol_cloud_done, R.string.premium_backup_title)
        PremiumBenefit(CoreResourcesR.drawable.ic_symbol_memory, R.string.premium_hardware_title)
    }
}

@Composable
private fun PremiumBenefit(
    @DrawableRes icon: Int,
    @StringRes title: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            stringResource(title),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Preview(name = "Benefícios Premium", widthDp = 360, locale = "pt-rBR")
@Composable
private fun PremiumBenefitsPreview() {
    ScaniqoTheme { PremiumBenefits() }
}
