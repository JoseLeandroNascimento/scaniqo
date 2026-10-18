package com.joseleandro.scaniqo.ui.documentSignature

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.joseleandro.scaniqo.R
import kotlin.math.roundToInt

@Composable
internal fun DocumentSignatureZoomControls(
    zoom: Float,
    enabled: Boolean,
    onZoomOut: () -> Unit,
    onReset: () -> Unit,
    onZoomIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val zoomOut = stringResource(R.string.signature_document_zoom_out)
    val zoomIn = stringResource(R.string.signature_document_zoom_in)
    val reset = stringResource(R.string.signature_document_zoom_reset)
    Surface(modifier, color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            Modifier.fillMaxWidth().height(48.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onZoomOut, enabled = enabled && zoom > 1f,
                modifier = Modifier.size(48.dp).semantics { contentDescription = zoomOut }) {
                Text("−", style = MaterialTheme.typography.headlineSmall)
            }
            TextButton(
                onClick = onReset,
                enabled = enabled,
                contentPadding = PaddingValues(horizontal = 8.dp),
                modifier = Modifier.height(48.dp).semantics { contentDescription = reset },
            ) {
                Text(stringResource(R.string.signature_document_zoom_value, (zoom * 100).roundToInt()), maxLines = 1)
            }
            IconButton(onClick = onZoomIn, enabled = enabled && zoom < 3f,
                modifier = Modifier.size(48.dp).semantics { contentDescription = zoomIn }) {
                Text("+", style = MaterialTheme.typography.headlineSmall)
            }
        }
    }
}
