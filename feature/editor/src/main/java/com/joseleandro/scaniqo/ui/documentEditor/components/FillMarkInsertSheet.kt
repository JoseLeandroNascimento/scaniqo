package com.joseleandro.scaniqo.ui.documentEditor.components

import com.joseleandro.scaniqo.core.resources.R as CoreResourcesR
import com.joseleandro.scaniqo.feature.editor.R

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FillMarkInsertSheet(
    enabled: Boolean,
    hasElements: Boolean,
    showSavedValues: Boolean,
    onText: () -> Unit,
    onMark: () -> Unit,
    onSavedValues: () -> Unit,
    onElements: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = modifier.testTag("fill_insert_sheet"),
    ) {
        Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            Text(
                stringResource(R.string.fill_context_insert),
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                style = MaterialTheme.typography.titleLarge,
            )
            FillInsertAction(R.string.fill_v4_add_text, CoreResourcesR.drawable.ic_fill_text, enabled, onText, "fill_insert_text")
            FillInsertAction(R.string.fill_v4_add_mark, R.drawable.ic_fill_mark, enabled, onMark, "fill_insert_mark")
            if (showSavedValues) FillInsertAction(
                R.string.overlay_saved_values, CoreResourcesR.drawable.ic_tag_person, enabled, onSavedValues, "fill_insert_saved",
            )
            if (hasElements) FillInsertAction(
                R.string.overlay_elements, CoreResourcesR.drawable.ic_symbol_layers, enabled, onElements, "fill_select_element",
            )
        }
    }
}

@Composable
private fun FillInsertAction(
    label: Int,
    icon: Int,
    enabled: Boolean,
    onClick: () -> Unit,
    tag: String,
) {
    ListItem(
        headlineContent = { Text(stringResource(label)) },
        leadingContent = { Icon(painterResource(icon), null) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .testTag(tag).clickable(enabled = enabled, role = Role.Button, onClick = onClick),
    )
}
