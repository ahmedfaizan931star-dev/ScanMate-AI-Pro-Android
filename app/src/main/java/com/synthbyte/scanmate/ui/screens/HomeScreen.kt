package com.synthbyte.scanmate.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TextSnippet
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.synthbyte.scanmate.data.Document
import com.synthbyte.scanmate.data.SettingsRepository
import com.synthbyte.scanmate.data.Page
import com.synthbyte.scanmate.ui.theme.GridArchiveBg
import com.synthbyte.scanmate.ui.theme.GridArchiveIcon
import com.synthbyte.scanmate.ui.theme.GridOcrBg
import com.synthbyte.scanmate.ui.theme.GridOcrIcon
import com.synthbyte.scanmate.ui.theme.GridPdfBg
import com.synthbyte.scanmate.ui.theme.GridPdfIcon
import com.synthbyte.scanmate.ui.viewmodels.rememberDocumentViewModel
import com.synthbyte.scanmate.utils.FileUtils
import com.synthbyte.scanmate.utils.NetworkUtils
import com.synthbyte.scanmate.utils.ZipUtils
import com.synthbyte.scanmate.widgets.WidgetStateStore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


@Composable
fun HomeScreen(
    onNavigateToCamera: () -> Unit,
    onNavigateToDoc: (Long) -> Unit,
    onNavigateToQr: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToAi: () -> Unit,
    onNavigateToZip: () -> Unit,
    onNavigateToFiles: () -> Unit,
    onNavigateToPdfTools: () -> Unit,
    onNavigateToTranslate: () -> Unit,
    onNavigateToVault: () -> Unit
) {
    val context = LocalContext.current
    val settingsRepository = remember { SettingsRepository(context) }
    val defaultWorkspace by settingsRepository.defaultWorkspaceFlow.collectAsState(initial = "Inbox")
    val viewModel = rememberDocumentViewModel()
    val documents by viewModel.allDocuments.collectAsState(initial = emptyList())
    val firstPages by viewModel.firstPages.collectAsState(initial = emptyList())
    val pageCounts by viewModel.pageCountsByDocument.collectAsState(initial = emptyList())
    val pinned by viewModel.pinnedDocuments.collectAsState(initial = emptyList())
    val pageCount by viewModel.pageCount.collectAsState(initial = 0)
    val pdfCount by viewModel.pdfCount.collectAsState(initial = 0)
    var isOnline by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var filterMode by remember { mutableStateOf(DocumentFilterMode.ALL) }
    var sortMode by remember { mutableStateOf(DocumentSortMode.NEWEST) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var pendingSwipeDelete by remember { mutableStateOf<Document?>(null) }
    var homeLoaded by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 30)
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.createDocumentFromUris(
                uris = uris,
                defaultWorkspace = defaultWorkspace,
                onCreated = { newDocId -> onNavigateToDoc(newDocId) },
                onError = { message -> Toast.makeText(context, message, Toast.LENGTH_SHORT).show() }
            )
        }
    }

    val recentForWidget = documents.firstOrNull()
    LaunchedEffect(recentForWidget?.id, recentForWidget?.title, recentForWidget?.workspace, recentForWidget?.category) {
        WidgetStateStore.publishRecentDocument(context, recentForWidget)
    }

    LaunchedEffect(Unit) {
        isOnline = withContext(Dispatchers.IO) { NetworkUtils.isOnline(context) }
        delay(220)
        homeLoaded = true
    }

    val pageCountByDocument = remember(pageCounts) { pageCounts.associate { it.documentId to it.pageCount } }
    val firstPageByDocument = remember(firstPages) { firstPages.associate { it.documentId to it.imagePath } }
    val fileSizeByDocument = remember(firstPages) {
        firstPages.associate { page -> page.documentId to (File(page.imagePath).takeIf { it.exists() }?.length() ?: 0L) }
    }

    val visibleDocuments = remember(documents, query, filterMode, sortMode, pageCountByDocument, fileSizeByDocument) {
        val recentCutoff = System.currentTimeMillis() - 7L * 24L * 60L * 60L * 1000L
        documents.filter { doc ->
            val matchesQuery = query.isBlank() ||
                doc.title.contains(query, ignoreCase = true) ||
                doc.ocrText.orEmpty().contains(query, ignoreCase = true) ||
                doc.category.contains(query, ignoreCase = true) ||
                doc.tags.contains(query, ignoreCase = true) ||
                doc.workspace.contains(query, ignoreCase = true)
            val matchesFilter = when (filterMode) {
                DocumentFilterMode.ALL -> true
                DocumentFilterMode.FAVORITES -> doc.isFavorite
                DocumentFilterMode.PINNED -> doc.isPinned
                DocumentFilterMode.RECENT -> doc.updatedAt >= recentCutoff || doc.timestamp >= recentCutoff
                DocumentFilterMode.OCR -> !doc.ocrText.isNullOrBlank()
                DocumentFilterMode.PDF -> doc.type.equals("PDF", ignoreCase = true)
            }
            matchesQuery && matchesFilter
        }.let { filtered ->
            when (sortMode) {
                DocumentSortMode.NEWEST -> filtered.sortedWith(compareByDescending<Document> { it.isPinned }.thenByDescending { it.updatedAt }.thenByDescending { it.timestamp })
                DocumentSortMode.OLDEST -> filtered.sortedBy { it.timestamp }
                DocumentSortMode.NAME -> filtered.sortedBy { it.title.lowercase(Locale.getDefault()) }
                DocumentSortMode.LARGEST -> filtered.sortedByDescending { fileSizeByDocument[it.id] ?: 0L }
                DocumentSortMode.MOST_PAGES -> filtered.sortedByDescending { pageCountByDocument[it.id] ?: 0 }
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                NavigationBarItem(
                    selected = true,
                    onClick = {},
                    icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                    label = { Text("Home") }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = onNavigateToFiles,
                    icon = { Icon(Icons.Default.Folder, contentDescription = "Files") },
                    label = { Text("Files") }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = onNavigateToCamera,
                    icon = { Icon(Icons.Default.CameraAlt, contentDescription = "Scan") },
                    label = { Text("Scan") }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = onNavigateToQr,
                    icon = { Icon(Icons.Default.Apps, contentDescription = "Tools") },
                    label = { Text("Tools") }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = onNavigateToAi,
                    icon = { Icon(Icons.Default.AutoAwesome, contentDescription = "AI") },
                    label = { Text("AI") }
                )
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
            contentPadding = PaddingValues(top = 18.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item {
                HeaderRow(onNavigateToAi, onNavigateToSettings)
            }

            item {
                OnlineStatusCard(isOnline = isOnline)
            }

            item {
                BeginnerWorkflowCard(
                    onScan = onNavigateToCamera,
                    onImport = { imagePickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    onAi = onNavigateToAi,
                    onPdf = onNavigateToPdfTools,
                    onTranslate = onNavigateToTranslate
                )
            }

            item {
                Card(
                    onClick = onNavigateToCamera,
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .background(
                                Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, Color(0xFF4D8DFF)))
                            )
                            .padding(24.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("New Document Scan", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 21.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("CameraX scan, OCR, PDF export, ZIP backup and optional AI tools.", color = Color.White.copy(alpha = 0.86f), fontSize = 14.sp, lineHeight = 19.sp)
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Box(
                            modifier = Modifier.size(54.dp).background(Color.White.copy(alpha = 0.20f), RoundedCornerShape(18.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.CameraAlt, "Scan", tint = Color.White, modifier = Modifier.size(30.dp))
                        }
                    }
                }
            }

            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DashboardStat("Docs", documents.size.toString(), Icons.Default.Description, Modifier.weight(1f))
                    DashboardStat("Pages", pageCount.toString(), Icons.Default.Image, Modifier.weight(1f))
                    DashboardStat("Pinned", pinned.size.toString(), Icons.Default.PushPin, Modifier.weight(1f))
                }
            }

            item {
                ScanAnalyticsCard(
                    documents = documents,
                    pageCount = pageCount,
                    pdfCount = pdfCount,
                    isOnline = isOnline
                )
            }

            item {
                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    ToolButton("PDF", Icons.Default.PictureAsPdf, GridPdfBg, GridPdfIcon) { onNavigateToPdfTools() }
                    ToolButton("OCR", Icons.Default.TextSnippet, GridOcrBg, GridOcrIcon) { onNavigateToCamera() }
                    ToolButton("QR", Icons.Default.QrCodeScanner, GridOcrBg, GridOcrIcon) { onNavigateToQr() }
                    ToolButton("ZIP", Icons.Default.FolderZip, GridArchiveBg, GridArchiveIcon) { onNavigateToZip() }
                    ToolButton("Translate", Icons.Default.Translate, GridOcrBg, GridOcrIcon) { onNavigateToTranslate() }
                    ToolButton("Vault", Icons.Default.Lock, GridArchiveBg, GridArchiveIcon) { onNavigateToVault() }
                }
            }

            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    label = { Text("Search documents or OCR text") },
                    shape = RoundedCornerShape(18.dp)
                )
            }

            item {
                DocumentFilterSortBar(
                    filterMode = filterMode,
                    sortMode = sortMode,
                    selectionMode = selectionMode,
                    onFilterChange = { filterMode = it },
                    onSortChange = { sortMode = it },
                    onToggleSelection = {
                        selectionMode = !selectionMode
                        selectedIds = emptySet()
                    }
                )
            }

            if (selectionMode) {
                item {
                    BulkActionCard(
                        selectedCount = selectedIds.size,
                        onFavorite = {
                            viewModel.setFavoriteBulk(selectedIds.toList(), true) { selectedIds = emptySet(); selectionMode = false }
                        },
                        onPin = {
                            viewModel.setPinnedBulk(selectedIds.toList(), true) { selectedIds = emptySet(); selectionMode = false }
                        },
                        onMoveInbox = {
                            viewModel.setWorkspace(selectedIds.toList(), "Inbox") { selectedIds = emptySet(); selectionMode = false }
                        },
                        onExportZip = {
                            scope.launch {
                                val selectedPages = viewModel.getPagesForDocumentsOnce(selectedIds.toList())
                                val selectedFiles = selectedPages.map { File(it.imagePath) }
                                val zip = ZipUtils.createZip(context, selectedFiles, "ScanMate_Selected_${System.currentTimeMillis()}")
                                if (zip != null) FileUtils.shareFile(context, zip, "application/zip")
                                else Toast.makeText(context, "No selected page files to export", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onDelete = { if (selectedIds.isNotEmpty()) showDeleteConfirm = true },
                        onClear = { selectedIds = emptySet(); selectionMode = false }
                    )
                }
            }

            if (!homeLoaded) {
                item { HomeLoadingSkeleton() }
            } else if (visibleDocuments.isEmpty()) {
                item {
                    EmptyDocumentState(
                        hasDocuments = documents.isNotEmpty(),
                        onImport = { imagePickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        onScan = onNavigateToCamera
                    )
                }
            } else {
                item {
                    SectionHeader(filterMode.sectionTitle, "${visibleDocuments.size} shown")
                }
                items(visibleDocuments, key = { it.id }) { doc ->
                    SwipeableDocumentListItem(
                        doc = doc,
                        thumbnailPath = firstPageByDocument[doc.id],
                        pageCount = pageCountByDocument[doc.id] ?: 0,
                        fileSizeBytes = fileSizeByDocument[doc.id] ?: 0L,
                        selectionMode = selectionMode,
                        selected = doc.id in selectedIds,
                        onClick = { if (selectionMode) selectedIds = selectedIds.toggle(doc.id) else onNavigateToDoc(doc.id) },
                        onFavorite = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.toggleFavorite(doc)
                        },
                        onPin = { viewModel.togglePinned(doc) },
                        onSelectToggle = { selectedIds = selectedIds.toggle(doc.id) },
                        onDeleteRequest = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            pendingSwipeDelete = doc
                        }
                    )
                }
            }

            item {
                Card(
                    onClick = onNavigateToAi,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.AutoAwesome, "AI", tint = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("GEMINI ASSISTANT", style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 0.05.em))
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("Optional online AI: summarize documents, clean OCR and generate study material.", style = MaterialTheme.typography.bodyMedium)
                        }
                        Icon(Icons.Default.ChevronRight, "Go", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete selected documents?") },
            text = { Text("${selectedIds.size} document(s) will be removed from ScanMate AI Pro. This cannot be undone.") },
            confirmButton = {
                Button(onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.deleteDocuments(selectedIds.toList()) {
                        selectedIds = emptySet()
                        selectionMode = false
                        showDeleteConfirm = false
                        scope.launch { snackbarHostState.showSnackbar("Selected documents deleted") }
                    }
                }) { Text("Delete") }
            },
            dismissButton = { OutlinedButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } }
        )
    }

    pendingSwipeDelete?.let { doc ->
        AlertDialog(
            onDismissRequest = { pendingSwipeDelete = null },
            title = { Text("Delete this document?") },
            text = { Text("${doc.title} will be removed. You can undo immediately after deletion.") },
            confirmButton = {
                Button(onClick = {
                    pendingSwipeDelete = null
                    scope.launch {
                        val pagesSnapshot = viewModel.getPagesForDocumentsOnce(listOf(doc.id))
                        viewModel.deleteDocument(doc.id) {
                            scope.launch {
                                val result = snackbarHostState.showSnackbar(
                                    message = "Deleted ${doc.title}",
                                    actionLabel = "Undo",
                                    withDismissAction = true
                                )
                                if (result == SnackbarResult.ActionPerformed) {
                                    viewModel.restoreDocument(doc, pagesSnapshot)
                                }
                            }
                        }
                    }
                }) { Text("Delete") }
            },
            dismissButton = { OutlinedButton(onClick = { pendingSwipeDelete = null }) { Text("Cancel") } }
        )
    }

}

