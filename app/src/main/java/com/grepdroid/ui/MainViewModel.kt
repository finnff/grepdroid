package com.grepdroid.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grepdroid.data.MediaStoreScanner
import com.grepdroid.data.UserPreferences
import com.grepdroid.repository.ImageRepository
import com.grepdroid.repository.ScanProgress
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class SortOrder {
    BEST_MATCH, ALPHABETICAL, NEWEST_FIRST, OLDEST_FIRST
}

enum class MediaFilter {
    BOTH, IMAGES, VIDEOS
}

data class UiState(
    val searchQuery: String = "",
    val searchResults: List<MediaResult> = emptyList(),
    val isScanning: Boolean = false,
    val scanProgress: ScanProgress = ScanProgress(),
    val isLoading: Boolean = false,
    val isFuzzySearch: Boolean = false,
    val totalScannedCount: Int = 0,
    val totalScannedVideoCount: Int = 0,
    val availableBuckets: List<MediaStoreScanner.ImageBucket> = emptyList(),
    val selectedBucketIds: Set<Long> = emptySet(),
    val availableVideoBuckets: List<MediaStoreScanner.VideoBucket> = emptyList(),
    val selectedVideoBucketIds: Set<Long> = emptySet(),
    val indexVideos: Boolean = false,
    val showFolderPicker: Boolean = false,
    val gridColumns: Int = 3,
    val sortOrder: SortOrder = SortOrder.BEST_MATCH,
    val totalImagesInSelection: Int = 0,
    val totalVideosInSelection: Int = 0,
    val mediaFilter: MediaFilter = MediaFilter.BOTH
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class MainViewModel(
    private val repository: ImageRepository,
    private val userPreferences: UserPreferences? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val _uiState = MutableStateFlow(UiState(
        isFuzzySearch = userPreferences?.useFuzzySearch ?: false,
        gridColumns = userPreferences?.gridColumns ?: 3,
        sortOrder = userPreferences?.sortOrder?.let {
            try { SortOrder.valueOf(it) } catch (_: Exception) { SortOrder.BEST_MATCH }
        } ?: SortOrder.BEST_MATCH,
        indexVideos = userPreferences?.indexVideos ?: false,
        mediaFilter = userPreferences?.mediaFilter?.let {
            try { MediaFilter.valueOf(it) } catch (_: Exception) { MediaFilter.BOTH }
        } ?: MediaFilter.BOTH
    ))
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    private val _isFuzzyMode = MutableStateFlow(userPreferences?.useFuzzySearch ?: false)
    private val _sortOrder = MutableStateFlow(_uiState.value.sortOrder)
    private val _mediaFilter = MutableStateFlow(_uiState.value.mediaFilter)

    val lastSearchQuery: String get() = _searchQuery.value

    init {
        viewModelScope.launch {
            repository.isScanning.collect { isScanning ->
                _uiState.update { it.copy(isScanning = isScanning) }
            }
        }

        viewModelScope.launch {
            repository.scanProgress.collect { progress ->
                _uiState.update { it.copy(scanProgress = progress) }
            }
        }

        viewModelScope.launch {
            repository.getTotalScannedCount().collect { count ->
                _uiState.update { it.copy(totalScannedCount = count) }
            }
        }

        viewModelScope.launch {
            repository.getTotalScannedVideoCount().collect { count ->
                _uiState.update { it.copy(totalScannedVideoCount = count) }
            }
        }

        viewModelScope.launch {
            combine(
                _searchQuery.debounce(300).distinctUntilChanged(),
                _isFuzzyMode,
                _sortOrder,
                _mediaFilter
            ) { query, fuzzy, sort, filter ->
                SearchInputs(query, fuzzy, sort, filter)
            }.flatMapLatest { (query, fuzzy, sort, filter) ->
                if (query.isBlank()) {
                    flowOf(emptyList())
                } else if (fuzzy) {
                    combine(
                        repository.fuzzySearch(query),
                        repository.fuzzySearchVideos(query)
                    ) { imgs, vids ->
                        val merged: List<MediaResult> =
                            imgs.map { MediaResult.Image(it.image, it.matchPercent) } +
                                vids.map { MediaResult.Video(it.video, it.matchPercent) }
                        sortResults(applyFilter(merged, filter), sort)
                    }
                } else {
                    combine(
                        repository.search(query),
                        repository.searchVideos(query)
                    ) { imgs, vids ->
                        val merged: List<MediaResult> =
                            imgs.map { MediaResult.Image(it) } +
                                vids.map { MediaResult.Video(it) }
                        sortResults(applyFilter(merged, filter), sort)
                    }
                }
            }.collect { result ->
                _uiState.update { it.copy(searchResults = result) }
            }
        }
    }

    private data class SearchInputs(
        val query: String,
        val fuzzy: Boolean,
        val sort: SortOrder,
        val filter: MediaFilter
    )

    private fun applyFilter(
        results: List<MediaResult>,
        filter: MediaFilter
    ): List<MediaResult> = when (filter) {
        MediaFilter.BOTH -> results
        MediaFilter.IMAGES -> results.filterIsInstance<MediaResult.Image>()
        MediaFilter.VIDEOS -> results.filterIsInstance<MediaResult.Video>()
    }

    private fun sortResults(
        results: List<MediaResult>,
        sort: SortOrder
    ): List<MediaResult> = when (sort) {
        SortOrder.BEST_MATCH -> results.sortedWith(
            compareByDescending<MediaResult> { it.matchPercent ?: -1 }
                .thenByDescending { it.dateModified }
        )
        SortOrder.ALPHABETICAL -> results.sortedBy { it.displayName.lowercase() }
        SortOrder.NEWEST_FIRST -> results.sortedByDescending { it.dateModified }
        SortOrder.OLDEST_FIRST -> results.sortedBy { it.dateModified }
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun startScan() {
        repository.startScan()
    }

    fun cancelScan() {
        repository.cancelScan()
    }

    fun clearSearch() {
        _searchQuery.value = ""
        _uiState.update { it.copy(searchQuery = "", searchResults = emptyList()) }
    }

    fun toggleFuzzySearch() {
        val newValue = !_isFuzzyMode.value
        _isFuzzyMode.value = newValue
        userPreferences?.useFuzzySearch = newValue
        _uiState.update { it.copy(isFuzzySearch = newValue) }
    }

    fun cycleGridColumns() {
        val current = _uiState.value.gridColumns
        val next = when (current) {
            2 -> 3
            3 -> 4
            else -> 2
        }
        userPreferences?.gridColumns = next
        _uiState.update { it.copy(gridColumns = next) }
    }

    fun cycleMediaFilter() {
        val next = when (_mediaFilter.value) {
            MediaFilter.BOTH -> MediaFilter.IMAGES
            MediaFilter.IMAGES -> MediaFilter.VIDEOS
            MediaFilter.VIDEOS -> MediaFilter.BOTH
        }
        _mediaFilter.value = next
        userPreferences?.mediaFilter = next.name
        _uiState.update { it.copy(mediaFilter = next) }
    }

    fun cycleSortOrder() {
        val next = when (_sortOrder.value) {
            SortOrder.BEST_MATCH -> SortOrder.ALPHABETICAL
            SortOrder.ALPHABETICAL -> SortOrder.NEWEST_FIRST
            SortOrder.NEWEST_FIRST -> SortOrder.OLDEST_FIRST
            SortOrder.OLDEST_FIRST -> SortOrder.BEST_MATCH
        }
        _sortOrder.value = next
        userPreferences?.sortOrder = next.name
        _uiState.update { it.copy(sortOrder = next) }
    }

    fun loadBuckets() {
        viewModelScope.launch {
            val imageBuckets = withContext(ioDispatcher) { repository.getAvailableBuckets() }
            val videoBuckets = withContext(ioDispatcher) { repository.getAvailableVideoBuckets() }

            val storedImageIds = repository.getSelectedBucketIds()
            val allImageIds = imageBuckets.map { it.bucketId }.toSet()
            val uiSelectedImages = if (storedImageIds.isEmpty()) allImageIds else storedImageIds
            val totalImagesInSel = imageBuckets
                .filter { it.bucketId in uiSelectedImages }
                .sumOf { it.imageCount }

            val storedVideoIds = repository.getSelectedVideoBucketIds()
            val allVideoIds = videoBuckets.map { it.bucketId }.toSet()
            val uiSelectedVideos = if (storedVideoIds.isEmpty()) allVideoIds else storedVideoIds
            val totalVideosInSel = videoBuckets
                .filter { it.bucketId in uiSelectedVideos }
                .sumOf { it.videoCount }

            _uiState.update { it.copy(
                availableBuckets = imageBuckets,
                availableVideoBuckets = videoBuckets,
                showFolderPicker = true,
                selectedBucketIds = uiSelectedImages,
                selectedVideoBucketIds = uiSelectedVideos,
                totalImagesInSelection = totalImagesInSel,
                totalVideosInSelection = totalVideosInSel,
                indexVideos = repository.getIndexVideos()
            ) }
        }
    }

    fun toggleBucket(bucketId: Long) {
        _uiState.update { state ->
            val current = state.selectedBucketIds
            val updated = if (bucketId in current) current - bucketId else current + bucketId
            state.copy(selectedBucketIds = updated)
        }
    }

    fun selectAllBuckets() {
        _uiState.update { state ->
            val allBucketIds = state.availableBuckets.map { it.bucketId }.toSet()
            if (state.selectedBucketIds == allBucketIds) {
                state.copy(selectedBucketIds = emptySet())
            } else {
                state.copy(selectedBucketIds = allBucketIds)
            }
        }
    }

    fun toggleVideoBucket(bucketId: Long) {
        _uiState.update { state ->
            val current = state.selectedVideoBucketIds
            val updated = if (bucketId in current) current - bucketId else current + bucketId
            state.copy(selectedVideoBucketIds = updated)
        }
    }

    fun selectAllVideoBuckets() {
        _uiState.update { state ->
            val allBucketIds = state.availableVideoBuckets.map { it.bucketId }.toSet()
            if (state.selectedVideoBucketIds == allBucketIds) {
                state.copy(selectedVideoBucketIds = emptySet())
            } else {
                state.copy(selectedVideoBucketIds = allBucketIds)
            }
        }
    }

    fun toggleIndexVideos() {
        _uiState.update { it.copy(indexVideos = !it.indexVideos) }
    }

    fun confirmBucketSelection() {
        val state = _uiState.value

        // Image buckets
        val selectedImages = state.selectedBucketIds
        val allImageBucketIds = state.availableBuckets.map { it.bucketId }.toSet()

        if (selectedImages.isEmpty()) {
            repository.setSelectedBuckets(emptySet())
            _uiState.update { it.copy(showFolderPicker = false) }
            repository.cancelScan()
            return
        }

        val imagesToStore = if (selectedImages == allImageBucketIds) emptySet() else selectedImages
        repository.setSelectedBuckets(imagesToStore)
        val totalImagesInSel = state.availableBuckets
            .filter { it.bucketId in selectedImages }
            .sumOf { it.imageCount }

        // Video buckets + toggle
        repository.setIndexVideos(state.indexVideos)
        val selectedVideos = state.selectedVideoBucketIds
        val allVideoBucketIds = state.availableVideoBuckets.map { it.bucketId }.toSet()
        val videosToStore = if (selectedVideos == allVideoBucketIds) emptySet() else selectedVideos
        repository.setSelectedVideoBuckets(videosToStore)
        val totalVideosInSel = state.availableVideoBuckets
            .filter { it.bucketId in selectedVideos }
            .sumOf { it.videoCount }

        _uiState.update { it.copy(
            showFolderPicker = false,
            totalImagesInSelection = totalImagesInSel,
            totalVideosInSelection = totalVideosInSel
        ) }
        repository.cancelScan()
        repository.startScan()
    }

    fun dismissFolderPicker() {
        val storedImages = repository.getSelectedBucketIds()
        val allImageBucketIds = _uiState.value.availableBuckets.map { it.bucketId }.toSet()
        val restoredImages = if (storedImages.isEmpty()) allImageBucketIds else storedImages

        val storedVideos = repository.getSelectedVideoBucketIds()
        val allVideoBucketIds = _uiState.value.availableVideoBuckets.map { it.bucketId }.toSet()
        val restoredVideos = if (storedVideos.isEmpty()) allVideoBucketIds else storedVideos

        _uiState.update { it.copy(
            showFolderPicker = false,
            selectedBucketIds = restoredImages,
            selectedVideoBucketIds = restoredVideos,
            indexVideos = repository.getIndexVideos()
        ) }
    }
}
