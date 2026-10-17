package com.joseleandro.scaniqo.data.local.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DocumentFilenameTest {

    @Test
    fun `nome compartilhado conserva acentos e possui uma unica extensao`() {
        assertEquals(
            "Declaração.pdf",
            AndroidDocumentFileStore.safePdfFilename("  Declaração.PDF.pdf ")
        )
    }

    @Test
    fun `separadores e controles nao podem alterar o destino do arquivo`() {
        val name = AndroidDocumentFileStore.safePdfFilename("../pasta\\arquivo\nfinal.pdf")

        assertFalse(name.contains('/'))
        assertFalse(name.contains('\\'))
        assertFalse(name.any { it.isISOControl() })
        assertEquals("_pasta_arquivo_final.pdf", name)
    }

    @Test
    fun `nome vazio usa identificacao legivel`() {
        assertEquals("documento.pdf", AndroidDocumentFileStore.safePdfFilename(" ... "))
    }
}
