package com.joseleandro.scaniqo.data.pdf

import android.app.Application
import com.joseleandro.scaniqo.domain.geometry.PdfPageNumberMetrics
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, manifest = Config.NONE)
class PdfPageNumberMetricsParityTest {
    @Test
    fun previewFooterAdvancesMatchTheFontUsedByThePdfExporter() {
        PDFBoxResourceLoader.init(RuntimeEnvironment.getApplication())
        val values = (0..100).toList() + listOf(999, 1000, 10001, Int.MAX_VALUE, Int.MIN_VALUE)
        values.forEach { pageNumber ->
            values.forEach { pageCount ->
                val text = "$pageNumber / $pageCount"
                val exportedWidth = PDType1Font.HELVETICA.getStringWidth(text) / 1000f * 10f
                assertEquals(text, exportedWidth, PdfPageNumberMetrics.width(pageNumber, pageCount), 0f)
            }
        }
    }
}
