package com.example.jugcoach.ui.tooltest

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.jugcoach.data.dao.PatternDao
import com.example.jugcoach.data.query.PatternQueryParser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "ToolTest"

data class ToolTestUiState(
    val patternId: String = "",
    val searchCriteria: String = "",
    val response: String = "Response will appear here",
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ToolTestViewModel @Inject constructor(
    private val patternDao: PatternDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(ToolTestUiState())
    val uiState: StateFlow<ToolTestUiState> = _uiState.asStateFlow()
    private val queryParser = PatternQueryParser(patternDao)

    fun setPatternId(patternId: String) {
        _uiState.update { it.copy(patternId = patternId) }
    }

    fun setSearchCriteria(criteria: String) {
        _uiState.update { it.copy(searchCriteria = criteria) }
    }

    fun testSearchPatterns() {
        val criteria = _uiState.value.searchCriteria.trim()
        if (criteria.isEmpty()) {
            _uiState.update { it.copy(error = "Please enter search criteria") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                Log.d(TAG, "Searching patterns with criteria: $criteria")
                
                // Use the queryParser to search patterns
                queryParser.parseSearchCommand(criteria, 1L).collect { patterns ->
                    Log.d(TAG, "Found ${patterns.size} patterns matching criteria")
                    
                    // Format each pattern using concise format
                    val formattedPatterns = patterns.map { pattern ->
                        queryParser.formatPatternResponse(pattern, concise = true)
                    }
                    
                    // Update UI with results
                    _uiState.update { 
                        it.copy(
                            response = """
                                Found ${patterns.size} patterns:
                                
                                ${formattedPatterns.joinToString("\n\n")}
                            """.trimIndent(),
                            isLoading = false
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error searching patterns", e)
                _uiState.update { 
                    it.copy(
                        error = "Error: ${e.message}",
                        response = "Failed to search patterns: ${e.message}",
                        isLoading = false
                    )
                }
            }
        }
    }

    init {
        // Load all patterns on init to verify database content
        viewModelScope.launch {
            try {
                val allPatterns = patternDao.getAllPatternsSync(1L)
                Log.d(TAG, "All patterns in database (${allPatterns.size}):")
                allPatterns.forEach { pattern ->
                    Log.d(TAG, "Pattern: id=${pattern.id}, name=${pattern.name}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading patterns", e)
            }
        }
    }

    fun testLookupPattern() {
        val patternId = _uiState.value.patternId.trim()
        if (patternId.isEmpty()) {
            _uiState.update { it.copy(error = "Please enter a pattern ID") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                Log.d(TAG, "Looking up pattern with name: $patternId")
                
                // Get all patterns first to see what's available
                val allPatterns = patternDao.getAllPatternsSync(1L)
                Log.d(TAG, "Available patterns: ${allPatterns.map { it.name }}")
                
                // Query the database by name
                val pattern = allPatterns.find { it.name == patternId }
                Log.d(TAG, "Raw pattern from database: $pattern")

                if (pattern == null) {
                    Log.d(TAG, "No pattern found with name: $patternId")
                    _uiState.update { 
                        it.copy(
                            response = """
                                No pattern found with name: $patternId
                                
                                Available patterns:
                                ${allPatterns.joinToString("\n") { "- ${it.name} (${it.id})" }}
                            """.trimIndent(),
                            isLoading = false
                        )
                    }
                    return@launch
                }

                // Format the response
                val formattedResponse = queryParser.formatPatternResponse(pattern)
                Log.d(TAG, "Formatted response: $formattedResponse")

                _uiState.update { 
                    it.copy(
                        response = """
                            Raw Pattern:
                            $pattern
                            
                            Formatted Response:
                            $formattedResponse
                            
                            Available patterns:
                            ${allPatterns.joinToString("\n") { "- ${it.id} (${it.name})" }}
                        """.trimIndent(),
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error looking up pattern", e)
                _uiState.update { 
                    it.copy(
                        error = "Error: ${e.message}",
                        response = "Failed to lookup pattern: ${e.message}",
                        isLoading = false
                    )
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
