package com.synthbyte.scanmate.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.UploadFile
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.synthbyte.scanmate.utils.FileUtils
import com.synthbyte.scanmate.utils.PdfExportQuality
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfToolsScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val selectedPdfs = remember { mutableStateListOf<Uri>() }
    var isProcessing by remember { mutableStateOf(false) }
    var exportedPdf by remember { mutableStateOf<File?>(null) }
    var outputName by remember { mutableStateOf("ScanMate_PDF_${System.currentTimeMillis()}") }
    var splitStart by remember { mutableStateOf("1") }
    var splitEnd by remember { mutableStateOf("1") }
    var quality by remember { mutableStateOf(PdfExportQuality.BALANCED) }

    val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            selectedPdfs.clear()
            selectedPdfs.addAll(uris)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PDF Tools") },
                navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (isProcessing) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)),
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PictureAsPdf, null, tint = MaterialTheme.colorScheme.primary)
                        Column(modifier = Modifier.padding(start = 12.dp)) {
                            Text("Offline PDF utilities", fontWeight = FontWeight.Bold)
                            Text("Merge, split, compress, rename, open, and share PDFs using Android PdfRenderer + PdfDocument.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Button(onClick = { pdfPicker.launch(arrayOf("application/pdf")) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.UploadFile, null)
                        Text(" Select PDFs")
                    }
                }
            }

            OutlinedTextField(
                value = outputName,
                onValueChange = { outputName = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Output PDF name") }
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                PdfExportQuality.entries.forEach { item ->
                    FilterChip(
                        selected = quality == item,
                        onClick = { quality = item },
                        label = { Text(item.label.substringBefore(" /")) }
                    )
                }
            }

            if (selectedPdfs.isEmpty()) {
                EmptyPdfToolState()
            } else {
                Text("Selected PDFs", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f, fill = false)) {
                    itemsIndexed(selectedPdfs) { index, uri ->
                        PdfUriRow(index = index, uri = uri, onRemove = { selectedPdfs.remove(uri) })
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = {
                        if (selectedPdfs.isEmpty()) {
                            Toast.makeText(context, "Select one or more PDFs first", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        scope.launch {
                            isProcessing = true
                            val output = withContext(Dispatchers.IO) {
                                val pages = selectedPdfs.flatMap { uri -> FileUtils.renderPdfUriToBitmaps(context, uri, maxWidth = widthFor(quality)) }
                                FileUtils.generatePdfFromBitmaps(context, pages, outputName, quality)
                            }
                            exportedPdf = output
                            isProcessing = false
                            Toast.makeText(context, if (output != null) "PDF exported" else "PDF merge failed", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.MergeType, null)
                    Text(" Merge")
                }
                OutlinedButton(
                    onClick = {
                        if (selectedPdfs.isEmpty()) {
                            Toast.makeText(context, "Select a PDF first", Toast.LENGTH_SHORT).show()
                            return@OutlinedButton
                        }
                        val start = splitStart.toIntOrNull()?.coerceAtLeast(1) ?: 1
                        val end = splitEnd.toIntOrNull()?.coerceAtLeast(start) ?: start
                        scope.launch {
                            isProcessing = true
                            val output = withContext(Dispatchers.IO) {
                                val pages = FileUtils.renderPdfUriToBitmaps(context, selectedPdfs.first(), maxWidth = widthFor(quality), pageRange = start..end)
                                FileUtils.generatePdfFromBitmaps(context, pages, "${outputName}_pages_${start}_$end", quality)
                            }
                            exportedPdf = output
                            isProcessing = false
                            Toast.makeText(context, if (output != null) "Split PDF exported" else "Split failed", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("Split") }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = splitStart,
                    onValueChange = { splitStart = it.filter(Char::isDigit).take(4) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text("From page") }
                )
                OutlinedTextField(
                    value = splitEnd,
                    onValueChange = { splitEnd = it.filter(Char::isDigit).take(4) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text("To page") }
                )
            }

            exportedPdf?.let { pdfFile ->
                ExportedPdfActions(pdfFile = pdfFile, onDismiss = { exportedPdf = null })
            }
        }
    }
}

@Composable
private fun EmptyPdfToolState() {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("No PDFs selected", fontWeight = FontWeight.Bold)
            Text("Choose PDFs to merge or select one PDF and set a page range to split it.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PdfUriRow(index: Int, uri: Uri, onRemove: () -> Unit) {
    val context = LocalContext.current
    val name = remember(uri) { FileUtils.getDisplayName(context, uri) }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(14.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            AssistChip(onClick = {}, label = { Text("${index + 1}") })
            Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Will be rendered safely before export", color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            IconButton(onClick = onRemove) { Icon(Icons.Default.Delete, "Remove") }
        }
    }
}

@Composable
private fun ExportedPdfActions(pdfFile: File, onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("PDF ready") },
        text = { Text("${pdfFile.name} was created successfully.") },
        confirmButton = {
            Button(onClick = { FileUtils.openFile(context, pdfFile, "application/pdf") }) {
                Icon(Icons.Default.OpenInNew, null)
                Text(" Open")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { FileUtils.shareFile(context, pdfFile, "application/pdf") }) {
                    Icon(Icons.Default.Share, null)
                    Text(" Share")
                }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        }
    )
}

private fun widthFor(quality: PdfExportQuality): Int = when (quality) {
    PdfExportQuality.SMALL -> 1000
    PdfExportQuality.BALANCED -> 1500
    PdfExportQuality.HIGH -> 2200
}
