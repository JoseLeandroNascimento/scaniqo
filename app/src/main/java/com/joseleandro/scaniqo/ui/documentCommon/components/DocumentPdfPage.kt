package com.joseleandro.scaniqo.ui.documentCommon.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.joseleandro.scaniqo.R
import com.joseleandro.scaniqo.domain.model.DocumentModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Renderiza o próprio PDF exportável; as margens e o enquadramento pertencem ao arquivo. */
@Composable
fun DocumentPdfPage(
    pdfUri: String,
    pageIndex: Int,
    modifier: Modifier = Modifier,
    contentKey: String = "$pdfUri:$pageIndex",
    pageNumber: Int = pageIndex + 1,
    onImageBitmapChanged: ((ImageBitmap?) -> Unit)? = null,
) {
    // Uma identidade estável mantém a folha visível até a nova versão terminar de decodificar.
    // A chave muda ao trocar de página e a composição é descartada ao bloquear o documento.
    key(contentKey) {
        DocumentPageImage(
            pdfUri = pdfUri,
            pageIndex = pageIndex,
            thumbnailUri = null,
            compact = false,
            modifier = modifier,
            retainPrevious = true,
            pageNumber = pageNumber,
            onImageBitmapChanged = onImageBitmapChanged,
        )
    }
}

/** Miniatura leve para organizar páginas sem decodificar cada folha em tamanho de leitura. */
@Composable
internal fun DocumentPageThumbnail(
    pdfUri: String,
    pageIndex: Int,
    modifier: Modifier = Modifier,
) {
    DocumentPageImage(
        pdfUri = pdfUri,
        pageIndex = pageIndex,
        thumbnailUri = null,
        compact = true,
        modifier = modifier,
    )
}

@Composable
fun DocumentThumbnail(
    document: DocumentModel,
    modifier: Modifier = Modifier,
) {
    if (document.isProtected) {
        Box(
            modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_document_lock),
                contentDescription = "PDF protegido por senha",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
            )
        }
        return
    }
    DocumentPageImage(
        pdfUri = document.pdfUri,
        pageIndex = 0,
        thumbnailUri = document.thumbnailUri,
        compact = true,
        modifier = modifier,
    )
}

private sealed interface PageImageState {
    data object Loading : PageImageState
    data class Ready(val bitmap: Bitmap) : PageImageState
    data class Error(val previous: Bitmap?) : PageImageState
}

