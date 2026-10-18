package com.joseleandro.scaniqo.ui.documentEditor.components

import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.joseleandro.scaniqo.R
import com.joseleandro.scaniqo.domain.model.PdfSignaturePlacement
import com.joseleandro.scaniqo.ui.documentEditor.event.DocumentToolsEvent
import com.joseleandro.scaniqo.ui.signatures.SignatureImage
import kotlin.math.roundToInt

/** A assinatura em edição fica fora do PDF de prévia e é desenhada uma única vez aqui. */
@Composable
internal fun DocumentSignatureOverlay(
    placement: PdfSignaturePlacement,
    onEvent: (DocumentToolsEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val current by rememberUpdatedState(placement)
    val description = stringResource(R.string.tools_signature_selected)
    val left = stringResource(R.string.tools_signature_left)
    val right = stringResource(R.string.tools_signature_right)
    val up = stringResource(R.string.tools_signature_up)
    val down = stringResource(R.string.tools_signature_down)
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val pageWidth = constraints.maxWidth.toFloat()
        val pageHeight = constraints.maxHeight.toFloat()
        Box(
            modifier = Modifier
                .offset { IntOffset((placement.x * pageWidth).roundToInt(), (placement.y * pageHeight).roundToInt()) }
                .size(maxWidth * placement.width, maxHeight * placement.height)
                .border(1.dp, MaterialTheme.colorScheme.primary)
                .pointerInput(placement.id, pageWidth, pageHeight) {
                    detectDragGestures { change, delta ->
                        change.consume()
                        if (pageWidth > 0f && pageHeight > 0f) {
                            onEvent(
                                DocumentToolsEvent.MoveSignature(
                                    current.x + delta.x / pageWidth,
                                    current.y + delta.y / pageHeight,
                                ),
                            )
                        }
                    }
                }
                .semantics(mergeDescendants = true) {
                    contentDescription = description
                    customActions = listOf(
                        CustomAccessibilityAction(left) { onEvent(DocumentToolsEvent.MoveSignature(current.x - 0.02f, current.y)); true },
                        CustomAccessibilityAction(right) { onEvent(DocumentToolsEvent.MoveSignature(current.x + 0.02f, current.y)); true },
                        CustomAccessibilityAction(up) { onEvent(DocumentToolsEvent.MoveSignature(current.x, current.y - 0.02f)); true },
                        CustomAccessibilityAction(down) { onEvent(DocumentToolsEvent.MoveSignature(current.x, current.y + 0.02f)); true },
                    )
                },
        ) {
            SignatureImage(placement.imageUri, Modifier.fillMaxSize())
        }
    }
}
