package com.grepdroid.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream

class OcrProcessor(private val context: Context) {
    
    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    
    suspend fun extractTextFromUri(contentUri: String): String = withContext(Dispatchers.IO) {
        try {
            val bitmap = loadAndScaleBitmap(contentUri)
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            
            val visionText = textRecognizer.process(inputImage).await()
            val text = visionText.text ?: ""
            
            visionText.close()
            bitmap.recycle()
            
            text
        } catch (e: Exception) {
            e.printStackTrace()
            ""
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
    
    fun cleanup() {
        textRecognizer.close()
    }
}
