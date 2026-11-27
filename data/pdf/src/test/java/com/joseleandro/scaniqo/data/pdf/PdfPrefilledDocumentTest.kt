package com.joseleandro.scaniqo.data.pdf

import android.app.Application
import android.graphics.Color
import android.net.Uri
import com.joseleandro.scaniqo.data.local.document.AndroidDocumentFileStore
import com.joseleandro.scaniqo.domain.geometry.PdfPageGeometry
import com.joseleandro.scaniqo.domain.model.*
import com.joseleandro.scaniqo.domain.repository.DocumentRepository
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.*
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.*
import com.tom_roush.pdfbox.pdmodel.interactive.form.*
import com.tom_roush.pdfbox.rendering.PDFRenderer
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File
import kotlin.math.ceil
import kotlin.math.floor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Campos existentes viram conteúdo visual; somente textos e marcações novos ficam editáveis. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, manifest = Config.NONE,
    shadows = [AndroidPdfEditorSettingsTest.HostFileUri::class,
        AndroidPdfEditorSettingsTest.SettingsPdfRenderer::class,
        AndroidPdfEditorSettingsTest.SettingsPdfPage::class])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PdfPrefilledDocumentTest {
    @get:Rule val temporary = TemporaryFolder()
    private val projects = mutableListOf<File>()
    private val documents = Documents()
    private val app get() = RuntimeEnvironment.getApplication()
    private fun editor() = AndroidPdfEditorRepository(app, documents,
        AndroidDocumentFileStore(app, Dispatchers.Unconfined), Dispatchers.Unconfined)

    @After fun cleanup() { projects.forEach(File::deleteRecursively) }

    @Test fun importAndOverlayEditsPreservePrefilledAppearanceAcrossSaveAndReopen() = runTest {
        val input = source("prefilled.pdf", listOf("Primeira pagina", "Segunda pagina"))
        val before = input.readBytes()
        val repository = editor()
        val scan = repository.importPdf(uri(input), null, null)
        track(scan.pdfUri)
        documents.current = model(scan)
        val workspace = repository.open(documents.current!!, null)
        try {
            assertEquals(2, workspace.pages.size)
            assertPrefilledOutput(local(scan.pdfUri), listOf("Primeira pagina", "Segunda pagina"))
            val note = PdfOverlay("note", PdfOverlayKind.TEXT, text = "Nova anotacao", y = .7f)
            val mark = PdfOverlay("mark", PdfOverlayKind.CHECKMARK,
                x = .75f, y = .75f, width = .08f, height = .05f)
            val edited = workspace.copy(pages = workspace.pages.mapIndexed { index, page ->
                page.copy(overlays = if (index == 0) listOf(note) else listOf(mark))
            })
            val result = repository.save(edited, null, null, false)
            track(result.pdfUri)
            assertArrayEquals(before, input.readBytes())
            assertPrefilledOutput(local(result.pdfUri), listOf("Primeira pagina", "Segunda pagina"))
            PDDocument.load(local(result.pdfUri)).use { pdf ->
                assertEquals(1, occurrences(pdfText(pdf), "Nova anotacao"))
                assertTrue("A nova marcação deve aparecer nos pixels exportados.",
                    darkPixels(pdf, 1, .75f * 595, .2f * 842, .08f * 595, .05f * 842) > 20)
            }
            val reopened = repository.open(result, null)
            try {
                assertEquals(listOf(note), reopened.pages[0].overlays)
                assertEquals(listOf(mark), reopened.pages[1].overlays)
                assertTrue(reopened.pages.all { it.signatures.isEmpty() })
            } finally { repository.discard(reopened) }
        } finally { repository.discard(workspace) }
    }

    @Test fun legacyRecipeUsesFinalAppearanceWithoutReapplyingOldOverlaysOrLayout() = runTest {
        val repository = editor()
        val legacy = legacyProject(repository)
        val finalFile = local(legacy.pdfUri)
        val project = finalFile.parentFile!!
        val beforePdf = finalFile.readBytes()
        val beforeRecipe = File(project, "recipe.json").readBytes()
        documents.current = legacy
        val workspace = repository.open(legacy, null)
        try {
            assertArrayEquals(beforePdf, finalFile.readBytes())
            assertArrayEquals(beforeRecipe, File(project, "recipe.json").readBytes())
            assertEquals(2, workspace.pages.size)
            assertTrue(workspace.pages.all {
                it.layout == PdfPageLayout(edgeToEdge = true) &&
                    it.overlays.isEmpty() && it.signatures.isEmpty()
            })
            assertFalse(workspace.showPageNumbers)
            assertFalse(workspace.ocrEnabled)
            assertEquals(PdfExportQuality.BALANCED, workspace.exportQuality)
            PDDocument.load(local(workspace.pages[0].sourceUri)).use { base ->
                val text = pdfText(base)
                assertTrue(text.contains("Valor persistido"))
                assertFalse(text.contains("Fonte desatualizada"))
                assertEquals(1, occurrences(text, "Marca anterior"))
            }
            val note = PdfOverlay("new-note", PdfOverlayKind.TEXT, text = "Texto novo", y = .8f)
            val result = repository.save(workspace.copy(pages = workspace.pages.map {
                it.copy(overlays = listOf(note.copy(id = "new-" + it.id)))
            }), null, null, false)
            track(result.pdfUri)
            PDDocument.load(local(result.pdfUri)).use { pdf ->
                val text = pdfText(pdf)
                assertEquals(1, occurrences(text, "Valor persistido"))
                assertEquals(1, occurrences(text, "Outro valor"))
                assertEquals(1, occurrences(text, "Marca anterior"))
                assertEquals(2, occurrences(text, "Texto novo"))
                assertEquals(1, occurrences(text, "1 / 2"))
                assertEquals(1, occurrences(text, "2 / 2"))
                assertTrue("A marca pré-preenchida permanece visível.",
                    darkPixels(pdf, 0, 55f, 665f, 10f, 10f) > 8)
            }
            assertFalse(File(local(result.pdfUri).parentFile!!, "recipe.json").readText().contains("formFields"))
            val reopened = repository.open(result, null)
            try {
                assertTrue(reopened.pages.all { it.overlays.single().text == "Texto novo" })
                assertFalse(reopened.showPageNumbers)
            } finally { repository.discard(reopened) }
        } finally { repository.discard(workspace) }
    }

    @Test fun legacyRecipeCanRecoverItsFinalPdfWhenOldSourcesAreUnavailable() = runTest {
        val repository = editor()
        val legacy = legacyProject(repository)
        assertTrue(File(local(legacy.pdfUri).parentFile!!, "sources.pdf").delete())
        documents.current = legacy
        val workspace = repository.open(legacy, null)
        try {
            PDDocument.load(local(workspace.pages[0].sourceUri)).use {
                assertTrue(pdfText(it).contains("Valor persistido"))
            }
        } finally { repository.discard(workspace) }
    }

    @Test fun legacyRecoveryAuthenticatesTheSavedPdfAndKeepsItsProtection() = runTest {
        val repository = editor()
        val legacy = legacyProject(repository).copy(protectionKeyId = "existing-document-key")
        val finalFile = local(legacy.pdfUri)
        val encrypted = temporary.newFile("protected-final.pdf")
        PDDocument.load(finalFile).use { pdf ->
            PdfProtection.apply(pdf, "per-document-secret")
            pdf.save(encrypted)
        }
        encrypted.copyTo(finalFile, overwrite = true)
        documents.current = legacy
        assertTrue(runCatching { repository.open(legacy, null) }.exceptionOrNull() is PdfPasswordRequiredException)
        assertTrue(runCatching { repository.open(legacy, "wrong-password") }.exceptionOrNull() is PdfPasswordRequiredException)
        val workspace = repository.open(legacy, "per-document-secret")
        try {
            assertTrue(workspace.isProtected)
            assertEquals(legacy.protectionKeyId, workspace.protectionKeyId)
            val note = PdfOverlay("protected-note", PdfOverlayKind.TEXT, text = "Nota protegida", y = .8f)
            val edited = workspace.copy(pages = workspace.pages.map { it.copy(overlays = listOf(note)) })
            val result = repository.save(edited, "per-document-secret", null, false)
            track(result.pdfUri)
            assertTrue(result.isProtected)
            assertEquals(legacy.protectionKeyId, result.protectionKeyId)
            PDDocument.load(local(result.pdfUri), "per-document-secret").use { pdf ->
                assertTrue(pdf.isEncrypted)
                assertEquals(1, occurrences(pdfText(pdf), "Valor persistido"))
                assertEquals(2, occurrences(pdfText(pdf), "Nota protegida"))
            }
        } finally { repository.discard(workspace) }
    }

    @Test fun mergingPrefilledDocumentsKeepsTheirVisibleTextAndCheckboxes() = runTest {
        val repository = editor()
        val scan = repository.mergePdfs(listOf(
            PdfMergeSource(uri(source("first.pdf", listOf("Primeiro")))),
            PdfMergeSource(uri(source("second.pdf", listOf("Segundo")))),
        ))
        track(scan.pdfUri)
        assertPrefilledOutput(local(scan.pdfUri), listOf("Primeiro", "Segundo"))
    }

    private suspend fun legacyProject(repository: AndroidPdfEditorRepository): DocumentModel {
        val scan = repository.importPdf(uri(source("legacy-source.pdf",
            listOf("Fonte desatualizada", "Fonte antiga"))), null, null)
        track(scan.pdfUri)
        val finalFile = local(scan.pdfUri)
        // Simula o projeto antigo: valores só no PDF final, fontes ainda anteriores ao preenchimento.
        source("legacy-final.pdf", listOf("Valor persistido", "Outro valor"), oldMarks = true)
            .copyTo(finalFile, overwrite = true)
        val recipe = PdfProjectRecipe(
            pages = listOf(RecipePage("old-page", 1, PdfPageLayout(rotationQuarterTurns = 1),
                overlays = listOf(PdfOverlay("old-mark", PdfOverlayKind.TEXT, text = "Marca anterior"))),
                RecipePage("second-page", 0, PdfPageLayout())),
            showPageNumbers = true, exportQuality = PdfExportQuality.BALANCED, ocrEnabled = true,
        )
        val encoded = Json.encodeToJsonElement(recipe).jsonObject
        val legacyPages = encoded.getValue("pages").jsonArray.map { element ->
            JsonObject(element.jsonObject + ("formFields" to Json.parseToJsonElement(
                """[{"id":"old-field","name":"name","kind":"TEXT","values":["Valor persistido"]}]""")))
        }
        File(finalFile.parentFile!!, "recipe.json").writeText(
            JsonObject(encoded + ("pages" to JsonArray(legacyPages))).toString())
        return model(scan).copy(sizeBytes = finalFile.length())
    }

    private fun assertPrefilledOutput(file: File, expected: List<String>) {
        PDDocument.load(file).use { pdf ->
            assertEquals(expected.size, pdf.numberOfPages)
            assertTrue(pdf.documentCatalog.acroForm?.fields.isNullOrEmpty())
            val text = pdfText(pdf)
            val transform = PdfPageGeometry.placement(0f, 0f, 595f, 842f, 0, PdfPageLayout())
            expected.forEachIndexed { index, value ->
                assertEquals(1, occurrences(text, value))
                assertTrue(pdf.getPage(index).annotations.isEmpty())
                assertTrue("A aparência do campo deve permanecer visível na página $index.",
                    darkPixels(pdf, index, transform.x(50f, 720f), transform.y(50f, 720f),
                        220f * transform.a, 24f * transform.d) > 40)
                // Examina apenas o centro do checkbox: sua borda vazia não faz o teste passar.
                assertTrue("O estado marcado deve permanecer visível na página $index.",
                    darkPixels(pdf, index, transform.x(55f, 665f), transform.y(55f, 665f),
                        10f * transform.a, 10f * transform.d) > 8)
            }
        }
    }

    private fun darkPixels(pdf: PDDocument, page: Int, x: Float, y: Float, width: Float, height: Float): Int {
        val bitmap = PDFRenderer(pdf).renderImage(page)
        return try {
            val pageHeight = pdf.getPage(page).cropBox.height
            var count = 0
            for (py in floor(pageHeight - y - height).toInt().coerceAtLeast(0) until
                ceil(pageHeight - y).toInt().coerceAtMost(bitmap.height)) {
                for (px in floor(x).toInt().coerceAtLeast(0) until
                    ceil(x + width).toInt().coerceAtMost(bitmap.width)) {
                    val pixel = bitmap.getPixel(px, py)
                    if (Color.red(pixel) < 128 && Color.green(pixel) < 128 && Color.blue(pixel) < 128) count++
                }
            }
            count
        } finally { bitmap.recycle() }
    }

    private fun source(name: String, values: List<String>, oldMarks: Boolean = false): File {
        PDFBoxResourceLoader.init(app)
        val file = temporary.newFile(name)
        PDDocument().use { pdf ->
            val form = PDAcroForm(pdf)
            pdf.documentCatalog.acroForm = form
            form.defaultResources = PDResources().apply { put(COSName.getPDFName("Helv"), PDType1Font.HELVETICA) }
            form.defaultAppearance = "/Helv 12 Tf 0 g"
            values.forEachIndexed { index, value ->
                val page = PDPage(PDRectangle(595f, 842f))
                pdf.addPage(page)
                val text = PDTextField(form).apply { partialName = "name$index" }
                form.fields.add(text)
                text.widgets.single().apply {
                    rectangle = PDRectangle(50f, 720f, 220f, 24f)
                    this.page = page
                }
                page.annotations.addAll(text.widgets)
                text.value = value
                val checkbox = PDCheckBox(form).apply { partialName = "accept$index" }
                form.fields.add(checkbox)
                checkbox.widgets.single().apply {
                    rectangle = PDRectangle(50f, 660f, 20f, 20f)
                    this.page = page
                    val states = COSDictionary()
                    for (state in listOf("Off", "Yes")) {
                        val appearance = PDAppearanceStream(pdf).apply {
                            bBox = PDRectangle(20f, 20f); resources = PDResources()
                        }
                        PDPageContentStream(pdf, appearance).use {
                            it.setStrokingColor(0f)
                            it.addRect(1f, 1f, 18f, 18f); it.stroke()
                            if (state == "Yes") {
                                it.setLineWidth(2f)
                                it.moveTo(3f, 3f); it.lineTo(17f, 17f); it.stroke()
                            }
                        }
                        states.setItem(COSName.getPDFName(state), appearance)
                    }
                    appearance = PDAppearanceDictionary().apply { cosObject.setItem(COSName.N, states) }
                }
                page.annotations.addAll(checkbox.widgets)
                checkbox.check()
                if (oldMarks) PDPageContentStream(pdf, page).use { stream ->
                    stream.beginText()
                    stream.setNonStrokingColor(0f)
                    stream.setFont(PDType1Font.HELVETICA, 12f)
                    stream.newLineAtOffset(50f, 500f)
                    stream.showText(if (index == 0) "Marca anterior" else "Texto preservado")
                    stream.newLineAtOffset(0f, -460f)
                    stream.showText("${index + 1} / ${values.size}")
                    stream.endText()
                }
            }
            pdf.save(file)
        }
        return file
    }

    private fun pdfText(pdf: PDDocument) = PDFTextStripper().apply {
        suppressDuplicateOverlappingText = false
    }.getText(pdf)

    private fun occurrences(text: String, token: String) = text.windowed(token.length).count { it == token }
    private fun uri(file: File): String {
        val uri = Uri.parse("content://prefilled-fixtures/${file.name}")
        shadowOf(app.contentResolver).registerInputStreamSupplier(uri) { file.inputStream() }
        return uri.toString()
    }
    private fun local(uri: String) = File(requireNotNull(Uri.parse(uri).path))
    private fun track(uri: String) { projects += requireNotNull(local(uri).parentFile) }
    private fun model(scan: ScannedDocument) = DocumentModel(1, "Prefilled", scan.pdfUri,
        scan.pageCount, scan.createdAt, scan.sizeBytes, scan.thumbnailUri)

    private class Documents : DocumentRepository {
        var current: DocumentModel? = null
        override fun observeDocuments() = MutableStateFlow<List<DocumentModel>>(emptyList())
        override fun observeTrashedDocuments() = MutableStateFlow<List<DocumentModel>>(emptyList())
        override suspend fun findById(id: Long) = current
        override suspend fun replaceContent(id: Long, scan: ScannedDocument, allowProtectionRemoval: Boolean): DocumentModel =
            current!!.copy(pdfUri = scan.pdfUri, pageCount = scan.pageCount, sizeBytes = scan.sizeBytes,
                thumbnailUri = scan.thumbnailUri).also { current = it }
        override suspend fun prepareScan(pageUris: List<String>): ScannedDocument = error("unused")
        override suspend fun discardScan(scan: ScannedDocument) = Unit
        override suspend fun create(scan: ScannedDocument, name: String, tagId: Long?): Long = error("unused")
        override suspend fun update(id: Long, name: String, tagId: Long?) = error("unused")
        override suspend fun moveToTrash(id: Long) = error("unused")
        override suspend fun restoreFromTrash(id: Long) = error("unused")
        override suspend fun deletePermanently(id: Long) = error("unused")
        override suspend fun delete(document: DocumentModel) = error("unused")
        override suspend fun export(document: DocumentModel, targetUri: String) = error("unused")
        override suspend fun prepareShare(document: DocumentModel): String = error("unused")
    }
}
