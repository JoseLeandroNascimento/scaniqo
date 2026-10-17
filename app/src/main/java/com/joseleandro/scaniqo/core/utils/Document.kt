package com.joseleandro.scaniqo.core.utils

import android.text.format.DateFormat
import android.text.format.Formatter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.joseleandro.scaniqo.domain.model.DocumentModel
import java.util.Date

@Composable
internal fun documentDate(document: DocumentModel): String {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    return remember(document.createdAt, configuration) {
        DateFormat.getMediumDateFormat(context).format(Date(document.createdAt))
    }
}

@Composable
internal fun documentFileSize(document: DocumentModel): String {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    return remember(document.sizeBytes, configuration) {
        Formatter.formatShortFileSize(context, document.sizeBytes)
    }
}