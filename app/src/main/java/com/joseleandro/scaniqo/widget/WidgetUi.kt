package com.joseleandro.scaniqo.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.background
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.material3.ColorProviders
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.joseleandro.scaniqo.R
import com.joseleandro.scaniqo.domain.model.AppThemeMode
import com.joseleandro.scaniqo.domain.repository.AppSettingsRepository
import com.joseleandro.scaniqo.ui.theme.personalizedColorScheme
import org.koin.core.context.GlobalContext

@Composable
internal fun widgetColors(): androidx.glance.color.ColorProviders {
    val repository = remember { GlobalContext.get().get<AppSettingsRepository>() }
    val settings by repository.settings.collectAsState()
    return ColorProviders(
        light = personalizedColorScheme(settings.colorTheme, settings.themeMode == AppThemeMode.DARK),
        dark = personalizedColorScheme(settings.colorTheme, settings.themeMode != AppThemeMode.LIGHT),
    )
}

@Composable
internal fun WidgetTheme(
    context: Context,
    colors: androidx.glance.color.ColorProviders,
    content: @Composable () -> Unit,
) {
    val configuration by WidgetUpdater.configuration.collectAsState()
    val localizedContext = remember(context, configuration) { widgetContext(context, configuration) }
    CompositionLocalProvider(LocalContext provides localizedContext) {
        GlanceTheme(colors = colors, content = content)
    }
}

@Composable
internal fun WidgetSurface(
    modifier: GlanceModifier = GlanceModifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier.fillMaxSize().appWidgetBackground()
            .background(
                ImageProvider(R.drawable.widget_background),
                colorFilter = ColorFilter.tint(GlanceTheme.colors.widgetBackground),
            )
            .padding(12.dp),
    ) { content() }
}

internal fun GlanceModifier.widgetCard(color: ColorProvider): GlanceModifier = background(
    ImageProvider(R.drawable.widget_action_background),
    colorFilter = ColorFilter.tint(color),
)

@Composable
internal fun WidgetActionTile(
    label: String,
    icon: Int,
    action: Action,
    modifier: GlanceModifier = GlanceModifier,
    primary: Boolean = false,
    compact: Boolean = false,
) {
    val largeFont = LocalContext.current.resources.configuration.fontScale >= 1.3f
    val iconOnly = compact && largeFont
    val background = if (primary) GlanceTheme.colors.primary else GlanceTheme.colors.secondaryContainer
    val foreground = if (primary) GlanceTheme.colors.onPrimary else GlanceTheme.colors.onSecondaryContainer
    Column(
        modifier = modifier.widgetCard(background).clickable(action).padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            provider = ImageProvider(icon),
            contentDescription = if (iconOnly) label else null,
            modifier = GlanceModifier.size(if (iconOnly) 36.dp else if (compact) 28.dp else 24.dp),
            colorFilter = ColorFilter.tint(foreground),
        )
        if (!iconOnly) {
            Spacer(GlanceModifier.height(if (largeFont) 2.dp else 4.dp))
            Text(
                text = label,
                style = TextStyle(color = foreground, fontSize = 12.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center),
                maxLines = 1,
            )
        }
    }
}

@Composable
internal fun WidgetLibraryButton(
    action: Action,
    modifier: GlanceModifier = GlanceModifier,
) {
    Box(
        modifier = modifier.fillMaxWidth().height(48.dp)
            .widgetCard(GlanceTheme.colors.primaryContainer).clickable(action),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = LocalContext.current.getString(R.string.widget_open_library),
            style = TextStyle(
                color = GlanceTheme.colors.onPrimaryContainer,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            ),
            maxLines = 1,
        )
    }
}
