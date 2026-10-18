package com.joseleandro.scaniqo.data.backup

import com.joseleandro.scaniqo.domain.model.DocumentBackupLimits
import com.joseleandro.scaniqo.domain.model.DriveBackupEntry
import com.joseleandro.scaniqo.domain.model.DriveBackupException
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.net.URLDecoder
import java.util.ArrayDeque
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class GoogleDriveBackupRemoteTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun listUsesPrivatePropertiesAndFollowsEncodedPageTokens() = runTest {
        val transport = FakeTransport(
            { request, _ ->
                val query = parameters(request.url)
                assertEquals("drive", query["spaces"])
                assertTrue(query.getValue("q").contains("key='kind' and value='scaniqo-backup'"))
                assertTrue(query.getValue("q").contains("key='version' and value='1'"))
                assertTrue(query.getValue("q").contains("trashed = false"))
                json("""{"files":[""" + metadata("old", size = 7, time = "2024-01-01T00:00:00Z") +
                    """],"nextPageToken":"next+/="}""")
            },
            { request, _ ->
                assertEquals("next+/=", parameters(request.url)["pageToken"])
                json("""{"files":[""" + metadata("new", size = 9, time = "2024-01-01T02:00:01.123456+02:00") + "]}")
            },
        )
        val entries = remote(transport).listBackups(TOKEN)
        assertEquals(listOf("new", "old"), entries.map { it.id })
        assertEquals(1_123L, entries[0].modifiedTimeMillis - entries[1].modifiedTimeMillis)
        assertEquals(listOf(9L, 7L), entries.map { it.sizeBytes })
        assertTrue(transport.requests.all { it.headers["Authorization"] == "Bearer " + TOKEN })
        transport.assertComplete()
    }

    @Test fun duplicatePaginationTokenFailsInsteadOfLooping() = runTest {
        val transport = FakeTransport(
            { _, _ -> json("""{"files":[],"nextPageToken":"same"}""") },
            { _, _ -> json("""{"files":[],"nextPageToken":"same"}""") },
        )
        expect<DriveBackupException.Remote> { remote(transport).listBackups(TOKEN) }
        transport.assertComplete()
    }

    @Test fun accountEmailUsesOnlyDriveAboutAndHandlesAbsentEmail() = runTest {
        val transport = FakeTransport(
            { request, _ ->
                assertEquals("/drive/v3/about", URI(request.url).path)
                assertEquals(mapOf("fields" to "user(emailAddress)"), parameters(request.url))
                json("""{"user":{"emailAddress":"person@example.com"}}""")
            },
            { _, _ -> json("""{"user":{}}""") },
        )
        val remote = remote(transport)
        assertEquals("person@example.com", remote.accountEmail(TOKEN))
        assertEquals(null, remote.accountEmail(TOKEN))
    }

    @Test fun uploadCreatesVisibleFolderAndLabelsTheZip() = runTest {
        val archive = archive(150)
        val transport = FakeTransport(
            { request, _ ->
                assertTrue(parameters(request.url).getValue("q").contains("scaniqo-backup-folder"))
                json("""{"files":[]}""")
            },
            { request, body ->
                assertEquals("POST", request.method)
                assertEquals("/drive/v3/files", URI(request.url).path)
                val metadata = body.toString(Charsets.UTF_8)
                assertTrue(metadata.contains("Scaniqo backups"))
                assertTrue(metadata.contains("application/vnd.google-apps.folder"))
                json("""{"id":"folder"}""")
            },
            { request, body ->
                assertEquals("resumable", parameters(request.url)["uploadType"])
                assertEquals("150", request.headers["X-Upload-Content-Length"])
                assertEquals("application/zip", request.headers["X-Upload-Content-Type"])
                val metadata = body.toString(Charsets.UTF_8)
                assertTrue(metadata.contains("\"parents\":[\"folder\"]"))
                assertTrue(metadata.contains("\"kind\":\"scaniqo-backup\""))
                assertTrue(metadata.contains("\"version\":\"1\""))
                assertTrue(metadata.contains("backup.zip"))
                Reply(200, mapOf("Location" to SESSION))
            },
            { request, body ->
                assertEquals("PUT", request.method)
                assertEquals("bytes 0-149/150", request.headers["Content-Range"])
                assertEquals(150L, request.bodyLength)
                assertArrayEquals(archive.readBytes(), body)
                json(metadata("backup", size = 150), status = 201)
            },
        )
        val progress = mutableListOf<Pair<Long, Long>>()
        val entry = remote(transport).upload(TOKEN, archive, "backup.zip") { done, total -> progress += done to total }
        assertEquals("backup", entry.id)
        assertEquals(0L to 150L, progress.first())
        assertEquals(150L to 150L, progress.last())
        transport.assertComplete()
    }

    @Test fun uploadUsesAcknowledgedRangeRatherThanAssumingWholeChunkWasReceived() = runTest {
        val archive = archive(CHUNK * 2 + 17)
        val bytes = archive.readBytes()
        val total = bytes.size.toLong()
        val transport = FakeTransport(
            existingFolder(), session(),
            { request, body ->
                assertEquals("bytes 0-262143/" + total, request.headers["Content-Range"])
                assertArrayEquals(bytes.copyOfRange(0, CHUNK), body)
                Reply(308, mapOf("Range" to "bytes=0-131071"))
            },
            { request, body ->
                assertEquals("bytes 131072-393215/" + total, request.headers["Content-Range"])
                assertArrayEquals(bytes.copyOfRange(CHUNK / 2, CHUNK + CHUNK / 2), body)
                Reply(308, mapOf("Range" to "bytes=0-393215"))
            },
            { request, body ->
                assertEquals("bytes 393216-" + (total - 1) + "/" + total, request.headers["Content-Range"])
                assertArrayEquals(bytes.copyOfRange(CHUNK + CHUNK / 2, bytes.size), body)
                json(metadata("backup", size = total))
            },
        )
        val progress = mutableListOf<Long>()
        remote(transport).upload(TOKEN, archive, "backup.zip") { done, _ -> progress += done }
        assertTrue(progress.zipWithNext().all { (previous, next) -> next >= previous })
        transport.assertComplete()
    }

    @Test fun interruptedUploadProbesSessionAndResumesWithoutCreatingAnotherFile() = runTest {
        val archive = archive(CHUNK + 27)
        val transport = FakeTransport(
            existingFolder(), session(),
            { _, _ -> throw IOException("simulated interruption") },
            { request, body ->
                assertEquals(SESSION, request.url)
                assertEquals("bytes */" + archive.length(), request.headers["Content-Range"])
                assertEquals(0L, request.bodyLength)
                assertTrue(body.isEmpty())
                Reply(308, mapOf("Range" to "bytes=0-131071"))
            },
            { request, body ->
                assertEquals("bytes 131072-" + (archive.length() - 1) + "/" + archive.length(), request.headers["Content-Range"])
                assertArrayEquals(archive.readBytes().copyOfRange(131072, archive.length().toInt()), body)
                json(metadata("backup", size = archive.length()))
            },
        )
        remote(transport).upload(TOKEN, archive, "backup.zip")
        assertEquals(1, transport.requests.count { it.method == "POST" })
        transport.assertComplete()
    }

    @Test fun serverFailureAfterFinalChunkCanRecoverAlreadyCommittedUpload() = runTest {
        val archive = archive(123)
        val transport = FakeTransport(
            existingFolder(), session(),
            { _, _ -> Reply(503) },
            { request, body ->
                assertEquals("bytes */123", request.headers["Content-Range"])
                assertTrue(body.isEmpty())
                json(metadata("backup", size = 123))
            },
        )
        assertEquals("backup", remote(transport).upload(TOKEN, archive, "backup.zip").id)
        assertEquals(4, transport.requests.size)
    }

    @Test fun unacknowledgedChunksHaveBoundedRetries() = runTest {
        val transport = FakeTransport(
            existingFolder(), session(),
            { _, _ -> Reply(308) }, { _, _ -> Reply(308) },
            { _, _ -> Reply(308) }, { _, _ -> Reply(308) },
        )
        expect<DriveBackupException.Network> { remote(transport).upload(TOKEN, archive(123), "backup.zip") }
        transport.assertComplete()
    }

    @Test fun unsafeSessionUrlsNeverReceiveCredentialsOrFileBytes() = runTest {
        for (url in listOf(
            "http://www.googleapis.com/upload/drive/v3/files",
            "https://www.googleapis.com.evil.example/upload/drive/v3/files",
            "https://www.googleapis.com@evil.example/upload/drive/v3/files",
            "https://www.googleapis.com:444/upload/drive/v3/files",
            "https://www.googleapis.com/upload/drive/v3/files#fragment",
            "https://www.googleapis.com/other-service",
        )) {
            val transport = FakeTransport(existingFolder(), { _, _ -> Reply(200, mapOf("Location" to url)) })
            expect<DriveBackupException.Remote> { remote(transport).upload(TOKEN, archive(16), "backup.zip") }
            assertEquals(2, transport.requests.size)
            assertTrue(transport.requests.none { it.method == "PUT" })
        }
    }

    @Test fun invalidAcknowledgementCannotSkipLocalArchiveBytes() = runTest {
        val transport = FakeTransport(
            existingFolder(), session(),
            { _, _ -> Reply(308, mapOf("Range" to "bytes=0-99999999")) },
        )
        expect<DriveBackupException.Remote> { remote(transport).upload(TOKEN, archive(123), "backup.zip") }
    }

    @Test fun uploadCancellationDoesNotProbeRetryOrDeleteRemoteFiles() = runTest {
        val archive = archive(CHUNK + 1)
        val transport = FakeTransport(existingFolder(), session(), { _, _ -> error("must cancel while streaming") })
        expect<CancellationException> {
            remote(transport).upload(TOKEN, archive, "backup.zip") { done, _ ->
                if (done > 0) throw CancellationException("cancel")
            }
        }
        assertEquals(3, transport.requests.size)
        assertFalse(transport.requests.any { it.method == "DELETE" })
    }

    @Test fun downloadStreamsAndPublishesOnlyCompleteBytes() = runTest {
        val bytes = ByteArray(CHUNK + 17) { (it % 251).toByte() }
        val transport = FakeTransport({ request, _ ->
            assertEquals("GET", request.method)
            assertEquals("/drive/v3/files/backup", URI(request.url).path)
            assertEquals("media", parameters(request.url)["alt"])
            Reply(200, mapOf("Content-Length" to bytes.size.toString()), bytes)
        })
        val destination = File(temporary.root, "download.zip")
        val progress = mutableListOf<Long>()
        remote(transport).download(TOKEN, entry(bytes.size.toLong()), destination) { done, _ -> progress += done }
        assertArrayEquals(bytes, destination.readBytes())
        assertEquals(bytes.size.toLong(), progress.last())
        assertNoPartialFiles()
    }

    @Test fun truncatedDownloadPreservesExistingDestinationAndCleansPartialFile() = runTest {
        val destination = temporary.newFile("existing.zip").apply { writeText("original") }
        val transport = FakeTransport({ _, _ -> Reply(200, body = byteArrayOf(1, 2, 3)) })
        expect<DriveBackupException.Network> {
            remote(transport).download(TOKEN, entry(100), destination)
        }
        assertEquals("original", destination.readText())
        assertNoPartialFiles()
    }

    @Test fun oversizedDownloadIsRejectedWithoutReplacingDestination() = runTest {
        val destination = temporary.newFile("existing.zip").apply { writeText("original") }
        val transport = FakeTransport({ _, _ -> Reply(200, body = ByteArray(101)) })
        expect<DriveBackupException.Remote> {
            remote(transport).download(TOKEN, entry(100), destination)
        }
        assertEquals("original", destination.readText())
        assertNoPartialFiles()
    }

    @Test fun cancelledDownloadClosesStreamAndRemovesPartialBytes() = runTest {
        val input = TrackingInputStream(ByteArray(CHUNK))
        val transport = FakeTransport({ _, _ -> Reply(200, stream = input) })
        val destination = File(temporary.root, "cancelled.zip")
        expect<CancellationException> {
            remote(transport).download(TOKEN, entry(CHUNK.toLong()), destination) { done, _ ->
                if (done > 0) throw CancellationException("cancel")
            }
        }
        assertTrue(input.closed)
        assertFalse(destination.exists())
        assertNoPartialFiles()
    }

    @Test fun unauthorizedQuotaAndRemoteErrorsAreTypedWithoutReturningServerSecrets() = runTest {
        val cases = listOf(
            401 to DriveBackupException.Unauthorized::class.java,
            403 to DriveBackupException.Quota::class.java,
            429 to DriveBackupException.Quota::class.java,
            500 to DriveBackupException.Remote::class.java,
            302 to DriveBackupException.Remote::class.java,
        )
        for ((status, expected) in cases) {
            val body = """{"error":{"message":"secret-token","errors":[{"reason":"storageQuotaExceeded"}]}}"""
            val transport = FakeTransport({ _, _ -> json(body, status) })
            val error = expect<DriveBackupException> { remote(transport).accountEmail(TOKEN) }
            assertEquals(expected, error.javaClass)
            assertFalse(error.toString().contains("secret-token"))
            assertFalse(error.toString().contains(TOKEN))
            assertEquals(1, transport.requests.size)
        }
        val noPermission = FakeTransport({ _, _ ->
            json("""{"error":{"errors":[{"reason":"insufficientPermissions"}]}}""", 403)
        })
        expect<DriveBackupException.Unauthorized> { remote(noPermission).accountEmail(TOKEN) }
    }

    @Test fun malformedMetadataAndConnectionFailureAreTypedAndSanitizedAcrossExceptionChains() = runTest {
        val malformedBody = "not JSON with secret-response-body " + TOKEN + " " + SESSION
        val metadataError = expect<DriveBackupException.Remote> {
            remote(FakeTransport({ _, _ -> json(malformedBody) })).listBackups(TOKEN)
        }
        val connectionFailure = IOException(
            "secret-connection-detail",
            IOException("secret-nested-detail " + TOKEN + " " + SESSION),
        ).apply { addSuppressed(IOException("secret-suppressed-detail")) }
        val networkError = expect<DriveBackupException.Network> {
            remote(FakeTransport({ _, _ -> throw connectionFailure })).accountEmail(TOKEN)
        }
        // Coroutine stacktrace recovery may attach a sanitized copy as the cause.
        // Check the full printable graph, including causes and suppressed exceptions.
        for (error in listOf(metadataError, networkError)) {
            val diagnostics = error.stackTraceToString()
            for (secret in listOf(
                "secret-response-body", "secret-connection-detail",
                "secret-nested-detail", "secret-suppressed-detail", TOKEN, SESSION,
            )) {
                assertFalse("Exception diagnostics must omit sensitive transport details", diagnostics.contains(secret))
            }
        }
    }

    @Test fun blankOrHeaderInjectedTokensNeverReachTransport() = runTest {
        val transport = FakeTransport()
        for (token in listOf("", "  ", "token\r\nX-Injected: value")) {
            expect<DriveBackupException.Unauthorized> { remote(transport).listBackups(token) }
        }
        assertTrue(transport.requests.isEmpty())
    }

    @Test fun archiveSizeLimitRejectsUploadAndDownloadBeforeAnyRemoteRequest() = runTest {
        val transport = FakeTransport()
        val oversized = object : File(temporary.newFile().path) {
            override fun length(): Long = DocumentBackupLimits.MAX_ARCHIVE_BYTES + 1
        }
        expect<DriveBackupException.Remote> {
            remote(transport).upload(TOKEN, oversized, "backup.zip")
        }
        expect<DriveBackupException.Remote> {
            remote(transport).download(TOKEN, entry(DocumentBackupLimits.MAX_ARCHIVE_BYTES + 1),
                File(temporary.root, "oversized.zip"))
        }
        assertTrue(transport.requests.isEmpty())
        assertNoPartialFiles()
    }

    @Test fun oversizedRemoteMetadataIsRejectedBeforeItCanBeSelectedForDownload() = runTest {
        val transport = FakeTransport({ _, _ ->
            json("""{"files":[""" + metadata("too-large", DocumentBackupLimits.MAX_ARCHIVE_BYTES + 1) + "]}")
        })
        expect<DriveBackupException.Remote> { remote(transport).listBackups(TOKEN) }
        transport.assertComplete()
    }

    @Test fun cancellingTheCoroutineDuringProgressStopsStreamingAndClosesPartialFile() = runTest {
        val input = TrackingInputStream(ByteArray(CHUNK))
        val transport = FakeTransport({ _, _ -> Reply(200, stream = input) })
        val destination = File(temporary.root, "job-cancelled.zip")
        val operation = launch {
            val activeJob = currentCoroutineContext().job
            remote(transport).download(TOKEN, entry(CHUNK.toLong()), destination) { done, _ ->
                if (done > 0) activeJob.cancel()
            }
        }
        operation.join()
        assertTrue(operation.isCancelled)
        assertTrue(input.closed)
        assertFalse(destination.exists())
        assertNoPartialFiles()
    }

    private fun archive(size: Int): File = temporary.newFile().apply {
        writeBytes(ByteArray(size) { (it % 251).toByte() })
    }

    private fun assertNoPartialFiles() {
        assertTrue(temporary.root.listFiles().orEmpty().none { it.name.endsWith(".part") })
    }

    private fun remote(transport: FakeTransport) = GoogleDriveBackupRemote(transport, CHUNK) { }

    private fun existingFolder(): suspend (DriveHttpRequest, ByteArray) -> Reply =
        { _, _ -> json("""{"files":[{"id":"folder"}]}""") }

    private fun session(): suspend (DriveHttpRequest, ByteArray) -> Reply =
        { _, _ -> Reply(200, mapOf("Location" to SESSION)) }

    private fun entry(size: Long) = DriveBackupEntry("backup", "backup.zip", 0, size)

    private fun metadata(id: String, size: Long, time: String = "2024-01-01T00:00:00Z") =
        """{"id":"""" + id + """","name":"backup.zip","modifiedTime":"""" + time + """","size":"""" + size + """"}"""

    private fun json(value: String, status: Int = 200) = Reply(status, body = value.toByteArray())

    private fun parameters(url: String): Map<String, String> =
        URI(url).rawQuery.orEmpty().split('&').filter(String::isNotEmpty).associate {
            val values = it.split('=', limit = 2)
            URLDecoder.decode(values[0], "UTF-8") to URLDecoder.decode(values.getOrElse(1) { "" }, "UTF-8")
        }

    private suspend inline fun <reified T : Throwable> expect(noinline action: suspend () -> Unit): T {
        try { action() } catch (failure: Throwable) {
            if (failure is T) return failure
            throw AssertionError("Expected " + T::class.java.simpleName + ", received " + failure.javaClass.simpleName, failure)
        }
        throw AssertionError("Expected " + T::class.java.simpleName)
    }

    private class Reply(
        val status: Int,
        val headers: Map<String, String> = emptyMap(),
        val body: ByteArray = ByteArray(0),
        val stream: InputStream? = null,
    )

    private class FakeTransport(vararg steps: suspend (DriveHttpRequest, ByteArray) -> Reply) : DriveHttpTransport {
        private val steps = ArrayDeque(steps.toList())
        val requests = mutableListOf<DriveHttpRequest>()

        override suspend fun <T> execute(request: DriveHttpRequest, consume: (DriveHttpResponse) -> T): T {
            requests += request
            val bytes = ByteArrayOutputStream().also { request.writeBody?.invoke(it) }.toByteArray()
            if (request.bodyLength != null) assertEquals(request.bodyLength, bytes.size.toLong())
            val reply = steps.removeFirst()(request, bytes)
            return (reply.stream ?: ByteArrayInputStream(reply.body)).use {
                consume(DriveHttpResponse(reply.status, reply.headers, it))
            }
        }

        fun assertComplete() { assertTrue("Unconsumed fake HTTP responses", steps.isEmpty()) }
    }

    private class TrackingInputStream(bytes: ByteArray) : ByteArrayInputStream(bytes) {
        var closed = false
        override fun close() { closed = true; super.close() }
    }

    private companion object {
        const val CHUNK = 256 * 1024
        const val TOKEN = "unit-test-access-token"
        const val SESSION = "https://www.googleapis.com/upload/drive/v3/files?uploadType=resumable&upload_id=local"
    }
}
