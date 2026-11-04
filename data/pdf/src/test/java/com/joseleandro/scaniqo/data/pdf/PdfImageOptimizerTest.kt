package com.joseleandro.scaniqo.data.pdf

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import com.joseleandro.scaniqo.domain.model.PdfExportQuality
import com.tom_roush.pdfbox.cos.COSArray
import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.cos.COSFloat
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDDeviceGray
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDDeviceRGB
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Random
import java.util.zip.DeflaterOutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, manifest = Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PdfImageOptimizerTest {
    @Test fun unsupportedImagesDoNotPreventOtherImagesFromShrinking() = runTest {
        PDDocument().use { document ->
            val resources = addPage(document)
            val photo = photo(document)
            val unknown = PDImageXObject(
                document, ByteArrayInputStream(byteArrayOf(1, 2, 3)),
                COSName.getPDFName("UnknownImageFilter"), 600, 600, 8, PDDeviceRGB.INSTANCE,
            )
            val jpx = PDImageXObject(
                document, ByteArrayInputStream(byteArrayOf(4, 5, 6)),
                COSName.JPX_DECODE, 600, 600, 8, PDDeviceRGB.INSTANCE,
            ).apply {
                // Construir este XObject exige JP2Android, mesmo antes de pedir seus pixels.
                cosObject.removeItem(COSName.WIDTH)
            }
            resources.put(COSName.getPDFName("Unsupported"), unknown)
            resources.put(COSName.getPDFName("Jpx"), jpx)
            resources.put(COSName.getPDFName("Photo"), photo)
            val unknownBytes = unknown.cosObject.createRawInputStream().use { it.readBytes() }
            val jpxBytes = jpx.cosObject.createRawInputStream().use { it.readBytes() }

            PdfImageOptimizer.optimize(document, PdfExportQuality.SMALL)

            val replacement = resources.getXObject(COSName.getPDFName("Photo")) as PDImageXObject
            assertTrue(replacement.cosObject.length < photo.cosObject.length)
            val objects = resources.cosObject.getCOSDictionary(COSName.XOBJECT)
            assertSame(unknown.cosObject, objects.getDictionaryObject(COSName.getPDFName("Unsupported")))
            assertSame(jpx.cosObject, objects.getDictionaryObject(COSName.getPDFName("Jpx")))
            assertArrayEquals(unknownBytes, unknown.cosObject.createRawInputStream().use { it.readBytes() })
            assertArrayEquals(jpxBytes, jpx.cosObject.createRawInputStream().use { it.readBytes() })
        }
    }

    @Test fun smallerImageKeepsVisibilityInterpolationIntentAndStructureTag() = runTest {
        PDDocument().use { document ->
            val resources = addPage(document)
            val original = photo(document).apply {
                cosObject.setItem(COSName.OC, COSDictionary().apply {
                    setItem(COSName.TYPE, COSName.getPDFName("OCG"))
                    setString(COSName.NAME, "Foto opcional")
                })
                interpolate = true
                cosObject.setName(COSName.INTENT, "RelativeColorimetric")
                cosObject.setInt(COSName.getPDFName("StructParent"), 7)
            }
            val name = COSName.getPDFName("Photo")
            resources.put(name, original)

            PdfImageOptimizer.optimize(document, PdfExportQuality.SMALL)

            val replacement = resources.getXObject(name) as PDImageXObject
            assertNotSame(original.cosObject, replacement.cosObject)
            assertTrue(replacement.cosObject.length < original.cosObject.length)
            listOf("OC", "Interpolate", "Intent", "StructParent").map(COSName::getPDFName).forEach {
                assertSame(original.cosObject.getItem(it), replacement.cosObject.getItem(it))
            }
        }
    }

    @Test fun unsupportedColorAndDecodeMappingsKeepTheirOriginalStreams() = runTest {
        PDDocument().use { document ->
            val resources = addPage(document)
            val cmyk = photo(document).apply { cosObject.setItem(COSName.COLORSPACE, COSName.DEVICECMYK) }
            val inverted = photo(document).apply {
                decode = COSArray().apply {
                    repeat(3) { add(COSFloat(1f)); add(COSFloat(0f)) }
                }
            }
            resources.put(COSName.getPDFName("CMYK"), cmyk)
            resources.put(COSName.getPDFName("Inverted"), inverted)
            assertFalse(PdfCompressionImageDecoder.canDecodeSafely(cmyk))
            assertFalse(PdfCompressionImageDecoder.canDecodeSafely(inverted))

            PdfImageOptimizer.optimize(document, PdfExportQuality.SMALL)

            assertSame(cmyk.cosObject, resources.getXObject(COSName.getPDFName("CMYK")).cosObject)
            assertSame(inverted.cosObject, resources.getXObject(COSName.getPDFName("Inverted")).cosObject)
        }
    }

    @Test fun jpegWithOuterFlateFilterUsesNativeSamplingAndKeepsBothEdges() {
        PDDocument().use { document ->
            val bitmap = Bitmap.createBitmap(801, 3203, Bitmap.Config.ARGB_8888).apply {
                eraseColor(Color.RED)
                val blue = IntArray(width * 100) { Color.BLUE }
                setPixels(blue, 0, width, 0, height - 100, width, 100)
            }
            val jpeg = try {
                JPEGFactory.createFromImage(document, bitmap, .95f)
            } finally {
                bitmap.recycle()
            }
            val encoded = ByteArrayOutputStream().apply {
                DeflaterOutputStream(this).use { output ->
                    jpeg.cosObject.createRawInputStream().use { it.copyTo(output) }
                }
            }.toByteArray()
            val filters = COSArray().apply { add(COSName.FLATE_DECODE); add(COSName.DCT_DECODE) }
            val wrapped = PDImageXObject(
                document, ByteArrayInputStream(encoded), filters, 801, 3203, 8, PDDeviceRGB.INSTANCE,
            )

            val decoded = PdfCompressionImageDecoder.decode(wrapped, 1500)
            try {
                assertTrue(decoded.height in 1601..1602)
                assertTrue(decoded.width in 400..401)
                val top = decoded.getPixel(decoded.width / 2, 5)
                assertTrue(Color.red(top) > 240 && Color.blue(top) < 15)
                val bottom = decoded.getPixel(decoded.width / 2, decoded.height - 6)
                assertTrue(Color.blue(bottom) > 240 && Color.red(bottom) < 15)
            } finally {
                decoded.recycle()
            }
        }
    }

    @Test fun grayscaleSamplesRemainOpaqueAfterCompression() = runTest {
        PDDocument().use { document ->
            val resources = addPage(document)
            val gray = PDImageXObject(
                document, ByteArrayInputStream(ByteArray(600 * 600) { 128.toByte() }),
                null, 600, 600, 8, PDDeviceGray.INSTANCE,
            )
            val name = COSName.getPDFName("Gray")
            resources.put(name, gray)

            val decoded = PdfCompressionImageDecoder.decode(gray, 1500)
            try {
                assertEquals(Color.rgb(128, 128, 128), decoded.getPixel(300, 300))
                assertFalse(decoded.hasAlpha())
            } finally {
                decoded.recycle()
            }
            PdfImageOptimizer.optimize(document, PdfExportQuality.SMALL)

            val replacement = resources.getXObject(name) as PDImageXObject
            assertTrue(replacement.cosObject.length < gray.cosObject.length)
            assertFalse(replacement.cosObject.containsKey(COSName.SMASK))
            val result = PdfCompressionImageDecoder.decode(replacement, 1500)
            try {
                val pixel = result.getPixel(300, 300)
                assertEquals(255, Color.alpha(pixel))
                assertTrue(Color.red(pixel) in 126..130)
                assertTrue(Color.green(pixel) in 126..130)
                assertTrue(Color.blue(pixel) in 126..130)
            } finally {
                result.recycle()
            }
        }
    }

    @Test fun cancellationDuringRowDecodingPropagatesAndLeavesOriginalReadable() {
        PDDocument().use { document ->
            val image = photo(document)
            val job = Job()
            var checks = 0

            assertThrows(CancellationException::class.java) {
                PdfCompressionImageDecoder.decode(image, 1500) {
                    if (++checks == 4) job.cancel()
                    job.ensureActive()
                }
            }

            assertTrue(job.isCancelled)
            val retry = PdfCompressionImageDecoder.decode(image, 1500)
            try {
                assertEquals(600, retry.width)
                assertEquals(600, retry.height)
            } finally {
                retry.recycle()
            }
        }
    }

    private fun addPage(document: PDDocument): PDResources = PDResources().also { resources ->
        document.addPage(PDPage().apply { this.resources = resources })
    }

    private fun photo(document: PDDocument): PDImageXObject {
        val bitmap = Bitmap.createBitmap(600, 600, Bitmap.Config.ARGB_8888).apply { setHasAlpha(false) }
        val random = Random(42)
        val pixels = IntArray(bitmap.width * bitmap.height) {
            Color.rgb(random.nextInt(256), random.nextInt(256), random.nextInt(256))
        }
        bitmap.setPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return try {
            LosslessFactory.createFromImage(document, bitmap)
        } finally {
            bitmap.recycle()
        }
    }
}
