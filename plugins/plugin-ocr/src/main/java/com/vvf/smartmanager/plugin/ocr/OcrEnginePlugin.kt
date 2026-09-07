package com.vvf.smartmanager.plugin.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.vvf.smartmanager.core.common.BitmapUtils
import com.vvf.smartmanager.core.model.FileItem
import com.vvf.smartmanager.core.model.OcrBlock
import com.vvf.smartmanager.core.model.OcrOptions
import com.vvf.smartmanager.core.model.OcrProgress
import com.vvf.smartmanager.core.model.OcrResult
import com.vvf.smartmanager.core.plugin.spi.IOcrEngine
import com.vvf.smartmanager.core.plugin.spi.OcrPluginSPI
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max

/**
 * OCR Engine Plugin — dual-script (Latin/English + Devanagari/Hindi).
 *
 * Runs both ML Kit recognizers on every image/PDF page and merges text so mixed
 * English+Hindi documents work without the user picking a language.
 */
open class OcrEnginePlugin(
    private val context: Context? = null
) : IOcrEngine, OcrPluginSPI {

    override val pluginId: String = "plugin.ocr.mlkit"
    override val displayName: String = "ML Kit OCR (Latin + Devanagari)"
    override val version: String = "1.1.0"

    private var _isEnabled: Boolean = true
    override val isEnabled: Boolean get() = _isEnabled

    private val isCancelled = AtomicBoolean(false)
    private var isModelReady = true

    fun setEnabled(enabled: Boolean) {
        _isEnabled = enabled
    }

    override suspend fun isModelDownloaded(): Boolean = isModelReady

    override suspend fun downloadModel(progressCallback: (Float) -> Unit): Boolean {
        withContext(Dispatchers.IO) {
            for (step in 1..5) {
                kotlinx.coroutines.delay(100)
                progressCallback(step / 5f)
            }
            isModelReady = true
        }
        return true
    }

    override fun cancelOngoing() {
        isCancelled.set(true)
    }

    override suspend fun extractText(
        fileItem: FileItem,
        options: OcrOptions,
        onProgress: ((OcrProgress) -> Unit)?
    ): Result<OcrResult> = withContext(Dispatchers.IO) {
        isCancelled.set(false)
        val startTime = System.currentTimeMillis()
        var temporaryInput: File? = null

        try {
            temporaryInput = fileItem.canonicalUri
                ?.takeIf { it.startsWith("content://") }
                ?.let { materializeContentUri(it, fileItem.name) }
            val file = temporaryInput ?: File(fileItem.path)
            if (!file.exists() || !file.canRead()) {
                return@withContext Result.failure(
                    IllegalArgumentException("File not found or unreadable: ${fileItem.path}")
                )
            }

            val extension = fileItem.name.substringAfterLast('.', file.extension).lowercase()
            when (extension) {
                "pdf" -> processPdfFile(file, options, startTime, onProgress)
                "jpg", "jpeg", "png", "webp", "bmp", "heic" ->
                    processImageFile(file, options, startTime, onProgress)
                else -> processImageFile(file, options, startTime, onProgress)
            }
        } catch (ce: CancellationException) {
            onProgress?.invoke(OcrProgress(currentStep = "Scan Cancelled", isCancelled = true))
            Result.failure(ce)
        } catch (e: Exception) {
            Log.e(TAG, "OCR processing failed for ${fileItem.path}", e)
            Result.failure(e)
        } finally {
            temporaryInput?.delete()
        }
    }

    private fun materializeContentUri(uriString: String, displayName: String): File {
        val appContext = context
            ?: throw IllegalStateException("OCR cannot read a document-provider URI without an Android context")
        val extension = displayName.substringAfterLast('.', "").takeIf { it.isNotBlank() } ?: "bin"
        val cacheDirectory = File(appContext.cacheDir, "ocr_inputs").apply { mkdirs() }
        val target = File.createTempFile("ocr_", ".${extension}", cacheDirectory)
        try {
            appContext.contentResolver.openInputStream(Uri.parse(uriString))?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: throw IllegalArgumentException("Could not open selected document")
            return target
        } catch (error: Throwable) {
            target.delete()
            throw error
        }
    }

    /** Latin + Devanagari recognizers — closed after each batch to free native resources. */
    private fun createRecognizers(): Pair<TextRecognizer, TextRecognizer> {
        val latin = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val devanagari = TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build())
        return latin to devanagari
    }

    private suspend fun recognizeDual(
        bitmap: Bitmap,
        latin: TextRecognizer,
        devanagari: TextRecognizer
    ): Pair<String, List<OcrBlock>> = coroutineScope {
        val inputImage = InputImage.fromBitmap(bitmap, 0)
        val latinDeferred = async(Dispatchers.IO) {
            try {
                Tasks.await(latin.process(inputImage)).text.trim()
            } catch (e: Exception) {
                Log.w(TAG, "Latin OCR failed", e)
                ""
            }
        }
        val devDeferred = async(Dispatchers.IO) {
            try {
                Tasks.await(devanagari.process(inputImage)).text.trim()
            } catch (e: Exception) {
                Log.w(TAG, "Devanagari OCR failed", e)
                ""
            }
        }
        val latinText = latinDeferred.await()
        val devText = devDeferred.await()
        val merged = mergeScriptTexts(latinText, devText)
        val blocks = buildList {
            if (latinText.isNotBlank()) add(OcrBlock(text = latinText, confidence = 1.0f))
            if (devText.isNotBlank() && !latinText.contains(devText)) {
                add(OcrBlock(text = devText, confidence = 1.0f))
            }
        }
        merged to blocks
    }

    /** Prefer longer combined text; avoid exact duplicates. */
    private fun mergeScriptTexts(latin: String, devanagari: String): String {
        val a = latin.trim()
        val b = devanagari.trim()
        return when {
            a.isBlank() -> b
            b.isBlank() -> a
            a == b -> a
            a.contains(b) -> a
            b.contains(a) -> b
            else -> "$a\n\n$b"
        }
    }

    private suspend fun processImageFile(
        file: File,
        options: OcrOptions,
        startTime: Long,
        onProgress: ((OcrProgress) -> Unit)?
    ): Result<OcrResult> {
        currentCoroutineContext().ensureActive()
        if (isCancelled.get()) throw CancellationException("OCR scan cancelled by user")

        onProgress?.invoke(
            OcrProgress(
                currentStep = "Decoding image...",
                progressFraction = 0.15f,
                currentPage = 1,
                totalPages = 1
            )
        )

        val bitmap = BitmapUtils.decodeSampledBitmapFromFile(
            filePath = file.absolutePath,
            maxDimension = options.maxDimension,
            config = Bitmap.Config.RGB_565,
            autoRotate = options.autoRotate
        ) ?: return Result.failure(IllegalStateException("Failed to decode image: ${file.name}"))

        val (latin, devanagari) = createRecognizers()
        try {
            currentCoroutineContext().ensureActive()
            if (isCancelled.get()) throw CancellationException("OCR scan cancelled by user")

            onProgress?.invoke(
                OcrProgress(
                    currentStep = "Extracting text (English + Hindi)...",
                    progressFraction = 0.45f,
                    currentPage = 1,
                    totalPages = 1
                )
            )

            val (fullText, blocks) = recognizeDual(bitmap, latin, devanagari)
            val words = if (fullText.isNotBlank()) fullText.split("\\s+".toRegex()).size else 0
            val lines = if (fullText.isNotBlank()) fullText.lines().size else 0
            val duration = System.currentTimeMillis() - startTime

            onProgress?.invoke(
                OcrProgress(
                    currentStep = "Scan complete",
                    progressFraction = 1.0f,
                    currentPage = 1,
                    totalPages = 1,
                    isComplete = true
                )
            )

            return Result.success(
                OcrResult(
                    fullText = fullText,
                    blocks = blocks,
                    totalWords = words,
                    totalLines = lines,
                    pageCount = 1,
                    language = "latin+devanagari",
                    processingDurationMs = duration,
                    sourceFilePath = file.absolutePath
                )
            )
        } finally {
            BitmapUtils.recycleSafely(bitmap)
            try { latin.close() } catch (_: Exception) {}
            try { devanagari.close() } catch (_: Exception) {}
        }
    }

    private suspend fun processPdfFile(
        file: File,
        options: OcrOptions,
        startTime: Long,
        onProgress: ((OcrProgress) -> Unit)?
    ): Result<OcrResult> {
        currentCoroutineContext().ensureActive()
        if (isCancelled.get()) throw CancellationException("OCR scan cancelled by user")

        var pfd: ParcelFileDescriptor? = null
        var pdfRenderer: PdfRenderer? = null
        val (latin, devanagari) = createRecognizers()

        try {
            pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            pdfRenderer = PdfRenderer(pfd)
            val totalPages = pdfRenderer.pageCount.coerceAtMost(options.maxPagesForPdf)

            val fullTextBuilder = StringBuilder()
            val allBlocks = mutableListOf<OcrBlock>()

            for (pageIndex in 0 until totalPages) {
                currentCoroutineContext().ensureActive()
                if (isCancelled.get()) throw CancellationException("OCR scan cancelled by user")

                onProgress?.invoke(
                    OcrProgress(
                        currentStep = "Scanning PDF page ${pageIndex + 1}/$totalPages (EN+HI)...",
                        progressFraction = pageIndex.toFloat() / totalPages,
                        currentPage = pageIndex + 1,
                        totalPages = totalPages
                    )
                )

                val page = pdfRenderer.openPage(pageIndex)
                val scale = (options.maxDimension.toFloat() / max(page.width, page.height)).coerceAtMost(2.0f)
                val renderWidth = (page.width * scale).toInt().coerceAtLeast(100)
                val renderHeight = (page.height * scale).toInt().coerceAtLeast(100)
                val pageBitmap = Bitmap.createBitmap(renderWidth, renderHeight, Bitmap.Config.ARGB_8888)
                page.render(pageBitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()

                try {
                    val (pageText, pageBlocks) = recognizeDual(pageBitmap, latin, devanagari)
                    if (pageText.isNotBlank()) {
                        if (fullTextBuilder.isNotEmpty()) {
                            fullTextBuilder.append("\n\n--- [Page ${pageIndex + 1}] ---\n\n")
                        }
                        fullTextBuilder.append(pageText)
                        allBlocks.addAll(pageBlocks)
                    }
                } finally {
                    BitmapUtils.recycleSafely(pageBitmap)
                }
            }

            val finalFullText = fullTextBuilder.toString().trim()
            val words = if (finalFullText.isNotBlank()) finalFullText.split("\\s+".toRegex()).size else 0
            val lines = if (finalFullText.isNotBlank()) finalFullText.lines().size else 0
            val duration = System.currentTimeMillis() - startTime

            onProgress?.invoke(
                OcrProgress(
                    currentStep = "PDF scan complete ($totalPages pages)",
                    progressFraction = 1.0f,
                    currentPage = totalPages,
                    totalPages = totalPages,
                    isComplete = true
                )
            )

            return Result.success(
                OcrResult(
                    fullText = finalFullText,
                    blocks = allBlocks,
                    totalWords = words,
                    totalLines = lines,
                    pageCount = totalPages,
                    language = "latin+devanagari",
                    processingDurationMs = duration,
                    sourceFilePath = file.absolutePath
                )
            )
        } finally {
            pdfRenderer?.close()
            pfd?.close()
            try { latin.close() } catch (_: Exception) {}
            try { devanagari.close() } catch (_: Exception) {}
        }
    }

    companion object {
        private const val TAG = "OcrEnginePlugin"
    }
}
