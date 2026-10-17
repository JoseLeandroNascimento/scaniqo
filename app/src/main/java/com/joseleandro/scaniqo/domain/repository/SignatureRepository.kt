package com.joseleandro.scaniqo.domain.repository

import com.joseleandro.scaniqo.domain.model.SignatureFont
import com.joseleandro.scaniqo.domain.model.SignatureModel
import com.joseleandro.scaniqo.domain.model.SignatureStroke
import kotlinx.coroutines.flow.Flow

interface SignatureRepository {
    fun observeSignatures(): Flow<List<SignatureModel>>
    suspend fun createDrawing(name: String, strokes: List<SignatureStroke>): SignatureModel
    suspend fun createTyped(name: String, text: String, font: SignatureFont, colorArgb: Int): SignatureModel
    suspend fun importImage(name: String, uri: String, removeLightBackground: Boolean): SignatureModel
    suspend fun rename(id: String, name: String)
    suspend fun delete(id: String)
}
