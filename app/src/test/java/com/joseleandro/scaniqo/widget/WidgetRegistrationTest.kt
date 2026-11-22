package com.joseleandro.scaniqo.widget

import android.app.Application
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Xml
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24, 35], application = Application::class)
class WidgetRegistrationTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val receiverNames = listOf(
        "com.joseleandro.scaniqo.widget.QuickActionsWidgetReceiver",
        "com.joseleandro.scaniqo.widget.RecentDocumentsWidgetReceiver",
    )

    @Test
    fun systemCanDiscoverBothWidgetsWithoutExposingTheirReceivers() {
        val packageManager = context.packageManager
        val matches = packageManager.queryBroadcastReceivers(
            Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).setPackage(context.packageName),
            PackageManager.GET_META_DATA,
        )
        val labels = receiverNames.map { name ->
            val receiver = matches.single { it.activityInfo.name == name }.activityInfo
            assertTrue(receiver.enabled)
            assertFalse(receiver.exported)
            assertTrue(receiver.metaData.getInt(AppWidgetManager.META_DATA_APPWIDGET_PROVIDER) != 0)
            receiver.loadLabel(packageManager).toString().also { assertTrue(it.isNotBlank()) }
        }

        assertEquals(2, labels.distinct().size)
    }

    @Test
    fun providerMetadataAllowsResizingAndSuppliesAnInitialHomeScreenLayout() {
        receiverNames.forEach { name ->
            val receiver = context.packageManager.getReceiverInfo(
                ComponentName(context.packageName, name),
                PackageManager.GET_META_DATA,
            )
            val parser = requireNotNull(
                receiver.loadXmlMetaData(context.packageManager, AppWidgetManager.META_DATA_APPWIDGET_PROVIDER),
            )
            parser.use {
                while (parser.next() != XmlPullParser.START_TAG) {
                    check(parser.eventType != XmlPullParser.END_DOCUMENT)
                }
                assertEquals("appwidget-provider", parser.name)
                val attributes = context.obtainStyledAttributes(
                    Xml.asAttributeSet(parser),
                    intArrayOf(
                        android.R.attr.minWidth,
                        android.R.attr.minHeight,
                        android.R.attr.minResizeWidth,
                        android.R.attr.minResizeHeight,
                        android.R.attr.resizeMode,
                        android.R.attr.widgetCategory,
                        android.R.attr.initialLayout,
                    ),
                )
                try {
                    for (index in 0..3) {
                        assertTrue("$name must declare positive dimensions", attributes.getDimensionPixelSize(index, 0) > 0)
                    }
                    assertEquals(
                        AppWidgetProviderInfo.RESIZE_HORIZONTAL or AppWidgetProviderInfo.RESIZE_VERTICAL,
                        attributes.getInt(4, AppWidgetProviderInfo.RESIZE_NONE),
                    )
                    assertEquals(
                        AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN,
                        attributes.getInt(5, 0),
                    )
                    assertTrue(attributes.getResourceId(6, 0) != 0)
                } finally {
                    attributes.recycle()
                }
            }
        }
    }
}
