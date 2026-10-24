package com.grepdroid.data

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.nio.ByteOrder

/**
 * Decodes a video's first audio track to 16 kHz mono float PCM, handing it out in
 * chunks so an hour-long video never has to fit in memory at once.
 */
object AudioDecoder {

    const val SAMPLE_RATE = 16000

    /**
     * Calls [onChunk] with consecutive 16 kHz mono samples (each array is only valid
     * during the call). Returns false when the file has no audio track.
     */
    fun decode(context: Context, contentUri: String, onChunk: (FloatArray) -> Unit): Boolean {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, Uri.parse(contentUri), null)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return false
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val decoder = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
            codec = decoder
            decoder.configure(format, null, null, 0)
            decoder.start()

            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var floatPcm = false
            var resampler = Resampler(format.getInteger(MediaFormat.KEY_SAMPLE_RATE), SAMPLE_RATE)
            var mono = FloatArray(0)
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            while (true) {
                if (!inputDone) {
                    val i = decoder.dequeueInputBuffer(10_000)
                    if (i >= 0) {
                        val n = extractor.readSampleData(decoder.getInputBuffer(i)!!, 0)
                        if (n < 0) {
                            decoder.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            decoder.queueInputBuffer(i, 0, n, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val o = decoder.dequeueOutputBuffer(info, 10_000)
                if (o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val f = decoder.outputFormat
                    channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    floatPcm = f.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                        f.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                    val rate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    if (rate != resampler.from) resampler = Resampler(rate, SAMPLE_RATE)
                } else if (o >= 0) {
                    val buf = decoder.getOutputBuffer(o)!!.order(ByteOrder.nativeOrder())
                    buf.position(info.offset)
                    buf.limit(info.offset + info.size)
                    // Downmix to mono by averaging the channels of each frame.
                    var frames = 0
                    if (floatPcm) {
                        val fb = buf.asFloatBuffer()
                        if (mono.size < fb.remaining() / channels) mono = FloatArray(fb.remaining() / channels)
                        while (fb.remaining() >= channels) {
                            var s = 0f
                            repeat(channels) { s += fb.get() }
                            mono[frames++] = s / channels
                        }
                    } else {
                        val sb = buf.asShortBuffer()
                        if (mono.size < sb.remaining() / channels) mono = FloatArray(sb.remaining() / channels)
                        while (sb.remaining() >= channels) {
                            var s = 0f
                            repeat(channels) { s += sb.get() / 32768f }
                            mono[frames++] = s / channels
                        }
                    }
                    decoder.releaseOutputBuffer(o, false)
                    val out = resampler.process(mono, frames)
                    if (out.isNotEmpty()) onChunk(out)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                }
            }
            return true
        } finally {
            try {
                codec?.stop()
            } catch (_: Exception) {
            }
            codec?.release()
            extractor.release()
        }
    }

    /**
     * Streaming sample-rate converter: averages each output sample's span of input
     * when downsampling (a cheap anti-aliasing filter), linearly interpolates when
     * upsampling. Keeps the not-yet-consumed tail of the input between calls.
     */
    internal class Resampler(val from: Int, private val to: Int) {
        private val ratio = from.toDouble() / to
        private var pending = FloatArray(0)
        private var pendingSize = 0
        /** Absolute input index of pending[0]. */
        private var base = 0L
        /** Absolute index of the next output sample. */
        private var next = 0L

        fun process(input: FloatArray, count: Int): FloatArray {
            if (from == to) return input.copyOf(count)
            if (pending.size < pendingSize + count) pending = pending.copyOf(maxOf(pendingSize + count, pending.size * 2))
            System.arraycopy(input, 0, pending, pendingSize, count)
            pendingSize += count

            val end = base + pendingSize
            val out = FloatArray(((end / ratio).toLong() - next + 2).coerceAtLeast(0).toInt())
            var n = 0
            while (true) {
                if (ratio > 1) {
                    val a = (next * ratio).toLong()
                    val b = ((next + 1) * ratio).toLong().coerceAtLeast(a + 1)
                    if (b > end) break
                    var s = 0f
                    for (j in a until b) s += pending[(j - base).toInt()]
                    out[n++] = s / (b - a)
                } else {
                    val x = next * ratio
                    val a = x.toLong()
                    if (a + 1 >= end) break
                    val t = (x - a).toFloat()
                    out[n++] = pending[(a - base).toInt()] * (1 - t) + pending[(a + 1 - base).toInt()] * t
                }
                next++
            }
            // Drop input that no future output sample reads.
            val keepFrom = (next * ratio).toLong().coerceIn(base, end)
            val drop = (keepFrom - base).toInt()
            System.arraycopy(pending, drop, pending, 0, pendingSize - drop)
            pendingSize -= drop
            base = keepFrom
            return out.copyOf(n)
        }
    }
}
