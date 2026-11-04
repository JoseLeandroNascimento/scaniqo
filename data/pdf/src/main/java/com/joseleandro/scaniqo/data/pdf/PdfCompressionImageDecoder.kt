package com.joseleandro.scaniqo.data.pdf

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import kotlin.math.max

/** Mantém a subamostragem do JPEG no decodificador Android, antes de alocar os pixels. */
internal object PdfCompressionImageDecoder {
    fun canDecodeSafely(image: PDImageXObject): Boolean {
        // O leitor Android ignora Decode customizado e não converte corretamente todos os
        // espaços de cor/precisões. Preservar esses recursos evita mudar as cores do original.
        if (image.bitsPerComponent != 8 || image.width <= 0 || image.height <= 0) return false
        // Conferir o dicionário: esta versão do PDFBox substitui CMYK/ICC/Indexed por RGB.
        val declaredColorSpace = image.cosObject.getDictionaryObject(COSName.COLORSPACE)
        if (declaredColorSpace != COSName.DEVICERGB && declaredColorSpace != COSName.DEVICEGRAY) return false
        val colorSpace = image.colorSpace
        val decode = image.decode
        if (decode != null && !decode.toFloatArray().contentEquals(colorSpace.getDefaultDecode(8))) {
            return false
        }
        if (image.suffix == "jpg" && image.cosObject.containsKey(COSName.DECODE_PARMS)) {
            // BitmapFactory não recebe os parâmetros PDF de transformação de cores do JPEG.
            return false
        }
        return true
    }

    fun decode(image: PDImageXObject, maxSide: Int, checkActive: () -> Unit = {}): Bitmap {
        checkActive()
        if (image.suffix != "jpg") return decodeSamples(image, maxSide, checkActive)
        // No PDFBox Android 2.0.27, getImage(..., subsampling > 1) trata os bytes
        // comprimidos do JPEG como RGB bruto e calcula uma altura incorreta.
        // Remover somente os filtros externos, deixando DCT para o BitmapFactory.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        image.createInputStream(jpegStopFilters).use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth != image.width || bounds.outHeight != image.height) {
            throw IOException("As dimensões da imagem JPEG não correspondem ao PDF.")
        }
        checkActive()
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxSide)
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inScaled = false
        }
        return image.createInputStream(jpegStopFilters).use { BitmapFactory.decodeStream(it, null, options) }
            ?: throw IOException("Não foi possível decodificar a imagem JPEG deste PDF.")
    }

    private fun decodeSamples(image: PDImageXObject, maxSide: Int, checkActive: () -> Unit): Bitmap {
        val sample = sampleSize(image.width, image.height, maxSide)
        val width = ((image.width.toLong() + sample - 1) / sample).toInt()
        val height = ((image.height.toLong() + sample - 1) / sample).toInt()
        val components = if (image.cosObject.getDictionaryObject(COSName.COLORSPACE) == COSName.DEVICEGRAY) 1 else 3
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { setHasAlpha(false) }
        var completed = false
        try {
            // PDFBox aplica os filtros e preditores. Ler linhas evita seus buffers RGB de
            // resolução integral e a conversão incorreta de tons de cinza para transparência.
            image.createInputStream().buffered().use { input ->
                val pixels = IntArray(width)
                for (y in 0 until image.height) {
                    checkActive()
                    if (y % sample != 0) {
                        input.skipExactly(image.width.toLong() * components, checkActive)
                        continue
                    }
                    for (x in 0 until width) {
                        val red = input.readSample()
                        val green = if (components == 1) red else input.readSample()
                        val blue = if (components == 1) red else input.readSample()
                        pixels[x] = Color.rgb(red, green, blue)
                        val remaining = minOf(sample - 1, image.width - x * sample - 1)
                        input.skipExactly(remaining.toLong() * components, checkActive)
                    }
                    bitmap.setPixels(pixels, 0, width, 0, y / sample, width, 1)
                }
            }
            completed = true
            return bitmap
        } finally {
            if (!completed) bitmap.recycle()
        }
    }

    private fun InputStream.readSample(): Int =
        read().takeIf { it >= 0 } ?: throw EOFException("Imagem incompleta no PDF.")

    private fun InputStream.skipExactly(count: Long, checkActive: () -> Unit) {
        var remaining = count
        while (remaining > 0) {
            if (count > 64 * 1024) checkActive()
            val skipped = skip(minOf(remaining, 64 * 1024L))
            if (skipped > 0) remaining -= skipped else {
                readSample()
                remaining--
            }
        }
    }

    private fun sampleSize(width: Int, height: Int, maxSide: Int): Int {
        require(width > 0 && height > 0 && maxSide > 0)
        var sample = 1
        while (max(width, height).toLong() > maxSide.toLong() * sample * 2) sample *= 2
        return sample
    }

    private val jpegStopFilters = listOf(COSName.DCT_DECODE.name, "DCT")
}
