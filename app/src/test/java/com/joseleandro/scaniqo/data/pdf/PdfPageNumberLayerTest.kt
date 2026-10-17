package com.joseleandro.scaniqo.data.pdf

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.common.PDStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfPageNumberLayerTest {
    private val original = "0 0 100 100 re f % CONTEUDO_ORIGINAL\n"
    private val footer = "0 0 20 20 re f % NUMERACAO_ANTIGA\n"
    private val ocr = "q Q % OCR_PRESERVADO\n"

    @Test
    fun `remove apenas rodape marcado e preserva OCR acrescentado depois dele`() {
        PDDocument().use { source ->
            val page = createPage(source, numbered = true, withOcr = true)
            val without = requireNotNull(PdfPageNumberLayer.withoutNumber(page))
            val content = without.contents.use { it.readBytes().decodeToString() }
            assertTrue(content.contains("CONTEUDO_ORIGINAL"))
            assertTrue(content.contains("OCR_PRESERVADO"))
            assertFalse(content.contains("NUMERACAO_ANTIGA"))
            assertTrue(page.contents.use { it.readBytes().decodeToString() }.contains("NUMERACAO_ANTIGA"))
            assertTrue(PdfPageNumberLayer.hasLayer(page))
        }
    }

    @Test
    fun `numero de PDF antigo nao e removido por adivinhacao`() {
        PDDocument().use { source ->
            val page = createPage(source, numbered = false)
            PDDocument().use { output ->
                assertNull(PdfPageNumberLayer.withoutNumber(page))
                assertFalse(PdfReadyPageCopier(output).copy(page, removePageNumber = true))
                assertEquals(0, output.numberOfPages)
            }
        }
    }

    @Test
    fun `marca e recursos sobrevivem ao salvamento e copia do PDF real`() {
        val bytes = PDDocument().use { source ->
            createPage(source, numbered = true, withOcr = true)
            serialize(source)
        }
        PDDocument.load(bytes).use { source ->
            assertTrue(PdfPageNumberLayer.hasLayer(source.getPage(0)))
            PDDocument().use { output ->
                assertTrue(PdfReadyPageCopier(output).copy(source.getPage(0), removePageNumber = true))
                PDDocument.load(serialize(output)).use { loaded ->
                    val content = loaded.getPage(0).contents.use { it.readBytes().decodeToString() }
                    assertTrue(content.contains("CONTEUDO_ORIGINAL"))
                    assertTrue(content.contains("OCR_PRESERVADO"))
                    assertFalse(content.contains("NUMERACAO_ANTIGA"))
                    assertFalse(PdfPageNumberLayer.hasLayer(loaded.getPage(0)))
                    assertNotNull(loaded.getPage(0).resources)
                }
            }
        }
    }

    @Test
    fun `renumerar repetidamente nao acumula rodapes nem forms aninhados`() {
        var bytes = PDDocument().use { source ->
            createPage(source, numbered = true)
            serialize(source)
        }
        repeat(12) { version ->
            bytes = PDDocument.load(bytes).use { previous ->
                PDDocument().use { output ->
                    assertTrue(PdfReadyPageCopier(output).copy(previous.getPage(0), removePageNumber = true))
                    val page = output.getPage(0)
                    val contents = page.contentStreams.asSequence().toList()
                    page.setContents(contents + stream(output, "q Q % RODAPE_$version\n"))
                    PdfPageNumberLayer.markLastStream(page)
                    serialize(output)
                }
            }
        }
        PDDocument.load(bytes).use { final ->
            val page = final.getPage(0)
            val content = page.contents.use { it.readBytes().decodeToString() }
            assertTrue(content.contains("CONTEUDO_ORIGINAL"))
            assertTrue(content.contains("RODAPE_11"))
            assertFalse(content.contains("RODAPE_10"))
            assertFalse(content.contains("NUMERACAO_ANTIGA"))
            assertTrue(PdfPageNumberLayer.hasLayer(page))
            assertTrue(page.resources.xObjectNames.none())
        }
    }

    @Test
    fun `remover numero preserva desenho da assinatura e nao altera streams da fonte`() {
        PDDocument().use { source ->
            val page = createPage(source, numbered = true, withOcr = true)
            val signature = stream(source, "q 20 40 m 35 52 l 48 35 l 65 50 l S Q % ASSINATURA\n")
            page.setContents(page.contentStreams.asSequence().toList() + signature)
            val originalStreams = page.contentStreams.asSequence().toList()
            val originalBytes = originalStreams.map { it.createInputStream().use { input -> input.readBytes() } }

            val without = requireNotNull(PdfPageNumberLayer.withoutNumber(page))
            val remaining = without.contentStreams.asSequence().toList()
            assertEquals(3, remaining.size)
            assertSame(originalStreams[0].cosObject, remaining[0].cosObject)
            assertSame(originalStreams[2].cosObject, remaining[1].cosObject)
            assertSame(signature.cosObject, remaining[2].cosObject)
            assertFalse(PdfPageNumberLayer.hasLayer(without))

            PDDocument().use { output ->
                assertTrue(PdfReadyPageCopier(output).copy(page, removePageNumber = true))
                val copied = output.getPage(0).contents.use { it.readBytes().decodeToString() }
                assertTrue(copied.contains("CONTEUDO_ORIGINAL"))
                assertTrue(copied.contains("OCR_PRESERVADO"))
                assertTrue(copied.contains("ASSINATURA"))
                assertFalse(copied.contains("NUMERACAO_ANTIGA"))
            }
            val sourceAfter = page.contentStreams.asSequence().toList()
            assertEquals(originalStreams.size, sourceAfter.size)
            sourceAfter.forEachIndexed { index, current ->
                assertSame(originalStreams[index].cosObject, current.cosObject)
                assertArrayEquals(originalBytes[index], current.createInputStream().use { it.readBytes() })
            }
            assertTrue(PdfPageNumberLayer.hasLayer(page))
        }
    }

    @Test
    fun `marcadores duplicados exigem composicao normal sem apagar conteudo`() {
        PDDocument().use { source ->
            val page = createPage(source, numbered = true)
            page.setContents(page.contentStreams.asSequence().toList() + stream(source, "q Q % OUTRO_RODAPE\n"))
            PdfPageNumberLayer.markLastStream(page)
            val before = page.contents.use { it.readBytes() }

            assertFalse(PdfPageNumberLayer.hasLayer(page))
            assertNull(PdfPageNumberLayer.withoutNumber(page))
            PDDocument().use { output ->
                assertFalse(PdfReadyPageCopier(output).copy(page, removePageNumber = true))
                assertEquals(0, output.numberOfPages)
            }
            assertArrayEquals(before, page.contents.use { it.readBytes() })
        }
    }

    private fun createPage(document: PDDocument, numbered: Boolean, withOcr: Boolean = false): PDPage {
        val page = PDPage(PDRectangle(PdfPageGeometry.WIDTH, PdfPageGeometry.HEIGHT))
        document.addPage(page)
        page.resources = PDResources()
        page.setContents(listOf(stream(document, original), stream(document, footer)))
        if (numbered) PdfPageNumberLayer.markLastStream(page)
        if (withOcr) page.setContents(page.contentStreams.asSequence().toList() + stream(document, ocr))
        return page
    }

    private fun stream(document: PDDocument, content: String) =
        PDStream(document, ByteArrayInputStream(content.toByteArray()))

    private fun serialize(document: PDDocument): ByteArray = ByteArrayOutputStream().use { output ->
        document.save(output)
        output.toByteArray()
    }
}
