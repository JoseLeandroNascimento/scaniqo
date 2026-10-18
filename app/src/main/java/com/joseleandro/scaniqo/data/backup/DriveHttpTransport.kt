package com.joseleandro.scaniqo.data.backup

import com.joseleandro.scaniqo.domain.model.DriveBackupException
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URI
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** Não é data class: toString nunca deve imprimir Authorization ou a URL da sessão. */
internal class DriveHttpRequest(
    val method: String,
    val url: String,
    val headers: Map<String, String>,
    val bodyLength: Long? = null,
    val writeBody: ((OutputStream) -> Unit)? = null,
)

internal class DriveHttpResponse(
    val status: Int,
    val headers: Map<String, String>,
    val body: InputStream,
) {
    fun header(name: String): String? = headers.entries.firstOrNull { it.key.equals(name, true) }?.value
}

/** O corpo da resposta pertence ao transporte e só pode ser lido dentro de consume. */
internal interface DriveHttpTransport {
    suspend fun <T> execute(request: DriveHttpRequest, consume: (DriveHttpResponse) -> T): T
}

internal class UrlConnectionDriveTransport : DriveHttpTransport {
    override suspend fun <T> execute(request: DriveHttpRequest, consume: (DriveHttpResponse) -> T): T =
        withContext(Dispatchers.IO) {
            val operationContext = currentCoroutineContext()
            try {
                if (request.writeBody != null) {
                    // Limita escritas bloqueadas por chunk; não limita o tempo total de downloads.
                    withTimeout(120_000L) { exchange(request, consume) }
                } else {
                    exchange(request, consume)
                }
            } catch (_: TimeoutCancellationException) {
                operationContext.ensureActive()
                throw SocketTimeoutException("Tempo limite da conexão com o Google Drive.")
            }
        }

    private suspend fun <T> exchange(request: DriveHttpRequest, consume: (DriveHttpResponse) -> T): T =
        coroutineScope {
            val connection = checkedDriveUrl(request.url).toURL().openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 20_000
            connection.readTimeout = 30_000
            connection.useCaches = false
            connection.requestMethod = request.method
            request.headers.forEach(connection::setRequestProperty)
            // A desconexão também interrompe leituras/escritas bloqueadas ao cancelar.
            val cancellation = launch(start = CoroutineStart.UNDISPATCHED) {
                try {
                    awaitCancellation()
                } finally {
                    runCatching { connection.disconnect() }
                }
            }
            try {
                currentCoroutineContext().ensureActive()
                request.writeBody?.let { write ->
                    connection.doOutput = true
                    connection.setFixedLengthStreamingMode(requireNotNull(request.bodyLength))
                    connection.outputStream.use(write)
                }
                val status = connection.responseCode
                currentCoroutineContext().ensureActive()
                val headers = connection.headerFields.entries.mapNotNull { (name, values) ->
                    if (name == null || values.isNullOrEmpty()) null else name to values.first()
                }.toMap()
                val input = if (status >= 400) connection.errorStream else connection.inputStream
                (input ?: ByteArrayInputStream(ByteArray(0))).use {
                    consume(DriveHttpResponse(status, headers, it))
                }
            } finally {
                cancellation.cancel()
                connection.disconnect()
            }
        }
}

/** O token nunca segue redirects ou hosts fornecidos livremente pelo servidor. */
internal fun checkedDriveUrl(value: String): URI {
    val uri = try { URI(value) } catch (_: Exception) { throw DriveBackupException.Remote() }
    if (!uri.scheme.equals("https", true) || uri.host?.lowercase() !in ALLOWED_HOSTS ||
        uri.rawUserInfo != null || uri.rawFragment != null || (uri.port != -1 && uri.port != 443)
    ) throw DriveBackupException.Remote()
    return uri
}

private val ALLOWED_HOSTS = setOf("www.googleapis.com", "drive.googleapis.com", "content.googleapis.com")
