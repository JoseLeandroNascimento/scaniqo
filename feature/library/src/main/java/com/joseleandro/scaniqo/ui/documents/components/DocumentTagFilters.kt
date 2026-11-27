package com.joseleandro.scaniqo.ui.documents.components

import com.joseleandro.scaniqo.feature.library.R

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.joseleandro.scaniqo.domain.model.TagModel
import com.joseleandro.scaniqo.ui.documentCommon.components.DocumentTagSelector

@Composable
fun DocumentTagFilters(
    tags: List<TagModel>,
    selectedTagId: Long?,
    onSelect: (Long?) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp),
) {
    DocumentTagSelector(
        tags = tags,
        selectedTagId = selectedTagId,
        nullOptionLabel = stringResource(R.string.documents_all_tags),
        onSelect = onSelect,
        modifier = modifier,
        contentPadding = contentPadding,
    )
}