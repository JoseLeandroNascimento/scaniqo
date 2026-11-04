package com.joseleandro.scaniqo.ui.signatures

import android.app.Application
import android.graphics.ImageFormat
import android.graphics.Rect
import androidx.camera.core.ImageInfo
import androidx.camera.core.ImageProxy
import androidx.camera.core.impl.TagBundle
import androidx.camera.core.impl.utils.ExifData
import androidx.camera.view.TransformExperimental
import androidx.camera.view.transform.ImageProxyTransformFactory
import java.nio.ByteBuffer
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, manifest = Config.NONE)
@androidx.annotation.OptIn(markerClass = [TransformExperimental::class])
class SignatureFrameAnalyzerTest {
    @Test fun guideMapsThroughEveryRotationViewportCropAndPlaneStride() {
        for (rotation in listOf(0, 90, 180, 270)) {
            val image = PaperImage(rotation)
            var result: SignatureScanResult? = null
            val analyzer = analyzer(image) { observation, _ -> result = observation }
            analyzer.analyze(image)
            assertTrue(image.closed)
            assertEquals("Rotation $rotation", SignatureScanHint.HOLD_STILL, result?.hint)
        }
    }

    @Test fun pausedAndMissingTransformFramesAreClosedWithoutDetection() {
        val image = PaperImage()
        var callbacks = 0
        val disabled = SignatureFrameAnalyzer(
            image.viewport, { null }, { false }, { 0 }, { _, _ -> callbacks++ },
        )
        disabled.analyze(image)
        assertTrue(image.closed)
        val missing = PaperImage()
        SignatureFrameAnalyzer(
            missing.viewport, { null }, { true }, { 0 }, { _, _ -> callbacks++ },
        ).analyze(missing)
        assertTrue(missing.closed)
        assertEquals(0, callbacks)
    }

    @Test fun disabledFramesAndEpochChangesDiscardPreviousStability() {
        val template = PaperImage()
        var enabled = true
        var epoch = 0
        var last = SignatureScanResult()
        val transform = previewTransform(template)
        val analyzer = SignatureFrameAnalyzer(
            template.viewport, { transform }, { enabled }, { epoch }, { result, version ->
                assertEquals(epoch, version)
                last = result
            },
        )
        for (i in 0..1) analyzer.analyze(PaperImage(timestampMs = i * 100L))
        assertTrue(last.progress > 0f)
        assertFalse(last.shouldCapture)
        enabled = false
        val paused = PaperImage(timestampMs = 200)
        analyzer.analyze(paused)
        assertTrue(paused.closed)
        enabled = true
        analyzer.analyze(PaperImage(timestampMs = 300))
        assertEquals(0f, last.progress, 0f)
        analyzer.analyze(PaperImage(timestampMs = 400))
        assertTrue(last.progress > 0f)
        assertFalse(last.shouldCapture)
        epoch++
        // An epoch reset must process the first new frame even inside the throttle interval.
        analyzer.analyze(PaperImage(timestampMs = 450))
        assertEquals(0f, last.progress, 0f)
        assertFalse(last.shouldCapture)
    }

    @Test fun thirtyFpsPreviewCapturesWithinThreeHundredFiftyMillisecondsAndClosesSkippedFrames() {
        val template = PaperImage()
        var frameTimeMs = 0L
        val captures = mutableListOf<Long>()
        var analyzedFrames = 0
        val analyzer = analyzer(template) { result, _ ->
            analyzedFrames++
            if (result.shouldCapture) captures += frameTimeMs
        }
        for (i in 0..30) {
            frameTimeMs = i * 33L
            val image = PaperImage(timestampMs = frameTimeMs)
            analyzer.analyze(image)
            assertTrue(image.closed)
        }
        assertEquals(1, captures.size)
        assertTrue("Capture should follow the hold promptly: $captures", captures.single() in 200L..350L)
        assertTrue("Analysis stays limited to ten frames per second", analyzedFrames <= 10)
    }

    @Test fun unreadablePlaneFallsBackToManualAndAlwaysClosesImage() {
        val image = PaperImage(brokenPlane = true)
        var result: SignatureScanResult? = null
        analyzer(image) { observation, _ -> result = observation }.analyze(image)
        assertTrue(image.closed)
        assertEquals(SignatureScanHint.MANUAL, result?.hint)
    }

    private fun analyzer(image: PaperImage, result: (SignatureScanResult, Int) -> Unit) =
        SignatureFrameAnalyzer(image.viewport, { previewTransform(image) }, { true }, { 0 }, result)

    private fun previewTransform(image: PaperImage) = ImageProxyTransformFactory().apply {
        isUsingCropRect = true
        isUsingRotationDegrees = true
    }.getOutputTransform(image)

    private class PaperImage(
        private val rotation: Int = 0,
        private val timestampMs: Long = 0L,
        brokenPlane: Boolean = false,
    ) : ImageProxy {
        var closed = false
        private var crop = Rect(64, 24, 576, 456)
        private val uprightWidth = if (rotation % 180 == 0) crop.width() else crop.height()
        private val uprightHeight = if (rotation % 180 == 0) crop.height() else crop.width()
        val viewport = SignatureCameraViewport(uprightWidth, uprightHeight, 0)
        private val row = 640 * 2 + 11
        private val buffer = ByteBuffer.allocate(if (brokenPlane) 1 else 7 + row * 480).apply {
            if (!brokenPlane) {
                val guide = SignatureCameraFrameGeometry.guide(uprightWidth.toFloat(), uprightHeight.toFloat())
                for (y in 0 until 480) for (x in 0 until 640) {
                    val cx = x - crop.left
                    val cy = y - crop.top
                    val (px, py) = when (rotation) {
                        90 -> (crop.height() - 1 - cy) to cx
                        180 -> (crop.width() - 1 - cx) to (crop.height() - 1 - cy)
                        270 -> cy to (crop.width() - 1 - cx)
                        else -> cx to cy
                    }
                    val gx = ((px - guide.left) / guide.width() * SignatureInkDetector.WIDTH).toInt()
                    val gy = ((py - guide.top) / guide.height() * SignatureInkDetector.HEIGHT).toInt()
                    val centerY = (40 + 12 * sin(gx * 0.19) + 5 * sin(gx * 0.07)).toInt()
                    val ink = gx in 25..165 && gy in centerY - 1..centerY + 1
                    put(7 + y * row + x * 2, (if (ink) 35 else 230).toByte())
                }
                position(7)
            }
        }

        override fun close() { closed = true }
        override fun getCropRect(): Rect = Rect(crop)
        override fun setCropRect(rect: Rect?) { crop = rect ?: Rect(0, 0, 640, 480) }
        override fun getFormat(): Int = ImageFormat.YUV_420_888
        override fun getHeight(): Int = 480
        override fun getWidth(): Int = 640
        override fun getPlanes(): Array<ImageProxy.PlaneProxy> = arrayOf(object : ImageProxy.PlaneProxy {
            override fun getRowStride(): Int = row
            override fun getPixelStride(): Int = 2
            override fun getBuffer(): ByteBuffer = this@PaperImage.buffer
        })
        override fun getImageInfo(): ImageInfo = object : ImageInfo {
            override fun getTagBundle(): TagBundle = TagBundle.emptyBundle()
            override fun getTimestamp(): Long = timestampMs * 1_000_000
            override fun getRotationDegrees(): Int = rotation
            override fun populateExifData(exifBuilder: ExifData.Builder) = Unit
        }
        @androidx.camera.core.ExperimentalGetImage
        override fun getImage(): android.media.Image? = null
    }
}
