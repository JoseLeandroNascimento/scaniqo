package com.joseleandro.scaniqo.data.pdf

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfTextSearchTest {
    @Test
    fun phraseMatchesAcrossTextWritesAndLineBreaksIgnoringCase() {
        val match = find(" CONTRATO de serviço ", listOf("Con", "trato\n\t", "de\u00a0serviço assinado."))

        assertEquals(PdfTextMatch("Contrato de serviço assinado.", 1), match)
    }

    @Test
    fun textWithoutTheQueryDoesNotProduceAResult() {
        assertNull(find("contrato", listOf("uma digitalização sem esse termo")))
        assertNull(find("contrato", listOf("", "\n")))
    }

    @Test
    fun laterPageReportsItsOneBasedNumber() {
        val writer = PdfSearchSnippetWriter("contrato", EmptyCoroutineContext)
        writer.startPage(1)
        writer.write("Primeira página")
        writer.finishPage()

        val match = captureMatch {
            writer.startPage(4)
            writer.write("Contrato na quarta página")
            writer.finishPage()
        }

        assertEquals(PdfTextMatch("Contrato na quarta página", 4), match)
    }

    @Test
    fun queryCannotBeAssembledFromUnrelatedPages() {
        val writer = PdfSearchSnippetWriter("fim início", EmptyCoroutineContext)
        writer.startPage(1)
        writer.write("fim")
        writer.finishPage()
        writer.startPage(2)
        writer.write("início")

        assertNull(captureMatch { writer.finishPage() })
    }

    @Test
    fun findsTextBeyondTheInitialWindowAndKeepsOnlyNearbyContext() {
        val match = find("contrato", listOf("a".repeat(100_000), " contrato ", "b".repeat(100_000)))

        requireNotNull(match)
        assertTrue(match.snippet.contains("contrato"))
        assertTrue(match.snippet.startsWith("…"))
        assertTrue(match.snippet.endsWith("…"))
        assertTrue(match.snippet.length <= "contrato".length + 130)
    }

    @Test
    fun phraseSpanningInternalWindowsIsNotLost() {
        // Varia o alinhamento para cobrir todos os caracteres de uma consulta na borda do bloco.
        repeat(40) { shift ->
            val match = find(
                "contrato de serviço",
                listOf("a".repeat(1_050 + shift), " contrato de serviço depois")
            )
            requireNotNull(match)
            assertTrue(match.snippet.contains("contrato de serviço"))
        }
    }

    @Test
    fun longQueryAndPartialPrefixAtPageEndAreHandled() {
        val query = "documento".repeat(200)
        assertTrue(requireNotNull(find(query, listOf("antes ", query, " depois"))).snippet.contains(query))
        assertNull(find("contratos", listOf("contrato")))
    }

    @Test
    fun onlyTheFirstMatchIsReturned() {
        val match = find("contrato", listOf("primeiro contrato ", "a".repeat(200), " segundo contrato"))

        requireNotNull(match)
        assertTrue(match.snippet.contains("primeiro contrato"))
        assertTrue(!match.snippet.contains("segundo"))
    }

    @Test
    fun cancelledSearchStopsBeforeConsumingMoreText() {
        val job = Job()
        val writer = PdfSearchSnippetWriter("contrato", job)
        writer.startPage(1)
        writer.write("antes")
        job.cancel(CancellationException("Consulta cancelada"))

        val failure = runCatching { writer.write(" contrato") }.exceptionOrNull()

        assertTrue(failure is CancellationException)
    }

    @Test
    fun writerHonorsOffsetAndLength() {
        val writer = PdfSearchSnippetWriter("contrato", EmptyCoroutineContext)
        writer.startPage(2)

        val match = captureMatch {
            writer.write("IGNORARcontratoIGNORAR".toCharArray(), 7, 8)
            writer.finishPage()
        }

        assertEquals(PdfTextMatch("contrato", 2), match)
    }

    @Test
    fun emptyPdfAndMetadataDoNotBecomeSearchableContent() = runTest {
        PDDocument().use { document ->
            document.addPage(PDPage())
            document.documentInformation.title = "contrato"
            assertNull(findPdfTextMatch(document, "contrato"))
        }
    }

    private fun find(query: String, pieces: List<String>): PdfTextMatch? {
        val writer = PdfSearchSnippetWriter(query, EmptyCoroutineContext)
        return captureMatch {
            writer.startPage(1)
            pieces.forEach(writer::write)
            writer.finishPage()
        }
    }

    private inline fun captureMatch(block: () -> Unit): PdfTextMatch? = try {
        block()
        null
    } catch (found: PdfTextMatchFound) {
        found.match
    }
}
