package com.joseleandro.scaniqo.data.repository

import com.joseleandro.scaniqo.data.pdf.PdfTextMatch
import com.joseleandro.scaniqo.domain.model.DocumentModel
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import com.joseleandro.scaniqo.domain.model.DocumentSearchUpdate
import com.joseleandro.scaniqo.domain.repository.DocumentSearchRepository
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AndroidDocumentSearchRepositoryTest {
    @Test
    fun blankQueryDoesNotOpenAnyFiles() = runTest {
        val repository = AndroidDocumentSearchRepository(
            openPdf = { error("A busca vazia não deve abrir arquivos") },
            ioDispatcher = StandardTestDispatcher(testScheduler)
        )

        assertTrue(repository.search(listOf(document(1, "Arquivo")), " \n\t ").isEmpty())
    }

    @Test
    fun namesMatchIgnoringCaseAndExtraWhitespaceWithoutOpeningThePdf() = runTest {
        val original = document(1, "Meu   CONTRATO.pdf", protected = true)
        val repository = AndroidDocumentSearchRepository(
            openPdf = { error("A correspondência no nome não precisa abrir o PDF") },
            ioDispatcher = StandardTestDispatcher(testScheduler)
        )

        val result = repository.search(listOf(original), "  meu contrato  ").single()

        assertEquals(original, result.document)
        assertTrue(result.matchesName)
        assertEquals(null, result.contentSnippet)
        assertEquals(null, result.pageNumber)
    }

    @Test
    fun namesPrecedeContentMatchesAndPreserveLibraryOrderWithinEachGroup() = runTest {
        val opened = mutableListOf<String>()
        val originals = listOf(
            document(1, "Primeiro"),
            document(2, "Contrato B"),
            document(3, "Contrato A"),
            document(4, "Outro")
        )
        val repository = AndroidDocumentSearchRepository(
            openPdf = { uri -> opened += uri; PDDocument() },
            ioDispatcher = StandardTestDispatcher(testScheduler),
            searchContent = { _, query ->
                assertEquals("contrato", query)
                PdfTextMatch("Trecho do contrato encontrado.", pageNumber = 3)
            }
        )

        val result = repository.search(originals, "contrato")

        assertEquals(listOf(2L, 3L, 1L, 4L), result.map { it.document.id })
        assertEquals(listOf(true, true, false, false), result.map { it.matchesName })
        assertEquals(listOf(originals[0].pdfUri, originals[3].pdfUri), opened)
        assertEquals("Trecho do contrato encontrado.", result[2].contentSnippet)
        assertEquals(3, result[2].pageNumber)
    }

    @Test
    fun protectedRecordsAreSkippedForContentEvenWhenTheirPasswordCouldBeAvailable() = runTest {
        val repository = AndroidDocumentSearchRepository(
            openPdf = { error("A busca não deve desbloquear o documento") },
            ioDispatcher = StandardTestDispatcher(testScheduler)
        )

        assertTrue(repository.search(listOf(document(1, "Privado", protected = true)), "contrato").isEmpty())
    }

    @Test
    fun unreadableFileDoesNotPreventSearchingRemainingDocuments() = runTest {
        val repository = AndroidDocumentSearchRepository(
            openPdf = { uri ->
                if (uri.endsWith("/1.pdf")) throw IOException("Arquivo danificado")
                PDDocument()
            },
            ioDispatcher = StandardTestDispatcher(testScheduler),
            searchContent = { _, _ -> PdfTextMatch("contrato válido", 1) }
        )

        val result = repository.search(
            listOf(document(1, "Danificado"), document(2, "Acessível")),
            "contrato"
        )

        assertEquals(listOf(2L), result.map { it.document.id })
    }

    @Test
    fun extractionFailureClosesDocumentAndDoesNotRemoveNameMatches() = runTest {
        var closed = false
        val repository = AndroidDocumentSearchRepository(
            openPdf = {
                object : PDDocument() {
                    override fun close() {
                        closed = true
                        super.close()
                    }
                }
            },
            ioDispatcher = StandardTestDispatcher(testScheduler),
            searchContent = { _, _ -> throw IOException("Fonte inválida") }
        )

        val result = repository.search(
            listOf(document(1, "Contrato"), document(2, "Defeituoso")),
            "contrato"
        )

        assertEquals(listOf(1L), result.map { it.document.id })
        assertTrue(closed)
    }

    @Test
    fun extractedDocumentIsClosedAfterReturningTheFirstMatch() = runTest {
        var closed = false
        val repository = AndroidDocumentSearchRepository(
            openPdf = {
                object : PDDocument() {
                    override fun close() {
                        closed = true
                        super.close()
                    }
                }
            },
            ioDispatcher = StandardTestDispatcher(testScheduler),
            searchContent = { _, _ -> PdfTextMatch("contrato", 2) }
        )

        assertEquals(2, repository.search(listOf(document(1, "Arquivo")), "contrato").single().pageNumber)
        assertTrue(closed)
    }

    @Test
    fun cancellationPropagatesAndClosesTheCurrentDocument() = runTest {
        var closed = false
        var openCount = 0
        val repository = AndroidDocumentSearchRepository(
            openPdf = {
                openCount++
                object : PDDocument() {
                    override fun close() {
                        closed = true
                        super.close()
                    }
                }
            },
            ioDispatcher = StandardTestDispatcher(testScheduler),
            searchContent = { _, _ -> throw CancellationException("Nova consulta") }
        )

        val failure = runCatching {
            repository.search(listOf(document(1, "Um"), document(2, "Dois")), "contrato")
        }.exceptionOrNull()

        assertTrue(failure is CancellationException)
        assertEquals("Nova consulta", failure?.message)
        assertEquals(1, openCount)
        assertTrue(closed)
    }

    @Test
    fun encryptedPdfWithoutProtectionMetadataIsNotExtracted() = runTest {
        val bytes = ByteArrayOutputStream().use { output ->
            PDDocument().use { pdf ->
                pdf.addPage(PDPage())
                pdf.protect(StandardProtectionPolicy("owner-password", "", AccessPermission()).apply {
                    encryptionKeyLength = 256
                    isPreferAES = true
                })
                pdf.save(output)
            }
            output.toByteArray()
        }
        var extracted = false
        val repository = AndroidDocumentSearchRepository(
            openPdf = { PDDocument.load(bytes) },
            ioDispatcher = StandardTestDispatcher(testScheduler),
            searchContent = { _, _ -> extracted = true; PdfTextMatch("contrato", 1) }
        )

        assertTrue(repository.search(listOf(document(1, "Protegido externamente")), "contrato").isEmpty())
        assertFalse(extracted)
    }

    @Test
    fun imageOnlyDocumentHasNoContentMatch() = runTest {
        val repository = AndroidDocumentSearchRepository(
            openPdf = { PDDocument() },
            ioDispatcher = StandardTestDispatcher(testScheduler),
            searchContent = { _, _ -> null }
        )

        assertTrue(repository.search(listOf(document(1, "Digitalização")), "contrato").isEmpty())
    }

    @Test
    fun publishesNamesWhilePdfSearchIsStillRunningAndNeverDuplicatesDocuments() = runTest {
        val gate = CompletableDeferred<Unit>()
        val updates = mutableListOf<DocumentSearchUpdate>()
        val originals = listOf(document(1, "Other"), document(2, "Contract"), document(1, "Other"))
        val repository = AndroidDocumentSearchRepository(
            openPdf = { PDDocument() },
            ioDispatcher = StandardTestDispatcher(testScheduler),
            searchContent = { _, _ -> gate.await(); PdfTextMatch("contract content", 4) },
        )
        val collecting = launch { repository.searchUpdates(originals, "contract").toList(updates) }
        runCurrent()
        assertEquals(listOf(2L), updates.last().results.map { it.document.id })
        assertFalse(updates.last().isComplete)
        gate.complete(Unit)
        collecting.join()
        assertEquals(listOf(2L, 1L), updates.last().results.map { it.document.id })
        assertEquals(4, updates.last().results.last().pageNumber)
        assertTrue(updates.last().isComplete)
        assertFalse(updates.last().resultLimitReached)
    }

    @Test
    fun enoughNameMatchesStopBeforeOpeningPdfsAndPublishOnlyBatches() = runTest {
        var opened = 0
        val originals = listOf(document(0, "Content candidate")) +
            (1L..1_000L).flatMap { id -> listOf(document(id, "Contract"), document(id, "Contract")) }
        val repository = AndroidDocumentSearchRepository(
            openPdf = { opened++; PDDocument() },
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )
        val updates = repository.searchUpdates(originals, "contract").toList()
        val final = updates.last()
        assertEquals(DocumentSearchRepository.MAX_RESULTS, final.results.size)
        assertEquals(final.results.size, final.results.map { it.document.id }.distinct().size)
        assertTrue(final.isComplete)
        assertTrue(final.resultLimitReached)
        assertTrue(updates.size <= 17)
        assertEquals(0, opened)
    }

    @Test
    fun contentResultLimitStopsOpeningAdditionalFiles() = runTest {
        var opened = 0
        val repository = AndroidDocumentSearchRepository(
            openPdf = { opened++; PDDocument() },
            ioDispatcher = StandardTestDispatcher(testScheduler),
            searchContent = { _, _ -> PdfTextMatch("contract", 2) },
        )
        val updates = repository.searchUpdates(
            (1L..1_000L).map { document(it, "Other") }, "contract",
        ).toList()
        assertEquals(DocumentSearchRepository.MAX_RESULTS, opened)
        assertEquals(DocumentSearchRepository.MAX_RESULTS, updates.last().results.size)
        assertTrue(updates.last().resultLimitReached)
        assertTrue(updates.last().isComplete)
    }

    @Test
    fun cancellingProgressiveCollectionClosesTheActivePdfAndDoesNotOpenTheNextOne() = runTest {
        var opened = 0
        var closed = 0
        val gate = CompletableDeferred<Unit>()
        val repository = AndroidDocumentSearchRepository(
            openPdf = {
                opened++
                object : PDDocument() {
                    override fun close() { closed++; super.close() }
                }
            },
            ioDispatcher = StandardTestDispatcher(testScheduler),
            searchContent = { _, _ -> gate.await(); null },
        )
        val collecting = launch {
            repository.searchUpdates(listOf(document(1, "One"), document(2, "Two")), "contract").toList()
        }
        runCurrent()
        assertEquals(1, opened)
        collecting.cancel()
        collecting.join()
        assertEquals(1, closed)
        assertEquals(1, opened)
    }

    private fun document(id: Long, name: String, protected: Boolean = false) = DocumentModel(
        id = id,
        name = name,
        pdfUri = "file:///documents/$id.pdf",
        pageCount = 3,
        createdAt = id,
        sizeBytes = 100,
        protectionKeyId = if (protected) "key-$id" else null
    )
}