private enum class DocumentFilterMode(val label: String, val sectionTitle: String) {
    ALL("All", "RECENT FILES"),
    FAVORITES("Favorites", "FAVORITE DOCUMENTS"),
    PINNED("Pinned", "PINNED DOCUMENTS"),
    RECENT("Recent", "THIS WEEK"),
    OCR("OCR", "OCR DOCUMENTS"),
    PDF("PDF", "PDF DOCUMENTS")
}

private enum class DocumentSortMode(val label: String) {
    NEWEST("Newest"),
    OLDEST("Oldest"),
    NAME("Name A-Z"),
    LARGEST("Largest"),
    MOST_PAGES("Most pages")
}

private fun Set<Long>.toggle(id: Long): Set<Long> = if (id in this) this - id else this + id

private fun Long.toSizeLabel(): String = when {
    this <= 0L -> "0 KB"
    this < 1024L * 1024L -> "${(this / 1024L).coerceAtLeast(1L)} KB"
    else -> String.format(Locale.getDefault(), "%.1f MB", this / (1024f * 1024f))
}

@Composable
private fun DocumentFilterSortBar(
    filterMode: DocumentFilterMode,
    sortMode: DocumentSortMode,
    selectionMode: Boolean,
    onFilterChange: (DocumentFilterMode) -> Unit,
    onSortChange: (DocumentSortMode) -> Unit,
    onToggleSelection: () -> Unit
) {
    var sortMenuOpen by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            DocumentFilterMode.entries.forEach { mode ->
                FilterChip(
                    selected = filterMode == mode,
                    onClick = { onFilterChange(mode) },
                    label = { Text(mode.label) }
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Box {
                OutlinedButton(onClick = { sortMenuOpen = true }) {
                    Icon(Icons.Default.Sort, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(sortMode.label)
                }
                DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                    DocumentSortMode.entries.forEach { mode ->
                        DropdownMenuItem(
                            text = { Text(mode.label) },
                            onClick = {
                                onSortChange(mode)
                                sortMenuOpen = false
                            }
                        )
                    }
                }
            }
            OutlinedButton(onClick = onToggleSelection) {
                Icon(if (selectionMode) Icons.Default.Done else Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (selectionMode) "Done" else "Select")
            }
        }
    }
}

