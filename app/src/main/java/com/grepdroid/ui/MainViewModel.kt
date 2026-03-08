package com.grepdroid.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grepdroid.data.FuzzyMatcher
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

data class UiState(
    val searchQuery: String = "",
    val searchResults: List<com.grepdroid.data.db.ScannedImage> = emptyList(),
    val searchResultsWithScore: List<FuzzyMatcher.ScoredResult> = emptyList(),
    val isScanning: Boolean = false,
    val scanProgress: ScanProgress = ScanProgress(),
    val isLoading: Boolean = false,
    val isFuzzySearch: Boolean = false,
    val totalScannedCount: Int = 0,
    val availableBuckets: List<MediaStoreScanner.ImageBucket> = emptyList(),
    val selectedBucketIds: Set<Long> = emptySet(),
    val showFolderPicker: Boolean = false,
    val gridColumns: Int = 3,
    val sortOrder: SortOrder = SortOrder.BEST_MATCH,
    val totalImagesInSelection: Int = 0
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
        } ?: SortOrder.BEST_MATCH
    ))
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    private val _isFuzzyMode = MutableStateFlow(userPreferences?.useFuzzySearch ?: false)
    private val _sortOrder = MutableStateFlow(_uiState.value.sortOrder)

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
            combine(
                _searchQuery.debounce(300).distinctUntilChanged(),
                _isFuzzyMode,
                _sortOrder
            ) { query, fuzzy, sort ->
                Triple(query, fuzzy, sort)
            }.flatMapLatest { (query, fuzzy, sort) ->
                if (query.isBlank()) {
                    flowOf(SearchResult(emptyList(), emptyList()))
                } else if (fuzzy) {
                    repository.fuzzySearch(query).map { scored ->
                        val sortedScored = sortScoredResults(scored, sort)
                        SearchResult(sortedScored.map { it.image }, sortedScored)
                    }
                } else {
                    repository.search(query).map { results ->
                        SearchResult(sortResults(results, sort), emptyList())
                    }
                }
            }.collect { result ->
                _uiState.update {
                    it.copy(
                        searchResults = result.images,
                        searchResultsWithScore = result.scored
                    )
                }
            }
        }
    }

    private fun sortResults(
        images: List<com.grepdroid.data.db.ScannedImage>,
        sort: SortOrder
    ): List<com.grepdroid.data.db.ScannedImage> = when (sort) {
        SortOrder.BEST_MATCH -> images.sortedByDescending { it.dateModified }
        SortOrder.ALPHABETICAL -> images.sortedBy { it.displayName.lowercase() }
        SortOrder.NEWEST_FIRST -> images.sortedByDescending { it.dateModified }
        SortOrder.OLDEST_FIRST -> images.sortedBy { it.dateModified }
    }

    private fun sortScoredResults(
        scored: List<FuzzyMatcher.ScoredResult>,
        sort: SortOrder
    ): List<FuzzyMatcher.ScoredResult> = when (sort) {
        SortOrder.BEST_MATCH -> scored.sortedWith(compareByDescending<FuzzyMatcher.ScoredResult> { it.matchPercent }.thenByDescending { it.image.dateModified })
        SortOrder.ALPHABETICAL -> scored.sortedBy { it.image.displayName.lowercase() }
        SortOrder.NEWEST_FIRST -> scored.sortedByDescending { it.image.dateModified }
        SortOrder.OLDEST_FIRST -> scored.sortedBy { it.image.dateModified }
    }

    private data class SearchResult(
        val images: List<com.grepdroid.data.db.ScannedImage>,
        val scored: List<FuzzyMatcher.ScoredResult>
    )

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
        _uiState.update { it.copy(searchQuery = "", searchResults = emptyList(), searchResultsWithScore = emptyList()) }
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
            val buckets = withContext(ioDispatcher) { repository.getAvailableBuckets() }
            // Convert stored selection to UI selection:
            // stored empty set means "all" (no filter), so show all checked
            val storedIds = repository.getSelectedBucketIds()
            val allBucketIds = buckets.map { it.bucketId }.toSet()
            val uiSelected = if (storedIds.isEmpty()) allBucketIds else storedIds
            val totalInSelection = buckets
                .filter { it.bucketId in uiSelected }
                .sumOf { it.imageCount }
            _uiState.update { it.copy(
                availableBuckets = buckets,
                showFolderPicker = true,
                selectedBucketIds = uiSelected,
                totalImagesInSelection = totalInSelection
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

    fun confirmBucketSelection() {
        val state = _uiState.value
        val selected = state.selectedBucketIds
        val allBucketIds = state.availableBuckets.map { it.bucketId }.toSet()

        if (selected.isEmpty()) {
            // Nothing selected — store empty, don't scan
            repository.setSelectedBuckets(emptySet())
            _uiState.update { it.copy(showFolderPicker = false) }
            repository.cancelScan()
            return
        }

        // If all are selected, store empty set (meaning "all" / no filter)
        val toStore = if (selected == allBucketIds) emptySet() else selected
        repository.setSelectedBuckets(toStore)
        val totalInSelection = state.availableBuckets
            .filter { it.bucketId in selected }
            .sumOf { it.imageCount }
        _uiState.update { it.copy(showFolderPicker = false, totalImagesInSelection = totalInSelection) }
        repository.cancelScan()
        repository.startScan()
    }

    fun dismissFolderPicker() {
        val stored = repository.getSelectedBucketIds()
        // Restore: if stored is empty, that means "all" — show all buckets selected
        val allBucketIds = _uiState.value.availableBuckets.map { it.bucketId }.toSet()
        val restored = if (stored.isEmpty()) allBucketIds else stored
        _uiState.update { it.copy(showFolderPicker = false, selectedBucketIds = restored) }
    }
}
