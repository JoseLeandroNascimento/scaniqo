package com.joseleandro.scaniqo.ui.signatures

import com.joseleandro.scaniqo.core.resources.R as CoreResourcesR
import com.joseleandro.scaniqo.feature.signatures.R

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.hardware.display.DisplayManager
import android.os.SystemClock
import android.os.Handler
import android.os.Looper
import android.view.View
import android.provider.Settings
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraState
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.camera.view.TransformExperimental
import androidx.camera.view.transform.OutputTransform
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as ComposeSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.joseleandro.scaniqo.domain.model.PdfSignatureCrop
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors

@Composable
fun SignatureCameraScreen(
    onCaptured: (String, PdfSignatureCrop) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val activity = context.cameraActivity()
    val lifecycleOwner = LocalLifecycleOwner.current
    var permissionRefresh by remember { mutableIntStateOf(0) }
    var requestedPermission by rememberSaveable { mutableStateOf(false) }
    var permanentlyDenied by rememberSaveable { mutableStateOf(false) }
    val hasPermission = permissionRefresh.let { context.hasSignatureCameraPermission() }
    val showRationale = activity?.let {
        ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.CAMERA)
    } == true
    val needsSettings = permanentlyDenied && !showRationale
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        permanentlyDenied = !granted && activity != null &&
            !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)
        permissionRefresh++
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) permissionRefresh++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(Unit) {
        if (!context.hasSignatureCameraPermission() && !requestedPermission && !showRationale) {
            requestedPermission = true
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
    Box(modifier) {
        if (hasPermission) {
            SignatureCameraCapture(
                onCaptured = onCaptured,
                onPermissionLost = { permissionRefresh++ },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    painterResource(CoreResourcesR.drawable.ic_document_scan),
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    stringResource(
                        if (needsSettings) R.string.signature_camera_permission_settings
                        else R.string.signature_camera_permission_reason,
                    ),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Button(
                    onClick = {
                        if (context.hasSignatureCameraPermission()) {
                            permissionRefresh++
                        } else if (needsSettings) {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.fromParts("package", context.packageName, null),
                                ),
                            )
                        } else {
                            requestedPermission = true
                            permissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Text(stringResource(
                        if (needsSettings) R.string.signature_camera_open_settings
                        else R.string.signature_camera_allow,
                    ))
                }
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) { Text(stringResource(R.string.signature_camera_cancel)) }
            }
        }
    }
}

