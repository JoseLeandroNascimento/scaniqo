package com.joseleandro.scaniqo.domain.repository

import com.joseleandro.scaniqo.domain.model.DocumentModel
import com.joseleandro.scaniqo.domain.model.PdfCaptureSource
import com.joseleandro.scaniqo.domain.model.PdfFilterThumbnails
import com.joseleandro.scaniqo.domain.model.PdfInteractivePixels
import com.joseleandro.scaniqo.domain.model.PdfMergeSource
import com.joseleandro.scaniqo.domain.model.PdfPagePreview
import com.joseleandro.scaniqo.domain.model.PdfQualityReport
import com.joseleandro.scaniqo.domain.model.PdfSignatureCrop
import com.joseleandro.scaniqo.domain.model.PdfSignatureImage
import com.joseleandro.scaniqo.domain.model.PdfWorkspace
import com.joseleandro.scaniqo.domain.model.ScannedDocument

interface PdfEditorRepository {
    /** Remove sessões temporárias herdadas antes de permitir uma nova edição. */
    suspend fun initialize() = Unit

    suspend fun open(document: DocumentModel, password: String? = null): PdfWorkspace
    suspend fun importPdf(
        uri: String,
        password: String? = null,
        protectionKeyId: String? = null
    ): ScannedDocument
    /** Cria um rascunho independente, na ordem das fontes, sem alterar os documentos originais. */
    suspend fun mergePdfs(sources: List<PdfMergeSource>): ScannedDocument =
        throw UnsupportedOperationException("Mesclagem independente indisponível.")

    suspend fun appendScans(workspace: PdfWorkspace, pageUris: List<String>): PdfWorkspace
    suspend fun appendPdfs(workspace: PdfWorkspace, sources: List<PdfMergeSource>): PdfWorkspace
    /**
     * Importa todas as fontes em um único lote e intercala as páginas atuais sem reler o PDF salvo.
     * currentDocumentIndex é a quantidade de fontes que precedem o bloco da sessão.
     */
    suspend fun appendPdfsOrdered(
        workspace: PdfWorkspace,
        sources: List<PdfMergeSource>,
        currentDocumentIndex: Int,
    ): PdfWorkspace = throw UnsupportedOperationException("Mesclagem ordenada indisponível.")
    /** Sugere revisão de qualidade sem modificar o documento ou remover páginas. */
    suspend fun analyze(workspace: PdfWorkspace): PdfQualityReport = PdfQualityReport(emptyList())
    suspend fun copySignature(workspace: PdfWorkspace, imageUri: String): String
    /** Recorta uma nova imagem privada da sessão, sem alterar a biblioteca ou o histórico. */
    suspend fun cropSignature(
        workspace: PdfWorkspace,
        imageUri: String,
        crop: PdfSignatureCrop,
    ): PdfSignatureImage = throw UnsupportedOperationException("Recorte indisponível.")
    suspend fun preview(workspace: PdfWorkspace, retainedPreviewUri: String? = null): String
    /** Renderiza apenas a folha solicitada e preserva as prévias ainda exibidas pela interface. */
    suspend fun renderPagePreview(
        workspace: PdfWorkspace,
        pageId: String,
        retainedPreviewUris: Set<String> = emptySet(),
    ): PdfPagePreview {
        val index = workspace.pages.indexOfFirst { it.id == pageId }
        require(index >= 0) { "A página não está mais disponível." }
        return PdfPagePreview(preview(workspace), index)
    }

    /** Pixels em memória, sem compor PDF por gesto; null conserva o caminho de prévia compatível. */
    suspend fun renderInteractivePixels(
        workspace: PdfWorkspace,
        pageId: String,
        maxSide: Int = 480,
    ): PdfInteractivePixels? = null

    /** Fonte bruta de uma única captura; null evita tratar texto, várias imagens ou a A4 como captura. */
    suspend fun renderCaptureSource(
        workspace: PdfWorkspace,
        pageId: String,
        maxSide: Int = 1100,
    ): PdfCaptureSource? = null

    /** Resultado visual somente da captura, sem alterar a composição ou as margens do PDF salvo. */
    suspend fun renderCaptureResult(
        workspace: PdfWorkspace,
        pageId: String,
        maxSide: Int = 480,
    ): PdfInteractivePixels? = null

    /** Gera amostras pequenas de todos os filtros a partir de uma única leitura da página. */
    suspend fun filterThumbnails(
        workspace: PdfWorkspace,
        pageId: String,
        rasterizeText: Boolean = false,
        force: Boolean = false,
    ): PdfFilterThumbnails = PdfFilterThumbnails()

    suspend fun clearPreview(workspace: PdfWorkspace)
    suspend fun save(
        workspace: PdfWorkspace,
        password: String? = null,
        protectionKeyId: String? = null,
        removeProtection: Boolean = false
    ): DocumentModel
    suspend fun discard(workspace: PdfWorkspace)
}
