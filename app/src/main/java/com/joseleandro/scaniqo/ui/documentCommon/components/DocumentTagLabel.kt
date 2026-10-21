package com.joseleandro.scaniqo.ui.documentCommon.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joseleandro.scaniqo.domain.model.TagIcon
import com.joseleandro.scaniqo.domain.model.TagModel
import com.joseleandro.scaniqo.ui.tags.checkColor
import com.joseleandro.scaniqo.ui.theme.ScaniqoTheme

@Composable
internal fun DocumentTagLabel(tag: TagModel, modifier: Modifier = Modifier) {
    val background = tag.color.copy(alpha = 0.9f).compositeOver(MaterialTheme.colorScheme.surface)
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = background,
        contentColor = checkColor(background),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(painterResource(tag.icon.drawableRes), null, modifier = Modifier.size(12.dp))
            Text(
                text = tag.name,
                style = MaterialTheme.typography.labelSmall.copy(textDirection = TextDirection.ContentOrLtr),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Preview(name = "light", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DocumentTagLabelPreview() {

    ScaniqoTheme(
        dynamicColor = false
    ) {
        DocumentTagLabel(
            tag = TagModel(
                id = 1L,
                name = "Arquivo",
                color = MaterialTheme.colorScheme.primary,
                iconKey = TagIcon.DOCUMENT.key
            )
        )
    }
}