@androidx.annotation.OptIn(markerClass = [TransformExperimental::class])
@Composable
private fun SignatureCameraCapture(
    onCaptured: (String, PdfSignatureCrop) -> Unit,
    onPermissionLost: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val onCapturedNow by rememberUpdatedState(onCaptured)
    val onPermissionLostNow by rememberUpdatedState(onPermissionLost)
    val executor = remember(context) { ContextCompat.getMainExecutor(context) }
    val scope = rememberCoroutineScope()
    val previewView = remember(context) {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    val lifetime = remember(previewView) { SignatureCaptureLifetime() }
    var viewport by remember { mutableStateOf<SignatureCameraViewport?>(null) }
    var session by remember { mutableStateOf<SignatureCameraSession?>(null) }
    var ready by remember { mutableStateOf(false) }
    var isCapturing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Int?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    var scan by remember { mutableStateOf(SignatureScanResult()) }
    var resumed by remember { mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }

    fun isCurrent(bound: SignatureCameraSession): Boolean =
        lifetime.active && bound.active && session === bound &&
            previewView.signatureViewport() == bound.viewport

    fun capture() {
        if (!context.hasSignatureCameraPermission()) {
            onPermissionLostNow()
            return
        }
        if (isCapturing || lifetime.completed || !ready || !resumed || error != null) return
        val bound = session
        val outputTransform = previewView.outputTransform
        if (bound == null || !isCurrent(bound) || outputTransform == null) {
            error = R.string.signature_camera_frame_changed
            return
        }
        val guide = SignatureCameraFrameGeometry.guide(
            bound.viewport.width.toFloat(), bound.viewport.height.toFloat(),
        )
        isCapturing = true
        bound.analysisEnabled = false
        error = null
        try {
            val directory = File(context.cacheDir, "signature_captures")
            check(directory.isDirectory || directory.mkdirs())
            val file = File.createTempFile("signature_", ".jpg", directory)
            lifetime.pendingFile = file
            bound.capture.takePicture(
                ImageCapture.OutputFileOptions.Builder(file).build(),
                executor,
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                        if (!isCurrent(bound) || !resumed) {
                            file.delete()
                            if (lifetime.pendingFile === file) lifetime.pendingFile = null
                            if (lifetime.active) {
                                isCapturing = false
                                error = R.string.signature_camera_frame_changed
                            }
                            return
                        }
                        scope.launch {
                            var retained = false
                            try {
                                val crop = withContext(Dispatchers.IO) {
                                    SignatureCameraFrameGeometry.cropInOrientedImage(file, outputTransform, guide)
                                }
                                check(isCurrent(bound) && resumed)
                                lifetime.completed = true
                                onCapturedNow(Uri.fromFile(file).toString(), crop)
                                retained = true
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: Exception) {
                                lifetime.completed = false
                                if (lifetime.active) error = if (isCurrent(bound)) {
                                    R.string.signature_camera_capture_failed
                                } else R.string.signature_camera_frame_changed
                            } finally {
                                if (!retained) file.delete()
                                if (lifetime.pendingFile === file) lifetime.pendingFile = null
                                if (lifetime.active) isCapturing = false
                            }
                        }
                    }

                    override fun onError(exception: ImageCaptureException) {
                        file.delete()
                        if (lifetime.pendingFile === file) lifetime.pendingFile = null
                        if (lifetime.active) {
                            isCapturing = false
                            error = if (isCurrent(bound)) R.string.signature_camera_capture_failed
                            else R.string.signature_camera_frame_changed
                        }
                    }
                },
            )
        } catch (_: SecurityException) {
            lifetime.pendingFile?.delete()
            lifetime.pendingFile = null
            isCapturing = false
            onPermissionLostNow()
        } catch (_: Exception) {
            lifetime.pendingFile?.delete()
            lifetime.pendingFile = null
            isCapturing = false
            error = R.string.signature_camera_capture_failed
        }
    }

    val captureNow by rememberUpdatedState(::capture)
    SideEffect {
        session?.analysisEnabled = ready && resumed && !isCapturing && !lifetime.completed &&
            error == null && session?.autoAvailable == true
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, _ ->
            resumed = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            if (!resumed) {
                session?.let { it.analysisEnabled = false; it.analysisEpoch++ }
                scan = SignatureScanResult()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    DisposableEffect(previewView) {
        fun refreshViewport() { viewport = previewView.signatureViewport() }
        val layoutListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> refreshViewport() }
        val attachListener = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) = refreshViewport()
            override fun onViewDetachedFromWindow(view: View) { viewport = null }
        }
        val displays = context.getSystemService(DisplayManager::class.java)
        val displayListener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) = Unit
            override fun onDisplayRemoved(displayId: Int) = refreshViewport()
            override fun onDisplayChanged(displayId: Int) {
                if (displayId == previewView.display?.displayId) refreshViewport()
            }
        }
        previewView.addOnLayoutChangeListener(layoutListener)
        previewView.addOnAttachStateChangeListener(attachListener)
        displays?.registerDisplayListener(displayListener, Handler(Looper.getMainLooper()))
        previewView.post { refreshViewport() }
        onDispose {
            lifetime.active = false
            lifetime.pendingFile?.delete()
            previewView.removeOnLayoutChangeListener(layoutListener)
            previewView.removeOnAttachStateChangeListener(attachListener)
            displays?.unregisterDisplayListener(displayListener)
        }
    }
    DisposableEffect(lifecycleOwner, previewView, viewport, retry) {
        val currentViewport = viewport
        if (currentViewport == null) {
            onDispose {}
        } else {
            val bound = SignatureCameraSession(currentViewport)
            val analysisExecutor = Executors.newSingleThreadExecutor()
            var provider: ProcessCameraProvider? = null
            ready = false
            error = null
            scan = SignatureScanResult()
            var sawIdle = previewView.previewStreamState.value != PreviewView.StreamState.STREAMING
            val streamObserver = Observer<PreviewView.StreamState> {
                if (it == PreviewView.StreamState.IDLE) {
                    sawIdle = true
                    bound.analysisEnabled = false
                    bound.analysisEpoch++
                    bound.analyzeAfterMs = SystemClock.elapsedRealtime() + 600
                    scan = SignatureScanResult()
                }
                if (!bound.autoAvailable) scan = SignatureScanResult(SignatureScanHint.MANUAL)
                bound.previewTransform = previewView.outputTransform
                ready = bound.active && sawIdle && it == PreviewView.StreamState.STREAMING &&
                    bound.previewTransform != null
            }
            val cameraObserver = Observer<CameraState> {
                if (it.error != null) {
                    ready = false
                    bound.analysisEnabled = false
                    error = R.string.signature_camera_unavailable
                }
            }
            bound.analysis.setAnalyzer(
                analysisExecutor,
                SignatureFrameAnalyzer(
                    viewport = currentViewport,
                    previewTransform = { bound.previewTransform },
                    enabled = { bound.active && bound.analysisEnabled && SystemClock.elapsedRealtime() >= bound.analyzeAfterMs },
                    resetVersion = { bound.analysisEpoch },
                    onResult = { result, version ->
                        val deliveredAt = SystemClock.elapsedRealtime()
                        executor.execute {
                            if (SystemClock.elapsedRealtime() - deliveredAt > 350) {
                                bound.analysisEpoch++
                            } else if (isCurrent(bound) && bound.analysisEnabled && resumed && version == bound.analysisEpoch) {
                                scan = result
                                if (result.shouldCapture) captureNow()
                            }
                        }
                    },
                ),
            )
            previewView.previewStreamState.observe(lifecycleOwner, streamObserver)
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener({
                if (bound.active) {
                    try {
                        if (!context.hasSignatureCameraPermission()) {
                            onPermissionLostNow()
                        } else if (previewView.signatureViewport() != currentViewport) {
                            viewport = previewView.signatureViewport()
                        } else {
                            val activeProvider = future.get()
                            provider = activeProvider
                            val selector = when {
                                activeProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) ->
                                    CameraSelector.DEFAULT_BACK_CAMERA
                                activeProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) ->
                                    CameraSelector.DEFAULT_FRONT_CAMERA
                                else -> null
                            }
                            if (selector == null) {
                                error = R.string.signature_camera_missing
                            } else {
                                val viewPort = requireNotNull(previewView.getViewPort(currentViewport.rotation))
                                bound.preview.surfaceProvider = previewView.surfaceProvider
                                val group = UseCaseGroup.Builder()
                                    .setViewPort(viewPort)
                                    .addUseCase(bound.preview)
                                    .addUseCase(bound.capture)
                                    .addUseCase(bound.analysis)
                                    .build()
                                val camera = try {
                                    activeProvider.bindToLifecycle(lifecycleOwner, selector, group)
                                } catch (_: IllegalArgumentException) {
                                    activeProvider.unbind(bound.preview, bound.capture, bound.analysis)
                                    bound.autoAvailable = false
                                    scan = SignatureScanResult(SignatureScanHint.MANUAL)
                                    activeProvider.bindToLifecycle(
                                        lifecycleOwner, selector,
                                        UseCaseGroup.Builder().setViewPort(viewPort)
                                            .addUseCase(bound.preview).addUseCase(bound.capture).build(),
                                    )
                                }
                                bound.camera = camera
                                session = bound
                                camera.cameraInfo.cameraState.observe(lifecycleOwner, cameraObserver)
                            }
                        }
                    } catch (_: SecurityException) {
                        onPermissionLostNow()
                        error = R.string.signature_camera_unavailable
                    } catch (_: Exception) {
                        error = R.string.signature_camera_unavailable
                    }
                }
            }, executor)
            onDispose {
                bound.active = false
                bound.analysisEnabled = false
                bound.analysis.clearAnalyzer()
                if (session === bound) session = null
                ready = false
                bound.camera?.cameraInfo?.cameraState?.removeObserver(cameraObserver)
                previewView.previewStreamState.removeObserver(streamObserver)
                provider?.unbind(bound.preview, bound.capture, bound.analysis)
                analysisExecutor.shutdown()
            }
        }
    }

    Column(modifier) {
        Box(
            modifier = Modifier.fillMaxWidth().weight(1f).background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
            SignatureCaptureGuide(
                modifier = Modifier.fillMaxSize(),
                progress = scan.progress,
                detected = scan.hint == SignatureScanHint.HOLD_STILL,
                scanning = ready && resumed && !isCapturing && error == null,
            )
            Surface(
                modifier = Modifier.align(Alignment.TopCenter).padding(16.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
            ) {
                AnimatedContent(
                    targetState = when {
                        isCapturing -> R.string.signature_camera_capturing
                        scan.hint == SignatureScanHint.HOLD_STILL -> R.string.signature_scan_hold_still
                        scan.hint == SignatureScanHint.MORE_LIGHT -> R.string.signature_scan_more_light
                        scan.hint == SignatureScanHint.MOVE_INSIDE -> R.string.signature_scan_move_inside
                        scan.hint == SignatureScanHint.MANUAL -> R.string.signature_scan_manual
                        else -> R.string.signature_scan_align
                    },
                    label = "signatureScanHint",
                ) { message ->
                    Text(
                        text = stringResource(message),
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                            .semantics { liveRegion = LiveRegionMode.Polite },
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            if (ready && session?.autoAvailable == true && scan.hint != SignatureScanHint.MANUAL && !isCapturing && error == null) {
                Surface(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                ) {
                    Text(
                        stringResource(R.string.signature_scan_auto),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
            val currentError = error
            if (currentError != null) {
                Surface(modifier = Modifier.padding(24.dp), shape = MaterialTheme.shapes.large) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(stringResource(currentError), textAlign = TextAlign.Center)
                        Button(onClick = { retry++ }, enabled = !isCapturing) {
                            Text(stringResource(R.string.signature_camera_retry))
                        }
                    }
                }
            } else if (!ready || isCapturing || lifetime.completed) {
                Surface(shape = MaterialTheme.shapes.large) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator(Modifier.size(32.dp))
                        Text(stringResource(
                            if (isCapturing || lifetime.completed) R.string.signature_camera_capturing
                            else R.string.signature_camera_starting,
                        ))
                    }
                }
            }
        }
        Surface(tonalElevation = 3.dp, shadowElevation = 8.dp) {
            Button(
                onClick = ::capture,
                enabled = ready && resumed && session != null && !isCapturing &&
                    !lifetime.completed && error == null,
                modifier = Modifier.fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .heightIn(min = 52.dp)
                    .testTag("signature_camera_capture"),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
            ) {
                Icon(painterResource(CoreResourcesR.drawable.ic_document_scan), contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.signature_scan_capture_now), textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun SignatureCaptureGuide(
    modifier: Modifier = Modifier,
    progress: Float = 0f,
    detected: Boolean = false,
    scanning: Boolean = false,
) {
    val animatedProgress by animateFloatAsState(progress, tween(160), label = "signatureReadiness")
    val frameColor by animateColorAsState(
        if (detected) MaterialTheme.colorScheme.primary else Color.White,
        tween(250), label = "signatureFrameColor",
    )
    val transition = rememberInfiniteTransition(label = "signatureSearch")
    val sweep by transition.animateFloat(
        0f, 1f, infiniteRepeatable(tween(1800), RepeatMode.Reverse), label = "signatureSweep",
    )
    Canvas(modifier) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        val frame = SignatureCameraFrameGeometry.guide(size.width, size.height)
        val scrim = Color.Black.copy(alpha = 0.5f)
        drawRect(scrim, size = ComposeSize(size.width, frame.top))
        drawRect(scrim, Offset(0f, frame.bottom), ComposeSize(size.width, size.height - frame.bottom))
        drawRect(scrim, Offset(0f, frame.top), ComposeSize(frame.left, frame.height()))
        drawRect(scrim, Offset(frame.right, frame.top), ComposeSize(size.width - frame.right, frame.height()))
        drawRect(
            frameColor.copy(alpha = 0.7f), Offset(frame.left, frame.top), ComposeSize(frame.width(), frame.height()),
            style = Stroke(1.dp.toPx()),
        )
        if (scanning && !detected) {
            val y = frame.top + frame.height() * sweep
            drawLine(frameColor.copy(alpha = 0.45f), Offset(frame.left, y), Offset(frame.right, y), 1.dp.toPx())
        }
        if (animatedProgress > 0f) {
            drawLine(
                frameColor, Offset(frame.left, frame.bottom),
                Offset(frame.left + frame.width() * animatedProgress, frame.bottom),
                4.dp.toPx(), StrokeCap.Round,
            )
        }
        val corner = minOf(20.dp.toPx(), frame.height() / 3f)
        val thickness = 3.dp.toPx()
        for (x in listOf(frame.left, frame.right)) {
            for (y in listOf(frame.top, frame.bottom)) {
                val horizontal = if (x == frame.left) corner else -corner
                val vertical = if (y == frame.top) corner else -corner
                drawLine(frameColor, Offset(x, y), Offset(x + horizontal, y), thickness, StrokeCap.Round)
                drawLine(frameColor, Offset(x, y), Offset(x, y + vertical), thickness, StrokeCap.Round)
            }
        }
    }
}

@androidx.annotation.OptIn(markerClass = [TransformExperimental::class])
private class SignatureCameraSession(val viewport: SignatureCameraViewport) {
    @Volatile var active = true
    @Volatile var analysisEnabled = false
    @Volatile var analysisEpoch = 0
    @Volatile var analyzeAfterMs = 0L
    @Volatile var previewTransform: OutputTransform? = null
    var autoAvailable = true
    var camera: Camera? = null
    val analysis: ImageAnalysis = ImageAnalysis.Builder()
        .setTargetRotation(viewport.rotation)
        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        .setResolutionSelector(
            ResolutionSelector.Builder().setResolutionStrategy(
                ResolutionStrategy(Size(640, 480), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER),
            ).build(),
        )
        .build()
    val preview: Preview = Preview.Builder().setTargetRotation(viewport.rotation).build()
    val capture: ImageCapture = ImageCapture.Builder()
        .setFlashMode(ImageCapture.FLASH_MODE_OFF)
        .setTargetRotation(viewport.rotation)
        // Capture promptly once stable, preserving the configured JPEG quality and resolution.
        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
        .setJpegQuality(100)
        .setResolutionSelector(
            ResolutionSelector.Builder()
                .setResolutionStrategy(
                    ResolutionStrategy(
                        Size(2560, 1920),
                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                    ),
                )
                .build(),
        )
        .build()
}

private class SignatureCaptureLifetime {
    var active = true
    var completed = false
    var pendingFile: File? = null
}

private fun PreviewView.signatureViewport(): SignatureCameraViewport? =
    if (isAttachedToWindow && width > 0 && height > 0 && display != null) {
        SignatureCameraViewport(width, height, display.rotation)
    } else null

private fun Context.hasSignatureCameraPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

private tailrec fun Context.cameraActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.cameraActivity()
    else -> null
}
