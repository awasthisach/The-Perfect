package com.vvf.smartmanager.core.common

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.util.Log
import java.io.File
import kotlin.math.max

/**
 * Bitmap utility class for safe, sampled image decoding and orientation handling.
 * Specifically engineered to prevent Out-Of-Memory (OOM) crashes during OCR and file processing
 * of ultra-high-resolution photographs and large documents.
 */
object BitmapUtils {

    private const val TAG = "BitmapUtils"

    /**
     * Decodes a memory-efficient downsampled Bitmap from a file path based on a maximum bounding dimension.
     * Uses [Bitmap.Config.RGB_565] by default to save 50% memory over ARGB_8888 for OCR tasks.
     *
     * @param filePath Absolute path of the image file on device storage.
     * @param maxDimension The maximum allowed width or height in pixels.
     * @param config Preferred Bitmap color configuration (RGB_565 recommended for text analysis).
     * @param autoRotate Whether to automatically rotate the bitmap to match EXIF metadata.
     * @return Decoded and sampled [Bitmap], or null if decoding fails.
     */
    fun decodeSampledBitmapFromFile(
        filePath: String,
        maxDimension: Int = 2048,
        config: Bitmap.Config = Bitmap.Config.RGB_565,
        autoRotate: Boolean = true
    ): Bitmap? {
        val file = File(filePath)
        if (!file.exists() || !file.canRead()) {
            Log.w(TAG, "File does not exist or cannot be read: $filePath")
            return null
        }

        return try {
            // First pass: Read dimensions only without allocating full pixel memory
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(filePath, options)

            if (options.outWidth <= 0 || options.outHeight <= 0) {
                Log.w(TAG, "Invalid image bounds for file: $filePath (${options.outWidth}x${options.outHeight})")
                return null
            }

            // Calculate power-of-two sampling factor
            var inSampleSize = 1
            var halfWidth = options.outWidth
            var halfHeight = options.outHeight

            while (halfWidth > maxDimension || halfHeight > maxDimension) {
                inSampleSize *= 2
                halfWidth /= 2
                halfHeight /= 2
            }

            // Second pass: Decode actual bitmap with calculated inSampleSize
            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                this.inPreferredConfig = config
            }

            val decodedBitmap = BitmapFactory.decodeFile(filePath, decodeOptions) ?: return null

            if (autoRotate) {
                rotateBitmapIfRequired(decodedBitmap, filePath)
            } else {
                decodedBitmap
            }
        } catch (oom: OutOfMemoryError) {
            Log.e(TAG, "OOM caught while decoding image: $filePath. Falling back to extreme downsampling.", oom)
            decodeExtremeFallback(filePath, config)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode image from path: $filePath", e)
            null
        }
    }

    /**
     * Decodes a bitmap with specified target width and height constraints.
     */
    fun decodeSampledBitmapFromFile(
        filePath: String,
        reqWidth: Int,
        reqHeight: Int,
        config: Bitmap.Config = Bitmap.Config.RGB_565,
        autoRotate: Boolean = true
    ): Bitmap? {
        val maxDim = max(reqWidth, reqHeight)
        return decodeSampledBitmapFromFile(filePath, maxDim, config, autoRotate)
    }

    /**
     * Calculates the optimal inSampleSize factor for given dimensions.
     */
    fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.outHeight to options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2

            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    /**
     * Reads EXIF orientation from an image file and returns the required rotation degrees.
     */
    fun getExifRotationAngle(filePath: String): Float {
        return try {
            val exif = ExifInterface(filePath)
            when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not read EXIF data for $filePath: ${e.message}")
            0f
        }
    }

    /**
     * Rotates a Bitmap if required according to EXIF metadata, recycling the previous bitmap
     * if a new one was instantiated to prevent memory leaks.
     */
    fun rotateBitmapIfRequired(bitmap: Bitmap, filePath: String): Bitmap {
        val rotationAngle = getExifRotationAngle(filePath)
        if (rotationAngle == 0f) return bitmap

        return try {
            val matrix = Matrix().apply { postRotate(rotationAngle) }
            val rotatedBitmap = Bitmap.createBitmap(
                bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
            )
            if (rotatedBitmap != bitmap) {
                bitmap.recycle()
            }
            rotatedBitmap
        } catch (oom: OutOfMemoryError) {
            Log.w(TAG, "OOM while rotating bitmap, returning original unrotated bitmap.", oom)
            bitmap
        } catch (e: Exception) {
            Log.w(TAG, "Failed to rotate bitmap: ${e.message}")
            bitmap
        }
    }

    /**
     * Safely recycles a Bitmap and prevents subsequent usage crashes.
     */
    fun recycleSafely(bitmap: Bitmap?) {
        try {
            if (bitmap != null && !bitmap.isRecycled) {
                bitmap.recycle()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error recycling bitmap: ${e.message}")
        }
    }

    /**
     * Reads width and height of an image file without allocating pixel memory.
     */
    fun getImageDimensions(filePath: String): Pair<Int, Int>? {
        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(filePath, options)
            if (options.outWidth > 0 && options.outHeight > 0) {
                options.outWidth to options.outHeight
            } else null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Extreme downsample fallback when low-memory conditions occur.
     */
    private fun decodeExtremeFallback(filePath: String, config: Bitmap.Config): Bitmap? {
        return try {
            val options = BitmapFactory.Options().apply {
                inSampleSize = 8
                inPreferredConfig = config
            }
            BitmapFactory.decodeFile(filePath, options)
        } catch (e: Exception) {
            Log.e(TAG, "Extreme fallback failed: ${e.message}")
            null
        }
    }
}
