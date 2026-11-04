package com.joseleandro.scaniqo.ui.signatures

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.view.TransformExperimental
import androidx.camera.view.transform.CoordinateTransform
import androidx.camera.view.transform.ImageProxyTransformFactory
import androidx.camera.view.transform.OutputTransform

@androidx.annotation.OptIn(markerClass = [TransformExperimental::class])
internal class SignatureFrameAnalyzer(
    private val viewport: SignatureCameraViewport,
    private val previewTransform: () -> OutputTransform?,
    private val enabled: () -> Boolean,
    private val resetVersion: () -> Int,
    private val onResult: (SignatureScanResult, Int) -> Unit,
) : ImageAnalysis.Analyzer {
    private val tracker = SignatureAutoCaptureTracker()
    private val transformFactory = ImageProxyTransformFactory()
    private var lastFrameMs = Long.MIN_VALUE
    private var lastVersion = -1

    override fun analyze(image: ImageProxy) {
        val version = resetVersion()
        try {
            if (version != lastVersion) {
                tracker.reset()
                lastFrameMs = Long.MIN_VALUE
                lastVersion = version
            }
            if (!enabled()) {
                tracker.reset()
                lastFrameMs = Long.MIN_VALUE
                return
            }
            val timeMs = image.imageInfo.timestamp / 1_000_000
            if (lastFrameMs != Long.MIN_VALUE && timeMs - lastFrameMs in 0..99) return
            lastFrameMs = timeMs
            val source = previewTransform() ?: return
            val guide = SignatureCameraFrameGeometry.guide(viewport.width.toFloat(), viewport.height.toFloat())
            val corners = floatArrayOf(guide.left, guide.top, guide.right, guide.top, guide.left, guide.bottom)
            CoordinateTransform(source, transformFactory.getOutputTransform(image)).mapPoints(corners)
            val plane = image.planes.first()
            val buffer = plane.buffer
            val base = buffer.position()
            val width = SignatureInkDetector.WIDTH
            val height = SignatureInkDetector.HEIGHT
            val luma = IntArray(width * height)
            for (y in 0 until height) for (x in 0 until width) {
                val u = (x + 0.5f) / width
                val v = (y + 0.5f) / height
                val sx = (corners[0] + u * (corners[2] - corners[0]) +
                    v * (corners[4] - corners[0])).toInt()
                val sy = (corners[1] + u * (corners[3] - corners[1]) +
                    v * (corners[5] - corners[1])).toInt()
                require(sx in 0 until image.width && sy in 0 until image.height)
                luma[y * width + x] = buffer.get(base + sy * plane.rowStride + sx * plane.pixelStride)
                    .toInt() and 0xff
            }
            onResult(tracker.update(SignatureInkDetector.detect(luma), timeMs), version)
        } catch (_: Exception) {
            tracker.reset()
            onResult(SignatureScanResult(SignatureScanHint.MANUAL), version)
        } finally {
            image.close()
        }
    }
}
