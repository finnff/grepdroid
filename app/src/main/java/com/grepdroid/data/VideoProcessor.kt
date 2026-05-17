package com.grepdroid.data

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

data class FrameOcr(val timestampMs: Long, val text: String)

data class ProcessedVideo(
    val durationMs: Long,
    val frames: List<FrameOcr>,
    val thumbnailPath: String
) {
    val concatenatedText: String get() = frames.joinToString("\n") { it.text }.trim()

    val framesJson: String get() {
        val arr = JSONArray()
        for (f in frames) {
            if (f.text.isBlank()) continue
            arr.put(JSONObject().put("t", f.timestampMs).put("text", f.text))
        }
        return arr.toString()
    }
}

class VideoProcessor(
    private val context: Context,
    private val ocrProcessor: OcrProcessor,
    private val frameIntervalMs: Long = 1000L,
    private val maxFrameDimension: Int = 1280
) {

    suspend fun process(contentUri: String, mediaStoreId: Long): ProcessedVideo? =
        withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, Uri.parse(contentUri))
                val durationMs = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_DURATION
                )?.toLongOrNull() ?: 0L

                if (durationMs <= 0L) return@withContext null

                val frames = mutableListOf<FrameOcr>()
                var thumbnailBitmap: Bitmap? = null

                var timeMs = 0L
                while (timeMs < durationMs) {
                    val bitmap = retrieveScaledFrame(retriever, timeMs * 1000L)
                    if (bitmap != null) {
                        if (thumbnailBitmap == null) {
                            thumbnailBitmap = bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, false)
                        }
                        val text = ocrProcessor.extractTextFromBitmap(bitmap, recycleAfter = false)
                        frames.add(FrameOcr(timeMs, text))
                        bitmap.recycle()
                    }
                    timeMs += frameIntervalMs
                }

                val thumbPath = thumbnailBitmap?.let { saveThumbnail(it, mediaStoreId) } ?: ""
                thumbnailBitmap?.recycle()

                ProcessedVideo(
                    durationMs = durationMs,
                    frames = frames,
                    thumbnailPath = thumbPath
                )
            } catch (e: Exception) {
                e.printStackTrace()
                null
            } finally {
                try {
                    retriever.release()
                } catch (_: Exception) {
                }
            }
        }

    private fun retrieveScaledFrame(retriever: MediaMetadataRetriever, timeUs: Long): Bitmap? {
        return try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
                retriever.getScaledFrameAtTime(
                    timeUs,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                    maxFrameDimension,
                    maxFrameDimension
                ) ?: retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            } else {
                retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun saveThumbnail(bitmap: Bitmap, mediaStoreId: Long): String {
        val dir = File(context.cacheDir, "video_thumbs").apply { mkdirs() }
        val file = File(dir, "thumb_$mediaStoreId.jpg")
        return try {
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
            file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    companion object {
        fun parseFrames(framesJson: String): List<FrameOcr> {
            if (framesJson.isBlank()) return emptyList()
            return try {
                val arr = JSONArray(framesJson)
                val out = mutableListOf<FrameOcr>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    out.add(FrameOcr(obj.getLong("t"), obj.getString("text")))
                }
                out
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }
    }
}
