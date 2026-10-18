package com.joseleandro.scaniqo.data.backup

import com.joseleandro.scaniqo.domain.model.DocumentBackupLimits
import com.joseleandro.scaniqo.domain.model.DriveBackupEntry
import com.joseleandro.scaniqo.domain.model.DriveBackupException
import com.joseleandro.scaniqo.domain.repository.DriveBackupRemote
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import java.net.URLEncoder
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * Backups manuais visíveis no Drive, limitados aos arquivos criados/abertos pelo app (drive.file).
 * Tokens e sessões de upload ficam somente na pilha da operação; nunca são gravados ou registrados.
 */
class GoogleDriveBackupRemote internal constructor(
    private val transport: DriveHttpTransport,
    private val chunkSize: Int = 1024 * 1024,
    private val retryDelay: suspend (Long) -> Unit = { delay(it) },
) : DriveBackupRemote {
    constructor() : this(UrlConnectionDriveTransport())

    private val folderMutex = Mutex()

    init { require(chunkSize > 0 && chunkSize % (256 * 1024) == 0) }

    override suspend fun accountEmail(accessToken: String): String? = withContext(Dispatchers.IO) {
        val about = jsonRequest(accessToken, endpoint("about", "fields" to "user(emailAddress)"))
        val user = about["user"] as? JsonObject
        user?.string("emailAddress")?.takeIf(String::isNotBlank)
    }

    override suspend fun listBackups(accessToken: String): List<DriveBackupEntry> = withContext(Dispatchers.IO) {
        val query = "trashed = false and mimeType = 'application/zip' and " +
            "appProperties has { key='kind' and value='scaniqo-backup' } and " +
            "appProperties has { key='version' and value='1' }"
        listFiles(accessToken, query, ENTRY_FIELDS).map(::entry)
            .distinctBy(DriveBackupEntry::id).sortedByDescending(DriveBackupEntry::modifiedTimeMillis)
    }

    override suspend fun upload(
        accessToken: String,
        archive: File,
        name: String,
        onProgress: (Long, Long) -> Unit,
    ): DriveBackupEntry = withContext(Dispatchers.IO) {
        validateToken(accessToken)
        require(archive.isFile && archive.canRead() && archive.length() > 0) { "Arquivo de backup indisponível." }
        require(name.isNotBlank() && name.length <= 255 && name.endsWith(".zip", true) &&
            name.none { it.code < 32 || it == '/' || it == '\\' }) { "Nome de backup inválido." }
        val context = currentCoroutineContext()
        val total = archive.length()
        if (total > DocumentBackupLimits.MAX_ARCHIVE_BYTES) throw DriveBackupException.Remote()
        val folder = ensureFolder(accessToken)
        val metadata = buildJsonObject {
            put("name", name)
            put("mimeType", "application/zip")
            put("parents", JsonArray(listOf(JsonPrimitive(folder))))
            putJsonObject("appProperties") {
                put("kind", "scaniqo-backup")
                put("version", "1")
            }
        }.toString().toByteArray(Charsets.UTF_8)
        val session = request(
            accessToken, "POST", UPLOAD_URL + query("uploadType" to "resumable", "fields" to ENTRY_FIELDS),
            extraHeaders = mapOf(
                "Content-Type" to "application/json; charset=UTF-8",
                "X-Upload-Content-Type" to "application/zip",
                "X-Upload-Content-Length" to total.toString(),
            ),
            bodyLength = metadata.size.toLong(), writeBody = { it.write(metadata) },
        ) { response ->
            response.header("Location") ?: throw DriveBackupException.Remote(response.status)
        }
        val sessionUri = checkedDriveUrl(session)
        if (sessionUri.path != "/upload/drive/v3/files") throw DriveBackupException.Remote()
        onProgress(0, total)
        var acknowledged = 0L
        var reported = 0L
        var failures = 0
        var probe = false
        while (true) {
            context.ensureActive()
            if (archive.length() != total) throw DriveBackupException.Remote()
            val offset = acknowledged
            val length = if (probe) 0L else minOf(chunkSize.toLong(), total - offset)
            val isProbe = probe
            val outcome = try {
                request(
                    accessToken, "PUT", session,
                    extraHeaders = mapOf(
                        "Content-Type" to "application/zip",
                        "Content-Range" to if (isProbe) "bytes */" + total
                            else "bytes " + offset + "-" + (offset + length - 1) + "/" + total,
                    ),
                    bodyLength = length,
                    writeBody = { output ->
                        if (!isProbe) {
                            RandomAccessFile(archive, "r").use { input ->
                                input.seek(offset)
                                val buffer = ByteArray(BUFFER_SIZE)
                                var written = 0L
                                while (written < length) {
                                    context.ensureActive()
                                    val count = input.read(buffer, 0, minOf(buffer.size.toLong(), length - written).toInt())
                                    if (count < 0) throw IOException("O arquivo de backup mudou.")
                                    output.write(buffer, 0, count)
                                    written += count
                                    val progress = offset + written
                                    if (progress > reported) {
                                        reported = progress
                                        onProgress(progress, total)
                                    }
                                }
                            }
                        }
                    },
                    acceptIncomplete = true,
                ) { response ->
                    if (response.status == 308) {
                        UploadOutcome.Incomplete(acknowledgedBytes(response.header("Range"), total))
                    } else {
                        UploadOutcome.Complete(entry(readJson(response.body, context)))
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: DriveBackupException) {
                if (failure !is DriveBackupException.Network &&
                    !(failure is DriveBackupException.Remote && failure.statusCode in 500..599)
                ) throw failure
                if (++failures > MAX_RECOVERY_ATTEMPTS) throw failure
                retryDelay(1_000L shl (failures - 1))
                probe = true
                continue
            }
            when (outcome) {
                is UploadOutcome.Complete -> {
                    if (outcome.value.sizeBytes != total) throw DriveBackupException.Remote()
                    context.ensureActive()
                    onProgress(total, total)
                    return@withContext outcome.value
                }
                is UploadOutcome.Incomplete -> {
                    if (outcome.bytes < acknowledged ||
                        (!isProbe && outcome.bytes > offset + length)
                    ) throw DriveBackupException.Remote()
                    if (outcome.bytes == acknowledged) {
                        if (++failures > MAX_RECOVERY_ATTEMPTS) throw DriveBackupException.Network()
                        retryDelay(1_000L shl (failures - 1))
                    } else {
                        failures = 0
                    }
                    acknowledged = outcome.bytes
                    if (acknowledged > reported) {
                        reported = acknowledged
                        onProgress(acknowledged, total)
                    }
                    // Mesmo quando todos os bytes foram recebidos, só 200/201 confirma o arquivo.
                    probe = acknowledged == total
                }
            }
        }
        @Suppress("UNREACHABLE_CODE")
        throw DriveBackupException.Remote()
    }

    override suspend fun download(
        accessToken: String,
        entry: DriveBackupEntry,
        destination: File,
        onProgress: (Long, Long) -> Unit,
    ) = withContext(Dispatchers.IO) {
        validateToken(accessToken)
        checkedId(entry.id)
        if (entry.sizeBytes !in 0..DocumentBackupLimits.MAX_ARCHIVE_BYTES) throw DriveBackupException.Remote()
        val parent = destination.absoluteFile.parentFile
            ?: throw IllegalArgumentException("Destino de backup inválido.")
        require(parent.isDirectory) { "Pasta de destino indisponível." }
        val partial = File.createTempFile(".scaniqo-drive-", ".part", parent)
        val context = currentCoroutineContext()
        try {
            onProgress(0, entry.sizeBytes)
            request(accessToken, "GET", endpoint("files/" + entry.id, "alt" to "media"),
                extraHeaders = mapOf("Accept-Encoding" to "identity")) { response ->
                val advertised = response.header("Content-Length")?.toLongOrNull()
                if (advertised != null && advertised != entry.sizeBytes) throw DriveBackupException.Remote()
                partial.outputStream().buffered().use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var received = 0L
                    while (true) {
                        context.ensureActive()
                        val count = response.body.read(buffer)
                        if (count < 0) break
                        if (received > entry.sizeBytes - count ||
                            received > DocumentBackupLimits.MAX_ARCHIVE_BYTES - count
                        ) throw DriveBackupException.Remote()
                        output.write(buffer, 0, count)
                        received += count
                        onProgress(received, entry.sizeBytes)
                    }
                    if (received != entry.sizeBytes) throw DriveBackupException.Network()
                }
            }
            context.ensureActive()
            // renameTo não remove o destino em caso de falha. No Android a substituição é atômica.
            if (!partial.renameTo(destination)) throw DriveBackupException.Network()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: DriveBackupException) {
            throw failure
        } catch (_: IOException) {
            context.ensureActive()
            throw DriveBackupException.Network()
        } finally {
            partial.delete()
        }
    }

    private suspend fun ensureFolder(token: String): String = folderMutex.withLock {
        val existing = listFiles(
            token, "trashed = false and mimeType = 'application/vnd.google-apps.folder' and " +
                "appProperties has { key='kind' and value='scaniqo-backup-folder' }", "id",
        ).firstOrNull()
        if (existing != null) return@withLock checkedId(existing.string("id"))
        val metadata = buildJsonObject {
            put("name", "Scaniqo backups")
            put("mimeType", "application/vnd.google-apps.folder")
            putJsonObject("appProperties") { put("kind", "scaniqo-backup-folder") }
        }.toString().toByteArray(Charsets.UTF_8)
        val created = jsonRequest(
            token, endpoint("files", "fields" to "id"), "POST", metadata,
        )
        checkedId(created.string("id"))
    }

    private suspend fun listFiles(token: String, filter: String, fields: String): List<JsonObject> {
        val result = mutableListOf<JsonObject>()
        val visited = mutableSetOf<String>()
        var pageToken: String? = null
        do {
            val parameters = mutableListOf(
                "q" to filter, "spaces" to "drive", "pageSize" to "1000",
                "fields" to "nextPageToken,files(" + fields + ")",
            )
            pageToken?.let { parameters += "pageToken" to it }
            val response = jsonRequest(token, endpoint("files", *parameters.toTypedArray()))
            val files = response["files"]
            if (files != null && files !is JsonArray) throw DriveBackupException.Remote()
            (files as? JsonArray)?.forEach {
                result += it as? JsonObject ?: throw DriveBackupException.Remote()
            }
            pageToken = response.string("nextPageToken")?.takeIf(String::isNotBlank)
            if (pageToken != null && !visited.add(pageToken)) throw DriveBackupException.Remote()
        } while (pageToken != null)
        return result
    }

    private suspend fun jsonRequest(
        token: String, url: String, method: String = "GET", body: ByteArray? = null,
    ): JsonObject {
        val context = currentCoroutineContext()
        return request(
            token, method, url,
            extraHeaders = if (body == null) emptyMap() else mapOf("Content-Type" to "application/json; charset=UTF-8"),
            bodyLength = body?.size?.toLong(),
            writeBody = body?.let { bytes -> { output -> output.write(bytes) } },
        ) { readJson(it.body, context) }
    }

    private suspend fun <T> request(
        token: String,
        method: String,
        url: String,
        extraHeaders: Map<String, String> = emptyMap(),
        bodyLength: Long? = null,
        writeBody: ((java.io.OutputStream) -> Unit)? = null,
        acceptIncomplete: Boolean = false,
        consume: (DriveHttpResponse) -> T,
    ): T {
        validateToken(token)
        checkedDriveUrl(url)
        val context = currentCoroutineContext()
        context.ensureActive()
        try {
            return transport.execute(DriveHttpRequest(
                method, url,
                mapOf("Authorization" to "Bearer " + token, "Accept" to "application/json") + extraHeaders,
                bodyLength, writeBody,
            )) { response ->
                context.ensureActive()
                if (response.status !in 200..299 && !(acceptIncomplete && response.status == 308)) {
                    throw remoteFailure(response, context)
                }
                consume(response)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: DriveBackupException) {
            throw failure
        } catch (_: IOException) {
            context.ensureActive()
            throw DriveBackupException.Network()
        }
    }

    private fun remoteFailure(response: DriveHttpResponse, context: CoroutineContext): DriveBackupException {
        if (response.status == 401) return DriveBackupException.Unauthorized()
        if (response.status == 429) return DriveBackupException.Quota()
        if (response.status != 403) return DriveBackupException.Remote(response.status)
        val error = try { readJson(response.body, context)["error"] as? JsonObject }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { null }
        val reasons = (error?.get("errors") as? JsonArray)?.mapNotNull {
            (it as? JsonObject)?.string("reason")
        }.orEmpty()
        return when {
            reasons.any { it in QUOTA_REASONS } -> DriveBackupException.Quota()
            reasons.any { it == "authError" || it == "insufficientPermissions" } -> DriveBackupException.Unauthorized()
            else -> DriveBackupException.Remote(response.status)
        }
    }

    private fun entry(value: JsonObject): DriveBackupEntry {
        val id = checkedId(value.string("id"))
        val name = value.string("name")?.takeIf(String::isNotBlank) ?: throw DriveBackupException.Remote()
        val size = value.string("size")?.toLongOrNull()
            ?.takeIf { it in 0..DocumentBackupLimits.MAX_ARCHIVE_BYTES } ?: throw DriveBackupException.Remote()
        val modified = parseTime(value.string("modifiedTime") ?: throw DriveBackupException.Remote())
        return DriveBackupEntry(id, name, modified, size)
    }

    private sealed interface UploadOutcome {
        data class Incomplete(val bytes: Long) : UploadOutcome
        data class Complete(val value: DriveBackupEntry) : UploadOutcome
    }

    companion object {
        const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"
        private const val ENTRY_FIELDS = "id,name,modifiedTime,size"
        private const val UPLOAD_URL = "https://www.googleapis.com/upload/drive/v3/files"
        private const val BUFFER_SIZE = 64 * 1024
        private const val MAX_RECOVERY_ATTEMPTS = 3
        private val QUOTA_REASONS = setOf(
            "storageQuotaExceeded", "dailyLimitExceeded", "rateLimitExceeded",
            "userRateLimitExceeded", "downloadQuotaExceeded", "quotaExceeded",
        )

        private fun validateToken(token: String) {
            if (token.isBlank() || token.any { it.code <= 32 || it.code >= 127 }) throw DriveBackupException.Unauthorized()
        }

        private fun checkedId(value: String?): String = value?.takeIf {
            it.isNotEmpty() && it.length <= 256 && it.all { c -> c.isLetterOrDigit() && c.code < 128 || c == '_' || c == '-' }
        } ?: throw DriveBackupException.Remote()

        private fun endpoint(resource: String, vararg parameters: Pair<String, String>): String =
            "https://www.googleapis.com/drive/v3/" + resource + query(*parameters)

        private fun query(vararg parameters: Pair<String, String>): String = if (parameters.isEmpty()) "" else
            parameters.joinToString("&", prefix = "?") { (name, value) ->
                URLEncoder.encode(name, "UTF-8") + "=" + URLEncoder.encode(value, "UTF-8")
            }

        private fun JsonObject.string(name: String): String? = (get(name) as? JsonPrimitive)?.contentOrNull

        private fun readJson(input: InputStream, context: CoroutineContext): JsonObject {
            val bytes = ByteArrayOutputStream()
            val buffer = ByteArray(8 * 1024)
            while (true) {
                context.ensureActive()
                val count = input.read(buffer)
                if (count < 0) break
                if (bytes.size() + count > 2 * 1024 * 1024) throw DriveBackupException.Remote()
                bytes.write(buffer, 0, count)
            }
            return try { Json.parseToJsonElement(bytes.toString("UTF-8")) as? JsonObject
                ?: throw DriveBackupException.Remote() }
            catch (_: IllegalArgumentException) { throw DriveBackupException.Remote() }
        }

        private fun acknowledgedBytes(range: String?, total: Long): Long {
            if (range == null) return 0
            val match = Regex("bytes=0-([0-9]+)").matchEntire(range.trim())
                ?: throw DriveBackupException.Remote()
            val last = match.groupValues[1].toLongOrNull() ?: throw DriveBackupException.Remote()
            if (last < 0 || last >= total) throw DriveBackupException.Remote()
            return last + 1
        }

        private fun parseTime(value: String): Long {
            val match = Regex("(\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2})(?:\\.(\\d{1,9}))?(Z|[+-]\\d{2}:\\d{2})")
                .matchEntire(value) ?: throw DriveBackupException.Remote()
            val normalized = match.groupValues[1] + "." +
                match.groupValues[2].padEnd(3, '0').take(3) + match.groupValues[3]
            val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US).apply {
                isLenient = false
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val position = ParsePosition(0)
            val date = parser.parse(normalized, position)
            if (date == null || position.index != normalized.length) throw DriveBackupException.Remote()
            return date.time
        }
    }
}
