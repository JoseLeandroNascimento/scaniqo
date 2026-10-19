package com.joseleandro.scaniqo.data.repository

import com.joseleandro.scaniqo.data.pdf.PdfTextMatch
import com.joseleandro.scaniqo.domain.model.DocumentModel
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
