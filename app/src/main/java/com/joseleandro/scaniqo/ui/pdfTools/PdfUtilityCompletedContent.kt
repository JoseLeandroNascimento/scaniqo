package com.joseleandro.scaniqo.ui.pdfTools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.joseleandro.scaniqo.R
import com.joseleandro.scaniqo.domain.model.PdfUtility

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PdfUtilityCompletedContent(
    tool: PdfUtility,
    onBack: () -> Unit,
    onOpenDocument: (() -> Unit)?,
) {
    val title = when (tool) {
        PdfUtility.EXTRACT_PAGES -> R.string.utility_extract_title
        PdfUtility.COMPRESS_PDF -> R.string.utility_workspace_compress_title
        else -> R.string.utility_images_export_title
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.arrow_back_24px), stringResource(R.string.utility_back))
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).padding(24.dp), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ) {
                    Icon(
                        painterResource(R.drawable.ic_check_24),
                        contentDescription = null,
                        modifier = Modifier.padding(26.dp).size(52.dp),
                    )
                }
                Text(stringResource(if (tool == PdfUtility.PDF_TO_IMAGES) R.string.runtime_zip_exported else R.string.document_saved),
                    style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.size(4.dp))
                onOpenDocument?.let { open ->
                    Button(
                        onClick = open,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    ) {
                        Text(stringResource(R.string.runtime_open_document))
                    }
                }
                TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(stringResource(if (tool == PdfUtility.PDF_TO_IMAGES)
                        R.string.utility_export_action else R.string.utility_change_pdf))
                }
            }
        }
    }
}
