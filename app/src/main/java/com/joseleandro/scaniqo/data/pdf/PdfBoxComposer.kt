package com.joseleandro.scaniqo.data.pdf

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.joseleandro.scaniqo.domain.model.PdfCaptureSource
import com.joseleandro.scaniqo.domain.model.PdfCropBounds
import com.joseleandro.scaniqo.domain.model.PdfImageFilter
import com.joseleandro.scaniqo.domain.model.PdfInteractivePixels
import com.joseleandro.scaniqo.domain.model.PdfImportException
import com.joseleandro.scaniqo.domain.model.PdfPageLayout
import com.joseleandro.scaniqo.domain.model.PdfPageSpec
import com.joseleandro.scaniqo.domain.model.PdfPasswordRequiredException
import com.joseleandro.scaniqo.domain.model.PdfSignaturePlacement
import com.joseleandro.scaniqo.domain.model.PdfWorkspace
import com.tom_roush.harmony.awt.geom.AffineTransform
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.LayerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.util.Matrix
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.math.max
import kotlin.math.roundToInt

internal class PdfBoxComposer(private val files: PdfWorkspaceFiles) {
    private val nativeContentCache = PdfNativeContentCache()
    private val interactiveRenderer = PdfInteractivePageRenderer()
    private val pageCaches = mutableMapOf<String, PdfPreviewPageCache>()
    private val exportRasterCaches = mutableMapOf<String, PdfPreviewPageCache>()

    suspend fun inspectContent(file: File, document: PDDocument, pageIndex: Int): PdfNativeContent =
        nativeContentCache.get(file, pageIndex) { PdfNativeContent.inspect(document, pageIndex) }

    suspend fun inspectContent(file: File, pageIndex: Int): PdfNativeContent =
        nativeContentCache.get(file, pageIndex) {
            load(file).use { PdfNativeContent.inspect(it, pageIndex) }
        }

    /** Copia os bytes originais; quando necessário aplica somente a proteção, sem recompor folhas. */
    suspend fun saveCopy(source: File, target: File, password: String? = null) {
        if (password == null) {
            files.copy(files.uri(source), target)
            return
        }
        load(source).use { document ->
            PdfProtection.apply(document, password)
            val context = currentCoroutineContext()
            target.outputStream().use { stream ->
                document.save(PdfCancellableOutputStream(stream, context, PdfWorkspaceFiles.MAX_BYTES) {
                    PdfImportException("O PDF final excede 50 MB.")
                })
            }
        }
    }

    fun clearPreviewCache(workspace: PdfWorkspace) {
        pageCaches.remove(workspace.workspaceId)?.clear()
        exportRasterCaches.remove(workspace.workspaceId)?.clear()
        nativeContentCache.clear(files.file(workspace.directoryUri))
        interactiveRenderer.clear(files.file(workspace.directoryUri))
    }

    fun load(
        file: File,
        password: String? = null,
        pageLimit: Int = PdfWorkspaceFiles.MAX_PAGES,
        allowEmptyPassword: Boolean = false,
        pageLimitFailure: () -> IOException = {
            PdfImportException("O documento deve conter entre 1 e 100 páginas.")
        }
    ): PDDocument {
        files.checkSize(file)
        val document = try {
            PDDocument.load(file, password.orEmpty(), memory())
        } catch (_: InvalidPasswordException) {
            throw PdfPasswordRequiredException()
        } catch (failure: IOException) {
            throw PdfImportException(
                "Não foi possível ler este PDF. O arquivo pode estar corrompido ou usar uma proteção incompatível.",
                failure
            )
        }
        try {
            if (document.numberOfPages < 1) {
                throw PdfImportException("O documento precisa conter pelo menos uma página.")
            }
            if (document.numberOfPages > pageLimit) throw pageLimitFailure()
            if (document.signatureDictionaries.isNotEmpty()) {
                throw PdfImportException(
                    "Este PDF possui assinatura digital. A edição invalidaria o certificado."
                )
            }
            // A mesclagem aceita abertura vazia já validada pelo PDFBox, mantendo as permissões.
            if (document.isEncrypted && password.isNullOrBlank() && !allowEmptyPassword) {
                throw PdfPasswordRequiredException()
            }
            if (!document.currentAccessPermission.canModify()) {
                throw PdfImportException("A senha informada não permite editar este PDF.")
            }
            return document
        } catch (failure: Throwable) {
            try {
                document.close()
            } catch (closeFailure: Exception) {
                failure.addSuppressed(closeFailure)
            }
            throw failure
        }
    }

