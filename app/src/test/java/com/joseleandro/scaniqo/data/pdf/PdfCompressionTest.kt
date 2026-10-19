package com.joseleandro.scaniqo.data.pdf

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import com.joseleandro.scaniqo.domain.model.DocumentModel
import com.joseleandro.scaniqo.domain.model.PdfCompressionLevel
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.cos.COSString
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.pdmodel.interactive.digitalsignature.PDSignature
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDAcroForm
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDSignatureField
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDTextField
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File
import java.util.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, manifest = Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PdfCompressionTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun smallerCopyKeepsOriginalBytesTextVectorsGeometryAndNativeForm() = runTest {
        val source = createPhotoPdf()
        val before = source.readBytes()
        val result = repository().compress(model(source), PdfCompressionLevel.BALANCED, null)
        val scan = requireNotNull(result.scan)
        val copy = hostFile(scan.pdfUri)
        try {
            assertArrayEquals(before, source.readBytes())
            // O cadastro pode estar defasado; o resultado usa o tamanho do arquivo lido.
            assertEquals(source.length(), result.originalBytes)
            assertEquals(copy.length(), result.resultBytes)
            assertTrue(copy.length() < source.length())
            PDDocument.load(copy).use { pdf ->
                assertEquals(1, pdf.numberOfPages)
                assertEquals(90, pdf.getPage(0).rotation)
                assertEquals(420f, pdf.getPage(0).mediaBox.width, .01f)
                val text = PDFTextStripper().getText(pdf)
                assertEquals("Textopesquisavel", text.filterNot(Char::isWhitespace))
                PDDocument.load(source).use { original -> assertEquals(PDFTextStripper().getText(original), text) }
                assertEquals("Ana", (pdf.documentCatalog.acroForm.getField("name") as PDTextField).value)
                val content = pdf.getPage(0).contents.use { it.readBytes().toString(Charsets.ISO_8859_1) }
                assertTrue(content.contains(" re"))
                val resources = pdf.getPage(0).resources
                assertTrue(resources.xObjectNames.any { resources.getXObject(it) is PDImageXObject })
            }
        } finally { copy.delete(); copy.parentFile?.delete() }
    }

    @Test fun strongCompressionOfTallJpegKeepsBothImageEdgesAndSearchableContent() = runTest {
        val source = createPhotoPdf(jpeg = true, width = 1601, height = 3203)
        val before = source.readBytes()
        val result = repository().compress(model(source), PdfCompressionLevel.STRONG, null)
        val copy = hostFile(requireNotNull(result.scan).pdfUri)
        try {
            assertArrayEquals(before, source.readBytes())
            assertTrue(result.resultBytes < result.originalBytes)
            PDDocument.load(copy).use { pdf ->
                val resources = pdf.getPage(0).resources
                val image = resources.xObjectNames.map(resources::getXObject).filterIsInstance<PDImageXObject>().single()
                assertEquals(1500, image.height)
                assertTrue(image.width in 749..751)
                val bitmap = image.image
                try {
                    val top = bitmap.getPixel(bitmap.width / 2, 4)
                    val bottom = bitmap.getPixel(bitmap.width / 2, bitmap.height - 5)
                    assertTrue("A borda superior vermelha deve continuar na imagem.", Color.red(top) > 200 && Color.blue(top) < 40)
                    assertTrue("A borda inferior azul não pode ser recortada.", Color.blue(bottom) > 200 && Color.red(bottom) < 40)
                } finally { bitmap.recycle() }
                PDDocument.load(source).use { original ->
                    assertEquals(PDFTextStripper().getText(original), PDFTextStripper().getText(pdf))
                }
                assertEquals("Ana", (pdf.documentCatalog.acroForm.getField("name") as PDTextField).value)
            }
        } finally { copy.delete(); copy.parentFile?.delete() }
    }

    @Test fun protectedCompressionKeepsPasswordAndProtectionKey() = runTest {
        val source = createPhotoPdf("secret")
        val before = source.readBytes()
        val result = repository().compress(model(source).copy(protectionKeyId = "original-key"), PdfCompressionLevel.STRONG, "secret")
        val scan = requireNotNull(result.scan)
        val copy = hostFile(scan.pdfUri)
        try {
            assertEquals("original-key", scan.protectionKeyId)
            assertArrayEquals(before, source.readBytes())
            assertThrows(InvalidPasswordException::class.java) { PDDocument.load(copy).close() }
            PDDocument.load(copy, "secret").use { pdf ->
                assertTrue(pdf.isEncrypted)
                val text = PDFTextStripper().getText(pdf)
                assertEquals("Textopesquisavel", text.filterNot(Char::isWhitespace))
                PDDocument.load(source, "secret").use { original -> assertEquals(PDFTextStripper().getText(original), text) }
            }
        } finally { copy.delete(); copy.parentFile?.delete() }
    }

    @Test fun repeatedCompressionStopsWhenNoFurtherSavingsAndLeavesNoDuplicate() = runTest {
        val source = temporary.newFile("simple.pdf")
        PDDocument().use { pdf -> pdf.addPage(PDPage()); pdf.save(source) }
        val original = source.readBytes()
        var current = source
        val copies = mutableListOf<File>()
        try {
            var finished = false
            repeat(4) {
                if (!finished) {
                    val filesBefore = preparedFiles()
                    val result = repository().compress(model(current), PdfCompressionLevel.STRONG, null)
                    if (result.scan == null) {
                        assertEquals(result.originalBytes, result.resultBytes)
                        assertEquals(filesBefore, preparedFiles())
                        finished = true
                    } else {
                        current = hostFile(result.scan.pdfUri)
                        copies += current
                    }
                }
            }
            assertTrue("Um PDF sem imagens deve estabilizar sem produzir cópias iguais.", finished)
            assertArrayEquals(original, source.readBytes())
        } finally { copies.forEach { it.delete(); it.parentFile?.delete() } }
    }

    @Test fun digitalSignaturePreventsCompressionBeforeOutputIsWritten() = runTest {
        PDDocument().use { pdf ->
            pdf.addPage(PDPage())
            val form = PDAcroForm(pdf)
            pdf.documentCatalog.acroForm = form
            form.fields = listOf(PDSignatureField(form).apply {
                partialName = "signature"
                value = PDSignature()
            })
            val target = temporary.newFile("signed-copy.pdf")
            val failure = runCatching {
                PdfCompressionWriter.write(pdf, target, PdfCompressionLevel.BALANCED, null)
            }.exceptionOrNull()
            assertTrue(failure is IllegalArgumentException)
            assertEquals(0L, target.length())
        }
    }

    @Test fun incorrectPasswordDoesNotPrepareOutputOrModifyOriginal() = runTest {
        val source = createPhotoPdf("secret")
        val before = source.readBytes()
        val filesBefore = preparedFiles()
        val failure = runCatching {
            repository().compress(model(source).copy(protectionKeyId = "key"), PdfCompressionLevel.STRONG, "wrong")
        }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
        assertTrue(failure?.message.orEmpty().contains("Senha incorreta"))
        assertEquals(filesBefore, preparedFiles())
        assertArrayEquals(before, source.readBytes())
    }

    private fun repository() = AndroidPdfUtilityRepository(RuntimeEnvironment.getApplication(), Dispatchers.Unconfined)

    private fun model(file: File): DocumentModel {
        val uri = Uri.parse("content://compression-fixtures/${file.name}")
        shadowOf(RuntimeEnvironment.getApplication().contentResolver)
            .registerInputStreamSupplier(uri) { file.inputStream() }
        return DocumentModel(7, "Original", uri.toString(), 1, 1, 1)
    }

    /** Uri.fromFile usa a semântica Android; uma letra de unidade Windows vira authority no host. */
    private fun hostFile(value: String): File {
        val uri = Uri.parse(value)
        return File(requireNotNull(uri.path?.takeIf(String::isNotBlank) ?: uri.authority))
    }

    private fun preparedFiles(): Set<String> =
        File(RuntimeEnvironment.getApplication().filesDir, "documents").walkTopDown()
            .filter(File::isFile).map(File::getAbsolutePath).toSet()

    private fun createPhotoPdf(password: String? = null, jpeg: Boolean = false, width: Int = 640, height: Int = 640): File {
        val file = temporary.newFile()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { setHasAlpha(false) }
        val random = Random(42)
        val pixels = IntArray(width * height) { index ->
            when {
                index / width < 64 -> Color.RED
                index / width >= height - 64 -> Color.BLUE
                else -> Color.rgb(random.nextInt(256), random.nextInt(256), random.nextInt(256))
            }
        }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        try {
            PDDocument().use { pdf ->
                val page = PDPage(PDRectangle(420f, 595f)).apply { rotation = 90 }
                pdf.addPage(page)
                PDPageContentStream(pdf, page).use { stream ->
                    val photo = if (jpeg) JPEGFactory.createFromImage(pdf, bitmap, .99f)
                        else LosslessFactory.createFromImage(pdf, bitmap)
                    assertFalse("A foto opaca da fixture não deve criar máscara de transparência.", photo.cosObject.containsKey(COSName.SMASK))
                    stream.drawImage(photo, 30f, 30f, 350f, 350f)
                    stream.beginText()
                    stream.setFont(PDType1Font.HELVETICA, 14f)
                    stream.newLineAtOffset(30f, 550f)
                    stream.showText("Texto pesquisavel")
                    stream.endText()
                    stream.addRect(20f, 400f, 80f, 30f)
                    stream.stroke()
                }
                val form = PDAcroForm(pdf).apply {
                    defaultResources = PDResources().apply { put(COSName.getPDFName("Helv"), PDType1Font.HELVETICA) }
                    defaultAppearance = "/Helv 12 Tf 0 g"
                }
                pdf.documentCatalog.acroForm = form
                form.fields = listOf(PDTextField(form).apply {
                    partialName = "name"
                    cosObject.setItem(COSName.V, COSString("Ana"))
                })
                if (password != null) PdfProtection.apply(pdf, password)
                pdf.save(file)
            }
        } finally { bitmap.recycle() }
        return file
    }
}
