package com.joseleandro.scaniqo.data.pdf

import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PdfWorkspaceCacheTest {
    @get:Rule
    val temporary = TemporaryFolder()

    @Test
    fun `inicio remove fontes e previews antigos sem tocar documentos permanentes`() = runBlocking {
        val root = temporary.newFolder("pdf_workspaces")
        val old = File(root, UUID.randomUUID().toString()).apply { mkdir() }
        File(old, "source_antiga.pdf").writeText("fonte temporária")
        File(old, "preview_antiga.pdf").writeText("prévia temporária")
        val scratch = File(root, "PDFBox123.tmp").apply { writeText("cache do parser") }
        val stored = temporary.newFile("document.pdf").apply { writeText("documento salvo") }

        PdfWorkspaceCache.initialize(root)

        assertFalse(old.exists())
        assertFalse(scratch.exists())
        assertTrue(stored.exists())
    }

    @Test
    fun `inicializacoes posteriores e concorrentes preservam sessoes deste processo`() = runBlocking {
        val root = temporary.newFolder("pdf_workspaces")
        PdfWorkspaceCache.initialize(root)
        val active = File(root, UUID.randomUUID().toString()).apply { mkdir() }
        val source = File(active, "source_atual.pdf").apply { writeText("edição em andamento") }

        coroutineScope {
            repeat(12) { launch(Dispatchers.Default) { PdfWorkspaceCache.initialize(root) } }
        }

        assertTrue(source.exists())
    }

    @Test
    fun `falha de limpeza permite nova tentativa antes de liberar sessoes novas`() = runBlocking {
        val root = temporary.newFolder("pdf_workspaces")
        val old = File(root, UUID.randomUUID().toString()).apply { mkdir() }
        // Diretórios internos inesperados não são percorridos nem removidos recursivamente.
        val unexpected = File(old, "diretorio_desconhecido").apply { mkdir() }

        try {
            PdfWorkspaceCache.initialize(root)
            fail("A sessão não deveria ser liberada com limpeza incompleta.")
        } catch (_: IOException) {
            assertTrue(unexpected.exists())
        }
        assertTrue(unexpected.delete())
        PdfWorkspaceCache.initialize(root)

        assertFalse(old.exists())
    }
}
