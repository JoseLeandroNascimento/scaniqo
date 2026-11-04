package com.joseleandro.scaniqo.ui.documentEditor.components

import com.joseleandro.scaniqo.core.resources.R as CoreResourcesR
import com.joseleandro.scaniqo.feature.editor.R

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.SecureFlagPolicy
import com.joseleandro.scaniqo.domain.model.PdfContentPosition
import com.joseleandro.scaniqo.domain.model.PdfOverlay
import com.joseleandro.scaniqo.domain.model.PdfOverlayKind
import com.joseleandro.scaniqo.ui.documentCommon.components.DocumentTransformTabs
import com.joseleandro.scaniqo.ui.theme.ScaniqoTheme
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FillMarkTransformSheet(
    draft: PdfOverlay,
    editable: Boolean,
    onUpdate: (PdfOverlay) -> Unit,
    onCancel: () -> Unit,
    onApply: () -> Unit,
    modifier: Modifier = Modifier,
    maxHeight: Dp = 420.dp,
    onHeightChange: (Dp) -> Unit = {},
) {
    val density = LocalDensity.current
    var adjusting by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    ModalBottomSheet(
        onDismissRequest = onApply,
        sheetState = sheetState,
        sheetGesturesEnabled = !adjusting,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = .12f),
        modifier = modifier.testTag("fill_transform_modal")
            .onSizeChanged { onHeightChange(with(density) { it.height.toDp() }) },
        properties = ModalBottomSheetProperties(securePolicy = SecureFlagPolicy.SecureOn),
        dragHandle = {
            Box(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 8.dp)) {
                BottomSheetDefaults.DragHandle(Modifier.align(Alignment.Center))
                IconButton(
                    onClick = { scope.launch { sheetState.hide(); onApply() } },
                    modifier = Modifier.align(Alignment.CenterEnd).testTag("fill_transform_close"),
                ) { Icon(painterResource(CoreResourcesR.drawable.ic_toast_close), stringResource(R.string.tool_sheet_close)) }
            }
        },
    ) {
        FillMarkTransformContent(
            draft, editable, onUpdate, onCancel, onApply,
            maxHeight = maxHeight, onGestureActiveChange = { adjusting = it },
        )
    }
}

@Composable
internal fun FillMarkTransformContent(
    draft: PdfOverlay,
    editable: Boolean,
    onUpdate: (PdfOverlay) -> Unit,
    onCancel: () -> Unit,
    onApply: () -> Unit,
    modifier: Modifier = Modifier,
    maxHeight: Dp = 420.dp,
    onGestureActiveChange: (Boolean) -> Unit = {},
) {
    var tab by rememberSaveable(draft.id) { mutableIntStateOf(0) }
    val compact = maxHeight < 300.dp
    val scrollState = key(draft.id, tab) { rememberScrollState() }
    Column(modifier.fillMaxWidth().height(maxHeight).testTag("fill_transform_sheet")) {
        DocumentTransformTabs(
            selectedTabIndex = tab, enabled = editable, onTabSelected = { tab = it },
            compact = compact, testTagPrefix = "fill_transform",
        )
        Column(
            Modifier.fillMaxWidth().weight(1f).testTag("fill_transform_body")
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp, vertical = if (compact) 8.dp else 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (tab) {
                0 -> {
                    val mapping = fillMarkPositionMapping(draft)
                    DocumentPositionPad(
                        position = mapping.position(),
                        enabled = editable,
                        horizontalEnabled = mapping.horizontalTravel > .0001f,
                        verticalEnabled = mapping.verticalTravel > .0001f,
                        onPositionChange = { onUpdate(mapping.place(it)) },
                        onGestureStart = { onGestureActiveChange(true) },
                        onGestureEnd = { onGestureActiveChange(false) },
                        compact = compact,
                        itemSize = DpSize(72.dp, 44.dp),
                        description = stringResource(R.string.fill_v3_position_hint),
                        testTagPrefix = "fill",
                        itemContent = { itemModifier ->
                            Surface(itemModifier, color = MaterialTheme.colorScheme.surfaceContainerLowest,
                                shape = RoundedCornerShape(7.dp)) {
                                Box(Modifier.padding(6.dp).rotate(draft.rotationDegrees), contentAlignment = Alignment.Center) {
                                    if (draft.kind in textKinds) {
                                        Text(
                                            draft.text.ifBlank { stringResource(R.string.fill_v4_tool_text) },
                                            color = Color(draft.color).copy(alpha = draft.opacity),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = if (draft.bold) FontWeight.Bold else FontWeight.Normal,
                                            fontStyle = if (draft.italic) FontStyle.Italic else FontStyle.Normal,
                                            maxLines = 2,
                                        )
                                    } else {
                                        Icon(
                                            painterResource(if (draft.kind == PdfOverlayKind.CHECKMARK)
                                                CoreResourcesR.drawable.ic_check_24 else R.drawable.ic_fill_crossmark),
                                            null, Modifier.size(24.dp), tint = Color(draft.color).copy(alpha = draft.opacity),
                                        )
                                    }
                                }
                            }
                        },
                    )
                    TextButton(
                        onClick = { onUpdate(mapping.place(PdfContentPosition())) }, enabled = editable,
                        modifier = Modifier.testTag("fill_position_center"),
                    ) { Text(stringResource(R.string.tools_align_center)) }
                }
                1 -> {
                    Text(
                        if (draft.kind in textKinds) stringResource(
                            R.string.fill_v3_size_value, formatFillMarkSize(draft.fontSize, LocalConfiguration.current.locales[0]),
                        ) else stringResource(R.string.fill_mark_opacity_value, (draft.width * 100).roundToInt()),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    FillMarkSizeSlider(
                        draft, editable, onUpdate, proportionalText = true,
                        onGestureActiveChange = onGestureActiveChange,
                    )
                }
                2 -> RotationDial(
                    rotationDegrees = draft.rotationDegrees,
                    enabled = editable,
                    onRotationChange = { onUpdate(draft.rotatedFillMark(it)) },
                    onGestureStart = { onGestureActiveChange(true) },
                    onGestureEnd = { onGestureActiveChange(false) },
                    description = stringResource(R.string.layout_interactive_rotate),
                    leftLabel = stringResource(R.string.signature_rotate_left),
                    rightLabel = stringResource(R.string.signature_rotate_right),
                    compact = compact, testTagPrefix = "fill",
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().testTag("fill_transform_footer")
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TextButton(onClick = onCancel, enabled = editable, modifier = Modifier.heightIn(min = 48.dp).testTag("fill_transform_cancel"),
                contentPadding = PaddingValues(horizontal = 8.dp)) {
                Text(stringResource(R.string.tools_cancel))
            }
            Button(onClick = onApply, enabled = editable, modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("fill_transform_apply"),
                contentPadding = PaddingValues(horizontal = 12.dp)) {
                Text(stringResource(R.string.fill_mark_apply))
            }
        }
    }
}

@Preview(name = "Mover elemento", widthDp = 390, heightDp = 360, locale = "pt-rBR")
@Preview(name = "Mover elemento compacto", widthDp = 320, heightDp = 280, fontScale = 1.3f, locale = "pt-rBR")
@Preview(name = "Mover elemento escuro", widthDp = 390, heightDp = 360, locale = "pt-rBR", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun FillMarkTransformPreview() {
    ScaniqoTheme {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            BoxWithConstraints {
                FillMarkTransformContent(
                    PdfOverlay("preview", PdfOverlayKind.TEXT, text = "Maria Silva"), true, {}, {}, {},
                    maxHeight = maxHeight,
                )
            }
        }
    }
}
