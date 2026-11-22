package com.joseleandro.scaniqo.ui.documentCommon.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joseleandro.scaniqo.core.documentui.R
import com.joseleandro.scaniqo.ui.components.EmptyStateIllustration
import com.joseleandro.scaniqo.ui.components.ScaniqoEmptyState
import com.joseleandro.scaniqo.ui.theme.ScaniqoTheme

@Composable
internal fun DocumentMergeEmptySelection(
    enabled: Boolean,
    onPickFiles: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ScaniqoEmptyState(
        title = stringResource(R.string.merge_selection_empty_title),
        description = stringResource(R.string.merge_selection_empty),
        illustration = EmptyStateIllustration.MERGE,
        modifier = modifier.fillMaxWidth().padding(top = 8.dp),
        actionLabel = stringResource(R.string.merge_screen_add_files),
        onAction = onPickFiles,
        actionEnabled = enabled,
    )
}

@Preview(name = "Mesclagem vazia", widthDp = 390, locale = "pt-rBR", showBackground = true)
@Preview(name = "Mesclagem vazia escura", widthDp = 390, locale = "pt-rBR",
    uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Preview(name = "Mesclagem vazia texto ampliado", widthDp = 320, locale = "pt-rBR",
    fontScale = 1.5f, showBackground = true)
@Composable
private fun DocumentMergeEmptySelectionPreview() {
    ScaniqoTheme {
        Surface { DocumentMergeEmptySelection(enabled = true, onPickFiles = {}) }
    }
}
