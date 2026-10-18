package com.joseleandro.scaniqo.ui.signatures

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.core.net.toUri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

private data class SignatureBitmapResult(val uri: String, val result: Result<Bitmap>)

/** Carrega a imagem transparente fora da thread principal, com memória limitada. */
@Composable
fun SignatureImage(
    uri: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    onImageBitmapChanged: (ImageBitmap?) -> Unit = {},
) {
    val context = LocalContext.current.applicationContext
    val inspection = LocalInspectionMode.current
    val sample = if (inspection) remember {
        val bitmap = Bitmap.createBitmap(1200, 360, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF24385B.toInt()
            textSize = 235f
            typeface = Typeface.create("cursive", Typeface.ITALIC)
        }
        canvas.drawText("Leandro", 95f, 260f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 6f
        val flourish = android.graphics.Path().apply {
            moveTo(75f, 295f)
            cubicTo(350f, 325f, 780f, 250f, 1100f, 300f)
        }
        canvas.drawPath(flourish, paint)
        bitmap.asImageBitmap()
    } else null
    val result by produceState<SignatureBitmapResult?>(null, uri, inspection) {
        value = null
        if (inspection) return@produceState
        var bitmap: Bitmap? = null
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
            value = SignatureBitmapResult(uri, Result.success(requireNotNull(bitmap)))
        } catch (cancelled: CancellationException) {
            bitmap?.recycle()
            throw cancelled
        } catch (error: Exception) {
            bitmap?.recycle()
            value = SignatureBitmapResult(uri, Result.failure(error))
        }
    }
    val current = result?.takeIf { it.uri == uri }
    val bitmap = current?.result?.getOrNull()
    val image = sample ?: remember(bitmap) { bitmap?.asImageBitmap() }
    val callback by rememberUpdatedState(onImageBitmapChanged)
    DisposableEffect(uri, image) {
        callback(image)
        onDispose { callback(null) }
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        when {
            image != null -> Image(image, "Assinatura", Modifier.fillMaxSize(), contentScale = contentScale)
            current == null -> CircularProgressIndicator()
            else -> Text("Imagem indisponível")
        }
    }
}
