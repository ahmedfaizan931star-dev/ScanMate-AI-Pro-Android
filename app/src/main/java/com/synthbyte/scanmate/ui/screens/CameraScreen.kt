package com.synthbyte.scanmate.ui.screens

import android.Manifest
import android.content.Context
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.synthbyte.scanmate.data.SettingsRepository
import com.synthbyte.scanmate.ui.viewmodels.rememberDocumentViewModel
import com.synthbyte.scanmate.utils.FileUtils
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private enum class ScanQuality(
    val label: String,
    val captureMode: Int,
    val maxSide: Int?,
    val jpegQuality: Int,
    val description: String
) {
    STANDARD("Standard", ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY, 1800, 84, "Balanced size + speed"),
    HIGH("High", ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY, 2600, 92, "Sharp document quality"),
    MAX("Max", ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY, null, 100, "Original capture preserved")
}

private enum class ScanAspect(val label: String, val cameraValue: Int) {
    RATIO_4_3("4:3", AspectRatio.RATIO_4_3),
    RATIO_16_9("16:9", AspectRatio.RATIO_16_9)
}

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CameraScreen(onNavigateBack: () -> Unit, onScanFinished: (Long) -> Unit) {
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

    if (!cameraPermissionState.status.isGranted) {
        CameraPermissionState(onNavigateBack = onNavigateBack) {
            cameraPermissionState.launchPermissionRequest()
        }
        return
    }

    val context = LocalContext.current
    val viewModel = rememberDocumentViewModel()
    val settingsRepository = remember { SettingsRepository(context) }
    val defaultWorkspace by settingsRepository.defaultWorkspaceFlow.collectAsState(initial = "Inbox")
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    val capturedImages = remember { mutableStateListOf<File>() }
    val coroutineScope = rememberCoroutineScope()

    var lensFacing by remember { mutableStateOf(CameraSelector.LENS_FACING_BACK) }
    var quality by remember { mutableStateOf(ScanQuality.HIGH) }
    var aspect by remember { mutableStateOf(ScanAspect.RATIO_4_3) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    val cameraProviderRef = remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var torchEnabled by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var isFinishing by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf<String?>(null) }

    DisposableEffect(lifecycleOwner) {
        onDispose {
            runCatching { cameraProviderRef.value?.unbindAll() }
            cameraProviderRef.value = null
        }
    }

    val galleryPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 30)
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            coroutineScope.launch {
                isSaving = true
                val imported = withContext(Dispatchers.IO) {
                    uris.mapNotNull { uri -> FileUtils.copyUriToImageFile(context, uri) }
                }
                capturedImages.addAll(imported)
                isSaving = false
                Toast.makeText(
                    context,
                    if (imported.isNotEmpty()) "Imported ${imported.size} image(s)" else "No images could be imported",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    LaunchedEffect(lensFacing, quality, aspect) {
        cameraError = null
        runCatching {
            val cameraProvider = awaitCameraProvider(context)
            val requestedSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
            val selector = if (cameraProvider.hasCamera(requestedSelector)) {
                requestedSelector
            } else {
                val fallbackSelector = CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK).build()
                if (cameraProvider.hasCamera(fallbackSelector)) {
                    lensFacing = CameraSelector.LENS_FACING_BACK
                    cameraError = "Selected camera is not available. Using rear camera instead."
                    fallbackSelector
                } else {
                    cameraError = "No compatible camera was found on this device."
                    null
                }
            }
            if (selector != null) {
                val preview = Preview.Builder()
                    .setTargetAspectRatio(aspect.cameraValue)
                    .build()
                    .also { it.setSurfaceProvider(previewView.surfaceProvider) }
                val capture = ImageCapture.Builder()
                    .setTargetAspectRatio(aspect.cameraValue)
                    .setCaptureMode(quality.captureMode)
                    .setJpegQuality(quality.jpegQuality)
                    .build()

                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview, capture)
                cameraProviderRef.value = cameraProvider
                imageCapture = capture
                torchEnabled = false
            }
        }.onFailure { throwable ->
            Log.e("CameraScreen", "Camera bind failed", throwable)
            cameraError = throwable.localizedMessage ?: "Camera failed to start."
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .align(Alignment.TopCenter),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
                AssistChip(
                    onClick = {},
                    label = { Text("${capturedImages.size} page${if (capturedImages.size == 1) "" else "s"}") }
                )
            }

            cameraError?.let { error ->
                Text(
                    text = error,
                    color = Color.White,
                    modifier = Modifier
                        .background(Color(0xAA8B1A1A), RoundedCornerShape(14.dp))
                        .padding(12.dp)
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(Color.Black.copy(alpha = 0.48f))
                .padding(horizontal = 16.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                ScanQuality.entries.forEach { option ->
                    FilterChip(
                        selected = quality == option,
                        onClick = { quality = option },
                        label = { Text(option.label) }
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                ScanAspect.entries.forEach { option ->
                    FilterChip(
                        selected = aspect == option,
                        onClick = { aspect = option },
                        label = { Text(option.label) }
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(onClick = {
                    lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                        CameraSelector.LENS_FACING_FRONT
                    } else {
                        CameraSelector.LENS_FACING_BACK
                    }
                }) {
                    Icon(Icons.Default.Cameraswitch, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Camera")
                }

                OutlinedButton(onClick = {
                    val activeCamera = camera
                    if (activeCamera?.cameraInfo?.hasFlashUnit() == true) {
                        torchEnabled = !torchEnabled
                        activeCamera.cameraControl.enableTorch(torchEnabled)
                    } else {
                        Toast.makeText(context, "Torch not supported on this camera", Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Icon(if (torchEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Torch")
                }

                OutlinedButton(onClick = {
                    galleryPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Gallery")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.weight(1f))
                FloatingActionButton(
                    onClick = {
                        if (isSaving || isFinishing) return@FloatingActionButton
                        val capture = imageCapture
                        if (capture == null) {
                            Toast.makeText(context, "Camera is still preparing", Toast.LENGTH_SHORT).show()
                            return@FloatingActionButton
                        }
                        val file = FileUtils.createUniqueImageFile(context)
                        val outputOptions = ImageCapture.OutputFileOptions.Builder(file).build()
                        val captureQuality = quality
                        isSaving = true
                        capture.takePicture(
                            outputOptions,
                            ContextCompat.getMainExecutor(context),
                            object : ImageCapture.OnImageSavedCallback {
                                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                    coroutineScope.launch {
                                        val preparedFile = withContext(Dispatchers.IO) {
                                            if (captureQuality.maxSide != null) {
                                                FileUtils.normalizeScanImageFile(
                                                    sourceFile = file,
                                                    maxSide = captureQuality.maxSide,
                                                    jpegQuality = captureQuality.jpegQuality
                                                ) ?: file
                                            } else {
                                                file
                                            }
                                        }
                                        isSaving = false
                                        capturedImages.add(preparedFile)
                                        Toast.makeText(context, "Page captured · ${captureQuality.label}", Toast.LENGTH_SHORT).show()
                                    }
                                }

                                override fun onError(exception: ImageCaptureException) {
                                    isSaving = false
                                    Log.e("CameraScreen", "Photo capture failed", exception)
                                    Toast.makeText(context, "Capture failed: ${exception.localizedMessage ?: "Unknown error"}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    },
                    modifier = Modifier.size(74.dp),
                    shape = CircleShape,
                    containerColor = Color.White
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(30.dp), color = Color.Black)
                    } else {
                        Icon(Icons.Default.CameraAlt, contentDescription = "Take photo", tint = Color.Black, modifier = Modifier.size(36.dp))
                    }
                }
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                    FloatingActionButton(
                        onClick = {
                            if (isSaving || isFinishing) return@FloatingActionButton
                            if (capturedImages.isEmpty()) {
                                Toast.makeText(context, "Capture or import at least one page", Toast.LENGTH_SHORT).show()
                                return@FloatingActionButton
                            }
                            isFinishing = true
                            val filesSnapshot = capturedImages.toList()
                            viewModel.createDocumentFromFiles(
                                files = filesSnapshot,
                                title = "Scanned ${filesSnapshot.size} page${if (filesSnapshot.size == 1) "" else "s"}",
                                type = "SCAN",
                                defaultWorkspace = defaultWorkspace,
                                onCreated = { docId ->
                                    isFinishing = false
                                    onScanFinished(docId)
                                },
                                onError = { message ->
                                    isFinishing = false
                                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                }
                            )
                        },
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        if (isFinishing) CircularProgressIndicator(modifier = Modifier.size(24.dp)) else Icon(Icons.Default.Done, contentDescription = "Finish")
                    }
                }
            }
            Text(
                text = "${quality.label}: ${quality.description}",
                color = Color.White.copy(alpha = 0.78f),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun CameraPermissionState(onNavigateBack: () -> Unit, onRequestPermission: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Camera permission is required to scan documents.", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text("ScanMate keeps scanning offline and only uses the camera after you grant permission.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(18.dp))
        Button(onClick = onRequestPermission) { Text("Grant Permission") }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(onClick = onNavigateBack) { Text("Back") }
    }
}

private suspend fun awaitCameraProvider(context: Context): ProcessCameraProvider = suspendCancellableCoroutine { continuation ->
    val future = ProcessCameraProvider.getInstance(context)
    future.addListener(
        {
            try {
                if (continuation.isActive) continuation.resume(future.get())
            } catch (e: Exception) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }
        },
        ContextCompat.getMainExecutor(context)
    )
}
