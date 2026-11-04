package com.joseleandro.scaniqo.data.repository

import android.content.Context
import com.joseleandro.scaniqo.domain.model.SavedFormValue
import com.joseleandro.scaniqo.domain.repository.FormValueRepository
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/** Pequena biblioteca privada de trechos, independente dos documentos e da exportação de PDFs. */
class AndroidFormValueRepository(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : FormValueRepository {
    private val preferences = context.applicationContext.getSharedPreferences("saved_form_values", Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun list(): List<SavedFormValue> = withContext(ioDispatcher) {
        mutex.withLock { read() }
    }

    override suspend fun save(label: String, value: String): List<SavedFormValue> = withContext(ioDispatcher) {
        mutex.withLock {
            val entry = SavedFormValue(UUID.randomUUID().toString(), label.trim(), value.trim())
            require(entry.isValid()) { "Informe um nome e um texto válidos." }
            val current = read()
            val sameLabel = current.firstOrNull { it.label.equals(entry.label, ignoreCase = true) }
            require(current.size < 32 || sameLabel != null) { "Use até 32 dados salvos." }
            val updated = current.filterNot { it.id == sameLabel?.id } +
                entry.copy(id = sameLabel?.id ?: entry.id)
            write(updated)
        }
    }

    override suspend fun delete(id: String): List<SavedFormValue> = withContext(ioDispatcher) {
        mutex.withLock { write(read().filterNot { it.id == id }) }
    }

    private fun read(): List<SavedFormValue> {
        val values = json.decodeFromString<List<SavedFormValue>>(preferences.getString("values", "[]") ?: "[]")
        require(values.size <= 32 && values.all(SavedFormValue::isValid))
        return values
    }

    private fun write(values: List<SavedFormValue>): List<SavedFormValue> {
        check(preferences.edit().putString("values", json.encodeToString(values)).commit()) {
            "Não foi possível salvar os dados."
        }
        return values
    }
}