    /** Incorpora aparências dos formulários/anotações ao conteúdo, mantendo texto e vetores. */
    suspend fun flattenVisualAnnotations(document: PDDocument) {
        document.documentCatalog.acroForm?.flatten()
        document.pages.forEach { page ->
            currentCoroutineContext().ensureActive()
            val annotations = page.annotations.toList()
            if (annotations.isEmpty()) return@forEach
            PDPageContentStream(
                document, page, PDPageContentStream.AppendMode.APPEND, true, true
            ).use { stream ->
                annotations.forEach { annotation ->
                    currentCoroutineContext().ensureActive()
                    if (annotation.isHidden || annotation.isInvisible || annotation.isNoView) {
                        return@forEach
                    }
                    if (annotation.normalAppearanceStream == null) {
                        annotation.constructAppearances(document)
                    }
                    val appearance = annotation.normalAppearanceStream ?: return@forEach
                    val rectangle = annotation.rectangle ?: return@forEach
                    val bounds = appearance.bBox ?: return@forEach
                    val matrix = appearance.matrix
                    val corners = listOf(
                        matrix.transformPoint(bounds.lowerLeftX, bounds.lowerLeftY),
                        matrix.transformPoint(bounds.upperRightX, bounds.lowerLeftY),
                        matrix.transformPoint(bounds.lowerLeftX, bounds.upperRightY),
                        matrix.transformPoint(bounds.upperRightX, bounds.upperRightY)
                    )
                    val left = corners.minOf { it.x }
                    val bottom = corners.minOf { it.y }
                    val width = corners.maxOf { it.x } - left
                    val height = corners.maxOf { it.y } - bottom
                    if (width <= 0 || height <= 0) return@forEach
                    val scaleX = rectangle.width / width
                    val scaleY = rectangle.height / height
                    stream.saveGraphicsState()
                    stream.transform(Matrix(
                        scaleX, 0f, 0f, scaleY,
                        rectangle.lowerLeftX - left * scaleX,
                        rectangle.lowerLeftY - bottom * scaleY
                    ))
                    stream.drawForm(appearance)
                    stream.restoreGraphicsState()
                }
            }
            page.annotations = emptyList()
        }
    }

    /** Fornece a imagem bruta, mantendo margens e layout do PDF fora dos controles de captura. */
    suspend fun renderCaptureSource(
        workspace: PdfWorkspace, pageId: String, maxSide: Int,
    ): PdfCaptureSource? {
        require(maxSide in 128..PdfInteractivePageRenderer.MAX_SIDE)
        val spec = workspace.pages.firstOrNull { it.id == pageId }
            ?: throw IllegalArgumentException("A página não está mais disponível.")
        val source = files.source(workspace, spec.sourceUri)
        val native = inspectContent(source, spec.sourcePageIndex)
        val bounds = native.captureBounds ?: return null
        return interactiveRenderer.render(
            source, spec.sourceUri, spec.sourcePageIndex, PdfPageLayout(crop = bounds), maxSide,
        ).use { rendered ->
            currentCoroutineContext().ensureActive()
            val bitmap = rendered.bitmap
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            PdfCaptureSource(
                PdfInteractivePixels(bitmap.width, bitmap.height, pixels, rendered.width, rendered.height), bounds,
                preservesNativeContent = native.shouldPreserve,
            )
        }
    }

