package com.joseleandro.scaniqo.data.pdf

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import java.io.IOException
import kotlin.math.max

/** Mantém a subamostragem do JPEG no decodificador Android, antes de alocar os pixels. */
internal object PdfCompressionImageDecoder {
    fun decode(image: PDImageXObject, maxSide: Int): Bitmap {
        if (image.suffix != "jpg") {
            return image.getImage(null, sampleSize(image.width, image.height, maxSide))
        }
        // No PDFBox Android 2.0.27, getImage(..., subsampling > 1) trata os bytes
        // comprimidos do JPEG como RGB bruto e calcula uma altura incorreta.
        // createInputStream remove filtros externos e preserva o JPEG para BitmapFactory.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        image.createInputStream().use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw IOException("Não foi possível ler a imagem JPEG deste PDF.")
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxSide)
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inScaled = false
        }
        return image.createInputStream().use { BitmapFactory.decodeStream(it, null, options) }
            ?: throw IOException("Não foi possível decodificar a imagem JPEG deste PDF.")
    }

    private fun sampleSize(width: Int, height: Int, maxSide: Int): Int {
        require(width > 0 && height > 0 && maxSide > 0)
        var sample = 1
        while (max(width, height).toLong() > maxSide.toLong() * sample * 2) sample *= 2
        return sample
    }
}
