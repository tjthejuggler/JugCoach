package com.example.jugcoach.ui.gallery

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.jugcoach.data.dao.PatternDao
import com.example.jugcoach.data.dao.CoachDao
import com.example.jugcoach.data.entity.Pattern
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GalleryUiState(
    val patterns: List<Pattern> = emptyList(),
    val isLoading: Boolean = true,
    val searchQuery: String = "",
    val filterOptions: FilterOptions = FilterOptions(),
    val availableTags: Set<String> = emptySet(),
    val sortOrder: SortOrder = SortOrder.NAME_ASC,
    val shouldScrollToTop: Boolean = false
)

@HiltViewModel
class GalleryViewModel @Inject constructor(
    private val patternDao: PatternDao,
    private val coachDao: CoachDao
) : ViewModel() {
    companion object {
        private const val DEBUG_TAG = "FilterDebug"
    }
    private val _uiState = MutableStateFlow(GalleryUiState())
    val uiState: StateFlow<GalleryUiState> = _uiState
    private var patternsJob: Job? = null

    init {
        loadPatterns()
    }

    private suspend fun getCurrentCoachId(): Long {
        return coachDao.getAllCoaches().first().find { it.isHeadCoach }?.id ?: 1L
    }

    private fun loadPatterns() {
        Log.d(DEBUG_TAG, "Loading patterns with filters: ${_uiState.value.filterOptions}")
        patternsJob?.cancel()
        patternsJob = viewModelScope.launch {
            val coachId = getCurrentCoachId()
            val query = _uiState.value.searchQuery
            Log.d(DEBUG_TAG, "Query: '$query', Filters: ${_uiState.value.filterOptions}")
            if (query.isEmpty()) {
                patternDao.getAllPatterns(coachId).collectLatest { patterns ->
                    Log.d(DEBUG_TAG, "Got ${patterns.size} patterns from DAO")
                    updatePatterns(patterns)
                }
            } else {
                patternDao.searchPatterns(query, coachId).collectLatest { patterns ->
                    Log.d(DEBUG_TAG, "Got ${patterns.size} patterns from search")
                    updatePatterns(patterns)
                }
            }
        }
    }

    private fun updatePatterns(patterns: List<Pattern>) {
        Log.d(DEBUG_TAG, "Updating patterns with current filters: ${_uiState.value.filterOptions}")
        val tags = patterns.flatMap { it.tags }.toSet()
        val filteredPatterns = filterPatterns(patterns, _uiState.value.filterOptions)
        Log.d(DEBUG_TAG, "After filtering: ${filteredPatterns.size} patterns (from ${patterns.size})")
        val filteredAndSortedPatterns = sortPatterns(
            filteredPatterns,
            _uiState.value.sortOrder
        )
        _uiState.value = _uiState.value.copy(
            patterns = filteredAndSortedPatterns,
            isLoading = false,
            availableTags = tags
        )
        Log.d(DEBUG_TAG, "UI state updated with ${filteredAndSortedPatterns.size} patterns")
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
        SortOrder.LAST_PRACTICED_ASC -> patterns.sortedWith(compareBy<Pattern> { pattern ->
            // Get most recent run date, or Long.MAX_VALUE if no runs (to sort to the end)
            pattern.runHistory.runs.maxOfOrNull { it.date } ?: Long.MAX_VALUE
        }.thenBy { it.name })
        SortOrder.LAST_PRACTICED_DESC -> patterns.sortedWith(compareByDescending<Pattern> { pattern ->
            // Get most recent run date, or Long.MIN_VALUE if no runs (to sort to the end)
            pattern.runHistory.runs.maxOfOrNull { it.date } ?: Long.MIN_VALUE
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
        Log.d(DEBUG_TAG, "Updating filters to: $options")
        Log.d(DEBUG_TAG, "Previous filters were: ${_uiState.value.filterOptions}")
        
        // Create new state with updated filters
        val newState = _uiState.value.copy(
            filterOptions = options,
            shouldScrollToTop = true
        )
        
        // Update state
        _uiState.value = newState
        Log.d(DEBUG_TAG, "New UI state filters: ${_uiState.value.filterOptions}")
        
        // Load patterns with new filters
        Log.d(DEBUG_TAG, "Loading patterns with new filters")
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