    /**
     * A janela visual não muda a receita: processa a mesma página da exportação e só então
     * extrai seus pixels visíveis. Isso preserva centro/escala de correções já existentes.
     */
    suspend fun renderCaptureResult(
        workspace: PdfWorkspace, pageId: String, maxSide: Int,
    ): PdfInteractivePixels? {
        require(maxSide in 128..PdfInteractivePageRenderer.MAX_SIDE)
        val spec = workspace.pages.firstOrNull { it.id == pageId }
            ?: throw IllegalArgumentException("A página não está mais disponível.")
        spec.layout.validateImageProcessing()
        val source = files.source(workspace, spec.sourceUri)
        val native = inspectContent(source, spec.sourcePageIndex)
        val capture = native.captureBounds ?: return null
        val renderArea = spec.layout.crop ?: PdfCropBounds()
        val visible = PdfCaptureGeometry.intersect(capture, renderArea) ?: return null
        val local = PdfCaptureGeometry.relativeTo(visible, renderArea)
        if (local.isIdentity()) return renderInteractivePixels(workspace, pageId, maxSide)
        val renderSide = PdfCaptureGeometry.resultMaxSide(
            native.sourceWidth, native.sourceHeight, renderArea, visible, maxSide,
        )
        val effective = effectivePreviewPage(spec, source)
        return PdfFilteredPageRenderer(mapOf(spec.sourceUri to source)).use { renderer ->
            renderer.render(spec.sourceUri, spec.sourcePageIndex, effective.layout, renderSide,
                captureBounds = native.captureBounds).use { rendered ->
                currentCoroutineContext().ensureActive()
                val bitmap = rendered.bitmap
                val left = (local.left * bitmap.width).roundToInt().coerceIn(0, bitmap.width - 1)
                val top = (local.top * bitmap.height).roundToInt().coerceIn(0, bitmap.height - 1)
                val right = (local.right * bitmap.width).roundToInt().coerceIn(left + 1, bitmap.width)
                val bottom = (local.bottom * bitmap.height).roundToInt().coerceIn(top + 1, bitmap.height)
                val width = right - left
                val height = bottom - top
                val pixels = IntArray(width * height)
                bitmap.getPixels(pixels, 0, width, left, top, width, height)
                currentCoroutineContext().ensureActive()
                PdfInteractivePixels(width, height, pixels,
                    rendered.width * (local.right - local.left),
                    rendered.height * (local.bottom - local.top))
            }
        }
    }

    /** Entrega os pixels à interface sem JPEG intermediário, composição de PDF ou gravação em disco. */
    suspend fun renderInteractivePixels(
        workspace: PdfWorkspace,
        pageId: String,
        maxSide: Int,
    ): PdfInteractivePixels {
        require(workspace.pages.size in 1..PdfWorkspaceFiles.MAX_PAGES)
        require(maxSide in 128..PdfInteractivePageRenderer.MAX_SIDE)
        currentCoroutineContext().ensureActive()
        val spec = workspace.pages.firstOrNull { it.id == pageId }
            ?: throw IllegalArgumentException("A página não está mais disponível.")
        val source = files.source(workspace, spec.sourceUri)
        val effective = effectivePreviewPage(spec, source)
        return interactiveRenderer.render(
            source, spec.sourceUri, spec.sourcePageIndex, effective.layout, maxSide,
            captureBounds = captureBoundsFor(effective, source),
        ).use { rendered ->
            currentCoroutineContext().ensureActive()
            val bitmap = rendered.bitmap
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            currentCoroutineContext().ensureActive()
            PdfInteractivePixels(bitmap.width, bitmap.height, pixels, rendered.width, rendered.height)
        }
    }

    /** A mesma regra evita divergência entre a prévia em memória e a prévia em PDF. */
    private suspend fun effectivePreviewPage(spec: PdfPageSpec, source: File): PdfPageSpec {
        spec.layout.validateImageProcessing()
        // Consultar o cache antes de abrir o PDFBox evita reler a fonte a cada alteração.
        val preserveNative = spec.layout.needsImageProcessing() && !spec.layout.rasterizeText &&
            nativeContentCache.get(source, spec.sourcePageIndex) {
                load(source).use { PdfNativeContent.inspect(it, spec.sourcePageIndex) }
            }.shouldPreserve
        return if (!preserveNative) spec else spec.copy(layout = spec.layout.copy(
            imageFilter = PdfImageFilter.ORIGINAL,
            straightenDegrees = 0f,
            perspective = null,
            curvature = 0f,
            backgroundMask = null,
        ))
    }

