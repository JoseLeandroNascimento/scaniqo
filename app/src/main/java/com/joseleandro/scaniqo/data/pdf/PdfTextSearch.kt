package com.joseleandro.scaniqo.data.pdf

import com.tom_roush.pdfbox.contentstream.operator.Operator
import com.tom_roush.pdfbox.cos.COSBase
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import java.io.Writer
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal data class PdfTextMatch(val snippet: String, val pageNumber: Int)

internal suspend fun findPdfTextMatch(document: PDDocument, query: String): PdfTextMatch? {
    val context = currentCoroutineContext()
    context.ensureActive()
    val writer = PdfSearchSnippetWriter(query, context)
    val stripper = object : PDFTextStripper() {
        override fun startPage(page: PDPage) {
            context.ensureActive()
            writer.startPage(currentPageNo)
        }

        override fun endPage(page: PDPage) {
            writer.finishPage()
        }

        override fun processOperator(operator: Operator, operands: MutableList<COSBase>) {
            context.ensureActive()
            super.processOperator(operator, operands)
        }

        override fun processTextPosition(text: TextPosition) {
            context.ensureActive()
            super.processTextPosition(text)
        }
    }.apply { sortByPosition = true }
    return try {
        // O extrator lê somente operadores de texto: não renderiza páginas nem executa OCR.
        stripper.writeText(document, writer)
        null
    } catch (found: PdfTextMatchFound) {
        found.match
    }
}

/** Mantém apenas uma janela de texto e encerra a leitura após o primeiro trecho encontrado. */
internal class PdfSearchSnippetWriter(
    query: String,
    private val context: CoroutineContext
) : Writer() {
    private val query = normalizeSearchWhitespace(query).also { require(it.isNotEmpty()) }
    private val buffer = StringBuilder()
    private var pageNumber = 1
    private var discardedCharacters = false
    private var pendingMatch = -1

    fun startPage(pageNumber: Int) {
        context.ensureActive()
        this.pageNumber = pageNumber
        buffer.clear()
        discardedCharacters = false
        pendingMatch = -1
    }

    override fun write(chars: CharArray, offset: Int, length: Int) {
        context.ensureActive()
        for (index in offset until offset + length) {
            val character = chars[index]
            if (character.isWhitespace()) {
                if (buffer.isNotEmpty() && buffer.last() != ' ') buffer.append(' ')
            } else {
                buffer.append(character)
            }
            if (pendingMatch >= 0) {
                if (buffer.length >= pendingMatch + query.length + CONTEXT_LENGTH) {
                    finishMatch(hasMoreText = true)
                }
            } else if (buffer.length >= query.length + CONTEXT_LENGTH + SCAN_CHUNK_LENGTH) {
                scan()
            }
            if ((index - offset) % SCAN_CHUNK_LENGTH == 0) context.ensureActive()
        }
    }

    fun finishPage() {
        context.ensureActive()
        if (pendingMatch < 0) scan()
        if (pendingMatch >= 0) finishMatch(hasMoreText = false)
    }

    override fun flush() = Unit
    override fun close() = Unit

    private fun scan() {
        pendingMatch = buffer.indexOf(query, ignoreCase = true)
        if (pendingMatch >= 0) {
            if (buffer.length >= pendingMatch + query.length + CONTEXT_LENGTH) {
                finishMatch(hasMoreText = true)
            }
        } else {
            // Preserva início de frase entre blocos e contexto suficiente para o trecho.
            val keep = query.length - 1 + CONTEXT_LENGTH
            if (buffer.length > keep) {
                buffer.delete(0, buffer.length - keep)
                discardedCharacters = true
            }
        }
    }

    private fun finishMatch(hasMoreText: Boolean): Nothing {
        val start = (pendingMatch - CONTEXT_LENGTH).coerceAtLeast(0)
        val end = (pendingMatch + query.length + CONTEXT_LENGTH).coerceAtMost(buffer.length)
        val snippet = buildString {
            if (discardedCharacters || start > 0) append('…')
            append(buffer.substring(start, end).trim())
            if (hasMoreText || end < buffer.length) append('…')
        }
        throw PdfTextMatchFound(PdfTextMatch(snippet, pageNumber))
    }

    private companion object {
        const val CONTEXT_LENGTH = 64
        const val SCAN_CHUNK_LENGTH = 1_024
    }
}

internal class PdfTextMatchFound(val match: PdfTextMatch) : RuntimeException(null, null, false, false)

internal fun normalizeSearchWhitespace(value: String): String = buildString {
    value.forEach { character ->
        if (character.isWhitespace()) {
            if (isNotEmpty() && last() != ' ') append(' ')
        } else {
            append(character)
        }
    }
}.trim()
