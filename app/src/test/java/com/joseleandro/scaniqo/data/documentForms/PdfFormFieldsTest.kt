package com.joseleandro.scaniqo.data.documentForms

import com.joseleandro.scaniqo.domain.model.PdfFormFieldKind
import com.tom_roush.pdfbox.cos.COSArray
import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.cos.COSString
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAppearanceDictionary
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAppearanceEntry
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAppearanceStream
import com.tom_roush.pdfbox.pdmodel.interactive.digitalsignature.PDSignature
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDAcroForm
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDCheckBox
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDComboBox
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDListBox
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDSignatureField
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDTextField
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class, manifest = Config.NONE)
class PdfFormFieldsTest {
    @Test fun inspectKeepsTextValueLabelAndValidationFlags() = withForm { pdf, form ->
        form.fields = listOf(text(form, "customer", "João").apply {
            alternateFieldName = "Nome completo"
            isRequired = true
            isReadOnly = true
            isMultiline = true
            maxLen = 80
        })
        val (fields, unsupported) = PdfFormFields.inspect(pdf)
        val field = fields.single()
        assertEquals("customer", field.id)
        assertEquals("Nome completo", field.label)
        assertEquals(listOf("João"), field.values)
        assertTrue(field.required)
        assertTrue(field.readOnly)
        assertTrue(field.multiline)
        assertEquals(80, field.maxLength)
        assertEquals(0, unsupported)
    }

    @Test fun readonlyFieldAcceptsUnchangedValueAndRejectsMutation() = withForm { pdf, form ->
        val native = text(form, "fixed", "Original").apply { isReadOnly = true }
        form.fields = listOf(native)
        PdfFormFields.fill(pdf, mapOf("fixed" to listOf("Original")))
        assertThrows(IllegalArgumentException::class.java) {
            PdfFormFields.fill(pdf, mapOf("fixed" to listOf("Alterado")))
        }
        assertEquals("Original", native.value)
    }

    @Test fun requiredAndMaxLengthAreValidatedBeforeWriting() = withForm { pdf, form ->
        val native = text(form, "name", "Ana").apply { isRequired = true; maxLen = 5 }
        form.fields = listOf(native)
        assertThrows(IllegalArgumentException::class.java) { PdfFormFields.fill(pdf, mapOf("name" to emptyList())) }
        assertThrows(IllegalArgumentException::class.java) { PdfFormFields.fill(pdf, mapOf("name" to listOf("  "))) }
        assertThrows(IllegalArgumentException::class.java) { PdfFormFields.fill(pdf, mapOf("name" to listOf("LongName"))) }
        assertEquals("Ana", native.value)
    }

    @Test fun unknownFieldsAndMultipleValuesForTextAreRejected() = withForm { pdf, form ->
        form.fields = listOf(text(form, "name", "Ana"))
        assertThrows(IllegalArgumentException::class.java) { PdfFormFields.fill(pdf, mapOf("missing" to listOf("value"))) }
        assertThrows(IllegalArgumentException::class.java) { PdfFormFields.fill(pdf, mapOf("name" to listOf("a", "b"))) }
    }

    @Test fun choiceKeepsExportValuesSeparateFromDisplayLabels() = withForm { pdf, form ->
        val choice = PDListBox(form).apply {
            partialName = "colors"
            isMultiSelect = true
            setOptions(listOf("R", "G"), listOf("Vermelho", "Verde"))
            cosObject.setItem(COSName.V, COSArray().apply { add(COSString("R")); add(COSString("G")) })
        }
        form.fields = listOf(choice)
        val field = PdfFormFields.inspect(pdf).first.single()
        assertEquals(PdfFormFieldKind.MULTIPLE_CHOICE, field.kind)
        assertEquals(listOf("R", "G"), field.values)
        assertEquals(listOf("Vermelho", "Verde"), field.options.map { it.label })
        assertEquals(listOf("R", "G"), field.options.map { it.value })
        assertThrows(IllegalArgumentException::class.java) { PdfFormFields.fill(pdf, mapOf("colors" to listOf("B"))) }
    }

    @Test fun editableComboIsMappedAsFreeText() = withForm { pdf, form ->
        form.fields = listOf(PDComboBox(form).apply {
            partialName = "city"
            isEdit = true
            options = listOf("Rio Branco")
            cosObject.setString(COSName.V, "Outra cidade")
        })
        val mapped = PdfFormFields.inspect(pdf).first.single()
        assertEquals(PdfFormFieldKind.TEXT, mapped.kind)
        assertEquals(listOf("Outra cidade"), mapped.values)
    }

