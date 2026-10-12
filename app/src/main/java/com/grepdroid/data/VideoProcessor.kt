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
    private val frameIntervalMs: Long = DEFAULT_FRAME_INTERVAL_MS,
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
        val dir = File(context.filesDir, "video_thumbs").apply { mkdirs() }
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

    fun deleteThumbnail(mediaStoreId: Long) {
        val file = File(File(context.filesDir, "video_thumbs"), "thumb_$mediaStoreId.jpg")
        if (file.exists()) file.delete()
    }

    /**
     * Regenerates only the thumbnail for a video (no OCR), used by the
     * "repair missing thumbnails" pass. Samples a frame ~10% into the clip for
     * a more representative still than the very first frame, writing it to
     * filesDir via [saveThumbnail].
     */
    suspend fun regenerateThumbnail(contentUri: String, mediaStoreId: Long): String? =
        withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, Uri.parse(contentUri))
                val durationMs = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_DURATION
                )?.toLongOrNull() ?: 0L
                val timeUs = if (durationMs > 0L) durationMs * 100L else 0L
                val bitmap = retrieveScaledFrame(retriever, timeUs) ?: return@withContext null
                val path = saveThumbnail(bitmap, mediaStoreId)
                bitmap.recycle()
                path.takeIf { it.isNotBlank() }
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

    companion object {
        /** Gap between OCR'd frames; also how long each search hit is assumed to stay on screen. */
        const val DEFAULT_FRAME_INTERVAL_MS = 1000L

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
        /**
         * Decodes a single downscaled frame for the grid press-and-hold preview.
         * Returns null on any failure so the caller keeps showing the static
         * thumbnail. Uses [MediaMetadataRetriever.getScaledFrameAtTime] on API
         * 27+; on API 26 falls back to a full decode plus a manual downscale.
         */
        fun decodePreviewFrame(
            retriever: MediaMetadataRetriever,
            timeUs: Long,
            maxDimension: Int = 256
        ): Bitmap? = try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
                retriever.getScaledFrameAtTime(
                    timeUs,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                    maxDimension,
                    maxDimension
                ) ?: retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?.let { scaleDown(it, maxDimension) }
            } else {
                retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?.let { scaleDown(it, maxDimension) }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }

        private fun scaleDown(src: Bitmap, maxDimension: Int): Bitmap {
            val maxSide = maxOf(src.width, src.height)
            if (maxSide <= maxDimension) return src
            val ratio = maxDimension.toFloat() / maxSide
            val scaled = Bitmap.createScaledBitmap(
                src,
                (src.width * ratio).toInt().coerceAtLeast(1),
                (src.height * ratio).toInt().coerceAtLeast(1),
                true
            )
            if (scaled !== src) src.recycle()
            return scaled
        }
    }
}
