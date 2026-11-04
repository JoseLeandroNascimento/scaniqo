package com.joseleandro.scaniqo.ui.documentSignature

import com.joseleandro.scaniqo.core.resources.R as CoreResourcesR
import com.joseleandro.scaniqo.feature.editor.R

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.joseleandro.scaniqo.domain.model.PdfSignaturePlacement
import com.joseleandro.scaniqo.ui.documentEditor.event.DocumentToolsEvent
import com.joseleandro.scaniqo.ui.theme.ScaniqoTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DocumentSignatureControls(
    draft: PdfSignaturePlacement,
    mode: SignatureAdjustmentMode,
    enabled: Boolean,
    applying: Boolean,
    adjustmentActive: Boolean,
    maxHeight: Dp,
    onModeChange: (SignatureAdjustmentMode) -> Unit,
    onEvent: (DocumentToolsEvent) -> Unit,
    onDismiss: () -> Unit,
    onCancel: () -> Unit,
    onApply: () -> Unit,
    onHeightChange: (Dp) -> Unit,
    modifier: Modifier = Modifier,
    onAdjustmentActiveChange: (Boolean) -> Unit = {},
) {
    val density = LocalDensity.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        sheetGesturesEnabled = !adjustmentActive,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = .12f),
        modifier = modifier.testTag("signature_adjustment_modal")
            .onSizeChanged { onHeightChange(with(density) { it.height.toDp() }) },
        dragHandle = {
            Box(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 8.dp)) {
                BottomSheetDefaults.DragHandle(Modifier.align(Alignment.Center))
                IconButton(
                    onClick = { scope.launch { sheetState.hide(); onDismiss() } },
                    modifier = Modifier.align(Alignment.CenterEnd).testTag("signature_close_adjustments"),
                ) {
                    Icon(painterResource(CoreResourcesR.drawable.ic_toast_close), stringResource(R.string.tool_sheet_close))
                }
            }
        },
    ) {
        SignatureControlsContent(
            draft, mode, enabled, applying, maxHeight, onModeChange, onEvent,
            onCancel, onApply, onAdjustmentActiveChange = onAdjustmentActiveChange,
        )
    }
}

@Composable
private fun SignatureControlsContent(
    draft: PdfSignaturePlacement,
    mode: SignatureAdjustmentMode,
    enabled: Boolean,
    applying: Boolean,
    maxHeight: Dp,
    onModeChange: (SignatureAdjustmentMode) -> Unit,
    onEvent: (DocumentToolsEvent) -> Unit,
    onCancel: () -> Unit,
    onApply: () -> Unit,
    modifier: Modifier = Modifier,
    onAdjustmentActiveChange: (Boolean) -> Unit = {},
) {
    val compact = maxHeight < 380.dp
    val scrollState = rememberScrollState()
    LaunchedEffect(mode) { scrollState.scrollTo(0) }
    Column(
        modifier.fillMaxWidth().height(maxHeight).testTag("signature_adjustment_sheet"),
    ) {
        SignatureAdjustmentTabs(mode, enabled, onModeChange, compact = compact)
        SignatureAdjustmentContent(
            draft = draft,
            mode = mode,
            enabled = enabled,
            onEvent = onEvent,
            modifier = Modifier.weight(1f).fillMaxWidth()
                .testTag("signature_adjustment_body").verticalScroll(scrollState),
            compact = compact,
            onAdjustmentActiveChange = onAdjustmentActiveChange,
        )
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                .padding(top = 4.dp, bottom = if (compact) 8.dp else 16.dp)
                .testTag("signature_adjustment_footer"),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = onCancel,
                enabled = enabled,
                modifier = Modifier.heightIn(min = 48.dp).testTag("signature_cancel_edit"),
            ) { Text(stringResource(CoreResourcesR.string.signature_cancel), maxLines = 1) }
            Button(
                onClick = onApply,
                enabled = enabled && !applying,
                modifier = Modifier.weight(2f).heightIn(min = 48.dp).testTag("signature_apply_adjustments"),
            ) { Text(stringResource(R.string.signature_apply_adjustments)) }
        }
    }
}

@Preview(name = "Ajustes de assinatura", widthDp = 390, locale = "pt-rBR")
@Preview(name = "Painel compacto", widthDp = 320, fontScale = 1.3f, locale = "pt-rBR")
@Preview(name = "Painel escuro", widthDp = 390, uiMode = Configuration.UI_MODE_NIGHT_YES, locale = "pt-rBR")
@Composable
private fun SignatureControlsContentPreview() {
    ScaniqoTheme {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            SignatureControlsContent(
                draft = PdfSignaturePlacement("preview", "preview"),
                mode = SignatureAdjustmentMode.MOVE,
                enabled = true,
                applying = false,
                maxHeight = 340.dp,
                onModeChange = {},
                onEvent = {},
                onCancel = {},
                onApply = {},
            )
        }
    }
}
