package com.joseleandro.scaniqo.ui.documents.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.joseleandro.scaniqo.R
import com.joseleandro.scaniqo.domain.model.TagModel
import com.joseleandro.scaniqo.ui.documentCommon.components.DocumentTagSelector

@Composable
fun DocumentTagFilters(
    tags: List<TagModel>,
    selectedTagId: Long?,
    onSelect: (Long?) -> Unit,
) {
    DocumentTagSelector(
        tags = tags,
        selectedTagId = selectedTagId,
        nullOptionLabel = stringResource(R.string.documents_all_tags),
        onSelect = onSelect,
    )
}