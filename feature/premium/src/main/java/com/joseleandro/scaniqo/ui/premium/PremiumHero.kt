package com.joseleandro.scaniqo.ui.premium

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joseleandro.scaniqo.feature.premium.R
import com.joseleandro.scaniqo.ui.theme.ScaniqoTheme
import com.joseleandro.scaniqo.core.resources.R as CoreResourcesR

@Composable
internal fun PremiumHero(
    isPremium: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) {
            Icon(
                painterResource(if (isPremium) CoreResourcesR.drawable.ic_check_24 else CoreResourcesR.drawable.ic_tag_star),
                contentDescription = null,
                modifier = Modifier.padding(8.dp).size(24.dp),
            )
        }
        Text(
            text = stringResource(if (isPremium) R.string.premium_success_heading else R.string.premium_brand),
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(if (isPremium) R.string.premium_success_intro else R.string.premium_heading),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Preview(name = "Premium destaque", widthDp = 360, locale = "pt-rBR")
@Preview(name = "Premium destaque escuro", widthDp = 360, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PremiumHeroPreview() {
    ScaniqoTheme { PremiumHero(isPremium = false) }
}
