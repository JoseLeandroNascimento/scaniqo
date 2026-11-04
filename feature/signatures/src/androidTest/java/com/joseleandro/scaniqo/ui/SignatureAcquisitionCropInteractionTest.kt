package com.joseleandro.scaniqo.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.joseleandro.scaniqo.domain.model.PdfSignatureCrop
import com.joseleandro.scaniqo.ui.signatures.crop.SignatureCropEditor
import com.joseleandro.scaniqo.ui.theme.ScaniqoTheme
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SignatureAcquisitionCropInteractionTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val files = mutableListOf<File>()
    private val initialCrop = PdfSignatureCrop(.1f, .15f, .9f, .85f)

    @After fun removeFixtures() { files.forEach(File::delete) }

    @Test fun pinchZoomAndImagePanAdjustTheSelectedSourceWithoutDistortingIt() {
        val state = showEditor()
        pinchImage()
        var zoomed = initialCrop
        compose.runOnIdle {
            zoomed = state.crop
            assertTrue(zoomed.right - zoomed.left < initialCrop.right - initialCrop.left)
            assertEquals(
                (initialCrop.right - initialCrop.left) / (initialCrop.bottom - initialCrop.top),
                (zoomed.right - zoomed.left) / (zoomed.bottom - zoomed.top), .001f,
            )
        }
        compose.onNodeWithTag("signature_crop_canvas").performTouchInput {
            swipe(center, center + Offset(width * .05f, 0f), 400)
        }
        compose.runOnIdle {
            assertTrue("Dragging the image right selects pixels farther left", state.crop.left < zoomed.left)
            assertEquals(zoomed.right - zoomed.left, state.crop.right - state.crop.left, .001f)
            assertEquals(zoomed.bottom - zoomed.top, state.crop.bottom - state.crop.top, .001f)
            assertTrue(state.crop.isValid)
        }
    }

    @Test fun resetKeyRestoresImageTransformAsWellAsCrop() {
        val state = showEditor()
        pinchImage()
        var first = initialCrop
        compose.runOnIdle {
            first = state.crop
            state.crop = initialCrop
            state.resetKey++
        }
        compose.waitForIdle()
        pinchImage()
        compose.runOnIdle { assertCrop(first, state.crop) }
    }

    @Test fun cancelledPanRollsBackBothTransformAndSelectionAndBusyStateBlocksGestures() {
        val state = showEditor()
        pinchImage()
        var before = initialCrop
        compose.runOnIdle { before = state.crop }
        compose.onNodeWithTag("signature_crop_canvas").performTouchInput {
            down(center)
            moveTo(center + Offset(width * .07f, 0f))
            cancel()
        }
        compose.runOnIdle {
            assertCrop(before, state.crop)
            state.enabled = false
        }
        compose.waitForIdle()
        pinchImage()
        compose.runOnIdle { assertCrop(before, state.crop) }
    }

    private fun pinchImage() {
        compose.onNodeWithTag("signature_crop_canvas").performTouchInput {
            pinch(
                start0 = center - Offset(width * .1f, 0f),
                end0 = center - Offset(width * .2f, 0f),
                start1 = center + Offset(width * .1f, 0f),
                end1 = center + Offset(width * .2f, 0f),
                durationMillis = 400,
            )
        }
        compose.waitForIdle()
    }

    private fun showEditor(): EditorState {
        val file = File.createTempFile("signature-acquisition-", ".png", context.cacheDir).also(files::add)
        val bitmap = Bitmap.createBitmap(600, 200, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(Color.WHITE)
            Canvas(bitmap).drawLine(80f, 100f, 520f, 120f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLUE
                strokeWidth = 6f
            })
            file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally {
            bitmap.recycle()
        }
        val state = EditorState(initialCrop)
        compose.setContent {
            ScaniqoTheme {
                SignatureCropEditor(
                    uri = file.toURI().toString(),
                    aspectRatio = 3f,
                    crop = state.crop,
                    onCropChange = { state.crop = it },
                    onImageReady = { state.ready = it },
                    modifier = Modifier.fillMaxWidth().height(300.dp),
                    enableImageGestures = true,
                    resetKey = state.resetKey,
                    enabled = state.enabled,
                )
            }
        }
        compose.waitUntil(10_000) { state.ready }
        compose.waitForIdle()
        return state
    }

    private fun assertCrop(expected: PdfSignatureCrop, actual: PdfSignatureCrop) {
        assertEquals(expected.left, actual.left, .002f)
        assertEquals(expected.top, actual.top, .002f)
        assertEquals(expected.right, actual.right, .002f)
        assertEquals(expected.bottom, actual.bottom, .002f)
    }

    private class EditorState(initial: PdfSignatureCrop) {
        var crop by mutableStateOf(initial)
        var resetKey by mutableStateOf(0)
        var enabled by mutableStateOf(true)
        var ready by mutableStateOf(false)
    }
}
