package com.joseleandro.scaniqo.ui.documentSignature

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.joseleandro.scaniqo.R
import com.joseleandro.scaniqo.domain.model.PdfPageSpec
import com.joseleandro.scaniqo.domain.model.PdfPoint
import com.joseleandro.scaniqo.domain.model.PdfSignaturePlacement
import com.joseleandro.scaniqo.domain.model.PdfWorkspace
import com.joseleandro.scaniqo.ui.documentCommon.components.DocumentPdfPage
import com.joseleandro.scaniqo.ui.documentCommon.components.detectDocumentTransformGestures
import com.joseleandro.scaniqo.ui.documentEditor.components.DocumentSignatureGesture
import com.joseleandro.scaniqo.ui.documentEditor.components.DocumentSignatureOverlay
import com.joseleandro.scaniqo.ui.documentEditor.event.DocumentToolsEvent
import com.joseleandro.scaniqo.ui.documentEditor.state.DocumentToolsUiState
import com.joseleandro.scaniqo.ui.theme.ScaniqoTheme
import kotlin.math.roundToInt

@Composable
internal fun DocumentSignaturePage(
    state: DocumentToolsUiState,
    workspace: PdfWorkspace,
    page: PdfPageSpec,
    draft: PdfSignaturePlacement?,
    adjustmentMode: SignatureAdjustmentMode,
    onEvent: (DocumentToolsEvent) -> Unit,
    modifier: Modifier = Modifier,
    adjustmentActive: Boolean = false,
) {
    var documentImage by remember(workspace.workspaceId, page.id, draft?.id) { mutableStateOf<ImageBitmap?>(null) }
    var gesture by remember(workspace.workspaceId, page.id, draft?.id, state.canEdit, adjustmentMode) {
        mutableStateOf<DocumentSignatureGesture?>(null)
    }
    val viewport = rememberSaveable(workspace.workspaceId, page.id, saver = DocumentSignatureViewportState.Saver) {
        DocumentSignatureViewportState()
    }
    val previousPageLabel = stringResource(R.string.signature_previous_page)
    val nextPageLabel = stringResource(R.string.signature_next_page)
    val preview = state.pagePreviews[page.id]
    val showPreview = !state.isLocked && preview?.preview != null && !(draft != null && preview.isRendering)
    LaunchedEffect(showPreview) {
        if (!showPreview) {
            documentImage = null
            gesture = null
        }
    }
    Column(modifier) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(
                enabled = state.canEdit && draft == null && state.selectedPageIndex > 0,
                onClick = { onEvent(DocumentToolsEvent.SelectPage(workspace.pages[state.selectedPageIndex - 1].id)) },
                modifier = Modifier.semantics { contentDescription = previousPageLabel },
            ) { Text(if (androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl) "›" else "‹", style = MaterialTheme.typography.headlineSmall) }
            Text(stringResource(R.string.signature_page, state.selectedPageIndex + 1, workspace.pages.size))
            TextButton(
                enabled = state.canEdit && draft == null && state.selectedPageIndex < workspace.pages.lastIndex,
                onClick = { onEvent(DocumentToolsEvent.SelectPage(workspace.pages[state.selectedPageIndex + 1].id)) },
                modifier = Modifier.semantics { contentDescription = nextPageLabel },
            ) { Text(if (androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl) "‹" else "›", style = MaterialTheme.typography.headlineSmall) }
        }
        DocumentSignatureViewportStage(
            viewport = viewport,
            draft = draft.takeIf { showPreview },
            enabled = state.canEdit && showPreview,
            onEvent = onEvent,
            gesture = gesture,
            onGestureChange = { gesture = it },
            documentImage = documentImage,
            adjustmentActive = adjustmentActive,
            isContentReady = showPreview,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            when {
                showPreview -> DocumentPdfPage(
                    pdfUri = requireNotNull(preview?.preview).uri,
                    pageIndex = preview.preview.pageIndex,
                    modifier = Modifier.fillMaxSize(),
                    contentKey = "${workspace.workspaceId}:${page.id}:${draft?.id}",
                    pageNumber = state.selectedPageIndex + 1,
                    onImageBitmapChanged = { documentImage = it },
                )
                preview?.error == true -> TextButton(
                    onClick = { onEvent(DocumentToolsEvent.RetryPagePreview(page.id)) },
                    modifier = Modifier.align(Alignment.Center),
                ) { Text(stringResource(R.string.signature_retry)) }
                else -> CircularProgressIndicator(Modifier.align(Alignment.Center).size(28.dp))
            }
        }
    }
}

