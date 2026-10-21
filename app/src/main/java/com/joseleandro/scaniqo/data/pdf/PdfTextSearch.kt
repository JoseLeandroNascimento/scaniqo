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

/**
 * KMP evita rescans de prefixos repetidos; a janela circular conserva só a consulta e
 * 64 caracteres de contexto em cada lado, independentemente do tamanho do texto extraído.
 */
internal class PdfSearchSnippetWriter(
    query: String,
    private val context: CoroutineContext,
) : Writer() {
    private val pattern = normalizeSearchWhitespace(query).also { require(it.isNotEmpty()) }
        .map { foldCase(it) }.toCharArray()
    private val prefixes = IntArray(pattern.size).apply {
        var matched = 0
        for (index in 1 until size) {
            while (matched > 0 && pattern[index] != pattern[matched]) matched = this[matched - 1]
            if (pattern[index] == pattern[matched]) matched++
            this[index] = matched
        }
    }
    private val window = CharArray(pattern.size + CONTEXT_LENGTH * 2)
    private var next = 0
    private var retained = 0
    private var normalizedCharacters = 0L
    private var previousWasSpace = false
    private var matchedPrefix = 0
    private var matchEnd = -1L
    private var pageNumber = 1

    fun startPage(pageNumber: Int) {
        context.ensureActive()
        this.pageNumber = pageNumber
        next = 0
        retained = 0
        normalizedCharacters = 0
        previousWasSpace = false
        matchedPrefix = 0
        matchEnd = -1
    }

    override fun write(chars: CharArray, offset: Int, length: Int) {
        checkBounds(chars.size, offset, length)
        context.ensureActive()
        for (index in offset until offset + length) {
            if ((index - offset) % CANCELLATION_INTERVAL == 0) context.ensureActive()
            appendCharacter(chars[index])
        }
    }

    // Writer's default implementation creates a char array as large as the supplied string.
    override fun write(value: String, offset: Int, length: Int) {
        checkBounds(value.length, offset, length)
        context.ensureActive()
        for (index in offset until offset + length) {
            if ((index - offset) % CANCELLATION_INTERVAL == 0) context.ensureActive()
            appendCharacter(value[index])
        }
    }

    private fun appendCharacter(raw: Char) {
        val space = raw.isWhitespace()
        if (space && (normalizedCharacters == 0L || previousWasSpace)) return
        val character = if (space) ' ' else raw
        previousWasSpace = space
        window[next] = character
        next = (next + 1) % window.size
        retained = (retained + 1).coerceAtMost(window.size)
        normalizedCharacters++

        if (matchEnd < 0) {
            val folded = foldCase(character)
            while (matchedPrefix > 0 && pattern[matchedPrefix] != folded) {
                matchedPrefix = prefixes[matchedPrefix - 1]
            }
            if (pattern[matchedPrefix] == folded) matchedPrefix++
            if (matchedPrefix == pattern.size) matchEnd = normalizedCharacters
        }
        if (matchEnd >= 0 && normalizedCharacters >= matchEnd + CONTEXT_LENGTH) {
            finishMatch(hasMoreText = true)
        }
    }

    fun finishPage() {
        context.ensureActive()
        if (matchEnd >= 0) finishMatch(hasMoreText = false)
    }

    override fun flush() = Unit
    override fun close() = Unit

    private fun finishMatch(hasMoreText: Boolean): Nothing {
        val start = (matchEnd - pattern.size - CONTEXT_LENGTH).coerceAtLeast(0)
        val end = (matchEnd + CONTEXT_LENGTH).coerceAtMost(normalizedCharacters)
        val windowStart = normalizedCharacters - retained
        val oldest = (next - retained + window.size) % window.size
        val excerpt = buildString {
            for (position in start until end) {
                append(window[(oldest + (position - windowStart).toInt()) % window.size])
            }
        }.trim()
        val snippet = buildString {
            if (start > 0) append('…')
            append(excerpt)
            if (hasMoreText) append('…')
        }
        throw PdfTextMatchFound(PdfTextMatch(snippet, pageNumber))
    }

    private fun checkBounds(size: Int, offset: Int, length: Int) {
        if (offset < 0 || length < 0 || offset > size - length) throw IndexOutOfBoundsException()
    }

    private companion object {
        const val CONTEXT_LENGTH = 64
        const val CANCELLATION_INTERVAL = 1_024

        fun foldCase(character: Char): Char = character.uppercaseChar().lowercaseChar()
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
