package com.grepdroid.repository

import com.grepdroid.data.MediaStoreScanner
import com.grepdroid.data.OcrProcessor
import com.grepdroid.data.db.ImageDao
import com.grepdroid.data.db.TrackedImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class ImageRepository(
    private val dao: ImageDao,
    private val mediaStoreScanner: MediaStoreScanner,
    private val ocrProcessor: OcrProcessor
) {
    
    private val scanSemaphore = Semaphore(4)
    private var scanJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    
    private val _scanProgress = MutableStateFlow(0f)
    val scanProgress: StateFlow<Float> = _scanProgress.asStateFlow()
    
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()
    
    fun search(query: String) = dao.search(query).flowOn(Dispatchers.IO)
    
    fun getAllTracked() = dao.getAllTracked().flowOn(Dispatchers.IO)
    
    fun startScan() {
        if (_isScanning.value) return
        
        _isScanning.value = true
        _scanProgress.value = 0f
        
        scanJob = scope.launch {
            try {
                val allImages = mediaStoreScanner.scanImages().first()
                val trackedImages = dao.getAllTracked().first()
                val trackedIds = trackedImages.associate { it.mediaStoreId to it.dateModified }
                
                val toScan = mutableListOf<MediaStoreScanner.ImageItem>()
                val toRemove = trackedImages.map { it.mediaStoreId }.toMutableList()
                
                for (image in allImages) {
                    val existingDate = trackedIds[image.mediaStoreId]
                    if (existingDate == null || existingDate != image.dateModified) {
                        toScan.add(image)
                    } else {
                        toRemove.remove(image.mediaStoreId)
                    }
                }
                
                if (toRemove.isNotEmpty()) {
                    dao.deleteRemoved(toRemove)
                }
                
                val totalImages = toScan.size
                var processed = 0
                
                for (image in toScan) {
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
                        
                        processed++
                        _scanProgress.value = processed.toFloat() / totalImages.toFloat()
                        
                        delay(100)
                    }
                }
                
                _scanProgress.value = 1f
            } finally {
                _isScanning.value = false
            }
        }
    }
    
    fun cancelScan() {
        scanJob?.cancel()
        _isScanning.value = false
        _scanProgress.value = 0f
    }
    
    suspend fun getScannedImage(mediaStoreId: Long) = dao.getById(mediaStoreId)
}
