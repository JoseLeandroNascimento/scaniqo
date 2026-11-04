package com.joseleandro.scaniqo.ui.documentEditor.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joseleandro.scaniqo.domain.model.PdfOverlayKind
import com.joseleandro.scaniqo.ui.theme.ScaniqoTheme

/** O check padrão em uma página de 300 dp, com dois níveis de ampliação do editor. */
@Preview(name = "Preencher — check em três tamanhos", widthDp = 320, showBackground = true)
@Composable
private fun DocumentCheckmarkPreview() {
    val checkmark = remember { newOverlay(PdfOverlayKind.CHECKMARK) }
    ScaniqoTheme {
        Column(
            Modifier.background(Color.White).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "Preencher sobre a página",
                style = MaterialTheme.typography.titleSmall,
                color = Color(0xFF202124),
            )
            Row(Modifier.fillMaxWidth()) {
                listOf("Página" to 1f, "Zoom 2×" to 2f, "Zoom 3×" to 3f).forEach { (label, zoom) ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Canvas(Modifier.size(80.dp, 88.dp)) {
                            val pageWidth = 300.dp.toPx() * zoom
                            val pageHeight = pageWidth * 842f / 595f
                            val width = checkmark.width * pageWidth / size.width
                            val height = checkmark.height * pageHeight / size.height
                            drawOverlayDraft(
                                checkmark.copy(
                                    x = (1f - width) / 2f,
                                    y = (1f - height) / 2f,
                                    width = width,
                                    height = height,
                                ),
                                pageNumber = 1,
                                count = 1,
                            )
                        }
                        Text(label, style = MaterialTheme.typography.labelSmall, color = Color(0xFF202124))
                    }
                }
            }
        }
    }
}