    /** Gera somente a folha visível; o PDF completo continua sendo montado apenas na gravação. */
    suspend fun composePagePreview(workspace: PdfWorkspace, pageId: String, target: File) {
        require(workspace.pages.size in 1..PdfWorkspaceFiles.MAX_PAGES)
        val index = workspace.pages.indexOfFirst { it.id == pageId }
        require(index >= 0) { "A página não está mais disponível." }
        val spec = workspace.pages[index]
        val source = files.source(workspace, spec.sourceUri)
        val effective = effectivePreviewPage(spec, source)
        val cache = pageCaches.getOrPut(workspace.workspaceId) {
            PdfPreviewPageCache(files.directory(workspace))
        }
        val key = PdfRasterKey.from(source, effective, PdfInteractivePageRenderer.MAX_SIDE)
        cache.beginPreview(setOf(key))
        PDDocument(memory()).use { output ->
            val page = PDPage(PDRectangle(PdfPageGeometry.WIDTH, PdfPageGeometry.HEIGHT))
            output.addPage(page)
            val image = cachedImage(output, key, cache) {
                interactiveRenderer.render(source, spec.sourceUri, spec.sourcePageIndex, effective.layout,
                captureBounds = captureBoundsFor(effective, source))
            }
            val layout = spec.layout
            val margin = PdfPageGeometry.marginFor(layout)
            PDPageContentStream(output, page).use { stream ->
                stream.setNonStrokingColor(1f)
                stream.addRect(0f, 0f, PdfPageGeometry.WIDTH, PdfPageGeometry.HEIGHT)
                stream.fill()
                stream.saveGraphicsState()
                stream.addRect(
                    margin, margin, PdfPageGeometry.WIDTH - margin * 2,
                    PdfPageGeometry.HEIGHT - margin * 2
                )
                stream.clip()
                // PdfRenderer já resolve CropBox/rotação nativa; a receita posiciona essa aparência.
                stream.transform(PdfPageGeometry.placement(
                    0f, 0f, image.width, image.height, 0, layout,
                    PdfPageGeometry.WIDTH, PdfPageGeometry.HEIGHT, margin
                ).toMatrix())
                stream.drawImage(image.image, 0f, 0f, image.width, image.height)
                stream.restoreGraphicsState()
                require(spec.signatures.size <= 20) { "Use até 20 assinaturas por página." }
                spec.signatures.forEach { drawSignature(output, stream, it, maxSide = 512) }
                PdfOverlayLayer.draw(output, stream, spec.overlays, index + 1, workspace.pages.size)
                if (workspace.showPageNumbers) drawPageNumber(stream, index + 1, workspace.pages.size)
            }
            currentCoroutineContext().ensureActive()
            val context = currentCoroutineContext()
            target.outputStream().use { stream ->
                output.save(PdfCancellableOutputStream(stream, context, PdfWorkspaceFiles.MAX_BYTES) {
                    PdfImportException("A prévia desta página excedeu o limite de tamanho.")
                })
            }
        }
    }

