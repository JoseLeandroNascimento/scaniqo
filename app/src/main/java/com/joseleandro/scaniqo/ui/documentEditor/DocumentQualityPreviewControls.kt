package com.joseleandro.scaniqo.ui.documentEditor

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.joseleandro.scaniqo.R

/** A escolha acompanha a imagem exibida; o carregamento não antecipa o rótulo do resultado. */
@Composable
internal fun DocumentQualityPreviewControls(
    state: DocumentToolsUiState,
    onEvent: (DocumentToolsEvent) -> Unit,
    enabled: Boolean,
    showingOriginal: Boolean,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(R.string.viewer_compare_description, state.selectedPageIndex + 1)
    SingleChoiceSegmentedButtonRow(
        modifier = modifier.widthIn(min = 156.dp).heightIn(min = 48.dp)
            .semantics { contentDescription = description },
    ) {
        listOf(true, false).forEachIndexed { index, original ->
            val actionDescription = stringResource(
                if (original) R.string.viewer_compare_before_description else R.string.viewer_compare_after_description,
            )
            SegmentedButton(
                selected = showingOriginal == original,
                onClick = { onEvent(DocumentToolsEvent.SetCompareOriginal(original)) },
                enabled = enabled && state.canEdit && !state.isRendering,
                shape = SegmentedButtonDefaults.itemShape(index, 2),
                icon = {},
                modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = actionDescription },
            ) {
                Text(
                    stringResource(if (original) R.string.viewer_compare_before else R.string.viewer_compare_after),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                )
            }
        }
    }
}
