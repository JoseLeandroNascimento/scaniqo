package com.joseleandro.scaniqo.ui.documentCommon.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joseleandro.scaniqo.domain.model.TagIcon
import com.joseleandro.scaniqo.domain.model.TagModel
import com.joseleandro.scaniqo.ui.tags.checkColor
import com.joseleandro.scaniqo.ui.theme.ScaniqoTheme

@Composable
internal fun DocumentTagIcon(tag: TagModel) {
    val background = tag.color.copy(alpha = 0.9f).compositeOver(MaterialTheme.colorScheme.surface)
    Surface(
        modifier = Modifier.size(22.dp),
        shape = MaterialTheme.shapes.small,
        color = background,
        contentColor = checkColor(background),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(painterResource(tag.icon.drawableRes), null, modifier = Modifier.size(14.dp))
        }
    }
}

@Preview(name = "light", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DocumentTagIconPreview() {
    ScaniqoTheme(
        dynamicColor = false
    ) {
        DocumentTagIcon(
            tag = TagModel(
                name = "Documento",
                iconKey = TagIcon.DOCUMENT.key,
                id = 1L,
                color = MaterialTheme.colorScheme.primary
            )
        )
    }
}