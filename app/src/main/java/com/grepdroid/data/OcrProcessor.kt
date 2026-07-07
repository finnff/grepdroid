package com.grepdroid.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.InputStream

class OcrProcessor(private val context: Context) {

    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun extractTextFromUri(contentUri: String): String = withContext(Dispatchers.IO) {
        try {
            val bitmap = loadAndScaleBitmap(contentUri)
            val rotation = readOrientationDegrees(contentUri)
            extractTextFromBitmap(bitmap, rotation, recycleAfter = true)
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    suspend fun extractTextFromBitmap(
        bitmap: Bitmap,
        rotationDegrees: Int = 0,
        recycleAfter: Boolean = false
    ): String = withContext(Dispatchers.IO) {
        try {
            val inputImage = InputImage.fromBitmap(bitmap, rotationDegrees)
            val visionText = textRecognizer.process(inputImage).await()
            visionText.text
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        } finally {
            if (recycleAfter && !bitmap.isRecycled) bitmap.recycle()
        }
    }

    private fun loadAndScaleBitmap(contentUri: String): Bitmap {
        val inputStream: InputStream = context.contentResolver.openInputStream(Uri.parse(contentUri))
            ?: throw IllegalStateException("Cannot open content URI")

        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }

        BitmapFactory.decodeStream(inputStream, null, options)
        inputStream.close()

        val maxDimension = 1920
        val scale = calculateSampleSize(options, maxDimension)

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = scale
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        val stream: InputStream = context.contentResolver.openInputStream(Uri.parse(contentUri))
            ?: throw IllegalStateException("Cannot open content URI")

        val bitmap = BitmapFactory.decodeStream(stream, null, decodeOptions)
        stream.close()

        return bitmap ?: throw IllegalStateException("Failed to decode bitmap")
    }

    private fun calculateSampleSize(options: BitmapFactory.Options, maxDimension: Int): Int {
        val (width, height) = options.outWidth to options.outHeight
        val maxSide = maxOf(width, height)

        return if (maxSide <= maxDimension) {
            1
        } else {
            var sampleSize = 1
            while (maxSide / sampleSize > maxDimension) {
                sampleSize *= 2
            }
            sampleSize
        }
    }

    /**
     * EXIF orientation that [BitmapFactory] ignores but ML Kit needs to read
     * the image upright. Returns clockwise degrees (0/90/180/270) for content
     * URIs, 0 on any error or for formats without orientation metadata.
     */
    private fun readOrientationDegrees(contentUri: String): Int =
        try {
            context.contentResolver.openFileDescriptor(Uri.parse(contentUri), "r")?.use { pfd ->
                val exif = ExifInterface(pfd.fileDescriptor)
                when (exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            } ?: 0
        } catch (e: Exception) {
            e.printStackTrace()
            0
        }

    fun cleanup() {
        textRecognizer.close()
    }
}
