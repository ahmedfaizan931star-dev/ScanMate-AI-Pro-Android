package com.synthbyte.scanmate.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.TextSnippet
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.ImageNotSupported
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.synthbyte.scanmate.data.DocumentWithPages
import com.synthbyte.scanmate.data.Page
import com.synthbyte.scanmate.ui.viewmodels.DocumentViewModel
import com.synthbyte.scanmate.ui.viewmodels.rememberDocumentViewModel
import com.synthbyte.scanmate.utils.DocumentAnalyticsEngine
import com.synthbyte.scanmate.utils.EncryptedVaultUtils
import com.synthbyte.scanmate.utils.FileUtils
import com.synthbyte.scanmate.utils.OcrHelper
import com.synthbyte.scanmate.utils.PdfExportQuality
import com.synthbyte.scanmate.utils.PdfPageSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentDetailScreen(
    docId: Long,
    onNavigateBack: () -> Unit,
    onNavigateToPageEditor: (Long, Long) -> Unit,
    onNavigateToSignature: (Long) -> Unit
) {
    val context = LocalContext.current
    val viewModel = rememberDocumentViewModel()
    val docFlow = remember(docId) { viewModel.getDocumentWithPages(docId) }
    val documentWithPages by docFlow.collectAsState(initial = null)
    val coroutineScope = rememberCoroutineScope()
    var isProcessing by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showMetaDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var exportedPdf by remember { mutableStateOf<File?>(null) }
    var exportedDocx by remember { mutableStateOf<File?>(null) }
    var exportProgressText by remember { mutableStateOf<String?>(null) }
    var selectedPdfQuality by remember { mutableStateOf(PdfExportQuality.BALANCED) }
    var selectedPdfPageSize by remember { mutableStateOf(PdfPageSize.A4) }
    var renameTitle by remember { mutableStateOf("") }
    var exportName by remember { mutableStateOf("ScanMate_${docId}_${System.currentTimeMillis()}") }
    var category by remember { mutableStateOf("General") }
    var tags by remember { mutableStateOf("") }
    var workspace by remember { mutableStateOf("Inbox") }
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    LaunchedEffect(documentWithPages?.document?.title, documentWithPages?.document?.category, documentWithPages?.document?.tags, documentWithPages?.document?.workspace) {
        renameTitle = documentWithPages?.document?.title.orEmpty()
        category = documentWithPages?.document?.category ?: "General"
        tags = documentWithPages?.document?.tags.orEmpty()
        workspace = documentWithPages?.document?.workspace ?: "Inbox"
        exportName = FileUtils.sanitizeFileBaseName(documentWithPages?.document?.title ?: "ScanMate_${docId}")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(documentWithPages?.document?.title ?: "Document", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    documentWithPages?.let { dwp ->
                        IconButton(onClick = {
                            viewModel.toggleFavorite(dwp.document.id, dwp.document.isFavorite)
                        }) {
                            Icon(if (dwp.document.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "Favorite")
                        }
                    }
                    documentWithPages?.let { dwp ->
                        IconButton(onClick = {
                            viewModel.togglePinned(dwp.document.id, dwp.document.isPinned)
                        }) {
                            Icon(Icons.Default.PushPin, if (dwp.document.isPinned) "Unpin" else "Pin")
                        }
                    }
                    IconButton(onClick = { showRenameDialog = true }) { Icon(Icons.Default.DriveFileRenameOutline, "Rename") }
                    IconButton(onClick = { showExportDialog = true }) { Icon(Icons.Default.PictureAsPdf, "Export PDF") }
                    IconButton(onClick = { extractOcr(coroutineScope, documentWithPages, context, viewModel, clipboardManager) { isProcessing = it } }) {
                        Icon(Icons.Default.TextSnippet, "OCR full document")
                    }
                    IconButton(onClick = { showDeleteDialog = true }) { Icon(Icons.Default.Delete, "Delete") }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
            if (isProcessing) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                exportProgressText?.let { Text(it, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }

            val dwp = documentWithPages
            if (dwp == null) {
                LoadingDocumentState()
            } else {
                val pages = dwp.pages.sortedBy { it.pageOrder }
                DocumentPreview(pages)
                QuickActionRow(
                    dwp = dwp,
                    onShareFirstImage = {
                        val firstFile = pages.firstOrNull()?.imagePath?.let { File(it) }
                        if (firstFile != null && firstFile.exists()) {
                            FileUtils.shareFile(context, firstFile, FileUtils.mimeTypeFor(firstFile))
                        } else {
                            Toast.makeText(context, "No image file found to share", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onExport = { showExportDialog = true },
                    onExportDocx = { exportDocx(coroutineScope, documentWithPages, context, onDocxReady = { exportedDocx = it }) { isProcessing = it } },
                    onSignature = { onNavigateToSignature(docId) },
                    onMeta = { showMetaDialog = true }
                )
                DocumentMetaChips(dwp)
                DocumentInsightsCard(dwp)
                OcrCard(dwp = dwp, clipboardManager = clipboardManager, context = context) { file -> exportedDocx = file }
                PageThumbnails(pages, onEdit = { page -> onNavigateToPageEditor(docId, page.id) })
                PageManagementList(
                    pages = pages,
                    onEdit = { page -> onNavigateToPageEditor(docId, page.id) },
                    onMove = { page, direction ->
                        coroutineScope.launch {
                            viewModel.movePage(docId, page.id, direction) {
                                Toast.makeText(context, "Page order updated", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    onReorder = { reorderedPages ->
                        coroutineScope.launch {
                            viewModel.reorderPages(reorderedPages) {
                                Toast.makeText(context, "Drag reorder saved", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    onDuplicate = { page ->
                        coroutineScope.launch {
                            viewModel.duplicatePage(docId, page.imagePath, page.id,
                                onDone = { Toast.makeText(context, "Page duplicated", Toast.LENGTH_SHORT).show() },
                                onError = { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
                            )
                        }
                    },
                    onDelete = { page ->
                        coroutineScope.launch {
                            viewModel.deletePage(docId, page.id) {
                                Toast.makeText(context, "Page deleted", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )
            }
        }
    }

    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename document") },
            text = {
                OutlinedTextField(
                    value = renameTitle,
                    onValueChange = { renameTitle = it },
                    singleLine = true,
                    label = { Text("Document name") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.renameDocument(docId, renameTitle.trim().ifBlank { "Untitled Scan" })
                    showRenameDialog = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showRenameDialog = false }) { Text("Cancel") } }
        )
    }

    if (showMetaDialog) {
        AlertDialog(
            onDismissRequest = { showMetaDialog = false },
            title = { Text("Category & tags") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = workspace, onValueChange = { workspace = it }, label = { Text("Workspace / folder") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = category, onValueChange = { category = it }, label = { Text("Category") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = tags, onValueChange = { tags = it }, label = { Text("Tags, comma separated") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.updateCategoryTags(docId, category.ifBlank { "General" }, tags, workspace.ifBlank { "Inbox" })
                    showMetaDialog = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showMetaDialog = false }) { Text("Cancel") } }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete this document?") },
            text = { Text("This removes the document record from ScanMate. Export or share anything important first.") },
            confirmButton = {
                Button(onClick = {
                    viewModel.deleteDocument(docId) {
                        Toast.makeText(context, "Deleted", Toast.LENGTH_SHORT).show()
                        showDeleteDialog = false
                        onNavigateBack()
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") } }
        )
    }

    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("Export PDF") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = exportName,
                        onValueChange = { exportName = it },
                        label = { Text("PDF name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Page size", fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        PdfPageSize.entries.forEach { pageSize ->
                            FilterChip(
                                selected = selectedPdfPageSize == pageSize,
                                onClick = { selectedPdfPageSize = pageSize },
                                label = { Text(pageSize.label) }
                            )
                        }
                    }
                    Text(selectedPdfPageSize.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Quality", fontWeight = FontWeight.Bold)
                    PdfExportQuality.entries.forEach { quality ->
                        OutlinedButton(onClick = {
                            selectedPdfQuality = quality
                            showExportDialog = false
                            exportPdf(coroutineScope, documentWithPages, context, quality, selectedPdfPageSize, exportName, onPdfReady = { exportedPdf = it }, onProgress = { exportProgressText = it }) { isProcessing = it }
                        }, modifier = Modifier.fillMaxWidth()) {
                            Column(horizontalAlignment = Alignment.Start, modifier = Modifier.fillMaxWidth()) {
                                Text(quality.label, fontWeight = FontWeight.Bold)
                                Text(quality.description, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showExportDialog = false }) { Text("Cancel") } }
        )
    }

    exportedPdf?.let { pdfFile ->
        AlertDialog(
            onDismissRequest = { exportedPdf = null },
            title = { Text("PDF ready") },
            text = { Text("${pdfFile.name} was exported successfully and can be opened or shared.") },
            confirmButton = {
                Button(onClick = { FileUtils.openFile(context, pdfFile, "application/pdf") }) { Text("Open PDF") }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { FileUtils.shareFile(context, pdfFile, "application/pdf") }) { Text("Share") }
                    TextButton(onClick = { exportedPdf = null }) { Text("Close") }
                }
            }
        )
    }

    exportedDocx?.let { docxFile ->
        AlertDialog(
            onDismissRequest = { exportedDocx = null },
            title = { Text("DOCX ready") },
            text = { Text("${docxFile.name} was exported from OCR text and can be opened or shared.") },
            confirmButton = {
                Button(onClick = { FileUtils.openFile(context, docxFile, FileUtils.mimeTypeFor(docxFile)) }) { Text("Open DOCX") }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { FileUtils.shareFile(context, docxFile, FileUtils.mimeTypeFor(docxFile)) }) { Text("Share") }
                    TextButton(onClick = { exportedDocx = null }) { Text("Close") }
                }
            }
        )
    }
}

@Composable
private fun LoadingDocumentState() {
    Column(modifier = Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(12.dp))
        Text("Loading document...")
    }
}

@Composable
private fun DocumentPreview(pages: List<Page>) {
    val firstPath = pages.firstOrNull()?.imagePath
    val bitmap = remember(firstPath) { firstPath?.let { FileUtils.decodeSampledBitmap(it, 1400, 1400) } }
    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Document Page",
                modifier = Modifier.fillMaxWidth().height(420.dp).padding(12.dp),
                contentScale = ContentScale.Fit
            )
        } else {
            Column(modifier = Modifier.fillMaxWidth().height(220.dp).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(Icons.Default.ImageNotSupported, null, modifier = Modifier.size(42.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(8.dp))
                Text("No preview available", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun QuickActionRow(
    dwp: DocumentWithPages,
    onShareFirstImage: () -> Unit,
    onExport: () -> Unit,
    onExportDocx: () -> Unit,
    onSignature: () -> Unit,
    onMeta: () -> Unit
) {
    LazyRow(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item { AssistChip(onClick = {}, label = { Text("${dwp.pages.size} page${if (dwp.pages.size == 1) "" else "s"}") }) }
        item { AssistChip(onClick = onShareFirstImage, leadingIcon = { Icon(Icons.Default.Share, null, modifier = Modifier.size(16.dp)) }, label = { Text("Share image") }) }
        item { AssistChip(onClick = onExport, leadingIcon = { Icon(Icons.Default.PictureAsPdf, null, modifier = Modifier.size(16.dp)) }, label = { Text("Export PDF") }) }
        item { AssistChip(onClick = onExportDocx, leadingIcon = { Icon(Icons.Default.TextSnippet, null, modifier = Modifier.size(16.dp)) }, label = { Text("Export DOCX") }) }
        item { AssistChip(onClick = onSignature, leadingIcon = { Icon(Icons.Default.Style, null, modifier = Modifier.size(16.dp)) }, label = { Text("Signature") }) }
        item { AssistChip(onClick = onMeta, leadingIcon = { Icon(Icons.Default.Tag, null, modifier = Modifier.size(16.dp)) }, label = { Text("Tags") }) }
    }
}

@Composable
private fun DocumentMetaChips(dwp: DocumentWithPages) {
    LazyRow(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item { FilterChip(selected = true, onClick = {}, label = { Text(dwp.document.workspace.ifBlank { "Inbox" }) }) }
        item { FilterChip(selected = true, onClick = {}, label = { Text(dwp.document.category.ifBlank { "General" }) }) }
        if (dwp.document.isPinned) item { FilterChip(selected = true, onClick = {}, label = { Text("Pinned") }) }
        val tagList = dwp.document.tags.split(',').map { it.trim() }.filter { it.isNotBlank() }
        tagList.forEach { tag ->
            item { FilterChip(selected = false, onClick = {}, label = { Text(tag) }) }
        }
    }
}

@Composable
private fun DocumentInsightsCard(dwp: DocumentWithPages) {
    val insights = remember(dwp.document.id, dwp.document.ocrText, dwp.pages.size, dwp.document.tags) {
        DocumentAnalyticsEngine.analyze(dwp)
    }
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Document insights", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InsightPill("Pages", insights.pageCount.toString(), Modifier.weight(1f))
                InsightPill("Words", insights.wordCount.toString(), Modifier.weight(1f))
                InsightPill("Read", "${insights.estimatedReadingMinutes}m", Modifier.weight(1f))
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InsightPill("Size", "${insights.storageKb}KB", Modifier.weight(1f))
                InsightPill("Status", insights.qualityLabel, Modifier.weight(1f))
            }
            Text("Keywords: ${insights.keywordPreview}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun InsightPill(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun OcrCard(dwp: DocumentWithPages, clipboardManager: ClipboardManager, context: Context, onDocxReady: (File) -> Unit) {
    val text = dwp.document.ocrText
    val coroutineScope = rememberCoroutineScope()
    if (!text.isNullOrBlank()) {
        Card(modifier = Modifier.fillMaxWidth().padding(16.dp), shape = RoundedCornerShape(20.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                val stats = remember(text) { OcrHelper.buildStats(text) }
                Text("Extracted Text", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("${stats.qualityLabel} · ${stats.confidencePercent}% · ${stats.wordCount} words", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                Text(stats.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        TextButton(onClick = {
                            clipboardManager.setPrimaryClip(ClipData.newPlainText("Extracted Text", text))
                            Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                        }) { Text("Copy") }
                    }
                    item { TextButton(onClick = { FileUtils.shareText(context, text) }) { Text("Share") } }
                    item {
                        TextButton(onClick = {
                            coroutineScope.launch {
                                val file = FileUtils.saveTextFile(context, text, "OCR_${dwp.document.id}_${System.currentTimeMillis()}")
                                if (file != null) FileUtils.shareFile(context, file, "text/plain") else Toast.makeText(context, "TXT export failed", Toast.LENGTH_SHORT).show()
                            }
                        }) { Text("Save TXT") }
                    }
                    item {
                        TextButton(onClick = {
                            coroutineScope.launch {
                                val file = FileUtils.saveDocxText(context, text, "OCR_${dwp.document.id}_${System.currentTimeMillis()}")
                                if (file != null) onDocxReady(file) else Toast.makeText(context, "DOCX export failed", Toast.LENGTH_SHORT).show()
                            }
                        }) { Text("Save DOCX") }
                    }
                    item {
                        TextButton(onClick = {
                            coroutineScope.launch {
                                val file = EncryptedVaultUtils.saveEncryptedText(context, text, "OCR_${dwp.document.id}_${System.currentTimeMillis()}")
                                if (file != null) Toast.makeText(context, "Saved to encrypted vault", Toast.LENGTH_SHORT).show() else Toast.makeText(context, "Vault save failed", Toast.LENGTH_SHORT).show()
                            }
                        }) { Text("Vault") }
                    }
                }
            }
        }
    }
}

@Composable
private fun PageThumbnails(pages: List<Page>, onEdit: (Page) -> Unit) {
    if (pages.isNotEmpty()) {
        Text("Pages", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        LazyRow(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(pages, key = { it.id }) { page ->
                val bitmap = remember(page.imagePath) { FileUtils.decodeSampledBitmap(page.imagePath, 360, 360) }
                Card(onClick = { onEdit(page) }, shape = RoundedCornerShape(16.dp)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (bitmap != null) {
                            Image(bitmap = bitmap.asImageBitmap(), contentDescription = "Thumbnail", modifier = Modifier.size(112.dp), contentScale = ContentScale.Crop)
                        } else {
                            Column(modifier = Modifier.size(112.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                Icon(Icons.Default.ImageNotSupported, null)
                            }
                        }
                        Text("Page ${page.pageOrder + 1}", modifier = Modifier.padding(bottom = 8.dp), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun PageManagementList(
    pages: List<Page>,
    onEdit: (Page) -> Unit,
    onMove: (Page, Int) -> Unit,
    onReorder: (List<Page>) -> Unit,
    onDuplicate: (Page) -> Unit,
    onDelete: (Page) -> Unit
) {
    val orderedPages = remember { mutableStateListOf<Page>() }
    var activePageId by remember { mutableStateOf<Long?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    val haptics = LocalHapticFeedback.current
    val itemHeightPx = with(LocalDensity.current) { 76.dp.toPx() }

    LaunchedEffect(pages.map { it.id to it.pageOrder }) {
        orderedPages.clear()
        orderedPages.addAll(pages.sortedBy { it.pageOrder })
    }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Page management", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("Long-press a page row and drag up/down to reorder. Arrow buttons stay as a safe fallback.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        orderedPages.forEachIndexed { index, page ->
            val offsetY = if (activePageId == page.id) dragOffsetY.roundToInt() else 0
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = if (activePageId == page.id) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .offset { IntOffset(0, offsetY) }
                    .zIndex(if (activePageId == page.id) 1f else 0f)
                    .pointerInput(orderedPages.size, page.id) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                activePageId = page.id
                                dragOffsetY = 0f
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            onDragCancel = {
                                activePageId = null
                                dragOffsetY = 0f
                            },
                            onDragEnd = {
                                activePageId = null
                                dragOffsetY = 0f
                                onReorder(orderedPages.toList())
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                if (activePageId == page.id) {
                                    dragOffsetY += dragAmount.y
                                    val currentIndex = orderedPages.indexOfFirst { it.id == page.id }
                                    if (dragOffsetY > itemHeightPx && currentIndex in 0 until orderedPages.lastIndex) {
                                        orderedPages.removeAt(currentIndex)
                                        orderedPages.add(currentIndex + 1, page)
                                        dragOffsetY -= itemHeightPx
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    } else if (dragOffsetY < -itemHeightPx && currentIndex > 0) {
                                        orderedPages.removeAt(currentIndex)
                                        orderedPages.add(currentIndex - 1, page)
                                        dragOffsetY += itemHeightPx
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    }
                                }
                            }
                        )
                    }
            ) {
                Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Page ${index + 1}", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    IconButton(onClick = { onMove(page, -1) }) { Icon(Icons.Default.KeyboardArrowUp, "Move up") }
                    IconButton(onClick = { onMove(page, 1) }) { Icon(Icons.Default.KeyboardArrowDown, "Move down") }
                    IconButton(onClick = { onDuplicate(page) }) { Icon(Icons.Default.ContentCopy, "Duplicate") }
                    IconButton(onClick = { onEdit(page) }) { Icon(Icons.Default.Edit, "Edit") }
                    IconButton(onClick = { onDelete(page) }) { Icon(Icons.Default.Delete, "Delete") }
                }
            }
        }
    }
}


private fun exportDocx(
    coroutineScope: CoroutineScope,
    dwp: DocumentWithPages?,
    context: Context,
    onDocxReady: (File) -> Unit,
    setProcessing: (Boolean) -> Unit
) {
    if (dwp == null) return
    val text = dwp.document.ocrText.orEmpty().trim()
    if (text.isBlank()) {
        Toast.makeText(context, "Run OCR first, then export DOCX", Toast.LENGTH_SHORT).show()
        return
    }
    coroutineScope.launch {
        setProcessing(true)
        val file = FileUtils.saveDocxText(
            context = context,
            text = text,
            filename = dwp.document.title.ifBlank { "ScanMate_${dwp.document.id}_${System.currentTimeMillis()}" }
        )
        setProcessing(false)
        if (file != null) {
            Toast.makeText(context, "DOCX exported", Toast.LENGTH_SHORT).show()
            onDocxReady(file)
        } else {
            Toast.makeText(context, "DOCX export failed", Toast.LENGTH_SHORT).show()
        }
    }
}

private fun exportPdf(
    coroutineScope: CoroutineScope,
    dwp: DocumentWithPages?,
    context: Context,
    quality: PdfExportQuality,
    pageSize: PdfPageSize,
    filename: String,
    onPdfReady: (File) -> Unit,
    onProgress: (String?) -> Unit,
    setProcessing: (Boolean) -> Unit
) {
    if (dwp == null) return
    val pages = dwp.pages.sortedBy { it.pageOrder }
    if (pages.isEmpty()) {
        Toast.makeText(context, "No pages found to export", Toast.LENGTH_SHORT).show()
        return
    }
    coroutineScope.launch {
        setProcessing(true)
        onProgress("Building page 0 of ${pages.size}…")
        val pdfFile = FileUtils.generatePdfFromPaths(
            context = context,
            imagePaths = pages.map { it.imagePath },
            filename = filename.ifBlank { dwp.document.title.ifBlank { "ScanMate_${dwp.document.id}_${System.currentTimeMillis()}" } },
            quality = quality,
            pageSize = pageSize,
            onProgress = { current, total -> onProgress("Building page $current of $total…") }
        )
        setProcessing(false)
        onProgress(null)
        if (pdfFile != null) {
            Toast.makeText(context, "PDF exported", Toast.LENGTH_SHORT).show()
            onPdfReady(pdfFile)
        } else {
            Toast.makeText(context, "PDF export failed. Check that pages are valid images.", Toast.LENGTH_SHORT).show()
        }
    }
}

private fun extractOcr(
    coroutineScope: CoroutineScope,
    dwp: DocumentWithPages?,
    context: Context,
    viewModel: DocumentViewModel,
    clipboardManager: ClipboardManager,
    setProcessing: (Boolean) -> Unit
) {
    if (dwp == null || dwp.pages.isEmpty()) {
        Toast.makeText(context, "No pages available for OCR", Toast.LENGTH_SHORT).show()
        return
    }
    val pages = dwp.pages.sortedBy { it.pageOrder }
    coroutineScope.launch {
        setProcessing(true)
        val text = withContext(Dispatchers.IO) {
            pages.mapIndexedNotNull { index, page ->
                val file = File(page.imagePath)
                if (!file.exists() || file.length() == 0L) return@mapIndexedNotNull null
                val fileResult = OcrHelper.extractTextFromFile(context, file)
                val result = if (fileResult.startsWith("OCR failed", ignoreCase = true)) {
                    FileUtils.decodeSampledBitmap(file.absolutePath, 1800, 1800)?.let { bitmap ->
                        OcrHelper.extractTextFromBitmap(bitmap)
                    }.orEmpty()
                } else {
                    fileResult
                }
                if (result.isBlank() || result.startsWith("OCR failed", ignoreCase = true)) null else "Page ${index + 1}:\n$result"
            }.joinToString(separator = "\n\n")
        }
        setProcessing(false)
        if (text.isBlank()) {
            Toast.makeText(context, "No readable text found", Toast.LENGTH_SHORT).show()
            return@launch
        }
        val stats = OcrHelper.buildStats(text)
        val suggested = com.synthbyte.scanmate.utils.DocumentIntelligence.suggestedCategory(stats.text)
        val keywords = com.synthbyte.scanmate.utils.DocumentIntelligence.keywordList(stats.text, 8).joinToString(", ")
        viewModel.updateOcrText(dwp.document.id, stats.text)
        viewModel.updateCategoryTags(dwp.document.id, suggested, keywords, dwp.document.workspace.ifBlank { "Inbox" })
        clipboardManager.setPrimaryClip(ClipData.newPlainText("Extracted Text", stats.text))
        Toast.makeText(context, "OCR completed · ${stats.qualityLabel}", Toast.LENGTH_SHORT).show()
    }
}
