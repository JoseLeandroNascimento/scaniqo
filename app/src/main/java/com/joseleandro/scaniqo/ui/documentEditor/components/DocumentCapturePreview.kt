package com.joseleandro.scaniqo.ui.documentEditor.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.joseleandro.scaniqo.R
import com.joseleandro.scaniqo.domain.model.PdfInteractivePixels
import com.joseleandro.scaniqo.ui.documentEditor.state.DocumentLivePreviewState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

internal data class CaptureRaster(val source: PdfInteractivePixels, val image: ImageBitmap?)

/** Uma conversão por captura, compartilhada pela imagem e pela lupa durante o gesto. */
@Composable
internal fun rememberCaptureRaster(pixels: PdfInteractivePixels): CaptureRaster? {
    if (LocalInspectionMode.current) return remember(pixels) {
        CaptureRaster(pixels, Bitmap.createBitmap(
            pixels.pixels, pixels.width, pixels.height, Bitmap.Config.ARGB_8888,
        ).asImageBitmap())
    }
    val raster by produceState<CaptureRaster?>(null, pixels) {
        value = null
        var prepared: Bitmap? = null
        try {
            val next = withContext(Dispatchers.Default) {
                prepared = Bitmap.createBitmap(pixels.pixels, pixels.width, pixels.height, Bitmap.Config.ARGB_8888)
                ensureActive()
                CaptureRaster(pixels, requireNotNull(prepared).asImageBitmap())
            }
            value = next
        } catch (cancelled: CancellationException) {
            prepared?.recycle()
            throw cancelled
        } catch (_: Exception) {
            prepared?.recycle()
            value = CaptureRaster(pixels, null)
        }
        // Depois de publicado, o bitmap pode estar na GPU e é liberado pelo GC.
    }
    return raster?.takeIf { it.source === pixels }
}

/** Exibe apenas a captura, sem enquadramento A4, numeração ou assinaturas. */
@Composable
internal fun DocumentCaptureImage(
    pixels: PdfInteractivePixels,
    description: String,
    modifier: Modifier = Modifier,
    raster: CaptureRaster? = rememberCaptureRaster(pixels),
) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val current = raster?.takeIf { it.source === pixels }
        val image = current?.image
        if (image != null) {
            val ratio = pixels.sourceWidth / pixels.sourceHeight
            val width = minOf(maxWidth, maxHeight * ratio).coerceAtLeast(1.dp)
            Image(image, description, Modifier.width(width).height(width / ratio), contentScale = ContentScale.FillBounds)
        } else if (current != null) {
            Text(stringResource(R.string.quality_preview_unavailable), style = MaterialTheme.typography.bodySmall)
        } else CircularProgressIndicator(Modifier.size(28.dp))
    }
}

@Composable
internal fun DocumentCaptureResultPreview(
    pageId: String,
    state: DocumentLivePreviewState,
    enabled: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val matchesPage = state.pageId == pageId
    val pixels = state.pixels.takeIf { matchesPage }
    if (pixels != null && !state.isRendering && !state.error) {
        DocumentCaptureImage(
            pixels = pixels,
            description = stringResource(R.string.capture_editor_result),
            modifier = modifier,
        )
    } else {
        DocumentCaptureStatus(
            unsupported = matchesPage && state.unsupported,
            error = matchesPage && state.error,
            enabled = enabled,
            onRetry = onRetry,
            modifier = modifier,
        )
    }
}

@Composable
internal fun DocumentCaptureStatus(
    unsupported: Boolean,
    error: Boolean,
    enabled: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        if (unsupported || error) {
            Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    stringResource(if (unsupported) R.string.capture_editor_unavailable else R.string.quality_preview_unavailable),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (error) {
                    TextButton(onClick = onRetry, enabled = enabled) {
                        Text(stringResource(R.string.corrections_preview_retry))
                    }
                }
            }
        } else CircularProgressIndicator(Modifier.size(28.dp))
    }
}
