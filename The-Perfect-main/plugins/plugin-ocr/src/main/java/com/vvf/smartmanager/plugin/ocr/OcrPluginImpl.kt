package com.vvf.smartmanager.plugin.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.media.ExifInterface
import android.os.ParcelFileDescriptor
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.vvf.smartmanager.core.model.FileItem
import com.vvf.smartmanager.core.model.OcrBlock
import com.vvf.smartmanager.core.model.OcrOptions
import com.vvf.smartmanager.core.model.OcrProgress
import com.vvf.smartmanager.core.model.OcrResult
import com.vvf.smartmanager.core.plugin.spi.OcrPluginSPI
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max

/**
 * High-Performance, Memory-Safe On-Device ML Kit OCR Text Extraction Plugin.
 * Extends [OcrEnginePlugin] to maintain backward compatibility while conforming to [IOcrEngine].
 */
class OcrPluginImpl(
    context: Context? = null
) : OcrEnginePlugin(context)