@Composable
private fun BulkActionCard(
    selectedCount: Int,
    onFavorite: () -> Unit,
    onPin: () -> Unit,
    onMoveInbox: () -> Unit,
    onExportZip: () -> Unit,
    onDelete: () -> Unit,
    onClear: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.52f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("$selectedCount selected", fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onFavorite, enabled = selectedCount > 0, modifier = Modifier.weight(1f)) { Text("Favorite") }
                OutlinedButton(onClick = onPin, enabled = selectedCount > 0, modifier = Modifier.weight(1f)) { Text("Pin") }
                OutlinedButton(onClick = onMoveInbox, enabled = selectedCount > 0, modifier = Modifier.weight(1f)) { Text("Inbox") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = onExportZip, enabled = selectedCount > 0, modifier = Modifier.weight(1f)) { Text("ZIP") }
                OutlinedButton(onClick = onDelete, enabled = selectedCount > 0, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Delete")
                }
                OutlinedButton(onClick = onClear, modifier = Modifier.weight(1f)) { Text("Clear") }
            }
        }
    }
}

@Composable
private fun HeaderRow(onNavigateToAi: () -> Unit, onNavigateToSettings: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column {
            Text("PROFESSIONAL SUITE", style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.16.em, color = MaterialTheme.colorScheme.primary))
            Text("ScanMate AI Pro", style = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp, color = MaterialTheme.colorScheme.onBackground))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(onClick = onNavigateToAi, modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant, CircleShape).size(42.dp)) {
                Icon(Icons.Default.AutoAwesome, "AI", tint = MaterialTheme.colorScheme.onBackground)
            }
            IconButton(onClick = onNavigateToSettings, modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant, CircleShape).size(42.dp)) {
                Icon(Icons.Default.Settings, "Settings", tint = MaterialTheme.colorScheme.onBackground)
            }
        }
    }
}

