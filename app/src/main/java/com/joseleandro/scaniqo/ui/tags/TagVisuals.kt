package com.joseleandro.scaniqo.ui.tags

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.joseleandro.scaniqo.domain.model.TagIcon

@Composable
internal fun TagIconBadge(
    icon: TagIcon,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
) {
    // O fundo destaca a cor escolhida; o ícone mantém contraste nos dois temas.
    val background = color.copy(alpha = 0.9f).compositeOver(MaterialTheme.colorScheme.surface)
    Surface(
        modifier = modifier.size(size),
        shape = MaterialTheme.shapes.large,
        color = background,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(icon.drawableRes),
                contentDescription = null,
                tint = checkColor(background),
                modifier = Modifier.size(if (size > 48.dp) 32.dp else 24.dp),
            )
        }
    }
}

internal fun checkColor(background: Color): Color =
    if (background.luminance() > 0.179f) Color.Black else Color.White
