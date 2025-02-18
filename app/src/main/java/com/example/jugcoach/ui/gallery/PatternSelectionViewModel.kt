package com.example.jugcoach.ui.gallery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.jugcoach.data.dao.PatternDao
import com.example.jugcoach.data.entity.Pattern
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PatternSelectionViewModel @Inject constructor(
    private val patternDao: PatternDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(PatternSelectionUiState())
    val uiState: StateFlow<PatternSelectionUiState> = _uiState

    private val searchQuery = MutableStateFlow("")
    private val selectedTags = MutableStateFlow<Set<String>>(emptySet())
    private val selectedPatterns = MutableStateFlow<Set<String>>(emptySet())
    private val selectionType = MutableStateFlow<PatternSelectionDialog.SelectionType?>(null)

    init {
        viewModelScope.launch {
            // Combine all filters and sort patterns accordingly
            searchQuery.combine(selectedTags) { query, tags ->
                Pair(query, tags)
            }.combine(selectionType) { (query, tags), type ->
                Triple(query, tags, type)
            }.combine(patternDao.getSharedPatterns()) { (query, tags, type), patterns ->
                val filteredPatterns = patterns.filter { pattern ->
                    val matchesSearch = if (query.isBlank()) {
                        true
                    } else {
                        pattern.name.contains(query, ignoreCase = true)
                    }

                    val matchesTags = if (tags.isEmpty()) {
                        true
                    } else {
                        pattern.tags.any { it in tags }
                    }

                    matchesSearch && matchesTags
                }

                // Sort patterns based on selection type
                val sortedPatterns = when (type) {
                    PatternSelectionDialog.SelectionType.PREREQUISITES -> {
                        filteredPatterns.sortedBy { it.difficulty }
                    }
                    PatternSelectionDialog.SelectionType.DEPENDENTS -> {
                        filteredPatterns.sortedByDescending { it.difficulty }
                    }
                    PatternSelectionDialog.SelectionType.RELATED -> {
                        // Sort by tag similarity
                        filteredPatterns
                    }
                    null -> filteredPatterns
                }

                // Get all available tags from filtered patterns
                val availableTags = filteredPatterns
                    .flatMap { it.tags }
                    .toSet()

                _uiState.value = _uiState.value.copy(
                    patterns = sortedPatterns,
                    availableTags = availableTags,
                    selectedTags = tags,
                    selectedPatternIds = selectedPatterns.value
                )
            }
        }
    }

    fun setSelectionType(type: PatternSelectionDialog.SelectionType) {
        selectionType.value = type
    }

    fun updateSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun addTagFilter(tag: String) {
        selectedTags.value = selectedTags.value + tag
    }

    fun removeTagFilter(tag: String) {
        selectedTags.value = selectedTags.value - tag
    }

    fun togglePatternSelection(pattern: Pattern) {
        selectedPatterns.value = if (pattern.id in selectedPatterns.value) {
            selectedPatterns.value - pattern.id
        } else {
            selectedPatterns.value + pattern.id
        }
        _uiState.value = _uiState.value.copy(
            selectedPatternIds = selectedPatterns.value
        )
    }

    fun getSelectedPatterns(): List<Pattern> {
        return _uiState.value.patterns.filter { 
            it.id in selectedPatterns.value 
        }
    }
}

data class PatternSelectionUiState(
    val patterns: List<Pattern> = emptyList(),
    val availableTags: Set<String> = emptySet(),
    val selectedTags: Set<String> = emptySet(),
    val selectedPatternIds: Set<String> = emptySet()
)