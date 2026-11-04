package com.joseleandro.scaniqo.data.pdf

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.net.Uri
import com.joseleandro.scaniqo.data.local.document.AndroidDocumentFileStore
import com.joseleandro.scaniqo.data.repository.AndroidSignatureRepository
import com.joseleandro.scaniqo.domain.model.DocumentModel
import com.joseleandro.scaniqo.domain.model.PdfExportQuality
import com.joseleandro.scaniqo.domain.model.PdfPageSpec
import com.joseleandro.scaniqo.domain.model.PdfSignatureCrop
import com.joseleandro.scaniqo.domain.model.PdfSignaturePlacement
import com.joseleandro.scaniqo.domain.model.PdfWorkspace
import com.joseleandro.scaniqo.domain.model.ScannedDocument
import com.joseleandro.scaniqo.domain.model.SignatureModel
import com.joseleandro.scaniqo.domain.repository.DocumentRepository
import com.tom_roush.pdfbox.contentstream.operator.Operator
import com.tom_roush.pdfbox.cos.COSBase
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.cos.COSNumber
import com.tom_roush.pdfbox.pdfparser.PDFStreamParser
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
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
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import kotlin.math.hypot

/**
 * Exercises actual PNG encoding/decoding and PDF soft-mask streams. No thumbnail or
 * screenshot stand-in is used to infer transparency; live camera gestures remain device tests.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, manifest = Config.NONE,
    shadows = [PdfSignatureTransparencyTest.HostFileUri::class])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PdfSignatureTransparencyTest {
    @get:Rule val temporary = TemporaryFolder()
    private val context get() = RuntimeEnvironment.getApplication()
    private val directories = mutableListOf<File>()
    private val signatureIds = mutableListOf<String>()

    @After fun cleanUp() = runTest {
        val repository = AndroidSignatureRepository(context)
        signatureIds.forEach { repository.delete(it) }
        directories.forEach(File::deleteRecursively)
    }

    @Test fun importedJpegKeepsActualAlphaThroughLibraryReloadAndPdfMoveResizeRotate() = runTest {
        val stored = importOpaqueImage()
        val repository = AndroidSignatureRepository(context)
        val reopened = repository.observeSignatures().first().single { it.id == stored.id }
        assertEquals(stored, reopened)
        val png = localFile(reopened.imageUri)
        val originalPng = png.readBytes()
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(png.path, bounds)
        assertEquals("image/png", bounds.outMimeType)
        val expected = requireNotNull(BitmapFactory.decodeFile(png.path))
        try {
            assertTrue("PNG must advertise its alpha channel", expected.hasAlpha())
            val alpha = alphaBytes(expected)
            assertTrue("Background must contain fully transparent pixels", alpha.any { it.toInt() == 0 })
            assertTrue("Signature ink must survive processing", alpha.any { (it.toInt() and 255) > 180 })
            assertTrue("Antialiased edges must retain fractional coverage",
                alpha.any { (it.toInt() and 255) in 1..254 })

            val files = PdfWorkspaceFiles(context)
            val workspace = workspace(files)
            val editor = editor()
            val copied = editor.copySignature(workspace, reopened.imageUri)
            assertArrayEquals("Copying into the document must preserve the complete PNG",
                png.readBytes(), localFile(copied).readBytes())
            val original = PdfSignaturePlacement("signature", copied, .08f, .15f, .3f, .13f)
            val variants = listOf(
                original,
                original.copy(x = .55f, y = .62f),
                original.copy(width = .6f, height = .26f),
                original.copy(x = .25f, y = .35f, rotationDegrees = 37f),
            )
            val composer = PdfBoxComposer(files)
            val traces = variants.mapIndexed { index, placement ->
                val target = File(files.directory(workspace), "signature-export-$index.pdf")
                composer.compose(
                    workspace.copy(pages = workspace.pages.map { it.copy(signatures = listOf(placement)) }),
                    target, forExport = true,
                )
                verifyPdf(target, expected, placement)
            }
            assertNotEquals(traces[0].e, traces[1].e)
            assertNotEquals(traces[0].f, traces[1].f)
            assertEquals(2f, hypot(traces[2].a, traces[2].b) / hypot(traces[0].a, traces[0].b), .001f)
            assertTrue("Rotation must change the image matrix, without rasterizing the ink", traces[3].b != 0f)
            assertArrayEquals("Placement edits must leave the stored PNG untouched", originalPng, png.readBytes())
        } finally {
            expected.recycle()
        }
    }

    @Test fun documentCropAndSmallExportPreserveFractionalAlphaAndOriginalInkResolution() = runTest {
        val stored = importOpaqueImage()
        val files = PdfWorkspaceFiles(context)
        val workspace = workspace(files)
        val editor = editor()
        val copied = editor.copySignature(workspace, stored.imageUri)
        val source = requireNotNull(BitmapFactory.decodeFile(localFile(copied).path))
        try {
            val crop = PdfSignatureCrop(.03f, .03f, .97f, .97f)
            val area = crop.pixels(source.width, source.height)
            val cropped = editor.cropSignature(workspace, copied, crop)
            val expected = Bitmap.createBitmap(source, area.x, area.y, area.width, area.height)
            val actual = requireNotNull(BitmapFactory.decodeFile(localFile(cropped.imageUri).path))
            try {
                assertEquals(expected.width.toFloat() / expected.height, cropped.aspectRatio, .0001f)
                assertTrue(actual.hasAlpha())
                assertArrayEquals("Cropping must preserve every retained alpha value",
                    alphaBytes(expected), alphaBytes(actual))
                assertTrue(alphaBytes(actual).any { it.toInt() == 0 })
                assertTrue(alphaBytes(actual).any { (it.toInt() and 255) in 1..254 })
                val placement = PdfSignaturePlacement("cropped", cropped.imageUri, .2f, .4f, .5f, .18f)
                val target = File(files.directory(workspace), "small-signature.pdf")
                PdfBoxComposer(files).compose(
                    workspace.copy(
                        exportQuality = PdfExportQuality.SMALL,
                        pages = workspace.pages.map { it.copy(signatures = listOf(placement)) },
                    ),
                    target, forExport = true,
                )
                verifyPdf(target, actual, placement)
            } finally {
                actual.recycle()
                if (expected !== source) expected.recycle()
            }
            assertTrue("Recropping must keep the source available for undo", localFile(copied).isFile)
        } finally {
            source.recycle()
        }
    }

    private suspend fun importOpaqueImage(): SignatureModel {
        val source = temporary.newFile("signature-${System.nanoTime()}.jpg")
        val bitmap = Bitmap.createBitmap(480, 180, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(Color.WHITE)
            bitmap.setHasAlpha(false)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(24, 43, 108)
                style = Paint.Style.STROKE
                strokeWidth = 2.6f
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            val path = Path().apply {
                moveTo(42f, 112f)
                cubicTo(120f, 22f, 85f, 154f, 161f, 81f)
                cubicTo(227f, 24f, 190f, 148f, 264f, 87f)
                cubicTo(315f, 28f, 283f, 141f, 354f, 91f)
                lineTo(430f, 104f)
            }
            Canvas(bitmap).drawPath(path, paint)
            source.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 98, it)) }
        } finally {
            bitmap.recycle()
        }
        val uri = Uri.parse("content://signature-test/${source.name}")
        shadowOf(context.contentResolver).registerInputStreamSupplier(uri) { source.inputStream() }
        val repository = AndroidSignatureRepository(context)
        val staged = repository.stageImage(uri.toString())
        try {
            val processed = repository.processImage(staged.imageUri, PdfSignatureCrop(0f, 0f, 1f, 1f))
            try {
                return repository.saveProcessedImage("Assinatura", processed.imageUri).also {
                    signatureIds += it.id
                }
            } finally {
                repository.discardStagedImage(processed.imageUri)
            }
        } finally {
            repository.discardStagedImage(staged.imageUri)
        }
    }

    private suspend fun workspace(files: PdfWorkspaceFiles): PdfWorkspace {
        files.initialize()
        val directory = files.createWorkspace().also { directories += it }
        val source = File(directory, "source.pdf")
        PDDocument().use { document ->
            val page = PDPage(PDRectangle(595f, 842f))
            document.addPage(page)
            PDPageContentStream(document, page).use { stream ->
                stream.setNonStrokingColor(.2f, .25f, .3f)
                stream.addRect(0f, 0f, 595f, 842f)
                stream.fill()
            }
            document.save(source)
        }
        return PdfWorkspace(1L, directory.name, files.uri(directory),
            listOf(PdfPageSpec("page", files.uri(source), 0)))
    }

    private fun verifyPdf(file: File, expected: Bitmap, placement: PdfSignaturePlacement): PdfContentTransform =
        PDDocument.load(file).use { document ->
            val page = document.getPage(0)
            val image = page.resources.xObjectNames.map { page.resources.getXObject(it) }
                .filterIsInstance<PDImageXObject>().single()
            assertEquals(expected.width, image.width)
            assertEquals(expected.height, image.height)
            assertNotNull("PDF must contain a real soft mask, including after export", image.softMask)
            val mask = requireNotNull(image.softMask)
            assertEquals(8, mask.bitsPerComponent)
            assertEquals(expected.width, mask.width)
            assertEquals(expected.height, mask.height)
            assertArrayEquals("PDF soft mask must preserve every PNG alpha sample",
                alphaBytes(expected), mask.createInputStream().use { it.readBytes() })
            val decoded = image.image
            try {
                assertTrue(decoded.hasAlpha())
                assertArrayEquals("Reloading the embedded PDF image must restore its real alpha",
                    alphaBytes(expected), alphaBytes(decoded))
            } finally {
                decoded.recycle()
            }
            imageTransform(page).also { transform ->
                val center = transform.point(.5f, .5f)
                assertEquals((placement.x + placement.width / 2f) * 595f, center.first, .01f)
                assertEquals((1f - placement.y - placement.height / 2f) * 842f, center.second, .01f)
                assertEquals("Placement must preserve signature proportions",
                    expected.width.toFloat() / expected.height,
                    hypot(transform.a, transform.b) / hypot(transform.c, transform.d), .001f)
            }
        }

    private fun imageTransform(page: PDPage): PdfContentTransform {
        val parser = PDFStreamParser(page).apply { parse() }
        var transform = PdfContentTransform()
        val stack = mutableListOf<PdfContentTransform>()
        val operands = mutableListOf<COSBase>()
        var result: PdfContentTransform? = null
        for (token in parser.tokens) {
            if (token !is Operator) {
                if (token is COSBase) operands += token
                continue
            }
            fun number(index: Int) = (operands[index] as COSNumber).floatValue()
            when (token.name) {
                "q" -> stack += transform
                "Q" -> transform = stack.removeAt(stack.lastIndex)
                "cm" -> transform = transform.then(PdfContentTransform(
                    number(0), number(1), number(2), number(3), number(4), number(5),
                ))
                "Do" -> if (page.resources.getXObject(operands.single() as COSName) is PDImageXObject) {
                    assertEquals(null, result)
                    result = transform
                }
            }
            operands.clear()
        }
        return requireNotNull(result)
    }

    private fun alphaBytes(bitmap: Bitmap): ByteArray {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return ByteArray(pixels.size) { (pixels[it] ushr 24).toByte() }
    }

    private fun localFile(uri: String) = File(java.net.URI(uri))

    private fun editor() = AndroidPdfEditorRepository(
        context, UnusedDocuments(), AndroidDocumentFileStore(context, Dispatchers.Unconfined),
        Dispatchers.Unconfined,
    )

    private class UnusedDocuments : DocumentRepository {
        override fun observeDocuments() = MutableStateFlow<List<DocumentModel>>(emptyList())
        override fun observeTrashedDocuments() = MutableStateFlow<List<DocumentModel>>(emptyList())
        override suspend fun findById(id: Long): DocumentModel? = error("unused")
        override suspend fun prepareScan(pageUris: List<String>): ScannedDocument = error("unused")
        override suspend fun discardScan(scan: ScannedDocument) = Unit
        override suspend fun create(scan: ScannedDocument, name: String, tagId: Long?): Long = error("unused")
        override suspend fun update(id: Long, name: String, tagId: Long?) = error("unused")
        override suspend fun replaceContent(id: Long, scan: ScannedDocument, allowProtectionRemoval: Boolean): DocumentModel = error("unused")
        override suspend fun moveToTrash(id: Long) = error("unused")
        override suspend fun restoreFromTrash(id: Long) = error("unused")
        override suspend fun deletePermanently(id: Long) = error("unused")
        override suspend fun delete(document: DocumentModel) = error("unused")
        override suspend fun export(document: DocumentModel, targetUri: String) = error("unused")
        override suspend fun prepareShare(document: DocumentModel): String = error("unused")
    }

    /** Uri.fromFile expects Android paths; preserve the same tests on a Windows host. */
    @Implements(Uri::class)
    class HostFileUri {
        companion object {
            @JvmStatic @Implementation
            fun fromFile(file: File): Uri = Uri.parse(file.toURI().toASCIIString())
        }
    }
}
