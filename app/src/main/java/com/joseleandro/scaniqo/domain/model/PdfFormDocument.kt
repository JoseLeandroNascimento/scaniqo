package com.joseleandro.scaniqo.domain.model

enum class PdfFormFieldKind { TEXT, CHECKBOX, SINGLE_CHOICE, MULTIPLE_CHOICE }
data class PdfFormOption(val value: String, val label: String)
data class PdfFormField(
    val id: String,
    val label: String,
    val kind: PdfFormFieldKind,
    val values: List<String>,
    val options: List<PdfFormOption> = emptyList(),
    val readOnly: Boolean = false,
    val required: Boolean = false,
    val multiline: Boolean = false,
    val password: Boolean = false,
    val maxLength: Int = 0,
)
data class PdfFormDocument(
    val sessionId: String,
    val name: String,
    val fields: List<PdfFormField>,
    val unsupportedFields: Int = 0,
)
data class PdfFormExport(val id: String, val suggestedName: String)