    suspend fun compose(
        workspace: PdfWorkspace,
        target: File,
        password: String? = null,
        originalsOnly: Boolean = false,
        forExport: Boolean = false,
        reusablePreview: PdfReusablePreview? = null,
        reusableFinal: PdfFinalExportSnapshot? = null,
    ) {
        require(workspace.pages.size in 1..PdfWorkspaceFiles.MAX_PAGES) {
            "Mantenha entre 1 e 100 páginas no documento."
        }
        val sources = linkedMapOf<String, PDDocument>()
        val sourcePages = mutableMapOf<String, PdfPageIndex>()
        var failure: Throwable? = null
        var filteredPages: PdfFilteredPageRenderer? = null
        var previousDocument: PDDocument? = null
        try {
            val sourceFiles = workspace.pages.map { it.sourceUri }.distinct()
                .associateWith { files.source(workspace, it) }
            if (sourceFiles.values.sumOf { it.length() } > PdfWorkspaceFiles.MAX_BYTES) {
                throw PdfImportException("As fontes do documento excedem 50 MB.")
            }
            val directory = files.directory(workspace)
            val hasDynamicOverlays = workspace.pages.any { page -> page.overlays.any {
                it.text.contains("{pagina}") || it.text.contains("{total}")
            } }
            val final = reusableFinal?.takeUnless { hasDynamicOverlays }?.takeIf {
                forExport && !originalsOnly && it.file.isFile &&
                    it.file.canonicalFile.parentFile == directory &&
                    it.file.name.startsWith("final_preview_") && it.file.name.endsWith(".pdf")
            }
            val reusable = reusablePreview?.takeUnless { hasDynamicOverlays }?.takeIf {
                !forExport && !originalsOnly && it.file.isFile &&
                    it.file.canonicalFile.parentFile == directory &&
                    (it.file.name.startsWith("preview_") || it.file.name.startsWith("final_preview_")) &&
                    it.file.name.endsWith(".pdf")
            }
            val matchingIndices = final?.matchingPageIndices(workspace, sourceFiles)
                ?: reusable?.matchingPageIndices(workspace)
            if (final != null && final.visual.pages.size == workspace.pages.size &&
                matchingIndices == workspace.pages.indices.toList()
            ) {
                // Alterar apenas proteção/metadados não exige sequer regravar o conteúdo do PDF.
                saveCopy(final.file, target, password)
                return
            }
            val contentIndices = final?.matchingContentPageIndices(workspace, sourceFiles) ?: matchingIndices
            val previousFile = (final?.file ?: reusable?.file)
                .takeIf { contentIndices?.any { it != null } == true }
            val previous = previousFile?.let { load(it).also { loaded -> previousDocument = loaded } }
            val expectedPages = final?.visual?.pages?.size ?: reusable?.key?.pages?.size
            val reusedIndices = contentIndices.takeIf { previous?.numberOfPages == expectedPages }
            val previousPages = previous?.let { PdfPageIndex(it, currentCoroutineContext()) }
            val rasterizer = PdfFilteredPageRenderer(sourceFiles).also { filteredPages = it }
            PDDocument(memory()).use { output ->
                val importer = LayerUtility(output)
                val pageCopier = PdfReadyPageCopier(output)
                val pageCache = if (forExport || originalsOnly) null else {
                    pageCaches.getOrPut(workspace.workspaceId) {
                        PdfPreviewPageCache(files.directory(workspace))
                    }.also { cache ->
                        cache.beginPreview(workspace.pages.filter { it.layout.needsImageProcessing() }
                            .map { PdfRasterKey.from(requireNotNull(sourceFiles[it.sourceUri]), it) }.toSet())
                    }
                }
                val exportCache = if (forExport && !originalsOnly) {
                    exportRasterCaches.getOrPut(workspace.workspaceId) {
                        PdfPreviewPageCache(
                            files.directory(workspace), maxEntries = 32, filePrefix = "export_raster_"
                        )
                    }
                } else null
                val ocrPages = mutableListOf<Int>()
                val optimizedPages = mutableListOf<Int>()
                val settings = PdfExportSettings.forQuality(workspace.exportQuality)
                workspace.pages.forEachIndexed { index, spec ->
                    currentCoroutineContext().ensureActive()
                    val previousIndex = reusedIndices?.get(index)
                    if (previous != null && previousIndex != null) {
                        val ready = requireNotNull(previousPages)[previousIndex]
                        val numbered = final?.visual?.showPageNumbers == true
                        val removeNumber = numbered && PdfPageNumberLayer.hasLayer(ready)
                        val compatible = final == null || !numbered || removeNumber ||
                            matchingIndices?.get(index) == previousIndex
                        val copied = if (compatible) pageCopier.copyPage(ready, removePageNumber = removeNumber) else null
                        if (copied != null) {
                            if (final != null && workspace.showPageNumbers && (!numbered || removeNumber)) {
                                PdfPageNumberLayer.append(output, copied, index + 1, workspace.pages.size)
                            }
                            // A folha pronta já contém filtros, OCR e compressão na qualidade final correta.
                            return@forEachIndexed
                        }
                    }
                    val source = sources.getOrPut(spec.sourceUri) {
                        load(requireNotNull(sourceFiles[spec.sourceUri]))
                    }
                    val indexedPages = sourcePages.getOrPut(spec.sourceUri) {
                        PdfPageIndex(source, currentCoroutineContext())
                    }
                    require(spec.sourcePageIndex in 0 until indexedPages.size)
                    val sourcePage = indexedPages[spec.sourcePageIndex]
                    if (sourcePage.resources == null) sourcePage.resources = PDResources()
                    val layout = if (originalsOnly) PdfPageLayout() else spec.layout
                    layout.validateImageProcessing()
                    val originalBox = sourcePage.cropBox
                    val croppedBox = PdfCropGeometry.sourceBox(
                        originalBox.lowerLeftX, originalBox.lowerLeftY, originalBox.width, originalBox.height,
                        sourcePage.rotation, layout.crop,
                    )
                    val box = PDRectangle(croppedBox.x, croppedBox.y, croppedBox.width, croppedBox.height)
                    val rotated = ((sourcePage.rotation % 180) + 180) % 180 != 0
                    val width = if (originalsOnly) {
                        if (rotated) box.height else box.width
                    } else PdfPageGeometry.WIDTH
                    val height = if (originalsOnly) {
                        if (rotated) box.width else box.height
                    } else PdfPageGeometry.HEIGHT
                    require(width.isFinite() && height.isFinite() && width > 0 && height > 0)
                    val page = PDPage(PDRectangle(width, height))
                    output.addPage(page)
                    val needsProcessing = layout.needsImageProcessing()
                    val native = if (needsProcessing || (!originalsOnly && forExport && workspace.ocrEnabled)) {
                        nativeContentCache.get(requireNotNull(sourceFiles[spec.sourceUri]), spec.sourcePageIndex) {
                            PdfNativeContent.inspect(sourcePage)
                        }
                    } else null
                    // Filtros nunca removem texto ou vetores sem a confirmação explícita da interface.
                    val rasterize = needsProcessing &&
                        (layout.rasterizeText || native?.shouldPreserve != true)
                    // Rasters novos já usam o perfil final; não decodificamos/recomprimimos esses JPEGs outra vez.
                    if (forExport && !originalsOnly && !rasterize) optimizedPages += index
                    if (!originalsOnly && forExport && workspace.ocrEnabled &&
                        (native?.hasText != true || rasterize)
                    ) ocrPages += index
                    val margin = if (originalsOnly) 0f else PdfPageGeometry.marginFor(layout)
                    PDPageContentStream(output, page).use { stream ->
                        stream.setNonStrokingColor(1f)
                        stream.addRect(0f, 0f, width, height)
                        stream.fill()
                        stream.saveGraphicsState()
                        stream.addRect(margin, margin, width - margin * 2, height - margin * 2)
                        stream.clip()
                        if (!rasterize) {
                            val form = importer.importPageAsForm(source, sourcePage)
                            // Tratamos orientação e CropBox sem rasterizar texto ou vetores.
                            form.setMatrix(AffineTransform())
                            form.bBox = PDRectangle(box.cosArray)
                            val transform = PdfPageGeometry.placement(
                                box.lowerLeftX, box.lowerLeftY, box.width, box.height,
                                sourcePage.rotation, layout, width, height, margin
                            )
                            stream.transform(transform.toMatrix())
                            stream.drawForm(form)
                        } else if (pageCache != null) {
                            val cached = previewImage(
                                output, spec, requireNotNull(sourceFiles[spec.sourceUri]), rasterizer, pageCache,
                                captureBounds = native?.captureBounds,
                            )
                            val transform = PdfPageGeometry.placement(
                                0f, 0f, cached.width, cached.height, 0,
                                layout, width, height, margin
                            )
                            stream.transform(transform.toMatrix())
                            stream.drawImage(cached.image, 0f, 0f, cached.width, cached.height)
                        } else {
                            val key = PdfRasterKey.from(
                                requireNotNull(sourceFiles[spec.sourceUri]), spec,
                                settings.maxImageSide, settings.jpegQuality
                            )
                            val cache = requireNotNull(exportCache)
                            cache.beginPreview(setOf(key))
                            val cached = cachedImage(output, key, cache) {
                                rasterizer.render(
                                    spec.sourceUri, spec.sourcePageIndex, layout, settings.maxImageSide,
                                    captureBounds = native?.captureBounds,
                                )
                            }
                            stream.transform(PdfPageGeometry.placement(
                                0f, 0f, cached.width, cached.height, 0, layout, width, height, margin
                            ).toMatrix())
                            stream.drawImage(cached.image, 0f, 0f, cached.width, cached.height)
                        }
                        stream.restoreGraphicsState()
                        if (!originalsOnly) {
                            require(spec.signatures.size <= 20) { "Use até 20 assinaturas por página." }
                            spec.signatures.forEach { drawSignature(output, stream, it) }
                            PdfOverlayLayer.draw(output, stream, spec.overlays, index + 1, workspace.pages.size)
                        }
                    }
                    if (!originalsOnly && workspace.showPageNumbers) {
                        PdfPageNumberLayer.append(output, page, index + 1, workspace.pages.size)
                    }
                }
                currentCoroutineContext().ensureActive()
                if (!originalsOnly && forExport) {
                    if (ocrPages.isNotEmpty()) appendOcr(output, workspace, ocrPages)
                    PdfImageOptimizer.optimize(output, workspace.exportQuality, optimizedPages)
                }
                if (password != null) PdfProtection.apply(output, password)
                val context = currentCoroutineContext()
                target.outputStream().use { stream ->
                    output.save(PdfCancellableOutputStream(stream, context, PdfWorkspaceFiles.MAX_BYTES) {
                        PdfImportException("O PDF final excede 50 MB. Escolha um perfil menor ou remova páginas.")
                    })
                }
                files.checkSize(target)
            }
        } catch (cause: Throwable) {
            failure = cause
            throw cause
        } finally {
            closeResources(sources.values + listOfNotNull(filteredPages, previousDocument), failure)
        }
    }