@Composable
private fun OnlineStatusCard(isOnline: Boolean) {
    val label = if (isOnline) "Online tools ready" else "Offline safe mode"
    val detail = if (isOnline) "Offline Ready · OCR Ready · PDF Ready · AI works after Settings setup" else "Offline Ready · OCR Ready · PDF Ready · AI paused"
    AssistChip(
        onClick = {},
        label = { Text("$label · $detail") },
        leadingIcon = { Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp)) }
    )
}


@Composable
private fun BeginnerWorkflowCard(
    onScan: () -> Unit,
    onImport: () -> Unit,
    onAi: () -> Unit,
    onPdf: () -> Unit,
    onTranslate: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Beginner quick flow", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Text(
                "Scan or import first, run OCR, translate/clean text, export PDF/DOCX, then use optional AI for summaries.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onScan, modifier = Modifier.weight(1f)) { Text("Scan") }
                OutlinedButton(onClick = onImport, modifier = Modifier.weight(1f)) { Text("Gallery") }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onPdf, modifier = Modifier.weight(1f)) { Text("PDF Tools") }
                OutlinedButton(onClick = onTranslate, modifier = Modifier.weight(1f)) { Text("Translate") }
                Button(onClick = onAi, modifier = Modifier.weight(1f)) { Text("AI") }
            }
        }
    }
}


