package com.joseleandro.scaniqo.data.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Log
import com.joseleandro.scaniqo.data.local.document.DocumentFileStore
import com.joseleandro.scaniqo.domain.model.DocumentModel
import com.joseleandro.scaniqo.domain.model.PdfExportQuality
import com.joseleandro.scaniqo.domain.model.PdfFilterThumbnails
import com.joseleandro.scaniqo.domain.model.PdfCaptureSource
import com.joseleandro.scaniqo.domain.model.PdfInteractivePixels
import com.joseleandro.scaniqo.domain.model.PdfQualityReport
import com.joseleandro.scaniqo.domain.model.PdfImportException
import com.joseleandro.scaniqo.domain.model.PdfMergeLimitException
import com.joseleandro.scaniqo.domain.model.PdfMergePasswordRequiredException
import com.joseleandro.scaniqo.domain.model.PdfMergeSource
import com.joseleandro.scaniqo.domain.model.PdfPageSpec
import com.joseleandro.scaniqo.domain.model.PdfPagePreview
import com.joseleandro.scaniqo.domain.model.PdfPasswordRequiredException
import com.joseleandro.scaniqo.domain.model.PdfSignaturePlacement
import com.joseleandro.scaniqo.domain.model.PdfSignatureCrop
import com.joseleandro.scaniqo.domain.model.PdfSignatureImage
import com.joseleandro.scaniqo.domain.model.PdfWorkspace
import com.joseleandro.scaniqo.domain.model.ScannedDocument
import com.joseleandro.scaniqo.domain.repository.DocumentRepository
import com.joseleandro.scaniqo.domain.repository.PdfEditorRepository
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlin.math.roundToInt

