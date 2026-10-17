package com.joseleandro.scaniqo.data.repository

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.joseleandro.scaniqo.data.datasource.TagDatasource
import com.joseleandro.scaniqo.data.local.tag.TagEntity
import com.joseleandro.scaniqo.domain.model.TAG_NAME_MAX_LENGTH
import com.joseleandro.scaniqo.domain.model.TagModel
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class TagRepositoryImplTest {

    private val datasource = FakeTagDatasource()
    private val repository = TagRepositoryImpl(datasource)

    @Test
    fun `observacao reflete mudancas e preserva metadados persistidos`() = runBlocking {
        val emissions = mutableListOf<List<TagModel>>()
        val collection = launch(start = CoroutineStart.UNDISPATCHED) {
            repository.observeTags().take(2).toList(emissions)
        }
        val entity = TagEntity(
            id = 42L,
            name = "Recibos",
            colorArgb = 0x804080C0.toInt(),
            iconKey = "future_icon"
        )

        datasource.tags.value = listOf(entity)
        collection.join()

        assertTrue(emissions.first().isEmpty())
        val tag = emissions.last().single()
        assertEquals(entity.id, tag.id)
        assertEquals(entity.name, tag.name)
        assertEquals(entity.colorArgb, tag.color.toArgb())
        assertEquals(entity.iconKey, tag.iconKey)
    }

    @Test
    fun `busca converte tag existente e retorna nulo para id ausente`() = runBlocking {
        datasource.tags.value = listOf(
            TagEntity(7L, "Trabalho", 0xFF4080C0.toInt(), "document")
        )

        assertEquals("Trabalho", repository.findById(7L)?.name)
        assertEquals("document", repository.findById(7L)?.iconKey)
        assertNull(repository.findById(8L))
    }

    @Test
    fun `cadastro remove espacos nas pontas e retorna id gerado`() = runBlocking {
        val original = tag(name = "  Contas da casa  ")

        val id = repository.create(original)

        assertEquals(1L, id)
        val inserted = datasource.tags.value.single()
        assertEquals("Contas da casa", inserted.name)
        assertEquals(original.color.toArgb(), inserted.colorArgb)
        assertEquals(original.iconKey, inserted.iconKey)
    }

    @Test
    fun `edicao preserva identidade e atualiza nome cor e icone`() = runBlocking {
        val id = repository.create(tag(name = "Inicial"))
        val edited = tag(id = id, name = "  Editada  ").copy(
            color = Color(0xFFBA68C8.toInt()),
            iconKey = "future_icon"
        )

        repository.update(edited)

        val updated = datasource.tags.value.single()
        assertEquals(id, updated.id)
        assertEquals("Editada", updated.name)
        assertEquals(edited.color.toArgb(), updated.colorArgb)
        assertEquals(edited.iconKey, updated.iconKey)
    }

    @Test
    fun `nomes invalidos falham antes de consultar a persistencia`() = runBlocking {
        val invalidNames = listOf("", " \t\n ", "A".repeat(TAG_NAME_MAX_LENGTH + 1))

        invalidNames.forEach { name ->
            expectFailure<IllegalArgumentException> {
                repository.create(tag(name = name))
            }
            expectFailure<IllegalArgumentException> {
                repository.update(tag(id = 1L, name = name))
            }
        }

        assertEquals(0, datasource.insertCalls)
        assertEquals(0, datasource.updateCalls)
    }

    @Test
    fun `limite do nome considera o texto depois da normalizacao`() = runBlocking {
        val nameAtLimit = "A".repeat(TAG_NAME_MAX_LENGTH)

        repository.create(tag(name = "  $nameAtLimit  "))

        assertEquals(nameAtLimit, datasource.tags.value.single().name)
    }

    @Test
    fun `edicao e exclusao de tag ausente nao reportam sucesso`() = runBlocking {
        val missing = tag(id = 999L)

        expectFailure<IllegalStateException> { repository.update(missing) }
        expectFailure<IllegalStateException> { repository.delete(missing) }

        assertTrue(datasource.tags.value.isEmpty())
    }

    @Test
    fun `exclusao usa identidade e permite remover nomes antigos invalidos`() = runBlocking {
        datasource.tags.value = listOf(
            TagEntity(3L, "", 0xFF4080C0.toInt(), "document"),
            TagEntity(4L, "Manter", 0xFF4080C0.toInt(), "document")
        )

        repository.delete(tag(id = 3L, name = ""))

        assertEquals(listOf(4L), datasource.tags.value.map { it.id })
    }

    @Test
    fun `falhas de persistencia chegam ao chamador sem serem ocultadas`() = runBlocking {
        val failure = IOException("Falha de armazenamento")
        datasource.failure = failure

        assertSame(failure, expectFailure<IOException> { repository.create(tag()) })
        assertSame(failure, expectFailure<IOException> { repository.update(tag(id = 1L)) })
        assertSame(failure, expectFailure<IOException> { repository.delete(tag(id = 1L)) })
        assertSame(failure, expectFailure<IOException> { repository.findById(1L) })
        assertSame(failure, expectFailure<IOException> { repository.observeTags().first() })
    }

    @Test
    fun `cancelamento permanece cancelamento em leituras e escritas`() = runBlocking {
        val cancellation = CancellationException("Operação cancelada")
        datasource.failure = cancellation

        assertSame(
            cancellation,
            expectFailure<CancellationException> { repository.create(tag()) }
        )
        assertSame(
            cancellation,
            expectFailure<CancellationException> { repository.update(tag(id = 1L)) }
        )
        assertSame(
            cancellation,
            expectFailure<CancellationException> { repository.delete(tag(id = 1L)) }
        )
        assertSame(
            cancellation,
            expectFailure<CancellationException> { repository.findById(1L) }
        )
        assertSame(
            cancellation,
            expectFailure<CancellationException> { repository.observeTags().first() }
        )
    }

    private fun tag(id: Long = 0L, name: String = "Documentos") = TagModel(
        id = id,
        name = name,
        color = Color(0x804080C0.toInt()),
        iconKey = "document"
    )

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

    private class FakeTagDatasource : TagDatasource {
        val tags = MutableStateFlow<List<TagEntity>>(emptyList())
        var failure: Throwable? = null
        var insertCalls = 0
        var updateCalls = 0
        private var nextId = 1L

        override val tagsObserver: Flow<List<TagEntity>>
            get() = failure?.let { cause -> flow<List<TagEntity>> { throw cause } } ?: tags

        override suspend fun findById(id: Long): TagEntity? {
            failure?.let { throw it }
            return tags.value.firstOrNull { it.id == id }
        }

        override suspend fun insert(tag: TagEntity): Long {
            insertCalls++
            failure?.let { throw it }
            val id = nextId++
            tags.value = tags.value + tag.copy(id = id)
            return id
        }

        override suspend fun update(tag: TagEntity): Int {
            updateCalls++
            failure?.let { throw it }
            if (tags.value.none { it.id == tag.id }) return 0
            tags.value = tags.value.map { current ->
                if (current.id == tag.id) tag else current
            }
            return 1
        }

        override suspend fun delete(tag: TagEntity): Int {
            failure?.let { throw it }
            if (tags.value.none { it.id == tag.id }) return 0
            tags.value = tags.value.filterNot { it.id == tag.id }
            return 1
        }
    }
}
