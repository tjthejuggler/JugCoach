package com.example.jugcoach.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.jugcoach.data.JugCoachDatabase
import com.example.jugcoach.data.entity.SettingType
import com.example.jugcoach.data.entity.SettingCategory
import com.example.jugcoach.data.importer.PatternImporter
import com.example.jugcoach.data.converter.PatternConverter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

data class SettingsUiState(
    val apiKey: String = "",
    val patternCount: Int = 0,
    val isSaving: Boolean = false,
    val isImporting: Boolean = false,
    val isExporting: Boolean = false,
    val message: String? = null
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val database = JugCoachDatabase.getDatabase(application)
    private val settingsDao = database.settingsDao()
    private val patternDao = database.patternDao()
    private val patternImporter = PatternImporter(application, patternDao)

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            val apiKey = settingsDao.getSettingValue("llm_api_key") ?: ""
            
            // Update API key immediately
            _uiState.value = _uiState.value.copy(apiKey = apiKey)
            
            // Collect pattern count updates
            patternDao.getAllPatterns().collect { patterns ->
                _uiState.value = _uiState.value.copy(
                    patternCount = patterns.size
                )
            }
        }
    }

    fun updateApiKey(apiKey: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true)
            try {
                settingsDao.setSettingValue(
                    key = "llm_api_key",
                    value = apiKey,
                    type = SettingType.STRING,
                    category = SettingCategory.API_KEY,
                    description = "API key for LLM service",
                    isEncrypted = true
                )
                _uiState.value = _uiState.value.copy(
                    apiKey = apiKey,
                    message = "API key saved successfully",
                    isSaving = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    message = "Failed to save API key: ${e.message}",
                    isSaving = false
                )
            }
        }
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }

    fun importPatterns() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isImporting = true)
            try {
                val count = patternImporter.importLegacyPatterns()
                _uiState.value = _uiState.value.copy(
                    message = "Successfully imported $count patterns",
                    isImporting = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    message = "Failed to import patterns: ${e.message}",
                    isImporting = false
                )
            }
        }
    }

    fun exportPatterns() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isExporting = true)
            try {
                val patterns = patternDao.getAllPatternsSync()
                val json = PatternConverter.toJson(patterns)
                
                // Save to Downloads directory
                val downloadsDir = getApplication<Application>().getExternalFilesDir(null)
                val exportFile = File(downloadsDir, "juggling_patterns_${System.currentTimeMillis()}.json")
                exportFile.writeText(json)

                _uiState.value = _uiState.value.copy(
                    message = "Patterns exported to ${exportFile.absolutePath}",
                    isExporting = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    message = "Failed to export patterns: ${e.message}",
                    isExporting = false
                )
            }
        }
    }
}