/** A folha é uma camada estável; zoom e pan só mudam sua transformação de desenho. */
@Composable
private fun DocumentSignatureViewportStage(
    viewport: DocumentSignatureViewportState,
    draft: PdfSignaturePlacement?,
    enabled: Boolean,
    onEvent: (DocumentToolsEvent) -> Unit,
    modifier: Modifier = Modifier,
    gesture: DocumentSignatureGesture? = null,
    onGestureChange: (DocumentSignatureGesture?) -> Unit = {},
    documentImage: ImageBitmap? = null,
    adjustmentActive: Boolean = false,
    isContentReady: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    var signatureImage by remember(draft?.id, draft?.imageUri) { mutableStateOf<ImageBitmap?>(null) }
    val latestDraft by rememberUpdatedState(draft)
    val latestEvent by rememberUpdatedState(onEvent)
    val latestGestureChange by rememberUpdatedState(onGestureChange)
    val density = LocalDensity.current
    val documentLabel = stringResource(R.string.signature_document_zoom)
    val panLeft = stringResource(R.string.signature_document_pan_left)
    val panRight = stringResource(R.string.signature_document_pan_right)
    val panUp = stringResource(R.string.signature_document_pan_up)
    val panDown = stringResource(R.string.signature_document_pan_down)
    BoxWithConstraints(modifier) {
        val canvasWidth = constraints.maxWidth.toFloat()
        val canvasHeight = (constraints.maxHeight - with(density) { 48.dp.roundToPx() }).coerceAtLeast(1).toFloat()
        val margin = with(density) { 12.dp.toPx() }
        val bounds = signatureDocumentViewportBounds(viewport.value, canvasWidth, canvasHeight, margin)
        val zoomValue = stringResource(R.string.signature_document_zoom_value, (bounds.viewport.zoom * 100).roundToInt())
        fun pan(delta: Offset) {
            onGestureChange(null)
            viewport.pan(delta.x, delta.y, canvasWidth, canvasHeight, margin)
        }
        fun zoom(value: Float) {
            onGestureChange(null)
            viewport.zoomTo(
                value, canvasWidth, canvasHeight, margin,
                draft?.let { PdfPoint(it.x + it.width / 2f, it.y + it.height / 2f) },
            )
        }
        Column(Modifier.fillMaxSize()) {
            Box(
                Modifier.weight(1f).fillMaxWidth()
                    .clipToBounds()
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .semantics {
                        contentDescription = documentLabel
                        stateDescription = zoomValue
                        if (enabled && bounds.viewport.zoom > 1f) customActions = listOf(
                            CustomAccessibilityAction(panLeft) { pan(Offset(-canvasWidth * .2f, 0f)); true },
                            CustomAccessibilityAction(panRight) { pan(Offset(canvasWidth * .2f, 0f)); true },
                            CustomAccessibilityAction(panUp) { pan(Offset(0f, -canvasHeight * .2f)); true },
                            CustomAccessibilityAction(panDown) { pan(Offset(0f, canvasHeight * .2f)); true },
                        )
                    }
                    .pointerInput(viewport, enabled, draft == null, canvasWidth, canvasHeight) {
                        var gestureBaseline: PdfSignaturePlacement? = null
                        if (enabled) detectDocumentTransformGestures(
                            allowSinglePan = { latestDraft == null && viewport.value.zoom > 1f },
                            onGestureStart = { gestureBaseline = latestDraft },
                            onTransformStart = {
                                val baseline = gestureBaseline
                                if (baseline != null && latestDraft?.id == baseline.id) {
                                    latestEvent(DocumentToolsEvent.TransformSignature(
                                        baseline.x, baseline.y, baseline.width, baseline.rotationDegrees,
                                        baseline.height, baseline.stretchToBounds,
                                    ))
                                }
                                latestGestureChange(null)
                            },
                            onTransform = { centroid, pan, factor ->
                                viewport.transform(centroid, pan, factor, canvasWidth, canvasHeight, margin)
                            },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (isContentReady) {
                    Box(
                        Modifier.width(with(density) { bounds.basePage.width.toDp() }).aspectRatio(595f / 842f)
                            .graphicsLayer {
                                scaleX = bounds.viewport.zoom
                                scaleY = bounds.viewport.zoom
                                translationX = bounds.page.center.x - bounds.basePage.center.x
                                translationY = bounds.page.center.y - bounds.basePage.center.y
                            },
                    ) {
                        Surface(Modifier.fillMaxSize(), color = Color.White, shadowElevation = 3.dp) {
                            Box(contentAlignment = Alignment.Center, content = content)
                        }
                    }
                } else {
                    // Erro e progresso permanecem visíveis mesmo quando a folha está ampliada e deslocada.
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center, content = content)
                }
                if (draft != null) {
                    DocumentSignatureOverlay(
                        placement = draft,
                        onEvent = onEvent,
                        enabled = enabled,
                        pageBounds = bounds.page,
                        onGestureChange = onGestureChange,
                        onImageBitmapChanged = { signatureImage = it },
                        onViewportPan = ::pan,
                        viewportGestureKey = bounds.gestureKey,
                    )
                    val active = gesture?.placement ?: draft
                    val signatureBitmap = signatureImage
                    if (enabled && (gesture != null || adjustmentActive) &&
                        documentImage != null && signatureBitmap != null
                    ) {
                        DocumentSignatureMagnifier(
                            document = documentImage,
                            signature = signatureBitmap,
                            placement = active,
                            pointer = gesture?.pointer ?: PdfPoint(active.x + active.width / 2f, active.y + active.height / 2f),
                            pageBounds = bounds.page,
                            viewportSize = IntSize(canvasWidth.roundToInt(), canvasHeight.roundToInt()),
                            modifier = Modifier.align(androidx.compose.ui.AbsoluteAlignment.TopLeft),
                        )
                    }
                }
            }
            DocumentSignatureZoomControls(
                zoom = bounds.viewport.zoom,
                enabled = enabled,
                onZoomOut = { zoom(viewport.value.zoom - .25f) },
                onReset = { onGestureChange(null); viewport.reset() },
                onZoomIn = { zoom(viewport.value.zoom + .25f) },
            )
        }
    }
}

@Preview(name = "Assinatura · zoom 200%", widthDp = 390, heightDp = 550, showBackground = true)
@Preview(name = "Assinatura · zoom paisagem", widthDp = 640, heightDp = 320, fontScale = 1.3f)
@Composable
private fun DocumentSignatureZoomPreview() {
    val viewport = remember { DocumentSignatureViewportState(SignatureDocumentViewport(2f, 0f, -.2f)) }
    ScaniqoTheme {
        DocumentSignatureViewportStage(
            viewport = viewport,
            draft = PdfSignaturePlacement(
                id = "zoom-preview", imageUri = "preview", x = .28f, y = .5f,
                width = .45f, height = .09f, rotationDegrees = -8f,
            ),
            enabled = true,
            onEvent = {},
            modifier = Modifier.fillMaxSize(),
        ) {
            DocumentPdfPage("preview", 0, Modifier.fillMaxSize())
        }
    }
}
