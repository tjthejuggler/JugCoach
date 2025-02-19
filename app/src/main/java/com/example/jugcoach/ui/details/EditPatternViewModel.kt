package com.example.jugcoach.ui.details

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.jugcoach.data.dao.PatternDao
import com.example.jugcoach.data.dao.CoachDao
import com.example.jugcoach.data.dao.CoachProposalDao
import com.example.jugcoach.data.entity.Pattern
import com.example.jugcoach.data.entity.CoachProposal
import com.example.jugcoach.data.entity.ProposalStatus
import com.google.gson.Gson
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
    private val coachProposalDao: CoachProposalDao,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val gson = Gson()
    private var currentCoachId: Long = 1L
    private val _isHeadCoach = MutableLiveData<Boolean>(true)
    val isHeadCoach: LiveData<Boolean> = _isHeadCoach

    private val _pattern = MutableLiveData<Pattern>()
    val pattern: LiveData<Pattern> = _pattern

    private val _availablePatterns = MutableStateFlow<List<Pattern>>(emptyList())
    val availablePatterns: StateFlow<List<Pattern>> = _availablePatterns

    private val _availableTags = MutableStateFlow<Set<String>>(emptySet())
    val availableTags: StateFlow<Set<String>> = _availableTags

    init {
        savedStateHandle.get<String>("pattern_id")?.let { patternId ->
            loadPattern(patternId)
        }
        loadAvailablePatterns()
    }

    private suspend fun initializeCoachInfo() {
        coachDao.getAllCoaches().first().find { it.isHeadCoach }?.let { headCoach ->
            currentCoachId = headCoach.id
            _isHeadCoach.postValue(true)
        } ?: run {
            currentCoachId = coachDao.getAllCoaches().first().first().id
            _isHeadCoach.postValue(false)
        }
    }

    fun setPatternId(id: String) {
        loadPattern(id)
    }

    private fun loadPattern(id: String) {
        viewModelScope.launch {
            initializeCoachInfo()
            patternDao.getPatternById(id, currentCoachId)?.let { pattern ->
                _pattern.value = pattern
            }
        }
    }

    private fun loadAvailablePatterns() {
        viewModelScope.launch {
            patternDao.getAllPatterns(currentCoachId).collect { patterns ->
                _availablePatterns.value = patterns
                // Extract all unique tags from patterns
                val tags = patterns.flatMap { it.tags }.toSet()
                _availableTags.value = tags
            }
        }
    }

    fun addTag(tag: String) {
        val currentPattern = _pattern.value ?: return
        val updatedPattern = currentPattern.copy(
            tags = currentPattern.tags + tag
        )
        _pattern.value = updatedPattern
    }

    fun removeTag(tag: String) {
        val currentPattern = _pattern.value ?: return
        val updatedPattern = currentPattern.copy(
            tags = currentPattern.tags - tag
        )
        _pattern.value = updatedPattern
    }

    fun updatePattern(pattern: Pattern) {
        viewModelScope.launch {
            if (isHeadCoach.value == true) {
                patternDao.updatePattern(pattern)
            } else {
                // Create a proposal for the changes
                val currentPattern = _pattern.value ?: return@launch
                val changes = createChangeProposal(currentPattern, pattern)
                
                coachProposalDao.insertProposal(
                    CoachProposal(
                        patternId = pattern.id,
                        coachId = currentCoachId,
                        proposedChanges = gson.toJson(changes),
                        status = ProposalStatus.PENDING,
                        notes = buildProposalNotes(changes)
                    )
                )
            }
        }
    }

    private fun createChangeProposal(oldPattern: Pattern, newPattern: Pattern): Map<String, Any> {
        val changes = mutableMapOf<String, Any>()
        
        if (oldPattern.name != newPattern.name) 
            changes["name"] = newPattern.name
        if (oldPattern.explanation != newPattern.explanation) 
            changes["explanation"] = newPattern.explanation ?: ""
        if (oldPattern.difficulty != newPattern.difficulty) 
            changes["difficulty"] = newPattern.difficulty ?: ""
        if (oldPattern.num != newPattern.num) 
            changes["num"] = newPattern.num ?: ""
        if (oldPattern.siteswap != newPattern.siteswap) 
            changes["siteswap"] = newPattern.siteswap ?: ""
        if (oldPattern.gifUrl != newPattern.gifUrl) 
            changes["gifUrl"] = newPattern.gifUrl ?: ""
        if (oldPattern.video != newPattern.video) 
            changes["video"] = newPattern.video ?: ""
        if (oldPattern.url != newPattern.url) 
            changes["url"] = newPattern.url ?: ""
        if (oldPattern.prerequisites != newPattern.prerequisites) 
            changes["prerequisites"] = newPattern.prerequisites
        if (oldPattern.dependents != newPattern.dependents) 
            changes["dependents"] = newPattern.dependents
        if (oldPattern.related != newPattern.related) 
            changes["related"] = newPattern.related
        if (oldPattern.tags != newPattern.tags) 
            changes["tags"] = newPattern.tags
        if (oldPattern.runHistory != newPattern.runHistory) 
            changes["runHistory"] = gson.toJson(newPattern.runHistory)
        if (oldPattern.record != newPattern.record) 
            changes["record"] = gson.toJson(newPattern.record)
        
        return changes
    }

    private fun buildProposalNotes(changes: Map<String, Any>): String {
        return buildString {
            append("Proposed changes:\n")
            changes.forEach { (field, value) ->
                append("• $field: $value\n")
            }
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
