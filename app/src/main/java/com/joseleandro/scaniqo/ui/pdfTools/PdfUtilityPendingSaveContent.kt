package com.joseleandro.scaniqo.ui.pdfTools

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joseleandro.scaniqo.R
import com.joseleandro.scaniqo.domain.model.PdfUtility
import com.joseleandro.scaniqo.ui.theme.ScaniqoTheme

/** Compact result shown behind the shared name-and-tag sheet. It never claims the PDF is saved. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PdfUtilityPendingSaveContent(
    tool: PdfUtility,
    sourceName: String?,
    compressionResult: PdfCompressionSummary?,
) {
    val title = if (tool == PdfUtility.COMPRESS_PDF) {
        R.string.utility_workspace_compress_title
    } else {
        R.string.utility_extract_title
    }
    val context = LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(title)) },
                navigationIcon = {
                    IconButton(onClick = {}, enabled = false) {
                        Icon(painterResource(R.drawable.arrow_back_24px), stringResource(R.string.utility_back))
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).padding(24.dp), contentAlignment = Alignment.Center) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.extraLarge,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_check_24),
                            contentDescription = null,
                            modifier = Modifier.padding(22.dp).size(42.dp),
                        )
                    }
                    Text(
                        stringResource(R.string.utility_output_ready_to_save),
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center,
                    )
                    sourceName?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                        )
                    }
                    compressionResult?.let { result ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(20.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            SizeLabel(
                                label = stringResource(R.string.compression_size_before),
                                size = Formatter.formatShortFileSize(context, result.originalBytes),
                                modifier = Modifier.weight(1f),
                            )
                            SizeLabel(
                                label = stringResource(R.string.compression_size_after),
                                size = Formatter.formatShortFileSize(context, result.resultBytes),
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    Text(
                        stringResource(R.string.utility_output_finish_in_sheet),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun SizeLabel(label: String, size: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(size, style = MaterialTheme.typography.titleMedium)
    }
}

@Preview(name = "Pronto para salvar", widthDp = 390, locale = "pt-rBR")
@Preview(name = "Pronto para salvar — fonte grande", widthDp = 320, fontScale = 1.5f, locale = "pt-rBR")
@Composable
private fun PendingSavePreview() {
    ScaniqoTheme {
        PdfUtilityPendingSaveContent(
            tool = PdfUtility.COMPRESS_PDF,
            sourceName = "Contrato de prestação de serviços",
            compressionResult = PdfCompressionSummary(1_250_000, 780_000, null),
        )
    }
}
