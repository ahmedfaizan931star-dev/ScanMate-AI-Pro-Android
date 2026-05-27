package com.synthbyte.scanmate.utils

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Lightweight, dependency-free foundation for document edge confidence.
 * This does not pretend to be full perspective detection; it gives the UI/editor
 * a safe local signal that can later be replaced by a stronger detector.
 */
data class DocumentEdgeConfidence(
    val confidence: Float,
    val message: String
)

object DocumentEdgeDetector {
    fun estimateDocumentConfidence(bitmap: Bitmap): DocumentEdgeConfidence {
        if (bitmap.width < 120 || bitmap.height < 120) {
            return DocumentEdgeConfidence(0f, "Image is too small for edge analysis")
        }

        val sampleSide = 420
        val sampled = bitmap.scaleForAnalysis(sampleSide)
        val margin = (min(sampled.width, sampled.height) * 0.08f).roundToInt().coerceAtLeast(8)
        var borderContrastHits = 0
        var samples = 0
        val step = max(2, max(sampled.width, sampled.height) / 180)

        fun brightnessAt(x: Int, y: Int): Int {
            val c = sampled.getPixel(x.coerceIn(0, sampled.width - 1), y.coerceIn(0, sampled.height - 1))
            return (Color.red(c) + Color.green(c) + Color.blue(c)) / 3
        }

        for (x in margin until sampled.width - margin step step) {
            val topDelta = kotlin.math.abs(brightnessAt(x, margin) - brightnessAt(x, 0))
            val bottomDelta = kotlin.math.abs(brightnessAt(x, sampled.height - margin - 1) - brightnessAt(x, sampled.height - 1))
            if (topDelta > 28) borderContrastHits++
            if (bottomDelta > 28) borderContrastHits++
            samples += 2
        }
        for (y in margin until sampled.height - margin step step) {
            val leftDelta = kotlin.math.abs(brightnessAt(margin, y) - brightnessAt(0, y))
            val rightDelta = kotlin.math.abs(brightnessAt(sampled.width - margin - 1, y) - brightnessAt(sampled.width - 1, y))
            if (leftDelta > 28) borderContrastHits++
            if (rightDelta > 28) borderContrastHits++
            samples += 2
        }

        if (sampled !== bitmap) sampled.recycle()
        val confidence = (borderContrastHits.toFloat() / samples.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
        val message = when {
            confidence >= 0.62f -> "Document edges look clear"
            confidence >= 0.36f -> "Edges are visible; review crop before export"
            else -> "Edges are weak; use crop/auto-crop after capture"
        }
        return DocumentEdgeConfidence(confidence, message)
    }

    private fun Bitmap.scaleForAnalysis(maxSide: Int): Bitmap {
        val side = max(width, height)
        if (side <= maxSide) return this
        val ratio = maxSide.toFloat() / side.toFloat()
        return Bitmap.createScaledBitmap(
            this,
            (width * ratio).roundToInt().coerceAtLeast(1),
            (height * ratio).roundToInt().coerceAtLeast(1),
            true
        )
    }
}
