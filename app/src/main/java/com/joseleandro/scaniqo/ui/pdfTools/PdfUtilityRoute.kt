package com.joseleandro.scaniqo.ui.pdfTools

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.joseleandro.scaniqo.R
import com.joseleandro.scaniqo.core.image.DocumentImageDecoder
import com.joseleandro.scaniqo.core.security.SecureWindow
import com.joseleandro.scaniqo.domain.model.DOCUMENT_NAME_MAX_LENGTH
import com.joseleandro.scaniqo.domain.model.DocumentModel
import com.joseleandro.scaniqo.domain.model.PdfImageFormat
import com.joseleandro.scaniqo.domain.model.PdfUtility
import com.joseleandro.scaniqo.domain.repository.AppSettingsRepository
import com.joseleandro.scaniqo.ui.documentCommon.components.DocumentThumbnail
import com.joseleandro.scaniqo.ui.documentCommon.platform.rememberDocumentScannerLauncher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfUtilityRoute(
    tool: PdfUtility,
    onBack: () -> Unit,
    onOpenDocument: (Long) -> Unit,
    onImport: () -> Unit,
    importBusy: Boolean = false,
    importedDocumentId: Long? = null,
    onImportedSelected: () -> Unit = {},
    viewModel: PdfUtilityViewModel = koinViewModel(key = tool.name),
    settingsRepository: AppSettingsRepository = koinInject(),
) {
    val operationId = rememberSaveable(tool) { UUID.randomUUID().toString() }
    val defaults = remember(operationId) { settingsRepository.settings.value }
    val currentState by viewModel.uiState.collectAsStateWithLifecycle()
    val state = if (currentState.operationId == operationId) currentState else PdfUtilityUiState(
        operationId = operationId, documents = currentState.documents,
        loading = currentState.loading, loadError = currentState.loadError,
    )
    LaunchedEffect(operationId) { viewModel.beginOperation(operationId) }
    var selectedId by rememberSaveable(tool, operationId) { mutableStateOf<Long?>(null) }
    var pickingDocument by rememberSaveable(tool, operationId) { mutableStateOf(false) }
    var name by rememberSaveable(tool, operationId) { mutableStateOf("") }
    var pages by rememberSaveable(tool, operationId) { mutableStateOf("") }
    var receiptPage by rememberSaveable(tool, operationId) { mutableStateOf("1") }
    var images by rememberSaveable(tool, operationId) { mutableStateOf(emptyList<String>()) }
    var imageFormat by rememberSaveable(tool, operationId) { mutableStateOf(defaults.imageFormat) }
    var compression by rememberSaveable(tool, operationId) { mutableStateOf(defaults.compressionLevel) }
    var targetUri by rememberSaveable(tool, operationId) { mutableStateOf<String?>(null) }
    var externalPicker by rememberSaveable(tool, operationId) { mutableStateOf(false) }
    var imageSlot by rememberSaveable(tool, operationId) { mutableStateOf(0) }
    var receiptExportToken by rememberSaveable(tool, operationId) { mutableStateOf<Long?>(null) }
    var password by remember(selectedId) { mutableStateOf("") }
    val selected = state.documents.firstOrNull { it.id == selectedId }
    SecureWindow(enabled = selected?.isProtected == true || state.receiptProtected)
    val imageTool = tool == PdfUtility.IMAGES_TO_PDF || tool == PdfUtility.ID_CARD
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, viewModel, selectedId) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                password = ""
                viewModel.backgrounded()
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    BackHandler(enabled = state.busy || importBusy) {}
    fun selectDocument(document: DocumentModel) {
        selectedId = document.id
        pages = ""
        receiptPage = "1"
        targetUri = null
        name = (document.name + if (tool == PdfUtility.COMPRESS_PDF)
            " — comprimido" else " — páginas").take(DOCUMENT_NAME_MAX_LENGTH)
        viewModel.clearReceipt()
        viewModel.clearCompressionResult()
        pickingDocument = false
    }
    LaunchedEffect(importedDocumentId, state.documents) {
        val document = state.documents.firstOrNull { it.id == importedDocumentId }
        if (document != null) {
            selectDocument(document)
            onImportedSelected()
        }
    }
    LaunchedEffect(state.createdDocumentId) {
        state.createdDocumentId?.let { id ->
            viewModel.consumedDocument(id)
            onOpenDocument(id)
        }
    }
    fun persistRead(uri: Uri) {
        try { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        catch (_: SecurityException) { /* A concessão temporária também permite concluir a operação. */ }
    }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { values ->
        externalPicker = false
        if (values.isNotEmpty()) {
            if (values.size > 20) viewModel.reportError("Selecione no máximo 20 imagens.")
            else { values.forEach(::persistRead); images = values.map(Uri::toString) }
        }
    }
    val facePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { value ->
        externalPicker = false
        if (value != null) {
            persistRead(value)
            val updated = images.toMutableList()
            while (updated.size < 2) updated += ""
            updated[imageSlot] = value.toString()
            images = updated
        }
    }
    val zipPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { value ->
        externalPicker = false
        targetUri = value?.toString()
    }
    val csvPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { value ->
        externalPicker = false
        val token = receiptExportToken
        receiptExportToken = null
        viewModel.completeReceiptExport(value?.toString(), token)
    }
    val scanner = remember(tool) {
        GmsDocumentScanning.getClient(GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true).setPageLimit(if (tool == PdfUtility.ID_CARD) 2 else 20)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL).build())
    }
    val scan = rememberDocumentScannerLauncher(
        scanner,
        onCompleted = { images = it; externalPicker = false },
        onCancelled = { externalPicker = false },
        onFailed = { externalPicker = false; viewModel.reportError("Não foi possível abrir o scanner.") },
    )
    val title = when (tool) {
        PdfUtility.EXTRACT_PAGES -> R.string.utility_extract_title
        PdfUtility.PDF_TO_IMAGES -> R.string.utility_images_export_title
        PdfUtility.IMAGES_TO_PDF -> R.string.utility_images_import_title
        PdfUtility.ID_CARD -> R.string.utility_id_title
        PdfUtility.RECEIPT -> R.string.utility_receipt_title
        PdfUtility.COMPRESS_PDF -> R.string.utility_workspace_compress_title
    }
    fun launchSaf(onFailure: () -> Unit = {}, action: () -> Unit) {
        if (externalPicker) return
        externalPicker = true
        try { action() } catch (_: Exception) {
            externalPicker = false
            onFailure()
            viewModel.reportError("Não foi possível abrir o seletor de arquivos.")
        }
    }
    val retainedTool = tool in setOf(PdfUtility.EXTRACT_PAGES, PdfUtility.PDF_TO_IMAGES, PdfUtility.COMPRESS_PDF)
    if (retainedTool) {
        PdfUtilityWorkspace(
            tool = tool,
            state = state,
            document = selected,
            name = name,
            pages = pages,
            password = password,
            format = imageFormat,
            compression = compression,
            destinationReady = targetUri != null,
            externalPicker = externalPicker || importBusy,
            onBack = onBack,
            onChooseDocument = { pickingDocument = true },
            onImport = onImport,
            onName = { name = it; viewModel.clearCompressionResult() },
            onPages = { pages = it },
            onPassword = { password = it },
            onFormat = { imageFormat = it },
            onCompression = { compression = it; viewModel.clearCompressionResult() },
            onDestination = { launchSaf { zipPicker.launch("paginas.zip") } },
            onAction = {
                selected?.let { source ->
                    when (tool) {
                        PdfUtility.EXTRACT_PAGES -> viewModel.extract(source.id, pages, name, password)
                        PdfUtility.COMPRESS_PDF -> viewModel.compress(source.id, name, compression, password)
                        PdfUtility.PDF_TO_IMAGES -> if (targetUri == null) {
                            launchSaf { zipPicker.launch("paginas.zip") }
                        } else viewModel.exportImages(source.id, pages, imageFormat, requireNotNull(targetUri), password)
                        else -> Unit
                    }
                }
            },
            onRetry = viewModel::retryDocuments,
            onOpenDocument = onOpenDocument,
        )
    } else Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(title)) },
            navigationIcon = {
                IconButton(onClick = onBack, enabled = !state.busy) {
                    Icon(painterResource(R.drawable.arrow_back_24px), stringResource(R.string.utility_back))
                }
            },
        )
    }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()
                .verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (state.busy || state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (state.loadError && !imageTool) {
                Text(stringResource(R.string.utility_library_error), color = MaterialTheme.colorScheme.error)
                TextButton(onClick = viewModel::retryDocuments, enabled = !state.busy) {
                    Text(stringResource(R.string.documents_retry))
                }
            }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            state.message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            Text(stringResource(when (tool) {
                PdfUtility.EXTRACT_PAGES -> R.string.utility_extract_description
                PdfUtility.PDF_TO_IMAGES -> R.string.utility_export_note
                PdfUtility.IMAGES_TO_PDF -> R.string.utility_images_description
                PdfUtility.ID_CARD -> R.string.utility_id_description
                PdfUtility.RECEIPT -> R.string.utility_receipt_description
                PdfUtility.COMPRESS_PDF -> R.string.utility_workspace_compress_hint
            }), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!imageTool) {
                selected?.let { document ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        DocumentThumbnail(document, Modifier.size(48.dp, 68.dp).clip(MaterialTheme.shapes.small))
                        Column(Modifier.weight(1f)) {
                            Text(document.name, style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(R.string.utility_source_pages, document.pageCount), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                OutlinedButton(onClick = { pickingDocument = true }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(if (selected == null) R.string.utility_select_pdf else R.string.utility_change_pdf))
                }
                if (selected?.isProtected == true && state.receipt == null) {
                    OutlinedTextField(password, { password = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                        enabled = !state.busy, label = { Text(stringResource(R.string.utility_password)) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                    if (tool == PdfUtility.EXTRACT_PAGES) Text(stringResource(R.string.utility_password_note),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            when (tool) {
                PdfUtility.EXTRACT_PAGES, PdfUtility.PDF_TO_IMAGES -> {
                    OutlinedTextField(pages, { pages = it }, Modifier.fillMaxWidth(), enabled = !state.busy,
                        label = { Text(stringResource(R.string.utility_pages)) }, singleLine = true,
                        supportingText = { Text(stringResource(R.string.utility_pages_hint)) })
                    if (tool == PdfUtility.EXTRACT_PAGES) {
                        OutlinedTextField(name, { name = it.take(DOCUMENT_NAME_MAX_LENGTH) }, Modifier.fillMaxWidth(), enabled = !state.busy,
                            label = { Text(stringResource(R.string.utility_name)) }, singleLine = true)
                        Button(onClick = { selected?.let { viewModel.extract(it.id, pages, name, password) } },
                            enabled = !state.busy && selected != null && name.isNotBlank() &&
                                (selected.isProtected != true || password.isNotEmpty()), modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.utility_extract_action))
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PdfImageFormat.entries.forEach { format ->
                                FilterChip(selected = imageFormat == format, onClick = { imageFormat = format },
                                    label = { Text(if (format == PdfImageFormat.JPEG) "JPG" else "PNG") }, enabled = !state.busy)
                            }
                        }
                        if (targetUri != null) Text(stringResource(R.string.utility_destination_ready), style = MaterialTheme.typography.bodySmall)
                        Button(onClick = {
                            if (targetUri == null) launchSaf { zipPicker.launch("paginas.zip") }
                            else selected?.let { viewModel.exportImages(it.id, pages, imageFormat, requireNotNull(targetUri), password) }
                        }, enabled = !state.busy && selected != null &&
                            (targetUri == null || selected.isProtected != true || password.isNotEmpty()), modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(if (targetUri == null) R.string.utility_choose_destination else R.string.utility_export_action))
                        }
                    }
                }
                PdfUtility.IMAGES_TO_PDF, PdfUtility.ID_CARD -> {
                    OutlinedButton(onClick = { externalPicker = true; scan() }, enabled = !state.busy && !externalPicker, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.utility_scan))
                    }
                    if (tool == PdfUtility.ID_CARD) {
                        if (images.size == 2 && images.all(String::isNotBlank)) {
                            TextButton(onClick = { images = images.reversed() }, enabled = !state.busy) {
                                Text(stringResource(R.string.utility_id_swap))
                            }
                        }
                        repeat(2) { index ->
                            Text(stringResource(if (index == 0) R.string.utility_id_front else R.string.utility_id_back),
                                style = MaterialTheme.typography.titleSmall)
                            images.getOrNull(index)?.takeIf(String::isNotBlank)?.let { UtilityImage(it, Modifier.fillMaxWidth().height(120.dp)) }
                            OutlinedButton(onClick = {
                                imageSlot = index
                                launchSaf { facePicker.launch(arrayOf("image/*")) }
                            }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(if (index == 0) R.string.utility_id_pick_front else R.string.utility_id_pick_back))
                            }
                        }
                    } else {
                        OutlinedButton(onClick = { launchSaf { imagePicker.launch(arrayOf("image/*")) } },
                            enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.utility_pick_images)) }
                        images.forEachIndexed { index, uri ->
                            val moveUp = stringResource(R.string.utility_image_up, index + 1)
                            val moveDown = stringResource(R.string.utility_image_down, index + 1)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                UtilityImage(uri, Modifier.size(48.dp, 68.dp))
                                Text(stringResource(R.string.utility_image_label, index + 1), Modifier.weight(1f))
                                TextButton(onClick = { images = images.toMutableList().apply { add(index - 1, removeAt(index)) } },
                                    enabled = !state.busy && index > 0,
                                    modifier = Modifier.semantics { contentDescription = moveUp }) { Text("↑") }
                                TextButton(onClick = { images = images.toMutableList().apply { add(index + 1, removeAt(index)) } },
                                    enabled = !state.busy && index < images.lastIndex,
                                    modifier = Modifier.semantics { contentDescription = moveDown }) { Text("↓") }
                                TextButton(onClick = { images = images.filterIndexed { i, _ -> i != index } }, enabled = !state.busy) {
                                    Text(stringResource(R.string.utility_image_remove))
                                }
                            }
                        }
                    }
                    OutlinedTextField(name, { name = it.take(DOCUMENT_NAME_MAX_LENGTH) }, Modifier.fillMaxWidth(), enabled = !state.busy,
                        label = { Text(stringResource(R.string.utility_name)) }, singleLine = true)
                    Button(onClick = { viewModel.createFromImages(images, name, tool == PdfUtility.ID_CARD) },
                        enabled = !state.busy && name.isNotBlank() && images.isNotEmpty() &&
                            (tool != PdfUtility.ID_CARD || (images.size == 2 && images.all(String::isNotBlank))),
                        modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.utility_create_pdf)) }
                }
                PdfUtility.COMPRESS_PDF -> Unit // A compressão usa o workspace dedicado acima.
                PdfUtility.RECEIPT -> {
                    if (state.receipt == null) {
                        OutlinedTextField(receiptPage, { receiptPage = it.take(3) }, Modifier.fillMaxWidth(), enabled = !state.busy,
                            label = { Text(stringResource(R.string.utility_receipt_page)) }, singleLine = true)
                        Button(onClick = { selected?.let { viewModel.readReceipt(it.id, receiptPage, password) } },
                            enabled = !state.busy && selected != null && (selected.isProtected != true || password.isNotEmpty()),
                            modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.utility_receipt_read)) }
                    } else state.receipt?.let { receipt ->
                        HorizontalDivider()
                        Text(stringResource(R.string.utility_receipt_review), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.utility_receipt_review_note), style = MaterialTheme.typography.bodySmall)
                        OutlinedTextField(receipt.merchant, { viewModel.updateReceipt(receipt.copy(merchant = it)) },
                            Modifier.fillMaxWidth(), enabled = !state.busy, label = { Text(stringResource(R.string.utility_receipt_merchant)) })
                        OutlinedTextField(receipt.date, { viewModel.updateReceipt(receipt.copy(date = it)) },
                            Modifier.fillMaxWidth(), enabled = !state.busy, label = { Text(stringResource(R.string.utility_receipt_date)) })
                        OutlinedTextField(receipt.total, { viewModel.updateReceipt(receipt.copy(total = it)) },
                            Modifier.fillMaxWidth(), enabled = !state.busy, label = { Text(stringResource(R.string.utility_receipt_total)) })
                        OutlinedTextField(receipt.items, { viewModel.updateReceipt(receipt.copy(items = it)) },
                            Modifier.fillMaxWidth(), enabled = !state.busy, minLines = 3,
                            label = { Text(stringResource(R.string.utility_receipt_items)) })
                        Text(stringResource(R.string.utility_receipt_source), style = MaterialTheme.typography.titleSmall)
                        Text(receipt.sourceText, style = MaterialTheme.typography.bodySmall)
                        Text(stringResource(R.string.utility_receipt_csv_note), style = MaterialTheme.typography.bodySmall)
                        Button(onClick = {
                            viewModel.prepareReceiptExport()?.let { token ->
                                receiptExportToken = token
                                launchSaf(onFailure = {
                                    viewModel.completeReceiptExport(null, token)
                                    receiptExportToken = null
                                }) { csvPicker.launch("comprovante.csv") }
                            }
                        }, enabled = !state.busy && !externalPicker,
                            modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.utility_receipt_export)) }
                    }
                }
            }
        }
    }
    if (pickingDocument) {
        var query by rememberSaveable { mutableStateOf("") }
        val visible = state.documents.filter { it.name.contains(query.trim(), ignoreCase = true) }
        ModalBottomSheet(
            onDismissRequest = { pickingDocument = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.utility_select_pdf), style = MaterialTheme.typography.titleLarge)
                OutlinedButton(onClick = { pickingDocument = false; onImport() },
                    enabled = !state.busy && !externalPicker && !importBusy, modifier = Modifier.fillMaxWidth()) {
                    Icon(painterResource(R.drawable.ic_document_import), null, Modifier.padding(end = 8.dp).size(20.dp))
                    Text(stringResource(R.string.utility_workspace_import_device))
                }
                OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text(stringResource(R.string.utility_workspace_search)) },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_document_search), null) })
                if (visible.isEmpty()) Text(stringResource(if (state.documents.isEmpty())
                    R.string.utility_no_documents else R.string.utility_workspace_no_match))
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 440.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(visible, key = { it.id }) { document ->
                        Surface(
                            onClick = { selectDocument(document) },
                            enabled = !state.busy && !externalPicker && !importBusy,
                            shape = MaterialTheme.shapes.medium,
                            color = if (selectedId == document.id) MaterialTheme.colorScheme.secondaryContainer
                                else MaterialTheme.colorScheme.surface,
                        ) {
                            Row(Modifier.fillMaxWidth().padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                DocumentThumbnail(document, Modifier.size(40.dp, 56.dp).clip(MaterialTheme.shapes.extraSmall))
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(document.name, style = MaterialTheme.typography.titleSmall)
                                    Text(stringResource(R.string.utility_source_pages, document.pageCount),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (selectedId == document.id) Icon(painterResource(R.drawable.ic_check_24), null,
                                    tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UtilityImage(uri: String, modifier: Modifier) {
    val context = LocalContext.current.applicationContext
    var failed by remember(uri) { mutableStateOf(false) }
    val bitmap by produceState<Bitmap?>(null, uri) {
        var decoded: Bitmap? = null
        try {
            withContext(Dispatchers.IO) {
                decoded = DocumentImageDecoder.decode(480) {
                    requireNotNull(context.contentResolver.openInputStream(Uri.parse(uri)))
                }
            }
            value = decoded
        } catch (cancelled: CancellationException) { decoded?.recycle(); throw cancelled }
        catch (_: Exception) { decoded?.recycle(); failed = true }
    }
    bitmap?.let { Image(it.asImageBitmap(), null, modifier.clip(MaterialTheme.shapes.small), contentScale = ContentScale.Fit) }
        ?: Text(stringResource(if (failed) R.string.utility_image_error else R.string.utility_image_loading),
            modifier, style = MaterialTheme.typography.bodySmall)
}
