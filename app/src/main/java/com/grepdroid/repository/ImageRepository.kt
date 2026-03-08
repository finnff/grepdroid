package com.grepdroid.repository

import com.grepdroid.data.FuzzyMatcher
import com.grepdroid.data.MediaStoreScanner
import com.grepdroid.data.OcrProcessor
import com.grepdroid.data.UserPreferences
import com.grepdroid.data.db.ImageDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.atomic.AtomicInteger

data class ScanProgress(
    val processed: Int = 0,
    val total: Int = 0,
    val estimatedSecondsRemaining: Long? = null
) {
    val fraction: Float get() = if (total == 0) 0f else processed.toFloat() / total
}

class ImageRepository(
    private val dao: ImageDao,
    private val mediaStoreScanner: MediaStoreScanner,
    private val ocrProcessor: OcrProcessor,
    private val userPreferences: UserPreferences? = null,
    private val dispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO
) {

    private val scanSemaphore = Semaphore(4)
    private var scanJob: Job? = null
    private val scope = CoroutineScope(dispatcher)

    private val _scanProgress = MutableStateFlow(ScanProgress())
    val scanProgress: StateFlow<ScanProgress> = _scanProgress.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    fun search(query: String) = dao.search(query).flowOn(Dispatchers.IO)

    fun fuzzySearch(query: String): Flow<List<FuzzyMatcher.ScoredResult>> =
        dao.getAllWithText().map { images ->
            FuzzyMatcher.search(images, query)
        }.flowOn(Dispatchers.IO)

    fun getAllTracked() = dao.getAllTracked().flowOn(Dispatchers.IO)

    fun getTotalScannedCount(): Flow<Int> = dao.getTotalCount().flowOn(Dispatchers.IO)

    fun getAvailableBuckets(): List<MediaStoreScanner.ImageBucket> =
        mediaStoreScanner.getAvailableBuckets()

    fun getSelectedBucketIds(): Set<Long> = userPreferences?.selectedBucketIds ?: emptySet()

    fun setSelectedBuckets(ids: Set<Long>) {
        userPreferences?.selectedBucketIds = ids
    }

    fun startScan() {
        if (_isScanning.value) return

        _isScanning.value = true
        _scanProgress.value = ScanProgress()

        scanJob = scope.launch {
            try {
                val bucketIds = userPreferences?.selectedBucketIds?.takeIf { it.isNotEmpty() }
                val allImages = mediaStoreScanner.scanImages(bucketIds).first()
                val trackedImages = dao.getAllTracked().first()
                val trackedIds = trackedImages.associate { it.mediaStoreId to it.dateModified }

                // Find images that are new or modified
                val toScan = allImages.filter { image ->
                    val existingDate = trackedIds[image.mediaStoreId]
                    existingDate == null || existingDate != image.dateModified
                }

                // Delete images no longer in gallery
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
                    return@launch
                }

                val processed = AtomicInteger(0)
                val scanStartTime = System.currentTimeMillis()

                _scanProgress.value = ScanProgress(0, totalImages)

                // Process images in parallel, throttled by semaphore
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
            } finally {
                _isScanning.value = false
            }
        }
    }

    fun cancelScan() {
        scanJob?.cancel()
        _isScanning.value = false
        _scanProgress.value = ScanProgress()
    }

    suspend fun getScannedImage(mediaStoreId: Long) = dao.getById(mediaStoreId)
}
