package com.joseleandro.scaniqo.data.pdf

import android.app.Application
import android.net.Uri
import com.joseleandro.scaniqo.domain.model.DocumentModel
import com.joseleandro.scaniqo.domain.model.PdfPasswordRequiredException
import com.joseleandro.scaniqo.domain.model.ReadableDocument
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDStream
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import java.io.ByteArrayInputStream
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements

@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [28], application = Application::class, manifest = Config.NONE,
    shadows = [AndroidDocumentReaderRepositoryTest.HostFileUri::class],
)
class AndroidDocumentReaderRepositoryTest {
    @get:Rule val temporary = TemporaryFolder()
    private lateinit var repository: AndroidDocumentReaderRepository
    private val root get() = File(RuntimeEnvironment.getApplication().cacheDir, "pdf_workspaces")
    private val pageContent = "0.123 0.456 0.789 rg 20 30 40 50 re f\n".toByteArray()

    @Before fun setUp() {
        repository = AndroidDocumentReaderRepository(RuntimeEnvironment.getApplication(), Dispatchers.Unconfined)
    }

    @Test fun ordinaryReadUsesTheSavedFileWithoutCreatingOrCopyingAWorkspace() = runTest {
        val file = source(protected = false)
        val original = file.readBytes()
        val before = root.listFiles().orEmpty().map { it.name }.toSet()
        val result = repository.open(model(file, protected = false))
        assertEquals(file.toURI().toASCIIString(), result.pdfUri)
        assertNull(result.sessionId)
        assertEquals(before, root.listFiles().orEmpty().map { it.name }.toSet())
        repository.close(result)
        assertArrayEquals(original, file.readBytes())
    }

    @Test fun protectedReadUsesFinalSavedPagesAndDeletesOnlyTheTemporaryCopy() = runTest {
        val file = source(protected = true)
        val original = file.readBytes()
        // O leitor não consulta fontes ou receitas de edição ao lado do resultado final.
        File(file.parentFile, "sources.pdf").writeText("not the saved document")
        File(file.parentFile, "recipe.json").writeText("not an editor session")
        val result = repository.open(model(file), "reader-password")
        val temporaryCopy = File(requireNotNull(Uri.parse(result.pdfUri).path))
        try {
            assertNotEquals(file.canonicalPath, temporaryCopy.canonicalPath)
            assertEquals(root.canonicalFile, temporaryCopy.parentFile?.parentFile?.canonicalFile)
            assertEquals(2, result.pageCount)
            PDDocument.load(temporaryCopy).use { pdf ->
                assertFalse(pdf.isEncrypted)
                assertArrayEquals(pageContent, pdf.getPage(0).contents.use { it.readBytes() })
            }
            assertArrayEquals(original, file.readBytes())
        } finally {
            repository.close(result)
        }
        assertFalse(temporaryCopy.parentFile!!.exists())
        assertArrayEquals(original, file.readBytes())
    }

    @Test fun aUserPasswordCanReadAnUnmodifiablePdfWithoutEnablingEditing() = runTest {
        val file = source(protected = true, allowModification = false)
        PDDocument.load(file, "reader-password").use { assertFalse(it.currentAccessPermission.canModify()) }
        val result = repository.open(model(file), "reader-password")
        try {
            PDDocument.load(File(requireNotNull(Uri.parse(result.pdfUri).path))).use {
                assertEquals(2, it.numberOfPages)
            }
        } finally {
            repository.close(result)
        }
    }

    @Test fun missingAndWrongPasswordsNeverLeaveADecryptedSession() = runTest {
        val file = source(protected = true)
        // Inicializa o cache antes da comparação, incluindo limpeza de sessões de outro processo.
        PdfWorkspaceFiles(RuntimeEnvironment.getApplication()).initialize()
        val before = root.listFiles().orEmpty().map { it.name }.toSet()
        listOf(null, "wrong-password").forEach { password ->
            try {
                repository.open(model(file), password)
                fail("A senha incorreta deveria impedir a leitura")
            } catch (_: PdfPasswordRequiredException) {
                assertEquals(before, root.listFiles().orEmpty().map { it.name }.toSet())
            }
        }
        PDDocument.load(file, "reader-password").use { assertTrue(it.isEncrypted) }
    }

    @Test fun closingCannotRemoveAnUnownedEditorSessionOrSavedDocument() = runTest {
        root.mkdirs()
        val otherSession = File(root, UUID.randomUUID().toString()).apply { mkdir() }
        val marker = File(otherSession, "source.pdf").apply { writeText("editor-owned") }
        try {
            repository.close(ReadableDocument(marker.toURI().toString(), 1, otherSession.name))
            assertTrue(marker.exists())
            repository.close(ReadableDocument(marker.toURI().toString(), 1, "../documents"))
            assertTrue(marker.exists())
        } finally {
            marker.delete()
            otherSession.delete()
        }
    }

    private fun source(protected: Boolean, allowModification: Boolean = true): File {
        val file = File(temporary.newFolder(), "document.pdf")
        PDDocument().use { pdf ->
            pdf.addPage(PDPage().apply {
                setContents(PDStream(pdf, ByteArrayInputStream(pageContent)))
            })
            pdf.addPage(PDPage())
            if (protected) {
                val access = AccessPermission().apply { setCanModify(allowModification) }
                pdf.protect(StandardProtectionPolicy("owner-password", "reader-password", access).apply {
                    encryptionKeyLength = 128
                })
            }
            pdf.save(file)
        }
        return file
    }

    private fun model(file: File, protected: Boolean = true) = DocumentModel(
        id = 42, name = "PDF salvo", pdfUri = file.toURI().toASCIIString(),
        pageCount = 2, createdAt = 0, sizeBytes = file.length(),
        protectionKeyId = "key".takeIf { protected },
    )

    @Implements(Uri::class)
    class HostFileUri {
        companion object {
            @JvmStatic
            @Implementation
            fun fromFile(file: File): Uri = Uri.parse(file.toURI().toASCIIString())
        }
    }
}
