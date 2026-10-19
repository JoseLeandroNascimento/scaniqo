package com.joseleandro.scaniqo.domain.model

/** Números visíveis começam em 1; índices retornados começam em 0 e mantêm a ordem escolhida. */
object PdfPageSelection {
    fun parse(value: String, pageCount: Int): List<Int> {
        require(pageCount in 1..100) { "O PDF deve ter entre 1 e 100 páginas." }
        if (value.isBlank()) return (0 until pageCount).toList()
        require(value.length <= 1000) { "A seleção de páginas é muito longa." }
        val pages = linkedSetOf<Int>()
        for (part in value.split(',')) {
            val bounds = part.trim().split('-').map { it.trim().toIntOrNull() }
            require(bounds.size in 1..2 && bounds.all { it != null }) {
                "Use páginas ou intervalos, por exemplo: 1, 3-5."
            }
            val start = requireNotNull(bounds.first())
            val end = requireNotNull(bounds.last())
            require(start in 1..pageCount && end in start..pageCount) {
                "Escolha páginas entre 1 e $pageCount, em intervalos crescentes."
            }
            (start..end).forEach { pages += it - 1 }
        }
        require(pages.isNotEmpty()) { "Selecione pelo menos uma página." }
        return pages.toList()
    }
}
