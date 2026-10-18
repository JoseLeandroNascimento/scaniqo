package com.joseleandro.scaniqo.ui.documentEditor.model

import kotlinx.serialization.Serializable

@Serializable
enum class DocumentTool { LAYOUT, FILTERS, CORRECTIONS, QUALITY, PAGES, MERGE, SIGNATURES, PROTECTION }
