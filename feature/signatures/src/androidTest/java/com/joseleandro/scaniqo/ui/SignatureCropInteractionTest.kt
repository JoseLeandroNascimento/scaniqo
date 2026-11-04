package com.joseleandro.scaniqo.ui

import com.joseleandro.scaniqo.feature.signatures.R

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.test.platform.app.InstrumentationRegistry
import com.joseleandro.scaniqo.domain.model.PdfSignatureCrop
import com.joseleandro.scaniqo.ui.signatures.crop.SignatureCropDialog
import com.joseleandro.scaniqo.ui.theme.ScaniqoTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

class SignatureCropInteractionTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val files = mutableListOf<File>()

    @After
    fun removeFixtures() {
        files.forEach { it.delete() }
    }

    @Test
    fun cornerGestureUsesOriginalImageAspectAndOnlyAppliesWhenConfirmed() {
        val result = showCrop()
        dragTopLeftCorner()
        compose.onNodeWithTag("signature_crop_sheet").assertIsDisplayed()
        compose.runOnIdle { assertTrue(result.applied.isEmpty()) }
        compose.onNodeWithTag("signature_crop_apply").assertIsEnabled().performClick()
        compose.runOnIdle {
            val crop = result.applied.single()
            assertEquals(.2f, crop.left, .025f)
            assertEquals(.25f, crop.top, .025f)
            assertEquals(1f, crop.right, .001f)
            assertEquals(1f, crop.bottom, .001f)
            assertEquals(0, result.dismissed)
        }
        val image = BitmapFactory.decodeFile(result.file.absolutePath)
        try {
            assertTrue(image.hasAlpha())
            assertEquals(0, Color.alpha(image.getPixel(0, 0)))
            assertEquals(600, image.width)
            assertEquals(200, image.height)
        } finally {
            image.recycle()
        }
    }

    @Test
    fun cancellingCropDiscardsGestureChangesWithoutApplying() {
        val result = showCrop()
        dragTopLeftCorner()
        compose.onNodeWithTag("signature_crop_cancel").performClick()
        compose.runOnIdle {
            assertTrue(result.applied.isEmpty())
            assertEquals(1, result.dismissed)
        }
        compose.onNodeWithTag("signature_crop_sheet").assertDoesNotExist()
    }

    @Test
    fun interruptedGestureRestoresTheSelection() {
        val result = showCrop()
        val canvas = compose.onNodeWithTag("signature_crop_canvas")
        val image = imageBounds()
        canvas.performTouchInput {
            down(image.first)
            moveTo(image.first + Offset(image.second.x * .2f, image.second.y * .25f))
            cancel()
        }
        compose.onNodeWithTag("signature_crop_apply").performClick()
        compose.runOnIdle { assertEquals(PdfSignatureCrop(0f, 0f, 1f, 1f), result.applied.single()) }
    }

    @Test
    fun accessibleEdgeActionsAndResetWorkWithoutSliders() {
        val result = showCrop()
        val actionLabel = context.getString(R.string.signature_crop_left) + ": " +
            context.getString(R.string.signature_crop_inward)
        val actions = compose.onNodeWithTag("signature_crop_canvas").fetchSemanticsNode()
            .config[SemanticsActions.CustomActions]
        compose.runOnIdle { assertTrue(actions.single { it.label == actionLabel }.action()) }
        compose.onNodeWithTag("signature_crop_reset").assertIsEnabled().performClick()
        compose.onNodeWithTag("signature_crop_apply").performClick()
        compose.runOnIdle { assertEquals(PdfSignatureCrop(0f, 0f, 1f, 1f), result.applied.single()) }
    }

    private fun showCrop(): CropResult {
        val file = File.createTempFile("signature-crop-", ".png", context.cacheDir).also(files::add)
        val bitmap = Bitmap.createBitmap(600, 200, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawLine(80f, 100f, 520f, 120f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            strokeWidth = 6f
        })
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        val result = CropResult(file)
        var shown by mutableStateOf(true)
        compose.setContent {
            ScaniqoTheme {
                if (shown) {
                    SignatureCropDialog(
                        uri = file.toURI().toString(),
                        aspectRatio = 1f,
                        onDismiss = {
                            result.dismissed++
                            shown = false
                        },
                        onApply = { left, top, right, bottom ->
                            result.applied += PdfSignatureCrop(left, top, right, bottom)
                            shown = false
                        },
                    )
                }
            }
        }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("signature_crop_apply").fetchSemanticsNodes()
                .any { it.config.getOrNull(SemanticsProperties.Disabled) == null }
        }
        compose.waitForIdle()
        return result
    }

    private fun dragTopLeftCorner() {
        val image = imageBounds()
        compose.onNodeWithTag("signature_crop_canvas").performTouchInput {
            swipe(image.first, image.first + Offset(image.second.x * .2f, image.second.y * .25f), 400)
        }
    }

    private fun imageBounds(): Pair<Offset, Offset> {
        val canvas = compose.onNodeWithTag("signature_crop_canvas").fetchSemanticsNode().boundsInRoot
        val inset = 24f * context.resources.displayMetrics.density
        val width = minOf(canvas.width - inset * 2f, (canvas.height - inset * 2f) * 3f)
        val height = width / 3f
        return Offset((canvas.width - width) / 2f, (canvas.height - height) / 2f) to Offset(width, height)
    }

    private data class CropResult(
        val file: File,
        val applied: MutableList<PdfSignatureCrop> = mutableListOf(),
        var dismissed: Int = 0,
    )
}
