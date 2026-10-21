package com.joseleandro.scaniqo.data.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.joseleandro.scaniqo.R
import com.joseleandro.scaniqo.core.image.DocumentImageDecoder
import com.joseleandro.scaniqo.core.localization.appLocale
import com.joseleandro.scaniqo.core.localization.withLocale
import com.joseleandro.scaniqo.domain.model.DocumentModel
import com.joseleandro.scaniqo.domain.model.PdfCompressionLevel
import com.joseleandro.scaniqo.domain.model.PdfCompressionResult
import com.joseleandro.scaniqo.domain.model.PdfImageFormat
import com.joseleandro.scaniqo.domain.model.ReceiptDraft
import com.joseleandro.scaniqo.domain.model.ScannedDocument
import com.joseleandro.scaniqo.domain.repository.PdfUtilityRepository
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.UUID
import java.util.concurrent.Executor
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Ferramentas geram novos arquivos e nunca regravam a fonte. */
class AndroidPdfUtilityRepository(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : PdfUtilityRepository {
    private val app = context.applicationContext
    private val resolver = app.contentResolver

    override suspend fun compress(
        document: DocumentModel,
        level: PdfCompressionLevel,
        password: String?,
    ): PdfCompressionResult {
        var prepared: File? = null
        try {
            val result = withContext(ioDispatcher) {
                var originalBytes = 0L
                val scan = createDocument { target ->
                    prepared = target
                    withSourceSnapshot(document, password) { source, snapshot ->
                        originalBytes = snapshot.length()
                        PdfCompressionWriter.write(
                            source, target, level, password.takeIf { document.isProtected },
                        )
                        source.numberOfPages
                    }
                }.copy(protectionKeyId = document.protectionKeyId)
                if (scan.sizeBytes < originalBytes) {
                    PdfCompressionResult(scan, originalBytes, scan.sizeBytes)
                } else {
                    prepared?.let(::discardPrepared)
                    prepared = null
                    PdfCompressionResult(null, originalBytes, originalBytes)
                }
            }
            // A partir daqui, a tela confirma o cadastro ou descarta o arquivo preparado.
            prepared = null
            return result
        } catch (failure: Throwable) {
            withContext(NonCancellable + ioDispatcher) { prepared?.let(::discardPrepared) }
            throw failure
        }
    }

    private fun discardPrepared(file: File) {
        file.delete()
        file.parentFile?.delete()
    }

    override suspend fun extractPages(
        document: DocumentModel,
        pages: List<Int>,
        password: String?,
    ): ScannedDocument = createDocument { target ->
        withSource(document, password) { source ->
            validatePages(pages, source.numberOfPages)
            require(source.currentAccessPermission.canAssembleDocument()) {
                "A senha deste PDF não permite extrair páginas."
            }
            require(source.signatureDictionaries.isEmpty()) {
                "Este PDF possui assinatura digital. Use uma cópia sem assinatura para extrair páginas."
            }
            // A cópia local perde a interatividade dos campos, mantendo sua aparência preenchida.
            source.documentCatalog.acroForm?.flatten()
            val sourcePages = PdfPageIndex(source, currentCoroutineContext())
            PDDocument().use { output ->
                val copier = PdfExtractPageCopier(output)
                pages.forEach { index ->
                    currentCoroutineContext().ensureActive()
                    copier.copy(sourcePages[index])
                }
                if (document.isProtected) PdfProtection.apply(output, requireNotNull(password))
                output.save(target)
            }
        }
        pages.size
    }.copy(protectionKeyId = document.protectionKeyId)

    override suspend fun exportImages(
        document: DocumentModel,
        pages: List<Int>,
        format: PdfImageFormat,
        targetUri: String,
        password: String?,
    ) = withContext(ioDispatcher) {
        require(Uri.parse(targetUri).scheme == "content" && targetUri != document.pdfUri) {
            "Escolha um destino diferente do PDF original."
        }
        stageExport(targetUri, ".zip") { staged ->
            withRenderable(document, password) { file ->
                openRenderer(file).use { renderer ->
                    validatePages(pages, renderer.pageCount)
                    ZipOutputStream(staged.outputStream().buffered()).use { zip ->
                        pages.forEach { index ->
                            currentCoroutineContext().ensureActive()
                            val bitmap = render(renderer, index, 2000)
                            try {
                                zip.putNextEntry(ZipEntry("pagina-${index + 1}.${format.extension}"))
                                val encoding = if (format == PdfImageFormat.PNG) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
                                check(bitmap.compress(encoding, 94, zip)) { "Não foi possível converter a página." }
                                zip.closeEntry()
                                zip.flush()
                                require(staged.length() <= MAX_EXPORT_BYTES) { "As imagens excederam 200 MB. Exporte menos páginas por vez." }
                            } finally { bitmap.recycle() }
                        }
                    }
                }
            }
        }
    }

    override suspend fun imagesToPdf(imageUris: List<String>, idCard: Boolean): ScannedDocument =
        createDocument { target ->
            require(if (idCard) imageUris.size == 2 else imageUris.size in 1..20) {
                if (idCard) "Selecione a frente e o verso." else "Selecione entre 1 e 20 imagens."
            }
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            val output = PdfDocument()
            try {
                if (idCard) {
                    val page = output.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
                    try {
                        page.canvas.drawColor(Color.WHITE)
                        imageUris.forEachIndexed { index, uri ->
                            currentCoroutineContext().ensureActive()
                            val bitmap = decodeImage(uri)
                            try {
                                // Cada face cabe em 85,6 × 54 mm, sem distorção ou recorte.
                                val box = RectF(176.2f, 195f + index * 210f, 418.8f, 348.1f + index * 210f)
                                page.canvas.drawBitmap(bitmap, null, fit(bitmap.width, bitmap.height, box), paint)
                            } finally { bitmap.recycle() }
                        }
                    } finally { output.finishPage(page) }
                } else imageUris.forEachIndexed { index, uri ->
                    currentCoroutineContext().ensureActive()
                    val bitmap = decodeImage(uri)
                    try {
                        val page = output.startPage(PdfDocument.PageInfo.Builder(595, 842, index + 1).create())
                        try {
                            page.canvas.drawColor(Color.WHITE)
                            page.canvas.drawBitmap(bitmap, null, fit(bitmap.width, bitmap.height, RectF(24f, 24f, 571f, 818f)), paint)
                        } finally { output.finishPage(page) }
                    } finally { bitmap.recycle() }
                }
                target.outputStream().use(output::writeTo)
            } finally { output.close() }
            if (idCard) 1 else imageUris.size
        }

    override suspend fun readReceipt(document: DocumentModel, page: Int, password: String?): ReceiptDraft =
        withContext(ioDispatcher) {
            val embedded = withSource(document, password) { source ->
                validatePages(listOf(page), source.numberOfPages)
                PDFTextStripper().apply { startPage = page + 1; endPage = page + 1; sortByPosition = true }.getText(source)
            }
            currentCoroutineContext().ensureActive()
            val text = if (embedded.any(Char::isLetterOrDigit)) embedded else withRenderable(document, password) { file ->
                openRenderer(file).use { renderer ->
                    val bitmap = render(renderer, page, 2000)
                    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                    try {
                        // O SDK continua usando o bitmap até concluir a Task.
                        withContext(NonCancellable) {
                            recognizer.process(InputImage.fromBitmap(bitmap, 0)).awaitCompletion().text
                        }
                    } finally { recognizer.close(); bitmap.recycle() }
                }
            }
            currentCoroutineContext().ensureActive()
            require(text.isNotBlank()) { "Nenhum texto foi reconhecido. Tente uma digitalização mais nítida." }
            ReceiptTextParser.parse(text.take(100_000))
        }

    override suspend fun exportReceipt(receipt: ReceiptDraft, targetUri: String) = withContext(ioDispatcher) {
        require(Uri.parse(targetUri).scheme == "content") { "Selecione um destino válido." }
        val locale = AppCompatDelegate.getApplicationLocales()[0] ?: app.appLocale()
        val localized = app.withLocale(locale)
        val headers = ReceiptCsv.Headers(
            merchant = localized.getString(R.string.utility_receipt_merchant),
            date = localized.getString(R.string.utility_receipt_date),
            total = localized.getString(R.string.utility_receipt_total),
            item = localized.getString(R.string.utility_receipt_items),
        )
        val separator = ReceiptCsv.separatorFor(locale)
        stageExport(targetUri, ".csv") { staged ->
            staged.bufferedWriter(Charsets.UTF_8).use { it.write(ReceiptCsv.encode(receipt, headers, separator)) }
        }
    }

    /** O destino escolhido só recebe bytes depois que a conversão completa está pronta. */
    private suspend fun stageExport(targetUri: String, suffix: String, write: suspend (File) -> Unit) {
        val staged = File.createTempFile("utility-export-", suffix, app.cacheDir)
        try {
            write(staged)
            currentCoroutineContext().ensureActive()
            // Uma vez iniciada a cópia final, terminar evita um ZIP truncado por navegação/cancelamento.
            withContext(NonCancellable) {
                val output = resolver.openOutputStream(Uri.parse(targetUri), "wt")
                    ?: throw IOException("Não foi possível abrir o destino.")
                output.use { destination -> staged.inputStream().use { it.copyTo(destination) } }
            }
        } finally { staged.delete() }
    }

    private suspend fun createDocument(write: suspend (File) -> Int): ScannedDocument {
        var directory: File? = null
        try {
            return withContext(ioDispatcher) {
                currentCoroutineContext().ensureActive()
                val root = File(app.filesDir, "documents").apply { mkdirs() }
                val created = File(root, UUID.randomUUID().toString()).also {
                    check(it.mkdir()) { "Não foi possível preparar o documento." }
                    directory = it
                }
                val file = File(created, "document.pdf")
                val count = write(file)
                currentCoroutineContext().ensureActive()
                require(file.length() in 1..MAX_BYTES) { "O resultado excedeu o limite de 50 MB." }
                ScannedDocument(Uri.fromFile(file).toString(), count, System.currentTimeMillis(), file.length())
            }
        } catch (failure: Throwable) {
            withContext(NonCancellable + ioDispatcher) {
                directory?.let { File(it, "document.pdf").delete(); it.delete() }
            }
            throw failure
        }
    }

    private suspend fun <T> withSource(
        document: DocumentModel,
        password: String?,
        block: suspend (PDDocument) -> T,
    ): T = withSourceSnapshot(document, password) { pdf, _ -> block(pdf) }

    private suspend fun <T> withSourceSnapshot(
        document: DocumentModel,
        password: String?,
        block: suspend (PDDocument, File) -> T,
    ): T {
        require(!document.isProtected || !password.isNullOrEmpty()) { "Informe a senha deste PDF." }
        val temp = File.createTempFile("utility-source-", ".pdf", app.cacheDir)
        try {
            openInput(document.pdfUri).use { input ->
                temp.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        require(total <= MAX_BYTES) { "O PDF excede o limite de 50 MB." }
                        output.write(buffer, 0, read)
                    }
                }
            }
            val loaded = try {
                PDDocument.load(temp, password.orEmpty(), MemoryUsageSetting.setupTempFileOnly().setTempDir(app.cacheDir))
            }
            catch (_: InvalidPasswordException) { throw IllegalArgumentException("Senha incorreta. Confira a senha do PDF.") }
            return loaded.use { pdf ->
                require(pdf.numberOfPages in 1..100) { "O PDF deve ter entre 1 e 100 páginas." }
                require(pdf.isEncrypted == document.isProtected) { "A proteção do PDF não corresponde ao cadastro." }
                require(pdf.currentAccessPermission.canExtractContent()) { "Este PDF não permite extrair conteúdo." }
                block(pdf, temp)
            }
        } finally { temp.delete() }
    }

    private suspend fun <T> withRenderable(
        document: DocumentModel,
        password: String?,
        block: suspend (File) -> T,
    ): T = withSource(document, password) { source ->
        val decrypted = File.createTempFile("utility-render-", ".pdf", app.cacheDir)
        try {
            source.isAllSecurityToBeRemoved = true
            source.save(decrypted)
            currentCoroutineContext().ensureActive()
            block(decrypted)
        } finally { decrypted.delete() }
    }

    private fun openRenderer(file: File): PdfRenderer {
        val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        return try { PdfRenderer(descriptor) } catch (error: Throwable) { descriptor.close(); throw error }
    }

    private fun render(renderer: PdfRenderer, index: Int, maxSide: Int): Bitmap =
        renderer.openPage(index).use { page ->
            val scale = maxSide.toFloat() / maxOf(page.width, page.height)
            val bitmap = Bitmap.createBitmap(
                (page.width * scale).roundToInt().coerceAtLeast(1),
                (page.height * scale).roundToInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888,
            )
            try {
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                bitmap
            } catch (error: Throwable) { bitmap.recycle(); throw error }
        }

    private fun decodeImage(uri: String): Bitmap = DocumentImageDecoder.decode(2000) { openInput(uri) }

    private fun fit(width: Int, height: Int, box: RectF): RectF {
        val scale = minOf(box.width() / width, box.height() / height)
        val resultWidth = width * scale
        val resultHeight = height * scale
        return RectF(box.centerX() - resultWidth / 2, box.centerY() - resultHeight / 2,
            box.centerX() + resultWidth / 2, box.centerY() + resultHeight / 2)
    }

    private fun validatePages(pages: List<Int>, count: Int) {
        require(pages.isNotEmpty() && pages.size <= 100 && pages.distinct().size == pages.size &&
            pages.all { it in 0 until count }) { "A seleção de páginas não é válida." }
    }

    private fun openInput(uri: String): InputStream =
        resolver.openInputStream(Uri.parse(uri)) ?: throw IOException("O arquivo não está disponível.")

    private suspend fun <T> Task<T>.awaitCompletion(): T = suspendCoroutine { continuation ->
        addOnCompleteListener(Executor { it.run() }) { task ->
            if (task.isSuccessful) continuation.resume(task.result)
            else continuation.resumeWithException(task.exception ?: IOException("Não foi possível reconhecer o texto."))
        }
    }

    private companion object {
        const val MAX_BYTES = 50L * 1024 * 1024
        const val MAX_EXPORT_BYTES = 200L * 1024 * 1024
    }
}