    private data class CachedImage(val image: PDImageXObject, val width: Float, val height: Float)

    private suspend fun previewImage(
        output: PDDocument,
        spec: PdfPageSpec,
        source: File,
        renderer: PdfFilteredPageRenderer,
        cache: PdfPreviewPageCache,
        captureBounds: PdfCropBounds?,
    ): CachedImage = cachedImage(output, PdfRasterKey.from(source, spec), cache) {
        renderer.render(spec.sourceUri, spec.sourcePageIndex, spec.layout, maxSide = 1800,
            captureBounds = captureBounds)
    }

    private suspend fun captureBoundsFor(spec: PdfPageSpec, source: File): PdfCropBounds? =
        if (spec.layout.backgroundMask == null) null else inspectContent(source, spec.sourcePageIndex).captureBounds


    private suspend fun cachedImage(
        output: PDDocument,
        key: PdfRasterKey,
        cache: PdfPreviewPageCache,
        render: suspend () -> PdfFilteredPageRenderer.FilteredPage,
    ): CachedImage {
        val lossless = key.filter == PdfImageFilter.BLACK_WHITE ||
            key.filter == PdfImageFilter.ADAPTIVE_BLACK_WHITE
        return cache.use(
            key = key,
            extension = if (lossless) "png" else "jpg",
            create = { target ->
                render().use { page ->
                    val format = if (lossless) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
                    val context = currentCoroutineContext()
                    target.outputStream().use { stream ->
                        val limited = PdfCancellableOutputStream(stream, context, 16L * 1024 * 1024) {
                            PdfImportException("A prévia desta página excedeu o limite de tamanho.")
                        }
                        check(page.bitmap.compress(format, (key.jpegQuality * 100).roundToInt(), limited)) {
                            "Não foi possível preparar a prévia da página."
                        }
                    }
                    page.width to page.height
                }
            },
            consume = { asset ->
                // JPEG entra no PDF sem decodificar e comprimir novamente os mesmos pixels.
                val image = if (!lossless) asset.file.inputStream().use {
                    JPEGFactory.createFromStream(output, it)
                } else {
                    val bitmap = BitmapFactory.decodeFile(asset.file.path)
                        ?: throw IOException("A prévia desta página não está disponível.")
                    try {
                        LosslessFactory.createFromImage(output, bitmap)
                    } finally {
                        bitmap.recycle()
                    }
                }
                CachedImage(image, asset.width, asset.height)
            },
        )
    }

