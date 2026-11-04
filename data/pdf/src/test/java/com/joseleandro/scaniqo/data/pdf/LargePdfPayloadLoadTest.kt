package com.joseleandro.scaniqo.data.pdf

import android.app.Application
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDDeviceGray
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import java.io.File
import java.security.MessageDigest
import kotlin.system.measureNanoTime
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Actual 512 MiB image payload, PDFBox parser and decoded streams. No Bitmap, rendering, app import,
 * or editing is exercised. All image data and parser scratch files stay in JUnit's temporary tree.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, manifest = Config.NONE)
class LargePdfPayloadLoadTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun parsesAndVerifiesThirtyTwoUncompressedGrayImagesWithoutFileSizedArrays() {
        val file = temporary.newFile("large-image-payload.pdf")
        val generationScratch = temporary.newFolder("generation-scratch")
        val readingScratch = temporary.newFolder("reading-scratch")
        val expectedHashes = ArrayList<ByteArray>(PAGES)
        val block = ByteArray(BLOCK_BYTES)
        val heap = HeapSamples()
        var generationScratchBytes = 0L
        var saveNanos = 0L
        val generationStarted = System.nanoTime()

        PDDocument(diskMemory(generationScratch)).use { pdf ->
            repeat(PAGES) { pageIndex ->
                fillPattern(block, pageIndex)
                val page = PDPage(PDRectangle(PAGE_SIDE, PAGE_SIDE))
                pdf.addPage(page)
                // Build the low-level Image XObject directly: bitmap/image encoders would allocate
                // a full pixel array and/or compress away the load this fixture is intended to test.
                val image = PDImageXObject(pdf).apply {
                    width = IMAGE_SIDE
                    height = IMAGE_SIDE
                    bitsPerComponent = 8
                    colorSpace = PDDeviceGray.INSTANCE
                }
                val digest = MessageDigest.getInstance("SHA-256")
                image.cosObject.createRawOutputStream().use { output ->
                    repeat((IMAGE_BYTES / BLOCK_BYTES).toInt()) {
                        output.write(block)
                        digest.update(block)
                        heap.sample()
                    }
                }
                expectedHashes += digest.digest()
                assertNull(image.cosObject.getDictionaryObject(COSName.FILTER))
                PDPageContentStream(pdf, page).use { content ->
                    content.drawImage(image, 0f, 0f, PAGE_SIDE, PAGE_SIDE)
                }
            }
            generationScratchBytes = scratchBytes(generationScratch)
            assertTrue("The generated payload must be spooled to disk", generationScratchBytes >= PAYLOAD_BYTES)
            saveNanos = measureNanoTime { pdf.save(file) }
            heap.sample()
        }
        val generationNanos = System.nanoTime() - generationStarted - saveNanos
        assertTrue(file.length() >= PAYLOAD_BYTES)
        assertTrue("Closing the document must release generation scratch", generationScratch.listFiles().orEmpty().isEmpty())

        val openingStarted = System.nanoTime()
        val parsed = PDDocument.load(file, diskMemory(readingScratch))
        val openingNanos = System.nanoTime() - openingStarted
        heap.sample()
        var decodedBytes = 0L
        var readingScratchBytes = 0L
        val readingNanos = measureNanoTime {
            parsed.use { pdf ->
                assertEquals(PAGES, pdf.numberOfPages)
                val readBuffer = ByteArray(BLOCK_BYTES)
                repeat(PAGES) { pageIndex ->
                    val page = pdf.getPage(pageIndex)
                    assertEquals(PAGE_SIDE, page.mediaBox.width, 0f)
                    assertEquals(PAGE_SIDE, page.mediaBox.height, 0f)
                    val names = page.resources.xObjectNames.toList()
                    assertEquals("Exactly one image belongs to each page", 1, names.size)
                    val image = page.resources.getXObject(names.single()) as PDImageXObject
                    assertEquals(IMAGE_SIDE, image.width)
                    assertEquals(IMAGE_SIDE, image.height)
                    assertEquals(8, image.bitsPerComponent)
                    assertEquals(COSName.DEVICEGRAY, image.cosObject.getDictionaryObject(COSName.COLORSPACE))
                    assertNull(image.cosObject.getDictionaryObject(COSName.FILTER))
                    assertEquals(IMAGE_BYTES, image.cosObject.getLong(COSName.LENGTH))
                    fillPattern(block, pageIndex)
                    val digest = MessageDigest.getInstance("SHA-256")
                    var imageBytes = 0L
                    image.createInputStream().use { input ->
                        while (true) {
                            val count = input.read(readBuffer)
                            if (count < 0) break
                            if (count == 0) continue
                            assertTrue("Decoded bytes cannot exceed the image dimensions", imageBytes + count <= IMAGE_BYTES)
                            // Hashes below verify every byte; these samples independently verify the
                            // documented deterministic pattern even if reads split a fixture block.
                            for (index in intArrayOf(0, count / 2, count - 1)) {
                                val expected = block[((imageBytes + index) % BLOCK_BYTES).toInt()]
                                assertEquals(expected, readBuffer[index])
                            }
                            digest.update(readBuffer, 0, count)
                            imageBytes += count
                            heap.sample()
                        }
                    }
                    assertEquals(IMAGE_BYTES, imageBytes)
                    assertArrayEquals("Page $pageIndex image bytes changed", expectedHashes[pageIndex], digest.digest())
                    decodedBytes += imageBytes
                    readingScratchBytes = maxOf(readingScratchBytes, scratchBytes(readingScratch))
                }
            }
        }
        assertEquals(PAYLOAD_BYTES, decodedBytes)
        assertTrue("Closing the parser must release reading scratch", readingScratch.listFiles().orEmpty().isEmpty())
        println(
            "LOAD_METRIC largePdfImagePayload pages=$PAGES imageWidth=$IMAGE_SIDE imageHeight=$IMAGE_SIDE " +
                "payloadBytes=$PAYLOAD_BYTES fileBytes=${file.length()} decodedBytes=$decodedBytes " +
                "bufferBytes=$BLOCK_BYTES generationMs=${generationNanos / 1_000_000.0} " +
                "saveMs=${saveNanos / 1_000_000.0} openMs=${openingNanos / 1_000_000.0} " +
                "readAndVerifyMs=${readingNanos / 1_000_000.0} " +
                "generationScratchBytes=$generationScratchBytes readingScratchBytes=$readingScratchBytes " +
                "initialObservedHeapBytes=${heap.initial} peakObservedHeapBytes=${heap.peak}",
        )
    }

    private fun diskMemory(directory: File): MemoryUsageSetting =
        MemoryUsageSetting.setupTempFileOnly().setTempDir(directory)

    private fun scratchBytes(directory: File): Long =
        directory.listFiles().orEmpty().sumOf { it.length() }

    private fun fillPattern(buffer: ByteArray, pageIndex: Int) {
        for (index in buffer.indices) {
            buffer[index] = ((index * 31 + (index ushr 8) + pageIndex * 17) and 255).toByte()
        }
    }

    private class HeapSamples {
        private fun used(): Long = Runtime.getRuntime().let { it.totalMemory() - it.freeMemory() }
        val initial = used()
        var peak = initial
            private set
        fun sample() { peak = maxOf(peak, used()) }
    }

    private companion object {
        const val PAGES = 32
        const val IMAGE_SIDE = 4096
        const val BLOCK_BYTES = 64 * 1024
        const val PAGE_SIDE = 612f
        const val IMAGE_BYTES = IMAGE_SIDE.toLong() * IMAGE_SIDE
        const val PAYLOAD_BYTES = PAGES * IMAGE_BYTES
    }
}