@Composable
private fun DocumentPageImage(
    pdfUri: String,
    pageIndex: Int,
    thumbnailUri: String?,
    compact: Boolean,
    modifier: Modifier,
    retainPrevious: Boolean = false,
    pageNumber: Int = pageIndex + 1,
    onImageBitmapChanged: ((ImageBitmap?) -> Unit)? = null,
) {
    if (LocalInspectionMode.current) {
        DocumentPaperPreview(modifier)
        return
    }

    val context = LocalContext.current.applicationContext
    var retry by remember(pdfUri, pageIndex, thumbnailUri) { mutableIntStateOf(0) }
    val state by produceState<PageImageState>(
        initialValue = PageImageState.Loading,
        pdfUri, pageIndex, thumbnailUri, retry,
    ) {
        val previous = if (retainPrevious) when (val current = value) {
            is PageImageState.Ready -> current.bitmap
            is PageImageState.Error -> current.previous
            PageImageState.Loading -> null
        } else null
        if (previous == null) value = PageImageState.Loading
        var decoded: Bitmap? = null
        try {
            withContext(Dispatchers.IO) {
                decoded = if (compact && thumbnailUri != null) {
                    runCatching { decodeThumbnail(context, thumbnailUri) }.getOrNull()
                } else null
                ensureActive()
                if (decoded == null) {
                    decoded = renderPage(context, pdfUri, pageIndex, if (compact) 280 else 1400)
                }
                ensureActive()
            }
            value = PageImageState.Ready(requireNotNull(decoded))
        } catch (cancelled: CancellationException) {
            // Um resultado cancelado nunca foi entregue ao Compose e pode ser liberado aqui.
            decoded?.recycle()
            throw cancelled
        } catch (_: Exception) {
            decoded?.recycle()
            value = PageImageState.Error(previous)
        }
        // Bitmaps exibidos são liberados pelo GC quando a página sai da composição.
        // Reciclá-los em onDispose poderia atingir um frame ainda sendo desenhado.
    }

    // A lupa compartilha exatamente a folha exibida; movimentar uma assinatura não decodifica o PDF.
    if (onImageBitmapChanged != null) {
        val currentCallback by rememberUpdatedState(onImageBitmapChanged)
        val bitmap = when (val current = state) {
            is PageImageState.Ready -> current.bitmap
            is PageImageState.Error -> current.previous
            PageImageState.Loading -> null
        }
        val image = remember(bitmap) { bitmap?.asImageBitmap() }
        DisposableEffect(image) {
            currentCallback(image)
            onDispose { currentCallback(null) }
        }
    }

    Box(
        modifier = modifier.background(Color.White),
        contentAlignment = Alignment.Center,
    ) {
        when (val current = state) {
            PageImageState.Loading -> {
                val loadingDescription = stringResource(R.string.document_pdf_loading)
                CircularProgressIndicator(
                    modifier = Modifier.size(if (compact) 20.dp else 32.dp)
                        .semantics { contentDescription = loadingDescription },
                    strokeWidth = 2.dp,
                )
            }
            is PageImageState.Ready -> Image(
                bitmap = current.bitmap.asImageBitmap(),
                contentDescription = if (compact) {
                    stringResource(R.string.document_thumbnail_description)
                } else {
                    stringResource(R.string.document_pdf_page_description, pageNumber)
                },
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
            is PageImageState.Error -> {
                current.previous?.let { bitmap ->
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                }
                val retryDescription = stringResource(R.string.document_pdf_retry)
                if (compact) {
                    IconButton(onClick = { retry++ }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_tag_receipt),
                            contentDescription = retryDescription,
                            tint = Color(0xFF495461),
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier.padding(24.dp)
                            .background(Color.White.copy(alpha = .96f), MaterialTheme.shapes.medium)
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.document_pdf_error),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF495461),
                            textAlign = TextAlign.Center,
                        )
                        TextButton(onClick = { retry++ }) {
                            Text(text = retryDescription, color = Color(0xFF315E80))
                        }
                    }
                }
            }
        }
    }
}

/** Abre uma página por vez e fecha todos os recursos nativos fora da thread principal. */
private fun renderPage(context: Context, uri: String, pageIndex: Int, maxWidth: Int): Bitmap {
    val descriptor = requireNotNull(context.contentResolver.openFileDescriptor(uri.toUri(), "r"))
    return descriptor.use { file ->
        PdfRenderer(file).use { renderer ->
            require(pageIndex in 0 until renderer.pageCount)
            renderer.openPage(pageIndex).use { page ->
                val width = maxWidth
                val height = (width.toFloat() * page.height / page.width).toInt().coerceAtLeast(1)
                // Protege a memória caso um arquivo antigo tenha proporções inesperadas.
                require(height <= maxWidth * 4)
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                try {
                    bitmap.eraseColor(android.graphics.Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap
                } catch (error: Exception) {
                    bitmap.recycle()
                    throw error
                }
            }
        }
    }
}

private fun decodeThumbnail(context: Context, uri: String): Bitmap? {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri.toUri())?.use {
        BitmapFactory.decodeStream(it, null, options)
    }
    if (options.outWidth <= 0 || options.outHeight <= 0) return null
    options.inSampleSize = 1
    while (maxOf(options.outWidth, options.outHeight) / options.inSampleSize > 600) {
        options.inSampleSize *= 2
    }
    options.inJustDecodeBounds = false
    return context.contentResolver.openInputStream(uri.toUri())?.use {
        BitmapFactory.decodeStream(it, null, options)
    }
}

/** Ilustração exclusiva dos previews; em execução sempre usamos o arquivo persistido. */
@Composable
private fun DocumentPaperPreview(modifier: Modifier) {
    Canvas(modifier = modifier.background(Color.White)) {
        val left = size.width * 0.18f
        val top = size.height * 0.28f
        repeat(8) { line ->
            drawLine(
                color = if (line == 0) Color(0xFF63869B) else Color(0xFFCCD3D8),
                start = Offset(left, top + line * size.height * 0.055f),
                end = Offset(size.width * if (line == 7) 0.6f else 0.82f, top + line * size.height * 0.055f),
                strokeWidth = size.height * if (line == 0) 0.017f else 0.008f,
            )
        }
    }
}