    private suspend fun appendOcr(
        output: PDDocument,
        workspace: PdfWorkspace,
        pageIndices: List<Int>,
    ) {
        val pages = PdfPageIndex(output, currentCoroutineContext())
        val scratch = File(files.directory(workspace), "ocr_${UUID.randomUUID()}.pdf")
        try {
            // O reconhecedor recebe apenas as folhas alteradas, sem regravar todo o PDF como intermediário.
            PDDocument(memory()).use { subset ->
                val copier = PdfReadyPageCopier(subset)
                for (index in pageIndices) {
                    currentCoroutineContext().ensureActive()
                    val source = pages[index]
                    check(copier.copy(source, removePageNumber = PdfPageNumberLayer.hasLayer(source))) {
                        "Não foi possível preparar a página para OCR."
                    }
                }
                val context = currentCoroutineContext()
                scratch.outputStream().use { stream ->
                    subset.save(PdfCancellableOutputStream(stream, context, PdfWorkspaceFiles.MAX_BYTES) {
                        PdfImportException("O documento é grande demais para reconhecer o texto.")
                    })
                }
            }
            PdfOcrTextLayer(output).use { ocr ->
                PdfFilteredPageRenderer(mapOf("ocr" to scratch)).use { renderer ->
                    for ((temporaryIndex, outputIndex) in pageIndices.withIndex()) {
                        currentCoroutineContext().ensureActive()
                        renderer.render("ocr", temporaryIndex, maxSide = 2400).use { rendered ->
                            ocr.append(pages[outputIndex], rendered.bitmap)
                        }
                    }
                }
            }
        } finally {
            scratch.delete()
        }
    }