    @Test fun checkboxUsesNativeOnValueAndCanBeCleared() = withForm { pdf, form ->
        val check = PDCheckBox(form).apply { partialName = "agree" }
        val appearances = COSDictionary().apply {
            setItem(COSName.getPDFName("Yes"), PDAppearanceStream(pdf))
            setItem(COSName.Off, PDAppearanceStream(pdf))
        }
        check.widgets.single().appearance = PDAppearanceDictionary().apply {
            setNormalAppearance(PDAppearanceEntry(appearances))
        }
        form.fields = listOf(check)
        PdfFormFields.fill(pdf, mapOf("agree" to listOf("Yes")))
        assertTrue(check.isChecked)
        assertEquals(listOf("Yes"), PdfFormFields.inspect(pdf).first.single().values)
        PdfFormFields.fill(pdf, mapOf("agree" to emptyList()))
        assertFalse(check.isChecked)
        assertThrows(IllegalArgumentException::class.java) { PdfFormFields.fill(pdf, mapOf("agree" to listOf("invented"))) }
    }

    @Test fun unsupportedEmptySignatureFieldIsReportedAndPreserved() = withForm { pdf, form ->
        val signature = PDSignatureField(form).apply { partialName = "signature" }
        form.fields = listOf(text(form, "name", "Ana"), signature)
        val (fields, unsupported) = PdfFormFields.inspect(pdf)
        assertEquals(listOf("name"), fields.map { it.id })
        assertEquals(1, unsupported)
        PdfFormFields.fill(pdf, mapOf("name" to listOf("Maria")))
        assertTrue(form.getField("signature") is PDSignatureField)
        assertEquals(2, form.fields.size)
    }

    @Test fun fillRetainsInteractiveFieldsAcrossPdfRoundTrip() = withForm { pdf, form ->
        form.fields = listOf(text(form, "name", "Ana"))
        PdfFormFields.fill(pdf, mapOf("name" to listOf("Maria")))
        val bytes = ByteArrayOutputStream().also(pdf::save).toByteArray()
        PDDocument.load(bytes).use { loaded ->
            val native = loaded.documentCatalog.acroForm.getField("name")
            assertTrue(native is PDTextField)
            assertEquals("Maria", native.valueAsString)
            assertEquals(1, PdfFormFields.inspect(loaded).first.size)
        }
    }

    @Test fun signedAndCertifiedFormsAreRejectedBeforeAnyChanges() = withForm { pdf, form ->
        form.fields = listOf(text(form, "name", "Ana"), PDSignatureField(form).apply {
            partialName = "signature"
            value = PDSignature()
        })
        assertThrows(IllegalArgumentException::class.java) { PdfFormFields.inspect(pdf) }
        form.fields = listOf(text(form, "name", "Ana"))
        pdf.documentCatalog.cosObject.setItem(COSName.PERMS, COSDictionary())
        assertThrows(IllegalArgumentException::class.java) { PdfFormFields.fill(pdf, mapOf("name" to listOf("Maria"))) }
        assertEquals("Ana", form.getField("name").valueAsString)
    }

    @Test fun xfaAndDuplicateNamesAreRejected() = withForm { pdf, form ->
        form.fields = listOf(text(form, "name", "Ana"))
        form.cosObject.setItem(COSName.XFA, COSString("xfa"))
        assertThrows(IllegalArgumentException::class.java) { PdfFormFields.inspect(pdf) }
        form.cosObject.removeItem(COSName.XFA)
        form.fields = listOf(text(form, "name", "Ana"), text(form, "name", "Maria"))
        assertThrows(IllegalArgumentException::class.java) { PdfFormFields.inspect(pdf) }
    }

    private fun text(form: PDAcroForm, name: String, value: String) = PDTextField(form).apply {
        partialName = name
        cosObject.setString(COSName.V, value)
    }

    private fun withForm(block: (PDDocument, PDAcroForm) -> Unit) {
        PDDocument().use { pdf ->
            val form = PDAcroForm(pdf)
            pdf.documentCatalog.acroForm = form
            form.defaultResources = PDResources().apply { put(COSName.getPDFName("Helv"), PDType1Font.HELVETICA) }
            form.defaultAppearance = "/Helv 12 Tf 0 g"
            block(pdf, form)
        }
    }
}
