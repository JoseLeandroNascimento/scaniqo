package com.joseleandro.scaniqo.data.pdf

import android.app.Application
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.common.PDStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import java.io.ByteArrayInputStream
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.system.measureNanoTime
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, manifest = Config.NONE)
class PdfTextSearchLoadTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun searchesRealTextThroughTenThousandPersistedPagesAndReportsTheLastPage() = runTest(timeout = 5.minutes) {
        PDFBoxResourceLoader.init(RuntimeEnvironment.getApplication())
        val fixture = temporary.newFile("search_ten_thousand_pages.pdf")
        PDDocument(memory()).use { document ->
            val resources = PDResources().apply { put(COSName.getPDFName("F1"), PDType1Font.HELVETICA) }
            fun content(text: String) = PDStream(document, ByteArrayInputStream(
                "BT /F1 12 Tf 10 700 Td ($text) Tj ET".toByteArray(Charsets.US_ASCII),
            ))
            val ordinary = content("ordinary searchable content")
            val target = content("unique final contract")
            repeat(PAGE_COUNT) { number ->
                document.addPage(PDPage().apply {
                    this.resources = resources
                    setContents(if (number == PAGE_COUNT - 1) target else ordinary)
                })
            }
            document.save(fixture)
        }
        PDDocument.load(fixture, memory()).use { document ->
            assertEquals(PAGE_COUNT, document.numberOfPages)
            var match: PdfTextMatch?
            val elapsed = measureNanoTime { match = findPdfTextMatch(document, "unique final contract") }
            assertEquals(PAGE_COUNT, requireNotNull(match).pageNumber)
            assertTrue(requireNotNull(match).snippet.contains("unique final contract"))
            assertEquals(1, requireNotNull(findPdfTextMatch(document, "ordinary searchable")).pageNumber)
            assertEquals(null, findPdfTextMatch(document, "absent phrase"))
            println("PDF_TEXT_SEARCH pages=$PAGE_COUNT bytes=${fixture.length()} lastMatchPage=${match?.pageNumber} elapsedMs=${elapsed / 1_000_000}")
        }
    }

    @Test fun repeatedPrefixesInEightMillionCharactersDoNotRequireRescanningTheQuery() {
        val query = "a".repeat(16_384) + "b"
        val writer = PdfSearchSnippetWriter(query, EmptyCoroutineContext)
        val chunk = "a".repeat(8_192)
        var match: PdfTextMatch? = null
        val elapsed = measureNanoTime {
            try {
                writer.startPage(9)
                repeat(1_024) { writer.write(chunk) }
                writer.write("b")
                writer.finishPage()
            } catch (found: PdfTextMatchFound) {
                match = found.match
            }
        }
        assertEquals(9, requireNotNull(match).pageNumber)
        assertEquals("…" + "a".repeat(64) + query, requireNotNull(match).snippet)
        println("PDF_TEXT_PREFIX characters=${chunk.length.toLong() * 1_024 + 1} queryChars=${query.length} snippetChars=${match?.snippet?.length} elapsedMs=${elapsed / 1_000_000}")
    }

    private fun memory() = MemoryUsageSetting.setupMixed(8L * 1024 * 1024).setTempDir(temporary.root)

    private companion object { const val PAGE_COUNT = 10_000 }
}