    private fun closeResources(sources: Collection<Closeable>, failure: Throwable?) {
        var closeFailure: Exception? = null
        sources.forEach { document ->
            try {
                document.close()
            } catch (cause: Exception) {
                // Fechamos todas as fontes sem substituir uma falha ou cancelamento anterior.
                val previous = failure ?: closeFailure
                if (previous != null) previous.addSuppressed(cause) else closeFailure = cause
            }
        }
        if (failure == null) closeFailure?.let { throw it }
    }

    private fun drawPageNumber(stream: PDPageContentStream, page: Int, count: Int) {
        val text = "$page / $count"
        val font = PDType1Font.HELVETICA
        val size = 10f
        val width = font.getStringWidth(text) / 1000 * size
        // O fundo mantém a numeração legível mesmo quando a imagem preenche a folha inteira.
        stream.setNonStrokingColor(1f)
        stream.addRect((PdfPageGeometry.WIDTH - width) / 2 - 4, 12f, width + 8, size + 6)
        stream.fill()
        stream.beginText()
        stream.setNonStrokingColor(0f)
        stream.setFont(font, size)
        stream.newLineAtOffset((PdfPageGeometry.WIDTH - width) / 2, 15f)
        stream.showText(text)
        stream.endText()
    }

    private suspend fun drawSignature(
        document: PDDocument,
        stream: PDPageContentStream,
        signature: PdfSignaturePlacement,
        maxSide: Int = 2048,
    ) {
        require(listOf(signature.x, signature.y, signature.width, signature.height)
            .all { it.isFinite() && it in 0f..1f })
        require(signature.width > 0 && signature.height > 0 &&
            signature.x + signature.width <= 1.001f &&
            signature.y + signature.height <= 1.001f)
        val uri = Uri.parse(signature.imageUri)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        files.resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw IOException("A imagem da assinatura não está disponível.")
        }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight).toLong() > maxSide.toLong() * sample) sample *= 2
        currentCoroutineContext().ensureActive()
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = files.resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: throw IOException("Não foi possível ler a assinatura.")
        try {
            val image = LosslessFactory.createFromImage(document, bitmap)
            val transform = PdfSignatureGeometry.pdfTransform(signature, bitmap.width, bitmap.height)
            stream.saveGraphicsState()
            try {
                stream.transform(transform.toMatrix())
                stream.drawImage(image, 0f, 0f, 1f, 1f)
            } finally {
                stream.restoreGraphicsState()
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun memory(): MemoryUsageSetting =
        MemoryUsageSetting.setupMixed(8L * 1024 * 1024, 200L * 1024 * 1024)
            .setTempDir(files.workspaceRoot)

    private fun PdfPageGeometry.Transform.toMatrix() = Matrix(a, b, c, d, e, f)
}
