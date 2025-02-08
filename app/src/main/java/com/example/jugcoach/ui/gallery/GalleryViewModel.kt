package com.example.jugcoach.ui.gallery

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.jugcoach.data.JugCoachDatabase
import com.example.jugcoach.data.entity.Pattern
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class GalleryUiState(
    val patterns: List<Pattern> = emptyList(),
    val isLoading: Boolean = true,
    val searchQuery: String = "",
    val filterOptions: FilterOptions = FilterOptions(),
    val availableTags: Set<String> = emptySet(),
    val sortOrder: SortOrder = SortOrder.NAME_ASC,
    val shouldScrollToTop: Boolean = false
)

class GalleryViewModel(application: Application) : AndroidViewModel(application) {
    private val patternDao = JugCoachDatabase.getDatabase(application).patternDao()
    private val _uiState = MutableStateFlow(GalleryUiState())
    val uiState: StateFlow<GalleryUiState> = _uiState
    private var patternsJob: Job? = null

    init {
        loadPatterns()
    }

    private fun loadPatterns() {
        patternsJob?.cancel()
        patternsJob = viewModelScope.launch {
            val query = _uiState.value.searchQuery
            if (query.isEmpty()) {
                patternDao.getAllPatterns().collectLatest { patterns ->
                    updatePatterns(patterns)
                }
            } else {
                patternDao.searchPatterns(query).collectLatest { patterns ->
                    updatePatterns(patterns)
                }
            }
        }
    }

    private fun updatePatterns(patterns: List<Pattern>) {
        val tags = patterns.flatMap { it.tags }.toSet()
        val filteredAndSortedPatterns = sortPatterns(
            filterPatterns(patterns, _uiState.value.filterOptions),
            _uiState.value.sortOrder
        )
        _uiState.value = _uiState.value.copy(
            patterns = filteredAndSortedPatterns,
            isLoading = false,
            availableTags = tags
        )
    }

    private fun filterPatterns(patterns: List<Pattern>, options: FilterOptions): List<Pattern> = patterns.filter { pattern ->
        val matchesNumBalls = options.numBalls.isEmpty() || pattern.num in options.numBalls
        val matchesDifficulty = pattern.difficulty?.toFloatOrNull()?.let { diff ->
            diff >= options.difficultyRange.first && diff <= options.difficultyRange.second
        } ?: true
        val matchesTags = options.tags.isEmpty() || pattern.tags.any { it in options.tags }
        val matchesCatches = when {
            options.catchesRange.first != null && options.catchesRange.second != null ->
                pattern.record?.catches?.let { catches ->
                    catches >= options.catchesRange.first!! && catches <= options.catchesRange.second!!
                } ?: false
            options.catchesRange.first != null ->
                pattern.record?.catches?.let { catches ->
                    catches >= options.catchesRange.first!!
                } ?: false
            options.catchesRange.second != null ->
                pattern.record?.catches?.let { catches ->
                    catches <= options.catchesRange.second!!
                } ?: false
            else -> true
        }

        matchesNumBalls && matchesDifficulty && matchesTags && matchesCatches
    }

    private fun sortPatterns(
        patterns: List<Pattern>,
        order: SortOrder
    ): List<Pattern> = when (order) {
        SortOrder.SEARCH_RELEVANCE -> patterns // Already sorted by relevance from the DAO
        SortOrder.NAME_ASC -> patterns.sortedBy { it.name }
        SortOrder.NAME_DESC -> patterns.sortedByDescending { it.name }
        SortOrder.DIFFICULTY_ASC -> patterns.sortedWith(compareBy(
            { it.difficulty?.toIntOrNull() ?: Int.MAX_VALUE },
            { it.name }
        ))
        SortOrder.DIFFICULTY_DESC -> patterns.sortedWith(compareByDescending<Pattern> { 
            it.difficulty?.toIntOrNull() ?: Int.MIN_VALUE 
        }.thenBy { it.name })
        SortOrder.CATCHES_ASC -> patterns.sortedWith(compareBy<Pattern> { 
            it.record?.catches ?: 0 
        }.thenBy { it.name })
        SortOrder.CATCHES_DESC -> patterns.sortedWith(compareByDescending<Pattern> { 
            it.record?.catches ?: 0 
        }.thenBy { it.name })
    }

    fun updateSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(
            searchQuery = query,
            sortOrder = if (query.isNotEmpty()) SortOrder.SEARCH_RELEVANCE else _uiState.value.sortOrder,
            shouldScrollToTop = true
        )
        loadPatterns()
    }

    fun updateFilters(options: FilterOptions) {
        _uiState.value = _uiState.value.copy(
            filterOptions = options,
            shouldScrollToTop = true
        )
        loadPatterns()
    }

    fun updateSortOrder(order: SortOrder) {
        _uiState.value = _uiState.value.copy(
            sortOrder = order,
            shouldScrollToTop = true
        )
        loadPatterns()
    }

    fun scrollHandled() {
        _uiState.value = _uiState.value.copy(shouldScrollToTop = false)
    }

    override fun onCleared() {
        super.onCleared()
        patternsJob?.cancel()
    }
}
