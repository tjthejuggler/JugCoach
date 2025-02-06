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
import kotlinx.coroutines.flow.stateIn

class GalleryViewModel(application: Application) : AndroidViewModel(application) {
    private val patternDao = JugCoachDatabase.getDatabase(application).patternDao()
    private val searchQuery = MutableStateFlow("")

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

    val uiState = combine(
        patterns,
        searchQuery
    ) { patternsList, query ->
        GalleryUiState(
            patterns = patternsList,
            isLoading = false,
            searchQuery = query
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = GalleryUiState()
    )

    fun updateSearchQuery(query: String) {
        searchQuery.value = query
    }
}

data class GalleryUiState(
    val patterns: List<Pattern> = emptyList(),
    val isLoading: Boolean = true,
    val searchQuery: String = ""
)
