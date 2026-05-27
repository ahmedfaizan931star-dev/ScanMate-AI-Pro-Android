package com.synthbyte.scanmate.utils

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.media.ExifInterface
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object FileUtils {
    fun createUniqueImageFile(context: Context): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
        val imageFileName = "SCAN_${timeStamp}_"
        val storageDir = context.getExternalFilesDir("Scans")
        if (storageDir?.exists() == false) storageDir.mkdirs()
        return File.createTempFile(imageFileName, ".jpg", storageDir)
    }

    fun appFolder(context: Context, name: String): File? {
        val dir = context.getExternalFilesDir(name)
        if (dir?.exists() == false) dir.mkdirs()
        return dir
    }

    fun listManagedFiles(context: Context): List<File> {
        val folders = listOf("Scans", "PDFs", "QRCodes", "OCR", "Backups", "Signatures", "Vault")
        return folders.flatMap { folder ->
            appFolder(context, folder)?.listFiles()?.filter { it.isFile } ?: emptyList()
        }.sortedByDescending { it.lastModified() }
    }

    fun shareFile(context: Context, file: File, mimeType: String = mimeTypeFor(file)) {
        if (!file.exists() || file.length() == 0L) {
            Toast.makeText(context, "File is missing or empty", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share File"))
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "No app found to share this file", Toast.LENGTH_SHORT).show()
        }
    }

    fun openFile(context: Context, file: File, mimeType: String = mimeTypeFor(file)) {
        if (!file.exists() || file.length() == 0L) {
            Toast.makeText(context, "File is missing or empty", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Open with"))
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "No app found to open this file", Toast.LENGTH_SHORT).show()
        }
    }

    fun shareText(context: Context, text: String, title: String = "Share Text") {
        try {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
            context.startActivity(Intent.createChooser(shareIntent, title))
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "No app found to share text", Toast.LENGTH_SHORT).show()
        }
    }

    fun copyUriToImageFile(context: Context, uri: Uri): File? {
        return try {
            val file = createUniqueImageFile(context)
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(file).use { output -> input.copyTo(output) }
            } ?: return null
            if (file.exists() && file.length() > 0L) file else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun saveTextFile(context: Context, text: String, filename: String): File? = withContext(Dispatchers.IO) {
        try {
            val storageDir = appFolder(context, "OCR") ?: return@withContext null
            val safeName = sanitizeFileBaseName(filename.ifBlank { "OCR_${System.currentTimeMillis()}" })
            val file = File(storageDir, if (safeName.endsWith(".txt")) safeName else "$safeName.txt")
            FileOutputStream(file).use { out -> out.write(text.toByteArray()) }
            file.takeIf { it.exists() && it.length() > 0L }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun saveBitmapAsPng(context: Context, bitmap: Bitmap, filename: String): File? =
        saveBitmapToFolder(context, bitmap, "QRCodes", filename, Bitmap.CompressFormat.PNG, 100)

    suspend fun saveBitmapToFolder(
        context: Context,
        bitmap: Bitmap,
        folderName: String,
        filename: String,
        format: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG,
        quality: Int = 92
    ): File? = withContext(Dispatchers.IO) {
        try {
            val storageDir = appFolder(context, folderName) ?: return@withContext null
            val safeName = sanitizeFileBaseName(filename.ifBlank { "ScanMate_${System.currentTimeMillis()}" })
            val extension = when (format) {
                Bitmap.CompressFormat.PNG -> ".png"
                Bitmap.CompressFormat.WEBP -> ".webp"
                else -> ".jpg"
            }
            val finalName = if (safeName.endsWith(extension, ignoreCase = true)) safeName else "$safeName$extension"
            val file = File(storageDir, finalName)
            FileOutputStream(file).use { out ->
                if (!bitmap.compress(format, quality.coerceIn(1, 100), out)) return@withContext null
            }
            file.takeIf { it.exists() && it.length() > 0L }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun saveEditedBitmap(context: Context, bitmap: Bitmap, sourceName: String = "EDITED"): File? {
        val name = "${sourceName}_${System.currentTimeMillis()}"
        return saveBitmapToFolder(context, bitmap, "Scans", name, Bitmap.CompressFormat.JPEG, 94)
    }

    fun duplicateImageFile(context: Context, sourcePath: String): File? {
        return try {
            val source = File(sourcePath)
            if (!source.exists() || source.length() == 0L) return null
            val copy = createUniqueImageFile(context)
            source.inputStream().use { input -> copy.outputStream().use { output -> input.copyTo(output) } }
            copy.takeIf { it.exists() && it.length() > 0L }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun normalizeScanImageFile(sourceFile: File, maxSide: Int = 2600, jpegQuality: Int = 92): File? {
        if (!sourceFile.exists() || sourceFile.length() == 0L) return null
        var decoded: Bitmap? = null
        var rotated: Bitmap? = null
        var scaled: Bitmap? = null
        return try {
            decoded = decodeSampledBitmap(sourceFile.absolutePath, maxSide, maxSide) ?: return null
            val rotation = exifRotationDegrees(sourceFile.absolutePath)
            rotated = if (rotation != 0) rotateBitmap(decoded!!, rotation.toFloat()) else decoded
            scaled = rotated!!.scaleDownToMax(maxSide) ?: rotated
            FileOutputStream(sourceFile).use { output ->
                if (!scaled!!.compress(Bitmap.CompressFormat.JPEG, jpegQuality.coerceIn(1, 100), output)) return null
            }
            sourceFile.takeIf { it.exists() && it.length() > 0L }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            if (scaled != null && scaled !== rotated) runCatching { scaled!!.recycle() }
            if (rotated != null && rotated !== decoded) runCatching { rotated!!.recycle() }
            runCatching { decoded?.recycle() }
        }
    }

    fun exifRotationDegrees(path: String): Int = runCatching {
        when (ExifInterface(path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
    }.getOrDefault(0)

    fun decodeSampledBitmap(path: String, reqWidth: Int = 1600, reqHeight: Int = 1600): Bitmap? {
        return try {
            val file = File(path)
            if (!file.exists() || file.length() == 0L) return null
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, options)
            if (options.outWidth <= 0 || options.outHeight <= 0) return null
            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
            options.inJustDecodeBounds = false
            options.inPreferredConfig = Bitmap.Config.ARGB_8888
            BitmapFactory.decodeFile(path, options)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return max(1, inSampleSize)
    }

    suspend fun generatePdf(context: Context, images: List<Bitmap>, filename: String): File? = withContext(Dispatchers.IO) {
        generatePdfInternal(context, images, filename)
    }

    suspend fun generatePdfFromBitmaps(
        context: Context,
        images: List<Bitmap>,
        filename: String,
        quality: PdfExportQuality = PdfExportQuality.BALANCED,
        pageSize: PdfPageSize = PdfPageSize.A4
    ): File? = withContext(Dispatchers.IO) {
        val maxSide = when (quality) {
            PdfExportQuality.SMALL -> 1200
            PdfExportQuality.BALANCED -> 1800
            PdfExportQuality.HIGH -> 2600
        }
        val prepared = images.mapNotNull { bitmap -> bitmap.scaleDownToMax(maxSide) }
        try {
            generatePdfInternal(context, prepared, filename, pageSize)
        } finally {
            prepared.forEachIndexed { index, bitmap ->
                if (bitmap !== images.getOrNull(index)) runCatching { bitmap.recycle() }
            }
        }
    }

    suspend fun generatePdfFromPaths(
        context: Context,
        imagePaths: List<String>,
        filename: String,
        quality: PdfExportQuality = PdfExportQuality.BALANCED,
        pageSize: PdfPageSize = PdfPageSize.A4,
        onProgress: ((currentPage: Int, totalPages: Int) -> Unit)? = null
    ): File? = withContext(Dispatchers.IO) {
        val targetSize = when (quality) {
            PdfExportQuality.SMALL -> 1200
            PdfExportQuality.BALANCED -> 1800
            PdfExportQuality.HIGH -> 2600
        }
        val validPaths = imagePaths.filter { path ->
            val file = File(path)
            file.exists() && file.length() > 0L
        }
        if (validPaths.isEmpty()) return@withContext null
        generatePdfInternalFromPaths(context, validPaths, targetSize, filename, pageSize, onProgress)
    }

    private fun generatePdfInternalFromPaths(
        context: Context,
        imagePaths: List<String>,
        targetSize: Int,
        filename: String,
        pageSize: PdfPageSize,
        onProgress: ((currentPage: Int, totalPages: Int) -> Unit)? = null
    ): File? {
        if (imagePaths.isEmpty()) return null
        val pdfDocument = PdfDocument()
        return try {
            val whitePaint = Paint().apply { color = Color.WHITE }
            var pageNumber = 0

            imagePaths.forEach { path ->
                val bitmap = decodeSampledBitmap(path, targetSize, targetSize) ?: return@forEach
                try {
                    if (bitmap.width <= 0 || bitmap.height <= 0) return@forEach
                    pageNumber += 1
                    onProgress?.invoke(pageNumber, imagePaths.size)
                    val (pageWidth, pageHeight) = pdfPageDimensions(bitmap, pageSize)
                    val aspectRatio = bitmap.height.toFloat() / bitmap.width.toFloat()
                    val margin = pdfPageMargin(pageSize)
                    var targetWidth = (pageWidth - margin * 2).coerceAtLeast(1)
                    var targetHeight = (targetWidth * aspectRatio).toInt().coerceAtLeast(1)
                    val maxHeight = (pageHeight - margin * 2).coerceAtLeast(1)
                    if (targetHeight > maxHeight) {
                        targetHeight = maxHeight
                        targetWidth = (targetHeight / aspectRatio).toInt().coerceAtLeast(1)
                    }
                    val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                    val page = pdfDocument.startPage(pageInfo)
                    page.canvas.drawRect(0f, 0f, pageWidth.toFloat(), pageHeight.toFloat(), whitePaint)
                    val left = (pageWidth - targetWidth) / 2f
                    val top = (pageHeight - targetHeight) / 2f
                    val scaledBitmap = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
                    page.canvas.drawBitmap(scaledBitmap, left, top, null)
                    if (scaledBitmap !== bitmap) scaledBitmap.recycle()
                    pdfDocument.finishPage(page)
                } finally {
                    runCatching { bitmap.recycle() }
                }
            }

            if (pageNumber == 0) return null
            val storageDir = appFolder(context, "PDFs") ?: return null
            val safeName = sanitizeFileBaseName(filename.ifBlank { "ScanMate_${System.currentTimeMillis()}" })
                .removeSuffix(".pdf")
                .removeSuffix(".PDF")
            val file = File(storageDir, "$safeName.pdf")
            FileOutputStream(file).use { outputStream -> pdfDocument.writeTo(outputStream) }
            file.takeIf { it.exists() && it.length() > 0L } ?: run {
                file.delete()
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            try {
                pdfDocument.close()
            } catch (_: Exception) {
            }
        }
    }

    private fun generatePdfInternal(context: Context, images: List<Bitmap>, filename: String, pageSize: PdfPageSize = PdfPageSize.A4): File? {
        if (images.isEmpty()) return null
        val pdfDocument = PdfDocument()
        return try {
            val whitePaint = Paint().apply { color = Color.WHITE }

            var pageNumber = 0
            images.forEach { bitmap ->
                if (bitmap.width <= 0 || bitmap.height <= 0) return@forEach
                pageNumber += 1
                val (pageWidth, pageHeight) = pdfPageDimensions(bitmap, pageSize)
                val aspectRatio = bitmap.height.toFloat() / bitmap.width.toFloat()
                val margin = pdfPageMargin(pageSize)
                var targetWidth = (pageWidth - margin * 2).coerceAtLeast(1)
                var targetHeight = (targetWidth * aspectRatio).toInt().coerceAtLeast(1)
                val maxHeight = (pageHeight - margin * 2).coerceAtLeast(1)

                if (targetHeight > maxHeight) {
                    targetHeight = maxHeight
                    targetWidth = (targetHeight / aspectRatio).toInt().coerceAtLeast(1)
                }

                val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                val page = pdfDocument.startPage(pageInfo)
                page.canvas.drawRect(0f, 0f, pageWidth.toFloat(), pageHeight.toFloat(), whitePaint)
                val left = (pageWidth - targetWidth) / 2f
                val top = (pageHeight - targetHeight) / 2f
                val scaledBitmap = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
                page.canvas.drawBitmap(scaledBitmap, left, top, null)
                if (scaledBitmap !== bitmap) scaledBitmap.recycle()
                pdfDocument.finishPage(page)
            }

            if (pageNumber == 0) return null

            val storageDir = appFolder(context, "PDFs") ?: return null
            val safeName = sanitizeFileBaseName(filename.ifBlank { "ScanMate_${System.currentTimeMillis()}" })
                .removeSuffix(".pdf")
                .removeSuffix(".PDF")
            val file = File(storageDir, "$safeName.pdf")
            FileOutputStream(file).use { outputStream -> pdfDocument.writeTo(outputStream) }
            file.takeIf { it.exists() && it.length() > 0L } ?: run {
                file.delete()
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            try {
                pdfDocument.close()
            } catch (_: Exception) {
            }
        }
    }

    private fun pdfPageDimensions(bitmap: Bitmap, pageSize: PdfPageSize): Pair<Int, Int> {
        return when (pageSize) {
            PdfPageSize.A4 -> 595 to 842
            PdfPageSize.LETTER -> 612 to 792
            PdfPageSize.AUTO -> if (bitmap.width > bitmap.height) 842 to 595 else 595 to 842
        }
    }

    private fun pdfPageMargin(pageSize: PdfPageSize): Int = when (pageSize) {
        PdfPageSize.AUTO -> 0
        PdfPageSize.A4, PdfPageSize.LETTER -> 28
    }

    fun renderPdfUriToBitmaps(
        context: Context,
        uri: Uri,
        maxWidth: Int = 1600,
        pageRange: IntRange? = null
    ): List<Bitmap> {
        val descriptor = context.contentResolver.openFileDescriptor(uri, "r") ?: return emptyList()
        return try {
            PdfRenderer(descriptor).use { renderer ->
                val indices = pageRange?.map { it - 1 } ?: (0 until renderer.pageCount).toList()
                indices.mapNotNull { pageIndex ->
                    if (pageIndex !in 0 until renderer.pageCount) return@mapNotNull null
                    renderer.openPage(pageIndex).use { page ->
                        val scale = (maxWidth.toFloat() / page.width.toFloat()).coerceAtMost(3f).coerceAtLeast(1f)
                        val width = (page.width * scale).roundToInt().coerceAtLeast(1)
                        val height = (page.height * scale).roundToInt().coerceAtLeast(1)
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        Canvas(bitmap).drawColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bitmap
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        } finally {
            try {
                descriptor.close()
            } catch (_: Exception) {
            }
        }
    }

    fun getDisplayName(context: Context, uri: Uri): String {
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && index >= 0) cursor.getString(index) else null
            }
        }.getOrNull() ?: uri.lastPathSegment ?: "Selected file"
    }

    fun mimeTypeFor(file: File): String = when (file.extension.lowercase(Locale.US)) {
        "pdf" -> "application/pdf"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "txt" -> "text/plain"
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "zip" -> "application/zip"
        "vault" -> "application/octet-stream"
        else -> "application/octet-stream"
    }

    fun rotateBitmap(source: Bitmap, degrees: Float): Bitmap {
        val matrix = android.graphics.Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    fun cropBitmapNormalized(source: Bitmap, leftPercent: Float, topPercent: Float, rightPercent: Float, bottomPercent: Float): Bitmap {
        val left = (source.width * leftPercent.coerceIn(0f, 0.85f)).roundToInt()
        val top = (source.height * topPercent.coerceIn(0f, 0.85f)).roundToInt()
        val rightMargin = (source.width * rightPercent.coerceIn(0f, 0.85f)).roundToInt()
        val bottomMargin = (source.height * bottomPercent.coerceIn(0f, 0.85f)).roundToInt()
        val width = (source.width - left - rightMargin).coerceAtLeast(64)
        val height = (source.height - top - bottomMargin).coerceAtLeast(64)
        return Bitmap.createBitmap(source, left.coerceAtMost(source.width - 1), top.coerceAtMost(source.height - 1), min(width, source.width - left), min(height, source.height - top))
    }

    fun autoCropDocument(source: Bitmap): Bitmap {
        if (source.width < 80 || source.height < 80) return source.copy(Bitmap.Config.ARGB_8888, false)
        val maxSampleSide = 700
        val sampled = source.scaleDownToMax(maxSampleSide) ?: return source.copy(Bitmap.Config.ARGB_8888, false)
        val threshold = 235
        var minX = sampled.width
        var minY = sampled.height
        var maxX = 0
        var maxY = 0
        val step = max(1, sampled.width.coerceAtLeast(sampled.height) / 350)
        for (y in 0 until sampled.height step step) {
            for (x in 0 until sampled.width step step) {
                val color = sampled.getPixel(x, y)
                val r = Color.red(color)
                val g = Color.green(color)
                val b = Color.blue(color)
                val brightness = (r + g + b) / 3
                val contrast = max(r, max(g, b)) - min(r, min(g, b))
                if (brightness < threshold || contrast > 28) {
                    minX = min(minX, x)
                    minY = min(minY, y)
                    maxX = max(maxX, x)
                    maxY = max(maxY, y)
                }
            }
        }
        if (maxX <= minX || maxY <= minY) return source.copy(Bitmap.Config.ARGB_8888, false)
        val marginX = (sampled.width * 0.025f).roundToInt()
        val marginY = (sampled.height * 0.025f).roundToInt()
        val sx = source.width.toFloat() / sampled.width.toFloat()
        val sy = source.height.toFloat() / sampled.height.toFloat()
        val left = ((minX - marginX).coerceAtLeast(0) * sx).roundToInt()
        val top = ((minY - marginY).coerceAtLeast(0) * sy).roundToInt()
        val right = ((maxX + marginX).coerceAtMost(sampled.width - 1) * sx).roundToInt()
        val bottom = ((maxY + marginY).coerceAtMost(sampled.height - 1) * sy).roundToInt()
        val width = (right - left).coerceAtLeast(64)
        val height = (bottom - top).coerceAtLeast(64)
        if (width < source.width * 0.35f || height < source.height * 0.35f) {
            return source.copy(Bitmap.Config.ARGB_8888, false)
        }
        return Bitmap.createBitmap(source, left.coerceAtLeast(0), top.coerceAtLeast(0), min(width, source.width - left), min(height, source.height - top))
    }

    fun perspectiveCorrectBitmapNormalized(
        source: Bitmap,
        topLeftX: Float,
        topLeftY: Float,
        topRightX: Float,
        topRightY: Float,
        bottomRightX: Float,
        bottomRightY: Float,
        bottomLeftX: Float,
        bottomLeftY: Float
    ): Bitmap {
        if (source.width < 80 || source.height < 80) return source.copy(Bitmap.Config.ARGB_8888, false)
        val w = source.width.toFloat()
        val h = source.height.toFloat()
        val src = floatArrayOf(
            (topLeftX.coerceIn(0f, 0.45f) * w), (topLeftY.coerceIn(0f, 0.45f) * h),
            (w - topRightX.coerceIn(0f, 0.45f) * w), (topRightY.coerceIn(0f, 0.45f) * h),
            (w - bottomRightX.coerceIn(0f, 0.45f) * w), (h - bottomRightY.coerceIn(0f, 0.45f) * h),
            (bottomLeftX.coerceIn(0f, 0.45f) * w), (h - bottomLeftY.coerceIn(0f, 0.45f) * h)
        )
        val dst = floatArrayOf(0f, 0f, w, 0f, w, h, 0f, h)
        val matrix = android.graphics.Matrix()
        if (!matrix.setPolyToPoly(src, 0, dst, 0, 4)) return source.copy(Bitmap.Config.ARGB_8888, false)
        val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
        canvas.drawBitmap(source, matrix, paint)
        return result
    }

    fun applyFilter(original: Bitmap, type: FilterType): Bitmap {
        val safe = original.copy(Bitmap.Config.ARGB_8888, false)
        if (type == FilterType.ORIGINAL) return safe
        if (type == FilterType.SHARPEN || type == FilterType.SHARP_SCAN) return sharpenBitmap(safe)

        val result = Bitmap.createBitmap(safe.width, safe.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val colorMatrix = ColorMatrix()

        when (type) {
            FilterType.ORIGINAL -> Unit
            FilterType.GRAYSCALE -> colorMatrix.setSaturation(0f)
            FilterType.BLACK_WHITE -> {
                colorMatrix.setSaturation(0f)
                colorMatrix.postConcat(contrastMatrix(1.85f, -85f))
            }
            FilterType.ENHANCED_COLOR, FilterType.MAGIC_COLOR -> {
                colorMatrix.setSaturation(1.35f)
                colorMatrix.postConcat(contrastMatrix(1.18f, 10f))
            }
            FilterType.LIGHTEN -> colorMatrix.set(contrastMatrixValues(1.08f, 34f))
            FilterType.SHARPEN, FilterType.SHARP_SCAN -> Unit
            FilterType.HIGH_CONTRAST -> colorMatrix.set(contrastMatrixValues(1.45f, -38f))
            FilterType.SOFT_SCAN -> {
                colorMatrix.setSaturation(0.82f)
                colorMatrix.postConcat(contrastMatrix(1.04f, 28f))
            }
            FilterType.RECEIPT_MODE -> {
                colorMatrix.setSaturation(0f)
                colorMatrix.postConcat(contrastMatrix(1.62f, -28f))
            }
            FilterType.BOOK_PAGE -> {
                colorMatrix.setSaturation(0.68f)
                colorMatrix.postConcat(contrastMatrix(1.12f, 38f))
            }
            FilterType.LOW_LIGHT_CLEANUP -> {
                colorMatrix.setSaturation(1.08f)
                colorMatrix.postConcat(contrastMatrix(1.16f, 54f))
            }
            FilterType.SHADOW_REDUCTION -> {
                colorMatrix.setSaturation(0.92f)
                colorMatrix.postConcat(contrastMatrix(1.1f, 42f))
            }
        }

        paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        canvas.drawBitmap(safe, 0f, 0f, paint)
        return result
    }

    fun drawSignatureOnBitmap(pageBitmap: Bitmap, signatureBitmap: Bitmap, alignRight: Boolean = true): Bitmap {
        val result = pageBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val targetWidth = (pageBitmap.width * 0.34f).roundToInt().coerceAtLeast(120)
        val scale = targetWidth.toFloat() / signatureBitmap.width.toFloat().coerceAtLeast(1f)
        val targetHeight = (signatureBitmap.height * scale).roundToInt().coerceAtLeast(60)
        val margin = (pageBitmap.width * 0.06f).roundToInt().coerceAtLeast(24)
        val left = if (alignRight) pageBitmap.width - targetWidth - margin else margin
        val top = pageBitmap.height - targetHeight - margin
        val rect = RectF(left.toFloat(), top.toFloat(), (left + targetWidth).toFloat(), (top + targetHeight).toFloat())
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(signatureBitmap, null, rect, paint)
        return result
    }

    fun drawWatermarkOnBitmap(source: Bitmap, text: String = "ScanMate AI Pro"): Bitmap {
        val cleanText = text.trim().ifBlank { "ScanMate AI Pro" }.take(70)
        val result = source.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val textSize = max(30f, source.width * 0.038f)
        val margin = max(28f, source.width * 0.045f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            color = Color.argb(82, 0, 0, 0)
            this.textSize = textSize
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
            setShadowLayer(textSize * 0.08f, 1.5f, 1.5f, Color.argb(70, 255, 255, 255))
        }
        canvas.drawText(cleanText, source.width - margin, source.height - margin, paint)
        return result
    }

    fun drawNoteStampOnBitmap(source: Bitmap, text: String): Bitmap {
        val cleanText = text.trim().ifBlank { "Reviewed in ScanMate AI Pro" }.take(140)
        val result = source.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val textSize = max(28f, source.width * 0.032f)
        val margin = max(24f, source.width * 0.035f)
        val maxChars = 34
        val lines = cleanText.chunked(maxChars).take(3)
        val lineHeight = textSize * 1.35f
        val boxWidth = source.width * 0.78f
        val boxHeight = lineHeight * lines.size.toFloat() + margin
        val rect = RectF(margin, margin, margin + boxWidth, margin + boxHeight)
        val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(214, 255, 255, 255) }
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(175, 30, 30, 30)
            style = Paint.Style.STROKE
            strokeWidth = max(2f, source.width * 0.002f)
        }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            color = Color.argb(230, 20, 20, 20)
            this.textSize = textSize
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawRoundRect(rect, 18f, 18f, boxPaint)
        canvas.drawRoundRect(rect, 18f, 18f, strokePaint)
        lines.forEachIndexed { index, line ->
            canvas.drawText(line, margin * 1.45f, margin + lineHeight * (index.toFloat() + 0.95f), textPaint)
        }
        return result
    }


    suspend fun saveDocxText(context: Context, text: String, filename: String): File? = withContext(Dispatchers.IO) {
        try {
            val storageDir = appFolder(context, "OCR") ?: return@withContext null
            val safeName = sanitizeFileBaseName(filename.ifBlank { "ScanMate_Document_${System.currentTimeMillis()}" })
                .removeSuffix(".docx")
                .removeSuffix(".DOCX")
            val file = File(storageDir, "$safeName.docx")
            val cleaned = DocumentIntelligence.cleanOcrText(text).ifBlank { "ScanMate AI Pro document export" }
            val paragraphs = cleaned.lines().map { line ->
                "<w:p><w:r><w:t xml:space=\"preserve\">${escapeXml(line)}</w:t></w:r></w:p>"
            }.joinToString("\n")
            ZipOutputStream(FileOutputStream(file).buffered()).use { zip ->
                zip.putDocxEntry(
                    "[Content_Types].xml",
                    """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                        <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                        <Default Extension="xml" ContentType="application/xml"/>
                        <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
                    </Types>
                    """.trimIndent()
                )
                zip.putDocxEntry(
                    "_rels/.rels",
                    """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                        <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
                    </Relationships>
                    """.trimIndent()
                )
                zip.putDocxEntry(
                    "word/document.xml",
                    """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                        <w:body>
                            $paragraphs
                            <w:sectPr>
                                <w:pgSz w:w="12240" w:h="15840"/>
                                <w:pgMar w:top="1440" w:right="1440" w:bottom="1440" w:left="1440"/>
                            </w:sectPr>
                        </w:body>
                    </w:document>
                    """.trimIndent()
                )
            }
            file.takeIf { it.exists() && it.length() > 0L }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun ZipOutputStream.putDocxEntry(path: String, content: String) {
        putNextEntry(ZipEntry(path))
        write(content.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private fun escapeXml(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    fun sanitizeFileBaseName(value: String): String = value
        .trim()
        .ifBlank { "ScanMate_${System.currentTimeMillis()}" }
        .replace(Regex("[^A-Za-z0-9._-]"), "_")
        .take(90)

    private fun contrastMatrix(contrast: Float, brightness: Float): ColorMatrix = ColorMatrix(contrastMatrixValues(contrast, brightness))

    private fun contrastMatrixValues(contrast: Float, brightness: Float): FloatArray = floatArrayOf(
        contrast, 0f, 0f, 0f, brightness,
        0f, contrast, 0f, 0f, brightness,
        0f, 0f, contrast, 0f, brightness,
        0f, 0f, 0f, 1f, 0f
    )

    private fun sharpenBitmap(source: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(1.05f) })
        }
        canvas.drawBitmap(source, 0f, 0f, paint)
        val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { alpha = 42 }
        canvas.drawBitmap(source, 0f, 0f, overlayPaint)
        return result
    }

    private fun Bitmap.scaleDownToMax(maxSide: Int): Bitmap? {
        if (width <= 0 || height <= 0) return null
        val side = max(width, height)
        if (side <= maxSide) return this
        val ratio = maxSide.toFloat() / side.toFloat()
        val targetWidth = (width * ratio).roundToInt().coerceAtLeast(1)
        val targetHeight = (height * ratio).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(this, targetWidth, targetHeight, true)
    }
}

enum class FilterType(val label: String) {
    ORIGINAL("Original"),
    ENHANCED_COLOR("Enhanced Color"),
    GRAYSCALE("Grayscale"),
    BLACK_WHITE("Black & White"),
    HIGH_CONTRAST("High Contrast"),
    SOFT_SCAN("Soft Scan"),
    SHARP_SCAN("Sharp Scan"),
    RECEIPT_MODE("Receipt Mode"),
    BOOK_PAGE("Book/Page Mode"),
    LOW_LIGHT_CLEANUP("Low-Light Cleanup"),
    MAGIC_COLOR("Magic Color"),
    LIGHTEN("Lighten"),
    SHARPEN("Sharpen"),
    SHADOW_REDUCTION("Shadow Reduction")
}

enum class PdfExportQuality(val label: String, val description: String) {
    SMALL("Small / Compressed", "Lower memory usage and smaller files"),
    BALANCED("Balanced", "Recommended for normal documents"),
    HIGH("High", "Sharper output for important scans")
}

enum class PdfPageSize(val label: String, val description: String) {
    AUTO("Auto", "Match page orientation with no extra margin"),
    A4("A4", "Standard document page with safe margins"),
    LETTER("Letter", "US Letter page with safe margins")
}
