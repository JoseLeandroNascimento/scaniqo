package com.joseleandro.scaniqo.data.pdf

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.common.PDStream
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class PdfProtectionTest {

    @Test
    fun `pdf salvo usa AES 256 e exige senha correta para ser aberto`() {
        // Conteúdo vetorial real permite verificar cifragem e recuperação sem recursos Android.
        val content = "0.123456 0.654321 0.333333 rg 10 20 30 40 re f\n".toByteArray()
        val bytes = ByteArrayOutputStream().use { output ->
            PDDocument().use { document ->
                document.addPage(PDPage(PDRectangle.A4).apply {
                    setContents(PDStream(document, ByteArrayInputStream(content)))
                })
                document.addPage(PDPage(PDRectangle.A4))
                PdfProtection.apply(document, "senha-de-teste-2026")
                document.save(output)
            }
            output.toByteArray()
        }

        assertFalse(bytes.toString(Charsets.ISO_8859_1).contains(content.toString(Charsets.US_ASCII)))
        assertInvalidPassword(bytes, "")
        assertInvalidPassword(bytes, "senha-incorreta")
        PDDocument.load(bytes, "senha-de-teste-2026").use { opened ->
            assertTrue(opened.isEncrypted)
            assertEquals(256, opened.encryption.length)
            assertEquals(2, opened.numberOfPages)
            assertEquals(PDRectangle.A4.width, opened.getPage(0).mediaBox.width, 0.001f)
            assertArrayEquals(content, opened.getPage(0).contents.use { it.readBytes() })
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `senha vazia nao produz arquivo aparentemente protegido`() {
        PDDocument().use { PdfProtection.apply(it, "") }
    }

    private fun assertInvalidPassword(bytes: ByteArray, password: String) {
        try {
            PDDocument.load(bytes, password).use { fail("O PDF foi aberto sem a senha correta.") }
        } catch (_: InvalidPasswordException) {
            // A falha vem do leitor PDF real; nenhuma API Android foi simulada.
        }
    }
}
