package com.joseleandro.scaniqo.ui.ads

import com.joseleandro.scaniqo.core.resources.R as CoreResourcesR
import com.joseleandro.scaniqo.core.ads.R

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joseleandro.scaniqo.ui.theme.ScaniqoTheme

val LocalOpenPremium = staticCompositionLocalOf<(() -> Unit)?> { null }

@Composable
fun ToolAdActionContent(
    isVideo: Boolean,
    label: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isVideo) {
            Icon(
                painter = painterResource(CoreResourcesR.drawable.ic_tag_gift),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
        }
        Text(label, textAlign = TextAlign.Center)
    }
}

@Composable
fun ToolAdRewardHint(modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = stringResource(CoreResourcesR.string.tool_ad_reward_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        PremiumAdFreeButton()
    }
}

@Composable
fun PremiumAdFreeButton(modifier: Modifier = Modifier) {
    val openPremium = LocalOpenPremium.current ?: return
    TextButton(onClick = openPremium, modifier = modifier) {
        Text(stringResource(R.string.ads_premium_ad_free), textAlign = TextAlign.Center)
    }
}

@Preview(name = "Vídeo direto na ação", widthDp = 390, locale = "pt-rBR")
@Preview(name = "Vídeo direto — escuro", widthDp = 390, locale = "pt-rBR", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Vídeo direto — fonte ampliada", widthDp = 320, locale = "pt-rBR", fontScale = 2f)
@Composable
private fun ToolAdActionPreview() {
    CompositionLocalProvider(LocalOpenPremium provides {}) {
        ScaniqoTheme {
            Surface {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {}, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                        ToolAdActionContent(true, stringResource(CoreResourcesR.string.tool_ad_watch_apply))
                    }
                    ToolAdRewardHint(Modifier.fillMaxWidth())
                }
            }
        }
    }
}
