package com.joseleandro.scaniqo.data.backup

import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DocumentBackupArchiveTest {
    @get:Rule val temporary = TemporaryFolder()
    private val archive = DocumentBackupArchive()

    @Test fun newBackupsContainOnlyReadableFinalPdfsAndMinimalManifest() = runBlocking {
        val source = source(mapOf("document.pdf" to "final", "sources.pdf" to "original",
            "recipe.json" to "{recipe}", "signature_1234.png" to "transparent", "thumbnail.jpg" to "thumbnail"))
        val target = temporary.newFile("backup.scaniqo")
        val written = archive.write(target, listOf(source))
        val destination = temporary.newFolder("restore")
        val restored = archive.extract(target, destination)
        assertEquals(written, restored)
        assertEquals(2, restored.version)
        assertTrue(restored.tags.isEmpty())
        assertFalse(restored.documents.single().editableProject)
        assertEquals(listOf("document.pdf"), restored.documents.single().files.map { it.name })
        assertArrayEquals(File(source.directory, "document.pdf").readBytes(),
            File(destination, restored.documents.single().key + "/document.pdf").readBytes())
        ZipFile(target).use { zip ->
            assertEquals(setOf("001 - Documento.pdf", DocumentBackupArchive.MANIFEST),
                zip.entries().asSequence().map { it.name }.toSet())
            val manifest = zip.getInputStream(zip.getEntry(DocumentBackupArchive.MANIFEST)).reader().readText()
            listOf(source.directory.absolutePath, "protectionKeyId", "tags", "tagKey", "editableProject",
                "sources.pdf", "recipe.json", "thumbnail.jpg", "signature_").forEach {
                assertFalse("O manifesto não deve incluir $it", manifest.contains(it))
            }
        }
    }

    @Test fun readablePdfNamesAreUniqueAndCannotCreatePaths() = runBlocking {
        val sources = List(2) {
            val source = source(mapOf("document.pdf" to "final"))
            source.copy(document = source.document.copy(name = "Recibo/saúde.pdf"))
        }
        val target = temporary.newFile()
        archive.write(target, sources)
        ZipFile(target).use { zip ->
            assertEquals(setOf("001 - Recibo_saúde.pdf", "002 - Recibo_saúde.pdf", DocumentBackupArchive.MANIFEST),
                zip.entries().asSequence().map { it.name }.toSet())
        }
        val restored = archive.extract(target, temporary.newFolder())
        assertEquals(listOf("Recibo/saúde.pdf", "Recibo/saúde.pdf"), restored.documents.map { it.name })
    }

    @Test fun protectedBackupContainsOnlyItsStillEncryptedPdfEvenWithSidecars() = runBlocking {
        val source = source(mapOf("document.pdf" to "ciphertext", "sources.pdf" to "plain",
            "recipe.json" to "private", "thumbnail.jpg" to "private", "signature_1234.png" to "private"),
            protected = true)
        val target = temporary.newFile()
        val written = archive.write(target, listOf(source))
        assertEquals(listOf("document.pdf"), written.documents.single().files.map { it.name })
        assertFalse(written.documents.single().editableProject)
        ZipFile(target).use { zip ->
            assertEquals(2, zip.size())
            assertEquals("ciphertext", zip.getInputStream(zip.getEntry("001 - Documento.pdf")).reader().readText())
        }
    }

    @Test fun legacyProjectsStillRestoreSourcesRecipeSignaturesAndTags() = runBlocking {
        val tag = BackupTag(UUID.randomUUID().toString(), "Recibos", 0xff556677.toInt(), "folder")
        val original = source(mapOf("document.pdf" to "final", "sources.pdf" to "original",
            "recipe.json" to "{recipe}", "signature_1234.png" to "transparent"))
        val source = original.copy(document = original.document.copy(tagKey = tag.key))
        val target = temporary.newFile()
        val written = writeLegacyDocumentBackup(target, listOf(source), listOf(tag))
        val destination = temporary.newFolder()
        val restored = archive.extract(target, destination)
        assertEquals(written, restored)
        assertTrue(restored.documents.single().editableProject)
        source.filenames.forEach {
            assertArrayEquals(File(source.directory, it).readBytes(), File(destination, source.document.key + "/" + it).readBytes())
        }
    }

    @Test fun legacyProtectedSidecarsAreStillRejected() = runBlocking {
        val source = source(mapOf("document.pdf" to "ciphertext", "sources.pdf" to "plain"), protected = true)
        val target = temporary.newFile()
        writeLegacyDocumentBackup(target, listOf(source))
        val destination = temporary.newFolder()
        expectFailure { archive.extract(target, destination) }
        assertTrue(destination.listFiles().orEmpty().isEmpty())
    }

    @Test fun checksumMismatchRollsBackExtractedFiles() = runBlocking {
        val target = packed()
        val altered = rewrite(target) { name, bytes ->
            name to if (name.endsWith(".pdf")) bytes.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() } else bytes
        }
        val output = temporary.newFolder()
        expectFailure { archive.extract(altered, output) }
        assertTrue(output.listFiles().orEmpty().isEmpty())
    }

    @Test fun undeclaredTraversalEntryCannotEscapeTheRestoreDirectory() = runBlocking {
        val target = packed()
        val altered = rewrite(target, extra = "../outside.txt" to "escape".toByteArray())
        val output = temporary.newFolder()
        expectFailure { archive.extract(altered, output) }
        assertFalse(File(output.parentFile, "outside.txt").exists())
        assertTrue(output.listFiles().orEmpty().isEmpty())
    }

    @Test fun declaredPdfTraversalIsRejectedBeforeExtraction() = runBlocking {
        val target = packed()
        val altered = rewrite(target) { name, bytes ->
            when (name) {
                "001 - Documento.pdf" -> "../outside.pdf" to bytes
                DocumentBackupArchive.MANIFEST -> name to bytes.toString(Charsets.UTF_8)
                    .replace("001 - Documento.pdf", "../outside.pdf").toByteArray(Charsets.UTF_8)
                else -> name to bytes
            }
        }
        val destination = temporary.newFolder()
        expectFailure { archive.extract(altered, destination) }
        assertTrue(destination.listFiles().orEmpty().isEmpty())
        assertFalse(File(destination.parentFile, "outside.pdf").exists())
    }

    @Test fun unsupportedVersionAndOversizedManifestAreRejectedBeforeExtraction() = runBlocking {
        val target = packed()
        val altered = rewrite(target) { name, bytes ->
            name to if (name == DocumentBackupArchive.MANIFEST)
                bytes.toString(Charsets.UTF_8).replace("\"version\":2", "\"version\":99").toByteArray() else bytes
        }
        val output = temporary.newFolder()
        expectFailure { archive.extract(altered, output) }
        expectFailure { DocumentBackupArchive(maxManifestBytes = 4).extract(target, output) }
        assertTrue(output.listFiles().orEmpty().isEmpty())
    }

    @Test fun expandedSizeAndCompressedSizeLimitsAreEnforced() = runBlocking {
        val target = temporary.newFile()
        archive.write(target, listOf(source(mapOf("document.pdf" to "A".repeat(4096)))))
        val output = temporary.newFolder()
        expectFailure { DocumentBackupArchive(maxUncompressedBytes = 1024).extract(target, output) }
        val tooSmall = temporary.newFile()
        expectFailure { DocumentBackupArchive(maxArchiveBytes = 16).write(tooSmall,
            listOf(source(mapOf("document.pdf" to "pdf")))) }
        assertFalse(tooSmall.exists())
        assertTrue(output.listFiles().orEmpty().isEmpty())
    }

    @Test fun cancellationDuringWriteOrExtractCleansOnlyItsTemporaryFiles() = runBlocking {
        val target = temporary.newFile()
        val source = source(mapOf("document.pdf" to "pdf"))
        expectFailure { archive.write(target, listOf(source)) { throw CancellationException() } }
        assertFalse(target.exists())
        assertTrue(File(source.directory, "document.pdf").isFile)
        val valid = packed()
        val output = temporary.newFolder()
        expectFailure { archive.extract(valid, output) { throw CancellationException() } }
        assertTrue(output.listFiles().orEmpty().isEmpty())
        assertTrue(valid.isFile)
    }

    @Test fun duplicateZipNamesAreRejectedBeforeAnyDocumentIsCreated() = runBlocking {
        val file = temporary.newFile()
        ZipOutputStream(file.outputStream()).use { zip ->
            for (name in listOf("spare0", "spare1")) {
                zip.putNextEntry(ZipEntry(name))
                zip.write("x".toByteArray())
                zip.closeEntry()
            }
        }
        val bytes = file.readBytes()
        val from = "spare1".toByteArray()
        val to = "spare0".toByteArray()
        for (index in 0..bytes.size - from.size) {
            if (from.indices.all { bytes[index + it] == from[it] }) to.copyInto(bytes, index)
        }
        file.writeBytes(bytes)
        val output = temporary.newFolder()
        expectFailure { archive.extract(file, output) }
        assertTrue(output.listFiles().orEmpty().isEmpty())
    }

    @Test fun missingDeclaredFileIsRejected() = runBlocking {
        val target = packed()
        val altered = rewrite(target) { name, bytes ->
            if (name.endsWith(".pdf")) null else name to bytes
        }
        expectFailure { archive.extract(altered, temporary.newFolder()) }
    }

    private suspend fun packed(): File = temporary.newFile().also {
        archive.write(it, listOf(source(mapOf("document.pdf" to "%PDF-example"))))
    }

    private fun source(files: Map<String, String>, protected: Boolean = false): BackupDocumentSource {
        val directory = temporary.newFolder()
        files.forEach { (name, value) -> File(directory, name).writeText(value) }
        return BackupDocumentSource(
            BackupDocument(UUID.randomUUID().toString(), "Documento", 1, 123L, null, protected,
                editableProject = "sources.pdf" in files),
            directory, files.keys.toList(),
        )
    }

    private fun rewrite(
        source: File,
        extra: Pair<String, ByteArray>? = null,
        transform: (String, ByteArray) -> Pair<String, ByteArray>? = { name, bytes -> name to bytes },
    ): File {
        val target = temporary.newFile()
        ZipFile(source).use { input ->
            ZipOutputStream(target.outputStream()).use { output ->
                val entries = input.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val changed = transform(entry.name, input.getInputStream(entry).use { it.readBytes() }) ?: continue
                    output.putNextEntry(ZipEntry(changed.first))
                    output.write(changed.second)
                    output.closeEntry()
                }
                if (extra != null) {
                    output.putNextEntry(ZipEntry(extra.first))
                    output.write(extra.second)
                    output.closeEntry()
                }
            }
        }
        return target
    }

    private suspend fun expectFailure(block: suspend () -> Unit) {
        var thrown = false
        try { block() } catch (_: Exception) { thrown = true }
        assertTrue("A entrada deveria ser recusada.", thrown)
    }
}

/** Fixture independente: mantém o formato publicado v1 mesmo depois de o writer passar a produzir v2. */
internal fun writeLegacyDocumentBackup(
    target: File,
    sources: List<BackupDocumentSource>,
    tags: List<BackupTag> = emptyList(),
): DocumentBackupManifest {
    val manifest = DocumentBackupManifest(
        format = "scaniqo.document-backup", version = 1, createdAt = 123456L,
        documents = sources.map { source ->
            source.document.copy(files = source.filenames.map { name ->
                val bytes = File(source.directory, name).readBytes()
                val hash = MessageDigest.getInstance("SHA-256").digest(bytes)
                    .joinToString("") { "%02x".format(it.toInt() and 255) }
                BackupFile(name, bytes.size.toLong(), hash)
            })
        },
        tags = tags,
    )
    ZipOutputStream(target.outputStream()).use { zip ->
        sources.forEach { source ->
            source.filenames.forEach { name ->
                zip.putNextEntry(ZipEntry(DocumentBackupArchive.path(source.document.key, name)))
                File(source.directory, name).inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        zip.putNextEntry(ZipEntry(DocumentBackupArchive.MANIFEST))
        zip.write(Json { encodeDefaults = true }.encodeToString(manifest).toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }
    return manifest
}
