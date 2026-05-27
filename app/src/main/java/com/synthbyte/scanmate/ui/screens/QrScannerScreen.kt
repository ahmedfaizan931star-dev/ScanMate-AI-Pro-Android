package com.synthbyte.scanmate.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.synthbyte.scanmate.ui.viewmodels.rememberDocumentViewModel
import com.synthbyte.scanmate.utils.QrPayloadParser
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class, ExperimentalGetImage::class)
@Composable
fun QrScannerScreen(onNavigateBack: () -> Unit) {
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
    if (!cameraPermissionState.status.isGranted) {
        QrCameraPermissionState(onNavigateBack = onNavigateBack) {
            cameraPermissionState.launchPermissionRequest()
        }
        return
    }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val viewModel = rememberDocumentViewModel()
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    val scanner = remember {
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
            .build()
        BarcodeScanning.getClient(options)
    }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val analyzerBusy = remember { AtomicBoolean(false) }

    var lensFacing by remember { mutableStateOf(CameraSelector.LENS_FACING_BACK) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    val cameraProviderRef = remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var torchEnabled by remember { mutableStateOf(false) }
    var scanResult by remember { mutableStateOf<String?>(null) }
    var cameraError by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            scanner.close()
            analysisExecutor.shutdown()
            runCatching { cameraProviderRef.value?.unbindAll() }
            cameraProviderRef.value = null
        }
    }

    LaunchedEffect(lensFacing) {
        cameraError = null
        runCatching {
            val provider = awaitQrCameraProvider(context)
            val requestedSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
            val selector = if (provider.hasCamera(requestedSelector)) {
                requestedSelector
            } else {
                val fallbackSelector = CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK).build()
                if (provider.hasCamera(fallbackSelector)) {
                    lensFacing = CameraSelector.LENS_FACING_BACK
                    cameraError = "Selected camera is not available. Using rear camera instead."
                    fallbackSelector
                } else {
                    cameraError = "No compatible camera was found on this device."
                    null
                }
            }
            if (selector != null) {
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { imageAnalysis ->
                        imageAnalysis.setAnalyzer(analysisExecutor) { imageProxy ->
                            val mediaImage = imageProxy.image
                            if (mediaImage == null || analyzerBusy.get() || scanResult != null) {
                                imageProxy.close()
                                return@setAnalyzer
                            }
                            analyzerBusy.set(true)
                            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                            scanner.process(image)
                                .addOnSuccessListener { barcodes ->
                                    val value = barcodes.firstOrNull()?.rawValue?.trim()?.takeIf { it.isNotBlank() }
                                    if (value != null && scanResult == null) {
                                        scanResult = value
                                        viewModel.insertQrHistory(value, "SCANNED")
                                    }
                                }
                                .addOnFailureListener { error ->
                                    Log.e("QrScannerScreen", "Barcode scan failed", error)
                                }
                                .addOnCompleteListener {
                                    analyzerBusy.set(false)
                                    imageProxy.close()
                                }
                        }
                    }
                provider.unbindAll()
                cameraProviderRef.value = provider
                camera = provider.bindToLifecycle(lifecycleOwner, selector, preview, analysis)
                torchEnabled = false
            }
        }.onFailure { throwable ->
            Log.e("QrScannerScreen", "Camera bind failed", throwable)
            cameraError = throwable.localizedMessage ?: "QR scanner camera failed to start."
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Camera QR Scanner") },
                navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = {
                        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
                        scanResult = null
                    }) { Icon(Icons.Default.Cameraswitch, "Switch camera") }
                    IconButton(onClick = {
                        val activeCamera = camera
                        if (activeCamera?.cameraInfo?.hasFlashUnit() == true) {
                            torchEnabled = !torchEnabled
                            activeCamera.cameraControl.enableTorch(torchEnabled)
                        } else {
                            Toast.makeText(context, "Torch not supported on this camera", Toast.LENGTH_SHORT).show()
                        }
                    }) { Icon(if (torchEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff, "Torch") }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize().background(Color.Black)) {
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(240.dp)
                    .background(Color.Transparent, RoundedCornerShape(28.dp))
            )
            Column(
                modifier = Modifier.align(Alignment.TopCenter).padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = cameraError ?: "Point the camera at a QR code or barcode",
                    color = Color.White,
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(18.dp)).padding(horizontal = 14.dp, vertical = 10.dp)
                )
            }

            scanResult?.let { result ->
                val payloadInfo = remember(result) { QrPayloadParser.parse(result) }
                val contactIntent = remember(result) { QrPayloadParser.contactInsertIntent(result) }
                Card(
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.QrCodeScanner, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("QR/barcode found", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        }
                        AssistChip(onClick = {}, label = { Text(payloadInfo.typeLabel) })
                        Text(payloadInfo.summary, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (payloadInfo.summary != result) {
                            Text(result, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = {
                                clipboardManager.setPrimaryClip(ClipData.newPlainText("QR Scan Result", result))
                                Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(Icons.Default.ContentCopy, null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Copy")
                            }
                            payloadInfo.actionUri?.let { uri ->
                                TextButton(onClick = { openScannerLink(context, uri.toString()) }) {
                                    Icon(Icons.Default.Link, null)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(payloadInfo.actionLabel ?: "Open")
                                }
                            }
                            if (contactIntent != null) {
                                TextButton(onClick = {
                                    runCatching { context.startActivity(contactIntent) }
                                        .onFailure { Toast.makeText(context, "No contacts app found", Toast.LENGTH_SHORT).show() }
                                }) { Text("Save contact") }
                            }
                            OutlinedButton(onClick = { scanResult = null }) { Text("Scan again") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QrCameraPermissionState(onNavigateBack: () -> Unit, onRequestPermission: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Camera permission is required for QR scanning.", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text("QR scanning works offline using on-device barcode detection.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(18.dp))
        Button(onClick = onRequestPermission) { Text("Grant Permission") }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(onClick = onNavigateBack) { Text("Back") }
    }
}

private suspend fun awaitQrCameraProvider(context: Context): ProcessCameraProvider = suspendCancellableCoroutine { continuation ->
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

private fun String.isSafeHttpUrlForScanner(): Boolean {
    val uri = runCatching { Uri.parse(this.trim()) }.getOrNull() ?: return false
    return uri.scheme == "http" || uri.scheme == "https"
}

private fun openScannerLink(context: Context, value: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(value.trim())))
    } catch (e: Exception) {
        Toast.makeText(context, "No app found to open this link", Toast.LENGTH_SHORT).show()
    }
}
