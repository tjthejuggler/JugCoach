package com.example.jugcoach.ui.details

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.jugcoach.data.dao.PatternDao
import com.example.jugcoach.data.dao.CoachDao
import com.example.jugcoach.data.entity.Pattern
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EditPatternViewModel @Inject constructor(
    private val patternDao: PatternDao,
    private val coachDao: CoachDao,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _pattern = MutableLiveData<Pattern>()
    val pattern: LiveData<Pattern> = _pattern

    private val _availablePatterns = MutableStateFlow<List<Pattern>>(emptyList())
    val availablePatterns: StateFlow<List<Pattern>> = _availablePatterns

    init {
        savedStateHandle.get<String>("pattern_id")?.let { patternId ->
            loadPattern(patternId)
        }
        loadAvailablePatterns()
    }

    private suspend fun getCurrentCoachId(): Long {
        return coachDao.getAllCoaches().first().find { it.isHeadCoach }?.id ?: 1L
    }

    fun setPatternId(id: String) {
        loadPattern(id)
    }

    private fun loadPattern(id: String) {
        viewModelScope.launch {
            val coachId = getCurrentCoachId()
            patternDao.getPatternById(id, coachId)?.let { pattern ->
                _pattern.value = pattern
            }
        }
    }

    private fun loadAvailablePatterns() {
        viewModelScope.launch {
            val coachId = getCurrentCoachId()
            patternDao.getAllPatterns(coachId).collect { patterns ->
                _availablePatterns.value = patterns
            }
        }
    }

    fun updatePattern(pattern: Pattern) {
        viewModelScope.launch {
            patternDao.updatePattern(pattern)
        }
    }

    fun addPrerequisite(patternId: String) {
        val currentPattern = _pattern.value ?: return
        val updatedPattern = currentPattern.copy(
            prerequisites = currentPattern.prerequisites + patternId
        )
        _pattern.value = updatedPattern
    }

    fun addDependent(patternId: String) {
        val currentPattern = _pattern.value ?: return
        val updatedPattern = currentPattern.copy(
            dependents = currentPattern.dependents + patternId
        )
        _pattern.value = updatedPattern
    }

    fun addRelated(patternId: String) {
        val currentPattern = _pattern.value ?: return
        val updatedPattern = currentPattern.copy(
            related = currentPattern.related + patternId
        )
        _pattern.value = updatedPattern
    }
}
