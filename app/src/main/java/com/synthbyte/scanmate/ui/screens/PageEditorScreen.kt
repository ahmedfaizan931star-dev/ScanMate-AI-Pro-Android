package com.synthbyte.scanmate.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.TextSnippet
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.ImageNotSupported
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SwapHoriz
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.synthbyte.scanmate.ui.viewmodels.rememberDocumentViewModel
import com.synthbyte.scanmate.utils.FileUtils
import com.synthbyte.scanmate.utils.FilterType
import com.synthbyte.scanmate.utils.OcrHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageEditorScreen(docId: Long, pageId: Long, onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val viewModel = rememberDocumentViewModel()
    val page by remember(pageId) { viewModel.getPage(pageId) }.collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    var workingBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var sourcePath by remember { mutableStateOf<String?>(null) }
    var selectedFilter by remember { mutableStateOf(FilterType.ORIGINAL) }
    var showCropDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showWatermarkDialog by remember { mutableStateOf(false) }
    var showNoteDialog by remember { mutableStateOf(false) }
    var showPerspectiveDialog by remember { mutableStateOf(false) }
    var watermarkText by remember { mutableStateOf("ScanMate AI Pro") }
    var noteText by remember { mutableStateOf("Reviewed") }
    var isProcessing by remember { mutableStateOf(false) }
    var changeVersion by remember { mutableIntStateOf(0) }
    val undoStack = remember { mutableStateListOf<android.graphics.Bitmap>() }
    val redoStack = remember { mutableStateListOf<android.graphics.Bitmap>() }

    fun pushUndoSnapshot() {
        workingBitmap?.let { bitmap ->
            undoStack.add(bitmap.copy(android.graphics.Bitmap.Config.ARGB_8888, false))
            if (undoStack.size > 3) undoStack.removeAt(0)
            redoStack.clear()
        }
    }

    val replaceLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                isProcessing = true
                val file = withContext(Dispatchers.IO) { FileUtils.copyUriToImageFile(context, uri) }
                val currentPage = page
                if (file != null && currentPage != null) {
                    viewModel.updatePageImage(currentPage.id, file.absolutePath)
                    sourcePath = null
                    workingBitmap = null
                    Toast.makeText(context, "Page replaced", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Could not replace this page", Toast.LENGTH_SHORT).show()
                }
                isProcessing = false
            }
        }
    }

    LaunchedEffect(page?.imagePath, changeVersion) {
        val path = page?.imagePath
        if (path != null && path != sourcePath) {
            sourcePath = path
            selectedFilter = FilterType.ORIGINAL
            undoStack.clear()
            redoStack.clear()
            workingBitmap = withContext(Dispatchers.IO) { FileUtils.decodeSampledBitmap(path, 2600, 2600) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Page Editor") },
                navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = {
                        val path = page?.imagePath ?: return@IconButton
                        scope.launch {
                            isProcessing = true
                            workingBitmap = withContext(Dispatchers.IO) { FileUtils.decodeSampledBitmap(path, 2600, 2600) }
                            selectedFilter = FilterType.ORIGINAL
                            undoStack.clear()
                            redoStack.clear()
                            isProcessing = false
                        }
                    }) { Icon(Icons.Default.Restore, "Reset") }
                    IconButton(onClick = {
                        val bitmap = workingBitmap ?: return@IconButton
                        val currentPage = page ?: return@IconButton
                        scope.launch {
                            isProcessing = true
                            val file = FileUtils.saveEditedBitmap(context, bitmap, "PAGE_${currentPage.id}")
                            if (file != null) {
                                viewModel.updatePageImage(currentPage.id, file.absolutePath)
                                Toast.makeText(context, "Edited page saved", Toast.LENGTH_SHORT).show()
                                sourcePath = null
                                changeVersion++
                            } else {
                                Toast.makeText(context, "Could not save edited page", Toast.LENGTH_SHORT).show()
                            }
                            isProcessing = false
                        }
                    }) { Icon(Icons.Default.Save, "Save") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (isProcessing) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

            val bitmap = workingBitmap
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Edited page preview",
                        modifier = Modifier.fillMaxWidth().height(460.dp).padding(12.dp),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth().height(260.dp).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        if (page == null) CircularProgressIndicator() else Icon(Icons.Default.ImageNotSupported, null, modifier = Modifier.size(42.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(if (page == null) "Loading page..." else "This page image could not be loaded")
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        val previous = if (undoStack.isNotEmpty()) undoStack.removeAt(undoStack.lastIndex) else null
                        if (previous != null) {
                            workingBitmap?.let { redoStack.add(it.copy(android.graphics.Bitmap.Config.ARGB_8888, false)) }
                            workingBitmap = previous
                            selectedFilter = FilterType.ORIGINAL
                        }
                    },
                    enabled = undoStack.isNotEmpty(),
                    modifier = Modifier.weight(1f)
                ) { Text("Undo") }
                OutlinedButton(
                    onClick = {
                        val next = if (redoStack.isNotEmpty()) redoStack.removeAt(redoStack.lastIndex) else null
                        if (next != null) {
                            workingBitmap?.let { undoStack.add(it.copy(android.graphics.Bitmap.Config.ARGB_8888, false)) }
                            workingBitmap = next
                        }
                    },
                    enabled = redoStack.isNotEmpty(),
                    modifier = Modifier.weight(1f)
                ) { Text("Redo") }
            }

            ToolSectionTitle("Edit tools")
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    pushUndoSnapshot()
                    workingBitmap = workingBitmap?.let { FileUtils.rotateBitmap(it, -90f) }
                }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.RotateLeft, null)
                    Text(" Left")
                }
                OutlinedButton(onClick = {
                    pushUndoSnapshot()
                    workingBitmap = workingBitmap?.let { FileUtils.rotateBitmap(it, 90f) }
                }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.RotateRight, null)
                    Text(" Right")
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { showCropDialog = true }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Crop, null)
                    Text(" Manual crop")
                }
                OutlinedButton(onClick = {
                    scope.launch {
                        val current = workingBitmap ?: return@launch
                        pushUndoSnapshot()
                        isProcessing = true
                        workingBitmap = withContext(Dispatchers.Default) { FileUtils.autoCropDocument(current) }
                        isProcessing = false
                    }
                }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.FilterAlt, null)
                    Text(" Auto crop")
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { showPerspectiveDialog = true }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Crop, null)
                    Text(" Corners")
                }
                OutlinedButton(onClick = {
                    val current = workingBitmap ?: return@OutlinedButton
                    pushUndoSnapshot()
                    workingBitmap = FileUtils.applyFilter(FileUtils.autoCropDocument(current), FilterType.ENHANCED_COLOR)
                    selectedFilter = FilterType.ENHANCED_COLOR
                }, modifier = Modifier.weight(1f)) {
                    Text("Smart enhance")
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { showWatermarkDialog = true }, modifier = Modifier.weight(1f)) {
                    Text("Watermark")
                }
                OutlinedButton(onClick = { showNoteDialog = true }, modifier = Modifier.weight(1f)) {
                    Text("Text note")
                }
            }

            ToolSectionTitle("CamScanner-style filters")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                items(FilterType.entries) { filter ->
                    FilterChip(
                        selected = selectedFilter == filter,
                        onClick = {
                            val current = workingBitmap ?: return@FilterChip
                            scope.launch {
                                pushUndoSnapshot()
                                isProcessing = true
                                workingBitmap = withContext(Dispatchers.Default) { FileUtils.applyFilter(current, filter) }
                                selectedFilter = filter
                                isProcessing = false
                            }
                        },
                        label = { Text(filter.label) }
                    )
                }
            }

            ToolSectionTitle("Page actions")
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    replaceLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.SwapHoriz, null)
                    Text(" Replace")
                }
                OutlinedButton(onClick = {
                    val currentPage = page ?: return@OutlinedButton
                    viewModel.duplicatePage(
                        documentId = docId,
                        sourcePath = currentPage.imagePath,
                        sourcePageId = currentPage.id,
                        onDone = { Toast.makeText(context, "Page duplicated", Toast.LENGTH_SHORT).show() },
                        onError = { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
                    )
                }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.ContentCopy, null)
                    Text(" Duplicate")
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    val currentPage = page ?: return@OutlinedButton
                    viewModel.movePage(docId, currentPage.id, -1) {
                        Toast.makeText(context, "Page moved", Toast.LENGTH_SHORT).show()
                    }
                }, modifier = Modifier.weight(1f)) { Text("Move up") }
                OutlinedButton(onClick = {
                    val currentPage = page ?: return@OutlinedButton
                    viewModel.movePage(docId, currentPage.id, 1) {
                        Toast.makeText(context, "Page moved", Toast.LENGTH_SHORT).show()
                    }
                }, modifier = Modifier.weight(1f)) { Text("Move down") }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    val currentPage = page ?: return@OutlinedButton
                    scope.launch {
                        isProcessing = true
                        val text = withContext(Dispatchers.IO) { OcrHelper.extractTextFromFile(context, java.io.File(currentPage.imagePath)) }
                        isProcessing = false
                        if (text.isBlank() || text.startsWith("OCR failed", ignoreCase = true)) {
                            Toast.makeText(context, "No readable text found on this page", Toast.LENGTH_SHORT).show()
                        } else {
                            clipboardManager.setPrimaryClip(ClipData.newPlainText("Page OCR", text))
                            viewModel.savePageOcrText(docId, currentPage.pageOrder, text)
                            Toast.makeText(context, "Page OCR copied and saved", Toast.LENGTH_SHORT).show()
                        }
                    }
                }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.TextSnippet, null)
                    Text(" OCR page")
                }
                OutlinedButton(onClick = { showDeleteDialog = true }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Delete, null)
                    Text(" Delete")
                }
            }
            AssistChip(onClick = {}, label = { Text("Tip: use Save after rotate, crop, or filters to write the edited bitmap back to the document.") })
        }
    }

    if (showCropDialog) {
        ManualCropDialog(
            onDismiss = { showCropDialog = false },
            onApply = { left, top, right, bottom ->
                pushUndoSnapshot()
                workingBitmap = workingBitmap?.let { FileUtils.cropBitmapNormalized(it, left, top, right, bottom) }
                showCropDialog = false
            }
        )
    }

    if (showWatermarkDialog) {
        AlertDialog(
            onDismissRequest = { showWatermarkDialog = false },
            title = { Text("Add watermark") },
            text = {
                OutlinedTextField(
                    value = watermarkText,
                    onValueChange = { watermarkText = it },
                    label = { Text("Watermark text") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    pushUndoSnapshot()
                    workingBitmap = workingBitmap?.let { FileUtils.drawWatermarkOnBitmap(it, watermarkText) }
                    showWatermarkDialog = false
                }) { Text("Apply") }
            },
            dismissButton = { TextButton(onClick = { showWatermarkDialog = false }) { Text("Cancel") } }
        )
    }

    if (showNoteDialog) {
        AlertDialog(
            onDismissRequest = { showNoteDialog = false },
            title = { Text("Add text note") },
            text = {
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text("Note text") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
            },
            confirmButton = {
                Button(onClick = {
                    pushUndoSnapshot()
                    workingBitmap = workingBitmap?.let { FileUtils.drawNoteStampOnBitmap(it, noteText) }
                    showNoteDialog = false
                }) { Text("Apply") }
            },
            dismissButton = { TextButton(onClick = { showNoteDialog = false }) { Text("Cancel") } }
        )
    }

    if (showPerspectiveDialog) {
        PerspectiveDialog(
            onDismiss = { showPerspectiveDialog = false },
            onApply = { tlx, tly, trx, tryY, brx, bry, blx, bly ->
                pushUndoSnapshot()
                workingBitmap = workingBitmap?.let { FileUtils.perspectiveCorrectBitmapNormalized(it, tlx, tly, trx, tryY, brx, bry, blx, bly) }
                showPerspectiveDialog = false
            }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete page?") },
            text = { Text("This removes this page from the document. Other pages will be re-numbered safely.") },
            confirmButton = {
                Button(onClick = {
                    val currentPage = page ?: return@Button
                    viewModel.deletePage(docId, currentPage.id) {
                        Toast.makeText(context, "Page deleted", Toast.LENGTH_SHORT).show()
                        showDeleteDialog = false
                        onNavigateBack()
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun ToolSectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
}

@Composable
private fun ManualCropDialog(onDismiss: () -> Unit, onApply: (Float, Float, Float, Float) -> Unit) {
    var left by remember { mutableFloatStateOf(0f) }
    var top by remember { mutableFloatStateOf(0f) }
    var right by remember { mutableFloatStateOf(0f) }
    var bottom by remember { mutableFloatStateOf(0f) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Manual crop") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CropSlider("Left", left) { left = it }
                CropSlider("Top", top) { top = it }
                CropSlider("Right", right) { right = it }
                CropSlider("Bottom", bottom) { bottom = it }
                Text("Crop values are gentle by default so document text stays readable.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { Button(onClick = { onApply(left, top, right, bottom) }) { Text("Apply") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun PerspectiveDialog(
    onDismiss: () -> Unit,
    onApply: (Float, Float, Float, Float, Float, Float, Float, Float) -> Unit
) {
    var topLeftX by remember { mutableFloatStateOf(0.04f) }
    var topLeftY by remember { mutableFloatStateOf(0.04f) }
    var topRightX by remember { mutableFloatStateOf(0.04f) }
    var topRightY by remember { mutableFloatStateOf(0.04f) }
    var bottomRightX by remember { mutableFloatStateOf(0.04f) }
    var bottomRightY by remember { mutableFloatStateOf(0.04f) }
    var bottomLeftX by remember { mutableFloatStateOf(0.04f) }
    var bottomLeftY by remember { mutableFloatStateOf(0.04f) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Manual corner correction") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Drag the corner handles first. Sliders remain below for precise advanced tuning.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                CornerAdjustPreview(
                    topLeftX = topLeftX,
                    topLeftY = topLeftY,
                    topRightX = topRightX,
                    topRightY = topRightY,
                    bottomRightX = bottomRightX,
                    bottomRightY = bottomRightY,
                    bottomLeftX = bottomLeftX,
                    bottomLeftY = bottomLeftY,
                    onTopLeft = { x, y -> topLeftX = x; topLeftY = y },
                    onTopRight = { x, y -> topRightX = x; topRightY = y },
                    onBottomRight = { x, y -> bottomRightX = x; bottomRightY = y },
                    onBottomLeft = { x, y -> bottomLeftX = x; bottomLeftY = y }
                )
                CropSlider("Top-left X", topLeftX) { topLeftX = it }
                CropSlider("Top-left Y", topLeftY) { topLeftY = it }
                CropSlider("Top-right X", topRightX) { topRightX = it }
                CropSlider("Top-right Y", topRightY) { topRightY = it }
                CropSlider("Bottom-right X", bottomRightX) { bottomRightX = it }
                CropSlider("Bottom-right Y", bottomRightY) { bottomRightY = it }
                CropSlider("Bottom-left X", bottomLeftX) { bottomLeftX = it }
                CropSlider("Bottom-left Y", bottomLeftY) { bottomLeftY = it }
            }
        },
        confirmButton = { Button(onClick = { onApply(topLeftX, topLeftY, topRightX, topRightY, bottomRightX, bottomRightY, bottomLeftX, bottomLeftY) }) { Text("Apply") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}


@Composable
private fun CornerAdjustPreview(
    topLeftX: Float,
    topLeftY: Float,
    topRightX: Float,
    topRightY: Float,
    bottomRightX: Float,
    bottomRightY: Float,
    bottomLeftX: Float,
    bottomLeftY: Float,
    onTopLeft: (Float, Float) -> Unit,
    onTopRight: (Float, Float) -> Unit,
    onBottomRight: (Float, Float) -> Unit,
    onBottomLeft: (Float, Float) -> Unit
) {
    val density = LocalDensity.current
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp))
            .padding(18.dp)
    ) {
        val widthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
        val heightPx = with(density) { maxHeight.toPx() }.coerceAtLeast(1f)
        val handlePx = with(density) { 22.dp.toPx() }

        fun handleOffset(xPercent: Float, yPercent: Float): IntOffset = IntOffset(
            (xPercent.coerceIn(0f, 1f) * widthPx - handlePx / 2f).roundToInt(),
            (yPercent.coerceIn(0f, 1f) * heightPx - handlePx / 2f).roundToInt()
        )

        Box(
            modifier = Modifier
                .matchParentSize()
                .padding(18.dp)
                .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.65f), RoundedCornerShape(10.dp))
        )

        CornerHandle(
            label = "TL",
            offset = handleOffset(topLeftX, topLeftY),
            onDrag = { dx, dy -> onTopLeft(((topLeftX * widthPx + dx) / widthPx).coerceIn(0f, 0.35f), ((topLeftY * heightPx + dy) / heightPx).coerceIn(0f, 0.35f)) }
        )
        CornerHandle(
            label = "TR",
            offset = handleOffset(1f - topRightX, topRightY),
            onDrag = { dx, dy -> onTopRight(((topRightX * widthPx - dx) / widthPx).coerceIn(0f, 0.35f), ((topRightY * heightPx + dy) / heightPx).coerceIn(0f, 0.35f)) }
        )
        CornerHandle(
            label = "BR",
            offset = handleOffset(1f - bottomRightX, 1f - bottomRightY),
            onDrag = { dx, dy -> onBottomRight(((bottomRightX * widthPx - dx) / widthPx).coerceIn(0f, 0.35f), ((bottomRightY * heightPx - dy) / heightPx).coerceIn(0f, 0.35f)) }
        )
        CornerHandle(
            label = "BL",
            offset = handleOffset(bottomLeftX, 1f - bottomLeftY),
            onDrag = { dx, dy -> onBottomLeft(((bottomLeftX * widthPx + dx) / widthPx).coerceIn(0f, 0.35f), ((bottomLeftY * heightPx - dy) / heightPx).coerceIn(0f, 0.35f)) }
        )
    }
}

@Composable
private fun CornerHandle(label: String, offset: IntOffset, onDrag: (Float, Float) -> Unit) {
    Box(
        modifier = Modifier
            .offset { offset }
            .size(34.dp)
            .background(MaterialTheme.colorScheme.primary, CircleShape)
            .border(2.dp, MaterialTheme.colorScheme.onPrimary, CircleShape)
            .pointerInput(label) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount.x, dragAmount.y)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun CropSlider(label: String, value: Float, onChange: (Float) -> Unit) {
    Column {
        Text("$label ${(value * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium)
        Slider(value = value, onValueChange = onChange, valueRange = 0f..0.35f)
    }
}
