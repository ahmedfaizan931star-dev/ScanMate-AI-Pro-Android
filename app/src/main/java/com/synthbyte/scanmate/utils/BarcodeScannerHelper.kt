package com.synthbyte.scanmate.utils

import android.graphics.Bitmap
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

object BarcodeScannerHelper {
    suspend fun scanBarcode(bitmap: Bitmap, rotationDegrees: Int = 0): String? = suspendCancellableCoroutine { continuation ->
        try {
            val image = InputImage.fromBitmap(bitmap, rotationDegrees)
            val options = BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
                .build()
            val scanner = BarcodeScanning.getClient(options)
            scanner.process(image)
                .addOnSuccessListener { barcodes ->
                    continuation.resume(barcodes.firstOrNull()?.rawValue?.trim()?.takeIf { it.isNotBlank() })
                }
                .addOnFailureListener { continuation.resume(null) }
        } catch (e: Exception) {
            continuation.resume(null)
        }
    }
}
