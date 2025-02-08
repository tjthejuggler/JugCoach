package com.example.jugcoach.ui.details

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.jugcoach.data.dao.PatternDao
import com.example.jugcoach.data.entity.Pattern
import com.example.jugcoach.data.entity.Run
import com.example.jugcoach.data.entity.RunHistory
import com.example.jugcoach.data.entity.Record
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PatternDetailsViewModel @Inject constructor(
    private val patternDao: PatternDao,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val patternId: String = checkNotNull(savedStateHandle["patternId"])

    private val _uiState = MutableStateFlow<PatternDetailsUiState>(PatternDetailsUiState.Loading)
    val uiState: StateFlow<PatternDetailsUiState> = _uiState.asStateFlow()

    private val _prerequisites = MutableStateFlow<List<Pattern>>(emptyList())
    val prerequisites = _prerequisites.asStateFlow()

    private val _dependents = MutableStateFlow<List<Pattern>>(emptyList())
    val dependents = _dependents.asStateFlow()

    private val _related = MutableStateFlow<List<Pattern>>(emptyList())
    val related = _related.asStateFlow()

    init {
        loadPattern()
    }

    private fun loadPattern() {
        viewModelScope.launch {
            try {
                val pattern = patternDao.getPatternById(patternId)
                if (pattern != null) {
                    _uiState.value = PatternDetailsUiState.Success(
                        pattern = pattern,
                        runHistory = pattern.runHistory.runs
                    )
                    loadRelatedPatterns(pattern)
                } else {
                    _uiState.value = PatternDetailsUiState.Error("Pattern not found")
                }
            } catch (e: Exception) {
                _uiState.value = PatternDetailsUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    private suspend fun loadRelatedPatterns(pattern: Pattern) {
        android.util.Log.d("PatternDetails", "Loading related patterns for ${pattern.name}")
        android.util.Log.d("PatternDetails", "Prerequisites IDs: ${pattern.prerequisites}")
        android.util.Log.d("PatternDetails", "Dependents IDs: ${pattern.dependents}")
        android.util.Log.d("PatternDetails", "Related IDs: ${pattern.related}")

        // Load prerequisites
        val prerequisites = pattern.prerequisites.mapNotNull { id ->
            patternDao.getPatternById(id)?.also {
                android.util.Log.d("PatternDetails", "Found prerequisite: ${it.name}")
            }
        }
        _prerequisites.value = prerequisites
        android.util.Log.d("PatternDetails", "Loaded ${prerequisites.size} prerequisites")

        // Load dependent patterns
        val dependents = pattern.dependents.mapNotNull { id ->
            patternDao.getPatternById(id)?.also {
                android.util.Log.d("PatternDetails", "Found dependent: ${it.name}")
            }
        }
        _dependents.value = dependents
        android.util.Log.d("PatternDetails", "Loaded ${dependents.size} dependents")

        // Load related patterns
        val related = pattern.related.mapNotNull { id ->
            patternDao.getPatternById(id)?.also {
                android.util.Log.d("PatternDetails", "Found related: ${it.name}")
            }
        }
        _related.value = related
        android.util.Log.d("PatternDetails", "Loaded ${related.size} related patterns")
    }

    fun updatePattern(updatedPattern: Pattern) {
        viewModelScope.launch {
            try {
                patternDao.updatePattern(updatedPattern)
                loadPattern() // Reload to refresh the UI
            } catch (e: Exception) {
                _uiState.value = PatternDetailsUiState.Error(e.message ?: "Failed to update pattern")
            }
        }
    }

    fun deletePattern() {
        viewModelScope.launch {
            try {
                val currentPattern = (uiState.value as? PatternDetailsUiState.Success)?.pattern
                    ?: return@launch
                patternDao.deletePattern(currentPattern)
                _uiState.value = PatternDetailsUiState.Deleted
            } catch (e: Exception) {
                _uiState.value = PatternDetailsUiState.Error(e.message ?: "Failed to delete pattern")
            }
        }
    }

    fun addRun(catches: Int?, duration: Long?, isCleanEnd: Boolean) {
        viewModelScope.launch {
            try {
                val currentPattern = (uiState.value as? PatternDetailsUiState.Success)?.pattern
                    ?: return@launch

                val newRun = Run(
                    catches = catches,
                    duration = duration,
                    isCleanEnd = isCleanEnd,
                    date = System.currentTimeMillis()
                )

                val updatedHistory = currentPattern.runHistory.runs + newRun
                val updatedPattern = currentPattern.copy(
                    runHistory = RunHistory(updatedHistory)
                )

                // Update record if this run has more catches than current record
                val finalPattern = if (catches != null) {
                    val shouldUpdateRecord = currentPattern.record == null || catches > currentPattern.record.catches
                    if (shouldUpdateRecord) {
                        updatedPattern.copy(
                            record = Record(
                                catches = catches,
                                date = System.currentTimeMillis()
                            )
                        )
                    } else {
                        updatedPattern
                    }
                } else {
                    updatedPattern
                }

                patternDao.updatePattern(finalPattern)
                loadPattern() // Reload to refresh the UI
            } catch (e: Exception) {
                _uiState.value = PatternDetailsUiState.Error(e.message ?: "Failed to add run")
            }
        }
    }
}

sealed class PatternDetailsUiState {
    data object Loading : PatternDetailsUiState()
    data class Success(
        val pattern: Pattern,
        val runHistory: List<Run> = emptyList()
    ) : PatternDetailsUiState()
    data class Error(val message: String) : PatternDetailsUiState()
    data object Deleted : PatternDetailsUiState()
}
