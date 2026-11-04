package com.joseleandro.scaniqo.ui.signatures

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RectF
import android.media.ExifInterface
import android.util.Size
import androidx.camera.view.TransformExperimental
import androidx.camera.view.transform.FileTransformFactory
import com.joseleandro.scaniqo.domain.model.PdfSignatureCrop
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
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
@androidx.annotation.OptIn(markerClass = [TransformExperimental::class])
class SignatureCameraFrameGeometryTest {
    private val images = mutableListOf<File>()

    @After fun removeImages() { images.forEach(File::delete) }

    @Test fun portraitGuideSelectsOnlyTheVisibleFrameInTheSavedViewport() {
        val preview = jpeg(120, 200)
        val captured = jpeg(1200, 2000)
        val crop = map(preview, captured, SignatureCameraFrameGeometry.guide(120f, 200f))
        assertCrop(crop, 0.08f, 0.40307692f, 0.92f, 0.59692308f)
    }

    @Test fun landscapeGuideIsLimitedToHalfThePreviewHeight() {
        val preview = jpeg(200, 80)
        val captured = jpeg(2000, 800)
        val crop = map(preview, captured, SignatureCameraFrameGeometry.guide(200f, 80f))
        assertCrop(crop, 0.08f, 0.25f, 0.92f, 0.75f)
    }

    @Test fun squareGuidePreservesItsSignatureAspectRatio() {
        val preview = jpeg(100, 100)
        val captured = jpeg(1000, 1000)
        val crop = map(preview, captured, SignatureCameraFrameGeometry.guide(100f, 100f))
        assertCrop(crop, 0.08f, 0.33846155f, 0.92f, 0.66153845f)
        assertEquals(2.6f, (crop.right - crop.left) / (crop.bottom - crop.top), 0.0001f)
    }

    @Test fun offCenterFrameMapsBetweenDifferentCaptureAndPreviewResolutions() {
        val crop = map(jpeg(120, 80), jpeg(1200, 800), RectF(12f, 24f, 72f, 54f))
        assertCrop(crop, 0.1f, 0.3f, 0.6f, 0.675f)
    }

    @Test fun jpegRotationMapsToTheSameUprightCoordinatesUsedByImageStaging() {
        val preview = jpeg(120, 80)
        val guide = RectF(12f, 24f, 72f, 54f)
        val cases = listOf(
            ExifInterface.ORIENTATION_NORMAL to PdfSignatureCrop(0.1f, 0.3f, 0.6f, 0.675f),
            ExifInterface.ORIENTATION_ROTATE_90 to PdfSignatureCrop(0.325f, 0.1f, 0.7f, 0.6f),
            ExifInterface.ORIENTATION_ROTATE_180 to PdfSignatureCrop(0.4f, 0.325f, 0.9f, 0.7f),
            ExifInterface.ORIENTATION_ROTATE_270 to PdfSignatureCrop(0.3f, 0.4f, 0.675f, 0.9f),
        )
        for ((rotation, expected) in cases) {
            val crop = map(preview, jpeg(1200, 800, rotation), guide)
            assertCrop(crop, expected.left, expected.top, expected.right, expected.bottom)
        }
    }

    @Test fun equallyRotatedPreviewAndJpegPreserveTheSelectedUprightRegion() {
        for (rotation in listOf(ExifInterface.ORIENTATION_ROTATE_90, ExifInterface.ORIENTATION_ROTATE_270)) {
            val crop = map(
                jpeg(80, 120, rotation),
                jpeg(800, 1200, rotation),
                RectF(12f, 24f, 72f, 54f),
            )
            assertCrop(crop, 0.1f, 0.3f, 0.6f, 0.675f)
        }
    }

    @Test fun mirroredPreviewMapsBackToTheUnmirroredJpeg() {
        val preview = jpeg(120, 80, ExifInterface.ORIENTATION_FLIP_HORIZONTAL)
        val crop = map(preview, jpeg(1200, 800), RectF(12f, 24f, 72f, 54f))
        assertCrop(crop, 0.4f, 0.3f, 0.9f, 0.675f)
    }

    @Test fun transposedExifOrientationsUseUprightImageDimensions() {
        for (orientation in listOf(
            ExifInterface.ORIENTATION_TRANSPOSE,
            ExifInterface.ORIENTATION_ROTATE_90,
            ExifInterface.ORIENTATION_TRANSVERSE,
            ExifInterface.ORIENTATION_ROTATE_270,
        )) {
            assertEquals(Size(800, 1200), SignatureCameraFrameGeometry.orientedSize(1200, 800, orientation))
        }
        assertEquals(
            Size(1200, 800),
            SignatureCameraFrameGeometry.orientedSize(1200, 800, ExifInterface.ORIENTATION_ROTATE_180),
        )
    }

    @Test fun invalidTransformCannotSilentlyCaptureTheWholePhoto() {
        val size = Size(1200, 800)
        for (invalid in listOf(
            RectF(-20f, 20f, 1100f, 700f),
            RectF(20f, 20f, 1220f, 700f),
            RectF(20f, 20f, 1100f, 820f),
            RectF(20f, Float.NaN, 1100f, 700f),
            RectF(30f, 20f, 20f, 700f),
            RectF(20f, 20f, 20f, 700f),
        )) {
            assertThrows(IllegalArgumentException::class.java) {
                SignatureCameraFrameGeometry.normalize(invalid, size)
            }
        }
    }

    @Test fun subpixelRoundingAtImageEdgesIsClamped() {
        val crop = SignatureCameraFrameGeometry.normalize(
            RectF(-0.5f, -0.5f, 1200.5f, 800.5f), Size(1200, 800),
        )
        assertCrop(crop, 0f, 0f, 1f, 1f)
    }

    private fun map(preview: File, captured: File, frame: RectF): PdfSignatureCrop {
        // A file transform supplies a real CameraX output coordinate space for the same
        // normalized sensor viewport; production supplies PreviewView.outputTransform.
        val previewTransform = FileTransformFactory().apply {
            isUsingExifOrientation = true
        }.getOutputTransform(preview)
        return SignatureCameraFrameGeometry.cropInOrientedImage(captured, previewTransform, frame)
    }

    private fun jpeg(width: Int, height: Int, orientation: Int = ExifInterface.ORIENTATION_NORMAL): File {
        val file = File.createTempFile(
            "signature-camera-geometry-", ".jpg", RuntimeEnvironment.getApplication().cacheDir,
        ).also(images::add)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(Color.WHITE)
            file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it)) }
        } finally {
            bitmap.recycle()
        }
        ExifInterface(file.path).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
            saveAttributes()
        }
        return file
    }

    private fun assertCrop(actual: PdfSignatureCrop, left: Float, top: Float, right: Float, bottom: Float) {
        assertTrue(actual.isValid)
        assertEquals(left, actual.left, 0.0001f)
        assertEquals(top, actual.top, 0.0001f)
        assertEquals(right, actual.right, 0.0001f)
        assertEquals(bottom, actual.bottom, 0.0001f)
    }
}
