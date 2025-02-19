package com.example.jugcoach.ui.gallery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.jugcoach.data.dao.PatternDao
import com.example.jugcoach.data.entity.Pattern
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class CreatePatternViewModel @Inject constructor(
    private val patternDao: PatternDao
) : ViewModel() {

    companion object {
        const val RELATIONSHIP_PREREQUISITE = "prerequisite"
        const val RELATIONSHIP_RELATED = "related"
        const val RELATIONSHIP_DEPENDENT = "dependent"
    }

    private var sourcePatternId: String? = null
    private var relationshipType: String? = null

    fun initializeFromSourcePattern(patternId: String, relationship: String) {
        viewModelScope.launch {
            sourcePatternId = patternId
            relationshipType = relationship

            // Load the source pattern
            val sourcePattern = patternDao.getPatternById(patternId, -1) // -1 to get any pattern
            sourcePattern?.let { pattern ->
                // Copy tags from source pattern
                pattern.tags.forEach { tag ->
                    addTag(tag)
                }

                // Set up relationships based on type
                when (relationship) {
                    RELATIONSHIP_PREREQUISITE -> {
                        // New pattern will be a prerequisite of source pattern
                        _uiState.update { it.copy(
                            dependentPatterns = it.dependentPatterns + patternId
                        )}
                    }
                    RELATIONSHIP_RELATED -> {
                        // New pattern will be related to source pattern and inherit all its relationships
                        _uiState.update { it.copy(
                            // Add source pattern and all its related patterns (except itself)
                            relatedPatterns = it.relatedPatterns + patternId + (pattern.related - patternId),
                            prerequisites = it.prerequisites + pattern.prerequisites,
                            dependentPatterns = it.dependentPatterns + pattern.dependents
                        )}
                    }
                    RELATIONSHIP_DEPENDENT -> {
                        // New pattern will have source pattern as prerequisite
                        _uiState.update { it.copy(
                            prerequisites = it.prerequisites + patternId
                        )}
                    }
                }

                // Copy name and number of balls
                updateName(pattern.name)
                pattern.num?.let { updateNumBalls(it) }

                // Copy difficulty (optionally decrease/increase based on relationship)
                pattern.difficulty?.let { diff ->
                    val newDifficulty = when (relationship) {
                        RELATIONSHIP_PREREQUISITE -> (diff.toFloatOrNull()?.minus(1) ?: diff).toString()
                        RELATIONSHIP_DEPENDENT -> (diff.toFloatOrNull()?.plus(1) ?: diff).toString()
                        else -> diff
                    }
                    updateDifficulty(newDifficulty)
                }

                // For dependent patterns, copy source pattern's related patterns as prerequisites
                if (relationship == RELATIONSHIP_DEPENDENT) {
                    pattern.related.forEach { relatedId ->
                        _uiState.update { it.copy(
                            prerequisites = it.prerequisites + relatedId
                        )}
                    }
                }
            }
        }
    }

    private val _uiState = MutableStateFlow(CreatePatternUiState())
    val uiState: StateFlow<CreatePatternUiState> = _uiState

    private val _availablePatterns = MutableStateFlow<List<Pattern>>(emptyList())
    val availablePatterns: StateFlow<List<Pattern>> = _availablePatterns

    init {
        viewModelScope.launch {
            patternDao.getSharedPatterns().collect { patterns ->
                _availablePatterns.value = patterns
                // Extract all unique tags from patterns
                val tags = patterns.flatMap { it.tags }.toSet()
                _uiState.update { it.copy(availableTags = tags) }
            }
        }
    }

    // Form field updates
    fun updateName(name: String) {
        _uiState.update { state ->
            state.copy(
                name = name,
                nameError = validateName(name)
            )
        }
        // Auto-detect siteswap from name
        detectSiteswapFromName(name)
    }

    fun updateNumBalls(num: String) {
        _uiState.update { state ->
            state.copy(
                numBalls = num,
                numBallsError = validateNumBalls(num)
            )
        }
    }

    fun updateDifficulty(difficulty: String) {
        _uiState.update { state ->
            state.copy(
                difficulty = difficulty,
                difficultyError = validateDifficulty(difficulty)
            )
        }
    }

    fun updateSiteswap(siteswap: String) {
        _uiState.update { state ->
            state.copy(
                siteswap = siteswap,
                siteswapError = validateSiteswap(siteswap)
            )
        }
        if (siteswap.isNotBlank()) {
            handleValidSiteswap(siteswap)
        }
    }

    fun updateVideoUrl(url: String) {
        _uiState.update { state ->
            state.copy(
                videoUrl = url,
                videoUrlError = validateVideoUrl(url)
            )
        }
    }

    fun updateVideoStartTime(time: String) {
        _uiState.update { state ->
            state.copy(
                videoStartTime = time,
                videoTimeError = validateVideoTimes(time, state.videoEndTime)
            )
        }
    }

    fun updateVideoEndTime(time: String) {
        _uiState.update { state ->
            state.copy(
                videoEndTime = time,
                videoTimeError = validateVideoTimes(state.videoStartTime, time)
            )
        }
    }

    fun updateGifUrl(url: String) {
        _uiState.update { state ->
            state.copy(
                gifUrl = url,
                gifUrlError = validateGifUrl(url)
            )
        }
    }

    fun updateTutorialUrl(url: String) {
        _uiState.update { state ->
            state.copy(
                tutorialUrl = url,
                tutorialUrlError = validateTutorialUrl(url)
            )
        }
    }

    fun addTag(tag: String) {
        if (tag.isNotBlank() && !_uiState.value.tags.contains(tag)) {
            _uiState.update { state ->
                state.copy(tags = state.tags + tag)
            }
            inferRelatedTags(tag)
        }
    }

    fun removeTag(tag: String) {
        _uiState.update { state ->
            state.copy(tags = state.tags - tag)
        }
    }

    fun updateExplanation(explanation: String) {
        _uiState.update { state ->
            state.copy(explanation = explanation)
        }
    }

    // Pattern relations
    fun addPrerequisite(patternId: String) {
        _uiState.update { state ->
            state.copy(prerequisites = state.prerequisites + patternId)
        }
    }

    fun removePrerequisite(patternId: String) {
        _uiState.update { state ->
            state.copy(prerequisites = state.prerequisites - patternId)
        }
    }

    fun addRelatedPattern(patternId: String) {
        _uiState.update { state ->
            state.copy(relatedPatterns = state.relatedPatterns + patternId)
        }
    }

    fun removeRelatedPattern(patternId: String) {
        _uiState.update { state ->
            state.copy(relatedPatterns = state.relatedPatterns - patternId)
        }
    }

    fun addDependentPattern(patternId: String) {
        _uiState.update { state ->
            state.copy(dependentPatterns = state.dependentPatterns + patternId)
        }
    }

    fun removeDependentPattern(patternId: String) {
        _uiState.update { state ->
            state.copy(dependentPatterns = state.dependentPatterns - patternId)
        }
    }

    // Validation
    private fun validateName(name: String): String? {
        return when {
            name.isBlank() -> "Name is required"
            name.length < 2 -> "Name must be at least 2 characters"
            else -> null
        }
    }

    private fun validateNumBalls(num: String): String? {
        return when {
            num.isBlank() -> "Number of balls is required"
            num.toIntOrNull() == null -> "Must be a number"
            num.toInt() !in 1..11 -> "Must be between 1 and 11"
            else -> null
        }
    }

    private fun validateDifficulty(difficulty: String): String? {
        return when {
            difficulty.isBlank() -> "Difficulty is required"
            difficulty.toFloatOrNull() == null -> "Must be a number"
            difficulty.toFloat() !in 1f..10f -> "Must be between 1 and 10"
            else -> null
        }
    }

    private fun validateSiteswap(siteswap: String): String? {
        // TODO: Implement siteswap validation
        return null
    }

    private fun validateVideoUrl(url: String): String? {
        if (url.isBlank() && _uiState.value.gifUrl.isBlank()) {
            return "Either video or GIF URL is required"
        }
        if (url.isNotBlank() && !isValidVideoUrl(url)) {
            return "Invalid video URL"
        }
        return null
    }

    private fun validateVideoTimes(startTime: String, endTime: String): String? {
        if (startTime.isBlank() || endTime.isBlank()) return null
        
        val start = parseTimeToSeconds(startTime)
        val end = parseTimeToSeconds(endTime)
        
        return when {
            start == null -> "Invalid start time format (use mm:ss)"
            end == null -> "Invalid end time format (use mm:ss)"
            start >= end -> "End time must be after start time"
            else -> null
        }
    }

    private fun validateGifUrl(url: String): String? {
        if (url.isBlank() && _uiState.value.videoUrl.isBlank()) {
            return "Either video or GIF URL is required"
        }
        if (url.isNotBlank() && !isValidGifUrl(url)) {
            return "Invalid GIF URL"
        }
        return null
    }

    private fun validateTutorialUrl(url: String): String? {
        if (url.isNotBlank() && !isValidUrl(url)) {
            return "Invalid URL"
        }
        return null
    }

    // Helper functions
    private fun detectSiteswapFromName(name: String) {
        // TODO: Implement siteswap detection from name
    }

    private fun handleValidSiteswap(siteswap: String) {
        // TODO: Implement siteswap handling (auto-populate num balls, add pure-ss tag)
    }

    private fun inferRelatedTags(tag: String) {
        // TODO: Implement tag inference
    }

    private fun isValidVideoUrl(url: String): Boolean {
        // TODO: Implement video URL validation (YouTube, Instagram)
        return true
    }

    private fun isValidGifUrl(url: String): Boolean {
        // TODO: Implement GIF URL validation
        return true
    }

    private fun isValidUrl(url: String): Boolean {
        return url.startsWith("http://") || url.startsWith("https://")
    }

    private fun parseTimeToSeconds(time: String): Int? {
        val parts = time.split(":")
        if (parts.size != 2) return null
        
        val minutes = parts[0].toIntOrNull() ?: return null
        val seconds = parts[1].toIntOrNull() ?: return null
        
        if (minutes < 0 || seconds !in 0..59) return null
        
        return minutes * 60 + seconds
    }

    // Save pattern
    fun savePattern() {
        if (!isFormValid()) return

        viewModelScope.launch {
            val pattern = Pattern(
                id = UUID.randomUUID().toString(),
                name = _uiState.value.name,
                num = _uiState.value.numBalls,
                difficulty = _uiState.value.difficulty,
                siteswap = _uiState.value.siteswap.takeIf { it.isNotBlank() },
                explanation = _uiState.value.explanation.takeIf { it.isNotBlank() },
                gifUrl = _uiState.value.gifUrl.takeIf { it.isNotBlank() },
                video = _uiState.value.videoUrl.takeIf { it.isNotBlank() },
                url = _uiState.value.tutorialUrl.takeIf { it.isNotBlank() },
                tags = _uiState.value.tags.toList(),
                prerequisites = _uiState.value.prerequisites.toList(),
                dependents = _uiState.value.dependentPatterns.toList(),
                related = _uiState.value.relatedPatterns.toList()
            )
            patternDao.insertPattern(pattern)
            _uiState.update { it.copy(isSaved = true) }
        }
    }

    private fun isFormValid(): Boolean {
        return _uiState.value.run {
            nameError == null && numBallsError == null &&
                    difficultyError == null && siteswapError == null &&
                    videoUrlError == null && videoTimeError == null &&
                    gifUrlError == null && tutorialUrlError == null &&
                    name.isNotBlank() && numBalls.isNotBlank() &&
                    difficulty.isNotBlank() && (videoUrl.isNotBlank() || gifUrl.isNotBlank())
        }
    }
}

data class CreatePatternUiState(
    val name: String = "",
    val nameError: String? = null,
    val numBalls: String = "",
    val numBallsError: String? = null,
    val difficulty: String = "",
    val difficultyError: String? = null,
    val siteswap: String = "",
    val siteswapError: String? = null,
    val videoUrl: String = "",
    val videoUrlError: String? = null,
    val videoStartTime: String = "",
    val videoEndTime: String = "",
    val videoTimeError: String? = null,
    val gifUrl: String = "",
    val gifUrlError: String? = null,
    val tutorialUrl: String = "",
    val tutorialUrlError: String? = null,
    val tags: Set<String> = emptySet(),
    val availableTags: Set<String> = emptySet(),
    val explanation: String = "",
    val prerequisites: Set<String> = emptySet(),
    val relatedPatterns: Set<String> = emptySet(),
    val dependentPatterns: Set<String> = emptySet(),
    val isSaved: Boolean = false
)