@Composable
private fun ScanAnalyticsCard(documents: List<Document>, pageCount: Int, pdfCount: Int, isOnline: Boolean) {
    val ocrWords = remember(documents) {
        documents.sumOf { it.ocrText.orEmpty().split(Regex("\\s+")).count { word -> word.isNotBlank() } }
    }
    val weekStart = remember { System.currentTimeMillis() - 7L * 24L * 60L * 60L * 1000L }
    val weeklyScans = remember(documents) { documents.count { it.timestamp >= weekStart } }
    val mostUsedTool = when {
        ocrWords > 250 -> "OCR"
        pdfCount > 0 -> "PDF"
        documents.isNotEmpty() -> "Scanner"
        else -> "Start scanning"
    }
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Scan history analytics", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DashboardStat("PDFs", pdfCount.toString(), Icons.Default.PictureAsPdf, Modifier.weight(1f))
                DashboardStat("OCR words", ocrWords.toString(), Icons.Default.TextSnippet, Modifier.weight(1f))
                DashboardStat("This week", weeklyScans.toString(), Icons.Default.CameraAlt, Modifier.weight(1f))
            }
            Text("Most used tool: $mostUsedTool · AI mode: ${if (isOnline) "online-ready" else "offline-safe"}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun DashboardStat(label: String, value: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = modifier) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Text(value, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1)
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, maxLines = 1)
        }
    }
}

@Composable
fun ToolButton(label: String, icon: ImageVector, bgColor: Color, tint: Color, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onClick() }) {
        Box(modifier = Modifier.size(64.dp).background(bgColor, RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(25.dp))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
    }
}

@Composable
private fun SectionHeader(title: String, meta: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
        Text(title, style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.06.em, color = MaterialTheme.colorScheme.onSurfaceVariant))
        Text(meta, style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary))
    }
}

