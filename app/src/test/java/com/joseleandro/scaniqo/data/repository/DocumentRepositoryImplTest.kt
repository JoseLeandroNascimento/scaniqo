package com.joseleandro.scaniqo.data.repository

import com.joseleandro.scaniqo.data.datasource.DocumentDatasource
import com.joseleandro.scaniqo.data.local.document.DocumentEntity
import com.joseleandro.scaniqo.data.local.document.DocumentFileStore
import com.joseleandro.scaniqo.domain.model.DOCUMENT_NAME_MAX_LENGTH
import com.joseleandro.scaniqo.domain.model.DocumentModel
import com.joseleandro.scaniqo.domain.model.ScannedDocument
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentRepositoryImplTest {
    private val operations = mutableListOf<String>()
    private val datasource = FakeDatasource(operations)
    private val fileStore = FakeFileStore(operations)
    private val repository = DocumentRepositoryImpl(datasource, fileStore)

    @Test
    fun `cadastro normaliza nome e preserva metadados do scan`() = runBlocking {
        val scan = scan()
        val id = repository.create(scan, "  Recibos de outubro  ", tagId = 4L)

        val document = requireNotNull(repository.findById(id))
        assertEquals("Recibos de outubro", document.name)
        assertEquals(scan.pdfUri, document.pdfUri)
        assertEquals(scan.thumbnailUri, document.thumbnailUri)
        assertEquals(scan.pageCount, document.pageCount)
        assertEquals(scan.createdAt, document.createdAt)
        assertEquals(scan.sizeBytes, document.sizeBytes)
        assertEquals(4L, document.tagId)
        assertEquals(listOf(document), repository.observeDocuments().first())
        assertNull(repository.findById(999L))
    }

    @Test
    fun `confirmar formulario restaurado reutiliza documento e aplica os metadados atuais`() = runBlocking {
        val scan = scan()
        val firstId = repository.create(scan, "Nome anterior", tagId = 2L)
        val original = requireNotNull(repository.findById(firstId))
        val restoredRepository = DocumentRepositoryImpl(datasource, fileStore)

        val recoveredId = restoredRepository.create(scan, "  Nome revisado  ", tagId = 3L)
        val repeatedId = restoredRepository.create(scan, "Nome revisado", tagId = 3L)

        assertEquals(firstId, recoveredId)
        assertEquals(firstId, repeatedId)
        assertEquals(
            listOf(original.copy(name = "Nome revisado", tagId = 3L)),
            restoredRepository.observeDocuments().first()
        )
        assertEquals(listOf("insert-db", "update-db"), operations)
        assertEquals(0, fileStore.prepareCalls)
    }

    @Test
    fun `formulario restaurado continua rejeitando nome invalido`() = runBlocking {
        val firstId = repository.create(scan(), "Nome original", tagId = null)
        val restoredRepository = DocumentRepositoryImpl(datasource, fileStore)

        expectFailure<IllegalArgumentException> {
            restoredRepository.create(scan(), "  ", tagId = null)
        }

        assertEquals("Nome original", restoredRepository.findById(firstId)?.name)
        assertEquals(listOf("insert-db"), operations)
    }

    @Test
    fun `edicao modifica apenas nome e tag sem regenerar pdf`() = runBlocking {
        val id = repository.create(scan(), "Inicial", tagId = 2L)
        val original = requireNotNull(repository.findById(id))

        repository.update(id, "  Alterado  ", tagId = null)

        assertEquals(original.copy(name = "Alterado", tagId = null), repository.findById(id))
        assertEquals(0, fileStore.prepareCalls)
    }

    @Test
    fun `nomes invalidos nao chegam ao banco`() = runBlocking {
        listOf("", " \n ", "A".repeat(DOCUMENT_NAME_MAX_LENGTH + 1)).forEach { name ->
            expectFailure<IllegalArgumentException> { repository.create(scan(), name, null) }
            expectFailure<IllegalArgumentException> { repository.update(1L, name, null) }
        }

        assertTrue(operations.isEmpty())
        assertTrue(datasource.documents.value.isEmpty())
    }

    @Test
    fun `nome no limite e aceito depois de remover espacos`() = runBlocking {
        val name = "A".repeat(DOCUMENT_NAME_MAX_LENGTH)

        val id = repository.create(scan(), "  $name  ", null)

        assertEquals(name, repository.findById(id)?.name)
    }

    @Test
    fun `falha ao salvar preserva scan para permitir nova tentativa`() = runBlocking {
        val failure = IOException("Banco indisponível")
        datasource.failure = failure

        assertSame(failure, expectFailure<IOException> {
            repository.create(scan(), "Documento", null)
        })
        assertTrue(fileStore.deletedUris.isEmpty())

        datasource.failure = null
        repository.create(scan(), "Documento", null)
        assertEquals(1, datasource.documents.value.size)
    }

    @Test
    fun `descarte remove scan pendente mas protege documento ja persistido`() = runBlocking {
        val pending = scan().copy(pdfUri = "file:///pending.pdf")
        repository.discardScan(pending)
        assertEquals(listOf(pending.pdfUri), fileStore.deletedUris)

        repository.create(scan(), "Salvo", null)
        repository.discardScan(scan())
        assertEquals(listOf(pending.pdfUri), fileStore.deletedUris)
    }

    @Test
    fun `exclusao consulta registro atual e remove banco antes dos arquivos`() = runBlocking {
        val id = repository.create(scan(), "Documento", null)
        val stale = requireNotNull(repository.findById(id)).copy(pdfUri = "file:///outro.pdf")
        operations.clear()

        repository.delete(stale)

        assertEquals(listOf("delete-db", "delete-file"), operations)
        assertEquals(listOf(scan().pdfUri), fileStore.deletedUris)
        assertNull(repository.findById(id))
    }

    @Test
    fun `falha do banco nao apaga arquivo do documento`() = runBlocking {
        val id = repository.create(scan(), "Documento", null)
        val document = requireNotNull(repository.findById(id))
        datasource.failure = IOException("Não foi possível excluir")

        expectFailure<IOException> { repository.delete(document) }

        assertTrue(fileStore.deletedUris.isEmpty())
        assertEquals(1, datasource.documents.value.size)
    }

    @Test
    fun `limpeza com erro nao desfaz exclusao confirmada no banco`() = runBlocking {
        val id = repository.create(scan(), "Documento", null)
        val document = requireNotNull(repository.findById(id))
        fileStore.failure = IOException("Armazenamento indisponível")

        repository.delete(document)

        assertTrue(datasource.documents.value.isEmpty())
    }

    @Test
    fun `cancelamento da limpeza permanece cancelamento`() = runBlocking {
        val id = repository.create(scan(), "Documento", null)
        val document = requireNotNull(repository.findById(id))
        val cancellation = CancellationException("Operação cancelada")
        fileStore.failure = cancellation

        assertSame(cancellation, expectFailure<CancellationException> {
            repository.delete(document)
        })
        assertTrue(datasource.documents.value.isEmpty())
    }

    @Test
    fun `exportacao e compartilhamento usam o pdf armazenado`() = runBlocking {
        val id = repository.create(scan(), "Documento", null)
        val document = requireNotNull(repository.findById(id))

        repository.export(document, "content://destino/escolhido")
        val sharedUri = repository.prepareShare(document)

        assertEquals(document to "content://destino/escolhido", fileStore.exported)
        assertEquals(document, fileStore.shared)
        assertEquals("content://scaniqo.files/copia.pdf", sharedUri)
        assertEquals(0, fileStore.prepareCalls)
    }

    @Test
    fun `preparacao rejeita lista vazia e propaga falhas e cancelamentos`() = runBlocking {
        expectFailure<IllegalArgumentException> { repository.prepareScan(emptyList()) }
        assertEquals(0, fileStore.prepareCalls)
        assertEquals(scan(), repository.prepareScan(listOf("content://pagina/1")))

        val failure = IOException("Página indisponível")
        fileStore.failure = failure
        assertSame(failure, expectFailure<IOException> {
            repository.prepareScan(listOf("content://pagina/1"))
        })

        val cancellation = CancellationException("Cancelado")
        fileStore.failure = cancellation
        assertSame(cancellation, expectFailure<CancellationException> {
            repository.prepareScan(listOf("content://pagina/1"))
        })
        datasource.failure = cancellation
        assertSame(cancellation, expectFailure<CancellationException> {
            repository.create(scan(), "Documento", null)
        })
    }

    @Test
    fun `operacoes com id ausente nao reportam sucesso`() = runBlocking {
        expectFailure<IllegalStateException> { repository.update(99L, "Nome", null) }
        val missing = DocumentModel(
            id = 99L,
            name = "Ausente",
            pdfUri = scan().pdfUri,
            pageCount = 2,
            createdAt = 10L,
            sizeBytes = 200L
        )
        expectFailure<IllegalStateException> { repository.delete(missing) }
        assertTrue(fileStore.deletedUris.isEmpty())
    }

    @Test
    fun `substituir pdf preserva identidade e metadados e limpa somente depois da confirmacao`() = runBlocking {
        val first = scan().copy(protectionKeyId = "chave-v1")
        val id = repository.create(first, "Nome preservado", 8L)
        val original = requireNotNull(repository.findById(id))
        operations.clear()
        val replacement = scan().copy(
            pdfUri = "file:///documents/novo/document.pdf",
            pageCount = 5,
            sizeBytes = 9000L,
            createdAt = 999999L,
            thumbnailUri = null,
            protectionKeyId = "chave-v2"
        )

        val result = repository.replaceContent(id, replacement)

        assertEquals(original.id, result.id)
        assertEquals(original.name, result.name)
        assertEquals(original.tagId, result.tagId)
        assertEquals(original.createdAt, result.createdAt)
        assertEquals(replacement.pdfUri, result.pdfUri)
        assertEquals(5, result.pageCount)
        assertEquals("chave-v2", result.protectionKeyId)
        assertEquals(listOf("replace-db", "delete-file"), operations)
        assertEquals(listOf(first.pdfUri), fileStore.deletedUris)
    }

    @Test
    fun `substituicao nao permite retirar protecao silenciosamente`() = runBlocking {
        val protected = scan().copy(protectionKeyId = "chave-v1")
        val id = repository.create(protected, "Protegido", null)
        operations.clear()

        expectFailure<IllegalArgumentException> {
            repository.replaceContent(id, scan().copy(pdfUri = "file:///outro.pdf"))
        }

        assertEquals(protected.pdfUri, repository.findById(id)?.pdfUri)
        assertTrue(operations.isEmpty())
        assertTrue(fileStore.deletedUris.isEmpty())
    }

    @Test
    fun `remocao explicita autorizada permite persistir pdf sem protecao`() = runBlocking {
        val id = repository.create(scan().copy(protectionKeyId = "chave-v1"), "Protegido", null)
        val replacement = scan().copy(pdfUri = "file:///desprotegido.pdf")

        val result = repository.replaceContent(id, replacement, allowProtectionRemoval = true)

        assertNull(result.protectionKeyId)
        assertEquals(replacement.pdfUri, result.pdfUri)
    }

    @Test
    fun `cancelamento apos confirmar substituicao nao apaga o pdf novo`() = runBlocking {
        val id = repository.create(scan(), "Documento", null)
        val replacement = scan().copy(pdfUri = "file:///novo.pdf")
        val cancellation = CancellationException("Cancelamento durante a limpeza")
        fileStore.failure = cancellation

        val failure = expectFailure<CancellationException> {
            repository.replaceContent(id, replacement)
        }
        // A recuperação de stack trace das corrotinas pode recriar a mesma exceção.
        assertEquals(cancellation.message, failure.message)
        fileStore.failure = null
        repository.discardScan(replacement)

        assertEquals(replacement.pdfUri, repository.findById(id)?.pdfUri)
        assertTrue(fileStore.deletedUris.isEmpty())
    }

    @Test
    fun `falha de substituicao conserva documento anterior e seus arquivos`() = runBlocking {
        val id = repository.create(scan(), "Documento", null)
        datasource.failure = IOException("Banco indisponível")

        expectFailure<IOException> {
            repository.replaceContent(id, scan().copy(pdfUri = "file:///novo.pdf"))
        }

        assertEquals(scan().pdfUri, datasource.documents.value.single().pdfUri)
        assertTrue(fileStore.deletedUris.isEmpty())
    }

    private suspend inline fun <reified T : Throwable> expectFailure(
        block: suspend () -> Unit
    ): T {
        try {
            block()
        } catch (failure: Throwable) {
            assertTrue("Falha inesperada: $failure", failure is T)
            return failure as T
        }
        throw AssertionError("Era esperada uma falha do tipo ${T::class.java.simpleName}.")
    }

    private class FakeDatasource(private val operations: MutableList<String>) : DocumentDatasource {
        val documents = MutableStateFlow<List<DocumentEntity>>(emptyList())
        var failure: Throwable? = null
        private var nextId = 1L
        override val documentsObserver: Flow<List<DocumentEntity>> = documents

        override suspend fun findById(id: Long): DocumentEntity? {
            failure?.let { throw it }
            return documents.value.firstOrNull { it.id == id }
        }

        override suspend fun findByPdfUri(pdfUri: String): DocumentEntity? {
            failure?.let { throw it }
            return documents.value.firstOrNull { it.pdfUri == pdfUri }
        }

        override suspend fun insert(document: DocumentEntity): Long {
            failure?.let { throw it }
            operations += "insert-db"
            val id = nextId++
            documents.value += document.copy(id = id)
            return id
        }

        override suspend fun updateMetadata(id: Long, name: String, tagId: Long?): Int {
            failure?.let { throw it }
            operations += "update-db"
            if (documents.value.none { it.id == id }) return 0
            documents.value = documents.value.map {
                if (it.id == id) it.copy(name = name, tagId = tagId) else it
            }
            return 1
        }

        override suspend fun replaceContent(document: DocumentEntity): Int {
            failure?.let { throw it }
            if (documents.value.none { it.id == document.id }) return 0
            operations += "replace-db"
            documents.value = documents.value.map { if (it.id == document.id) document else it }
            return 1
        }

        override suspend fun delete(document: DocumentEntity): Int {
            failure?.let { throw it }
            operations += "delete-db"
            if (documents.value.none { it.id == document.id }) return 0
            documents.value = documents.value.filterNot { it.id == document.id }
            return 1
        }
    }

    private class FakeFileStore(private val operations: MutableList<String>) : DocumentFileStore {
        var failure: Throwable? = null
        var prepareCalls = 0
        val deletedUris = mutableListOf<String>()
        var exported: Pair<DocumentModel, String>? = null
        var shared: DocumentModel? = null

        override suspend fun prepareScan(pageUris: List<String>): ScannedDocument {
            prepareCalls++
            failure?.let { throw it }
            return scan()
        }

        override suspend fun deleteOwnedDocument(pdfUri: String) {
            operations += "delete-file"
            failure?.let { throw it }
            deletedUris += pdfUri
        }

        override suspend fun export(document: DocumentModel, targetUri: String) {
            failure?.let { throw it }
            exported = document to targetUri
        }

        override suspend fun prepareShare(document: DocumentModel): String {
            failure?.let { throw it }
            shared = document
            return "content://scaniqo.files/copia.pdf"
        }
    }

    companion object {
        private fun scan() = ScannedDocument(
            pdfUri = "file:///documents/scan/document.pdf",
            pageCount = 2,
            createdAt = 123456L,
            sizeBytes = 4096L,
            thumbnailUri = "file:///documents/scan/thumbnail.jpg"
        )
    }
}
