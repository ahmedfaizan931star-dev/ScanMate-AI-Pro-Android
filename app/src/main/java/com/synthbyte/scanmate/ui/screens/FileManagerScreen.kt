package com.synthbyte.scanmate.ui.screens

import android.widget.Toast
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.synthbyte.scanmate.utils.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileManagerScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    var files by remember { mutableStateOf<List<File>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var sortMode by remember { mutableStateOf(FileSortMode.DATE) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var fileToDelete by remember { mutableStateOf<File?>(null) }

    LaunchedEffect(refreshKey) {
        files = withContext(Dispatchers.IO) { FileUtils.listManagedFiles(context) }
    }

    val visibleFiles = remember(files, query, sortMode) {
        files.filter { file ->
            query.isBlank() || file.name.contains(query, ignoreCase = true) || (file.parentFile?.name ?: "").contains(query, ignoreCase = true)
        }.let { filtered ->
            when (sortMode) {
                FileSortMode.DATE -> filtered.sortedByDescending { it.lastModified() }
                FileSortMode.NAME -> filtered.sortedBy { it.name.lowercase(Locale.getDefault()) }
                FileSortMode.TYPE -> filtered.sortedWith(compareBy<File> { it.extension.lowercase(Locale.getDefault()) }.thenBy { it.name.lowercase(Locale.getDefault()) })
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("File Manager") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.primary)
                    Column(modifier = Modifier.padding(start = 12.dp)) {
                        Text("Managed app files", fontWeight = FontWeight.Bold)
                        Text("Open, share, search, sort, or delete exported scans, PDFs, QR codes, OCR text and signatures.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, null) },
                label = { Text("Search files") }
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = sortMode == FileSortMode.DATE, onClick = { sortMode = FileSortMode.DATE }, label = { Text("Date") })
                FilterChip(selected = sortMode == FileSortMode.NAME, onClick = { sortMode = FileSortMode.NAME }, label = { Text("Name") })
                FilterChip(selected = sortMode == FileSortMode.TYPE, onClick = { sortMode = FileSortMode.TYPE }, label = { Text("Type") })
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (visibleFiles.isEmpty()) {
                EmptyFilesCard(hasFiles = files.isNotEmpty())
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(visibleFiles, key = { it.absolutePath }) { file ->
                        ManagedFileRow(
                            file = file,
                            onOpen = { FileUtils.openFile(context, file, FileUtils.mimeTypeFor(file)) },
                            onShare = { FileUtils.shareFile(context, file, FileUtils.mimeTypeFor(file)) },
                            onDelete = { fileToDelete = file }
                        )
                    }
                }
            }
        }
    }

    fileToDelete?.let { file ->
        AlertDialog(
            onDismissRequest = { fileToDelete = null },
            title = { Text("Delete file?") },
            text = { Text("${file.name} will be removed from ScanMate's app folder.") },
            confirmButton = {
                TextButton(onClick = {
                    val deleted = file.delete()
                    Toast.makeText(context, if (deleted) "File deleted" else "Could not delete file", Toast.LENGTH_SHORT).show()
                    fileToDelete = null
                    refreshKey++
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { fileToDelete = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun EmptyFilesCard(hasFiles: Boolean) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(if (hasFiles) "No matching files" else "No exported files yet", fontWeight = FontWeight.Bold)
            Text(
                if (hasFiles) "Try a different search term or sort option." else "Create a scan, export a PDF, save OCR text, generate QR codes, or make a ZIP backup to see files here.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ManagedFileRow(file: File, onOpen: () -> Unit, onShare: () -> Unit, onDelete: () -> Unit) {
    val date = remember(file.lastModified()) {
        SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(file.lastModified()))
    }
    val sizeKb = remember(file.length()) { maxOf(1, file.length() / 1024) }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(14.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.InsertDriveFile, null, tint = MaterialTheme.colorScheme.primary)
            Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(file.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${file.parentFile?.name ?: "App"} · ${sizeKb}KB · $date", color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onOpen) { Icon(Icons.Default.OpenInNew, "Open") }
            IconButton(onClick = onShare) { Icon(Icons.Default.Share, "Share") }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Delete") }
        }
    }
}

private enum class FileSortMode { DATE, NAME, TYPE }
