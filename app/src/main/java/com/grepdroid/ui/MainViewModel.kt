package com.grepdroid.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grepdroid.repository.ImageRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class UiState(
    val searchQuery: String = "",
    val searchResults: List<com.grepdroid.data.db.ScannedImage> = emptyList(),
    val isScanning: Boolean = false,
    val scanProgress: Float = 0f,
    val isLoading: Boolean = false
)

class MainViewModel(
    private val repository: ImageRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()
    
    private val _searchQuery = MutableStateFlow("")
    
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
            _searchQuery
                .debounce(300)
                .distinctUntilChanged()
                .flatMapLatest { query ->
                    if (query.isBlank()) {
                        emptyFlow()
                    } else {
                        repository.search(query)
                    }
                }
                .collect { results ->
                    _uiState.update { it.copy(searchResults = results) }
                }
        }
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
}
