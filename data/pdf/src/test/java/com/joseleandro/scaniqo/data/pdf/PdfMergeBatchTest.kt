package com.joseleandro.scaniqo.data.pdf

import com.joseleandro.scaniqo.domain.model.PdfMergeLimitException
import com.joseleandro.scaniqo.domain.model.PdfMergePasswordRequiredException
import com.joseleandro.scaniqo.domain.model.PdfMergeSource
import com.joseleandro.scaniqo.domain.model.PdfPageLayout
import com.joseleandro.scaniqo.domain.model.PdfPageSpec
import com.joseleandro.scaniqo.domain.model.PdfWorkspace
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PdfMergeBatchTest {
    @get:Rule
    val temporary = TemporaryFolder()

    @Test
    fun `lote preserva ordem receitas e protecao sem modificar fontes anteriores`() = runBlocking {
        val fixture = fixture()
        val requests = mutableListOf<PdfMergeBatch.Preparation>()

        val result = fixture.batch.append(
            fixture.workspace, listOf(PdfMergeSource("pdf-a"), PdfMergeSource("pdf-b")), 10L
        ) { request ->
            requests += request
            prepare(request, bytes = 8)
            if (request.source.uri == "pdf-a") 2 else 1
        }

        assertEquals(4, result.pages.size)
        assertEquals(fixture.workspace.pages.single(), result.pages.first())
        assertEquals(listOf(0, 1, 0), result.pages.drop(1).map { it.sourcePageIndex })
        assertEquals(result.pages[1].sourceUri, result.pages[2].sourceUri)
        assertNotEquals(result.pages[2].sourceUri, result.pages[3].sourceUri)
        assertEquals(result.pages.size, result.pages.map { it.id }.distinct().size)
        assertEquals(fixture.workspace, result.copy(pages = fixture.workspace.pages))
        assertEquals(1, fixture.workspace.pages.size)
        assertTrue(result.pages.drop(1).all { it.layout == PdfPageLayout() })
        assertTrue(requests.all { it.output.isFile && !it.incoming.exists() })
        assertEquals(listOf(99, 97), requests.map { it.remainingPages })
        assertEquals(
            listOf(PdfWorkspaceFiles.MAX_BYTES - 10, PdfWorkspaceFiles.MAX_BYTES - 18),
            requests.map { it.remainingBytes }
        )
        assertEquals("original", fixture.original.readText())
    }

    @Test
    fun `fonte repetida e preparada uma vez mas suas paginas mantem a ordem solicitada`() = runBlocking {
        val fixture = fixture()
        val preparedUris = mutableListOf<String>()

        val result = fixture.batch.append(
            fixture.workspace,
            listOf(PdfMergeSource("pdf-a"), PdfMergeSource("pdf-b"), PdfMergeSource("pdf-a")),
            PdfWorkspaceFiles.MAX_BYTES - 16
        ) { request ->
            preparedUris += request.source.uri
            prepare(request, 8)
            if (request.source.uri == "pdf-a") 2 else 1
        }

        assertEquals(listOf("pdf-a", "pdf-b"), preparedUris)
        assertEquals(listOf(0, 1, 0, 0, 1), result.pages.drop(1).map { it.sourcePageIndex })
        assertEquals(result.pages[1].sourceUri, result.pages[4].sourceUri)
        assertEquals(result.pages[2].sourceUri, result.pages[5].sourceUri)
        assertEquals(3, fixture.directory.listFiles().orEmpty().size)
    }

    @Test
    fun `senha exigida pelo segundo PDF desfaz todo lote e informa a URI correta`() = runBlocking {
        val fixture = fixture()

        val failure = runCatching {
            fixture.batch.append(
                fixture.workspace, listOf(PdfMergeSource("pdf-a"), PdfMergeSource("pdf-protegido")), 10
            ) { request ->
                prepare(request)
                if (request.source.uri == "pdf-protegido") {
                    throw PdfMergePasswordRequiredException(request.source.uri)
                }
                2
            }
        }.exceptionOrNull()

        assertTrue(failure is PdfMergePasswordRequiredException)
        assertEquals("pdf-protegido", (failure as PdfMergePasswordRequiredException).uri)
        assertRolledBack(fixture)
    }

    @Test
    fun `falha de leitura do segundo PDF remove arquivos incompletos e preserva o original`() = runBlocking {
        val fixture = fixture()

        val failure = runCatching {
            fixture.batch.append(
                fixture.workspace, listOf(PdfMergeSource("pdf-a"), PdfMergeSource("pdf-b")), 10
            ) { request ->
                prepare(request)
                if (request.source.uri == "pdf-b") throw IOException("Falha do provedor")
                1
            }
        }.exceptionOrNull()

        assertTrue(failure is IOException)
        assertEquals("Falha do provedor", failure?.message)
        assertRolledBack(fixture)
    }

    @Test
    fun `segundo PDF que excede cem paginas desfaz tambem o primeiro`() = runBlocking {
        val fixture = fixture(pageCount = 97)

        val failure = runCatching {
            fixture.batch.append(
                fixture.workspace, listOf(PdfMergeSource("pdf-a"), PdfMergeSource("pdf-b")), 10
            ) { request ->
                prepare(request)
                2
            }
        }.exceptionOrNull()

        assertLimit(failure, PdfMergeLimitException.Reason.PAGE_COUNT)
        assertRolledBack(fixture)
    }

    @Test
    fun `fonte repetida tambem respeita limite de paginas sem duplicar armazenamento`() = runBlocking {
        val fixture = fixture(pageCount = 97)
        var preparationCount = 0

        val failure = runCatching {
            fixture.batch.append(
                fixture.workspace, listOf(PdfMergeSource("pdf-a"), PdfMergeSource("pdf-a")), 10
            ) { request ->
                preparationCount++
                prepare(request)
                2
            }
        }.exceptionOrNull()

        assertLimit(failure, PdfMergeLimitException.Reason.PAGE_COUNT)
        assertEquals(1, preparationCount)
        assertRolledBack(fixture)
    }

    @Test
    fun `segundo PDF que excede tamanho total desfaz todos os arquivos novos`() = runBlocking {
        val fixture = fixture()

        val failure = runCatching {
            fixture.batch.append(
                fixture.workspace,
                listOf(PdfMergeSource("pdf-a"), PdfMergeSource("pdf-b")),
                PdfWorkspaceFiles.MAX_BYTES - 15
            ) { request ->
                prepare(request, bytes = 8)
                1
            }
        }.exceptionOrNull()

        assertLimit(failure, PdfMergeLimitException.Reason.TOTAL_BYTES)
        assertRolledBack(fixture)
    }

    @Test
    fun `documento ja cheio falha antes de copiar qualquer fonte`() = runBlocking {
        val fixture = fixture(pageCount = 100)
        var called = false

        val failure = runCatching {
            fixture.batch.append(fixture.workspace, listOf(PdfMergeSource("pdf-a")), 10) {
                called = true
                1
            }
        }.exceptionOrNull()

        assertLimit(failure, PdfMergeLimitException.Reason.PAGE_COUNT)
        assertFalse(called)
        assertRolledBack(fixture)
    }

    @Test
    fun `cancelamento apos preparar o primeiro PDF limpa o lote e continua cancelado`() = runBlocking {
        val fixture = fixture()
        val job = Job()

        val failure = runCatching {
            withContext(job) {
                fixture.batch.append(
                    fixture.workspace, listOf(PdfMergeSource("pdf-a"), PdfMergeSource("pdf-b")), 10
                ) { request ->
                    prepare(request)
                    if (request.source.uri == "pdf-b") {
                        job.cancel(CancellationException("Mesclagem cancelada"))
                        currentCoroutineContext().ensureActive()
                    }
                    1
                }
            }
        }.exceptionOrNull()

        assertTrue(failure is CancellationException)
        assertEquals("Mesclagem cancelada", failure?.message)
        assertRolledBack(fixture)
    }

    @Test
    fun `rollback apos retorno e repetido remove apenas os arquivos daquele lote`() = runBlocking {
        val fixture = fixture()
        fixture.batch.append(fixture.workspace, listOf(PdfMergeSource("pdf-a")), 10) {
            prepare(it)
            1
        }
        val laterFile = File(fixture.directory, "outra-operacao.pdf").apply { writeText("outro lote") }

        fixture.batch.rollback()
        fixture.batch.rollback()

        assertEquals(setOf(fixture.original.name, laterFile.name), fixture.directory.list().orEmpty().toSet())
        assertEquals("original", fixture.original.readText())
        assertEquals("outro lote", laterFile.readText())
    }

    @Test
    fun `senha nao aparece na representacao textual da fonte`() {
        val source = PdfMergeSource("content://pdf/selecionado", "segredo123")
        assertFalse(source.toString().contains("segredo123"))
    }


    @Test
    fun `bloco atual ocupa inicio meio ou fim preservando receitas e protecao`() = runBlocking {
        for (currentIndex in 0..2) {
            val fixture = fixture(pageCount = 2)
            val requests = mutableListOf<PdfMergeBatch.Preparation>()
            val result = fixture.batch.append(
                fixture.workspace,
                listOf(PdfMergeSource("pdf-a"), PdfMergeSource("pdf-b")),
                10L,
                currentDocumentIndex = currentIndex,
            ) { request ->
                requests += request
                prepare(request)
                if (request.source.uri == "pdf-a") 2 else 1
            }
            val added = requests.map { request ->
                result.pages.filter { it.sourceUri == request.output.toURI().toString() }
            }
            assertEquals(
                added.take(currentIndex).flatten() + fixture.workspace.pages + added.drop(currentIndex).flatten(),
                result.pages,
            )
            assertEquals(listOf("pdf-a", "pdf-b"), requests.map { it.source.uri })
            assertEquals(5, result.pages.size)
            assertEquals(5, result.pages.map { it.id }.distinct().size)
            assertEquals(fixture.workspace, result.copy(pages = fixture.workspace.pages))
            assertEquals("original", fixture.original.readText())
        }
    }

    @Test
    fun `falha depois do bloco atual desfaz tambem os arquivos anteriores ao bloco`() = runBlocking {
        val fixture = fixture()
        val failure = runCatching {
            fixture.batch.append(
                fixture.workspace, listOf(PdfMergeSource("antes"), PdfMergeSource("depois")),
                10L, currentDocumentIndex = 1,
            ) { request ->
                prepare(request)
                if (request.source.uri == "depois") throw PdfMergePasswordRequiredException("depois")
                2
            }
        }.exceptionOrNull()

        assertTrue(failure is PdfMergePasswordRequiredException)
        assertRolledBack(fixture)
    }

    @Test
    fun `posicao invalida do bloco atual falha antes de importar`() = runBlocking {
        val fixture = fixture()
        var prepared = false
        val failure = runCatching {
            fixture.batch.append(
                fixture.workspace, listOf(PdfMergeSource("anexo")), 10L, currentDocumentIndex = 2,
            ) {
                prepared = true
                1
            }
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertFalse(prepared)
        assertRolledBack(fixture)
    }

    private fun prepare(request: PdfMergeBatch.Preparation, bytes: Int = 8) {
        request.incoming.writeText("arquivo recebido")
        request.output.writeBytes(ByteArray(bytes) { 42 })
    }

    private fun assertLimit(failure: Throwable?, reason: PdfMergeLimitException.Reason) {
        assertTrue(failure is PdfMergeLimitException)
        assertEquals(reason, (failure as PdfMergeLimitException).reason)
    }

    private fun assertRolledBack(fixture: Fixture) {
        assertEquals(setOf(fixture.original.name), fixture.directory.list().orEmpty().toSet())
        assertEquals("original", fixture.original.readText())
    }

    private fun fixture(pageCount: Int = 1): Fixture {
        val directory = temporary.newFolder()
        val original = File(directory, "source-original.pdf").apply { writeText("original") }
        val workspace = PdfWorkspace(
            documentId = 7,
            workspaceId = directory.name,
            directoryUri = directory.toURI().toString(),
            pages = List(pageCount) {
                PdfPageSpec("original-$it", original.toURI().toString(), it,
                    PdfPageLayout(rotationQuarterTurns = 1, contentScale = .75f))
            },
            showPageNumbers = true,
            isProtected = true,
            protectionKeyId = "chave-original"
        )
        return Fixture(directory, original, workspace, PdfMergeBatch(directory) { it.toURI().toString() })
    }

    private data class Fixture(
        val directory: File,
        val original: File,
        val workspace: PdfWorkspace,
        val batch: PdfMergeBatch
    )
}
