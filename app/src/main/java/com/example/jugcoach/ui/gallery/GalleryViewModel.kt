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

enum class SortOrder {
    NAME_ASC, NAME_DESC,
    DIFFICULTY_ASC, DIFFICULTY_DESC
}

enum class DifficultyFilter {
    ALL, BEGINNER, ADVANCED;

    fun matches(difficulty: Int?): Boolean = when (this) {
        ALL -> true
        BEGINNER -> difficulty != null && difficulty <= 3
        ADVANCED -> difficulty != null && difficulty > 3
    }
}

private data class FilterState(
    val patterns: List<Pattern>,
    val query: String,
    val difficulty: DifficultyFilter,
    val tags: Set<String>,
    val order: SortOrder,
    val allTags: Set<String>
)

data class GalleryUiState(
    val patterns: List<Pattern> = emptyList(),
    val isLoading: Boolean = true,
    val searchQuery: String = "",
    val selectedDifficulty: DifficultyFilter = DifficultyFilter.ALL,
    val selectedTags: Set<String> = emptySet(),
    val availableTags: Set<String> = emptySet(),
    val sortOrder: SortOrder = SortOrder.NAME_ASC
)

class GalleryViewModel(application: Application) : AndroidViewModel(application) {
    private val patternDao = JugCoachDatabase.getDatabase(application).patternDao()
    private val searchQuery = MutableStateFlow("")
    private val selectedDifficulty = MutableStateFlow(DifficultyFilter.ALL)
    private val selectedTags = MutableStateFlow<Set<String>>(emptySet())
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

    private fun filterPatterns(
        patterns: List<Pattern>,
        difficulty: DifficultyFilter,
        tags: Set<String>
    ): List<Pattern> = patterns.filter { pattern ->
        difficulty.matches(pattern.difficulty) &&
        (tags.isEmpty() || pattern.tags.any { it in tags })
    }

    private fun sortPatterns(
        patterns: List<Pattern>,
        order: SortOrder
    ): List<Pattern> = when (order) {
        SortOrder.NAME_ASC -> patterns.sortedBy { it.name }
        SortOrder.NAME_DESC -> patterns.sortedByDescending { it.name }
        SortOrder.DIFFICULTY_ASC -> patterns.sortedBy { it.difficulty }
        SortOrder.DIFFICULTY_DESC -> patterns.sortedByDescending { it.difficulty }
    }

    private val combinedState = combine(
        patterns,
        searchQuery,
        selectedDifficulty
    ) { p, q, d -> Triple(p, q, d) }

    private val combinedFilters = combine(
        selectedTags,
        sortOrder,
        availableTags
    ) { t, o, a -> Triple(t, o, a) }

    val uiState: StateFlow<GalleryUiState> = combine(
        combinedState,
        combinedFilters
    ) { state, filters ->
        val patternsList = state.first
        val query = state.second
        val difficulty = state.third
        val tags = filters.first
        val order = filters.second
        val allTags = filters.third
        FilterState(
            patterns = patternsList,
            query = query,
            difficulty = difficulty,
            tags = tags,
            order = order,
            allTags = allTags
        )
    }.map { state ->
        val filteredPatterns = filterPatterns(state.patterns, state.difficulty, state.tags)
        val sortedPatterns = sortPatterns(filteredPatterns, state.order)
        GalleryUiState(
            patterns = sortedPatterns,
            isLoading = false,
            searchQuery = state.query,
            selectedDifficulty = state.difficulty,
            selectedTags = state.tags,
            availableTags = state.allTags,
            sortOrder = state.order
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = GalleryUiState()
    )


    fun updateSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun updateDifficultyFilter(filter: DifficultyFilter) {
        selectedDifficulty.value = filter
    }

    fun toggleTag(tag: String) {
        val current = selectedTags.value
        selectedTags.value = if (tag in current) {
            current - tag
        } else {
            current + tag
        }
    }

    fun updateSortOrder(order: SortOrder) {
        sortOrder.value = order
    }
}
