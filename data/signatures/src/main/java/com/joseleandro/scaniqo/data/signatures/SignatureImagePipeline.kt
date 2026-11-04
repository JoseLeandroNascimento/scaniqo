package com.joseleandro.scaniqo.data.signatures

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import com.joseleandro.scaniqo.domain.model.PdfSignatureCrop
import com.joseleandro.scaniqo.domain.model.PdfSignatureImage
import java.io.File
import java.util.UUID
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Both gallery imports and camera captures use this bounded, app-owned staging pipeline. */
internal class SignatureImagePipeline(context: Context) {
    private val context = context.applicationContext
    private val staging = File(this.context.cacheDir, "signature_imports")

    suspend fun stage(uri: String): PdfSignatureImage {
        check(staging.isDirectory || staging.mkdirs()) { "Não foi possível preparar a imagem." }
        pruneStaleFiles(uri)
        val encoded = File(staging, "input-${UUID.randomUUID()}")
        var bitmap: Bitmap? = null
        var output: File? = null
        var completed = false
        try {
            val sourceUri = Uri.parse(uri)
            val stream = localFile(sourceUri)?.inputStream()
                ?: context.contentResolver.openInputStream(sourceUri)
                ?: error("Não foi possível abrir a imagem.")
            stream.use { source ->
                encoded.outputStream().use { target ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = source.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= MAX_ENCODED_BYTES) { "Use uma imagem de até 40 MB." }
                        target.write(buffer, 0, count)
                    }
                }
            }
            bitmap = decodeBounded(encoded)
            val orientation = runCatching {
                ExifInterface(encoded.path).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL,
                )
            }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
            val transform = orientationTransform(orientation)
            if (transform != null) {
                val original = bitmap
                bitmap = Bitmap.createBitmap(
                    original, 0, 0, original.width, original.height, transform, true,
                )
                if (bitmap !== original) original.recycle()
            }
            currentCoroutineContext().ensureActive()
            output = File(staging, "source-${UUID.randomUUID()}.png")
            writePng(bitmap, output)
            currentCoroutineContext().ensureActive()
            val result = bitmap.descriptor(output)
            completed = true
            return result
        } finally {
            bitmap?.recycle()
            encoded.delete()
            if (!completed) output?.delete()
            // Camera captures belong to this one-shot flow; gallery/files providers never do.
            captureFile(uri)?.delete()
        }
    }

    suspend fun process(uri: String, crop: PdfSignatureCrop): PdfSignatureImage {
        val source = owned(uri, "source")
        require(source.isFile) { "Selecione novamente a imagem da assinatura." }
        val decoded = decodeBounded(source)
        var selected: Bitmap? = null
        var result: Bitmap? = null
        var output: File? = null
        var completed = false
        try {
            val bounds = crop.pixels(decoded.width, decoded.height)
            selected = Bitmap.createBitmap(decoded, bounds.x, bounds.y, bounds.width, bounds.height)
            val pixels = IntArray(selected.width * selected.height)
            selected.getPixels(pixels, 0, selected.width, 0, 0, selected.width, selected.height)
            val width = selected.width
            val height = selected.height
            if (selected !== decoded) selected.recycle()
            selected = null
            decoded.recycle()
            val coroutineContext = currentCoroutineContext()
            val processed = SignatureBackgroundRemover.remove(pixels, width, height) {
                coroutineContext.ensureActive()
            }
            result = Bitmap.createBitmap(processed.width, processed.height, Bitmap.Config.ARGB_8888)
            // JPEG decoders mark bitmaps opaque even with ARGB_8888. Alpha writes alone are ignored.
            result.setHasAlpha(true)
            result.setPixels(
                processed.pixels, 0, processed.width, 0, 0, processed.width, processed.height,
            )
            output = File(staging, "processed-${UUID.randomUUID()}.png")
            writePng(result, output)
            currentCoroutineContext().ensureActive()
            val descriptor = result.descriptor(output)
            completed = true
            return descriptor
        } finally {
            selected?.takeUnless { it === decoded }?.recycle()
            if (!decoded.isRecycled) decoded.recycle()
            result?.recycle()
            if (!completed) output?.delete()
        }
    }

    fun loadProcessed(uri: String): Bitmap {
        val file = owned(uri, "processed")
        require(file.isFile) { "Processe a assinatura antes de salvar." }
        val bitmap = decodeBounded(file, allowGutter = true)
        try {
            require(bitmap.hasAlpha()) { "O processamento não produziu transparência." }
            var hasTransparent = false
            var hasInk = false
            val row = IntArray(bitmap.width)
            for (y in 0 until bitmap.height) {
                bitmap.getPixels(row, 0, row.size, 0, y, row.size, 1)
                for (pixel in row) {
                    hasTransparent = hasTransparent || pixel ushr 24 == 0
                    hasInk = hasInk || pixel ushr 24 > 0
                }
            }
            require(hasTransparent && hasInk) { "O processamento não produziu uma assinatura válida." }
            return bitmap
        } catch (failure: Throwable) {
            bitmap.recycle()
            throw failure
        }
    }

    fun discard(uri: String) {
        val parsed = Uri.parse(uri)
        if (parsed.scheme != "file") return
        val file = localFile(parsed) ?: return
        if (file.parentFile?.canonicalFile == staging.canonicalFile &&
            Regex("(?:source|processed)-[0-9a-f-]{36}[.]png").matches(file.name)
        ) file.delete()
        captureFile(uri)?.delete()
    }

    private fun pruneStaleFiles(sourceUri: String) {
        val source = localFile(Uri.parse(sourceUri))?.canonicalFile
        val cutoff = System.currentTimeMillis() - 24L * 60 * 60 * 1000
        val uuid = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
        val drafts = Regex("(?:(?:source|processed)-$uuid[.]png|input-$uuid)")
        val captures = Regex("signature_[0-9A-Za-z_-]+[.]jpg")
        fun prune(directory: File, names: Regex) {
            directory.listFiles()?.forEach { file ->
                if (file.isFile && names.matches(file.name) && file.lastModified() in 1 until cutoff &&
                    file.canonicalFile != source
                ) file.delete()
            }
        }
        // A process can die before its ViewModel disposes a draft. Recent sessions stay intact.
        prune(staging, drafts)
        prune(File(context.cacheDir, "signature_captures"), captures)
    }

    private fun owned(uri: String, type: String): File {
        val parsed = Uri.parse(uri)
        require(parsed.scheme == "file") { "A imagem não pertence à edição atual." }
        val file = requireNotNull(localFile(parsed)) { "A imagem não pertence à edição atual." }
        require(
            file.parentFile?.canonicalFile == staging.canonicalFile &&
                Regex("$type-[0-9a-f-]{36}[.]png").matches(file.name),
        ) { "A imagem não pertence à edição atual." }
        return file
    }

    private fun captureFile(uri: String): File? {
        val parsed = Uri.parse(uri)
        if (parsed.scheme != "file") return null
        val file = localFile(parsed) ?: return null
        val captures = File(context.cacheDir, "signature_captures").canonicalFile
        return file.takeIf { it.parentFile?.canonicalFile == captures }
    }

    private fun localFile(uri: Uri): File? {
        if (uri.scheme != "file") return null
        // java.net.URI also round-trips Windows paths in host-side tests and percent-encoded names.
        return File(java.net.URI(uri.toString()))
    }

    private fun decodeBounded(file: File, allowGutter: Boolean = false): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Escolha uma imagem válida." }
        var sample = 1
        val dimensionLimit = MAX_DIMENSION + if (allowGutter) 8 else 0
        val pixelLimit = MAX_PIXELS + if (allowGutter) MAX_DIMENSION * 16L + 64L else 0L
        while (
            maxOf(bounds.outWidth, bounds.outHeight) / sample > dimensionLimit ||
            (bounds.outWidth.toLong() / sample) * (bounds.outHeight / sample) > pixelLimit
        ) sample *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return requireNotNull(BitmapFactory.decodeFile(file.path, options)) {
            "Não foi possível decodificar a imagem."
        }
    }

    private fun writePng(bitmap: Bitmap, file: File) {
        file.outputStream().use {
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) {
                "Não foi possível salvar a imagem da assinatura."
            }
        }
    }

    private fun Bitmap.descriptor(file: File) =
        PdfSignatureImage(file.toURI().toString(), width.toFloat() / height)

    private fun orientationTransform(orientation: Int): Matrix? = when (orientation) {
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> Matrix().apply { setScale(-1f, 1f) }
        ExifInterface.ORIENTATION_ROTATE_180 -> Matrix().apply { setRotate(180f) }
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> Matrix().apply { setScale(1f, -1f) }
        ExifInterface.ORIENTATION_TRANSPOSE -> Matrix().apply { setRotate(90f); postScale(-1f, 1f) }
        ExifInterface.ORIENTATION_ROTATE_90 -> Matrix().apply { setRotate(90f) }
        ExifInterface.ORIENTATION_TRANSVERSE -> Matrix().apply { setRotate(270f); postScale(-1f, 1f) }
        ExifInterface.ORIENTATION_ROTATE_270 -> Matrix().apply { setRotate(270f) }
        else -> null
    }

    companion object {
        private const val MAX_DIMENSION = 2560
        private const val MAX_PIXELS = 4_000_000L
        private const val MAX_ENCODED_BYTES = 40L * 1024 * 1024
    }
}
