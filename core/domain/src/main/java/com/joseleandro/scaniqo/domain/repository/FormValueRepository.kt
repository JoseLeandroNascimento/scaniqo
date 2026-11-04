package com.joseleandro.scaniqo.domain.repository

import com.joseleandro.scaniqo.domain.model.SavedFormValue

/** Apenas valores salvos explicitamente pelo usuário; nenhuma captura automática do formulário. */
interface FormValueRepository {
    suspend fun list(): List<SavedFormValue>
    suspend fun save(label: String, value: String): List<SavedFormValue>
    suspend fun delete(id: String): List<SavedFormValue>
}
