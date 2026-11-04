package com.joseleandro.scaniqo.domain.repository

import com.joseleandro.scaniqo.domain.model.PdfSignatureCrop
import com.joseleandro.scaniqo.domain.model.PdfSignatureImage
import com.joseleandro.scaniqo.domain.model.SignatureFont
import com.joseleandro.scaniqo.domain.model.SignatureModel
import com.joseleandro.scaniqo.domain.model.SignatureStroke
import kotlinx.coroutines.flow.Flow

interface SignatureRepository {
    fun observeSignatures(): Flow<List<SignatureModel>>
    suspend fun createDrawing(name: String, strokes: List<SignatureStroke>): SignatureModel
    suspend fun createTyped(name: String, text: String, font: SignatureFont, colorArgb: Int): SignatureModel
    /** Legacy callers retain their signature; background removal is always applied. */
    suspend fun importImage(name: String, uri: String, removeLightBackground: Boolean): SignatureModel
    /** Copies, bounds and orients an image into an app-owned source for the crop editor. */
    suspend fun stageImage(uri: String): PdfSignatureImage =
        throw UnsupportedOperationException("Importação em etapas não implementada.")
    /** Crops a staged source and creates the transparent PNG shown in the confirmation preview. */
    suspend fun processImage(uri: String, crop: PdfSignatureCrop): PdfSignatureImage =
        throw UnsupportedOperationException("Processamento de assinaturas não implementado.")
    /** Saves the exact processed preview, without running background removal a second time. */
    suspend fun saveProcessedImage(name: String, uri: String, editingId: String? = null): SignatureModel =
        throw UnsupportedOperationException("Importação em etapas não implementada.")
    suspend fun discardStagedImage(uri: String) = Unit
    suspend fun updateDrawing(id: String, name: String, strokes: List<SignatureStroke>): SignatureModel =
        throw UnsupportedOperationException("Edição de assinaturas não implementada.")
    suspend fun updateTyped(id: String, name: String, text: String, font: SignatureFont, colorArgb: Int): SignatureModel =
        throw UnsupportedOperationException("Edição de assinaturas não implementada.")
    suspend fun updateImage(id: String, name: String, uri: String, removeLightBackground: Boolean): SignatureModel =
        throw UnsupportedOperationException("Edição de assinaturas não implementada.")
    suspend fun rename(id: String, name: String)
    suspend fun delete(id: String)
}
