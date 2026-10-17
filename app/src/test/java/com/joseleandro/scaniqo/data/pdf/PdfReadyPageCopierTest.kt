package com.joseleandro.scaniqo.data.pdf

import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.common.PDStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfReadyPageCopierTest {
    private val content = "0.2 0.4 0.6 rg 20 30 100 200 re f\n".toByteArray()

    @Test
    fun `copias repetidas preservam conteudo sem adicionar forms aninhados`() {
        var bytes = original()
        repeat(12) {
            bytes = PDDocument.load(bytes).use { previous ->
                PDDocument().use { output ->
                    assertTrue(PdfReadyPageCopier(output).copy(previous.getPage(0)))
                    ByteArrayOutputStream().use { stream ->
                        output.save(stream)
                        stream.toByteArray()
                    }
                }
            }
        }
        PDDocument.load(bytes).use { final ->
            assertEquals(1, final.numberOfPages)
            assertArrayEquals(content, final.getPage(0).contents.use { it.readBytes() })
            assertTrue(final.getPage(0).resources.xObjectNames.none())
        }
    }

    @Test
    fun `recursos pertencem a copia e persistem mesmo apos fechar fonte`() {
        PDDocument().use { output ->
            PDDocument.load(original()).use { source ->
                source.getPage(0).resources.cosObject.setString(COSName.getPDFName("Teste"), "recurso")
                assertTrue(PdfReadyPageCopier(output).copy(source.getPage(0)))
                source.getPage(0).resources.cosObject.setString(COSName.getPDFName("Teste"), "alterado")
            }
            val bytes = ByteArrayOutputStream().use { stream ->
                output.save(stream)
                stream.toByteArray()
            }
            PDDocument.load(bytes).use { loaded ->
                assertEquals("recurso", loaded.getPage(0).resources.cosObject.getString(COSName.getPDFName("Teste")))
            }
        }
    }

    @Test
    fun `pagina com proporcao ou rotacao diferente exige composicao normal`() {
        PDDocument().use { output ->
            val copier = PdfReadyPageCopier(output)
            assertFalse(copier.copy(PDPage(PDRectangle.LETTER)))
            assertFalse(copier.copy(PDPage(PDRectangle.A4).apply { rotation = 90 }))
            assertEquals(0, output.numberOfPages)
        }
    }

    private fun original(): ByteArray = PDDocument().use { document ->
        document.addPage(PDPage(PDRectangle(PdfPageGeometry.WIDTH, PdfPageGeometry.HEIGHT)).apply {
            resources = PDResources()
            setContents(PDStream(document, ByteArrayInputStream(content)))
        })
        ByteArrayOutputStream().use { stream ->
            document.save(stream)
            stream.toByteArray()
        }
    }
}
