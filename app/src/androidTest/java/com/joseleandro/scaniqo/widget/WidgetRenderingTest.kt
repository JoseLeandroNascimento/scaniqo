package com.joseleandro.scaniqo.widget

import android.appwidget.AppWidgetHostView
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ListView
import android.widget.TextView
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.compose
import androidx.glance.appwidget.provideContent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.joseleandro.scaniqo.R
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WidgetRenderingTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    @Test
    fun quickActionsRenderWithUsableTargetsFromScannerToTabletInBothThemes() = runBlocking {
        val sizes = listOf(
            DpSize(96.dp, 96.dp),
            DpSize(200.dp, 184.dp),
            DpSize(320.dp, 112.dp),
            DpSize(480.dp, 180.dp),
        )
        for (dark in listOf(false, true)) {
            for (size in sizes) {
                render(QuickActionsWidget(), size, dark, "quick") { context, root ->
                    val text = visibleText(root)
                    val compact = size.width < 200.dp
                    val actionLabels = if (compact) {
                        listOf(R.string.widget_scan)
                    } else {
                        listOf(R.string.widget_scan, R.string.widget_import, R.string.widget_search, R.string.widget_merge)
                    }
                    actionLabels.forEach { label ->
                        assertTrue("Missing action: ${context.getString(label)}", context.getString(label) in text)
                    }
                    val targets = root.descendants().filter { it.visibility == View.VISIBLE && it.hasOnClickListeners() }.toList()
                    assertEquals(actionLabels.size, targets.size)
                    assertUsableTargets(context, root)
                }
            }
        }
    }

    @Test
    fun recentDocumentsRenderContentAndLibraryAccessAtPhoneAndTabletSizesInBothThemes() = runBlocking {
        val sizes = listOf(
            DpSize(200.dp, 180.dp),
            DpSize(280.dp, 340.dp),
            DpSize(480.dp, 420.dp),
        )
        for (dark in listOf(false, true)) {
            for (size in sizes) {
                render(RecentDocumentsWidget(), size, dark, "recent") { context, root ->
                    val text = visibleText(root)
                    val views = root.descendants().toList()
                    assertTrue(context.getString(R.string.widget_recent_title) in text)
                    assertTrue(views.any {
                        it.visibility == View.VISIBLE &&
                            it.contentDescription?.toString() == context.getString(R.string.widget_open_library)
                    })
                    assertTrue(views.any { it.visibility == View.VISIBLE && it.hasOnClickListeners() })
                    assertTrue(
                        "Missing documents or empty/error feedback",
                        views.any { it is ListView && it.visibility == View.VISIBLE } ||
                            context.getString(R.string.widget_recent_empty) in text ||
                            context.getString(R.string.widget_recent_error) in text,
                    )
                }
            }
        }
    }

    @Test
    fun enlargedFontsKeepCompactActionsAndRecentFeedbackReadable() = runBlocking {
        for (size in listOf(DpSize(96.dp, 96.dp), DpSize(200.dp, 184.dp))) {
            render(QuickActionsWidget(), size, false, "quick_large_font", fontScale = 1.6f) { context, root ->
                assertUsableTargets(context, root)
                if (size.width < 200.dp) {
                    val target = root.descendants().single {
                        it.visibility == View.VISIBLE && it.hasOnClickListeners()
                    }
                    assertTrue("Compact scanner must remain accessible", target.descendants().any {
                        it.contentDescription?.toString() == context.getString(R.string.widget_scan)
                    })
                } else {
                    assertNoVerticalTextClipping(root)
                }
            }
        }
        render(RecentDocumentsWidget(), DpSize(200.dp, 180.dp), false, "recent_large_font", fontScale = 1.6f) { context, root ->
            assertUsableTargets(context, root)
            assertNoVerticalTextClipping(root)
        }
    }

    @Test
    fun populatedRecentFixtureShowsLongNamesAndProtectionWithoutChangingTheLibrary() = runBlocking {
        val names = listOf(
            "Contrato de prestação de serviços e condições comerciais para assinatura",
            "Comprovante de pagamento da mensalidade do curso de especialização",
            "Relatório completo de despesas e reembolsos da viagem de trabalho",
            "Certificado de participação no encontro de desenvolvimento Android",
            "Notas fiscais e documentos de garantia dos equipamentos do escritório",
        )
        val documents = names.mapIndexed { index, name ->
            RecentWidgetDocument(
                id = 50_000L + index,
                name = name,
                pageCount = index + 2,
                createdAt = 1_730_678_400_000L - index * 86_400_000L,
                isProtected = index % 2 == 0,
            )
        }
        for (dark in listOf(false, true)) {
            for (size in listOf(DpSize(200.dp, 180.dp), DpSize(480.dp, 420.dp))) {
                render(RecentDocumentsFixtureWidget(documents), size, dark, "recent_populated") { context, root ->
                    val text = visibleText(root)
                    assertTrue("First document is missing", documents.first().name in text)
                    if (size.height >= 420.dp) {
                        documents.forEach { document ->
                            assertTrue("Missing fixture document: ${document.name}", document.name in text)
                        }
                    }
                    assertTrue(root.descendants().any {
                        it.visibility == View.VISIBLE &&
                            it.contentDescription?.toString() == context.getString(R.string.widget_document_protected)
                    })
                    assertNoVerticalTextClipping(root)
                }
            }
        }
    }

    private class RecentDocumentsFixtureWidget(
        private val documents: List<RecentWidgetDocument>,
    ) : GlanceAppWidget() {
        override suspend fun provideGlance(context: Context, id: GlanceId) {
            provideContent {
                WidgetTheme(context, widgetColors()) { RecentDocumentsContent(documents) }
            }
        }
    }

    private fun assertUsableTargets(context: Context, root: FrameLayout) {
        val targets = root.descendants().filter { it.visibility == View.VISIBLE && it.hasOnClickListeners() }.toList()
        assertTrue("No interactive targets", targets.isNotEmpty())
        val minimumTarget = (48 * context.resources.displayMetrics.density).roundToInt()
        targets.forEach { target ->
            assertTrue("Action is too narrow", target.width >= minimumTarget)
            assertTrue("Action is too short", target.height >= minimumTarget)
            val bounds = Rect()
            target.getDrawingRect(bounds)
            root.offsetDescendantRectToMyCoords(target, bounds)
            assertTrue(
                "Action extends outside widget",
                bounds.left >= 0 && bounds.top >= 0 && bounds.right <= root.width && bounds.bottom <= root.height,
            )
        }
    }

    private fun assertNoVerticalTextClipping(root: FrameLayout) {
        val labels = root.descendants().filterIsInstance<TextView>()
            .filter { it.visibility == View.VISIBLE && it.text.isNotEmpty() }.toList()
        assertTrue("No readable labels", labels.isNotEmpty())
        labels.forEach { label ->
            val layout = requireNotNull(label.layout)
            val availableHeight = label.height - label.compoundPaddingTop - label.compoundPaddingBottom
            assertTrue("Text height is clipped: ${label.text}", layout.height <= availableHeight + 1)
            val bounds = Rect()
            label.getDrawingRect(bounds)
            root.offsetDescendantRectToMyCoords(label, bounds)
            assertTrue("Text extends outside widget: ${label.text}", bounds.top >= 0 && bounds.bottom <= root.height)
        }
    }

    private suspend fun render(
        widget: GlanceAppWidget,
        size: DpSize,
        dark: Boolean,
        name: String,
        fontScale: Float = 1f,
        assertions: (Context, FrameLayout) -> Unit,
    ) {
        val target = instrumentation.targetContext
        val configuration = Configuration(target.resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            this.fontScale = fontScale
        }
        val context = ContextCompat.getContextForLanguage(target.createConfigurationContext(configuration))
        val remoteViews = withTimeout(30_000) { widget.compose(context, size = size) }
        var result: Result<Unit>? = null
        instrumentation.runOnMainSync {
            result = runCatching {
                val root = AppWidgetHostView(context)
                root.addView(
                    remoteViews.apply(context, root),
                    FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
                )
                val density = context.resources.displayMetrics.density
                val width = (size.width.value * density).roundToInt()
                val height = (size.height.value * density).roundToInt()
                root.measure(
                    View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
                )
                root.layout(0, 0, width, height)
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                try {
                    root.draw(Canvas(bitmap))
                    val directory = requireNotNull(target.getExternalFilesDir("widget-rendering")).apply { mkdirs() }
                    val theme = if (dark) "dark" else "light"
                    File(directory, "${name}_${size.width.value.toInt()}x${size.height.value.toInt()}_$theme.png")
                        .outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    assertions(context, root)
                } finally {
                    bitmap.recycle()
                    root.removeAllViews()
                }
            }
        }
        checkNotNull(result).getOrThrow()
    }

    private fun visibleText(root: View): List<String> = root.descendants()
        .filterIsInstance<TextView>()
        .filter { it.visibility == View.VISIBLE }
        .map { it.text.toString() }
        .toList()

    private fun View.descendants(): Sequence<View> = sequence {
        yield(this@descendants)
        if (this@descendants is ViewGroup) {
            for (index in 0 until childCount) yieldAll(getChildAt(index).descendants())
        }
    }
}
