package com.joseleandro.scaniqo.data.pdf

import android.app.Application
import android.net.Uri
import com.joseleandro.scaniqo.domain.model.DocumentModel
import com.joseleandro.scaniqo.domain.model.PdfCompressionLevel
import com.tom_roush.pdfbox.cos.COSArray
import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.cos.COSStream
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.common.PDStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.form.PDFormXObject
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FilterInputStream
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, manifest = Config.NONE)
class PdfStructuralCompressionTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun importedTextAndVectorsCompressAtBothLevelsWithoutRasterizing() = runTest {
        val source = createTextAndVectorPdf()
        val originalBytes = source.readBytes()
        for (level in PdfCompressionLevel.entries) {
            val result = repository().compress(model(source), level, null)
            val copy = hostFile(requireNotNull(result.scan).pdfUri)
            try {
                assertArrayEquals(originalBytes, source.readBytes())
                assertEquals(source.length(), result.originalBytes)
                assertEquals(copy.length(), result.resultBytes)
                assertTrue("Conteúdo vetorial sem filtro deve ter redução expressiva.", copy.length() < source.length() / 2)
                assertSameTextAndPages(source, copy)
                PDDocument.load(copy).use { pdf ->
                    assertEquals("Relatorio importado", pdf.documentInformation.title)
                    assertEquals("Arquivo do dispositivo", pdf.documentInformation.author)
                    for (page in pdf.pages) {
                        assertEquals(90, page.rotation)
                        assertEquals(420f, page.mediaBox.width, 0f)
                        assertEquals(595f, page.mediaBox.height, 0f)
                        assertEquals(400f, page.cropBox.width, 0f)
                        assertEquals(570f, page.cropBox.height, 0f)
                        assertFalse("Texto e vetores não devem virar imagens.", page.resources.xObjectNames.iterator().hasNext())
                    }
                }
            } finally { discard(copy) }
        }
    }

    @Test fun existingFlateStreamsAreRecompressedWithoutChangingDecodedContent() = runTest {
        // Alguns exportadores aplicam o filtro Flate com nível zero, mantendo o PDF grande.
        val source = createTextAndVectorPdf(uncompressedFlate = true)
        val originalBytes = source.readBytes()
        val result = repository().compress(model(source), PdfCompressionLevel.BALANCED, null)
        val copy = hostFile(requireNotNull(result.scan).pdfUri)
        try {
            assertTrue(copy.length() < source.length() / 2)
            assertArrayEquals(originalBytes, source.readBytes())
            assertSameTextAndPages(source, copy)
        } finally { discard(copy) }
    }

    @Test fun nestedFormContentCompressesAndRemainsSearchable() = runTest {
        val source = temporary.newFile("nested-forms.pdf")
        val innerContent = (textContent(1) + vectorContent()).toByteArray(Charsets.ISO_8859_1)
        PDDocument().use { pdf ->
            val inner = PDFormXObject(pdf).apply {
                bBox = PDRectangle(420f, 595f)
                resources = fontResources()
            }
            inner.cosObject.createOutputStream().use { it.write(innerContent) }
            val outer = PDFormXObject(pdf).apply {
                bBox = PDRectangle(420f, 595f)
                resources = PDResources().apply { put(COSName.getPDFName("Inner"), inner) }
            }
            outer.cosObject.createOutputStream().use { it.write("/Inner Do\n".toByteArray()) }
            val page = PDPage(PDRectangle(420f, 595f)).apply {
                resources = PDResources().apply { put(COSName.getPDFName("Outer"), outer) }
                setContents(PDStream(pdf, ByteArrayInputStream("/Outer Do\n".toByteArray())))
            }
            pdf.addPage(page)
            pdf.save(source)
        }
        val originalBytes = source.readBytes()
        val result = repository().compress(model(source), PdfCompressionLevel.STRONG, null)
        val copy = hostFile(requireNotNull(result.scan).pdfUri)
        try {
            assertTrue(copy.length() < source.length() / 2)
            assertArrayEquals(originalBytes, source.readBytes())
            assertSameTextAndPages(source, copy)
            PDDocument.load(copy).use { pdf ->
                val outer = pdf.getPage(0).resources.getXObject(COSName.getPDFName("Outer")) as PDFormXObject
                val inner = outer.resources.getXObject(COSName.getPDFName("Inner")) as PDFormXObject
                assertArrayEquals(innerContent, inner.contents.use { it.readBytes() })
                assertEquals(420f, inner.bBox.width, 0f)
                assertEquals(595f, inner.bBox.height, 0f)
            }
        } finally { discard(copy) }
    }

    @Test fun flatePredictorParametersAndDecodedBytesSurviveRecompression() = runTest {
        val source = temporary.newFile("predictor.pdf")
        val columns = 256
        val rows = 64
        val expected = ByteArray(columns * rows) { (it / columns + it % columns).toByte() }
        // PNG Up armazena a diferença em relação à linha anterior após o byte de filtro.
        val predicted = ByteArray((columns + 1) * rows)
        repeat(rows) { row ->
            predicted[row * (columns + 1)] = 2
            repeat(columns) { column ->
                val index = row * columns + column
                val previous = if (row == 0) 0 else expected[index - columns].toInt() and 0xff
                predicted[row * (columns + 1) + column + 1] =
                    ((expected[index].toInt() and 0xff) - previous).toByte()
            }
        }
        val streamName = COSName.getPDFName("PredictorFixture")
        PDDocument().use { pdf ->
            pdf.addPage(PDPage())
            val stream = PDStream(pdf).cosObject
            stream.setItem(COSName.FILTER, COSArray().apply { add(COSName.FLATE_DECODE) })
            val parameters = COSDictionary().apply {
                setInt(COSName.PREDICTOR, 15)
                setInt(COSName.COLORS, 1)
                setInt(COSName.BITS_PER_COMPONENT, 8)
                setInt(COSName.COLUMNS, columns)
            }
            stream.setItem(COSName.DECODE_PARMS, COSArray().apply { add(parameters) })
            val deflater = Deflater(Deflater.NO_COMPRESSION)
            try {
                stream.createRawOutputStream().use { output ->
                    DeflaterOutputStream(output, deflater).use { it.write(predicted) }
                }
            } finally { deflater.end() }
            // Um stream referenciado pelo catálogo também precisa ser encontrado.
            pdf.documentCatalog.cosObject.setItem(streamName, stream)
            assertArrayEquals(expected, stream.createInputStream().use { it.readBytes() })
            pdf.save(source)
        }
        val originalBytes = source.readBytes()
        val result = repository().compress(model(source), PdfCompressionLevel.BALANCED, null)
        val copy = hostFile(requireNotNull(result.scan).pdfUri)
        try {
            assertTrue(copy.length() < source.length() / 2)
            assertArrayEquals(originalBytes, source.readBytes())
            PDDocument.load(copy).use { pdf ->
                val stream = pdf.documentCatalog.cosObject.getDictionaryObject(streamName) as COSStream
                assertArrayEquals(expected, stream.createInputStream().use { it.readBytes() })
                val parameters = stream.getDictionaryObject(COSName.DECODE_PARMS) as COSDictionary
                assertEquals(15, parameters.getInt(COSName.PREDICTOR))
                assertEquals(columns, parameters.getInt(COSName.COLUMNS))
                assertEquals(1, parameters.getInt(COSName.COLORS))
                assertEquals(8, parameters.getInt(COSName.BITS_PER_COMPONENT))
            }
        } finally { discard(copy) }
    }

    @Test fun interruptedImportCleansPartialSnapshotAndPreparedOutput() = runTest {
        val source = createTextAndVectorPdf()
        val originalBytes = source.readBytes()
        val document = model(source)
        val filesBefore = preparedFiles()
        val snapshotsBefore = sourceSnapshots()
        shadowOf(RuntimeEnvironment.getApplication().contentResolver)
            .registerInputStreamSupplier(Uri.parse(document.pdfUri)) {
                object : FilterInputStream(source.inputStream()) {
                    private var readOnce = false

                    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                        if (readOnce) throw CancellationException("Importação cancelada")
                        readOnce = true
                        return super.read(buffer, offset, length)
                    }
                }
            }
        val failure = runCatching {
            repository().compress(document, PdfCompressionLevel.BALANCED, null)
        }.exceptionOrNull()
        assertTrue(failure is CancellationException)
        assertEquals(filesBefore, preparedFiles())
        assertEquals(snapshotsBefore, sourceSnapshots())
        assertArrayEquals(originalBytes, source.readBytes())
    }

    @Test fun repeatedPageContentSharesOneStreamAfterCompression() = runTest {
        val source = temporary.newFile("repeated-content.pdf")
        val content = textContent(1) + vectorContent()
        PDDocument().use { pdf ->
            repeat(3) {
                val page = PDPage(PDRectangle(420f, 595f)).apply {
                    resources = fontResources()
                    setContents(contentStream(pdf, content, uncompressedFlate = false))
                }
                pdf.addPage(page)
            }
            pdf.save(source)
        }
        val originalBytes = source.readBytes()
        val result = repository().compress(model(source), PdfCompressionLevel.BALANCED, null)
        val copy = hostFile(requireNotNull(result.scan).pdfUri)
        try {
            assertTrue(copy.length() < source.length() / 2)
            assertArrayEquals(originalBytes, source.readBytes())
            assertSameTextAndPages(source, copy)
            PDDocument.load(copy).use { pdf ->
                val shared = pdf.getPage(0).cosObject.getDictionaryObject(COSName.CONTENTS)
                for (index in 1 until pdf.numberOfPages) {
                    assertSame(shared, pdf.getPage(index).cosObject.getDictionaryObject(COSName.CONTENTS))
                }
            }
        } finally { discard(copy) }
    }

    @Test fun optimizedTextPdfStabilizesWithoutReturningLargerOrDuplicateFiles() = runTest {
        val source = createTextAndVectorPdf()
        val originalBytes = source.readBytes()
        val copies = mutableListOf<File>()
        var current = source
        var stabilized = false
        try {
            repeat(4) {
                if (!stabilized) {
                    val filesBefore = preparedFiles()
                    val result = repository().compress(model(current), PdfCompressionLevel.BALANCED, null)
                    assertTrue(result.resultBytes <= result.originalBytes)
                    if (result.scan == null) {
                        assertEquals(result.originalBytes, result.resultBytes)
                        assertEquals(filesBefore, preparedFiles())
                        stabilized = true
                    } else {
                        val copy = hostFile(result.scan.pdfUri)
                        copies += copy
                        assertTrue(copy.length() < current.length())
                        assertSameTextAndPages(current, copy)
                        current = copy
                    }
                }
            }
            assertFalse("A primeira compressão precisa reduzir o PDF importado.", copies.isEmpty())
            assertTrue("Não deve criar cópias indefinidamente sem ganho.", stabilized)
            assertArrayEquals(originalBytes, source.readBytes())
        } finally { copies.forEach(::discard) }
    }

    private fun createTextAndVectorPdf(uncompressedFlate: Boolean = false): File {
        val source = temporary.newFile()
        PDDocument().use { pdf ->
            pdf.documentInformation.title = "Relatorio importado"
            pdf.documentInformation.author = "Arquivo do dispositivo"
            repeat(3) { index ->
                val page = PDPage(PDRectangle(420f, 595f)).apply {
                    rotation = 90
                    cropBox = PDRectangle(10f, 10f, 400f, 570f)
                    resources = fontResources()
                }
                // Uma página pode ter uma lista de streams, e não apenas um /Contents.
                page.setContents(listOf(
                    contentStream(pdf, textContent(index + 1), uncompressedFlate),
                    contentStream(pdf, vectorContent(), uncompressedFlate),
                ))
                pdf.addPage(page)
            }
            pdf.save(source)
        }
        return source
    }

    private fun contentStream(pdf: PDDocument, content: String, uncompressedFlate: Boolean): PDStream {
        val bytes = content.toByteArray(Charsets.ISO_8859_1)
        if (!uncompressedFlate) return PDStream(pdf, ByteArrayInputStream(bytes))
        return PDStream(pdf).apply {
            cosObject.setItem(COSName.FILTER, COSName.FLATE_DECODE)
            val deflater = Deflater(Deflater.NO_COMPRESSION)
            try {
                cosObject.createRawOutputStream().use { output ->
                    DeflaterOutputStream(output, deflater).use { it.write(bytes) }
                }
            } finally { deflater.end() }
        }
    }

    private fun textContent(pageNumber: Int): String = buildString {
        append("BT /F1 12 Tf 30 550 Td (Pagina $pageNumber - texto pesquisavel) Tj ET\n")
        repeat(20) { line ->
            append("BT /F1 10 Tf 30 ${520 - line * 15} Td (Relatorio de texto e graficos vetoriais) Tj ET\n")
        }
    }

    private fun vectorContent(): String = buildString {
        append("q 0.2 0.4 0.6 RG 0.25 w\n")
        repeat(2_000) { rectangle ->
            append("${20 + rectangle % 30 * 10} ${20 + rectangle / 30 * 3} 8 2 re S\n")
        }
        append("Q\n")
    }

    private fun fontResources() = PDResources().apply {
        put(COSName.getPDFName("F1"), PDType1Font.HELVETICA)
    }

    private fun assertSameTextAndPages(source: File, copy: File) {
        PDDocument.load(source).use { original ->
            PDDocument.load(copy).use { compressed ->
                assertEquals(original.numberOfPages, compressed.numberOfPages)
                val expectedText = PDFTextStripper().getText(original)
                assertTrue("A fixture deve conter texto pesquisável.", expectedText.filterNot(Char::isWhitespace).contains("textopesquisavel"))
                assertEquals(expectedText, PDFTextStripper().getText(compressed))
                for (index in 0 until original.numberOfPages) {
                    assertArrayEquals(
                        original.getPage(index).contents.use { it.readBytes() },
                        compressed.getPage(index).contents.use { it.readBytes() },
                    )
                }
            }
        }
    }

    private fun repository() = AndroidPdfUtilityRepository(RuntimeEnvironment.getApplication(), Dispatchers.Unconfined)

    private fun model(file: File): DocumentModel {
        val uri = Uri.parse("content://structural-compression-fixtures/${file.name}")
        shadowOf(RuntimeEnvironment.getApplication().contentResolver)
            .registerInputStreamSupplier(uri) { file.inputStream() }
        return DocumentModel(7, "Importado", uri.toString(), 3, 1, 1)
    }

    /** Uri.fromFile usa a semântica Android; uma letra de unidade Windows vira authority no host. */
    private fun hostFile(value: String): File {
        val uri = Uri.parse(value)
        return File(requireNotNull(uri.path?.takeIf(String::isNotBlank) ?: uri.authority))
    }

    private fun preparedFiles(): Set<String> =
        File(RuntimeEnvironment.getApplication().filesDir, "documents").walkTopDown()
            .filter(File::isFile).map(File::getAbsolutePath).toSet()

    private fun sourceSnapshots(): Set<String> =
        RuntimeEnvironment.getApplication().cacheDir.listFiles().orEmpty()
            .filter { it.name.startsWith("utility-source-") }.map(File::getAbsolutePath).toSet()

    private fun discard(file: File) {
        file.delete()
        file.parentFile?.delete()
    }
}
