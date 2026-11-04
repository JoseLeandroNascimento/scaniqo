package com.joseleandro.scaniqo.data.documentOriginal

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import java.io.File
import java.io.FilterInputStream
import java.io.InputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.security.MessageDigest
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

/** Exercises byte-copy limits for certificate originals, not PDF parsing/rendering. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, manifest = Config.NONE)
class OriginalPdfFilesLoadTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun copiesThe50MiBBoundaryInChunksAndDiscardsOnlyTheOwnedCopy() = runBlocking {
        val context = isolatedContext()
        val files = OriginalPdfFiles(context, "originals")
        val source = temporary.newFile("boundary.pdf")
        val digest = MessageDigest.getInstance("SHA-256")
        val block = ByteArray(32 * 1024) { (it % 251).toByte() }
        source.outputStream().use { output ->
            repeat((OriginalPdfFiles.MAX_BYTES / block.size).toInt()) {
                output.write(block)
                digest.update(block)
            }
        }
        val expectedDigest = digest.digest()
        val uri = Uri.parse("content://original-load/boundary")
        lateinit var input: MeasuredInput
        shadowOf(context.contentResolver).registerInputStreamSupplier(uri) {
            MeasuredInput(source.inputStream()).also { input = it }
        }
        val started = System.nanoTime()

        val copied = files.copyOriginal(uri.toString()).first

        assertEquals(OriginalPdfFiles.MAX_BYTES, copied.length())
        assertEquals(OriginalPdfFiles.MAX_BYTES, input.bytesRead)
        assertTrue(input.closed)
        assertTrue(input.largestReadRequest <= 32 * 1024)
        assertArrayEquals(expectedDigest, sha256(copied))
        files.discard(copied)
        assertTrue(File(context.cacheDir, "originals").listFiles().orEmpty().isEmpty())
        assertArrayEquals(expectedDigest, sha256(source))
        println("LOAD_METRIC originalBoundary bytes=${input.bytesRead} maxReadRequest=${input.largestReadRequest} " +
            "elapsedMs=${(System.nanoTime() - started) / 1_000_000}")
    }

    @Test fun rejectsA512MiBSourceWithoutReadingItAllOrKeepingThePartialCopy() = runBlocking {
        val context = isolatedContext()
        val files = OriginalPdfFiles(context, "originals")
        val source = temporary.newFile("oversized.pdf")
        // A sparse source tests the logical size limit; the separate export workload writes 512 MiB.
        RandomAccessFile(source, "rw").use { it.setLength(512L * 1024 * 1024) }
        val uri = Uri.parse("content://original-load/oversized")
        lateinit var input: MeasuredInput
        shadowOf(context.contentResolver).registerInputStreamSupplier(uri) {
            MeasuredInput(source.inputStream()).also { input = it }
        }
        val started = System.nanoTime()

        val failure = runCatching { files.copyOriginal(uri.toString()) }.exceptionOrNull()

        assertTrue(failure is IOException)
        assertTrue(input.closed)
        assertTrue(input.bytesRead > OriginalPdfFiles.MAX_BYTES)
        assertTrue(input.bytesRead <= OriginalPdfFiles.MAX_BYTES + 32 * 1024)
        assertTrue(input.largestReadRequest <= 32 * 1024)
        assertTrue(File(context.cacheDir, "originals").listFiles().orEmpty().isEmpty())
        assertEquals(512L * 1024 * 1024, source.length())
        println("LOAD_METRIC originalRejected sourceBytes=${source.length()} readBytes=${input.bytesRead} " +
            "elapsedMs=${(System.nanoTime() - started) / 1_000_000}")
    }

    private fun isolatedContext(): Context {
        val cache = temporary.newFolder("cache")
        return object : ContextWrapper(RuntimeEnvironment.getApplication()) {
            override fun getApplicationContext(): Context = this
            override fun getCacheDir(): File = cache
        }
    }

    private fun sha256(file: File): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(32 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest()
    }

    private class MeasuredInput(input: InputStream) : FilterInputStream(input) {
        var bytesRead = 0L
        var largestReadRequest = 0
        var closed = false
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            largestReadRequest = maxOf(largestReadRequest, length)
            val count = `in`.read(buffer, offset, length)
            if (count > 0) bytesRead += count
            return count
        }
        override fun close() {
            closed = true
            super.close()
        }
    }
}
