package com.joseleandro.scaniqo.data.pdf

import com.joseleandro.scaniqo.domain.model.PdfPageLayout
import com.joseleandro.scaniqo.domain.model.PdfPageQuality
import com.joseleandro.scaniqo.domain.model.PdfQualityReport
import com.joseleandro.scaniqo.domain.model.PdfWorkspace
import com.tom_roush.pdfbox.pdmodel.PDDocument
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Analisa fontes privadas em baixa resolução, sem gravar texto reconhecido ou imagens auxiliares. */
internal class PdfQualityAnalyzer(
    private val files: PdfWorkspaceFiles,
    private val composer: PdfBoxComposer,
) {
    suspend fun analyze(workspace: PdfWorkspace): PdfQualityReport {
        val sourceFiles = workspace.pages.map { it.sourceUri }.distinct()
            .associateWith { files.source(workspace, it) }
        val documents = linkedMapOf<String, PDDocument>()
        var originalFailure: Throwable? = null
        try {
            sourceFiles.forEach { (uri, file) -> documents[uri] = composer.load(file) }
            val previous = mutableListOf<Pair<String, PdfPageQualityMetrics>>()
            val contentCache = mutableMapOf<Pair<String, Int>, PdfNativeContent>()
            PdfFilteredPageRenderer(sourceFiles).use { renderer ->
                val result = workspace.pages.map { spec ->
                    currentCoroutineContext().ensureActive()
                    val key = spec.sourceUri to spec.sourcePageIndex
                    val native = contentCache[key] ?: composer.inspectContent(
                        requireNotNull(sourceFiles[spec.sourceUri]),
                        requireNotNull(documents[spec.sourceUri]), spec.sourcePageIndex
                    ).also { contentCache[key] = it }
                    val capture = native.captureBounds
                    renderer.render(spec.sourceUri, spec.sourcePageIndex,
                        layout = PdfPageLayout(crop = capture), maxSide = 1000).use { rendered ->
                        val bitmap = rendered.bitmap
                        val pixels = IntArray(bitmap.width * bitmap.height)
                        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                        val metrics = PdfPageQualityMetrics.measure(pixels, bitmap.width, bitmap.height)
                        val duplicate = previous.firstOrNull { metrics.resembles(it.second) }?.first
                        previous += spec.id to metrics
                        PdfPageQuality(
                            pageId = spec.id,
                            hasNativeText = native.hasText,
                            hasNativeVectors = native.hasVectors,
                            isBlank = metrics.isBlank && !native.hasText,
                            duplicateOfPageId = duplicate,
                            isBlurry = metrics.isBlurry && !native.shouldPreserve,
                            isLowResolution = !native.shouldPreserve && native.largestImageSide in 1..1199,
                            mayBeClipped = metrics.mayBeClipped,
                            suggestedStraightenDegrees = if (native.shouldPreserve) 0f else {
                                PdfImageCorrections.estimateStraightenDegrees(pixels, bitmap.width, bitmap.height)
                            },
                            suggestedCrop = if (capture == null) null else {
                                PdfDocumentBoundsDetector.detect(pixels, bitmap.width, bitmap.height)
                                    ?.let { PdfCaptureGeometry.toSource(it, capture) }
                            },
                        )
                    }
                }
                return PdfQualityReport(result)
            }
        } catch (failure: Throwable) {
            originalFailure = failure
            throw failure
        } finally {
            var closeFailure: Exception? = null
            documents.values.forEach { document ->
                try {
                    document.close()
                } catch (failure: Exception) {
                    val previous = originalFailure ?: closeFailure
                    if (previous != null) previous.addSuppressed(failure) else closeFailure = failure
                }
            }
            if (originalFailure == null) closeFailure?.let { throw it }
        }
    }
}
