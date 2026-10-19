package com.joseleandro.scaniqo.data.pdf

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.joseleandro.scaniqo.data.local.document.AndroidDocumentFileStore
import com.joseleandro.scaniqo.domain.model.AppSettings
import com.joseleandro.scaniqo.domain.model.DocumentModel
import com.joseleandro.scaniqo.domain.model.PdfExportQuality
import com.joseleandro.scaniqo.domain.model.PdfMergeSource
import com.joseleandro.scaniqo.domain.model.ScannedDocument
import com.joseleandro.scaniqo.domain.repository.AppSettingsRepository
import com.joseleandro.scaniqo.domain.repository.DocumentRepository
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadow.api.Shadow
import kotlin.math.roundToInt

@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [28], application = Application::class, manifest = Config.NONE,
    shadows = [
        AndroidPdfEditorSettingsTest.HostFileUri::class,
        AndroidPdfEditorSettingsTest.SettingsPdfRenderer::class,
        AndroidPdfEditorSettingsTest.SettingsPdfPage::class,
    ],
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AndroidPdfEditorSettingsTest {
    @get:Rule val temporary = TemporaryFolder()
    private val preferences = FakeSettings()
    private val createdProjects = mutableListOf<File>()
    private lateinit var repository: AndroidPdfEditorRepository

    @Before fun setUp() {
        val app = RuntimeEnvironment.getApplication()
        repository = AndroidPdfEditorRepository(
            app, UnusedDocuments(), AndroidDocumentFileStore(app, Dispatchers.Unconfined),
            Dispatchers.Unconfined, preferences,
        )
    }

    @After fun cleanUp() {
        createdProjects.forEach(File::deleteRecursively)
    }

    @Test fun importReadsCurrentDefaultsAndPersistsThemInTheEditableProject() = runTest {
        // A preferência muda depois da criação do repositório, antes da operação.
        preferences.settings.value = AppSettings(
            exportQuality = PdfExportQuality.SMALL, pageNumbers = true,
        )
        val original = source("external.pdf", "Texto original")
        val originalBytes = original.file.readBytes()
        val scan = track(repository.importPdf(original.uri, null, null))
        val recipe = recipe(scan)
        assertEquals(PdfExportQuality.SMALL, recipe.exportQuality)
        assertTrue(recipe.showPageNumbers)
        assertFalse(recipe.ocrEnabled)
        assertArrayEquals(originalBytes, original.file.readBytes())
        PDDocument.load(localFile(scan.pdfUri)).use { pdf ->
            assertTrue(PDFTextStripper().getText(pdf).contains("Texto original"))
            assertTrue(PdfPageNumberLayer.hasLayer(pdf.getPage(0)))
        }
    }

    @Test fun reopeningAnImportedProjectKeepsItsOptionsAfterGlobalDefaultsChange() = runTest {
        preferences.settings.value = AppSettings(
            exportQuality = PdfExportQuality.SMALL, pageNumbers = true,
        )
        val source = source("existing.pdf", "Projeto existente")
        val scan = track(repository.importPdf(source.uri, null, null))
        val savedPdf = localFile(scan.pdfUri).readBytes()
        val savedRecipe = recipeFile(scan).readBytes()
        preferences.settings.value = AppSettings(
            exportQuality = PdfExportQuality.HIGH_QUALITY, pageNumbers = false, ocrEnabled = true,
        )
        val workspace = repository.open(scan.model(), null)
        try {
            assertEquals(PdfExportQuality.SMALL, workspace.exportQuality)
            assertTrue(workspace.showPageNumbers)
            assertFalse(workspace.ocrEnabled)
            assertArrayEquals(savedPdf, localFile(scan.pdfUri).readBytes())
            assertArrayEquals(savedRecipe, recipeFile(scan).readBytes())
        } finally {
            repository.discard(workspace)
        }
    }

    @Test fun mergingCreatesANewProjectWithCurrentDefaultsAndLeavesSourcesUntouched() = runTest {
        val first = source("first.pdf", "Primeiro documento")
        val second = source("second.pdf", "Segundo documento")
        val firstBytes = first.file.readBytes()
        val secondBytes = second.file.readBytes()
        preferences.settings.value = AppSettings(
            exportQuality = PdfExportQuality.BALANCED, pageNumbers = true,
        )
        val scan = track(repository.mergePdfs(listOf(PdfMergeSource(first.uri), PdfMergeSource(second.uri))))
        val recipe = recipe(scan)
        assertEquals(2, scan.pageCount)
        assertEquals(PdfExportQuality.BALANCED, recipe.exportQuality)
        assertTrue(recipe.showPageNumbers)
        assertFalse(recipe.ocrEnabled)
        assertArrayEquals(firstBytes, first.file.readBytes())
        assertArrayEquals(secondBytes, second.file.readBytes())
        PDDocument.load(localFile(scan.pdfUri)).use { pdf ->
            assertEquals(2, pdf.numberOfPages)
            val text = PDFTextStripper().getText(pdf)
            assertTrue(text.contains("Primeiro documento") && text.contains("Segundo documento"))
            assertTrue((0 until pdf.numberOfPages).all { PdfPageNumberLayer.hasLayer(pdf.getPage(it)) })
        }
    }

    @Test fun openingAFileWithoutRecipeUsesDefaultsWithoutWritingTheSource() = runTest {
        val source = source("no-recipe.pdf", "Arquivo sem receita")
        val originalBytes = source.file.readBytes()
        preferences.settings.value = AppSettings(
            exportQuality = PdfExportQuality.BALANCED, pageNumbers = true, ocrEnabled = true,
        )
        val document = DocumentModel(
            7, "Arquivo externo", source.uri, 1, 1L, source.file.length(),
        )
        val workspace = repository.open(document, null)
        try {
            assertEquals(PdfExportQuality.BALANCED, workspace.exportQuality)
            assertTrue(workspace.showPageNumbers)
            assertTrue(workspace.ocrEnabled)
            assertArrayEquals(originalBytes, source.file.readBytes())
        } finally {
            repository.discard(workspace)
        }
    }

    @Test fun legacyRecipeWithOmittedOptionsKeepsLegacyDefaultsInsteadOfGlobalOnes() = runTest {
        val app = RuntimeEnvironment.getApplication()
        val directory = File(File(app.filesDir, "documents"), UUID.randomUUID().toString())
        check(directory.mkdirs())
        createdProjects += directory
        val source = source("legacy-source.pdf", "Projeto legado")
        val pdf = source.file.copyTo(File(directory, "document.pdf"))
        source.file.copyTo(File(directory, "sources.pdf"))
        File(directory, "recipe.json").writeText(
            """{"pages":[{"id":"legacy-page","sourcePageIndex":0,"layout":{}}]}""",
        )
        preferences.settings.value = AppSettings(
            exportQuality = PdfExportQuality.SMALL, pageNumbers = true, ocrEnabled = true,
        )
        val document = DocumentModel(8, "Legado", Uri.fromFile(pdf).toString(), 1, 1L, pdf.length())
        val workspace = repository.open(document, null)
        try {
            assertEquals(PdfExportQuality.HIGH_QUALITY, workspace.exportQuality)
            assertFalse(workspace.showPageNumbers)
            assertFalse(workspace.ocrEnabled)
            assertEquals("legacy-page", workspace.pages.single().id)
        } finally {
            repository.discard(workspace)
        }
    }

    private fun track(scan: ScannedDocument): ScannedDocument = scan.also {
        createdProjects += requireNotNull(localFile(it.pdfUri).parentFile)
    }

    private fun localFile(uri: String) = File(requireNotNull(Uri.parse(uri).path))
    private fun recipeFile(scan: ScannedDocument) = File(localFile(scan.pdfUri).parentFile, "recipe.json")
    private fun recipe(scan: ScannedDocument) = Json.decodeFromString<PdfProjectRecipe>(recipeFile(scan).readText())
    private fun ScannedDocument.model() = DocumentModel(
        1, "Documento", pdfUri, pageCount, createdAt, sizeBytes, thumbnailUri, protectionKeyId = protectionKeyId,
    )

    private data class Source(val file: File, val uri: String)

    private fun source(name: String, text: String): Source {
        val file = temporary.newFile(name)
        PDDocument().use { pdf ->
            val page = PDPage()
            pdf.addPage(page)
            PDPageContentStream(pdf, page).use { stream ->
                stream.beginText()
                stream.setFont(PDType1Font.HELVETICA, 16f)
                stream.newLineAtOffset(50f, 700f)
                stream.showText(text)
                stream.endText()
            }
            pdf.save(file)
        }
        val uri = Uri.parse("content://editor-settings-fixtures/$name")
        shadowOf(RuntimeEnvironment.getApplication().contentResolver)
            .registerInputStreamSupplier(uri) { file.inputStream() }
        return Source(file, uri.toString())
    }

    private class FakeSettings : AppSettingsRepository {
        override val settings = MutableStateFlow(AppSettings())
        override suspend fun update(transform: (AppSettings) -> AppSettings) {
            settings.value = transform(settings.value)
        }
    }

    /** O repositório cria projetos; estes testes não cadastram documentos no banco. */
    private class UnusedDocuments : DocumentRepository {
        override fun observeDocuments() = MutableStateFlow<List<DocumentModel>>(emptyList())
        override suspend fun findById(id: Long): DocumentModel? = error("unused")
        override suspend fun prepareScan(pageUris: List<String>): ScannedDocument = error("unused")
        override suspend fun discardScan(scan: ScannedDocument) = Unit
        override suspend fun create(scan: ScannedDocument, name: String, tagId: Long?): Long = error("unused")
        override suspend fun update(id: Long, name: String, tagId: Long?) = error("unused")
        override suspend fun replaceContent(id: Long, scan: ScannedDocument, allowProtectionRemoval: Boolean): DocumentModel = error("unused")
        override suspend fun delete(document: DocumentModel) = error("unused")
        override suspend fun export(document: DocumentModel, targetUri: String) = error("unused")
        override suspend fun prepareShare(document: DocumentModel): String = error("unused")
    }

    /**
     * O backend SDK 28 do Robolectric devolve zero páginas. Lemos o PDF real com PDFBox;
     * só a miniatura é substituída, pois estes testes validam preferências e arquivos.
     */
    @Implements(PdfRenderer::class)
    class SettingsPdfRenderer {
        private var sizes: List<Pair<Int, Int>> = emptyList()
        private var closed = false

        @Implementation
        fun __constructor__(input: ParcelFileDescriptor) {
            ParcelFileDescriptor.AutoCloseInputStream(input).use { stream ->
                PDDocument.load(stream).use { pdf ->
                    sizes = (0 until pdf.numberOfPages).map { index ->
                        val page = pdf.getPage(index)
                        val box = page.cropBox
                        val rotated = ((page.rotation % 360) + 360) % 360 in setOf(90, 270)
                        val width = (if (rotated) box.height else box.width).roundToInt().coerceAtLeast(1)
                        val height = (if (rotated) box.width else box.height).roundToInt().coerceAtLeast(1)
                        width to height
                    }
                }
            }
        }

        @Implementation
        fun getPageCount(): Int {
            check(!closed)
            return sizes.size
        }

        @Implementation
        fun openPage(index: Int): PdfRenderer.Page {
            check(!closed)
            require(index in sizes.indices)
            return Shadow.newInstanceOf(PdfRenderer.Page::class.java).also {
                val shadow = Shadow.extract<SettingsPdfPage>(it)
                shadow.pageWidth = sizes[index].first
                shadow.pageHeight = sizes[index].second
            }
        }

        @Implementation
        fun close() { closed = true }
    }

    @Implements(PdfRenderer.Page::class)
    class SettingsPdfPage {
        var pageWidth = 1
        var pageHeight = 1

        @Implementation
        fun getWidth(): Int = pageWidth

        @Implementation
        fun getHeight(): Int = pageHeight

        @Suppress("UNUSED_PARAMETER")
        @Implementation
        fun render(destination: Bitmap, clip: Rect?, transform: Matrix?, mode: Int) {
            destination.eraseColor(Color.WHITE)
        }

        @Implementation
        fun close() = Unit
    }

    /** Uri.fromFile espera caminhos Android. Mantém o teste portátil no host Windows. */
    @Implements(Uri::class)
    class HostFileUri {
        companion object {
            @JvmStatic
            @Implementation
            fun fromFile(file: File): Uri = Uri.parse(file.toURI().toASCIIString())
        }
    }
}
