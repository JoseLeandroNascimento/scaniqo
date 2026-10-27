package com.joseleandro.scaniqo.data.pdf

import com.tom_roush.pdfbox.cos.COSArray
import com.tom_roush.pdfbox.cos.COSBase
import com.tom_roush.pdfbox.cos.COSBoolean
import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.cos.COSNull
import com.tom_roush.pdfbox.cos.COSNumber
import com.tom_roush.pdfbox.cos.COSObject
import com.tom_roush.pdfbox.cos.COSStream
import com.tom_roush.pdfbox.cos.COSString
import com.tom_roush.pdfbox.pdmodel.PDDocument
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.Collections
import java.util.IdentityHashMap
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import java.util.zip.InflaterInputStream
import java.util.zip.ZipException
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal object PdfStreamOptimizer {
    private const val MAX_DECODED_STREAM_BYTES = 128L * 1024 * 1024
    private const val MAX_DECODED_DOCUMENT_BYTES = 512L * 1024 * 1024
    private const val FILTER_OVERHEAD_BYTES = 32L

    suspend fun optimize(document: PDDocument) {
        val context = currentCoroutineContext()
        val nodes = reachableObjects(document.document.trailer, context)
        var remainingBytes = MAX_DECODED_DOCUMENT_BYTES
        for (stream in nodes.filterIsInstance<COSStream>()) {
            context.ensureActive()
            if (remainingBytes <= 0) break
            remainingBytes -= recompress(document, stream, remainingBytes, context)
        }
        deduplicate(nodes, context)
    }

    private fun reachableObjects(root: COSBase, context: CoroutineContext): List<COSBase> {
        val visited = Collections.newSetFromMap(IdentityHashMap<COSBase, Boolean>())
        val pending = ArrayDeque<COSBase>()
        pending.add(root)
        while (pending.isNotEmpty()) {
            context.ensureActive()
            val value = pending.removeLast()
            if (!visited.add(value)) continue
            when (value) {
                is COSObject -> value.`object`?.let(pending::add)
                is COSDictionary -> value.values.forEach { pending.add(it) }
                is COSArray -> value.forEach { pending.add(it) }
            }
        }
        return visited.toList()
    }

    private fun recompress(
        document: PDDocument,
        stream: COSStream,
        remainingBytes: Long,
        context: CoroutineContext,
    ): Long {
        if (stream.containsKey(COSName.F) || stream.length <= FILTER_OVERHEAD_BYTES ||
            stream.getCOSName(COSName.TYPE) in listOf(COSName.XREF, COSName.OBJ_STM)
        ) return 0
        val filter = when (val filters = stream.getDictionaryObject(COSName.FILTER)) {
            null, COSNull.NULL -> null
            is COSName -> filters
            is COSArray -> if (filters.size() == 1) filters.getObject(0) as? COSName ?: return 0
                else if (filters.size() == 0) null else return 0
            else -> return 0
        }
        if (filter != null && filter != COSName.FLATE_DECODE && filter != COSName.FLATE_DECODE_ABBREVIATION) return 0
        var decodedBytes = 0L
        val limit = minOf(MAX_DECODED_STREAM_BYTES, remainingBytes)
        val candidate = document.document.createCOSStream()
        try {
            val deflater = Deflater(Deflater.BEST_COMPRESSION)
            try {
                stream.createRawInputStream().use { raw ->
                    val input = if (filter == null) raw else InflaterInputStream(raw)
                    input.use {
                        candidate.createRawOutputStream().use { output ->
                            DeflaterOutputStream(
                                LimitedOutput(output, stream.length - FILTER_OVERHEAD_BYTES), deflater,
                            ).use { encoded ->
                                val buffer = ByteArray(16 * 1024)
                                while (true) {
                                    context.ensureActive()
                                    val count = input.read(buffer)
                                    if (count < 0) break
                                    decodedBytes += count
                                    if (decodedBytes > limit) throw OptimizationLimit()
                                    encoded.write(buffer, 0, count)
                                }
                            }
                        }
                    }
                }
            } catch (_: OptimizationLimit) {
                return decodedBytes
            } catch (_: ZipException) {
                return decodedBytes
            } finally {
                deflater.end()
            }
            context.ensureActive()
            candidate.createRawInputStream().use { input ->
                stream.createRawOutputStream().use { output -> copy(input, output, context) }
            }
            stream.setItem(COSName.FILTER, COSName.FLATE_DECODE)
            // Keep predictor parameters: only the outer zlib encoding changed.
            val parameters = stream.getDictionaryObject(COSName.DECODE_PARMS)
            if (filter == null) stream.removeItem(COSName.DECODE_PARMS)
            else if (parameters is COSArray) {
                stream.setItem(COSName.DECODE_PARMS, if (parameters.size() > 0) parameters.getObject(0) else null)
            }
        } finally {
            candidate.close()
        }
        return decodedBytes
    }

    private fun deduplicate(nodes: List<COSBase>, context: CoroutineContext) {
        val canonical = mutableMapOf<StreamKey, COSStream>()
        val replacements = IdentityHashMap<COSStream, COSStream>()
        for (stream in nodes.filterIsInstance<COSStream>()) {
            context.ensureActive()
            val attributes = scalarAttributes(stream) ?: continue
            val digest = MessageDigest.getInstance("SHA-256")
            stream.createRawInputStream().use { input ->
                val buffer = ByteArray(16 * 1024)
                while (true) {
                    context.ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
            val key = StreamKey(stream.length, digest.digest().toList(), attributes)
            val previous = canonical[key]
            if (previous == null) canonical[key] = stream
            else if (sameBytes(previous, stream, context)) replacements[stream] = previous
        }
        if (replacements.isEmpty()) return
        for (node in nodes) {
            context.ensureActive()
            when (node) {
                is COSDictionary -> node.keySet().toList().forEach { name ->
                    val value = node.getDictionaryObject(name)
                    if (value is COSStream) replacements[value]?.let { node.setItem(name, it) }
                }
                is COSArray -> for (index in 0 until node.size()) {
                    val value = node.getObject(index)
                    if (value is COSStream) replacements[value]?.let { node.set(index, it) }
                }
            }
        }
    }

    private fun scalarAttributes(stream: COSStream): Map<COSName, COSBase>? {
        if (stream.containsKey(COSName.F) ||
            stream.getCOSName(COSName.TYPE) in listOf(COSName.XREF, COSName.OBJ_STM)
        ) return null
        val attributes = mutableMapOf<COSName, COSBase>()
        for (key in stream.keySet()) {
            if (key == COSName.LENGTH) continue
            val value = stream.getDictionaryObject(key) ?: continue
            if (value !is COSName && value !is COSNumber && value !is COSBoolean &&
                value !is COSString && value !is COSNull
            ) return null
            attributes[key] = value
        }
        return attributes
    }

    private fun sameBytes(first: COSStream, second: COSStream, context: CoroutineContext): Boolean =
        first.createRawInputStream().use { left ->
            second.createRawInputStream().use { right ->
                val a = ByteArray(16 * 1024)
                val b = ByteArray(a.size)
                while (true) {
                    context.ensureActive()
                    val count = readChunk(left, a)
                    if (count != readChunk(right, b)) return@use false
                    if (count == 0) return@use true
                    for (index in 0 until count) if (a[index] != b[index]) return@use false
                }
                @Suppress("UNREACHABLE_CODE")
                false
            }
        }

    private fun readChunk(input: InputStream, buffer: ByteArray): Int {
        var size = 0
        while (size < buffer.size) {
            val count = input.read(buffer, size, buffer.size - size)
            if (count < 0) break
            size += count
        }
        return size
    }

    private fun copy(input: InputStream, output: OutputStream, context: CoroutineContext) {
        val buffer = ByteArray(16 * 1024)
        while (true) {
            context.ensureActive()
            val count = input.read(buffer)
            if (count < 0) break
            output.write(buffer, 0, count)
        }
    }

    private data class StreamKey(
        val length: Long,
        val digest: List<Byte>,
        val attributes: Map<COSName, COSBase>,
    )

    private class OptimizationLimit : IOException()

    private class LimitedOutput(
        private val output: OutputStream,
        private val limit: Long,
    ) : OutputStream() {
        private var written = 0L

        override fun write(value: Int) {
            if (++written > limit) throw OptimizationLimit()
            output.write(value)
        }

        override fun write(buffer: ByteArray, offset: Int, length: Int) {
            written += length
            if (written > limit) throw OptimizationLimit()
            output.write(buffer, offset, length)
        }
    }
}
