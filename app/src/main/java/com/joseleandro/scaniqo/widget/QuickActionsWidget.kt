package com.joseleandro.scaniqo.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.joseleandro.scaniqo.R
import com.joseleandro.scaniqo.core.resources.R as CoreResourcesR

class QuickActionsWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(
        setOf(
            DpSize(96.dp, 96.dp),
            DpSize(200.dp, 184.dp),
            DpSize(320.dp, 112.dp),
            DpSize(320.dp, 200.dp),
            DpSize(480.dp, 180.dp),
        ),
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            WidgetTheme(context, widgetColors()) { QuickActionsContent() }
        }
    }
}

class QuickActionsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickActionsWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetUpdater.start(context)
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        WidgetUpdater.start(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WidgetUpdater.stopIfUnused(context)
    }
}

private val quickWidgetActions = listOf(
    Triple(WidgetAction.SCAN, R.string.widget_scan, CoreResourcesR.drawable.ic_document_scan),
    Triple(WidgetAction.IMPORT, R.string.widget_import, CoreResourcesR.drawable.ic_symbol_file_open),
    Triple(WidgetAction.SEARCH, R.string.widget_search, CoreResourcesR.drawable.ic_document_search),
    Triple(WidgetAction.MERGE, R.string.widget_merge, CoreResourcesR.drawable.ic_symbol_merge),
)

@Composable
internal fun QuickActionsContent(
    modifier: GlanceModifier = GlanceModifier,
) {
    val context = LocalContext.current
    val size = LocalSize.current
    val expanded = (size.width >= 200.dp && size.height >= 184.dp) || size.width >= 320.dp
    val oneRow = size.width >= 480.dp || size.height < 184.dp
    val largeFont = context.resources.configuration.fontScale >= 1.3f
    WidgetSurface(modifier) {
        if (!expanded) {
            WidgetActionTile(
                label = context.getString(R.string.widget_scan),
                icon = CoreResourcesR.drawable.ic_document_scan,
                action = actionStartActivity(widgetLaunchIntent(context, WidgetAction.SCAN)),
                modifier = GlanceModifier.fillMaxSize(),
                primary = true,
                compact = true,
            )
        } else {
            Column(GlanceModifier.fillMaxSize()) {
                if (size.height >= 180.dp && (!largeFont || size.height >= 220.dp)) {
                    Text(
                        text = context.getString(R.string.app_name),
                        style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold),
                        maxLines = 1,
                    )
                    Spacer(GlanceModifier.height(8.dp))
                }
                if (oneRow) {
                    Row(GlanceModifier.fillMaxWidth().defaultWeight()) {
                        quickWidgetActions.forEachIndexed { index, (action, label, icon) ->
                            if (index > 0) Spacer(GlanceModifier.width(8.dp))
                            WidgetActionTile(
                                label = context.getString(label),
                                icon = icon,
                                action = actionStartActivity(widgetLaunchIntent(context, action)),
                                modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
                                primary = action == WidgetAction.SCAN,
                            )
                        }
                    }
                } else {
                    quickWidgetActions.chunked(2).forEachIndexed { rowIndex, rowActions ->
                        if (rowIndex > 0) Spacer(GlanceModifier.height(8.dp))
                        Row(GlanceModifier.fillMaxWidth().defaultWeight()) {
                            rowActions.forEachIndexed { index, (action, label, icon) ->
                                if (index > 0) Spacer(GlanceModifier.width(8.dp))
                                WidgetActionTile(
                                    label = context.getString(label),
                                    icon = icon,
                                    action = actionStartActivity(widgetLaunchIntent(context, action)),
                                    modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
                                    primary = action == WidgetAction.SCAN,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
