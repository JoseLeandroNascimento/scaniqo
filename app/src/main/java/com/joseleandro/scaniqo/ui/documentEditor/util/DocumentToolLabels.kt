package com.joseleandro.scaniqo.ui.documentEditor.util

import com.joseleandro.scaniqo.R
import com.joseleandro.scaniqo.ui.documentEditor.model.DocumentTool

internal fun DocumentTool.titleRes(): Int = when (this) {
    DocumentTool.LAYOUT -> R.string.tools_layout
    DocumentTool.FILTERS -> R.string.tools_filters
    DocumentTool.CORRECTIONS -> R.string.viewer_tool_corrections
    DocumentTool.QUALITY -> R.string.viewer_tool_quality
    DocumentTool.PAGES -> R.string.tools_pages
    DocumentTool.MERGE -> R.string.toolbar_tool_merge
    DocumentTool.SIGNATURES -> R.string.tools_signatures
    DocumentTool.PROTECTION -> R.string.tools_protection
    DocumentTool.FORMS -> R.string.overlay_forms
    DocumentTool.ANNOTATIONS -> R.string.overlay_annotations
    DocumentTool.WATERMARK -> R.string.overlay_watermark
    DocumentTool.REDACTION -> R.string.overlay_redaction
}
