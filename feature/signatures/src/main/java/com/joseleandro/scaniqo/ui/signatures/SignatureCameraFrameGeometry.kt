package com.joseleandro.scaniqo.ui.signatures

import android.graphics.BitmapFactory
import android.graphics.RectF
import android.media.ExifInterface
import android.util.Size
import androidx.camera.view.TransformExperimental
import androidx.camera.view.transform.CoordinateTransform
import androidx.camera.view.transform.FileTransformFactory
import androidx.camera.view.transform.OutputTransform
import com.joseleandro.scaniqo.domain.model.PdfSignatureCrop
import java.io.File

internal data class SignatureCameraViewport(
    val width: Int,
    val height: Int,
    val rotation: Int,
) {
    init {
        require(width > 0 && height > 0 && rotation in 0..3)
    }
}

@androidx.annotation.OptIn(markerClass = [TransformExperimental::class])
internal object SignatureCameraFrameGeometry {
    fun guide(width: Float, height: Float): RectF {
        require(width.isFinite() && height.isFinite() && width > 0f && height > 0f)
        val frameWidth = width * 0.84f
        val frameHeight = minOf(frameWidth / 2.6f, height * 0.5f)
        val left = (width - frameWidth) / 2f
        val top = (height - frameHeight) / 2f
        return RectF(left, top, left + frameWidth, top + frameHeight)
    }

    /**
     * Preview and ImageCapture must belong to the same CameraX UseCaseGroup/ViewPort.
     * CameraX crops the JPEG to that viewport before saving it. Its official transforms
     * map the captured guide through buffer rotation, viewport crop and preview mirroring.
     * The result uses the upright coordinates produced later by stageImage's EXIF transform.
     */
    fun cropInOrientedImage(
        image: File,
        previewTransform: OutputTransform,
        guide: RectF,
    ): PdfSignatureCrop {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(image.path, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0)
        val orientation = ExifInterface(image.path).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL,
        )
        val imageSize = orientedSize(bounds.outWidth, bounds.outHeight, orientation)
        val fileTransform = FileTransformFactory().apply {
            isUsingExifOrientation = true
        }.getOutputTransform(image)
        val mapped = RectF(guide)
        CoordinateTransform(previewTransform, fileTransform).mapRect(mapped)
        return normalize(mapped, imageSize)
    }

    fun orientedSize(width: Int, height: Int, exifOrientation: Int): Size {
        require(width > 0 && height > 0)
        return when (exifOrientation) {
            ExifInterface.ORIENTATION_TRANSPOSE,
            ExifInterface.ORIENTATION_ROTATE_90,
            ExifInterface.ORIENTATION_TRANSVERSE,
            ExifInterface.ORIENTATION_ROTATE_270 -> Size(height, width)
            else -> Size(width, height)
        }
    }

    fun normalize(rect: RectF, image: Size): PdfSignatureCrop {
        require(image.width > 0 && image.height > 0)
        require(listOf(rect.left, rect.top, rect.right, rect.bottom).all(Float::isFinite))
        require(rect.width() > 0f && rect.height() > 0f)
        require(
            rect.left >= -1f && rect.top >= -1f &&
                rect.right <= image.width + 1f && rect.bottom <= image.height + 1f,
        ) { "O enquadramento da câmera mudou. Tente fotografar novamente." }
        return PdfSignatureCrop(
            (rect.left / image.width).coerceIn(0f, 1f),
            (rect.top / image.height).coerceIn(0f, 1f),
            (rect.right / image.width).coerceIn(0f, 1f),
            (rect.bottom / image.height).coerceIn(0f, 1f),
        ).also { require(it.isValid) }
    }
}
