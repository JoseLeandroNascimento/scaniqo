package com.joseleandro.scaniqo.ui.signatures

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Carrega a imagem transparente fora da thread principal, com memória limitada. */
@Composable
fun SignatureImage(uri: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current.applicationContext
    val result by produceState<Result<Bitmap>?>(null, uri) {
        var bitmap: Bitmap? = null
        value = null
        try {
            withContext(Dispatchers.IO) {
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri.toUri())?.use { BitmapFactory.decodeStream(it, null, options) }
                require(options.outWidth > 0 && options.outHeight > 0)
                options.inSampleSize = 1
                while (maxOf(options.outWidth, options.outHeight) / options.inSampleSize > 1600) options.inSampleSize *= 2
                options.inJustDecodeBounds = false
                bitmap = context.contentResolver.openInputStream(uri.toUri())?.use { BitmapFactory.decodeStream(it, null, options) }
                requireNotNull(bitmap)
                ensureActive()
            }
            value = Result.success(requireNotNull(bitmap))
        } catch (cancelled: CancellationException) { bitmap?.recycle(); throw cancelled }
        catch (error: Exception) { bitmap?.recycle(); value = Result.failure(error) }
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        val bitmap = result?.getOrNull()
        when {
            bitmap != null -> Image(bitmap.asImageBitmap(), "Assinatura", Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            result == null -> CircularProgressIndicator()
            else -> Text("Imagem indisponível")
        }
    }
}
