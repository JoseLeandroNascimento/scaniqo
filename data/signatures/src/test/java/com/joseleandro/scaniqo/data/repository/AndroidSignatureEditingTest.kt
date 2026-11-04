package com.joseleandro.scaniqo.data.repository

import android.app.Application
import android.graphics.Color
import com.joseleandro.scaniqo.domain.model.SignatureFont
import com.joseleandro.scaniqo.domain.model.SignatureSourceType
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, manifest = Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AndroidSignatureEditingTest {
    @Test fun updatingTypedSignatureKeepsIdAndPersistsSourceWithNewPngVersion() = runTest {
        val context = RuntimeEnvironment.getApplication()
        val repository = AndroidSignatureRepository(context)
        val created = repository.createTyped("Pessoal", "Maria", SignatureFont.CURSIVE, Color.BLACK)
        val original = File(context.filesDir, "signatures/${created.id}.png")
        val originalBytes = original.readBytes()
        try {
            val updated = repository.updateTyped(
                created.id, "Trabalho", "Maria Silva", SignatureFont.BRUSH, Color.BLUE,
            )
            val current = File(context.filesDir, "signatures/${created.id}-1.png")
            assertEquals(created.id, updated.id)
            assertEquals(1, updated.imageVersion)
            assertEquals("Trabalho", updated.name)
            assertEquals(SignatureSourceType.TYPE, updated.source?.type)
            assertEquals("Maria Silva", updated.source?.text)
            assertEquals(SignatureFont.BRUSH, updated.source?.font)
            assertEquals(Color.BLUE, updated.source?.colorArgb)
            assertTrue(original.isFile)
            assertTrue(current.isFile)
            assertFalse(originalBytes.contentEquals(current.readBytes()))
            assertFalse(created.imageUri == updated.imageUri)

            val reopened = AndroidSignatureRepository(context).observeSignatures().first()
                .single { it.id == created.id }
            assertEquals(updated, reopened)
            assertFalse(original.exists())
            assertTrue(current.isFile)
        } finally {
            repository.delete(created.id)
            assertFalse(original.exists())
            assertFalse(File(context.filesDir, "signatures/${created.id}-1.png").exists())
        }
    }

    @Test fun legacyIndexWithoutSourceOrVersionLoadsAndCanBeEditedAsImage() = runTest {
        val context = RuntimeEnvironment.getApplication()
        val repository = AndroidSignatureRepository(context)
        val created = repository.createTyped("Original", "Ana", SignatureFont.CURSIVE, Color.BLACK)
        val library = File(context.filesDir, "signatures")
        val index = File(library, "index.json")
        try {
            index.writeText(
                """[{"id":"${created.id}","name":"Antiga","imageUri":"file:///outside.png","aspectRatio":${created.aspectRatio}}]""",
            )
            val legacy = AndroidSignatureRepository(context).observeSignatures().first()
                .single { it.id == created.id }
            assertEquals(0, legacy.imageVersion)
            assertEquals(null, legacy.source)
            assertEquals(created.imageUri, legacy.imageUri)

            val updated = repository.updateImage(created.id, "Atualizada", legacy.imageUri, false)
            assertEquals(created.id, updated.id)
            assertEquals(1, updated.imageVersion)
            assertEquals(SignatureSourceType.IMAGE, updated.source?.type)
            assertTrue(File(library, "${created.id}-1.png").isFile)
        } finally {
            repository.delete(created.id)
        }
    }
}
