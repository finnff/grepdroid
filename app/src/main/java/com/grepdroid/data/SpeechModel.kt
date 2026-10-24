package com.grepdroid.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

/**
 * The speech recognition model (NVIDIA Parakeet TDT 0.6B v3, int8, packaged for
 * sherpa-onnx) plus the Silero voice-activity detector. Too big to ship in the APK,
 * so it is downloaded once on request — the only thing the app ever fetches from
 * the network. Each file is pinned by size and SHA-256.
 */
class SpeechModel(context: Context) {

    class ModelFile(val name: String, val url: String, val size: Long, val sha256: String)

    sealed class State {
        data object Missing : State()
        data class Downloading(val downloadedBytes: Long, val totalBytes: Long) : State()
        data object Ready : State()
        data class Failed(val message: String) : State()
    }

    val dir = File(context.filesDir, "speech/parakeet-tdt-0.6b-v3-int8")

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var downloadJob: Job? = null

    private val _state = MutableStateFlow(if (isDownloaded()) State.Ready else State.Missing)
    val state: StateFlow<State> = _state.asStateFlow()

    val isReady: Boolean get() = _state.value == State.Ready

    fun file(name: String) = File(dir, name)

    private fun isDownloaded() = FILES.all { file(it.name).length() == it.size }

    fun download() {
        if (downloadJob?.isActive == true || isReady) return
        downloadJob = scope.launch {
            try {
                dir.mkdirs()
                var done = FILES.filter { file(it.name).length() == it.size }.sumOf { it.size }
                _state.value = State.Downloading(done, TOTAL_BYTES)
                for (f in FILES) {
                    if (file(f.name).length() == f.size) continue
                    fetch(f) { bytes -> _state.value = State.Downloading(done + bytes, TOTAL_BYTES) }
                    done += f.size
                }
                _state.value = State.Ready
            } catch (e: kotlinx.coroutines.CancellationException) {
                _state.value = State.Missing
                throw e
            } catch (e: Exception) {
                e.printStackTrace()
                _state.value = State.Failed(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    /** Keeps the partial download so a later [download] resumes where it stopped. */
    fun cancelDownload() {
        downloadJob?.cancel()
    }

    fun delete() {
        downloadJob?.cancel()
        dir.deleteRecursively()
        _state.value = State.Missing
    }

    /**
     * Downloads [f] into a `.part` file (resuming it if one exists), checks the
     * hash, then moves it into place. [onProgress] gets bytes of this file so far.
     */
    private suspend fun fetch(f: ModelFile, onProgress: (Long) -> Unit) {
        val part = File(dir, f.name + ".part")
        if (part.length() > f.size) part.delete()
        val digest = MessageDigest.getInstance("SHA-256")
        if (part.exists()) {
            part.inputStream().use { input ->
                val buf = ByteArray(1 shl 16)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    digest.update(buf, 0, n)
                }
            }
        }
        var have = part.length()

        if (have < f.size) {
            val conn = URL(f.url).openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = 30_000
                conn.readTimeout = 60_000
                if (have > 0) conn.setRequestProperty("Range", "bytes=$have-")
                val code = conn.responseCode
                if (have > 0 && code == HttpURLConnection.HTTP_OK) {
                    // Server ignored the Range header; start this file over.
                    have = 0
                    digest.reset()
                } else if (code != HttpURLConnection.HTTP_OK && code != HttpURLConnection.HTTP_PARTIAL) {
                    throw IOException("HTTP $code for ${f.name}")
                }
                conn.inputStream.use { input ->
                    FileOutputStream(part, have > 0).use { out ->
                        val buf = ByteArray(1 shl 16)
                        var lastReport = 0L
                        while (true) {
                            coroutineContext.ensureActive()
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            digest.update(buf, 0, n)
                            have += n
                            if (have - lastReport >= 1 shl 20) {
                                lastReport = have
                                onProgress(have)
                            }
                        }
                    }
                }
            } finally {
                conn.disconnect()
            }
        }

        val hash = digest.digest().joinToString("") { "%02x".format(it) }
        if (have != f.size || hash != f.sha256) {
            part.delete()
            throw IOException("${f.name} is corrupt, please retry")
        }
        if (!part.renameTo(file(f.name))) throw IOException("Couldn't save ${f.name}")
    }

    companion object {
        private const val HF =
            "https://huggingface.co/csukuangfj/sherpa-onnx-nemo-parakeet-tdt-0.6b-v3-int8/resolve/2bda32ec70b097a55adaa07d9a7173915b43cc78"

        const val ENCODER = "encoder.int8.onnx"
        const val DECODER = "decoder.int8.onnx"
        const val JOINER = "joiner.int8.onnx"
        const val TOKENS = "tokens.txt"
        const val VAD = "silero_vad.onnx"

        val FILES = listOf(
            ModelFile(TOKENS, "$HF/$TOKENS", 93_939, "d58544679ea4bc6ac563d1f545eb7d474bd6cfa467f0a6e2c1dc1c7d37e3c35d"),
            ModelFile(
                VAD, "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/silero_vad.onnx",
                643_854, "9e2449e1087496d8d4caba907f23e0bd3f78d91fa552479bb9c23ac09cbb1fd6"
            ),
            ModelFile(JOINER, "$HF/$JOINER", 6_355_277, "3164c13fc2821009440d20fcb5fdc78bff28b4db2f8d0f0b329101719c0948b3"),
            ModelFile(DECODER, "$HF/$DECODER", 11_845_275, "179e50c43d1a9de79c8a24149a2f9bac6eb5981823f2a2ed88d655b24248db4e"),
            ModelFile(ENCODER, "$HF/$ENCODER", 652_184_281, "acfc2b4456377e15d04f0243af540b7fe7c992f8d898d751cf134c3a55fd2247")
        )

        val TOTAL_BYTES = FILES.sumOf { it.size }
    }
}
