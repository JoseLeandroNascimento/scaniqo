package com.joseleandro.scaniqo.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.joseleandro.scaniqo.R
import java.text.DateFormat
import java.util.Date
import com.joseleandro.scaniqo.domain.repository.DocumentRepository
import kotlinx.coroutines.flow.flowOn
import org.koin.core.context.GlobalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.joseleandro.scaniqo.core.resources.R as CoreResourcesR

class RecentDocumentsWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(
        setOf(
            DpSize(200.dp, 180.dp),
            DpSize(280.dp, 250.dp),
            DpSize(280.dp, 340.dp),
            DpSize(360.dp, 420.dp),
            DpSize(480.dp, 420.dp),
        ),
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val initialDocuments = withContext(Dispatchers.IO) { loadRecentWidgetDocuments() }
        provideContent {
            val documentFlow = remember {
                recentWidgetDocumentUpdates(
                    GlobalContext.get().get<DocumentRepository>().observeDocuments(),
                ).flowOn(Dispatchers.IO)
            }
            val documents by documentFlow.collectAsState(initialDocuments)
            WidgetTheme(context, widgetColors()) {
                RecentDocumentsContent(documents)
            }
        }
    }
}

class RecentDocumentsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = RecentDocumentsWidget()

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

@Composable
internal fun RecentDocumentsContent(
    documents: List<RecentWidgetDocument>?,
    modifier: GlanceModifier = GlanceModifier,
) {
    val context = LocalContext.current
    val size = LocalSize.current
    val libraryAction = actionStartActivity(widgetLaunchIntent(context, WidgetAction.LIBRARY))
    WidgetSurface(modifier) {
        Column(GlanceModifier.fillMaxSize()) {
            Row(GlanceModifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = context.getString(R.string.widget_recent_title),
                    modifier = GlanceModifier.defaultWeight(),
                    style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold),
                    maxLines = 1,
                )
                Box(
                    modifier = GlanceModifier.size(48.dp).clickable(libraryAction),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        provider = ImageProvider(CoreResourcesR.drawable.ic_tag_document),
                        contentDescription = context.getString(R.string.widget_open_library),
                        modifier = GlanceModifier.size(24.dp),
                        colorFilter = ColorFilter.tint(GlanceTheme.colors.primary),
                    )
                }
            }
            if (documents.isNullOrEmpty()) {
                val fontScale = context.resources.configuration.fontScale.coerceAtLeast(1f)
                val showFooter = size.height >= 250.dp && (fontScale < 1.3f || size.height >= 340.dp)
                val showIllustration = size.height >= 250.dp
                val textHeight = size.height.value - 72f -
                    (if (showFooter) 56f else 0f) - (if (showIllustration) 40f else 0f)
                val textLines = ((textHeight - 8f) / (13f * fontScale * 1.45f + 3f)).toInt().coerceIn(1, 4)
                Column(
                    modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (showIllustration) {
                        Image(
                            provider = ImageProvider(
                                if (documents == null) CoreResourcesR.drawable.ic_toast_error
                                else CoreResourcesR.drawable.ic_tag_document,
                            ),
                            contentDescription = null,
                            modifier = GlanceModifier.size(32.dp),
                            colorFilter = ColorFilter.tint(GlanceTheme.colors.primary),
                        )
                        Spacer(GlanceModifier.height(8.dp))
                    }
                    Text(
                        text = context.getString(
                            if (documents == null) R.string.widget_recent_error else R.string.widget_recent_empty,
                        ),
                        style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp, textAlign = TextAlign.Center),
                        maxLines = textLines,
                    )
                }
                if (showFooter) {
                    Spacer(GlanceModifier.height(8.dp))
                    WidgetLibraryButton(libraryAction)
                }
            } else {
                val fontScale = context.resources.configuration.fontScale.coerceAtLeast(1f)
                val count = ((size.height.value - 72f) / (64f * fontScale)).toInt()
                    .coerceIn(1, 5)
                LazyColumn(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                    items(documents.take(count), itemId = { it.id }) { document ->
                        RecentDocumentRow(document)
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentDocumentRow(
    document: RecentWidgetDocument,
    modifier: GlanceModifier = GlanceModifier,
) {
    val context = LocalContext.current
    val locale = context.resources.configuration.locales[0]
    val date = DateFormat.getDateInstance(DateFormat.SHORT, locale).format(Date(document.createdAt))
    val pages = context.resources.getQuantityString(R.plurals.widget_document_pages, document.pageCount, document.pageCount)
    Row(
        modifier = modifier.fillMaxWidth()
            .clickable(actionStartActivity(widgetLaunchIntent(context, WidgetAction.OPEN_DOCUMENT, document.id)))
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            provider = ImageProvider(CoreResourcesR.drawable.ic_tag_document),
            contentDescription = null,
            modifier = GlanceModifier.size(28.dp),
            colorFilter = ColorFilter.tint(GlanceTheme.colors.primary),
        )
        Spacer(GlanceModifier.width(10.dp))
        Column(GlanceModifier.defaultWeight()) {
            Text(
                text = document.name,
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium),
                maxLines = 1,
            )
            Spacer(GlanceModifier.height(4.dp))
            Text(
                text = if (LocalSize.current.width < 280.dp) pages
                    else context.getString(R.string.widget_document_details, pages, date),
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
                maxLines = 1,
            )
        }
        if (document.isProtected) {
            Spacer(GlanceModifier.width(6.dp))
            Image(
                provider = ImageProvider(CoreResourcesR.drawable.ic_document_lock),
                contentDescription = context.getString(R.string.widget_document_protected),
                modifier = GlanceModifier.size(18.dp),
                colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurfaceVariant),
            )
        }
    }
}
