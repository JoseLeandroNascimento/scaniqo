package com.joseleandro.scaniqo.data.pdf

import com.joseleandro.scaniqo.domain.model.PdfExportQuality
import com.joseleandro.scaniqo.domain.model.PdfImageFilter
import com.joseleandro.scaniqo.domain.model.PdfPageSpec
import com.joseleandro.scaniqo.domain.model.PdfSignaturePlacement
import com.joseleandro.scaniqo.domain.model.PdfWorkspace
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PdfFilterThumbnailCacheTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun `filtro intensidade exportacao e protecao nao refazem amostras`() {
        val source = temporary.newFile().apply { writeText("fonte") }
        val workspace = workspace(source)
        val changed = workspace.copy(
            pages = workspace.pages.map {
                it.copy(layout = it.layout.copy(imageFilter = PdfImageFilter.ENHANCE, filterIntensity = .25f))
            },
            exportQuality = PdfExportQuality.SMALL,
            ocrEnabled = true,
            isProtected = true,
            protectionKeyId = "chave",
        )
        assertEquals(key(workspace, source), key(changed, source))
    }

    @Test
    fun `correcoes fonte assinatura e permissao de rasterizar invalidam amostras`() {
        val source = temporary.newFile().apply { writeText("fonte") }
        val signature = temporary.newFile().apply { writeText("assinatura") }
        val workspace = workspace(source)
        val original = key(workspace, source)
        val corrected = workspace.copy(pages = workspace.pages.map {
            it.copy(layout = it.layout.copy(straightenDegrees = 3f))
        })
        assertNotEquals(original, key(corrected, source))
        assertNotEquals(original, PdfFilterThumbnailKey.from(workspace, "pagina", source, true))
        val signed = workspace.copy(pages = workspace.pages.map {
            it.copy(signatures = listOf(PdfSignaturePlacement("assinatura", signature.toURI().toString())))
        })
        val before = PdfFilterThumbnailKey.from(signed, "pagina", source, false, listOf(signature))
        signature.appendText("nova")
        assertNotEquals(before, PdfFilterThumbnailKey.from(signed, "pagina", source, false, listOf(signature)))
        source.appendText("nova pagina")
        assertNotEquals(original, key(workspace, source))
    }

    @Test
    fun `reordenacao so altera amostras quando ha numeracao`() {
        val source = temporary.newFile()
        val workspace = workspace(source).let {
            it.copy(pages = it.pages + it.pages.first().copy(id = "outra"))
        }
        val reordered = workspace.copy(pages = workspace.pages.reversed())
        assertEquals(key(workspace, source), key(reordered, source))
        assertNotEquals(
            key(workspace.copy(showPageNumbers = true), source),
            key(reordered.copy(showPageNumbers = true), source),
        )
    }

    @Test
    fun `lote pronto evita uma nova leitura e uma nova geracao`() = runTest {
        val directory = temporary.newFolder()
        val source = temporary.newFile()
        val cache = PdfFilterThumbnailCache(directory)
        var calls = 0
        val first = cache.getOrCreate(key(workspace(source), source)) { targets ->
            calls++
            batch(targets)
        }
        val second = cache.getOrCreate(key(workspace(source), source)) { targets ->
            calls++
            batch(targets)
        }
        assertEquals(1, calls)
        assertEquals(first, second)
        assertEquals(PdfImageFilter.entries.size, directory.listFiles().orEmpty().size)
    }

    @Test
    fun `paginas nativas podem compartilhar uma unica imagem preservada`() = runTest {
        val directory = temporary.newFolder()
        val source = temporary.newFile()
        val cache = PdfFilterThumbnailCache(directory)
        val result = cache.getOrCreate(key(workspace(source), source)) { targets ->
            val file = targets.getValue(PdfImageFilter.ORIGINAL).apply { writeText("original") }
            PdfFilterThumbnailBatch(PdfImageFilter.entries.associateWith { file }, true)
        }
        assertTrue(result.requiresRasterization)
        assertEquals(1, result.images.values.toSet().size)
        assertEquals(1, directory.listFiles().orEmpty().size)
        cache.clear()
        assertTrue(directory.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun `amostras filtradas mantem aviso de rasterizacao ao reutilizar cache`() = runTest {
        val directory = temporary.newFolder()
        val source = temporary.newFile()
        val cache = PdfFilterThumbnailCache(directory)
        val key = PdfFilterThumbnailKey.from(workspace(source), "pagina", source, true)
        val first = cache.getOrCreate(key) { targets ->
            batch(targets).copy(requiresRasterization = true)
        }
        val cached = cache.getOrCreate(key) { error("Não deve gerar novamente.") }

        assertEquals(PdfImageFilter.entries.size, cached.images.values.toSet().size)
        assertTrue(first.requiresRasterization)
        assertTrue(cached.requiresRasterization)
        assertEquals(first, cached)
    }

    @Test
    fun `cancelamento no meio do lote remove os temporarios sem publicar resultados`() = runTest {
        val directory = temporary.newFolder()
        val source = temporary.newFile()
        val cache = PdfFilterThumbnailCache(directory)
        val error = runCatching {
            cache.getOrCreate(key(workspace(source), source)) { targets ->
                targets.values.take(3).forEach { it.writeText("parcial") }
                throw CancellationException("cancelado")
            }
        }.exceptionOrNull()
        assertTrue(error is CancellationException)
        assertTrue(directory.listFiles().orEmpty().isEmpty())
        val completed = cache.getOrCreate(key(workspace(source), source), create = ::batch)
        assertEquals(PdfImageFilter.entries.toSet(), completed.images.keys)
    }

    @Test
    fun `falha ao forcar atualizacao preserva lote anterior e retry troca as URIs`() = runTest {
        val directory = temporary.newFolder()
        val source = temporary.newFile()
        val cache = PdfFilterThumbnailCache(directory)
        val key = key(workspace(source), source)
        val first = cache.getOrCreate(key, create = ::batch)
        val error = runCatching {
            cache.getOrCreate(key, force = true) { targets ->
                targets.values.first().writeText("parcial")
                throw IOException("falha")
            }
        }.exceptionOrNull()
        assertTrue(error is IOException)
        assertTrue(first.images.values.all { it.isFile })
        assertEquals(first, cache.getOrCreate(key) { error("Não deve gerar novamente.") })
        val replaced = cache.getOrCreate(key, force = true, create = ::batch)
        assertNotEquals(first.images, replaced.images)
        assertTrue(first.images.values.none { it.exists() })
        assertTrue(replaced.images.values.all { it.isFile })
    }

    @Test
    fun `limite de lotes remove mais antigo e clear preserva outros arquivos da sessao`() = runTest {
        val directory = temporary.newFolder()
        val unrelated = File(directory, "source.pdf").apply { writeText("fonte") }
        val cache = PdfFilterThumbnailCache(directory, maxEntries = 1)
        val workspace = workspace(unrelated)
        val first = cache.getOrCreate(key(workspace, unrelated), create = ::batch)
        val second = cache.getOrCreate(
            PdfFilterThumbnailKey.from(workspace, "pagina", unrelated, true), create = ::batch,
        )
        assertTrue(first.images.values.none { it.exists() })
        assertTrue(second.images.values.all { it.isFile })
        cache.clear()
        assertTrue(second.images.values.none { it.exists() })
        assertTrue(unrelated.isFile)
    }

    @Test
    fun `limite de bytes e lote incompleto nao deixam residuos`() = runTest {
        val directory = temporary.newFolder()
        val source = temporary.newFile()
        val key = key(workspace(source), source)
        val smallCache = PdfFilterThumbnailCache(directory, maxBytes = 1)
        assertTrue(runCatching {
            smallCache.getOrCreate(key, create = ::batch)
        }.exceptionOrNull() is IOException)
        assertTrue(directory.listFiles().orEmpty().isEmpty())
        val cache = PdfFilterThumbnailCache(directory)
        assertTrue(runCatching {
            cache.getOrCreate(key) { targets ->
                val original = targets.getValue(PdfImageFilter.ORIGINAL).apply { writeText("original") }
                PdfFilterThumbnailBatch(mapOf(PdfImageFilter.ORIGINAL to original), false)
            }
        }.exceptionOrNull() is IllegalArgumentException)
        assertTrue(directory.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun `amostra removida externamente invalida todo o lote`() = runTest {
        val directory = temporary.newFolder()
        val source = temporary.newFile()
        val cache = PdfFilterThumbnailCache(directory)
        val key = key(workspace(source), source)
        val first = cache.getOrCreate(key, create = ::batch)
        first.images.values.first().delete()
        val second = cache.getOrCreate(key, create = ::batch)
        assertNotEquals(first.images, second.images)
        assertFalse(first.images.values.any { it.exists() })
        assertEquals(PdfImageFilter.entries.size, directory.listFiles().orEmpty().size)
    }

    private fun workspace(source: File) = PdfWorkspace(
        documentId = 1,
        workspaceId = "sessao",
        directoryUri = requireNotNull(source.parentFile).toURI().toString(),
        pages = listOf(PdfPageSpec("pagina", source.toURI().toString(), 0)),
    )

    private fun key(workspace: PdfWorkspace, source: File) =
        PdfFilterThumbnailKey.from(workspace, "pagina", source, false)

    private fun batch(targets: Map<PdfImageFilter, File>): PdfFilterThumbnailBatch {
        targets.forEach { (filter, file) -> file.writeText(filter.name) }
        return PdfFilterThumbnailBatch(targets, false)
    }
}
