package com.joseleandro.scaniqo.ui.documentCommon.components

import com.joseleandro.scaniqo.core.documentui.R
import com.joseleandro.scaniqo.core.resources.R as CoreResourcesR

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
@Composable
fun DocumentTransformTabs(
    selectedTabIndex: Int,
    enabled: Boolean,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    testTagPrefix: String = "document_transform",
) {
    val tabs = listOf(
        Triple("MOVE", CoreResourcesR.drawable.ic_layout_position, R.string.signature_adjust_move),
        Triple("SIZE", CoreResourcesR.drawable.ic_layout_size, R.string.signature_adjust_size),
        Triple("ROTATE", CoreResourcesR.drawable.ic_layout_rotate_right, CoreResourcesR.string.signature_adjust_rotate),
    )
    PrimaryTabRow(
        selectedTabIndex = selectedTabIndex,
        modifier = modifier.fillMaxWidth().testTag("${testTagPrefix}_tabs"),
        containerColor = Color.Transparent,
        divider = {},
    ) {
        tabs.forEachIndexed { index, (key, icon, label) ->
            Tab(
                selected = selectedTabIndex == index,
                onClick = { onTabSelected(index) },
                enabled = enabled,
                modifier = Modifier.testTag("${testTagPrefix}_$key"),
            ) {
                val iconContent: @Composable () -> Unit = {
                    Icon(painterResource(icon), null, Modifier.size(20.dp))
                }
                val labelContent: @Composable () -> Unit = {
                    Text(
                        stringResource(label),
                        style = MaterialTheme.typography.labelLarge,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = 14.sp),
                    )
                }
                if (compact) {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 8.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        iconContent()
                        labelContent()
                    }
                } else {
                    Column(
                        Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(horizontal = 4.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
                    ) {
                        iconContent()
                        labelContent()
                    }
                }
            }
        }
    }
}
