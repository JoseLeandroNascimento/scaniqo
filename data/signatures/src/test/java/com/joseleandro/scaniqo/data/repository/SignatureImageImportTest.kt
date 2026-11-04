package com.joseleandro.scaniqo.data.repository

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.ExifInterface
import com.joseleandro.scaniqo.domain.model.PdfSignatureCrop
import com.joseleandro.scaniqo.domain.model.PdfSignatureImage
import com.joseleandro.scaniqo.domain.model.SignatureModel
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, manifest = Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SignatureImageImportTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Test fun opaqueJpegProducesRealAlphaAndSavesExactlyThePreviewPixels() = runTest {
        val repository = AndroidSignatureRepository(context)
        val input = fixture(Bitmap.CompressFormat.JPEG)
        val drafts = mutableListOf<PdfSignatureImage>()
        var saved: SignatureModel? = null
        try {
            BitmapFactory.decodeFile(input.path).useBitmap { assertFalse(it.hasAlpha()) }
            val source = repository.stageImage(input.toURI().toString()).also(drafts::add)
            val preview = repository.processImage(source.imageUri, FULL_CROP).also(drafts::add)
            val model = repository.saveProcessedImage("Importada", preview.imageUri)
            saved = model
            val reloaded = AndroidSignatureRepository(context).observeSignatures().first()
                .single { it.id == model.id }
            assertEquals(model, reloaded)
            decode(preview.imageUri).useBitmap { expected ->
                decode(reloaded.imageUri).useBitmap { actual ->
                    assertTrue(actual.hasAlpha())
                    assertEquals(expected.width, actual.width)
                    assertEquals(expected.height, actual.height)
                    assertArrayEquals(pixels(expected), pixels(actual))
                    assertTrue(pixels(actual).any { Color.alpha(it) == 0 })
                    assertTrue(pixels(actual).any { Color.alpha(it) > 200 })
                    assertTrue(actual.width < 200)
                }
            }
        } finally {
            saved?.let { repository.delete(it.id) }
            drafts.forEach { repository.discardStagedImage(it.imageUri) }
            input.delete()
        }
    }

    @Test fun legacyFalseFlagCannotBypassAutomaticBackgroundRemoval() = runTest {
        val repository = AndroidSignatureRepository(context)
        val input = fixture(Bitmap.CompressFormat.PNG, Color.rgb(200, 220, 160))
        var saved: SignatureModel? = null
        try {
            val model = repository.importImage("Sempre transparente", input.toURI().toString(), false)
            saved = model
            decode(model.imageUri).useBitmap {
                assertTrue(it.hasAlpha())
                assertEquals(0, Color.alpha(it.getPixel(0, 0)))
                assertTrue(pixels(it).any { pixel -> Color.blue(pixel) > Color.red(pixel) + 70 })
            }
        } finally {
            saved?.let { repository.delete(it.id) }
            input.delete()
        }
    }

    @Test fun cameraJpegUsesSamePipelineAndItsTemporaryCaptureIsConsumed() = runTest {
        val captureDirectory = File(context.cacheDir, "signature_captures").apply { mkdirs() }
        val input = fixture(Bitmap.CompressFormat.JPEG, parent = captureDirectory)
        val repository = AndroidSignatureRepository(context)
        val drafts = mutableListOf<PdfSignatureImage>()
        try {
            val source = repository.stageImage(input.toURI().toString()).also(drafts::add)
            assertFalse(input.exists())
            val preview = repository.processImage(source.imageUri, FULL_CROP).also(drafts::add)
            decode(preview.imageUri).useBitmap {
                assertTrue(it.hasAlpha())
                assertTrue(pixels(it).any { pixel -> Color.alpha(pixel) == 0 })
                assertTrue(pixels(it).any { pixel -> Color.alpha(pixel) > 0 })
            }
        } finally {
            drafts.forEach { repository.discardStagedImage(it.imageUri) }
            input.delete()
        }
    }

    @Test fun onlySelectedRegionIsProcessed() = runTest {
        val input = File.createTempFile("crop-signature", ".png", context.cacheDir)
        Bitmap.createBitmap(320, 120, Bitmap.Config.ARGB_8888).useBitmap { bitmap ->
            bitmap.eraseColor(Color.WHITE)
            for (y in 58..62) {
                for (x in 30..70) bitmap.setPixel(x, y, Color.RED)
                for (x in 180..280) bitmap.setPixel(x, y, Color.BLUE)
            }
            input.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        val repository = AndroidSignatureRepository(context)
        val drafts = mutableListOf<PdfSignatureImage>()
        try {
            val source = repository.stageImage(input.toURI().toString()).also(drafts::add)
            val preview = repository.processImage(
                source.imageUri, PdfSignatureCrop(.5f, 0f, 1f, 1f),
            ).also(drafts::add)
            decode(preview.imageUri).useBitmap { bitmap ->
                val ink = pixels(bitmap).filter { Color.alpha(it) > 0 }
                assertTrue(ink.isNotEmpty())
                assertTrue(ink.all { Color.blue(it) > Color.red(it) })
                assertEquals(109, bitmap.width)
            }
        } finally {
            drafts.forEach { repository.discardStagedImage(it.imageUri) }
            input.delete()
        }
    }

    @Test fun exifOrientationIsAppliedBeforeTheUserChoosesTheCrop() = runTest {
        val input = fixture(Bitmap.CompressFormat.JPEG)
        ExifInterface(input.path).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
            saveAttributes()
        }
        val repository = AndroidSignatureRepository(context)
        var source: PdfSignatureImage? = null
        try {
            source = repository.stageImage(input.toURI().toString())
            decode(source.imageUri).useBitmap {
                assertEquals(120, it.width)
                assertEquals(240, it.height)
                assertEquals(.5f, source.aspectRatio, 0.0001f)
            }
        } finally {
            source?.let { repository.discardStagedImage(it.imageUri) }
            input.delete()
        }
    }

    @Test fun blankImageFailsAndAnUnprocessedSourceCannotBeSaved() = runTest {
        val repository = AndroidSignatureRepository(context)
        val input = File.createTempFile("blank-signature", ".png", context.cacheDir)
        Bitmap.createBitmap(120, 80, Bitmap.Config.ARGB_8888).useBitmap {
            it.eraseColor(Color.WHITE)
            input.outputStream().use { output -> it.compress(Bitmap.CompressFormat.PNG, 100, output) }
        }
        val before = repository.observeSignatures().first()
        var source: PdfSignatureImage? = null
        try {
            source = repository.stageImage(input.toURI().toString())
            val processing = runCatching { repository.processImage(source.imageUri, FULL_CROP) }
            assertTrue(processing.exceptionOrNull() is IllegalArgumentException)
            val saving = runCatching { repository.saveProcessedImage("Inválida", source.imageUri) }
            assertTrue(saving.exceptionOrNull() is IllegalArgumentException)
            assertEquals(before, repository.observeSignatures().first())
        } finally {
            source?.let { repository.discardStagedImage(it.imageUri) }
            input.delete()
        }
    }

    @Test fun highResolutionSourceIsDecodedWithinPixelBudget() = runTest {
        val input = File.createTempFile("large-signature", ".png", context.cacheDir)
        Bitmap.createBitmap(3200, 1800, Bitmap.Config.ARGB_8888).useBitmap {
            it.eraseColor(Color.WHITE)
            input.outputStream().use { output -> it.compress(Bitmap.CompressFormat.PNG, 100, output) }
        }
        val repository = AndroidSignatureRepository(context)
        var source: PdfSignatureImage? = null
        try {
            source = repository.stageImage(input.toURI().toString())
            decode(source.imageUri).useBitmap {
                assertTrue(maxOf(it.width, it.height) <= 2560)
                assertTrue(it.width.toLong() * it.height <= 4_000_000)
                assertEquals(3200f / 1800, source.aspectRatio, 0.0001f)
            }
        } finally {
            source?.let { repository.discardStagedImage(it.imageUri) }
            input.delete()
        }
    }

    @Test fun stagingPrunesOnlyOldOwnedDraftsAndCaptures() = runTest {
        val imports = File(context.cacheDir, "signature_imports").apply { mkdirs() }
        val captures = File(context.cacheDir, "signature_captures").apply { mkdirs() }
        val id = "5aae5eb8-1cec-48de-89bc-66c9e1c9eb78"
        val oldFiles = listOf(
            File(imports, "source-$id.png"),
            File(imports, "processed-$id.png"),
            File(imports, "input-$id"),
            File(captures, "signature_old_capture.jpg"),
        )
        val recent = File(imports, "source-40beee38-4bb5-49cb-8414-004eb4a6a2df.png")
        val unrelated = File(imports, "family-photo.png")
        val oldTime = System.currentTimeMillis() - 25L * 60 * 60 * 1000
        oldFiles.forEach { it.writeText("abandoned"); assertTrue(it.setLastModified(oldTime)) }
        recent.writeText("active")
        unrelated.writeText("unowned")
        assertTrue(unrelated.setLastModified(oldTime))
        val input = fixture(Bitmap.CompressFormat.PNG)
        val repository = AndroidSignatureRepository(context)
        var source: PdfSignatureImage? = null
        try {
            source = repository.stageImage(input.toURI().toString())
            assertTrue(oldFiles.none(File::exists))
            assertTrue(recent.exists())
            assertTrue(unrelated.exists())
            assertTrue(input.exists())
        } finally {
            source?.let { repository.discardStagedImage(it.imageUri) }
            (oldFiles + recent + unrelated + input).forEach(File::delete)
        }
    }

    @Test fun discardingADraftDoesNotDeleteAnExternalGalleryFile() = runTest {
        val input = fixture(Bitmap.CompressFormat.PNG)
        try {
            AndroidSignatureRepository(context).discardStagedImage(input.toURI().toString())
            assertTrue(input.isFile)
        } finally { input.delete() }
    }

    private fun fixture(
        format: Bitmap.CompressFormat,
        paper: Int = Color.WHITE,
        parent: File = context.cacheDir,
    ): File {
        val file = File.createTempFile("signature-fixture", if (format == Bitmap.CompressFormat.JPEG) ".jpg" else ".png", parent)
        Bitmap.createBitmap(240, 120, Bitmap.Config.ARGB_8888).useBitmap { bitmap ->
            bitmap.eraseColor(paper)
            for (y in 58..62) for (x in 60..179) bitmap.setPixel(x, y, Color.rgb(20, 45, 150))
            file.outputStream().use { bitmap.compress(format, 100, it) }
        }
        return file
    }

    private fun decode(uri: String) = requireNotNull(BitmapFactory.decodeFile(File(java.net.URI(uri)).path))

    private fun pixels(bitmap: Bitmap) = IntArray(bitmap.width * bitmap.height).also {
        bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    }

    private inline fun Bitmap.useBitmap(block: (Bitmap) -> Unit) {
        try { block(this) } finally { recycle() }
    }

    private companion object {
        val FULL_CROP = PdfSignatureCrop(0f, 0f, 1f, 1f)
    }
}
