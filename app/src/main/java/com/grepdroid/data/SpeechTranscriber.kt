package com.grepdroid.data

import android.content.Context
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineTransducerModelConfig
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig
import kotlinx.coroutines.ensureActive
import org.json.JSONArray
import org.json.JSONObject
import kotlin.coroutines.coroutineContext

/** One stretch of speech: where it is in the video and what was said. */
data class TranscriptSegment(val startMs: Long, val endMs: Long, val text: String)

data class Transcript(val segments: List<TranscriptSegment>) {
    val text: String get() = segments.joinToString("\n") { it.text }

    val segmentsJson: String get() {
        val arr = JSONArray()
        for (s in segments) arr.put(JSONObject().put("t", s.startMs).put("e", s.endMs).put("text", s.text))
        return arr.toString()
    }

    companion object {
        fun parseSegments(json: String): List<TranscriptSegment> {
            if (json.isBlank()) return emptyList()
            return try {
                val arr = JSONArray(json)
                (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    TranscriptSegment(o.getLong("t"), o.getLong("e"), o.getString("text"))
                }
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }
    }
}

/**
 * Turns a video's audio into text, fully on-device: a voice-activity detector
 * (Silero) cuts the audio into stretches of speech, and only those are fed to the
 * recognizer (Parakeet TDT 0.6B v3, which handles English, Dutch, German, French,
 * Spanish and 20 other European languages, and mixes of them, without being told
 * the language). Videos with no speech cost little more than decoding the audio.
 *
 * Loading the model takes a few seconds and ~1.3 GB of RAM, so one instance is
 * kept for a whole batch and [close]d afterwards. Not thread-safe.
 */
class SpeechTranscriber(
    private val context: Context,
    private val model: SpeechModel,
    val threads: Int
) : AutoCloseable {

    private val recognizer = OfflineRecognizer(
        assetManager = null,
        config = OfflineRecognizerConfig(
            featConfig = FeatureConfig(sampleRate = AudioDecoder.SAMPLE_RATE, featureDim = 80),
            modelConfig = OfflineModelConfig(
                transducer = OfflineTransducerModelConfig(
                    encoder = model.file(SpeechModel.ENCODER).absolutePath,
                    decoder = model.file(SpeechModel.DECODER).absolutePath,
                    joiner = model.file(SpeechModel.JOINER).absolutePath
                ),
                tokens = model.file(SpeechModel.TOKENS).absolutePath,
                modelType = "nemo_transducer",
                numThreads = threads,
                provider = "cpu",
                debug = false
            )
        )
    )

    /** Returns null when the video has no audio track. Throws if the audio can't be decoded. */
    suspend fun transcribe(contentUri: String): Transcript? {
        val ctx = coroutineContext
        val vad = Vad(
            assetManager = null,
            config = VadModelConfig(
                sileroVadModelConfig = SileroVadModelConfig(
                    model = model.file(SpeechModel.VAD).absolutePath,
                    threshold = 0.5f,
                    minSilenceDuration = 0.3f,
                    minSpeechDuration = 0.25f,
                    windowSize = VAD_WINDOW,
                    // Long monologues are cut into pieces of at most this length, which
                    // also bounds how much audio is held in memory at once.
                    maxSpeechDuration = 20f
                ),
                sampleRate = AudioDecoder.SAMPLE_RATE,
                numThreads = 1,
                provider = "cpu"
            )
        )
        try {
            val segments = mutableListOf<TranscriptSegment>()
            fun drain() {
                while (!vad.empty()) {
                    val speech = vad.front()
                    vad.pop()
                    ctx.ensureActive()
                    val text = recognize(speech.samples)
                    if (text.isNotEmpty()) {
                        val startMs = speech.start * 1000L / AudioDecoder.SAMPLE_RATE
                        val endMs = startMs + speech.samples.size * 1000L / AudioDecoder.SAMPLE_RATE
                        segments.add(TranscriptSegment(startMs, endMs, text))
                    }
                }
            }

            // The VAD wants fixed-size windows; carry the remainder over between chunks.
            val window = FloatArray(VAD_WINDOW)
            var filled = 0
            val hasAudio = AudioDecoder.decode(context, contentUri) { chunk ->
                ctx.ensureActive()
                var i = 0
                while (i < chunk.size) {
                    val n = minOf(VAD_WINDOW - filled, chunk.size - i)
                    System.arraycopy(chunk, i, window, filled, n)
                    filled += n
                    i += n
                    if (filled == VAD_WINDOW) {
                        vad.acceptWaveform(window)
                        filled = 0
                        drain()
                    }
                }
            }
            if (!hasAudio) return null
            vad.flush()
            drain()
            return Transcript(segments)
        } finally {
            vad.release()
        }
    }

    private fun recognize(samples: FloatArray): String {
        val stream = recognizer.createStream()
        try {
            stream.acceptWaveform(samples, AudioDecoder.SAMPLE_RATE)
            recognizer.decode(stream)
            return recognizer.getResult(stream).text.trim()
        } finally {
            stream.release()
        }
    }

    override fun close() {
        recognizer.release()
    }

    private companion object {
        const val VAD_WINDOW = 512
    }
}
