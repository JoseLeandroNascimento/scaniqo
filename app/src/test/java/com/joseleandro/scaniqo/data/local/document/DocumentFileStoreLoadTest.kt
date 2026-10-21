package com.joseleandro.scaniqo.data.local.document

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import com.joseleandro.scaniqo.domain.model.DocumentModel
import java.io.File
import java.io.FilterInputStream
import java.io.InputStream
import java.security.DigestOutputStream
import java.security.MessageDigest
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Opt-in disk workload. Every byte is generated inside JUnit's temporary directory. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class DocumentFileStoreLoadTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun exportsA512MiBPdfWithBoundedReadsAndIdenticalPersistedBytes() = runBlocking {
        val context = isolatedContext()
        val fixture = pdfFixture(temporary.newFile("large-source.pdf"), 512L * MIB)
        val target = temporary.newFile("exported.pdf")
        val sourceUri = Uri.parse("content://load-source/large.pdf")
        val targetUri = Uri.parse("content://load-export/large.pdf")
        lateinit var source: MeasuredInput
        shadowOf(context.contentResolver).registerInputStreamSupplier(sourceUri) {
            MeasuredInput(fixture.file.inputStream()).also { source = it }
        }
        shadowOf(context.contentResolver).registerOutputStreamSupplier(targetUri) { target.outputStream() }
        val started = System.nanoTime()

        AndroidDocumentFileStore(context, Dispatchers.Unconfined)
            .export(document(sourceUri, fixture.file.length()), targetUri.toString())

        val elapsedMillis = (System.nanoTime() - started) / 1_000_000
        assertEquals(fixture.file.length(), target.length())
        assertEquals(fixture.file.length(), source.bytesRead)
        assertTrue("Source descriptor must close after export", source.closed)
        assertTrue("Copy must not request a file-sized allocation", source.largestReadRequest <= 64 * 1024)
        assertArrayEquals(fixture.sha256, sha256(target))
        println("LOAD_METRIC exportPdf bytes=${target.length()} reads=${source.readCalls} " +
            "maxReadRequest=${source.largestReadRequest} elapsedMs=$elapsedMillis " +
            "peakObservedHeapBytes=${source.peakObservedHeapBytes}")
    }

    @Test fun cancellingALargeShareClosesItsSourceAndRemovesOnlyTheNewCopy() = runBlocking {
        val context = isolatedContext()
        val fixture = pdfFixture(temporary.newFile("cancel-source.pdf"), 64L * MIB)
        val sourceUri = Uri.parse("content://load-source/cancel.pdf")
        val shareRoot = File(context.cacheDir, "shared_documents")
        val retained = File(shareRoot, "previous-share").apply { mkdirs() }
        val retainedFile = File(retained, "retained.pdf").apply { writeText("previous user share") }
        lateinit var operation: Job
        lateinit var source: MeasuredInput
        shadowOf(context.contentResolver).registerInputStreamSupplier(sourceUri) {
            MeasuredInput(fixture.file.inputStream()) { copied ->
                if (copied >= 8L * MIB) operation.cancel()
            }.also { source = it }
        }

        var failure: Throwable? = null
        operation = launch(start = CoroutineStart.LAZY) {
            failure = runCatching {
                AndroidDocumentFileStore(context, Dispatchers.Unconfined)
                    .prepareShare(document(sourceUri, fixture.file.length()))
            }.exceptionOrNull()
        }
        operation.start()
        operation.join()

        assertTrue(failure is CancellationException)
        assertTrue(source.closed)
        assertTrue("Cancellation must stop before the source is exhausted", source.bytesRead < fixture.file.length())
        assertEquals(listOf("previous-share"), shareRoot.listFiles().orEmpty().map { it.name })
        assertEquals("previous user share", retainedFile.readText())
        assertArrayEquals(fixture.sha256, sha256(fixture.file))
        println("LOAD_METRIC cancelledShare sourceBytes=${fixture.file.length()} copiedBytes=${source.bytesRead}")
    }

    private fun isolatedContext(): Context {
        val cache = temporary.newFolder("cache")
        val files = temporary.newFolder("files")
        return object : ContextWrapper(RuntimeEnvironment.getApplication()) {
            override fun getApplicationContext(): Context = this
            override fun getCacheDir(): File = cache
            override fun getFilesDir(): File = files
        }
    }

    private fun document(uri: Uri, size: Long) = DocumentModel(
        name = "Synthetic large PDF", pdfUri = uri.toString(), pageCount = 1,
        createdAt = 1L, sizeBytes = size,
    )

    private data class PdfFixture(val file: File, val sha256: ByteArray)

    /** A valid one-page PDF whose content consists solely of streamed PDF comments. */
    private fun pdfFixture(file: File, contentBytes: Long): PdfFixture {
        require(contentBytes % 64L == 0L)
        val digest = MessageDigest.getInstance("SHA-256")
        var position = 0L
        val offsets = mutableListOf<Long>()
        DigestOutputStream(file.outputStream(), digest).use { output ->
            fun ascii(value: String) {
                val bytes = value.toByteArray(Charsets.US_ASCII)
                output.write(bytes)
                position += bytes.size
            }
            fun obj(number: Int, body: String) {
                offsets += position
                ascii("$number 0 obj\n$body\nendobj\n")
            }
            ascii("%PDF-1.4\n")
            obj(1, "<< /Type /Catalog /Pages 2 0 R >>")
            obj(2, "<< /Type /Pages /Kids [3 0 R] /Count 1 >>")
            obj(3, "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents 4 0 R >>")
            offsets += position
            ascii("4 0 obj\n<< /Length $contentBytes >>\nstream\n")
            val block = ByteArray(64 * 1024) { index ->
                when (index % 64) { 0 -> '%'.code.toByte(); 63 -> '\n'.code.toByte(); else -> 'x'.code.toByte() }
            }
            var remaining = contentBytes
            while (remaining > 0L) {
                val count = minOf(remaining, block.size.toLong()).toInt()
                output.write(block, 0, count)
                position += count
                remaining -= count
            }
            ascii("\nendstream\nendobj\n")
            val xref = position
            ascii("xref\n0 5\n0000000000 65535 f \n")
            offsets.forEach { ascii(String.format(Locale.ROOT, "%010d 00000 n \n", it)) }
            ascii("trailer\n<< /Size 5 /Root 1 0 R >>\nstartxref\n$xref\n%%EOF\n")
        }
        return PdfFixture(file, digest.digest())
    }

    private fun sha256(file: File): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest()
    }

    private class MeasuredInput(input: InputStream, val onRead: (Long) -> Unit = {}) : FilterInputStream(input) {
        var bytesRead = 0L
        var readCalls = 0L
        var largestReadRequest = 0
        var peakObservedHeapBytes = 0L
        var closed = false

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            largestReadRequest = maxOf(largestReadRequest, length)
            val count = `in`.read(buffer, offset, length)
            readCalls++
            if (count > 0) {
                bytesRead += count
                onRead(bytesRead)
                if (readCalls % 128L == 0L) {
                    val runtime = Runtime.getRuntime()
                    peakObservedHeapBytes = maxOf(peakObservedHeapBytes, runtime.totalMemory() - runtime.freeMemory())
                }
            }
            return count
        }

        override fun close() {
            closed = true
            super.close()
        }
    }

    private companion object { const val MIB = 1024L * 1024 }
}
