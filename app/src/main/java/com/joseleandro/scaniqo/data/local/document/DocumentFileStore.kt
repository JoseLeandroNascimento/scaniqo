package com.joseleandro.scaniqo.data.local.document

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.core.content.FileProvider
import com.joseleandro.scaniqo.core.pdf.A4PageLayout
import com.joseleandro.scaniqo.domain.model.DocumentModel
import com.joseleandro.scaniqo.domain.model.ScannedDocument
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

interface DocumentFileStore {
    suspend fun prepareScan(pageUris: List<String>): ScannedDocument
    suspend fun deleteOwnedDocument(pdfUri: String)
    suspend fun export(document: DocumentModel, targetUri: String)
    suspend fun prepareShare(document: DocumentModel): String
}

class AndroidDocumentFileStore(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : DocumentFileStore {

    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver
    private val documentsRoot get() = File(appContext.filesDir, "documents")

    override suspend fun prepareScan(pageUris: List<String>): ScannedDocument {
        require(pageUris.isNotEmpty() && pageUris.size <= MAX_SCAN_PAGES) {
            "Digitalize entre 1 e $MAX_SCAN_PAGES páginas."
        }
        var directory: File? = null
        try {
            return withContext(ioDispatcher) {
                currentCoroutineContext().ensureActive()
                val scanDirectory = createUniqueDirectory(documentsRoot).also { directory = it }
                val pdfFile = File(scanDirectory, PDF_FILE_NAME)
                val thumbnailFile = File(scanDirectory, THUMBNAIL_FILE_NAME)
                writePdf(pageUris, pdfFile, File(scanDirectory, "sources.pdf"))
                currentCoroutineContext().ensureActive()
                writeThumbnail(pdfFile, thumbnailFile)
                currentCoroutineContext().ensureActive()
                ScannedDocument(
                    pdfUri = Uri.fromFile(pdfFile).toString(),
                    pageCount = pageUris.size,
                    createdAt = System.currentTimeMillis(),
                    sizeBytes = pdfFile.length(),
                    thumbnailUri = Uri.fromFile(thumbnailFile).toString()
                )
            }
        } catch (failure: Throwable) {
            // A limpeza também ocorre se o cancelamento chegar ao retornar ao dispatcher da UI.
            cleanupAfterFailure(directory, listOf(PDF_FILE_NAME, THUMBNAIL_FILE_NAME), failure)
            throw failure
        }
    }

    override suspend fun deleteOwnedDocument(pdfUri: String) {
        withContext(ioDispatcher) {
            currentCoroutineContext().ensureActive()
            val directory = ownedDocumentDirectory(pdfUri) ?: return@withContext
            // Nunca removemos uma árvore arbitrária nem arquivos recebidos de outros aplicativos.
            cleanupCreatedDirectory(directory, listOf(PDF_FILE_NAME, THUMBNAIL_FILE_NAME))
        }
    }

    override suspend fun export(document: DocumentModel, targetUri: String) {
        withContext(ioDispatcher) {
            val source = Uri.parse(document.pdfUri)
            val target = Uri.parse(targetUri)
            require(target.scheme == "content" && target != source) {
                "Selecione um destino válido para salvar a cópia."
            }
            openSource(source).use { input ->
                val output = resolver.openOutputStream(target, "wt")
                    ?: throw IOException("Não foi possível abrir o destino selecionado.")
                output.use { copyCancellable(input, it) }
            }
        }
    }

    override suspend fun prepareShare(document: DocumentModel): String {
        var directory: File? = null
        var filename: String? = null
        try {
            return withContext(ioDispatcher) {
                currentCoroutineContext().ensureActive()
                val shareDirectory = createUniqueDirectory(
                    File(appContext.cacheDir, "shared_documents")
                ).also { directory = it }
                val safeName = safePdfFilename(document.name).also { filename = it }
                val copy = File(shareDirectory, safeName)
                openSource(Uri.parse(document.pdfUri)).use { input ->
                    copy.outputStream().use { output -> copyCancellable(input, output) }
                }
                FileProvider.getUriForFile(
                    appContext,
                    "${appContext.packageName}.files",
                    copy
                ).toString()
            }
        } catch (failure: Throwable) {
            cleanupAfterFailure(directory, listOfNotNull(filename), failure)
            throw failure
        }
    }

    private suspend fun writePdf(pageUris: List<String>, target: File, sources: File) {
        val document = PdfDocument()
        val originalPages = PdfDocument()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        try {
            pageUris.forEachIndexed { index, pageUri ->
                currentCoroutineContext().ensureActive()
                val bitmap = decodeBoundedBitmap(Uri.parse(pageUri))
                try {
                    // Mantemos a captura sem margens para que futuras edições não acumulem bordas.
                    val original = originalPages.startPage(
                        PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, index + 1).create()
                    )
                    try {
                        original.canvas.drawColor(Color.WHITE)
                        original.canvas.drawBitmap(bitmap, 0f, 0f, paint)
                    } finally {
                        originalPages.finishPage(original)
                    }
                    val pageInfo = PdfDocument.PageInfo.Builder(
                        A4PageLayout.PAGE_WIDTH,
                        A4PageLayout.PAGE_HEIGHT,
                        index + 1
                    ).create()
                    val page = document.startPage(pageInfo)
                    try {
                        page.canvas.drawColor(Color.WHITE)
                        val bounds = A4PageLayout.fit(bitmap.width, bitmap.height)
                        page.canvas.drawBitmap(
                            bitmap,
                            null,
                            RectF(bounds.left, bounds.top, bounds.right, bounds.bottom),
                            paint
                        )
                    } finally {
                        document.finishPage(page)
                    }
                } finally {
                    bitmap.recycle()
                }
            }
            currentCoroutineContext().ensureActive()
            target.outputStream().use { document.writeTo(it) }
            sources.outputStream().use { originalPages.writeTo(it) }
        } finally {
            try {
                document.close()
            } finally {
                originalPages.close()
            }
        }
    }

    private suspend fun decodeBoundedBitmap(uri: Uri): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        openSource(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw IOException("Uma das páginas digitalizadas não pôde ser lida.")
        }
        var sampleSize = 1
        val longestSide = max(bounds.outWidth, bounds.outHeight)
        while (longestSide.toLong() > MAX_BITMAP_SIDE.toLong() * sampleSize) {
            sampleSize *= 2
        }
        currentCoroutineContext().ensureActive()
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.RGB_565
            inScaled = false
        }
        return openSource(uri).use { BitmapFactory.decodeStream(it, null, options) }
            ?: throw IOException("Não foi possível decodificar a página digitalizada.")
    }

    private fun writeThumbnail(pdfFile: File, target: File) {
        val descriptor = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = try {
            PdfRenderer(descriptor)
        } catch (failure: Throwable) {
            descriptor.close()
            throw failure
        }
        // O renderer assume a propriedade do descritor e o fecha junto com o documento.
        renderer.use {
            it.openPage(0).use { page ->
                val height = (THUMBNAIL_WIDTH * page.height.toFloat() / page.width).roundToInt()
                val bitmap = Bitmap.createBitmap(
                    THUMBNAIL_WIDTH,
                    height.coerceAtLeast(1),
                    Bitmap.Config.ARGB_8888
                )
                try {
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    target.outputStream().use { output ->
                        if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 85, output)) {
                            throw IOException("Não foi possível criar a miniatura do documento.")
                        }
                    }
                } finally {
                    bitmap.recycle()
                }
            }
        }
    }

    private fun createUniqueDirectory(root: File): File {
        if (!root.isDirectory && !root.mkdirs()) {
            throw IOException("Não foi possível preparar o armazenamento do documento.")
        }
        val directory = File(root, UUID.randomUUID().toString())
        if (!directory.mkdir()) throw IOException("Não foi possível criar a pasta do documento.")
        return directory
    }

    private fun ownedDocumentDirectory(pdfUri: String): File? {
        val uri = Uri.parse(pdfUri)
        if (uri.scheme != "file") return null
        val file = uri.path?.let(::File)?.canonicalFile ?: return null
        if (file.name != PDF_FILE_NAME) return null
        val directory = file.parentFile ?: return null
        if (directory.parentFile != documentsRoot.canonicalFile) return null
        if (runCatching { UUID.fromString(directory.name) }.isFailure) return null
        return directory
    }

    private suspend fun cleanupAfterFailure(
        directory: File?,
        filenames: List<String>,
        originalFailure: Throwable
    ) {
        withContext(NonCancellable + ioDispatcher) {
            try {
                directory?.let { cleanupCreatedDirectory(it, filenames) }
            } catch (cleanupFailure: Exception) {
                // Preserva a causa original, inclusive CancellationException.
                originalFailure.addSuppressed(cleanupFailure)
            }
        }
    }

    private fun cleanupCreatedDirectory(directory: File, filenames: List<String>) {
        val projectFiles = if (directory.canonicalFile.parentFile == documentsRoot.canonicalFile) {
            listOf("sources.pdf", "recipe.json") + directory.listFiles().orEmpty()
                .filter { it.name.matches(Regex("signature_[a-fA-F0-9-]+\\.png")) }
                .map { it.name }
        } else emptyList()
        (filenames + projectFiles).distinct().forEach { name ->
            val file = File(directory, name)
            // A verificação canônica impede que um link simbólico alcance outro diretório.
            if (file.canonicalFile.parentFile == directory.canonicalFile) file.delete()
        }
        directory.delete()
    }

    private fun openSource(uri: Uri): InputStream =
        resolver.openInputStream(uri)
            ?: throw IOException("O arquivo do documento não está disponível.")

    private suspend fun copyCancellable(input: InputStream, output: OutputStream) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            currentCoroutineContext().ensureActive()
            val count = input.read(buffer)
            if (count < 0) break
            output.write(buffer, 0, count)
        }
        output.flush()
    }

    companion object {
        private const val MAX_SCAN_PAGES = 20
        private const val MAX_BITMAP_SIDE = 2000
        private const val THUMBNAIL_WIDTH = 420
        private const val PDF_FILE_NAME = "document.pdf"
        private const val THUMBNAIL_FILE_NAME = "thumbnail.jpg"

        /** Mantém nomes legíveis, sem permitir separadores, controles ou extensões duplicadas. */
        internal fun safePdfFilename(name: String): String {
            var stem = name.trim().map { character ->
                if (character.isISOControl() || character in "/\\:*?\"<>|") '_' else character
            }.joinToString("").trim(' ', '.')
            while (stem.endsWith(".pdf", ignoreCase = true)) {
                stem = stem.dropLast(4).trimEnd(' ', '.')
            }
            stem = stem.take(100).trimEnd(' ', '.').ifBlank { "documento" }
            return "$stem.pdf"
        }
    }
}
