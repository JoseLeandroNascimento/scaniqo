package com.joseleandro.scaniqo.ui.documentEditor.model

import kotlinx.serialization.Serializable

@Serializable
enum class DocumentTool { LAYOUT, FILTERS, CORRECTIONS, QUALITY, PAGES, MERGE, SIGNATURES, PROTECTION, FORMS, ANNOTATIONS, WATERMARK, REDACTION }

val DocumentTool.hasEditorScreen: Boolean
    get() = this in setOf(DocumentTool.LAYOUT, DocumentTool.CORRECTIONS, DocumentTool.PAGES,
        DocumentTool.FORMS, DocumentTool.ANNOTATIONS, DocumentTool.WATERMARK, DocumentTool.REDACTION)
