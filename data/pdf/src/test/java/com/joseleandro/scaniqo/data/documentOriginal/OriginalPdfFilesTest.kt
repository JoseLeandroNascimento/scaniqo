package com.joseleandro.scaniqo.data.documentOriginal

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, manifest = Config.NONE)
class OriginalPdfFilesTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun cancellationOnTheFinalReadCannotPublishOrLeaveACopiedOriginal() = runBlocking {
        val context = isolatedContext()
        val files = OriginalPdfFiles(context, "originals")
        val source = temporary.newFile("source.pdf").apply { writeBytes(ByteArray(8192) { 37 }) }
        val uri = Uri.parse("content://original-fixture/cancel-at-eof")
        lateinit var operation: Job
        var closed = false
        shadowOf(context.contentResolver).registerInputStreamSupplier(uri) {
            object : FilterInputStream(source.inputStream()) {
                override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                    val count = `in`.read(buffer, offset, length)
                    if (count < 0) operation.cancel()
                    return count
                }
                override fun close() {
                    closed = true
                    super.close()
                }
            }
        }

        var failure: Throwable? = null
        operation = launch(start = CoroutineStart.LAZY) {
            failure = runCatching { files.copyOriginal(uri.toString()) }.exceptionOrNull()
        }
        operation.start()
        operation.join()

        assertTrue(failure is CancellationException)
        assertTrue(closed)
        assertTrue(File(context.cacheDir, "originals").listFiles().orEmpty().isEmpty())
        assertEquals(8192L, source.length())
    }

    @Test fun aProviderFailureRemovesItsPartialCopyAndClosesTheSource() = runBlocking {
        val context = isolatedContext()
        val files = OriginalPdfFiles(context, "originals")
        val source = temporary.newFile("source.pdf").apply { writeBytes(ByteArray(8192) { 37 }) }
        val uri = Uri.parse("content://original-fixture/provider-error")
        var closed = false
        shadowOf(context.contentResolver).registerInputStreamSupplier(uri) {
            object : FilterInputStream(source.inputStream()) {
                var readOnce = false
                override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                    if (readOnce) throw IOException("Synthetic provider failure")
                    readOnce = true
                    return `in`.read(buffer, offset, minOf(length, 1024))
                }
                override fun close() {
                    closed = true
                    super.close()
                }
            }
        }

        val failure = runCatching { files.copyOriginal(uri.toString()) }.exceptionOrNull()

        assertTrue(failure is IOException)
        assertTrue(closed)
        assertTrue(File(context.cacheDir, "originals").listFiles().orEmpty().isEmpty())
        assertEquals(8192L, source.length())
    }

    private fun isolatedContext(): Context {
        val cache = temporary.newFolder("cache")
        return object : ContextWrapper(RuntimeEnvironment.getApplication()) {
            override fun getApplicationContext(): Context = this
            override fun getCacheDir(): File = cache
        }
    }
}
