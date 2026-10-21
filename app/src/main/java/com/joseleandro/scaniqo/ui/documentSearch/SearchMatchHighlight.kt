package com.joseleandro.scaniqo.ui.documentSearch

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString

private val searchWhitespace = Regex("[\\s\\p{Z}]+")

/** Mantém o texto original e realça as mesmas palavras mesmo com espaços ou caixa diferentes. */
internal fun highlightSearchMatches(text: String, query: String, style: SpanStyle): AnnotatedString {
    val words = query.split(searchWhitespace).filter(String::isNotEmpty)
    if (words.isEmpty()) return AnnotatedString(text)
    val pattern = words.joinToString("[\\s\\p{Z}]+") { Regex.escape(it) }
    val matches = Regex(pattern, RegexOption.IGNORE_CASE).findAll(text)
    return buildAnnotatedString {
        append(text)
        matches.forEach { match ->
            addStyle(style, match.range.first, match.range.last + 1)
        }
    }
}
