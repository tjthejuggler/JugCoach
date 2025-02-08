package com.example.jugcoach.ui.gallery

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.jugcoach.data.JugCoachDatabase
import com.example.jugcoach.data.entity.Pattern
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class GalleryUiState(
    val patterns: List<Pattern> = emptyList(),
    val isLoading: Boolean = true,
    val searchQuery: String = "",
    val filterOptions: FilterOptions = FilterOptions(),
    val availableTags: Set<String> = emptySet(),
    val sortOrder: SortOrder = SortOrder.NAME_ASC
)

class GalleryViewModel(application: Application) : AndroidViewModel(application) {
    private val patternDao = JugCoachDatabase.getDatabase(application).patternDao()
    private val searchQuery = MutableStateFlow("")
    private val filterOptions = MutableStateFlow(FilterOptions())
    private val sortOrder = MutableStateFlow(SortOrder.NAME_ASC)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val patterns: StateFlow<List<Pattern>> = searchQuery
        .flatMapLatest { query ->
            if (query.isEmpty()) {
                patternDao.getAllPatterns()
            } else {
                patternDao.searchPatterns(query)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val availableTags: StateFlow<Set<String>> = patterns
        .map { patternList ->
            patternList.flatMap { it.tags }.toSet()
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptySet()
        )

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

    private val combinedState = combine(
        patterns,
        searchQuery,
        filterOptions,
        sortOrder,
        availableTags
    ) { p, q, f, o, a -> 
        GalleryUiState(
            patterns = sortPatterns(filterPatterns(p, f), o),
            isLoading = false,
            searchQuery = q,
            filterOptions = f,
            availableTags = a,
            sortOrder = o
        )
    }

    val uiState: StateFlow<GalleryUiState> = combinedState.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = GalleryUiState()
    )

    fun updateSearchQuery(query: String) {
        searchQuery.value = query
        // Switch to search relevance sort order when searching
        if (query.isNotEmpty()) {
            sortOrder.value = SortOrder.SEARCH_RELEVANCE
        }
    }

    fun updateFilters(options: FilterOptions) {
        filterOptions.value = options
    }

    fun updateSortOrder(order: SortOrder) {
        sortOrder.value = order
    }
}
