package com.joseleandro.scaniqo.data.backup

import android.content.Context
import android.content.SharedPreferences
import com.joseleandro.scaniqo.domain.model.BackupHistoryEntry
import com.joseleandro.scaniqo.domain.repository.BackupHistoryRepository
import java.io.IOException
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put

class AndroidBackupHistoryRepository internal constructor(
    private val preferences: SharedPreferences,
) : BackupHistoryRepository {
    constructor(context: Context) : this(
        context.applicationContext.getSharedPreferences("backup_history", Context.MODE_PRIVATE),
    )

    private val mutex = Mutex()
    private val initial = runCatching { readHistory() }
    private var accounts = initial.getOrDefault(emptyMap())
    private val state = MutableStateFlow(accounts.mapValues { it.value.entries })
    override val entries = state.asStateFlow()

    override suspend fun record(accountEmail: String, entries: List<BackupHistoryEntry>) {
        val key = accountEmail.accountKey()
        entries.forEach { it.validate() }
        change(key) { saved ->
            val merged = saved.entries.associateBy { it.id }.toMutableMap()
            entries.filterNot { it.id in saved.deletedIds }.forEach { entry ->
                merged[entry.id] = entry.copy(documentCount = entry.documentCount ?: merged[entry.id]?.documentCount)
            }
            saved.copy(entries = merged.values.sortedWith(
                compareByDescending<BackupHistoryEntry> { it.createdAtMillis }.thenByDescending { it.id },
            ))
        }
    }

    override suspend fun delete(accountEmail: String, id: String) {
        require(id.isNotBlank())
        change(accountEmail.accountKey()) { saved ->
            saved.copy(entries = saved.entries.filterNot { it.id == id }, deletedIds = saved.deletedIds + id)
        }
    }

    private suspend fun change(key: String, transform: (AccountHistory) -> AccountHistory) =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                if (initial.isFailure) {
                    throw IOException("Não foi possível ler o histórico de backups.", initial.exceptionOrNull())
                }
                val previous = accounts[key] ?: AccountHistory()
                val next = transform(previous)
                if (next == previous) return@withLock
                val updated = accounts + (key to next)
                if (!preferences.edit().putString("history", encode(updated)).commit()) {
                    throw IOException("Não foi possível salvar o histórico de backups.")
                }
                accounts = updated
                state.value = updated.mapValues { it.value.entries }
            }
        }

    private fun readHistory(): Map<String, AccountHistory> {
        val saved = preferences.getString("history", null) ?: return emptyMap()
        val root = Json.parseToJsonElement(saved).jsonObject
        require(root.getValue("version").jsonPrimitive.int == 1)
        return root.getValue("accounts").jsonObject.entries.associate { (email, value) ->
            val account = value.jsonObject
            val removed = account.getValue("deletedIds").jsonArray.map {
                it.jsonPrimitive.content.also { id -> require(id.isNotBlank()) }
            }.toSet()
            val history = account.getValue("entries").jsonArray.map { item ->
                val entry = item.jsonObject
                BackupHistoryEntry(
                    id = entry.getValue("id").jsonPrimitive.content,
                    name = entry.getValue("name").jsonPrimitive.content,
                    createdAtMillis = entry.getValue("createdAtMillis").jsonPrimitive.long,
                    sizeBytes = entry.getValue("sizeBytes").jsonPrimitive.long,
                    documentCount = entry["documentCount"]?.takeUnless { it == JsonNull }?.jsonPrimitive?.int,
                ).also { it.validate() }
            }.filterNot { it.id in removed }.distinctBy { it.id }
                .sortedWith(compareByDescending<BackupHistoryEntry> { it.createdAtMillis }.thenByDescending { it.id })
            email.accountKey() to AccountHistory(history, removed)
        }
    }

    private fun encode(accounts: Map<String, AccountHistory>) = buildJsonObject {
        put("version", 1)
        put("accounts", JsonObject(accounts.mapValues { (_, history) ->
            buildJsonObject {
                put("entries", JsonArray(history.entries.map { entry ->
                    buildJsonObject {
                        put("id", entry.id)
                        put("name", entry.name)
                        put("createdAtMillis", entry.createdAtMillis)
                        put("sizeBytes", entry.sizeBytes)
                        entry.documentCount?.let { put("documentCount", it) }
                    }
                }))
                put("deletedIds", JsonArray(history.deletedIds.sorted().map(::JsonPrimitive)))
            }
        }))
    }.toString()

    private data class AccountHistory(
        val entries: List<BackupHistoryEntry> = emptyList(),
        val deletedIds: Set<String> = emptySet(),
    )
}

private fun String.accountKey() = trim().lowercase(Locale.ROOT).also { require(it.isNotEmpty()) }

private fun BackupHistoryEntry.validate() {
    require(id.isNotBlank() && name.isNotBlank())
    require(createdAtMillis >= 0L && sizeBytes >= 0L)
    require(documentCount == null || documentCount >= 0)
}
