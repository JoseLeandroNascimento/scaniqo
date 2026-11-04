package com.joseleandro.scaniqo.data.local.tag

import androidx.room.withTransaction
import com.joseleandro.scaniqo.data.local.ScaniqoDatabase
import java.text.Normalizer
import java.util.Locale
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** O marcador e as inserções são confirmados juntos; uma falha permite repetir sem um seed parcial. */
class DefaultTagInitializer(
    private val database: ScaniqoDatabase,
    private val defaultTags: () -> List<TagEntity>,
) {
    private val mutex = Mutex()
    private var initialized = false

    suspend fun initialize() = mutex.withLock {
        if (initialized) return@withLock
        database.withTransaction {
            val dao = database.tagDao()
            if (dao.defaultsInitialized()) return@withTransaction
            val names = dao.findAll().mapTo(mutableSetOf()) { normalizedName(it.name) }
            defaultTags().forEach { tag ->
                if (names.add(normalizedName(tag.name))) dao.insert(tag)
            }
            dao.markDefaultsInitialized(TagDefaultsState())
        }
        initialized = true
    }

    private fun normalizedName(name: String): String =
        Normalizer.normalize(name.trim(), Normalizer.Form.NFD)
            // Marcas de outros alfabetos (como vogais em hindi) fazem parte do nome.
            .replace(Regex("([a-zA-Z])\\p{M}+"), "$1")
            .lowercase(Locale.ROOT)
}
