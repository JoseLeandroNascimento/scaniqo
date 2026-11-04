package com.joseleandro.scaniqo.ui.signatures

import android.Manifest
import android.content.Context
import android.hardware.camera2.CameraManager
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.joseleandro.scaniqo.domain.model.PdfSignatureCrop
import com.joseleandro.scaniqo.ui.theme.ScaniqoTheme
import java.io.File
import java.net.URI
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SignatureCameraCaptureInteractionTest {
    @get:Rule val compose = createComposeRule()
    private val captures = CopyOnWriteArrayList<Pair<String, PdfSignatureCrop>>()

    @Before
    fun grantCameraPermission() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        assumeTrue(manager.cameraIdList.isNotEmpty())
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.CAMERA)
    }

    @After
    fun deleteCaptures() {
        captures.forEach { (uri, _) -> File(URI(uri)).delete() }
    }

    @Test
    fun cameraBecomesReadyAndDeliversOnePhotoWithTheVisibleGuideCrop() {
        compose.setContent {
            ScaniqoTheme {
                SignatureCameraScreen(
                    onCaptured = { uri, crop -> captures += uri to crop },
                    onCancel = {},
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        compose.waitUntil(30_000) {
            captures.isNotEmpty() || runCatching {
                !compose.onNodeWithTag("signature_camera_capture")
                    .fetchSemanticsNode().config.contains(SemanticsProperties.Disabled)
            }.getOrDefault(false)
        }
        compose.onNodeWithTag("signature_camera_flash").assertDoesNotExist()
        if (captures.isEmpty()) {
            compose.onNodeWithTag("signature_camera_capture").assertIsEnabled().performClick()
        }
        compose.waitUntil(30_000) { captures.isNotEmpty() }
        compose.waitForIdle()
        assertEquals(1, captures.size)
        val (uri, crop) = captures.single()
        assertTrue(File(URI(uri)).length() > 0)
        assertTrue(crop.isValid)
        assertTrue(crop.left > 0f && crop.right < 1f)
        assertTrue(crop.top > 0f && crop.bottom < 1f)
    }
}
