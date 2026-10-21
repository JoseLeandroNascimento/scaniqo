package com.joseleandro.scaniqo.data.repository

import android.app.Application
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import com.joseleandro.scaniqo.domain.model.SignatureFont
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
class AndroidSignatureUnicodeTest {
    @Test fun typedSignaturesSaveVisibleTransparentPngsForSupportedScriptsAndFonts() = runTest {
        val context = RuntimeEnvironment.getApplication()
        val repository = AndroidSignatureRepository(context)
        for ((index, text) in listOf("ليلى أحمد 123", "王小明", "Мария Иванова", "Jose\u0301").withIndex()) {
            val model = repository.createTyped(text, text, SignatureFont.entries[index], Color.BLUE)
            try {
                val imageFile = File(context.filesDir, "signatures/${model.id}.png")
                assertEquals(Uri.fromFile(imageFile).toString(), model.imageUri)
                assertTrue(imageFile.isFile)
                // Decode bytes so native Android file paths do not depend on the host OS.
                val bytes = imageFile.readBytes()
                val bitmap = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
                try {
                    assertEquals(text, model.name)
                    assertTrue(bitmap.width in 1..1600)
                    assertTrue(bitmap.height in 1..600)
                    assertEquals(bitmap.width.toFloat() / bitmap.height, model.aspectRatio, .001f)
                    val pixels = IntArray(bitmap.width * bitmap.height)
                    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                    assertTrue(pixels.any { Color.alpha(it) > 0 })
                    assertTrue(pixels.any { Color.alpha(it) == 0 })
                } finally {
                    bitmap.recycle()
                }
            } finally {
                repository.delete(model.id)
            }
        }
    }
}