class AndroidPdfEditorRepository(
    context: Context,
    private val documentRepository: DocumentRepository,
    private val documentFileStore: DocumentFileStore,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : PdfEditorRepository {
    private val files = PdfWorkspaceFiles(context)
    private val composer = PdfBoxComposer(files)
    private val filterThumbnailRenderer = PdfFilterThumbnailRenderer(files, composer)
    private val previews = PdfPreviewSnapshotCache()
    private val finalSnapshots = mutableMapOf<String, PdfFinalExportSnapshot>()
    private val sourceArchives = mutableMapOf<String, PdfSourceArchive>()
    private val sourcePageCounts = mutableMapOf<PdfSourceStamp, Int>()
    private val displayedPreviews = mutableMapOf<String, File>()
    private val pagePreviews = mutableMapOf<String, PdfSinglePagePreviewCache>()
    private val retainedPagePreviewUris = mutableMapOf<String, Set<String>>()
    private val json = Json { ignoreUnknownKeys = true }
    // Serializa acesso a fontes/previews para que a limpeza nunca concorra com uma gravação.
    private val operationMutex = Mutex()

    override suspend fun initialize() {
        withContext(ioDispatcher) {
            operationMutex.withLock { files.initialize() }
        }
    }

    override suspend fun open(document: DocumentModel, password: String?): PdfWorkspace {
        var created: File? = null
        try {
            return withContext(ioDispatcher) {
                operationMutex.withLock {
                    files.initialize()
                    if (document.isProtected && password.isNullOrBlank()) {
                        throw PdfPasswordRequiredException()
                    }
                    val directory = files.createWorkspace().also { created = it }
                    val project = files.ownedDocumentDirectory(document.pdfUri)
                    val original = project?.let { File(it, "sources.pdf") }?.takeIf {
                        it.isFile && it.canonicalFile.parentFile == project.canonicalFile
                    }
                    val recipe = project?.let(::readRecipe)
                    if (recipe != null && original == null) {
                        throw PdfImportException("As fontes originais deste documento não estão disponíveis.")
                    }
                    val source = File(directory, "source_${UUID.randomUUID()}.pdf")
                    val pageCount = if (original != null && recipe != null && !document.isProtected) {
                        // Fontes do projeto já foram validadas; não regravamos o PDF ao abrir.
                        files.copy(files.uri(original), source)
                        countPreviewPages(source)
                    } else {
                        val input = File(directory, "incoming.pdf")
                        files.copy(original?.let(files::uri) ?: document.pdfUri, input)
                        try {
                            composer.load(input, password).use { loaded ->
                                if (document.isProtected != loaded.isEncrypted) {
                                    throw PdfImportException("A proteção do arquivo não corresponde ao cadastro.")
                                }
                                composer.flattenVisualAnnotations(loaded)
                                loaded.isAllSecurityToBeRemoved = true
                                loaded.save(source)
                                loaded.numberOfPages
                            }
                        } finally {
                            input.delete()
                        }
                    }
                    files.checkSize(source)
                    sourcePageCounts[PdfSourceStamp.from(source)] = pageCount
                    val pages = if (recipe == null) {
                        (0 until pageCount).map { index ->
                            PdfPageSpec(UUID.randomUUID().toString(), files.uri(source), index)
                        }
                    } else {
                        restorePages(recipe, requireNotNull(project), directory, source, pageCount)
                    }
                    currentCoroutineContext().ensureActive()
                    val workspace = PdfWorkspace(
                        documentId = document.id,
                        workspaceId = directory.name,
                        directoryUri = files.uri(directory),
                        pages = pages,
                        showPageNumbers = recipe?.showPageNumbers ?: false,
                        exportQuality = recipe?.exportQuality ?: PdfExportQuality.HIGH_QUALITY,
                        ocrEnabled = recipe?.ocrEnabled ?: false,
                        isProtected = document.isProtected,
                        protectionKeyId = document.protectionKeyId
                    )
                    if (original != null && recipe != null) {
                        seedPreview(document.pdfUri, document.isProtected, workspace, password)
                    }
                    workspace
                }
            }
        } catch (failure: Throwable) {
            cleanup(failure) {
                operationMutex.withLock {
                    created?.let {
                        previews.clear(it.name)
                        finalSnapshots.remove(it.name)
                        clearSourceArchive(it)
                        files.deleteWorkspace(it)
                    }
                }
            }
            throw failure
        }
    }

    override suspend fun importPdf(
        uri: String,
        password: String?,
        protectionKeyId: String?
    ): ScannedDocument {
        var workspaceDirectory: File? = null
        var project: File? = null
        var generated: ScannedDocument? = null
        try {
            return withContext(ioDispatcher) {
                operationMutex.withLock {
                    files.initialize()
                    val directory = files.createWorkspace().also { workspaceDirectory = it }
                    val incoming = File(directory, "incoming.pdf")
                    files.copy(uri, incoming)
                    val source = File(directory, "source_${UUID.randomUUID()}.pdf")
                    var protected = false
                    val pageCount = composer.load(incoming, password).use { loaded ->
                        protected = loaded.isEncrypted
                        if (protected && protectionKeyId.isNullOrBlank()) {
                            throw PdfImportException("Cadastre a senha deste PDF antes de importá-lo.")
                        }
                        composer.flattenVisualAnnotations(loaded)
                        loaded.isAllSecurityToBeRemoved = true
                        loaded.save(source)
                        loaded.numberOfPages
                    }
                    incoming.delete()
                    val workspace = PdfWorkspace(
                        documentId = 0,
                        workspaceId = directory.name,
                        directoryUri = files.uri(directory),
                        pages = (0 until pageCount).map {
                            PdfPageSpec(UUID.randomUUID().toString(), files.uri(source), it)
                        },
                        isProtected = protected,
                        protectionKeyId = protectionKeyId.takeIf { protected }
                    )
                    val target = files.createDocument().also { project = it }
                    writeProject(workspace, target, password.takeIf { protected })
                        .also { generated = it }
                }
            }
        } catch (failure: Throwable) {
            cleanup(failure) {
                generated?.let { documentRepository.discardScan(it) }
                    ?: project?.let { documentFileStore.deleteOwnedDocument(files.uri(File(it, "document.pdf"))) }
            }
            throw failure
        } finally {
            cleanupTemporary {
                operationMutex.withLock {
                    workspaceDirectory?.let { directory ->
                        clearSourceArchive(directory)
                        files.deleteWorkspace(directory)
                    }
                }
            }
        }
    }

    override suspend fun appendScans(
        workspace: PdfWorkspace,
        pageUris: List<String>
    ): PdfWorkspace {
        require(pageUris.size in 1..20) { "Digitalize entre 1 e 20 páginas por vez." }
        require(workspace.pages.size + pageUris.size <= PdfWorkspaceFiles.MAX_PAGES) {
            "O documento pode conter até 100 páginas."
        }
        var copied: File? = null
        var draft: ScannedDocument? = null
        try {
            return withContext(ioDispatcher) {
                operationMutex.withLock {
                    files.initialize()
                    val directory = files.directory(workspace)
                    val scan = documentFileStore.prepareScan(pageUris).also { draft = it }
                    val sourceDirectory = requireNotNull(files.ownedDocumentDirectory(scan.pdfUri))
                    val raw = File(sourceDirectory, "sources.pdf")
                    check(raw.isFile) { "As páginas digitalizadas não estão disponíveis." }
                    val target = File(directory, "source_${UUID.randomUUID()}.pdf").also { copied = it }
                    val existingSize = workspace.pages.map { it.sourceUri }.distinct()
                        .sumOf { files.source(workspace, it).length() }
                    if (existingSize + raw.length() > PdfWorkspaceFiles.MAX_BYTES) {
                        throw PdfImportException("As fontes do documento excedem 50 MB.")
                    }
                    files.copy(files.uri(raw), target)
                    workspace.copy(
                        pages = workspace.pages + (0 until scan.pageCount).map {
                            PdfPageSpec(UUID.randomUUID().toString(), files.uri(target), it)
                        }
                    )
                }
            }
        } catch (failure: Throwable) {
            cleanup(failure) { copied?.delete() }
            throw failure
        } finally {
            cleanupTemporary { draft?.let { documentFileStore.deleteOwnedDocument(it.pdfUri) } }
        }
    }

    override suspend fun mergePdfs(sources: List<PdfMergeSource>): ScannedDocument {
        require(sources.size >= 2) { "Selecione pelo menos dois PDFs." }
        var temporary: PdfWorkspace? = null
        var project: File? = null
        var generated: ScannedDocument? = null
        try {
            return withContext(ioDispatcher) {
                operationMutex.withLock {
                    files.initialize()
                    val directory = files.createWorkspace()
                    val empty = PdfWorkspace(0, directory.name, files.uri(directory), emptyList())
                        .also { temporary = it }
                    val merged = PdfMergeBatch(directory, files::uri)
                        .append(empty, sources, 0L, prepare = ::prepareMergedSource)
                    temporary = merged
                    val target = files.createDocument().also { project = it }
                    writeProject(merged, target, password = null).also {
                        generated = it
                        currentCoroutineContext().ensureActive()
                    }
                }
            }
        } catch (failure: Throwable) {
            cleanup(failure) {
                generated?.let { documentRepository.discardScan(it) }
                    ?: project?.let { documentFileStore.deleteOwnedDocument(files.uri(File(it, "document.pdf"))) }
            }
            throw failure
        } finally {
            cleanupTemporary { temporary?.let { discard(it) } }
        }
    }

    override suspend fun appendPdfs(
        workspace: PdfWorkspace,
        sources: List<PdfMergeSource>
    ): PdfWorkspace = appendPdfBatch(workspace, sources, currentDocumentIndex = 0)

    override suspend fun appendPdfsOrdered(
        workspace: PdfWorkspace,
        sources: List<PdfMergeSource>,
        currentDocumentIndex: Int,
    ): PdfWorkspace = appendPdfBatch(workspace, sources, currentDocumentIndex)

    private suspend fun appendPdfBatch(
        workspace: PdfWorkspace,
        sources: List<PdfMergeSource>,
        currentDocumentIndex: Int,
    ): PdfWorkspace {
        var batch: PdfMergeBatch? = null
        try {
            return withContext(ioDispatcher) {
                operationMutex.withLock {
                    files.initialize()
                    val directory = files.directory(workspace)
                    val existingBytes = workspace.pages.map { it.sourceUri }.distinct()
                        .sumOf { files.source(workspace, it).length() }
                    val transaction = PdfMergeBatch(directory, files::uri).also { batch = it }
                    transaction.append(workspace, sources, existingBytes, currentDocumentIndex) { preparation ->
                        prepareMergedSource(preparation)
                    }
                }
            }
        } catch (failure: Throwable) {
            cleanup(failure) { batch?.rollback() }
            throw failure
        }
    }

    private suspend fun prepareMergedSource(preparation: PdfMergeBatch.Preparation): Int {
        val source = preparation.source
        files.copy(source.uri, preparation.incoming, preparation.remainingBytes) {
            PdfMergeLimitException(PdfMergeLimitException.Reason.TOTAL_BYTES)
        }
        currentCoroutineContext().ensureActive()
        val loaded = try {
            composer.load(
                preparation.incoming,
                source.password,
                preparation.remainingPages,
                allowEmptyPassword = true,
            ) {
                PdfMergeLimitException(PdfMergeLimitException.Reason.PAGE_COUNT)
            }
        } catch (_: PdfPasswordRequiredException) {
            throw PdfMergePasswordRequiredException(source.uri)
        }
        return loaded.use { document ->
            currentCoroutineContext().ensureActive()
            val access = document.currentAccessPermission
            if (!access.canAssembleDocument() || !access.canExtractContent()) {
                throw PdfImportException("A senha deste PDF não permite copiar páginas para outro documento.")
            }
            composer.flattenVisualAnnotations(document)
            currentCoroutineContext().ensureActive()
            // A fonte em cache é privada; o PDF final segue a proteção do documento aberto.
            document.isAllSecurityToBeRemoved = true
            val context = currentCoroutineContext()
            preparation.output.outputStream().use { output ->
                document.save(PdfCancellableOutputStream(output, context, preparation.remainingBytes) {
                    PdfMergeLimitException(PdfMergeLimitException.Reason.TOTAL_BYTES)
                })
            }
            currentCoroutineContext().ensureActive()
            document.numberOfPages
        }
    }

    override suspend fun filterThumbnails(
        workspace: PdfWorkspace,
        pageId: String,
        rasterizeText: Boolean,
        force: Boolean,
    ): PdfFilterThumbnails = withContext(ioDispatcher) {
        operationMutex.withLock {
            files.initialize()
            filterThumbnailRenderer.render(workspace, pageId, rasterizeText, force)
        }
    }

    override suspend fun analyze(workspace: PdfWorkspace): PdfQualityReport = withContext(ioDispatcher) {
        operationMutex.withLock {
            files.initialize()
            files.directory(workspace)
            PdfQualityAnalyzer(files, composer).analyze(workspace)
        }
    }

    override suspend fun copySignature(workspace: PdfWorkspace, imageUri: String): String {
        var target: File? = null
        try {
            return withContext(ioDispatcher) {
                operationMutex.withLock {
                    files.initialize()
                    val copy = File(files.directory(workspace), "signature_${UUID.randomUUID()}.png")
                        .also { target = it }
                    files.copy(imageUri, copy, 8L * 1024 * 1024)
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(copy.path, bounds)
                    require(bounds.outWidth > 0 && bounds.outHeight > 0 &&
                        bounds.outMimeType == "image/png") { "Selecione uma assinatura PNG válida." }
                    currentCoroutineContext().ensureActive()
                    files.uri(copy)
                }
            }
        } catch (failure: Throwable) {
            cleanup(failure) { target?.delete() }
            throw failure
        }
    }

    override suspend fun cropSignature(
        workspace: PdfWorkspace,
        imageUri: String,
        crop: PdfSignatureCrop,
    ): PdfSignatureImage {
        var target: File? = null
        try {
            return withContext(ioDispatcher) {
                operationMutex.withLock {
                    files.initialize()
                    val directory = files.directory(workspace)
                    val source = files.file(imageUri)
                    require(source.parentFile == directory.canonicalFile) { "A assinatura não pertence à edição." }
                    require(crop.isValid) { "Selecione uma área válida da assinatura." }
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(source.path, bounds)
                    require(bounds.outWidth > 0 && bounds.outHeight > 0) { "A assinatura não está disponível." }
                    var sample = 1
                    while (maxOf(bounds.outWidth, bounds.outHeight).toLong() > 2048L * sample) sample *= 2
                    currentCoroutineContext().ensureActive()
                    val bitmap = BitmapFactory.decodeFile(
                        source.path, BitmapFactory.Options().apply { inSampleSize = sample },
                    ) ?: error("Não foi possível ler a assinatura.")
                    var cropped: Bitmap? = null
                    try {
                        val area = crop.pixels(bitmap.width, bitmap.height)
                        val result = Bitmap.createBitmap(bitmap, area.x, area.y, area.width, area.height)
                            .also { cropped = it }
                        val output = File(directory, "signature_${UUID.randomUUID()}.png").also { target = it }
                        output.outputStream().use { stream ->
                            check(result.compress(Bitmap.CompressFormat.PNG, 100, stream)) { "Não foi possível recortar a assinatura." }
                        }
                        currentCoroutineContext().ensureActive()
                        PdfSignatureImage(files.uri(output), result.width.toFloat() / result.height)
                    } finally {
                        cropped?.takeUnless { it === bitmap }?.recycle()
                        bitmap.recycle()
                    }
                }
            }
        } catch (failure: Throwable) {
            cleanup(failure) { target?.delete() }
            throw failure
        }
    }

    override suspend fun preview(
        workspace: PdfWorkspace,
        retainedPreviewUri: String?
    ): String {
        var target: File? = null
        try {
            return withContext(ioDispatcher) {
                operationMutex.withLock {
                    files.initialize()
                    files.directory(workspace)
                    previews.find(workspace)?.let { cached ->
                        files.retainRecentPreviews(
                            workspace, cached, retainedPreviewUri,
                            retainedSnapshotUris(workspace)
                        )
                        displayedPreviews[workspace.workspaceId] = cached
                        return@withLock files.uri(cached)
                    }
                    val preview = File(
                        files.directory(workspace), "preview_${UUID.randomUUID()}.pdf"
                    ).also { target = it }
                    composer.compose(workspace, preview, reusablePreview = previews.reusable(workspace))
                    currentCoroutineContext().ensureActive()
                    files.retainRecentPreviews(
                        workspace, preview, retainedPreviewUri,
                        retainedSnapshotUris(workspace)
                    )
                    previews.remember(workspace, preview)
                    displayedPreviews[workspace.workspaceId] = preview
                    files.uri(preview)
                }
            }
        } catch (failure: Throwable) {
            cleanup(failure) { target?.delete() }
            throw failure
        }
    }

    override suspend fun renderCaptureSource(
        workspace: PdfWorkspace, pageId: String, maxSide: Int,
    ): PdfCaptureSource? = withContext(ioDispatcher) {
        operationMutex.withLock {
            files.initialize()
            currentCoroutineContext().ensureActive()
            composer.renderCaptureSource(workspace, pageId, maxSide)
        }
    }

    override suspend fun renderCaptureResult(
        workspace: PdfWorkspace, pageId: String, maxSide: Int,
    ): PdfInteractivePixels? = withContext(ioDispatcher) {
        operationMutex.withLock {
            files.initialize()
            currentCoroutineContext().ensureActive()
            composer.renderCaptureResult(workspace, pageId, maxSide)
        }
    }

    override suspend fun renderInteractivePixels(
        workspace: PdfWorkspace,
        pageId: String,
        maxSide: Int,
    ): PdfInteractivePixels = withContext(ioDispatcher) {
        operationMutex.withLock {
            files.initialize()
            currentCoroutineContext().ensureActive()
            composer.renderInteractivePixels(workspace, pageId, maxSide)
        }
    }

    override suspend fun renderPagePreview(
        workspace: PdfWorkspace,
        pageId: String,
        retainedPreviewUris: Set<String>,
    ): PdfPagePreview = withContext(ioDispatcher) {
        operationMutex.withLock {
            files.initialize()
            val directory = files.directory(workspace)
            val spec = workspace.pages.firstOrNull { it.id == pageId }
                ?: throw IllegalArgumentException("A página não está mais disponível.")
            currentCoroutineContext().ensureActive()
            // Só arquivos da sessão podem impedir a limpeza; nunca aceitamos caminhos externos.
            val retainedFiles = retainedPreviewUris.mapNotNull { uri ->
                runCatching { files.file(uri) }.getOrNull()?.takeIf {
                    it.parentFile == directory && it.isFile &&
                        (it.name.startsWith("preview_") || it.name.startsWith("page_preview_") ||
                            it.name.startsWith("final_preview_")) &&
                        it.name.endsWith(".pdf")
                }
            }.toSet()
            retainedPagePreviewUris[workspace.workspaceId] = retainedFiles.mapTo(mutableSetOf(), files::uri)
            previews.findPage(workspace, pageId)?.let { (file, index) ->
                require(file.canonicalFile.parentFile == directory) { "A sessão de edição expirou." }
                val uri = files.uri(file)
                retainedPagePreviewUris[workspace.workspaceId] =
                    retainedPagePreviewUris[workspace.workspaceId].orEmpty() + uri
                files.retainRecentPreviews(
                    workspace, file, null, retainedSnapshotUris(workspace)
                )
                return@withLock PdfPagePreview(uri, index)
            }
            val key = PdfSinglePagePreviewKey.from(
                workspace, pageId, files.source(workspace, spec.sourceUri)
            )
            val cache = pagePreviews.getOrPut(workspace.workspaceId) {
                PdfSinglePagePreviewCache(directory)
            }
            val rendered = cache.getOrCreate(key, retainedFiles) { target ->
                composer.composePagePreview(workspace, pageId, target)
            }
            val uri = files.uri(rendered)
            retainedPagePreviewUris[workspace.workspaceId] =
                retainedPagePreviewUris[workspace.workspaceId].orEmpty() + uri
            files.retainRecentPreviews(
                workspace, rendered, null, retainedSnapshotUris(workspace)
            )
            PdfPagePreview(uri, 0)
        }
    }

    override suspend fun clearPreview(workspace: PdfWorkspace) {
        withContext(ioDispatcher) {
            operationMutex.withLock {
                files.initialize()
                previews.clear(workspace.workspaceId)
                finalSnapshots.remove(workspace.workspaceId)
                clearSourceArchive(files.file(workspace.directoryUri))
                displayedPreviews.remove(workspace.workspaceId)
                retainedPagePreviewUris.remove(workspace.workspaceId)
                pagePreviews.remove(workspace.workspaceId)?.clear()
                composer.clearPreviewCache(workspace)
                filterThumbnailRenderer.clear(workspace)
                if (files.file(workspace.directoryUri).exists()) files.clearPreviews(workspace)
            }
        }
    }

    override suspend fun save(
        workspace: PdfWorkspace,
        password: String?,
        protectionKeyId: String?,
        removeProtection: Boolean
    ): DocumentModel {
        var project: File? = null
        var generated: ScannedDocument? = null
        var preparedPreview: File? = null
        var previousSnapshot: PdfFinalExportSnapshot? = null
        try {
            return withContext(ioDispatcher) {
                operationMutex.withLock {
                    files.initialize()
                    files.directory(workspace)
                    val stored = checkNotNull(documentRepository.findById(workspace.documentId)) {
                        "O documento foi removido."
                    }
                    if (removeProtection) {
                        require(!workspace.isProtected && stored.isProtected) {
                            "Confirme a remoção da proteção deste documento."
                        }
                        verifyProtection(stored, workspace, password)
                    }
                    val keyId = if (removeProtection) null else
                        protectionKeyId ?: workspace.protectionKeyId ?: stored.protectionKeyId
                    val protected = !removeProtection &&
                        (workspace.isProtected || stored.isProtected || keyId != null)
                    if (protected && password.isNullOrBlank()) throw PdfPasswordRequiredException()
                    require(!protected || !keyId.isNullOrBlank()) {
                        "Cadastre a senha antes de salvar o documento protegido."
                    }
                    val secured = workspace.copy(
                        isProtected = protected,
                        protectionKeyId = keyId.takeIf { protected }
                    )
                    val target = files.createDocument().also { project = it }
                    previousSnapshot = finalSnapshots[workspace.workspaceId]
                    val ready = File(files.directory(workspace), "final_preview_${UUID.randomUUID()}.pdf")
                        .also { preparedPreview = it }
                    val result = writeProject(secured, target, password.takeIf { protected }, ready)
                        .also { generated = it }
                    currentCoroutineContext().ensureActive()
                    seedPreview(result.pdfUri, protected, secured, password.takeIf { protected }, ready)
                    documentRepository.replaceContent(
                        workspace.documentId, result, allowProtectionRemoval = removeProtection
                    )
                }
            }
        } catch (failure: Throwable) {
            cleanup(failure) {
                operationMutex.withLock {
                    // O snapshot é privado e não foi adotado pela interface quando o salvamento falha.
                    preparedPreview?.let { preview ->
                        if (finalSnapshots[workspace.workspaceId]?.file == preview) {
                            val previous = previousSnapshot?.takeIf { it.file.isFile }
                            if (previous == null) finalSnapshots.remove(workspace.workspaceId)
                            else finalSnapshots[workspace.workspaceId] = previous
                        }
                        preview.delete()
                    }
                }
                // Consultar o banco protege uma confirmação concluída antes do cancelamento.
                generated?.let { documentRepository.discardScan(it) }
                    ?: project?.let { documentFileStore.deleteOwnedDocument(files.uri(File(it, "document.pdf"))) }
            }
            throw failure
        }
    }

    override suspend fun discard(workspace: PdfWorkspace) {
        withContext(NonCancellable + ioDispatcher) {
            operationMutex.withLock {
                files.initialize()
                previews.clear(workspace.workspaceId)
                finalSnapshots.remove(workspace.workspaceId)
                clearSourceArchive(files.file(workspace.directoryUri))
                displayedPreviews.remove(workspace.workspaceId)
                retainedPagePreviewUris.remove(workspace.workspaceId)
                pagePreviews.remove(workspace.workspaceId)?.clear()
                composer.clearPreviewCache(workspace)
                filterThumbnailRenderer.clear(workspace)
                val directory = files.file(workspace.directoryUri)
                if (directory.exists()) {
                    files.directory(workspace)
                    files.deleteWorkspace(directory)
                }
            }
        }
    }

    private suspend fun seedPreview(
        pdfUri: String,
        protected: Boolean,
        workspace: PdfWorkspace,
        password: String?,
        preparedFinal: File? = null,
    ) {
        val directory = files.directory(workspace)
        val preview = preparedFinal ?: File(directory, "final_preview_${UUID.randomUUID()}.pdf")
        require(preview.canonicalFile.parentFile == directory &&
            preview.name.startsWith("final_preview_") && preview.name.endsWith(".pdf"))
        val incoming = File(directory, "saved_${UUID.randomUUID()}.pdf")
        try {
            if (preparedFinal != null) {
                // O compositor já produziu a saída final privada antes de criptografar a cópia persistida.
                require(preparedFinal.isFile)
            } else if (!protected) {
                files.copy(pdfUri, preview)
            } else {
                // A prévia protegida nunca aponta para as fontes: usa o PDF final autenticado.
                files.copy(pdfUri, incoming)
                composer.load(incoming, password).use { loaded ->
                    require(loaded.isEncrypted) { "A proteção do arquivo não corresponde ao cadastro." }
                    loaded.isAllSecurityToBeRemoved = true
                    val context = currentCoroutineContext()
                    preview.outputStream().use { stream ->
                        loaded.save(PdfCancellableOutputStream(stream, context, PdfWorkspaceFiles.MAX_BYTES) {
                            PdfImportException("O PDF deve ter no máximo 50 MB.")
                        })
                    }
                }
            }
            currentCoroutineContext().ensureActive()
            require(countPreviewPages(preview) == workspace.pages.size) {
                "O PDF salvo não corresponde às páginas do projeto."
            }
            finalSnapshots[workspace.workspaceId] = PdfFinalExportSnapshot.capture(
                workspace, preview, workspace.pages.map { it.sourceUri }.distinct()
                    .associateWith { files.source(workspace, it) }
            )
            files.retainRecentPreviews(
                workspace, preview, displayedPreviews[workspace.workspaceId]?.let(files::uri),
                retainedSnapshotUris(workspace)
            )
            previews.remember(workspace, preview)
        } catch (failure: Throwable) {
            preview.delete()
            throw failure
        } finally {
            incoming.delete()
        }
    }

    private fun countPreviewPages(file: File): Int {
        val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = try {
            PdfRenderer(descriptor)
        } catch (failure: Throwable) {
            descriptor.close()
            throw failure
        }
        return renderer.use {
            it.pageCount.also { count ->
                require(count in 1..PdfWorkspaceFiles.MAX_PAGES) {
                    "O documento deve conter entre 1 e 100 páginas."
                }
            }
        }
    }

    private suspend fun verifyProtection(
        document: DocumentModel,
        workspace: PdfWorkspace,
        password: String?
    ) {
        if (password.isNullOrBlank()) throw PdfPasswordRequiredException()
        val copy = File(files.directory(workspace), "authentication_${UUID.randomUUID()}.pdf")
        try {
            files.copy(document.pdfUri, copy)
            composer.load(copy, password).use { stored ->
                require(stored.isEncrypted) { "O arquivo não corresponde ao documento protegido." }
            }
        } finally {
            copy.delete()
        }
    }

    private suspend fun writeProject(
        workspace: PdfWorkspace,
        directory: File,
        password: String?,
        preparedFinal: File? = null,
    ): ScannedDocument {
        val copiedSignatures = mutableMapOf<String, String>()
        for (page in workspace.pages) {
            for (signature in page.signatures) {
                currentCoroutineContext().ensureActive()
                if (signature.imageUri !in copiedSignatures) {
                    val name = "signature_${UUID.randomUUID()}.png"
                    files.copy(signature.imageUri, File(directory, name), 8L * 1024 * 1024)
                    copiedSignatures[signature.imageUri] = name
                }
            }
        }
        val (archive, indices) = prepareSourceArchive(workspace)
        composer.saveCopy(archive, File(directory, "sources.pdf"), password)
        val pdf = File(directory, "document.pdf")
        // A comparação usa as URIs estáveis da sessão, não os novos nomes das cópias de assinaturas.
        composer.compose(
            workspace, preparedFinal ?: pdf,
            password = password.takeIf { preparedFinal == null }, forExport = true,
            reusableFinal = finalSnapshots[workspace.workspaceId]
        )
        if (preparedFinal != null) composer.saveCopy(preparedFinal, pdf, password)
        val recipe = PdfProjectRecipe(
            pages = workspace.pages.mapIndexed { index, page ->
                RecipePage(page.id, indices[index], page.layout, page.signatures.map {
                    RecipeSignature(
                        it.id, requireNotNull(copiedSignatures[it.imageUri]), it.x, it.y, it.width, it.height,
                        it.rotationDegrees, it.stretchToBounds
                    )
                })
            },
            showPageNumbers = workspace.showPageNumbers,
            exportQuality = workspace.exportQuality,
            ocrEnabled = workspace.ocrEnabled,
        )
        File(directory, "recipe.json").writeText(json.encodeToString(recipe))
        currentCoroutineContext().ensureActive()
        // Documentos protegidos não deixam miniaturas legíveis no armazenamento permanente.
        val thumbnail = if (workspace.isProtected) null else createThumbnail(pdf, directory)
        return ScannedDocument(
            pdfUri = files.uri(pdf),
            pageCount = workspace.pages.size,
            createdAt = System.currentTimeMillis(),
            sizeBytes = pdf.length(),
            thumbnailUri = thumbnail?.let(files::uri),
            protectionKeyId = workspace.protectionKeyId
        )
    }

    private suspend fun prepareSourceArchive(workspace: PdfWorkspace): Pair<File, List<Int>> {
        val directory = files.directory(workspace)
        val sources = workspace.pages.map { it.sourceUri }.distinct()
            .associateWith { files.source(workspace, it) }
        if (sources.values.sumOf { it.length() } > PdfWorkspaceFiles.MAX_BYTES) {
            throw PdfImportException("As fontes do documento excedem 50 MB.")
        }
        val previous = sourceArchives[workspace.workspaceId]
        previous?.indices(workspace, sources)?.let { return previous.file to it }
        val single = sources.values.singleOrNull()?.let { source ->
            val count = sourcePageCounts.getOrPut(PdfSourceStamp.from(source)) {
                countPreviewPages(source)
            }
            PdfSourceArchive.single(workspace, sources, count)
        }
        if (single != null) {
            sourceArchives[workspace.workspaceId] = single
            deleteGeneratedArchive(previous?.file, directory)
            return single.file to requireNotNull(single.indices(workspace, sources))
        }
        val target = File(directory, "source_archive_${UUID.randomUUID()}.pdf")
        try {
            // Mesclagem/exclusão reconsolidam as fontes uma vez. Ajustes posteriores apenas as copiam.
            composer.compose(workspace, target, originalsOnly = true)
            currentCoroutineContext().ensureActive()
            val archive = PdfSourceArchive.composed(workspace, target, sources)
            sourceArchives[workspace.workspaceId] = archive
            deleteGeneratedArchive(previous?.file, directory)
            return target to requireNotNull(archive.indices(workspace, sources))
        } catch (failure: Throwable) {
            target.delete()
            throw failure
        }
    }

    private fun retainedSnapshotUris(workspace: PdfWorkspace): Set<String> =
        retainedPagePreviewUris[workspace.workspaceId].orEmpty() +
            listOfNotNull(finalSnapshots[workspace.workspaceId]?.file?.let(files::uri))

    private fun clearSourceArchive(directory: File) {
        deleteGeneratedArchive(sourceArchives.remove(directory.name)?.file, directory)
        sourcePageCounts.entries.removeAll { File(it.key.path).parentFile == directory.canonicalFile }
    }

    private fun deleteGeneratedArchive(file: File?, directory: File) {
        if (file != null && file.name.startsWith("source_archive_") &&
            file.name.endsWith(".pdf") && file.canonicalFile.parentFile == directory.canonicalFile
        ) file.delete()
    }

    private fun readRecipe(directory: File): PdfProjectRecipe? {
        val recipe = File(directory, "recipe.json")
        if (!recipe.isFile) return null
        require(recipe.length() <= 4L * 1024 * 1024) { "A receita do documento é inválida." }
        return json.decodeFromString<PdfProjectRecipe>(recipe.readText()).also {
            it.validate()
        }
    }

    private suspend fun restorePages(
        recipe: PdfProjectRecipe,
        project: File,
        directory: File,
        source: File,
        sourcePageCount: Int
    ): List<PdfPageSpec> {
        val copies = mutableMapOf<String, String>()
        return recipe.pages.map { page ->
            require(page.sourcePageIndex in 0 until sourcePageCount)
            PdfPageSpec(
                page.id, files.uri(source), page.sourcePageIndex, page.layout,
                page.signatures.map { signature ->
                    require(signature.filename.matches(Regex("signature_[a-fA-F0-9-]+\\.png")))
                    val uri = copies[signature.filename] ?: run {
                        val input = File(project, signature.filename).canonicalFile
                        require(input.parentFile == project.canonicalFile && input.isFile) {
                            "Uma assinatura deste documento não está disponível."
                        }
                        val copy = File(directory, signature.filename)
                        files.copy(files.uri(input), copy, 8L * 1024 * 1024)
                        files.uri(copy).also { copies[signature.filename] = it }
                    }
                    PdfSignaturePlacement(
                        signature.id, uri, signature.x, signature.y, signature.width, signature.height,
                        signature.rotationDegrees, signature.stretchToBounds
                    )
                }
            )
        }
    }

    private fun createThumbnail(pdf: File, directory: File): File {
        val descriptor = ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = try {
            PdfRenderer(descriptor)
        } catch (failure: Throwable) {
            descriptor.close()
            throw failure
        }
        val target = File(directory, "thumbnail.jpg")
        renderer.use {
            it.openPage(0).use { page ->
                val height = (420f * page.height / page.width).roundToInt().coerceAtLeast(1)
                val bitmap = Bitmap.createBitmap(420, height, Bitmap.Config.ARGB_8888)
                try {
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    target.outputStream().use { output ->
                        check(bitmap.compress(Bitmap.CompressFormat.JPEG, 85, output))
                    }
                } finally {
                    bitmap.recycle()
                }
            }
        }
        return target
    }

    private suspend fun cleanupTemporary(action: suspend () -> Unit) {
        withContext(NonCancellable + ioDispatcher) {
            try {
                action()
            } catch (cleanupFailure: Exception) {
                // A limpeza de temporários não mascara a falha original nem invalida o resultado.
                Log.w("PdfEditor", "Não foi possível limpar um arquivo temporário.", cleanupFailure)
            }
        }
    }

    private suspend fun cleanup(failure: Throwable, action: suspend () -> Unit) {
        withContext(NonCancellable + ioDispatcher) {
            try {
                action()
            } catch (cleanupFailure: Exception) {
                failure.addSuppressed(cleanupFailure)
            }
        }
    }
}
