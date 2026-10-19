package com.joseleandro.scaniqo.data.documentForms

import com.joseleandro.scaniqo.domain.model.PdfFormField
import com.joseleandro.scaniqo.domain.model.PdfFormFieldKind
import com.joseleandro.scaniqo.domain.model.PdfFormOption
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDCheckBox
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDChoice
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDComboBox
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDField
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDRadioButton
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDTerminalField
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDTextField

internal object PdfFormFields {
    fun inspect(pdf: PDDocument): Pair<List<PdfFormField>, Int> {
        require(pdf.signatureDictionaries.isEmpty() && !pdf.documentCatalog.cosObject.containsKey(COSName.PERMS)) {
            "Este PDF possui assinatura ou certificação. O preenchimento poderia invalidá-la."
        }
        require(pdf.currentAccessPermission.canFillInForm()) { "Este PDF não permite preencher campos." }
        val form = requireNotNull(pdf.documentCatalog.acroForm) { "Este PDF não possui campos interativos. Use Preencher sobre a página para formulários digitalizados." }
        require(!form.hasXFA()) { "Formulários XFA não são compatíveis com esta ferramenta." }
        val native = form.fieldTree.filterIsInstance<PDTerminalField>().toList()
        require(native.size <= 300) { "Este formulário possui mais de 300 campos." }
        val fields = native.mapNotNull(::map)
        require(fields.map { it.id }.distinct().size == fields.size) { "O formulário possui nomes de campos duplicados." }
        require(fields.isNotEmpty()) { "Este PDF não possui campos de texto ou seleção compatíveis." }
        return fields to (native.size - fields.size)
    }

    private fun map(field: PDField): PdfFormField? {
        val id = field.fullyQualifiedName?.takeIf { it.isNotBlank() } ?: return null
        val base = PdfFormField(
            id = id,
            label = field.alternateFieldName?.takeIf { it.isNotBlank() } ?: id,
            kind = PdfFormFieldKind.TEXT,
            values = listOf(field.valueAsString.orEmpty()),
            readOnly = field.isReadOnly,
            required = field.isRequired,
        )
        return when (field) {
            is PDTextField -> base.copy(multiline = field.isMultiline, password = field.isPassword, maxLength = field.maxLen)
            is PDCheckBox -> base.copy(
                kind = PdfFormFieldKind.CHECKBOX,
                values = if (field.isChecked) listOf(field.onValue) else emptyList(),
                options = listOf(PdfFormOption(field.onValue, base.label)),
                readOnly = base.readOnly || field.onValue.isBlank(),
            )
            is PDRadioButton -> base.copy(
                kind = PdfFormFieldKind.SINGLE_CHOICE,
                values = listOf(field.value).filter { it != "Off" },
                options = field.onValues.filter { it != "Off" }.map { PdfFormOption(it, it) },
            )
            is PDChoice -> base.copy(
                kind = when {
                    field is PDComboBox && field.isEdit -> PdfFormFieldKind.TEXT
                    field.isMultiSelect -> PdfFormFieldKind.MULTIPLE_CHOICE
                    else -> PdfFormFieldKind.SINGLE_CHOICE
                },
                values = field.value,
                options = field.optionsExportValues.mapIndexed { index, value ->
                    PdfFormOption(value, field.optionsDisplayValues.getOrElse(index) { value })
                },
            )
            else -> null
        }
    }

    fun fill(pdf: PDDocument, values: Map<String, List<String>>) {
        val (fields, _) = inspect(pdf)
        val definitions = fields.associateBy { it.id }
        require(values.keys.all { it in definitions }) { "Um campo não está mais disponível." }
        val form = requireNotNull(pdf.documentCatalog.acroForm)
        // Values are written by PDFBox; no calculation/action JavaScript is executed.
        form.needAppearances = false
        values.forEach { (id, selected) ->
            val definition = definitions.getValue(id)
            val field = requireNotNull(form.getField(id))
            if (definition.readOnly) {
                require(selected == definition.values) { "O campo ${definition.label} é somente leitura." }
                return@forEach
            }
            require(selected.size <= 300 && selected.all { it.length <= 20_000 }) { "O conteúdo do campo é muito longo." }
            if (definition.required) require(selected.any { it.isNotBlank() }) { "Preencha o campo ${definition.label}." }
            if (definition.kind != PdfFormFieldKind.MULTIPLE_CHOICE) require(selected.size <= 1) { "Selecione somente uma opção." }
            if (definition.maxLength > 0) require(selected.firstOrNull().orEmpty().length <= definition.maxLength) {
                "O campo ${definition.label} aceita até ${definition.maxLength} caracteres."
            }
            if (definition.kind != PdfFormFieldKind.TEXT) {
                require(selected.all { selectedValue -> definition.options.any { it.value == selectedValue } }) { "Uma opção selecionada não existe mais." }
            }
            when (field) {
                is PDTextField -> field.value = selected.firstOrNull().orEmpty()
                is PDCheckBox -> if (selected.isEmpty()) field.unCheck() else field.check()
                is PDRadioButton -> field.value = selected.firstOrNull() ?: "Off"
                is PDChoice -> if (field.isMultiSelect) field.setValue(selected) else field.setValue(selected.firstOrNull().orEmpty())
            }
        }
    }
}
