package com.joseleandro.scaniqo.ui.documentEditor.util

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import com.joseleandro.scaniqo.domain.model.PdfPageLayout
import com.joseleandro.scaniqo.ui.documentEditor.model.DocumentPageScope
import com.joseleandro.scaniqo.ui.documentEditor.model.DocumentPageScopeMode
import kotlinx.serialization.json.Json

/** Rascunhos sobrevivem à recriação da tela sem incluir arquivos ou pixels no saved state. */
internal val PdfPageLayoutSaver = Saver<PdfPageLayout, String>(
    save = { Json.encodeToString(it) },
    restore = { runCatching { Json.decodeFromString<PdfPageLayout>(it) }.getOrNull() },
)

internal val DocumentPageScopeSaver = listSaver<DocumentPageScope, String>(
    save = { listOf(it.mode.name) + it.pageIds },
    restore = { values ->
        runCatching {
            DocumentPageScope(DocumentPageScopeMode.valueOf(values.first()), values.drop(1).toSet())
        }.getOrNull()
    },
)