@Composable
private fun HomeLoadingSkeleton() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        repeat(3) { index ->
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(14.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(58.dp).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f), RoundedCornerShape(16.dp)))
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                        Box(modifier = Modifier.fillMaxWidth(if (index == 0) 0.72f else 0.55f).height(14.dp).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f), RoundedCornerShape(8.dp)))
                        Box(modifier = Modifier.fillMaxWidth(0.42f).height(10.dp).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), RoundedCornerShape(8.dp)))
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyDocumentState(hasDocuments: Boolean, onImport: () -> Unit, onScan: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(if (hasDocuments) Icons.Default.Search else Icons.Default.AddPhotoAlternate, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(42.dp))
            Spacer(modifier = Modifier.height(10.dp))
            Text(if (hasDocuments) "No matching documents" else "No scans yet", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(
                if (hasDocuments) "Try a different search term or remove the favorite filter." else "Start with a camera scan or import images from your gallery.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onImport) { Text("Gallery") }
                Button(onClick = onScan) { Text("Scan now") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableDocumentListItem(
    doc: Document,
    thumbnailPath: String?,
    pageCount: Int,
    fileSizeBytes: Long,
    selectionMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onFavorite: () -> Unit,
    onPin: () -> Unit,
    onSelectToggle: () -> Unit,
    onDeleteRequest: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    if (!selectionMode) onFavorite()
                    false
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    if (!selectionMode) onDeleteRequest()
                    false
                }
                SwipeToDismissBoxValue.Settled -> false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = !selectionMode,
        enableDismissFromEndToStart = !selectionMode,
        backgroundContent = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(20.dp))
                    .padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Favorite, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Favorite", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Delete", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.error)
                    Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    ) {
        DocumentListItem(
            doc = doc,
            thumbnailPath = thumbnailPath,
            pageCount = pageCount,
            fileSizeBytes = fileSizeBytes,
            selectionMode = selectionMode,
            selected = selected,
            onClick = onClick,
            onFavorite = onFavorite,
            onPin = onPin,
            onSelectToggle = onSelectToggle
        )
    }
}

@Composable
fun DocumentListItem(
    doc: Document,
    thumbnailPath: String?,
    pageCount: Int,
    fileSizeBytes: Long,
    selectionMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onFavorite: () -> Unit,
    onPin: () -> Unit,
    onSelectToggle: () -> Unit
) {
    val dateString = SimpleDateFormat("dd MMM, yyyy", Locale.getDefault()).format(Date(doc.timestamp))
    val wordCount = remember(doc.ocrText) { doc.ocrText.orEmpty().split(Regex("\\s+")).count { it.isNotBlank() } }
    val qualityLabel = when {
        wordCount > 250 -> "Rich OCR"
        wordCount > 40 -> "OCR Ready"
        pageCount > 0 -> "Scanned"
        else -> doc.type
    }
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.60f) else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.55f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
        ),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(14.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            val iconBg = if (doc.type == "PDF") GridPdfBg else GridOcrBg
            val iconTint = if (doc.type == "PDF") GridPdfIcon else GridOcrIcon
            Box(modifier = Modifier.size(58.dp).background(iconBg, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                if (!thumbnailPath.isNullOrBlank() && File(thumbnailPath).exists()) {
                    AsyncImage(
                        model = File(thumbnailPath),
                        contentDescription = "Document thumbnail",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(if (doc.type == "PDF") Icons.Default.PictureAsPdf else Icons.Default.Description, contentDescription = null, tint = iconTint)
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(doc.title, style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(dateString, style = TextStyle(fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("$pageCount page${if (pageCount == 1) "" else "s"}", style = TextStyle(fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (doc.isPinned) Icon(Icons.Default.PushPin, contentDescription = "Pinned", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                    if (doc.isFavorite) Icon(Icons.Default.Favorite, contentDescription = "Favorite", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                    if (!doc.ocrText.isNullOrEmpty()) Icon(Icons.Default.TextSnippet, contentDescription = "Has OCR", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }
            if (selectionMode) {
                IconButton(onClick = onSelectToggle) {
                    Icon(Icons.Default.Done, contentDescription = "Select", tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                IconButton(onClick = onPin) {
                    Icon(Icons.Default.PushPin, contentDescription = "Pin", tint = if (doc.isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onFavorite) {
                    Icon(if (doc.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, contentDescription = "Favorite", tint = if (doc.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun RowScope.BottomNavItem(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f).clickable { onClick() }) {
        Box(
            modifier = Modifier.padding(vertical = 4.dp).background(
                color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                shape = CircleShape
            ).padding(horizontal = 18.dp, vertical = 5.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = label, tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
