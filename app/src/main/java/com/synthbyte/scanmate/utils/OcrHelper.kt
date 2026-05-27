package com.synthbyte.scanmate.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.math.max
import kotlin.math.roundToInt

// English/Latin OCR only. All preprocessing is local/offline.
data class OcrExtractionResult(
    val text: String,
    val confidencePercent: Int,
    val wordCount: Int,
    val qualityLabel: String
)

object OcrHelper {
    private const val OCR_MAX_SIDE = 2048
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    suspend fun extractTextFromBitmap(bitmap: Bitmap, rotationDegrees: Int = 0): String {
        val prepared = prepareBitmapForOcr(bitmap)
        return try {
            runTextRecognition { InputImage.fromBitmap(prepared, rotationDegrees) }
        } finally {
            runCatching { prepared.recycle() }
        }
    }

    suspend fun extractTextFromFile(context: Context, file: File): String = withContext(Dispatchers.IO) {
        var prepared: Bitmap? = null
        try {
            prepared = prepareBitmapForOcr(file) ?: return@withContext "OCR failed: Could not decode image"
            runTextRecognition { InputImage.fromBitmap(prepared!!, 0) }
        } finally {
            runCatching { prepared?.recycle() }
        }
    }

    suspend fun extractTextWithStatsFromFile(context: Context, file: File): OcrExtractionResult {
        val raw = extractTextFromFile(context, file)
        return buildStats(DocumentIntelligence.cleanOcrText(raw))
    }

    fun close() {
        runCatching { recognizer.close() }
    }

    fun buildStats(text: String): OcrExtractionResult {
        val clean = cleanupRecognizedText(DocumentIntelligence.cleanOcrText(text))
        val words = clean.split(Regex("\\s+")).filter { it.isNotBlank() }
        val alphaRatio = clean.count { it.isLetterOrDigit() }.toFloat() / clean.length.coerceAtLeast(1).toFloat()
        val confidence = when {
            clean.isBlank() || clean.startsWith("OCR failed", ignoreCase = true) -> 0
            words.size >= 120 && alphaRatio > 0.62f -> 92
            words.size >= 40 && alphaRatio > 0.54f -> 82
            words.size >= 12 && alphaRatio > 0.45f -> 68
            words.size >= 4 -> 52
            else -> 38
        }
        val label = when {
            confidence >= 88 -> "High confidence"
            confidence >= 72 -> "Good confidence"
            confidence >= 55 -> "Needs review"
            confidence > 0 -> "Low confidence"
            else -> "No OCR text"
        }
        return OcrExtractionResult(clean, confidence, words.size, label)
    }

    private suspend fun runTextRecognition(imageFactory: () -> InputImage): String = suspendCancellableCoroutine { continuation ->
        try {
            recognizer.process(imageFactory())
                .addOnSuccessListener { result ->
                    val orderedText = cleanupRecognizedText(assembleOrderedText(result))
                    if (continuation.isActive) continuation.resume(orderedText)
                }
                .addOnFailureListener { e ->
                    if (continuation.isActive) continuation.resume("OCR failed: ${e.localizedMessage ?: "Unknown error"}")
                }
        } catch (e: Exception) {
            if (continuation.isActive) continuation.resume("OCR failed: ${e.localizedMessage ?: "Unknown error"}")
        }
    }

    private fun prepareBitmapForOcr(file: File): Bitmap? {
        val decoded = FileUtils.decodeSampledBitmap(file.absolutePath, OCR_MAX_SIDE, OCR_MAX_SIDE) ?: return null
        val rotated = try {
            val degrees = FileUtils.exifRotationDegrees(file.absolutePath)
            if (degrees != 0) FileUtils.rotateBitmap(decoded, degrees.toFloat()).also { decoded.recycle() } else decoded
        } catch (_: Exception) {
            decoded
        }
        val normalized = scaleDownToMax(rotated, OCR_MAX_SIDE)
        if (normalized !== rotated) rotated.recycle()
        return enhanceForOcr(normalized)
    }

    private fun prepareBitmapForOcr(bitmap: Bitmap): Bitmap {
        val normalized = scaleDownToMax(bitmap, OCR_MAX_SIDE)
        return enhanceForOcr(normalized)
    }

    private fun scaleDownToMax(source: Bitmap, maxSide: Int): Bitmap {
        val side = max(source.width, source.height)
        if (side <= maxSide) return source.copy(Bitmap.Config.ARGB_8888, false)
        val ratio = maxSide.toFloat() / side.toFloat()
        val targetWidth = (source.width * ratio).roundToInt().coerceAtLeast(1)
        val targetHeight = (source.height * ratio).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true)
    }

    private fun enhanceForOcr(source: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val colorMatrix = ColorMatrix().apply {
            setSaturation(0f)
            postConcat(ColorMatrix(contrastMatrixValues(1.38f, 18f)))
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(colorMatrix)
        }
        Canvas(result).drawBitmap(source, 0f, 0f, paint)
        if (source !== result) runCatching { source.recycle() }
        return result
    }

    private fun assembleOrderedText(result: Text): String {
        return result.textBlocks
            .sortedWith(compareBy<Text.TextBlock>({ it.boundingBox?.top ?: 0 }, { it.boundingBox?.left ?: 0 }))
            .joinToString("\n\n") { block ->
                block.lines
                    .sortedWith(compareBy<Text.Line>({ it.boundingBox?.top ?: 0 }, { it.boundingBox?.left ?: 0 }))
                    .joinToString("\n") { line -> line.text.trim() }
            }
            .ifBlank { result.text.trim() }
    }

    private fun cleanupRecognizedText(value: String): String {
        return value
            .replace(Regex("[ \\t]+"), " ")
            .replace(Regex("(?m)^\\s*[|\\u00A6]\\s*$"), "")
            .replace(Regex("(?m)^\\s*[^A-Za-z0-9]\\s*$"), "")
            .replace(Regex("-\\s*\\n\\s*"), "")
            .replace(Regex("\\s+([,.;:!?])"), "$1")
            .replace(Regex("([,.;:!?])(?=[A-Za-z])"), "$1 ")
            .replace(Regex("\\n{3,}"), "\n\n")
            .lines()
            .joinToString("\n") { it.trim() }
            .trim()
    }

    private fun contrastMatrixValues(contrast: Float, brightness: Float): FloatArray = floatArrayOf(
        contrast, 0f, 0f, 0f, brightness,
        0f, contrast, 0f, 0f, brightness,
        0f, 0f, contrast, 0f, brightness,
        0f, 0f, 0f, 1f, 0f
    )
}
