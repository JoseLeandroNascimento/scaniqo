package com.joseleandro.scaniqo.widget

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import com.joseleandro.scaniqo.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, manifest = Config.NONE)
class WidgetIntentsTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Test
    fun everyActionTargetsMainActivityAndRoundTripsThroughTheSameParser() {
        WidgetAction.entries.forEach { action ->
            val id = 42L.takeIf { action == WidgetAction.OPEN_DOCUMENT }
            val intent = widgetLaunchIntent(context, action, id)

            assertEquals(ComponentName(context, MainActivity::class.java), intent.component)
            assertEquals(WidgetDestination(action, id), parseWidgetIntent(context, intent))
            assertEquals(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP,
                intent.flags,
            )
            assertNull(intent.selector)
            assertNull(intent.clipData)
            assertNull(intent.extras)
        }
    }

    @Test
    fun differentActionsAndDocumentsHaveDistinctPendingIntentIdentities() {
        val intents = WidgetAction.entries.map { action ->
            widgetLaunchIntent(context, action, 1L.takeIf { action == WidgetAction.OPEN_DOCUMENT })
        } + widgetLaunchIntent(context, WidgetAction.OPEN_DOCUMENT, 2L)
        intents.forEachIndexed { index, first ->
            intents.drop(index + 1).forEach { second -> assertFalse(first.filterEquals(second)) }
        }
        assertEquals(
            widgetLaunchIntent(context, WidgetAction.SCAN).data,
            widgetLaunchIntent(context, WidgetAction.SCAN).data,
        )
    }

    @Test
    fun documentIdsMustBePositiveAndOnlyDocumentActionsAcceptAnId() {
        listOf<Long?>(null, 0, -1).forEach { id ->
            assertThrows(IllegalArgumentException::class.java) {
                widgetLaunchIntent(context, WidgetAction.OPEN_DOCUMENT, id)
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            widgetLaunchIntent(context, WidgetAction.SEARCH, 42)
        }
        assertEquals(
            WidgetDestination(WidgetAction.OPEN_DOCUMENT, Long.MAX_VALUE),
            parseWidgetIntent(context, widgetLaunchIntent(context, WidgetAction.OPEN_DOCUMENT, Long.MAX_VALUE)),
        )
    }

    @Test
    fun malformedDocumentIdsAndNoncanonicalPayloadsAreIgnored() {
        val valid = widgetLaunchIntent(context, WidgetAction.OPEN_DOCUMENT, 42)
        listOf("0", "-1", "not-an-id", "9223372036854775808", "042", "+42").forEach { id ->
            val invalid = Intent(valid).setData(Uri.parse(valid.data.toString().substringBeforeLast('/') + "/" + id))
            assertNull(parseWidgetIntent(context, invalid))
        }
        listOf(
            Uri.parse("content://files/document/42"),
            valid.data!!.buildUpon().authority("another.app").build(),
            valid.data!!.buildUpon().appendPath("extra").build(),
            valid.data!!.buildUpon().appendQueryParameter("target", "content://files/private").build(),
            valid.data!!.buildUpon().fragment("extra").build(),
        ).forEach { uri ->
            assertNull(parseWidgetIntent(context, Intent(valid).setData(uri)))
        }
    }

    @Test
    fun launcherUnknownActionsAndMismatchedActionDataAreIgnored() {
        val scan = widgetLaunchIntent(context, WidgetAction.SCAN)
        val search = widgetLaunchIntent(context, WidgetAction.SEARCH)
        assertNull(parseWidgetIntent(context, null))
        assertNull(parseWidgetIntent(context, Intent(context, MainActivity::class.java).setAction(Intent.ACTION_MAIN)))
        assertNull(parseWidgetIntent(context, Intent(scan).setAction("arbitrary.action")))
        assertNull(parseWidgetIntent(context, Intent(scan).setData(search.data)))
        assertNull(parseWidgetIntent(context, Intent(scan).setComponent(null)))
        assertNull(parseWidgetIntent(context, Intent(scan).setComponent(ComponentName(context, Application::class.java))))
        assertNull(parseWidgetIntent(context, Intent(scan).setData(null)))
        assertNull(parseWidgetIntent(context, Intent(scan).apply { selector = Intent(Intent.ACTION_VIEW) }))
    }

    @Test
    fun nestedIntentsAndDocumentExtrasNeverControlTheDestination() {
        val intent = widgetLaunchIntent(context, WidgetAction.OPEN_DOCUMENT, 42)
            .putExtra("documentId", -1L)
            .putExtra(Intent.EXTRA_INTENT, Intent(Intent.ACTION_VIEW, Uri.parse("content://private/document")))
        assertEquals(WidgetDestination(WidgetAction.OPEN_DOCUMENT, 42), parseWidgetIntent(context, intent))
    }
}
