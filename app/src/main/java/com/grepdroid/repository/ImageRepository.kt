package com.grepdroid.repository

import com.grepdroid.data.FtsQuery
import com.grepdroid.data.FuzzyMatcher
import com.grepdroid.data.MediaStoreScanner
import com.grepdroid.data.OcrProcessor
import com.grepdroid.data.SpeechEngine
import com.grepdroid.data.SpeechTranscriber
import com.grepdroid.data.UserPreferences
import com.grepdroid.data.VideoProcessor
import com.grepdroid.data.db.ImageDao
import com.grepdroid.data.db.ScannedVideo
import com.grepdroid.data.db.VideoDao
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.atomic.AtomicInteger
import androidx.sqlite.db.SimpleSQLiteQuery

data class ScanProgress(
    val processed: Int = 0,
    val total: Int = 0,
    val estimatedSecondsRemaining: Long? = null,
    /** What's being worked on, when it isn't plain OCR (e.g. "Transcribing speech"). */
    val label: String? = null
) {
    val fraction: Float get() = if (total == 0) 0f else processed.toFloat() / total
}

class ImageRepository(
    private val dao: ImageDao,
    private val mediaStoreScanner: MediaStoreScanner,
    private val ocrProcessor: OcrProcessor,
    private val userPreferences: UserPreferences? = null,
    private val dispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO,
    private val videoDao: VideoDao? = null,
    private val videoProcessor: VideoProcessor? = null,
    private val speechEngine: SpeechEngine? = null
) {

    private val scanSemaphore = Semaphore(4)
    private val videoScanSemaphore = Semaphore(2)
    private var scanJob: Job? = null
    private var regenJob: Job? = null
    private val scope = CoroutineScope(dispatcher)

    private val _scanProgress = MutableStateFlow(ScanProgress())
    val scanProgress: StateFlow<ScanProgress> = _scanProgress.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    fun search(query: String) =
        FtsQuery.sanitize(query)?.let { match ->
            dao.search(match)
                .catch { emit(emptyList()) }
                .flowOn(Dispatchers.IO)
        } ?: flowOf(emptyList())

    fun fuzzySearch(query: String): Flow<List<FuzzyMatcher.ScoredResult>> = flow {
        val words = splitFuzzyWords(query)
        if (words.isEmpty()) { emit(emptyList()); return@flow }
        // Pre-filter in SQL so we never load the whole corpus into memory; the
        // matcher then re-ranks the candidates with its exact scoring.
        val candidates = dao.searchFuzzyCandidates(fuzzyLikeQuery("scanned_images", words))
        emit(FuzzyMatcher.search(candidates, query))
    }.flowOn(Dispatchers.IO)

    fun searchVideos(query: String): Flow<List<ScannedVideo>> =
        FtsQuery.sanitize(query)?.let { match ->
            (videoDao?.search(match) ?: flowOf(emptyList()))
                .catch { emit(emptyList()) }
                .flowOn(Dispatchers.IO)
        } ?: flowOf(emptyList())

    fun fuzzySearchVideos(query: String): Flow<List<FuzzyMatcher.ScoredVideoResult>> = flow {
        val words = splitFuzzyWords(query)
        val vdao = videoDao
        if (words.isEmpty() || vdao == null) { emit(emptyList()); return@flow }
        val candidates = vdao.searchFuzzyCandidates(fuzzyLikeQuery("scanned_videos", words, listOf("extractedText", "transcript")))
        emit(FuzzyMatcher.searchVideos(candidates, query))
    }.flowOn(Dispatchers.IO)

    private fun splitFuzzyWords(query: String): List<String> =
        query.lowercase().trim().split(Regex("\\s+")).filter { it.isNotBlank() }

    private fun escapeLike(s: String): String =
        s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

    private fun fuzzyLikeQuery(
        table: String,
        words: List<String>,
        columns: List<String> = listOf("extractedText")
    ): SimpleSQLiteQuery {
        val clauses = words.flatMap { columns }.joinToString(" OR ") { "$it LIKE ? ESCAPE '\\'" }
        val notEmpty = columns.joinToString(" OR ") { "$it != ''" }
        val sql = "SELECT * FROM $table WHERE ($notEmpty) AND ($clauses)"
        val args = words.flatMap { w -> columns.map { "%${escapeLike(w)}%" } }.toTypedArray<Any>()
        return SimpleSQLiteQuery(sql, args)
    }

    fun getAllTracked() = dao.getAllTracked().flowOn(Dispatchers.IO)

    fun getTotalScannedCount(): Flow<Int> = dao.getTotalCount().flowOn(Dispatchers.IO)

    fun getTotalScannedVideoCount(): Flow<Int> =
        videoDao?.getTotalCount()?.flowOn(Dispatchers.IO) ?: flowOf(0)

    fun getAvailableBuckets(): List<MediaStoreScanner.ImageBucket> =
        mediaStoreScanner.getAvailableBuckets()

    fun getAvailableVideoBuckets(): List<MediaStoreScanner.VideoBucket> =
        mediaStoreScanner.getAvailableVideoBuckets()

    fun getSelectedBucketIds(): Set<Long> = userPreferences?.selectedBucketIds ?: emptySet()

    fun setSelectedBuckets(ids: Set<Long>) {
        userPreferences?.selectedBucketIds = ids
    }

    fun getSelectedVideoBucketIds(): Set<Long> =
        userPreferences?.selectedVideoBucketIds ?: emptySet()

    fun setSelectedVideoBuckets(ids: Set<Long>) {
        userPreferences?.selectedVideoBucketIds = ids
    }

    fun getIndexVideos(): Boolean = userPreferences?.indexVideos ?: false

    fun setIndexVideos(value: Boolean) {
        userPreferences?.indexVideos = value
    }

    fun startScan() {
        // Capture any in-flight scan so the new one waits for it to actually
        // stop before starting. Without cancelAndJoin, a cancel()+start()
        // sequence (e.g. confirming a new folder selection) could let two
        // scans overlap and race on _scanProgress / the OCR semaphores.
        val prior = scanJob
        scanJob = scope.launch {
            prior?.cancelAndJoin()
            regenJob?.cancelAndJoin()
            _isScanning.value = true
            _scanProgress.value = ScanProgress()
            try {
                scanImages()
                if (userPreferences?.indexVideos == true && videoDao != null && videoProcessor != null) {
                    scanVideos()
                    if (userPreferences.transcribeSpeech) transcribeVideos()
                }
            } finally {
                _isScanning.value = false
            }
        }
    }

    private suspend fun scanImages() {
        val bucketIds = userPreferences?.selectedBucketIds?.takeIf { it.isNotEmpty() }
        val allImages = mediaStoreScanner.scanImages(bucketIds).first()
        val trackedImages = dao.getAllTracked().first()
        val trackedIds = trackedImages.associate { it.mediaStoreId to it.dateModified }

        val toScan = allImages.filter { image ->
            val existingDate = trackedIds[image.mediaStoreId]
            existingDate == null || existingDate != image.dateModified
        }

        val currentIds = allImages.map { it.mediaStoreId }.toSet()
        val deletedIds = trackedImages
            .map { it.mediaStoreId }
            .filter { it !in currentIds }
        if (deletedIds.isNotEmpty()) {
            dao.deleteByIds(deletedIds)
        }

        val totalImages = toScan.size
        if (totalImages == 0) {
            _scanProgress.value = ScanProgress(0, 0)
            return
        }

        val processed = AtomicInteger(0)
        val scanStartTime = System.currentTimeMillis()

        _scanProgress.value = ScanProgress(0, totalImages)

        coroutineScope {
            for (image in toScan) {
                launch {
                    scanSemaphore.withPermit {
                        val text = ocrProcessor.extractTextFromUri(image.contentUri)

                        dao.upsert(
                            com.grepdroid.data.db.ScannedImage(
                                mediaStoreId = image.mediaStoreId,
                                contentUri = image.contentUri,
                                displayName = image.displayName,
                                dateModified = image.dateModified,
                                extractedText = text,
                                scannedAt = System.currentTimeMillis()
                            )
                        )

                        val count = processed.incrementAndGet()
                        val elapsedMs = System.currentTimeMillis() - scanStartTime
                        val eta = if (count >= 3) {
                            val avgMs = elapsedMs.toDouble() / count
                            val remaining = totalImages - count
                            (avgMs * remaining / 1000).toLong()
                        } else {
                            null
                        }
                        _scanProgress.value = ScanProgress(count, totalImages, eta)
                    }
                }
            }
        }

        _scanProgress.value = ScanProgress(totalImages, totalImages, 0)
    }

    private suspend fun scanVideos() {
        val processor = videoProcessor ?: return
        val dao = videoDao ?: return

        val bucketIds = userPreferences?.selectedVideoBucketIds?.takeIf { it.isNotEmpty() }
        val allVideos = mediaStoreScanner.scanVideos(bucketIds).first()
        val trackedVideos = dao.getAllTracked().first()
        val trackedIds = trackedVideos.associate { it.mediaStoreId to it.dateModified }

        val toScan = allVideos.filter { video ->
            val existingDate = trackedIds[video.mediaStoreId]
            existingDate == null || existingDate != video.dateModified
        }

        val currentIds = allVideos.map { it.mediaStoreId }.toSet()
        val deletedIds = trackedVideos
            .map { it.mediaStoreId }
            .filter { it !in currentIds }
        if (deletedIds.isNotEmpty()) {
            dao.deleteByIds(deletedIds)
            // Drop the cached thumbnail for each removed video so cacheDir
            // doesn't accumulate orphaned thumb_<id>.jpg files over time.
            deletedIds.forEach { processor.deleteThumbnail(it) }
        }

        val totalVideos = toScan.size
        if (totalVideos == 0) return

        val processed = AtomicInteger(0)
        val scanStartTime = System.currentTimeMillis()

        _scanProgress.value = ScanProgress(0, totalVideos)

        coroutineScope {
            for (video in toScan) {
                launch {
                    videoScanSemaphore.withPermit {
                        val result = processor.process(video.contentUri, video.mediaStoreId)
                        if (result != null) {
                            dao.upsert(
                                ScannedVideo(
                                    mediaStoreId = video.mediaStoreId,
                                    contentUri = video.contentUri,
                                    displayName = video.displayName,
                                    durationMs = result.durationMs,
                                    dateModified = video.dateModified,
                                    extractedText = result.concatenatedText,
                                    frameTimestampsJson = result.framesJson,
                                    thumbnailPath = result.thumbnailPath,
                                    scannedAt = System.currentTimeMillis()
                                )
                            )
                        }

                        val count = processed.incrementAndGet()
                        val elapsedMs = System.currentTimeMillis() - scanStartTime
                        val eta = if (count >= 2) {
                            val avgMs = elapsedMs.toDouble() / count
                            val remaining = totalVideos - count
                            (avgMs * remaining / 1000).toLong()
                        } else {
                            null
                        }
                        _scanProgress.value = ScanProgress(count, totalVideos, eta)
                    }
                }
            }
        }

        _scanProgress.value = ScanProgress(totalVideos, totalVideos, 0)
    }

    /**
     * Transcribes every indexed video that hasn't been yet (new ones, and all existing
     * ones the first time this runs), newest first, one at a time. Skipped until the
     * speech model has been downloaded. Waits while the phone is overheating, and
     * re-opens the recognizer with more or fewer threads when the charger is plugged
     * in or out.
     */
    private suspend fun transcribeVideos() {
        val engine = speechEngine ?: return
        val dao = videoDao ?: return
        if (!engine.model.isReady) return
        val todo = dao.getUntranscribed()
        if (todo.isEmpty()) return

        val label = "Transcribing speech"
        val startTime = System.currentTimeMillis()
        _scanProgress.value = ScanProgress(0, todo.size, label = label)
        var transcriber: SpeechTranscriber? = null
        try {
            for ((index, video) in todo.withIndex()) {
                while (engine.isOverheating()) {
                    _scanProgress.value = _scanProgress.value.copy(label = "Phone is hot, pausing transcription")
                    delay(15_000)
                }
                val threads = engine.preferredThreads()
                val current = transcriber?.takeIf { it.threads == threads } ?: run {
                    transcriber?.close()
                    transcriber = null
                    engine.open(threads).also { transcriber = it }
                }

                val now = System.currentTimeMillis()
                try {
                    // A video without an audio track is "done" with an empty transcript.
                    val transcript = current.transcribe(video.contentUri)
                    dao.updateTranscript(
                        video.mediaStoreId,
                        transcript?.text.orEmpty(),
                        transcript?.segmentsJson.orEmpty(),
                        now
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    e.printStackTrace()
                    dao.updateTranscript(video.mediaStoreId, "", "", ScannedVideo.TRANSCRIBE_FAILED)
                }

                val count = index + 1
                val elapsedMs = System.currentTimeMillis() - startTime
                val eta = if (count >= 2) (elapsedMs.toDouble() / count * (todo.size - count) / 1000).toLong() else null
                _scanProgress.value = ScanProgress(count, todo.size, eta, label)
            }
        } finally {
            transcriber?.close()
        }
    }

    /**
     * One-off repair pass: for every tracked video whose thumbnail file is
     * missing (or whose path was never populated), decode a single frame and
     * store a fresh thumbnail in filesDir. Unlike a normal scan this touches
     * videos regardless of dateModified, so it recovers thumbnails lost to
     * cacheDir eviction. Reports progress on the scan channel so the UI bar
     * shows it and [cancelScan] can abort it.
     */
    fun regenerateMissingVideoThumbnails() {
        val processor = videoProcessor ?: return
        val dao = videoDao ?: return
        regenJob = scope.launch {
            scanJob?.cancelAndJoin()
            _isScanning.value = true
            _scanProgress.value = ScanProgress()
            try {
                val tracked = dao.getAll()
                val missing = tracked.filter {
                    it.thumbnailPath.isBlank() || !java.io.File(it.thumbnailPath).exists()
                }
                val total = missing.size
                if (total == 0) {
                    _scanProgress.value = ScanProgress(0, 0)
                    return@launch
                }
                val processed = AtomicInteger(0)
                val startTime = System.currentTimeMillis()
                _scanProgress.value = ScanProgress(0, total)
                coroutineScope {
                    for (v in missing) {
                        launch {
                            videoScanSemaphore.withPermit {
                                val path = processor.regenerateThumbnail(v.contentUri, v.mediaStoreId)
                                if (path != null) {
                                    dao.updateThumbnailPath(v.mediaStoreId, path)
                                }
                                val count = processed.incrementAndGet()
                                val elapsedMs = System.currentTimeMillis() - startTime
                                val eta = if (count >= 2) {
                                    val avgMs = elapsedMs.toDouble() / count
                                    ((total - count) * avgMs / 1000).toLong()
                                } else null
                                _scanProgress.value = ScanProgress(count, total, eta)
                            }
                        }
                    }
                }
                _scanProgress.value = ScanProgress(total, total, 0)
            } finally {
                _isScanning.value = false
            }
        }
    }

    fun cancelScan() {
        scanJob?.cancel()
        regenJob?.cancel()
        _isScanning.value = false
        _scanProgress.value = ScanProgress()
    }

    suspend fun getScannedImage(mediaStoreId: Long) = dao.getById(mediaStoreId)

    suspend fun getScannedVideo(mediaStoreId: Long) = videoDao?.getById(mediaStoreId)
}
