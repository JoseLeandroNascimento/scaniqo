package com.joseleandro.scaniqo.ui.documentEditor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.joseleandro.scaniqo.R
import com.joseleandro.scaniqo.domain.model.PdfImageFilter

/** Nas correções, permite escolher se texto e vetores também serão tratados como imagem. */
@Composable
internal fun DocumentRasterizationChoice(checked: Boolean, enabled: Boolean, onToggle: (Boolean) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onToggle),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.quality_native_title),
                Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
            )
            Switch(checked = checked, onCheckedChange = null, enabled = enabled)
        }
        Text(
            stringResource(if (checked) R.string.quality_native_active else R.string.quality_native_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

internal fun PdfImageFilter.titleResource(): Int = when (this) {
    PdfImageFilter.ORIGINAL -> R.string.tools_filter_original
    PdfImageFilter.ENHANCE -> R.string.tools_filter_enhance
    PdfImageFilter.AUTO -> R.string.tools_filter_auto
    PdfImageFilter.GRAYSCALE -> R.string.tools_filter_grayscale
    PdfImageFilter.BLACK_WHITE -> R.string.tools_filter_black_white
    PdfImageFilter.SHADOW_REMOVAL -> R.string.quality_filter_shadow
    PdfImageFilter.ILLUMINATION -> R.string.quality_filter_light
    PdfImageFilter.WHITEN -> R.string.quality_filter_white
    PdfImageFilter.ADAPTIVE_BLACK_WHITE -> R.string.quality_filter_adaptive
    PdfImageFilter.DENOISE -> R.string.quality_filter_noise
    PdfImageFilter.SHARPEN -> R.string.quality_filter_sharp
    PdfImageFilter.BLEED_THROUGH -> R.string.quality_filter_bleed
}

